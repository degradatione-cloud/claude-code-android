package com.degradatione.claudecode

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class PermissionRequest(val toolId: String, val toolName: String, val inputJson: String, val summary: String)

class AppState(private val app: Application) {

    val prefs = Prefs(app)
    val store = SessionStore(app)
    val tools = ToolRunner(prefs, app.filesDir)
    private val client = AnthropicClient(prefs)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    var sessions by mutableStateOf<List<SessionMeta>>(emptyList())
        private set
    var current by mutableStateOf<SessionMeta?>(null)
        private set
    var running by mutableStateOf(false)
        private set
    var pending by mutableStateOf<PermissionRequest?>(null)
        private set
    var usageIn by mutableStateOf(0)
        private set
    var usageOut by mutableStateOf(0)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    enum class Verdict { ALLOW, ALLOW_ALL, DENY }

    private var permissionDeferred: CompletableDeferred<Verdict>? = null

    init {
        LogBus.init(app)
        sessions = store.loadAll()
        LogBus.i("app", "Claude Code Android запущен, сессий: ${sessions.size}")
    }

    fun refreshSessions() {
        sessions = store.loadAll()
    }

    fun newSession(): SessionMeta {
        val s = SessionMeta(
            id = newId(),
            title = "Новая сессия",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            items = emptyList(),
            apiJson = "[]",
            model = prefs.model,
        )
        sessions = listOf(s) + sessions
        current = s
        store.save(s)
        LogBus.i("session", "создана сессия ${s.id}")
        return s
    }

    fun openSession(s: SessionMeta) {
        if (running) stop()
        current = s
    }

    fun deleteSession(s: SessionMeta) {
        store.delete(s.id)
        sessions = sessions.filter { it.id != s.id }
        if (current?.id == s.id) current = sessions.firstOrNull()
    }

    fun answerPermission(v: Verdict) {
        if (v == Verdict.ALLOW_ALL) {
            prefs.autoApprove = true
            LogBus.i("perm", "автоодобрение включено")
        }
        pending = null
        permissionDeferred?.complete(v)
        permissionDeferred = null
    }

    fun stop() {
        job?.cancel()
        job = null
        running = false
        LogBus.w("agent", "остановлено пользователем")
    }

    fun systemPrompt(): String {
        val ws = tools.workspace.absolutePath
        return """
You are Claude Code, Anthropic's official CLI for Claude, running natively on an Android phone inside the Claude Code Android app.

# Environment
- Platform: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT}), device ${android.os.Build.MODEL}
- Working directory: $ws
- Shell: /system/bin/sh (Android toybox; no apt/pip/npm/docker)
- You are on a touch-only device: prefer short answers, avoid wide ASCII tables.

# Tools
Bash, Read, Write, Edit, LS, Glob, Grep run locally on the device. Use them to inspect, create and modify real files in the working directory. Never invent file contents — read first.

# Style
- Be concise and direct. No preamble, no "Great question", no restating the task.
- Answer in the language the user writes in.
- When you change code, briefly say what changed and where; do not dump whole files back unless asked.
- If a tool fails, say so plainly and try a different approach instead of guessing.
""".trimIndent()
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || running) return
        val sess = current ?: newSession()
        val item = ChatItem(newId(), ItemKind.USER, trimmed)
        val items = sess.items + item
        val title = if (sess.items.none { it.kind == ItemKind.USER })
            trimmed.lineSequence().first().take(46) else sess.title
        current = sess.copy(items = items, title = title, updatedAt = System.currentTimeMillis())
        current?.let { store.save(it) }
        job = scope.launch { runLoop(trimmed) }
    }

    private fun pushItems(base: List<ChatItem>) {
        val s = current ?: return
        current = s.copy(items = base, updatedAt = System.currentTimeMillis())
    }

    private suspend fun runLoop(userText: String) {
        running = true
        lastError = null
        usageIn = 0
        usageOut = 0
        try {
            val messages = JSONArray(current?.apiJson ?: "[]")
            messages.put(MsgJson.userMessage(userText))
            var turn = 0
            while (turn < 24) {
                val turnStart = streamAssistantTurn(messages)
                val base = current?.items?.toMutableList() ?: mutableListOf()
                val toolItems = base.filter { it.kind == ItemKind.TOOL }
                if (toolItems.isEmpty()) break

                val results = mutableListOf<Triple<String, String, Boolean>>()
                for (item in toolItems) {
                    val t = item.tool ?: continue
                    val idx = base.indexOfFirst { it.id == item.id }
                    if (idx < 0) continue
                    var verdict = Verdict.ALLOW
                    if (tools.needsPermission(t.name, t.inputJson)) {
                        val req = PermissionRequest(t.id, t.name, t.inputJson, t.summary)
                        pending = req
                        val d = CompletableDeferred<Verdict>()
                        permissionDeferred = d
                        verdict = d.await()
                        pending = null
                    }
                    val currentItem = base[idx]
                    if (verdict == Verdict.DENY) {
                        base[idx] = currentItem.copy(
                            tool = currentItem.tool?.copy(
                                status = ToolStatus.DENIED,
                                output = "Пользователь отклонил вызов",
                            )
                        )
                        pushItems(base)
                        results += Triple(t.id, "Permission denied by user: the user rejected this tool call. Do not retry it; ask what to do instead.", true)
                        continue
                    }
                    base[idx] = currentItem.copy(
                        tool = currentItem.tool?.copy(status = ToolStatus.RUNNING, startedAt = System.currentTimeMillis())
                    )
                    pushItems(base)
                    val (out, isErr) = tools.run(t.name, t.inputJson)
                    base[idx] = base[idx].copy(
                        tool = base[idx].tool?.copy(
                            status = if (isErr) ToolStatus.ERROR else ToolStatus.OK,
                            output = out,
                        )
                    )
                    pushItems(base)
                    results += Triple(t.id, out, isErr)
                }
                messages.put(MsgJson.toolResultMessage(results))
                current = current?.copy(apiJson = messages.toString())
                current?.let { store.save(it) }
                turn++
            }
            current = current?.copy(apiJson = messages.toString())
            current?.let { store.save(it) }
        } catch (ce: CancellationException) {
            val base = current?.items?.toMutableList() ?: mutableListOf()
            for (i in base.indices) {
                if (base[i].streaming || base[i].tool?.status == ToolStatus.RUNNING) {
                    base[i] = base[i].copy(
                        streaming = false,
                        tool = base[i].tool?.copy(status = ToolStatus.ERROR, output = "прервано пользователем"),
                    )
                }
            }
            base += ChatItem(newId(), ItemKind.INFO, "⏹ Остановлено пользователем")
            pushItems(base)
            current?.let { store.save(it) }
            throw ce
        } catch (t: Throwable) {
            LogBus.e("agent", "ошибка хода", t)
            val msg = when (t) {
                is ApiException -> "Ошибка API ${t.code}: ${t.body.take(500)}"
                else -> "${t.javaClass.simpleName}: ${t.message}"
            }
            lastError = msg
            val base = current?.items?.toMutableList() ?: mutableListOf()
            base += ChatItem(newId(), ItemKind.ERROR, msg)
            pushItems(base)
            current?.let { store.save(it) }
        } finally {
            running = false
            pending = null
            permissionDeferred = null
        }
    }

    /** Один стриминговый ответ ассистента: тексты + tool_use. Добавляет сообщение в messages. */
    private suspend fun streamAssistantTurn(messages: JSONArray): Unit {
        val session = current ?: return
        val base = session.items.toMutableList()
        val toolInputs = LinkedHashMap<String, StringBuilder>()
        val turnItems = mutableListOf<ChatItem>()
        var stopReason = ""
        var attempt = 0
        var lastFailure: Throwable? = null

        while (attempt < 3) {
            attempt++
            val sizeBefore = base.size
            toolInputs.clear()
            turnItems.clear()
            try {
                val body = client.buildRequest(
                    model = prefs.model,
                    system = systemPrompt(),
                    messagesJson = messages.toString(),
                )
                val channel = Channel<JSONObject>(Channel.UNLIMITED)
                val producer = scope.launch(Dispatchers.IO) {
                    try {
                        client.stream(
                            body = body,
                            isCancelled = { !isActive },
                        ) { ev -> channel.trySend(ev) }
                    } catch (t: Throwable) {
                        channel.trySend(
                            JSONObject()
                                .put("type", "__failure")
                                .put("message", t.message ?: t.javaClass.simpleName)
                                .put("code", (t as? ApiException)?.code ?: -1)
                                .put("cls", t.javaClass.simpleName)
                        )
                    } finally {
                        channel.close()
                    }
                }

                var lastTextIndex = -1
                var lastToolIndex = -1
                for (ev in channel) {
                    when (ev.optString("type")) {
                        "__failure" -> {
                            val code = ev.optInt("code", -1)
                            val msg = ev.optString("message")
                            throw if (code > 0) ApiException(code, msg) else RuntimeException(msg)
                        }
                        "message_start" -> {
                            val u = ev.optJSONObject("message")?.optJSONObject("usage")
                            usageIn = u?.optInt("input_tokens", 0) ?: 0
                        }
                        "content_block_start" -> {
                            val cb = ev.optJSONObject("content_block") ?: continue
                            when (cb.optString("type")) {
                                "text" -> {
                                    val item = ChatItem(newId(), ItemKind.ASSISTANT, "", streaming = true)
                                    base += item
                                    turnItems += item
                                    lastTextIndex = base.lastIndex
                                    pushItems(base)
                                }
                                "thinking" -> {
                                    val item = ChatItem(newId(), ItemKind.THINKING, "", streaming = true)
                                    base += item
                                    turnItems += item
                                    lastTextIndex = base.lastIndex
                                    pushItems(base)
                                }
                                "tool_use" -> {
                                    val tool = ToolCall(
                                        id = cb.optString("id"),
                                        name = cb.optString("name"),
                                        inputJson = "{}",
                                        status = ToolStatus.PENDING,
                                    )
                                    toolInputs[tool.id] = StringBuilder()
                                    val item = ChatItem(newId(), ItemKind.TOOL, tool = tool)
                                    base += item
                                    turnItems += item
                                    lastToolIndex = base.lastIndex
                                    pushItems(base)
                                }
                            }
                        }
                        "content_block_delta" -> {
                            val d = ev.optJSONObject("delta") ?: continue
                            when (d.optString("type")) {
                                "text_delta", "thinking_delta" -> {
                                    if (lastTextIndex >= 0 && lastTextIndex < base.size) {
                                        val cur = base[lastTextIndex]
                                        base[lastTextIndex] = cur.copy(text = cur.text + d.optString("text"))
                                        pushItems(base)
                                    }
                                }
                                "input_json_delta" -> {
                                    if (lastToolIndex >= 0 && lastToolIndex < base.size) {
                                        val cur = base[lastToolIndex]
                                        val id = cur.tool?.id ?: continue
                                        toolInputs.getOrPut(id) { StringBuilder() }.append(d.optString("partial_json"))
                                    }
                                }
                            }
                        }
                        "content_block_stop" -> {
                            if (lastTextIndex >= 0 && lastTextIndex < base.size && base[lastTextIndex].streaming) {
                                base[lastTextIndex] = base[lastTextIndex].copy(streaming = false)
                            }
                            if (lastToolIndex >= 0 && lastToolIndex < base.size) {
                                val cur = base[lastToolIndex]
                                val id = cur.tool?.id
                                val json = toolInputs[id]?.toString()?.takeIf { it.isNotBlank() } ?: "{}"
                                base[lastToolIndex] = cur.copy(
                                    tool = cur.tool?.copy(
                                        inputJson = json,
                                        summary = tools.summarize(cur.tool.name, json),
                                    )
                                )
                                pushItems(base)
                            }
                        }
                        "message_delta" -> {
                            stopReason = ev.optJSONObject("delta")?.optString("stop_reason").orEmpty()
                            val u = ev.optJSONObject("usage")
                            if (u != null) usageOut = u.optInt("output_tokens", usageOut)
                        }
                        "message_stop" -> Unit
                        "error" -> {
                            val e = ev.optJSONObject("error")
                            throw RuntimeException("${e?.optString("type")}: ${e?.optString("message")}")
                        }
                    }
                }
                producer.join()
                lastFailure = null
                attempt = 3
                val assistant = MsgJson.assistantMessage(turnItems, toolInputs.mapValues { it.value.toString() })
                if ((assistant.optJSONArray("content")?.length() ?: 0) > 0) messages.put(assistant)
                return
            } catch (ce: CancellationException) {
                // убрать недописанные блоки этого захода
                while (base.size > sizeBefore) base.removeAt(base.lastIndex)
                pushItems(base)
                throw ce
            } catch (t: Throwable) {
                lastFailure = t
                while (base.size > sizeBefore) base.removeAt(base.lastIndex)
                pushItems(base)
                val retriable = (t is ApiException && t.code in listOf(408, 429, 500, 502, 503, 504, 529)) ||
                    (t !is ApiException && attempt < 3)
                LogBus.w("agent", "попытка $attempt не удалась: ${t.message}")
                if (!retriable || attempt >= 3) throw t
                delay(2500L * attempt)
            }
        }
        lastFailure?.let { throw it }
    }

    fun clearLogs() = LogBus.clear()

    fun checkUpdateNow(onResult: (Updater.Release?) -> Unit) {
        scope.launch {
            val r = Updater.check(prefs.repo)
            onResult(r)
        }
    }

    fun downloadUpdate(release: Updater.Release, onProgress: (Int) -> Unit, onDone: (java.io.File?) -> Unit) {
        scope.launch {
            try {
                val f = Updater.download(app, release) { pct, _ -> onProgress(pct) }
                onDone(f)
            } catch (t: Throwable) {
                LogBus.e("update", "скачивание не удалось: ${t.message}")
                onDone(null)
            }
        }
    }

    fun installApk(file: java.io.File) = Updater.install(app, file)

    fun listWorkspaceFiles(): List<java.io.File> {
        val ws = tools.workspace
        return ws.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
    }

    suspend fun readWorkspaceFile(f: java.io.File): String = withContext(Dispatchers.IO) {
        if (!f.exists() || f.isDirectory) return@withContext ""
        if (f.length() > 1_500_000) return@withContext "(файл слишком большой: ${f.length()} байт)"
        runCatching { f.readText() }.getOrElse { "Не читается: ${it.message}" }
    }
}
