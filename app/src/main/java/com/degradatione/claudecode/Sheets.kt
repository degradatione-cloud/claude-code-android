package com.degradatione.claudecode

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SheetHeader(title: String, subtitle: String? = null, onClose: (() -> Unit)? = null) {
    val p = LocalClaude.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = p.text)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = p.textTertiary)
            }
        }
        if (onClose != null) {
            IconTouch(Icons.Rounded.Close, "Закрыть", p.textSecondary, 18.dp, onClick = onClose)
        }
    }
}

@Composable
fun SessionsSheet(
    state: AppState,
    onOpen: (SessionMeta) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
) {
    val p = LocalClaude.current
    var confirmDelete by remember { mutableStateOf<SessionMeta?>(null) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClaudeMark(22.dp)
            Spacer(Modifier.width(8.dp))
            Text("Claude Code", style = MaterialTheme.typography.titleLarge, color = p.text)
            Spacer(Modifier.weight(1f))
            IconTouch(Icons.Rounded.BugReport, "Логи", p.textTertiary, 19.dp) { LogsBridge.open() }
            IconTouch(Icons.Rounded.Settings, "Настройки", p.textTertiary, 19.dp, onClick = onSettings)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .background(p.accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .border(1.dp, p.accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onNew,
                )
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, null, tint = p.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Новый чат", style = MaterialTheme.typography.labelLarge, color = p.accent)
            Spacer(Modifier.weight(1f))
            Text(
                state.tools.workspace.name,
                style = MaterialTheme.typography.labelSmall,
                color = p.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle("недавние", Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(state.sessions, key = { it.id }) { s ->
                val active = state.current?.id == s.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .background(
                            if (active) p.cardAlt else Color.Transparent,
                            RoundedCornerShape(10.dp),
                        )
                        .combinedClickable(
                            onClick = { onOpen(s) },
                            onLongClick = { confirmDelete = s },
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.title.ifBlank { "Без названия" },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = if (active) p.text else p.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${s.model.take(24)} · ${relativeTime(s.updatedAt)} · ${s.items.size} блоков",
                            style = MaterialTheme.typography.labelSmall,
                            color = p.textTertiary,
                            maxLines = 1,
                        )
                    }
                    if (s.items.isNotEmpty()) {
                        IconTouch(Icons.Rounded.Delete, "Удалить", p.textTertiary, 16.dp) { confirmDelete = s }
                    }
                }
            }
            if (state.sessions.isEmpty()) {
                item { Text("Пусто — создай первый чат", color = p.textTertiary, modifier = Modifier.padding(20.dp)) }
            }
        }
    }
    confirmDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Удалить сессию?") },
            text = { Text(s.title, color = p.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    state.deleteSession(s)
                    confirmDelete = null
                }) { Text("Удалить", color = p.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Отмена") } },
            containerColor = p.card,
            titleContentColor = p.text,
            textContentColor = p.textSecondary,
        )
    }
}

@Composable
fun SettingsSheet(
    state: AppState,
    onClose: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    val p = LocalClaude.current
    val prefs = state.prefs
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var key by remember { mutableStateOf(prefs.apiKey) }
    var base by remember { mutableStateOf(prefs.baseUrl) }
    var model by remember { mutableStateOf(prefs.model) }
    var showKey by remember { mutableStateOf(false) }
    var autoApprove by remember { mutableStateOf(prefs.autoApprove) }
    var dark by remember { mutableStateOf(prefs.darkTheme) }
    var autoUpdate by remember { mutableStateOf(prefs.autoUpdate) }
    var maxTokens by remember { mutableStateOf(prefs.maxTokens.toString()) }
    var mode by remember { mutableStateOf(prefs.mode) }
    var sshHost by remember { mutableStateOf(prefs.sshHost) }
    var sshPort by remember { mutableStateOf(prefs.sshPort.toString()) }
    var sshUser by remember { mutableStateOf(prefs.sshUser) }
    var sshAuth by remember { mutableStateOf(prefs.sshAuth) }
    var sshPass by remember { mutableStateOf(prefs.sshPassword) }
    var sshKey by remember { mutableStateOf(prefs.sshKey) }
    var sshPassphrase by remember { mutableStateOf(prefs.sshPassphrase) }
    var sshWs by remember { mutableStateOf(prefs.sshWorkspace) }
    var sshCmd by remember { mutableStateOf(prefs.sshCommand) }
    var sshPerm by remember { mutableStateOf(prefs.sshPermMode) }
    var style by remember { mutableStateOf(prefs.style) }
    var showSshPass by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testOut by remember { mutableStateOf<String?>(null) }

    val models = listOf(
        "claude-sonnet-4-5-20250929",
        "claude-opus-4-1-20250805",
        "claude-haiku-4-5-20251001",
        "sonnet",
        "opus",
        "haiku",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
    ) {
        SheetHeader("Настройки", "ключ, модель, поведение", onClose)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            SectionTitle("режим работы")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(
                    text = "API-ключ",
                    selected = mode == "api",
                    onClick = { mode = "api"; prefs.mode = "api" },
                )
                Chip(
                    text = "Сервер по SSH",
                    selected = mode == "ssh",
                    onClick = { mode = "ssh"; prefs.mode = "ssh" },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (mode == "ssh")
                    "Команды выполняет настоящий claude на твоём сервере — ключи Anthropic не нужны, авторизация уже там."
                else
                    "Запросы идут напрямую в Anthropic (или relay), инструменты работают на телефоне.",
                style = MaterialTheme.typography.labelSmall,
                color = p.textTertiary,
            )

            if (mode == "ssh") {
                Spacer(Modifier.height(14.dp))
                SectionTitle("сервер")
                Spacer(Modifier.height(8.dp))
                ClaudeTextField(
                    value = sshHost,
                    onValueChange = { sshHost = it; prefs.sshHost = it },
                    label = "Хост / IP",
                    placeholder = "ssh.example.com",
                    mono = true,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        ClaudeTextField(
                            value = sshUser,
                            onValueChange = { sshUser = it; prefs.sshUser = it },
                            label = "Пользователь",
                            mono = true,
                        )
                    }
                    Box(Modifier.width(96.dp)) {
                        ClaudeTextField(
                            value = sshPort,
                            onValueChange = { v -> sshPort = v.filter { it.isDigit() }.take(5); prefs.sshPort = sshPort.toIntOrNull() ?: 22 },
                            label = "Порт",
                            mono = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(
                        text = "Пароль",
                        selected = sshAuth == "password",
                        onClick = { sshAuth = "password"; prefs.sshAuth = "password" },
                    )
                    Chip(
                        text = "Приватный ключ",
                        selected = sshAuth == "key",
                        onClick = { sshAuth = "key"; prefs.sshAuth = "key" },
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (sshAuth == "password") {
                    ClaudeTextField(
                        value = sshPass,
                        onValueChange = { sshPass = it; prefs.sshPassword = it },
                        label = "Пароль SSH",
                        mono = true,
                        visualTransformation = if (showSshPass) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            IconTouch(
                                if (showSshPass) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                "Показать пароль", p.textTertiary, 18.dp,
                            ) { showSshPass = !showSshPass }
                        },
                    )
                } else {
                    ClaudeTextField(
                        value = sshKey,
                        onValueChange = { sshKey = it; prefs.sshKey = it },
                        label = "Приватный ключ (PEM/OpenSSH)",
                        placeholder = "-----BEGIN OPENSSH PRIVATE KEY-----",
                        mono = true,
                        singleLine = false,
                    )
                    Spacer(Modifier.height(10.dp))
                    ClaudeTextField(
                        value = sshPassphrase,
                        onValueChange = { sshPassphrase = it; prefs.sshPassphrase = it },
                        label = "Парольная фраза ключа (если есть)",
                        mono = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                Spacer(Modifier.height(10.dp))
                ClaudeTextField(
                    value = sshWs,
                    onValueChange = { sshWs = it; prefs.sshWorkspace = it },
                    label = "Рабочая папка на сервере",
                    placeholder = "~/projects/app",
                    mono = true,
                )
                Spacer(Modifier.height(10.dp))
                ClaudeTextField(
                    value = sshCmd,
                    onValueChange = { sshCmd = it; prefs.sshCommand = it },
                    label = "Команда Claude",
                    placeholder = "claude",
                    mono = true,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(
                        text = "спрашивать",
                        selected = sshPerm == "plan",
                        onClick = { sshPerm = "plan"; prefs.sshPermMode = "plan" },
                    )
                    Chip(
                        text = "правки автоматом",
                        selected = sshPerm == "acceptEdits",
                        onClick = { sshPerm = "acceptEdits"; prefs.sshPermMode = "acceptEdits" },
                    )
                    Chip(
                        text = "всё можно",
                        selected = sshPerm == "dangerous",
                        onClick = { sshPerm = "dangerous"; prefs.sshPermMode = "dangerous" },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton(if (testing) "Проверяю…" else "Проверить SSH") {
                        testing = true
                        testOut = null
                        state.testSsh { ok, text ->
                            testing = false
                            testOut = (if (ok) "✅ " else "❌ ") + text
                        }
                    }
                }
                testOut?.let { out ->
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(p.codeBg, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                out,
                                style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, lineHeight = 15.sp),
                                color = p.textSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            } else {
                Spacer(Modifier.height(18.dp))
            }
            SectionTitle("подключение")
            Spacer(Modifier.height(8.dp))
            ClaudeTextField(
                value = key,
                onValueChange = { key = it; prefs.apiKey = it },
                label = "API-ключ",
                placeholder = "sk-ant-…",
                mono = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    IconTouch(
                        if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        "Показать ключ", p.textTertiary, 18.dp,
                    ) { showKey = !showKey }
                },
            )
            Spacer(Modifier.height(10.dp))
            ClaudeTextField(
                value = base,
                onValueChange = { base = it; prefs.baseUrl = it },
                label = "Base URL",
                placeholder = "https://api.anthropic.com",
                mono = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Text(
                "Подходит и официальный API, и любой совместимый relay. Токен из `claude setup-token` тоже работает.",
                style = MaterialTheme.typography.labelSmall,
                color = p.textTertiary,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(18.dp))
            SectionTitle("модель")
            Spacer(Modifier.height(8.dp))
            ClaudeTextField(
                value = model,
                onValueChange = { model = it; prefs.model = it },
                label = "ID модели",
                mono = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                models.take(3).forEach { m ->
                    Chip(
                        text = modelChipLabel(m),
                        selected = model == m,
                        onClick = { model = m; prefs.model = m },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                models.drop(3).forEach { m ->
                    Chip(
                        text = modelChipLabel(m),
                        selected = model == m,
                        onClick = { model = m; prefs.model = m },
                    )
                }
            

            Spacer(Modifier.height(18.dp))
            SectionTitle("стиль ответа")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "concise" to "Concise",
                    "proactive" to "Proactive",
                ).forEach { (id, label) ->
                    Chip(text = label, selected = style == id, onClick = { style = id; prefs.style = id })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "explanatory" to "Explanatory",
                    "learning" to "Learning",
                ).forEach { (id, label) ->
                    Chip(text = label, selected = style == id, onClick = { style = id; prefs.style = id })
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("поведение")
            Spacer(Modifier.height(4.dp))
            SettingSwitch("Автоодобрение инструментов", "Не спрашивать перед Bash/Edit/Write", autoApprove) {
                autoApprove = it; prefs.autoApprove = it
            }
            SettingSwitch("Тёмная тема", "Светлая — тёплый кремовый вариант Claude", dark) {
                dark = it; prefs.darkTheme = it
            }
            SettingSwitch("Автообновление", "Тихо проверять новые релизы на GitHub", autoUpdate) {
                autoUpdate = it; prefs.autoUpdate = it
            }
            Spacer(Modifier.height(8.dp))
            ClaudeTextField(
                value = maxTokens,
                onValueChange = { v -> maxTokens = v.filter { it.isDigit() }.take(6); prefs.maxTokens = maxTokens.toIntOrNull() ?: 8000 },
                label = "max_tokens",
                mono = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Spacer(Modifier.height(18.dp))
            SectionTitle("рабочая папка")
            Spacer(Modifier.height(8.dp))
            Text(
                state.tools.workspace.absolutePath,
                style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp),
                color = p.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                GhostButton("Копировать путь") {
                    clipboard.setText(AnnotatedString(state.tools.workspace.absolutePath))
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionTitle("обновления")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Проверить обновления", onClick = onCheckUpdate)
            }
            Spacer(Modifier.height(18.dp))
            SectionTitle("логи")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Открыть консоль") { LogsBridge.open() }
                GhostButton("Поделиться") {
                    val text = LogBus.exportText()
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text.take(90000))
                    }
                    ctx.startActivity(Intent.createChooser(i, "Логи Claude Code"))
                }
            }

            Spacer(Modifier.height(24.dp))
            val vc = Updater.installedVersionCode(ctx)
            Text(
                "Claude Code for Android · ${prefs.repo} · build $vc",
                style = MaterialTheme.typography.labelSmall,
                color = p.textTertiary,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalClaude.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = p.text)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = p.textTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.onAccent,
                checkedTrackColor = p.accent,
                uncheckedThumbColor = p.textSecondary,
                uncheckedTrackColor = p.cardAlt,
                uncheckedBorderColor = p.border,
            ),
        )
    }
}

@Composable
fun FilesSheet(state: AppState, onClose: () -> Unit) {
    val p = LocalClaude.current
    var files by remember { mutableStateOf(state.listWorkspaceFiles()) }
    var preview by remember { mutableStateOf<Pair<String, String>?>(null) }
    var previewName by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f)) {
        SheetHeader("Рабочая папка", state.tools.workspace.absolutePath, onClose)
        Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Обновить") { files = state.listWorkspaceFiles() }
            GhostButton("Логи") { LogsBridge.open() }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(files, key = { it.absolutePath }) { f ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (f.isDirectory) {
                                LogBus.i("files", "папка ${f.name} — только просмотр корня")
                            } else {
                                previewName = f.name
                                preview = f.name to "загрузка…"
                                scope.launch {
                                    val text = state.readWorkspaceFile(f)
                                    preview = f.name to text
                                }
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (f.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Description,
                        null,
                        tint = if (f.isDirectory) p.accent else p.textTertiary,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        f.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = p.text,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (f.isDirectory) "${f.listFiles()?.size ?: 0}" else humanBytes(f.length()),
                        style = MaterialTheme.typography.labelSmall,
                        color = p.textTertiary,
                    )
                }
            }
            if (files.isEmpty()) {
                item {
                    Text(
                        "Папка пуста. Claude создаст файлы, как только попросишь.",
                        color = p.textTertiary,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }
    preview?.let { (_, text) ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(previewName, style = MaterialTheme.typography.titleSmall, color = p.text) },
            text = {
                Box(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(
                            text.take(20000),
                            style = TextStyle(fontFamily = MonoFamily, fontSize = 11.5.sp, lineHeight = 16.sp),
                            color = p.textSecondary,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { preview = null }) { Text("Закрыть") } },
            containerColor = p.card,
            titleContentColor = p.text,
            textContentColor = p.textSecondary,
        )
    }
}

fun humanBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> String.format("%.1f MB", b / 1048576.0)
}

/** Единая точка открытия консоли логов из любого места UI. */
object LogsBridge {
    var open: () -> Unit = {}
}

@Composable
fun LogsSheet(state: AppState, onClose: () -> Unit) {
    val p = LocalClaude.current
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var minLevel by remember { mutableStateOf(LogBus.Level.DEBUG) }
    val lines = LogBus.lines.filter { it.level.ordinal >= minLevel.ordinal }
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {
        SheetHeader("Консоль", "живые логи приложения и агента", onClose)
        Row(
            Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LogBus.Level.entries.forEach { lvl ->
                Chip(
                    text = lvl.name,
                    selected = minLevel == lvl,
                    onClick = { minLevel = lvl },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GhostButton("Копировать") {
                clipboard.setText(AnnotatedString(LogBus.exportText()))
            }
            GhostButton("Поделиться") {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, LogBus.exportText().take(90000))
                }
                ctx.startActivity(Intent.createChooser(i, "Логи Claude Code"))
            }
            GhostButton("Очистить") { LogBus.clear() }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(p.codeBg, RoundedCornerShape(10.dp))
                .border(1.dp, p.border, RoundedCornerShape(10.dp))
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(lines) { line ->
                    val color = when (line.level) {
                        LogBus.Level.ERROR -> p.danger
                        LogBus.Level.WARN -> p.warn
                        LogBus.Level.INFO -> p.textSecondary
                        LogBus.Level.DEBUG -> p.textTertiary
                    }
                    Text(
                        text = "${android.text.format.DateFormat.format("HH:mm:ss", line.ts)} ${line.level.name.take(1)} ${line.tag}: ${line.msg}",
                        style = TextStyle(fontFamily = MonoFamily, fontSize = 10.5.sp, lineHeight = 14.sp),
                        color = color,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
                if (lines.isEmpty()) {
                    item { Text("Логов пока нет", color = p.textTertiary, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun PermissionDialog(state: AppState, req: PermissionRequest) {
    val p = LocalClaude.current

    @Composable
    fun option(index: String, title: String, tint: Color, onClick: () -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .background(p.codeBg, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    index,
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 11.5.sp),
                    color = p.textTertiary,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                style = TextStyle(fontFamily = MonoFamily, fontSize = 13.sp, lineHeight = 18.sp),
                color = tint,
            )
        }
    }

    AlertDialog(
        onDismissRequest = { state.answerPermission(AppState.Verdict.DENY) },
        containerColor = p.card,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "●",
                        style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp),
                        color = p.accent,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        req.toolName + "(" + req.summary + ")",
                        style = TextStyle(fontFamily = MonoFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = p.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "инструмент выполнится на устройстве",
                    style = MaterialTheme.typography.labelSmall,
                    color = p.textTertiary,
                )
            }
        },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .background(p.codeBg, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        reqPreview(req),
                        style = TextStyle(fontFamily = MonoFamily, fontSize = 11.5.sp, lineHeight = 16.sp),
                        color = p.text,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Do you want to proceed?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = p.textSecondary,
                )
                option("1", "Yes", p.text) { state.answerPermission(AppState.Verdict.ALLOW) }
                option("2", "Yes, и больше не спрашивать «${req.toolName}»", p.textSecondary) {
                    state.answerPermission(AppState.Verdict.ALLOW_ALL)
                }
                option("3", "No, и объяснить Claude, что делать иначе", p.danger) {
                    state.answerPermission(AppState.Verdict.DENY)
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

private fun reqPreview(req: PermissionRequest): String {
    val o = runCatching { org.json.JSONObject(req.inputJson) }.getOrNull()
    return when (req.toolName) {
        "Bash" -> "$ " + (o?.optString("command") ?: req.summary)
        "Edit" -> "файл: ${o?.optString("file_path")}\n\n− ${o?.optString("old_string")?.take(400)}\n\n+ ${o?.optString("new_string")?.take(400)}"
        "Write" -> "файл: ${o?.optString("file_path")}\n${o?.optString("content")?.length ?: 0} символов"
        else -> req.inputJson.take(800)
    }
}

/** Подпись чипа модели: `*` — алиас Anthropic (всегда последняя версия семейства). */
internal fun modelChipLabel(m: String): String = when (m) {
    "sonnet", "opus", "haiku" -> "$m*"
    else -> m.removePrefix("claude-").replace(Regex("-\\d{8}$"), "")
}
