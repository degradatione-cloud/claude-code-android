package com.degradatione.claudecode

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.BufferedReader

/**
 * SSH-режим: подключаемся к своему серверу и запускаем там настоящий claude CLI.
 * Так не нужны API-ключи — авторизация уже сделана на сервере.
 */
class SshClient(private val prefs: Prefs) {

    private var session: Session? = null
    private val lock = Any()

    private fun open(): Session {
        synchronized(lock) {
            session?.let { if (it.isConnected) return it }
            val host = prefs.sshHost.trim()
            if (host.isEmpty()) throw IllegalStateException("Не задан SSH-хост (Настройки → Сервер)")
            val jsch = JSch()
            if (prefs.sshAuth == "key") {
                val key = prefs.sshKey.trim()
                if (key.isEmpty()) throw IllegalStateException("Не задан приватный ключ")
                val pass = prefs.sshPassphrase
                jsch.addIdentity(
                    "claude-code-key",
                    key.toByteArray(Charsets.UTF_8),
                    null,
                    if (pass.isBlank()) null else pass.toByteArray(Charsets.UTF_8),
                )
            }
            val s = jsch.getSession(prefs.sshUser.trim().ifBlank { "root" }, host, prefs.sshPort.coerceIn(1, 65535))
            if (prefs.sshAuth != "key") s.setPassword(prefs.sshPassword)
            val cfg = java.util.Properties()
            cfg["StrictHostKeyChecking"] = "no"
            cfg["PreferredAuthentications"] = if (prefs.sshAuth == "key") "publickey" else "password,keyboard-interactive,publickey"
            s.setConfig(cfg)
            s.timeout = 60_000
            s.connect(20_000)
            LogBus.i("ssh", "подключено к ${prefs.sshUser}@$host:${prefs.sshPort}")
            session = s
            return s
        }
    }

    /** Выполнить команду и отдать stdout построчно. Блокирующий вызов → Dispatchers.IO. */
    fun execStream(command: String, onLine: (String) -> Unit) {
        val s = open()
        val ch = s.openChannel("exec") as ChannelExec
        try {
            ch.setCommand(command)
            ch.setInputStream(null)
            ch.setErrStream(null)
            val input: BufferedReader
            ch.connect(20_000)
            input = ch.inputStream.bufferedReader()
            input.use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    onLine(line)
                }
            }
        } finally {
            runCatching { ch.disconnect() }
        }
    }

    /** Короткая команда целиком (для проверки подключения). */
    fun exec(command: String, timeoutMs: Long = 25_000): String {
        val s = open()
        val ch = s.openChannel("exec") as ChannelExec
        val sb = StringBuilder()
        try {
            ch.setCommand(command)
            ch.setInputStream(null)
            ch.setErrStream(null)
            ch.connect(15_000)
            val deadline = System.currentTimeMillis() + timeoutMs
            ch.inputStream.bufferedReader().use { r ->
                while (System.currentTimeMillis() < deadline) {
                    if (r.ready()) {
                        val line = r.readLine() ?: break
                        sb.append(line).append('\n')
                    } else if (!ch.isConnected) break else Thread.sleep(30)
                }
            }
        } finally {
            runCatching { ch.disconnect() }
        }
        return sb.toString().trim()
    }

    fun ping(): String = exec(
        "echo \"host=\$(hostname)\"; echo \"whoami=\$(whoami)\"; " +
            "echo \"pwd=\$(pwd)\"; command -v claude || echo 'claude: не найден в PATH'; " +
            "claude --version 2>&1 | head -2"
    )

    fun close() {
        synchronized(lock) {
            runCatching { session?.disconnect() }
            session = null
        }
    }
}

object RemoteCmd {

    /** Экранирование для одинарных кавычек в sh. */
    fun q(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    fun workspaceExpr(ws: String): String {
        val t = ws.trim()
        return when {
            t.isEmpty() -> ""
            t.startsWith("~") -> "\"\$HOME" + t.removePrefix("~").replace("\"", "\\\"") + "\""
            else -> q(t)
        }
    }

    fun build(prefs: Prefs, prompt: String, resumeId: String): String {
        val sb = StringBuilder()
        sb.append(" -p ").append(q(prompt))
        sb.append(" --output-format stream-json --verbose")
        if (resumeId.isNotBlank()) sb.append(" --resume ").append(q(resumeId))
        when (prefs.sshPermMode) {
            "dangerous" -> sb.append(" --dangerously-skip-permissions")
            "plan" -> sb.append(" --permission-mode plan")
            else -> sb.append(" --permission-mode acceptEdits")
        }
        val ws = workspaceExpr(prefs.sshWorkspace)
        val cd = if (ws.isBlank()) "" else "cd $ws && "
        val bin = prefs.sshCommand.trim().ifBlank { "claude" }
        val inner = cd + bin + sb
        return "bash -lc " + q(inner)
    }
}
