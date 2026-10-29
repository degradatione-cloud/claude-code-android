package com.degradatione.claudecode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashCatcher()
        enableEdgeToEdge()
        val state = AppState(application)
        val crash = readCrash()
        setContent {
            ClaudeTheme(dark = state.prefs.darkTheme) {
                // краш прошлого запуска показываем вместо UI: логи читать не надо
                var crashText by remember { mutableStateOf(crash) }
                var onboarded by remember { mutableStateOf(state.prefs.onboarded) }
                if (crashText != null) {
                    CrashScreen(crashText ?: "") {
                        clearCrash()
                        crashText = null
                    }
                } else if (!onboarded) {
                    OnboardingScreen(state) { onboarded = true }
                } else {
                    MainScreen(state)
                }
            }
        }
    }

    private fun installCrashCatcher() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                java.io.File(filesDir, "last_crash.txt").writeText(
                    "time: " + java.util.Date() + "\nthread: " + thread.name + "\n\n" +
                        android.util.Log.getStackTraceString(error)
                )
            }
            prev?.uncaughtException(thread, error)
        }
    }

    private fun readCrash(): String? {
        val f = java.io.File(filesDir, "last_crash.txt")
        if (!f.exists()) return null
        return runCatching { f.readText() }.getOrNull()
    }

    private fun clearCrash() {
        runCatching { java.io.File(filesDir, "last_crash.txt").delete() }
    }
}

fun modeLabel(prefs: Prefs): String =
    if (prefs.mode == "ssh") "ssh · " + prefs.sshHost.ifBlank { "сервер" }.take(22)
    else shortModel(prefs.model)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(state: AppState) {
    val p = LocalClaude.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var input by rememberSaveable { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showFiles by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<Updater.Release?>(null) }
    var readyApk by remember { mutableStateOf<java.io.File?>(null) }
    var pct by remember { mutableIntStateOf(-1) }
    var checking by remember { mutableStateOf(false) }

    LogsBridge.open = { showLogs = true }

    val currentVersion = Updater.installedVersionCode(LocalContext.current)

    fun checkUpdates(manual: Boolean) {
        checking = true
        state.checkUpdateNow { r ->
            checking = false
            if (r != null && (manual || r.versionCode > currentVersion)) {
                update = r
                if (manual && r.versionCode <= currentVersion) {
                    LogBus.i("update", "установлена последняя версия (${r.tag})")
                }
            } else if (manual) {
                LogBus.w("update", "релизов не найдено")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (state.prefs.autoUpdate) checkUpdates(false)
    }

    fun sendNow() {
        val text = input.trim()
        if (text.isEmpty()) return
        when {
            text == "/new" || text == "/clear" -> {
                state.newSession(); input = ""
            }
            text == "/logs" -> {
                showLogs = true; input = ""
            }
            text == "/files" -> {
                showFiles = true; input = ""
            }
            text == "/settings" -> {
                showSettings = true; input = ""
            }
            text == "/update" -> {
                checkUpdates(true); input = ""
            }
            text.startsWith("/model ") -> {
                state.prefs.model = text.removePrefix("/model ").trim()
                LogBus.i("cmd", "модель → ${state.prefs.model}")
                input = ""
            }
            else -> {
                state.send(text); input = ""
            }
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = p.sidebar,
                drawerContentColor = p.text,
                modifier = Modifier.width(320.dp),
            ) {
                SessionsSheet(
                    state = state,
                    onOpen = { s ->
                        state.openSession(s)
                        scope.launch { drawerState.close() }
                    },
                    onNew = {
                        state.newSession()
                        scope.launch { drawerState.close() }
                    },
                    onSettings = {
                        scope.launch { drawerState.close() }
                        showSettings = true
                    },
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize().background(p.page)) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                TopBar(
                    title = state.current?.title ?: "Claude Code",
                    subtitle = modeLabel(state.prefs),
                    onMenu = { scope.launch { drawerState.open() } },
                    onMore = { showSettings = true },
                )
                update?.let { u ->
                    UpdateBanner(
                        release = u,
                        pct = pct,
                        ready = readyApk != null,
                        onUpdate = {
                            val apk = readyApk
                            if (apk != null) {
                                state.installApk(apk)
                            } else {
                                pct = 0
                                state.downloadUpdate(
                                    release = u,
                                    onProgress = { pct = it },
                                    onDone = { f -> readyApk = f; if (f == null) pct = -1 },
                                )
                            }
                        },
                        onDismiss = { update = null; pct = -1; readyApk = null },
                    )
                }
                Box(Modifier.weight(1f)) {
                    MessageList(state)
                    LogFab(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = 10.dp),
                    ) { showLogs = true }
                }
                if (state.running) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiniSpinner(11.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Claude работает…",
                            style = MaterialTheme.typography.labelSmall,
                            color = p.textTertiary,
                        )
                        Spacer(Modifier.weight(1f))
                        if (state.usageOut > 0) {
                            Text(
                                "out ${state.usageOut} tok",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                                color = p.textTertiary,
                            )
                        }
                    }
                }
                Composer(
                    value = input,
                    running = state.running,
                    modelName = modeLabel(state.prefs),
                    autoApprove = state.prefs.autoApprove,
                    onValueChange = { input = it },
                    onSend = { sendNow() },
                    onStop = { state.stop() },
                    onModelClick = { showSettings = true },
                    onFilesClick = { showFiles = true },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            state.pending?.let { req -> PermissionDialog(state, req) }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = p.sidebar,
            contentColor = p.text,
            dragHandle = {
                Box(
                    Modifier
                        .padding(vertical = 10.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(p.borderStrong, CircleShape)
                )
            },
        ) {
            SettingsSheet(
                state = state,
                onClose = { showSettings = false },
                onCheckUpdate = { checkUpdates(true) },
            )
        }
    }

    if (showFiles) {
        ModalBottomSheet(
            onDismissRequest = { showFiles = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = p.sidebar,
            contentColor = p.text,
        ) {
            FilesSheet(state, onClose = { showFiles = false })
        }
    }

    if (showLogs) {
        ModalBottomSheet(
            onDismissRequest = { showLogs = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = p.sidebar,
            contentColor = p.text,
        ) {
            LogsSheet(state, onClose = { showLogs = false })
        }
    }
}

fun shortModel(m: String): String = m
    .replace("claude-", "")
    .replace(Regex("-\\d{8}$"), "")

@Composable
fun TopBar(title: String, subtitle: String, onMenu: () -> Unit, onMore: () -> Unit) {
    val p = LocalClaude.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTouch(Icons.Rounded.Menu, "Сессии", p.textSecondary, 21.dp, onClick = onMenu)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = p.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = p.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconTouch(Icons.Rounded.MoreVert, "Ещё", p.textSecondary, 21.dp, onClick = onMore)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.border))
}

@Composable
fun MessageList(state: AppState) {
    val p = LocalClaude.current
    val listState = rememberLazyListState()
    val listItems = state.current?.items ?: emptyList()
    val lastTextLen = listItems.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(listItems.size, lastTextLen) {
        if (listItems.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(listItems.lastIndex) }
        }
    }
    if (listItems.isEmpty()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 14.dp)) {
            item { EmptyState { example -> state.send(example) } }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(listItems, key = { it.id }) { item ->
                ChatItemView(item)
            }
        }
    }
}

@Composable
fun LogFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalClaude.current
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    val errorCount = LogBus.lines.count { it.level == LogBus.Level.ERROR }
    Box(
        modifier
            .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    dx = (dx + drag.x).coerceIn(-260f, 0f)
                    dy = (dy + drag.y).coerceIn(-720f, 60f)
                }
            }
            .size(50.dp)
            .background(p.cardAlt, CircleShape)
            .border(1.dp, p.borderStrong, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.BugReport,
            "Логи",
            tint = p.textSecondary,
            modifier = Modifier.size(21.dp),
        )
        if (errorCount > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(9.dp)
                    .background(p.danger, CircleShape)
                    .border(1.5.dp, p.cardAlt, CircleShape)
            )
        }
    }
}

@Composable
fun UpdateBanner(
    release: Updater.Release,
    pct: Int,
    ready: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalClaude.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(p.accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .border(1.dp, p.accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.SystemUpdate, null, tint = p.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (ready) "Готово: ${release.tag}" else "Доступно обновление ${release.tag}",
                style = MaterialTheme.typography.labelLarge,
                color = p.text,
            )
            Text(
                when {
                    pct > 0 && !ready -> "качаю… $pct%"
                    ready -> "нажми «Установить»"
                    else -> "скачать и поставить поверх"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = p.textTertiary,
            )
        }
        Box(
            Modifier
                .background(p.accent, RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onUpdate,
                )
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                if (ready) "Установить" else "Обновить",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = p.onAccent,
            )
        }
        Spacer(Modifier.width(4.dp))
        IconTouch(Icons.Rounded.MoreVert, "Скрыть", p.textTertiary, 16.dp, onClick = onDismiss)
    }
}

@Composable
fun OnboardingScreen(state: AppState, onDone: () -> Unit) {
    val p = LocalClaude.current
    val prefs = state.prefs
    var mode by remember { mutableStateOf(prefs.mode) }
    var key by remember { mutableStateOf(prefs.apiKey) }
    var base by remember { mutableStateOf(prefs.baseUrl) }
    var model by remember { mutableStateOf(prefs.model) }
    var host by remember { mutableStateOf(prefs.sshHost) }
    var user by remember { mutableStateOf(prefs.sshUser) }
    var pass by remember { mutableStateOf(prefs.sshPassword) }
    var ws by remember { mutableStateOf(prefs.sshWorkspace) }
    var testing by remember { mutableStateOf(false) }
    var testOut by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.page)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 28.dp),
    ) {
        ClaudeMark(46.dp)
        Spacer(Modifier.height(16.dp))
        Text("Claude Code", style = MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp), color = p.text)
        Text(
            "Десктопный Claude Code на Android. Два режима: напрямую через API или через свой сервер по SSH — тогда ключи вообще не нужны.",
            style = MaterialTheme.typography.bodyMedium,
            color = p.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(22.dp))
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
        Spacer(Modifier.height(16.dp))

        if (mode == "api") {
            ClaudeTextField(
                value = key,
                onValueChange = { key = it; prefs.apiKey = it },
                label = "API-ключ Anthropic",
                placeholder = "sk-ant-api03-…",
                mono = true,
            )
            Spacer(Modifier.height(12.dp))
            ClaudeTextField(
                value = base,
                onValueChange = { base = it; prefs.baseUrl = it },
                label = "Base URL",
                placeholder = "https://api.anthropic.com",
                mono = true,
            )
            Spacer(Modifier.height(12.dp))
            ClaudeTextField(
                value = model,
                onValueChange = { model = it; prefs.model = it },
                label = "Модель",
                mono = true,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Ключ хранится только на устройстве. Подойдёт токен из `claude setup-token` и любой совместимый relay.",
                style = MaterialTheme.typography.labelSmall,
                color = p.textTertiary,
            )
        } else {
            ClaudeTextField(
                value = host,
                onValueChange = { host = it; prefs.sshHost = it },
                label = "Хост или IP сервера",
                placeholder = "2.26.99.238",
                mono = true,
            )
            Spacer(Modifier.height(12.dp))
            ClaudeTextField(
                value = user,
                onValueChange = { user = it; prefs.sshUser = it },
                label = "Пользователь",
                mono = true,
            )
            Spacer(Modifier.height(12.dp))
            ClaudeTextField(
                value = pass,
                onValueChange = { pass = it; prefs.sshPassword = it },
                label = "Пароль (или подключи ключ в настройках)",
                mono = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            )
            Spacer(Modifier.height(12.dp))
            ClaudeTextField(
                value = ws,
                onValueChange = { ws = it; prefs.sshWorkspace = it },
                label = "Рабочая папка на сервере",
                placeholder = "~",
                mono = true,
            )
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
            Spacer(Modifier.height(8.dp))
            Text(
                "На сервере должен быть установлен и залогинен claude CLI (`claude --version`).",
                style = MaterialTheme.typography.labelSmall,
                color = p.textTertiary,
            )
        }

        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("Начать", enabled = if (mode == "api") key.isNotBlank() else host.isNotBlank()) {
                prefs.mode = mode
                prefs.onboarded = true
                onDone()
            }
            GhostButton("Пропустить") {
                prefs.mode = mode
                prefs.onboarded = true
                onDone()
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "build ${Updater.installedVersionCode(androidx.compose.ui.platform.LocalContext.current)} · ${prefs.repo}",
            style = MaterialTheme.typography.labelSmall,
            color = p.textTertiary,
        )
    }
}

@Composable
fun CrashScreen(text: String, onContinue: () -> Unit) {
    val p = LocalClaude.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxSize()
            .background(p.page)
            .systemBarsPadding()
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClaudeMark(22.dp)
            Spacer(Modifier.width(8.dp))
            Text("Прошлый запуск упал", style = MaterialTheme.typography.titleMedium, color = p.text)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(p.codeBg, RoundedCornerShape(10.dp))
                .border(1.dp, p.border, RoundedCornerShape(10.dp))
                .verticalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            SelectionContainer {
                Text(
                    text,
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 10.5.sp, lineHeight = 15.sp),
                    color = p.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Продолжить") { onContinue() }
            GhostButton("Скопировать") { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)) }
        }
    }
}
