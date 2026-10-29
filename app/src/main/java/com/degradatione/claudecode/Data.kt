package com.degradatione.claudecode

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ToolStatus { PENDING, RUNNING, OK, ERROR, DENIED }

@Immutable
data class ToolCall(
    val id: String,
    val name: String,
    val inputJson: String,
    val summary: String = "",
    val output: String? = null,
    val status: ToolStatus = ToolStatus.PENDING,
    val permissionNeeded: Boolean = false,
    val startedAt: Long = 0L,
)

enum class ItemKind { USER, ASSISTANT, TOOL, ERROR, INFO, THINKING, DIFF }

@Immutable
data class ChatItem(
    val id: String,
    val kind: ItemKind,
    val text: String = "",
    val tool: ToolCall? = null,
    val streaming: Boolean = false,
    val ts: Long = System.currentTimeMillis(),
)

data class SessionMeta(
    val id: String,
    var title: String,
    val createdAt: Long,
    var updatedAt: Long,
    var items: List<ChatItem>,
    var apiJson: String,
    var model: String,
)

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

object MsgJson {

    fun textBlock(text: String): JSONObject =
        JSONObject().put("type", "text").put("text", text)

    fun userMessage(text: String): JSONObject = JSONObject()
        .put("role", "user")
        .put("content", JSONArray().put(textBlock(text)))

    fun toolResultMessage(results: List<Triple<String, String, Boolean>>): JSONObject {
        val arr = JSONArray()
        for ((id, content, isError) in results) {
            arr.put(
                JSONObject()
                    .put("type", "tool_result")
                    .put("tool_use_id", id)
                    .put(
                        "content",
                        if (isError) JSONArray().put(
                            JSONObject().put("type", "text").put("text", content)
                        ) else content
                    )
                    .put("is_error", isError)
            )
        }
        return JSONObject().put("role", "user").put("content", arr)
    }

    fun assistantMessage(items: List<ChatItem>, toolInputs: Map<String, String>): JSONObject {
        val arr = JSONArray()
        for (it in items) {
            if (it.kind == ItemKind.ASSISTANT) {
                if (it.text.isNotBlank()) arr.put(textBlock(it.text))
            } else if (it.kind == ItemKind.TOOL) {
                val t = it.tool ?: continue
                val rawInput = toolInputs[t.id]?.takeIf { s -> s.isNotBlank() }
                    ?: t.inputJson.takeIf { s -> s.isNotBlank() }
                    ?: "{}"
                val inputObj = try {
                    JSONObject(rawInput)
                } catch (e: Exception) {
                    JSONObject()
                }
                arr.put(
                    JSONObject()
                        .put("type", "tool_use")
                        .put("id", t.id)
                        .put("name", t.name)
                        .put("input", inputObj)
                )
            }
        }
        return JSONObject().put("role", "assistant").put("content", arr)
    }
}

class SessionStore(private val app: android.content.Context) {

    private val dir = java.io.File(app.filesDir, "sessions").apply { mkdirs() }

    fun loadAll(): List<SessionMeta> {
        val out = mutableListOf<SessionMeta>()
        val files = dir.listFiles() ?: return out
        for (f in files) {
            if (!f.name.endsWith(".json")) continue
            try {
                out += fromJson(JSONObject(f.readText()))
            } catch (e: Exception) {
                LogBus.e("session", "не смог прочитать ${f.name}: ${e.message}")
            }
        }
        return out.sortedByDescending { it.updatedAt }
    }

    fun save(s: SessionMeta) {
        try {
            val o = JSONObject()
            o.put("id", s.id)
            o.put("title", s.title)
            o.put("createdAt", s.createdAt)
            o.put("updatedAt", s.updatedAt)
            o.put("model", s.model)
            o.put("apiJson", s.apiJson)
            val items = JSONArray()
            for (i in s.items) {
                val io = JSONObject()
                io.put("id", i.id)
                io.put("kind", i.kind.name)
                io.put("text", i.text)
                io.put("ts", i.ts)
                i.tool?.let { t ->
                    io.put(
                        "tool",
                        JSONObject()
                            .put("id", t.id)
                            .put("name", t.name)
                            .put("inputJson", t.inputJson)
                            .put("summary", t.summary)
                            .put("output", t.output ?: "")
                            .put("status", t.status.name)
                    )
                }
                items.put(io)
            }
            o.put("items", items)
            java.io.File(dir, s.id + ".json").writeText(o.toString())
        } catch (e: Exception) {
            LogBus.e("session", "не сохранил сессию: ${e.message}")
        }
    }

    fun delete(id: String) {
        java.io.File(dir, id + ".json").delete()
    }

    private fun fromJson(o: JSONObject): SessionMeta {
        val items = mutableListOf<ChatItem>()
        val arr = o.optJSONArray("items") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val io = arr.getJSONObject(i)
            val toolObj = io.optJSONObject("tool")
            val tool = toolObj?.let {
                ToolCall(
                    id = it.optString("id"),
                    name = it.optString("name"),
                    inputJson = it.optString("inputJson"),
                    summary = it.optString("summary"),
                    output = it.optString("output").ifBlank { null },
                    status = runCatching { ToolStatus.valueOf(it.optString("status", "OK")) }.getOrElse { ToolStatus.OK },
                )
            }
            items += ChatItem(
                id = io.optString("id"),
                kind = runCatching { ItemKind.valueOf(io.optString("kind")) }.getOrElse { ItemKind.ASSISTANT },
                text = io.optString("text"),
                tool = tool,
                ts = io.optLong("ts"),
            )
        }
        return SessionMeta(
            id = o.optString("id"),
            title = o.optString("title", "Сессия"),
            createdAt = o.optLong("createdAt"),
            updatedAt = o.optLong("updatedAt"),
            items = items,
            apiJson = o.optString("apiJson", "[]"),
            model = o.optString("model", ""),
        )
    }
}

class Prefs(private val app: android.content.Context) {
    private val sp = app.getSharedPreferences("claude_code", android.content.Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v).apply()

    var baseUrl: String
        get() = sp.getString("base_url", "https://api.anthropic.com") ?: "https://api.anthropic.com"
        set(v) = sp.edit().putString("base_url", v).apply()

    var model: String
        get() = sp.getString("model", "claude-sonnet-4-5-20250929") ?: "claude-sonnet-4-5-20250929"
        set(v) = sp.edit().putString("model", v).apply()

    var maxTokens: Int
        get() = sp.getInt("max_tokens", 8000)
        set(v) = sp.edit().putInt("max_tokens", v).apply()

    var autoApprove: Boolean
        get() = sp.getBoolean("auto_approve", false)
        set(v) = sp.edit().putBoolean("auto_approve", v).apply()

    var darkTheme: Boolean
        get() = sp.getBoolean("dark", true)
        set(v) = sp.edit().putBoolean("dark", v).apply()

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    var autoUpdate: Boolean
        get() = sp.getBoolean("auto_update", true)
        set(v) = sp.edit().putBoolean("auto_update", v).apply()

    var repo: String
        get() = sp.getString("repo", Updater.DEFAULT_REPO) ?: Updater.DEFAULT_REPO
        set(v) = sp.edit().putString("repo", v).apply()

    var workspace: String
        get() = sp.getString("workspace", "") ?: ""
        set(v) = sp.edit().putString("workspace", v).apply()
}

/** Общая шина логов: FAB-консоль читает то же, что пишется в файл. */
object LogBus {
    enum class Level { DEBUG, INFO, WARN, ERROR }

    @Immutable
    data class Line(val ts: Long, val level: Level, val tag: String, val msg: String)

    private const val MAX = 800
    private val buffer = mutableStateListOf<Line>()
    val lines: List<Line> get() = buffer
    private var logFile: java.io.File? = null

    fun init(app: android.content.Context) {
        logFile = java.io.File(app.filesDir, "claude-code.log")
    }

    fun d(tag: String, msg: String) = add(Level.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = add(Level.INFO, tag, msg)
    fun w(tag: String, msg: String) = add(Level.WARN, tag, msg)
    fun e(tag: String, msg: String) = add(Level.ERROR, tag, msg)

    fun e(tag: String, msg: String, t: Throwable) =
        add(Level.ERROR, tag, "$msg\n${t.javaClass.simpleName}: ${t.message}\n" + t.stackTraceToString().take(1200))

    private fun add(level: Level, tag: String, msg: String) {
        val line = Line(System.currentTimeMillis(), level, tag, msg)
        buffer.add(line)
        if (buffer.size > MAX) {
            repeat(buffer.size - MAX) { buffer.removeAt(0) }
        }
        val f = logFile
        if (f != null && level != Level.DEBUG) {
            runCatching {
                f.appendText("${android.text.format.DateFormat.format("HH:mm:ss", line.ts)} [${level.name}] $tag: $msg\n")
            }
        }
    }

    fun clear() {
        buffer.clear()
        logFile?.delete()
    }

    fun exportText(): String = buffer.joinToString("\n") { l ->
        val time = android.text.format.DateFormat.format("HH:mm:ss", l.ts)
        "$time [${l.level.name}] ${l.tag}: ${l.msg}"
    }
}
