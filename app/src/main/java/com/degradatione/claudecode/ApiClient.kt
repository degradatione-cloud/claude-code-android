package com.degradatione.claudecode

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val code: Int, val body: String) : Exception("HTTP $code: ${body.take(400)}")

/** Не настроено окружение (нет ключа/хоста) — повторять бессмысленно. */
class MissingConfigException(message: String) : Exception(message)

/**
 * Потоковый клиент Anthropic Messages API (SSE).
 * Работает и с официальным api.anthropic.com, и с любым совместимым relay'ем.
 */
class AnthropicClient(private val prefs: Prefs) {

    fun buildRequest(
        model: String,
        system: String,
        messagesJson: String,
        tools: Boolean = true,
        maxTokens: Int = prefs.maxTokens,
    ): JSONObject {
        val body = JSONObject()
        body.put("model", model)
        body.put("max_tokens", maxTokens)
        body.put("stream", true)
        if (system.isNotBlank()) body.put("system", system)
        body.put("messages", org.json.JSONArray(messagesJson))
        if (tools) body.put("tools", ToolRunner.toolSpecs())
        return body
    }

    /** Блокирующий вызов — дергать с Dispatchers.IO. onEvent получает каждый SSE-json. */
    fun stream(
        body: JSONObject,
        isCancelled: () -> Boolean,
        onEvent: (JSONObject) -> Unit,
    ) {
        val base = prefs.baseUrl.trim().trimEnd('/').ifBlank { "https://api.anthropic.com" }
        val urlStr = when {
            base.endsWith("/v1/messages") -> base
            base.endsWith("/v1") -> "$base/messages"
            else -> "$base/v1/messages"
        }
        val key = prefs.apiKey.trim()
        if (key.isEmpty()) throw MissingConfigException("API-ключ не задан — открой Настройки")
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 600_000
            doInput = true
            doOutput = true
            setRequestProperty("content-type", "application/json")
            setRequestProperty("accept", "text/event-stream")
            setRequestProperty("anthropic-version", "2023-06-01")
            if (key.startsWith("sk-ant-oat") || key.startsWith("sk-ant-sid")) {
                setRequestProperty("authorization", "Bearer $key")
                setRequestProperty("anthropic-beta", "oauth-2025-04-20")
            } else {
                setRequestProperty("x-api-key", key)
            }
            setRequestProperty("user-agent", "claude-code-android/1.0")
        }
        LogBus.i("api", "POST $urlStr model=${body.optString("model")} tokens=${body.optInt("max_tokens")}")
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = runCatching {
                    (conn.errorStream?.bufferedReader() ?: conn.inputStream.bufferedReader()).readText()
                }.getOrDefault("")
                LogBus.e("api", "HTTP $code: ${err.take(600)}")
                throw ApiException(code, err)
            }
            val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
            reader.use { r ->
                var data = StringBuilder()
                while (true) {
                    if (isCancelled()) {
                        LogBus.w("api", "поток отменён клиентом")
                        break
                    }
                    val line = r.readLine() ?: break
                    when {
                        line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
                        line.isEmpty() -> {
                            if (data.isNotEmpty()) {
                                val payload = data.toString()
                                data = StringBuilder()
                                if (payload != "[DONE]") {
                                    runCatching { onEvent(JSONObject(payload)) }
                                        .onFailure { LogBus.e("api", "битый SSE-чанк: ${payload.take(200)}") }
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            runCatching { conn.disconnect() }
        }
    }
}
