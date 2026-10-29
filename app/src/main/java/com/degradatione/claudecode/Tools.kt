package com.degradatione.claudecode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Реальные инструменты Claude Code, исполняемые на самом телефоне.
 * Bash идёт через /system/bin/sh (toybox), файлы — в рабочей папке приложения.
 */
class ToolRunner(private val prefs: Prefs, private val filesDir: File) {

    val workspace: File
        get() {
            val custom = prefs.workspace
            val f = if (custom.isNotBlank()) File(custom) else File(filesDir, "workspace")
            if (!f.exists()) f.mkdirs()
            return f
        }

    private fun appFilesDir(): File = filesDir

    fun resolve(path: String): File {
        val p = path.trim()
        return if (p.startsWith("/")) File(p) else File(workspace, p)
    }

    fun summarize(name: String, inputJson: String): String {
        val o = runCatching { JSONObject(inputJson) }.getOrNull() ?: return name
        return when (name) {
            "Bash" -> o.optString("command").lineSequence().firstOrNull().orEmpty().take(80)
            "Read" -> o.optString("file_path")
            "Write" -> o.optString("file_path")
            "Edit" -> o.optString("file_path")
            "LS" -> o.optString("path").ifBlank { "." }
            "Glob" -> o.optString("pattern")
            "Grep" -> "«${o.optString("pattern")}» " + o.optString("path", ".")
            else -> ""
        }
    }

    fun needsPermission(name: String, inputJson: String): Boolean {
        if (prefs.autoApprove) return false
        return when (name) {
            "Read", "LS", "Glob", "Grep" -> false
            else -> true
        }
    }

    suspend fun run(name: String, inputJson: String): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        val input = runCatching { JSONObject(inputJson) }.getOrElse { JSONObject() }
        try {
            when (name) {
                "Bash" -> bash(input.optString("command"), input.optLong("timeout", 120000))
                "Read" -> read(input)
                "Write" -> write(input)
                "Edit" -> edit(input)
                "LS" -> ls(input)
                "Glob" -> glob(input)
                "Grep" -> grep(input)
                else -> "Unknown tool: $name" to true
            }
        } catch (t: Throwable) {
            LogBus.e("tool", "$name упал", t)
            ("Tool $name failed: ${t.javaClass.simpleName}: ${t.message}") to true
        }
    }

    // ── Bash ────────────────────────────────────────────────────────────────
    private fun bash(command: String, timeoutMs: Long): Pair<String, Boolean> {
        if (command.isBlank()) return "Empty command" to true
        LogBus.i("bash", "$ $command")
        val pb = ProcessBuilder("/system/bin/sh", "-c", command)
        pb.directory(workspace)
        pb.redirectErrorStream(true)
        pb.environment()["HOME"] = workspace.absolutePath
        pb.environment()["TMPDIR"] = File(appFilesDir(), "tmp").apply { mkdirs() }.absolutePath
        pb.environment()["PATH"] = "/system/bin:/system/xbin:/vendor/bin:/sbin:/su/bin:" + pb.environment()["PATH"].orEmpty()
        pb.environment()["PAGER"] = "cat"
        val proc = pb.start()
        val out = StringBuilder()
        val reader = proc.inputStream.bufferedReader()
        val deadline = System.currentTimeMillis() + timeoutMs.coerceIn(1000, 600000)
        val buf = CharArray(4096)
        var truncated = false
        try {
            while (true) {
                if (System.currentTimeMillis() > deadline) {
                    proc.destroyForcibly()
                    out.append("\n[timeout ${timeoutMs / 1000}s — процесс убит]")
                    break
                }
                if (reader.ready() || proc.isAlive) {
                    val n = if (reader.ready()) reader.read(buf) else -1
                    if (n > 0) {
                        out.appendRange(buf, 0, n)
                        if (out.length > 200_000) {
                            truncated = true
                            proc.destroyForcibly()
                            break
                        }
                    } else if (!proc.isAlive) {
                        val rest = reader.read(buf)
                        if (rest > 0) out.appendRange(buf, 0, rest) else break
                    } else {
                        Thread.sleep(15)
                    }
                } else break
            }
            if (proc.isAlive) proc.waitFor(2, TimeUnit.SECONDS)
        } catch (t: Throwable) {
            runCatching { proc.destroyForcibly() }
            LogBus.e("bash", "чтение вывода: ${t.message}")
        }
        val code = runCatching { proc.exitValue() }.getOrDefault(-1)
        var text = out.toString().trimEnd()
        if (truncated) text += "\n… вывод обрезан (>200 KB)"
        LogBus.d("bash", "exit=$code, ${text.length} симв.")
        val body = if (text.isBlank()) "(пустой вывод)" else text
        val res = "$body\n[exit code: $code]"
        return res to (code != 0)
    }

    // ── Read ────────────────────────────────────────────────────────────────
    private fun read(i: JSONObject): Pair<String, Boolean> {
        val f = resolve(i.optString("file_path"))
        if (!f.exists()) return "File not found: ${f.absolutePath}" to true
        if (f.isDirectory) return "Is a directory: ${f.absolutePath} (use LS)" to true
        val offset = i.optInt("offset", 1).coerceAtLeast(1)
        val limit = i.optInt("limit", 2000).coerceIn(1, 5000)
        val lines = f.readLines()
        if (lines.size > 2_000_000) return "File too large" to true
        val slice = lines.drop(offset - 1).take(limit)
        val sb = StringBuilder()
        slice.forEachIndexed { idx, line ->
            val num = offset + idx
            sb.append(num.toString().padStart(6)).append("→").append(line).append('\n')
        }
        LogBus.d("read", "${f.path} строки $offset..${offset + slice.size - 1}")
        return sb.toString().ifBlank { "(пустой файл)" } to false
    }

    // ── Write ───────────────────────────────────────────────────────────────
    private fun write(i: JSONObject): Pair<String, Boolean> {
        val f = resolve(i.optString("file_path"))
        val content = i.optString("content")
        f.parentFile?.mkdirs()
        val existed = f.exists()
        f.writeText(content)
        LogBus.i("write", "${if (existed) "перезаписан" else "создан"} ${f.path} (${content.length} симв.)")
        return "${if (existed) "Updated" else "Created"} ${f.absolutePath} (${content.length} chars)" to false
    }

    // ── Edit ────────────────────────────────────────────────────────────────
    private fun edit(i: JSONObject): Pair<String, Boolean> {
        val f = resolve(i.optString("file_path"))
        if (!f.exists()) return "File not found: ${f.absolutePath}" to true
        val old = i.optString("old_string")
        val new = i.optString("new_string")
        val all = i.optBoolean("replace_all", false)
        val text = f.readText()
        if (old.isEmpty()) return "old_string is empty" to true
        val count = Regex(Regex.escape(old)).findAll(text).count()
        if (count == 0) return "old_string not found in ${f.path}" to true
        if (count > 1 && !all) return "old_string appears $count times; add more context or set replace_all" to true
        val updated = if (all) text.replace(old, new) else text.replaceFirst(old, new)
        f.writeText(updated)
        LogBus.i("edit", "${f.path}: заменено $count вхождений")
        return "Edited ${f.absolutePath}: replaced ${if (all) count else 1} occurrence(s)" to false
    }

    // ── LS ──────────────────────────────────────────────────────────────────
    private fun ls(i: JSONObject): Pair<String, Boolean> {
        val dir = resolve(i.optString("path", "."))
        if (!dir.exists()) return "Not found: ${dir.absolutePath}" to true
        if (!dir.isDirectory) return "Not a directory: ${dir.absolutePath}" to true
        val files = dir.listFiles()?.sortedBy { it.name.lowercase() } ?: emptyList()
        if (files.isEmpty()) return "(пустая папка)" to false
        val sb = StringBuilder()
        for (f in files.take(500)) {
            if (f.isDirectory) sb.append("d  ").append(f.name).append('/')
            else sb.append("f  ").append(f.name).append("  ").append(humanSize(f.length()))
            sb.append('\n')
        }
        if (files.size > 500) sb.append("… ещё ${files.size - 500}\n")
        return sb.toString() to false
    }

    // ── Glob ────────────────────────────────────────────────────────────────
    private fun glob(i: JSONObject): Pair<String, Boolean> {
        val pattern = i.optString("pattern", "**/*")
        val root = resolve(i.optString("path", "."))
        val regex = globToRegex(pattern)
        val out = mutableListOf<String>()
        walk(root, 0) { f ->
            val rel = f.relativeTo(workspace).path
            if (regex.matches(rel) || regex.matches(f.name)) out += rel
        }
        val sorted = out.sorted().take(500)
        return (if (sorted.isEmpty()) "No files match $pattern" else sorted.joinToString("\n")) to false
    }

    // ── Grep ────────────────────────────────────────────────────────────────
    private fun grep(i: JSONObject): Pair<String, Boolean> {
        val pattern = i.optString("pattern")
        if (pattern.isBlank()) return "pattern is required" to true
        val root = resolve(i.optString("path", "."))
        val globFilter = i.optString("glob", "")
        val rx = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrElse {
            return "Bad regex: ${it.message}" to true
        }
        val filterRx = if (globFilter.isBlank()) null else globToRegex(globFilter)
        val hits = mutableListOf<String>()
        walk(if (root.isDirectory) root else root.parentFile ?: workspace, 0) { f ->
            if (f.length() > 2_000_000) return@walk
            if (filterRx != null && !filterRx.matches(f.name) && !filterRx.matches(f.relativeTo(workspace).path)) return@walk
            runCatching {
                f.useLines { lines ->
                    lines.forEachIndexed { idx, line ->
                        if (hits.size < 200 && rx.containsMatchIn(line)) {
                            hits += "${f.relativeTo(workspace).path}:${idx + 1}: ${line.take(300)}"
                        }
                    }
                }
            }
        }
        return (if (hits.isEmpty()) "No matches for $pattern" else hits.joinToString("\n")) to false
    }

    private fun walk(dir: File, depth: Int, onFile: (File) -> Unit) {
        if (depth > 12) return
        val skip = setOf(".git", "node_modules", "build", ".gradle", "__pycache__", ".idea")
        val kids = dir.listFiles() ?: return
        for (f in kids) {
            if (f.isDirectory) {
                if (f.name in skip) continue
                walk(f, depth + 1, onFile)
            } else {
                onFile(f)
            }
        }
    }

    private fun humanSize(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> "${b / 1024} KB"
        else -> String.format("%.1f MB", b / 1048576.0)
    }

    private fun globToRegex(glob: String): Regex {
        val sb = StringBuilder("^")
        var i = 0
        while (i < glob.length) {
            val c = glob[i]
            when {
                c == '*' && i + 1 < glob.length && glob[i + 1] == '*' -> {
                    sb.append(".*"); i++
                    if (i + 1 < glob.length && glob[i + 1] == '/') i++
                }
                c == '*' -> sb.append("[^/]*")
                c == '?' -> sb.append("[^/]")
                c == '.' -> sb.append("\\.")
                else -> sb.append(Regex.escape(c.toString()))
            }
            i++
        }
        sb.append("$")
        return Regex(sb.toString())
    }

    companion object {
        fun toolSpecs(): JSONArray {
            val arr = JSONArray()
            fun spec(name: String, desc: String, props: Map<String, Pair<String, String>>, required: List<String>) {
                val p = JSONObject()
                for ((k, v) in props) {
                    p.put(k, JSONObject().put("type", v.first).put("description", v.second))
                }
                arr.put(
                    JSONObject()
                        .put("name", name)
                        .put("description", desc)
                        .put(
                            "input_schema",
                            JSONObject().put("type", "object").put("properties", p).put("required", JSONArray(required))
                        )
                )
            }
            spec(
                "Bash", "Run a shell command on the device (Android toybox sh). Working dir is the project workspace.",
                mapOf(
                    "command" to ("string" to "The command to run"),
                    "timeout" to ("integer" to "Timeout in milliseconds (default 120000)")
                ), listOf("command")
            )
            spec(
                "Read", "Read a text file. Returns lines numbered like '   12→code'.",
                mapOf(
                    "file_path" to ("string" to "Relative path inside the workspace or absolute path"),
                    "offset" to ("integer" to "1-indexed first line to read"),
                    "limit" to ("integer" to "Max lines to read (default 2000)")
                ), listOf("file_path")
            )
            spec(
                "Write", "Create or overwrite a file with full content.",
                mapOf(
                    "file_path" to ("string" to "Path to write"),
                    "content" to ("string" to "Full file contents")
                ), listOf("file_path", "content")
            )
            spec(
                "Edit", "Replace an exact string in a file. old_string must be unique unless replace_all is true.",
                mapOf(
                    "file_path" to ("string" to "File to edit"),
                    "old_string" to ("string" to "Exact text to replace"),
                    "new_string" to ("string" to "Replacement text"),
                    "replace_all" to ("boolean" to "Replace every occurrence")
                ), listOf("file_path", "old_string", "new_string")
            )
            spec(
                "LS", "List directory contents.",
                mapOf("path" to ("string" to "Directory path, defaults to workspace root")),
                emptyList()
            )
            spec(
                "Glob", "Find files by glob pattern, e.g. '**/*.kt'.",
                mapOf(
                    "pattern" to ("string" to "Glob pattern"),
                    "path" to ("string" to "Root directory")
                ), listOf("pattern")
            )
            spec(
                "Grep", "Search file contents with a regex (ripgrep-like).",
                mapOf(
                    "pattern" to ("string" to "Regex to search"),
                    "path" to ("string" to "Directory to search"),
                    "glob" to ("string" to "Only files matching this glob, e.g. '*.md'")
                ), listOf("pattern")
            )
            return arr
        }
    }
}
