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
            if (host.isEmpty()) throw IllegalStateException("Не задан SSH-хост (Настройки → режим «Сервер по SSH»)")
            val user = prefs.sshUser.trim().ifBlank { "root" }
            val port = prefs.sshPort.coerceIn(1, 65535)
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
            var last: com.jcraft.jsch.JSchException? = null
            // 0) штатные алгоритмы jsch (современный OpenSSH)
            // 1) совсем без настроек
            // 2) legacy-набор для древних серверов
            // kex НЕ переопределяем частично: jsch берёт список целиком, и клиент
            // начинает предлагать только sha1 → «Algorithm negotiation fail».
            for (mode in listOf(0, 1, 2)) {
                val s = jsch.getSession(user, host, port)
                if (prefs.sshPassword.isNotEmpty()) s.setPassword(prefs.sshPassword)
                val cfg = java.util.Properties()
                cfg["StrictHostKeyChecking"] = "no"
                if (mode == 0) {
                    cfg["PreferredAuthentications"] =
                        if (prefs.sshAuth == "key") "publickey,password,keyboard-interactive"
                        else "password,publickey,keyboard-interactive"
                } else if (mode == 2) {
                    cfg["PreferredAuthentications"] = "publickey,password,keyboard-interactive"
                    cfg["server_host_key"] = LEGACY_HOSTKEY
                    cfg["PubkeyAcceptedAlgorithms"] = LEGACY_PUBKEY
                    cfg["kex"] = LEGACY_KEX
                }
                s.setConfig(cfg)
                s.timeout = 60_000
                try {
                    s.connect(20_000)
                    LogBus.i("ssh", "подключено к $user@$host:$port (профиль $mode)")
                    session = s
                    return s
                } catch (e: com.jcraft.jsch.JSchException) {
                    last = e
                    runCatching { s.disconnect() }
                    val raw = e.message ?: ""
                    LogBus.w("ssh", "профиль $mode: $raw")
                    val negotiation = raw.contains("Algorithm negotiation fail", true) ||
                        raw.contains("no matching", true) ||
                        raw.contains("No common", true)
                    val authFail = raw.contains("Auth fail", true) || raw.contains("Auth cancel", true)
                    if (authFail) break
                    if (!negotiation) break
                }
            }
            val raw = last?.message ?: "неизвестная ошибка"
            val hint = when {
                raw.contains("Auth fail", true) || raw.contains("Auth cancel", true) ->
                    "Сервер отклонил логин: неверный пароль/ключ, либо вход только по ключу, либо другой пользователь."
                raw.contains("Connection refused", true) -> "Порт закрыт: на $host:$port никто не слушает."
                raw.contains("timeout", true) || raw.contains("timed out", true) ->
                    "Нет ответа от $host:$port — таймаут. Проверь IP и фаервол."
                raw.contains("Algorithm negotiation fail", true) ->
                    "Сервер и телефон не сошлись по шифрованию даже с legacy-набором. Пришли этот лог — добавлю нужный алгоритм."
                else -> "SSH: $raw"
            }
            LogBus.e("ssh", "connect: $raw")
            throw IllegalStateException(hint)
        }
    }

    companion object {
        private const val LEGACY_KEX =
            "curve25519-sha256,curve25519-sha256@libssh.org,ecdh-sha2-nistp256,ecdh-sha2-nistp384," +
                "ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256,diffie-hellman-group16-sha512," +
                "diffie-hellman-group18-sha512,diffie-hellman-group14-sha256,diffie-hellman-group14-sha1," +
                "diffie-hellman-group1-sha1"
        private const val LEGACY_HOSTKEY =
            "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521," +
                "rsa-sha2-512,rsa-sha2-256,ssh-rsa,ssh-dss"
        private const val LEGACY_PUBKEY =
            "ssh-ed25519,ecdsa-sha2-nistp256,rsa-sha2-512,rsa-sha2-256,ssh-rsa"
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
        "echo \"host=\$(hostname)\"; echo \"whoami=\$(whoami)\"; echo \"pwd=\$(pwd)\"; " +
            "C=\$(command -v claude 2>/dev/null); " +
            "if [ -z \"\$C\" ]; then for p in \$HOME/.local/bin/claude /usr/local/bin/claude " +
            "\$HOME/.npm-global/bin/claude \$HOME/.bun/bin/claude; do [ -x \"\$p\" ] && C=\$p && break; done; fi; " +
            "echo \"claude=\${C:-НЕ НАЙДЕН}\"; " +
            "if [ -n \"\$C\" ]; then \"\$C\" --version 2>&1 | head -2; fi"
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
