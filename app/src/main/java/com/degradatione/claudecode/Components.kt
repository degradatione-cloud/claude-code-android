package com.degradatione.claudecode

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
fun IconTouch(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    size: Dp = 20.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
fun ClaudeMark(size: Dp = 20.dp, color: Color? = null) {
    val p = LocalClaude.current
    Text(
        text = "✳",
        style = TextStyle(fontSize = size.value.sp, fontFamily = ClaudeSerif),
        color = color ?: p.accent,
    )
}

@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val p = LocalClaude.current
    Row(
        modifier
            .background(
                if (selected) p.accent.copy(alpha = 0.16f) else p.cardAlt,
                RoundedCornerShape(20.dp),
            )
            .border(
                1.dp,
                if (selected) p.accent.copy(alpha = 0.5f) else p.border,
                RoundedCornerShape(20.dp),
            )
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ) else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) p.accent else p.textSecondary, modifier = Modifier.size(14.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) p.accent else p.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

private fun statusColor(p: ClaudePalette, s: ToolStatus): Color = when (s) {
    ToolStatus.OK -> p.success
    ToolStatus.ERROR -> p.danger
    ToolStatus.DENIED -> p.warn
    ToolStatus.RUNNING -> p.accent
    ToolStatus.PENDING -> p.textTertiary
}

/** Глифы как в настоящем Claude Code: ● в работе, ✓ готово, ✗ ошибка, ⊘ отказ. */
private fun glyphOf(s: ToolStatus): String = when (s) {
    ToolStatus.RUNNING -> "●"
    ToolStatus.OK -> "✓"
    ToolStatus.ERROR -> "✗"
    ToolStatus.DENIED -> "⊘"
    ToolStatus.PENDING -> "○"
}

/**
 * Строка инструмента в стиле CLI: `● Bash(git status)` + отступ `⎿` для вывода,
 * свёрнутый результат, разворот по тапу. Никаких карточек-коробок.
 */
@Composable
fun ToolCard(tool: ToolCall, modifier: Modifier = Modifier) {
    val p = LocalClaude.current
    var expanded by remember(tool.id) { mutableStateOf(tool.status == ToolStatus.ERROR) }
    val args = remember(tool.name, tool.inputJson) { argSummary(tool.name, tool.inputJson) }
    val out = tool.output.orEmpty().trimEnd()
    val lines = remember(out) { if (out.isEmpty()) emptyList() else out.lines() }
    val tint = statusColor(p, tool.status)
    Column(
        modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { expanded = !expanded }
            .padding(vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                glyphOf(tool.status),
                style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, lineHeight = 18.sp),
                color = tint,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                tool.name,
                style = TextStyle(fontFamily = MonoFamily, fontSize = 12.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                color = p.text,
            )
            if (args.isNotBlank()) {
                Text(
                    "($args)",
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 12.5.sp, lineHeight = 18.sp),
                    color = p.textSecondary,
                    maxLines = if (expanded) 6 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (tool.status == ToolStatus.RUNNING) {
                Spacer(Modifier.width(6.dp))
                MiniSpinner(10.dp)
            }
        }
        when {
            tool.name == "Edit" || tool.name == "Write" -> if (expanded) {
                Spacer(Modifier.height(6.dp))
                DiffView(tool.name, tool.inputJson)
            }
            lines.isNotEmpty() -> {
                val shown = if (expanded) lines else lines.take(2)
                Column(Modifier.padding(top = 1.dp)) {
                    shown.forEachIndexed { i, line ->
                        Row {
                            Text(
                                if (i == 0) "  ⎿  " else "     ",
                                style = TextStyle(fontFamily = MonoFamily, fontSize = 11.5.sp, lineHeight = 16.sp),
                                color = p.textTertiary,
                            )
                            Text(
                                line.ifEmpty { " " },
                                style = TextStyle(fontFamily = MonoFamily, fontSize = 11.5.sp, lineHeight = 16.sp),
                                color = if (tool.status == ToolStatus.ERROR) p.danger else p.textSecondary,
                                maxLines = if (expanded) Int.MAX_VALUE else 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (!expanded && lines.size > shown.size) {
                        Text(
                            "     … +${lines.size - shown.size} строк",
                            style = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp, lineHeight = 15.sp),
                            color = p.textTertiary,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun argSummary(name: String, json: String): String {
    val o = runCatching { JSONObject(json) }.getOrNull() ?: return ""
    return when (name) {
        "Bash" -> o.optString("command")
        "Read", "Write", "Edit" -> shortPath(o.optString("file_path"))
        "LS" -> shortPath(o.optString("path"))
        "Grep" -> o.optString("pattern") + (o.optString("path").takeIf { it.isNotBlank() }?.let { "  $it" } ?: "")
        "Glob" -> o.optString("pattern")
        else -> o.keys().asSequence().take(2).joinToString(", ") { k -> "$k=${o.optString(k).take(40)}" }
    }.replace("\n", " ⏎ ").take(200)
}

private fun shortPath(path: String): String {
    if (path.isBlank()) return ""
    val marker = "/files/claude-work/"
    val idx = path.indexOf(marker)
    return if (idx >= 0) path.substring(idx + marker.length) else path.substringAfterLast('/')
}

@Composable
fun DiffView(toolName: String, inputJson: String) {
    val p = LocalClaude.current
    val o = runCatching { JSONObject(inputJson) }.getOrNull()
    val pairs = remember(inputJson) {
        if (toolName == "Edit" && o != null) {
            diffLines(o.optString("old_string").split('\n'), o.optString("new_string").split('\n'))
        } else if (o != null) {
            o.optString("content").split('\n').map { '+' to it }
        } else emptyList()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .background(p.codeBg, RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp)
    ) {
        pairs.take(400).forEach { (sign, line) ->
            val bg = when (sign) {
                '+' -> p.diffAddBg
                '-' -> p.diffDelBg
                else -> Color.Transparent
            }
            val fg = when (sign) {
                '+' -> p.diffAddText
                '-' -> p.diffDelText
                else -> p.textSecondary
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = if (sign == ' ') " " else sign.toString(),
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 11.5.sp, lineHeight = 16.sp),
                    color = fg,
                    modifier = Modifier.width(12.dp),
                )
                Text(
                    text = line.ifEmpty { " " },
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 11.5.sp, lineHeight = 16.sp),
                    color = fg,
                )
            }
        }
        if (pairs.size > 400) {
            Text(
                "… диф обрезан",
                style = MaterialTheme.typography.labelSmall,
                color = p.textTertiary,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Простейший построчный диф (LCS) — хватает для правок Claude Code. */
fun diffLines(old: List<String>, new: List<String>): List<Pair<Char, String>> {
    val a = old.take(600)
    val b = new.take(600)
    if (a.size * b.size > 250_000) {
        return a.map { '-' to it } + b.map { '+' to it }
    }
    val dp = Array(a.size + 1) { IntArray(b.size + 1) }
    for (i in a.indices.reversed()) {
        for (j in b.indices.reversed()) {
            dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
    }
    val out = mutableListOf<Pair<Char, String>>()
    var i = 0
    var j = 0
    while (i < a.size && j < b.size) {
        if (a[i] == b[j]) {
            out += ' ' to a[i]; i++; j++
        } else if (dp[i + 1][j] >= dp[i][j + 1]) {
            out += '-' to a[i]; i++
        } else {
            out += '+' to b[j]; j++
        }
    }
    while (i < a.size) {
        out += '-' to a[i]; i++
    }
    while (j < b.size) {
        out += '+' to b[j]; j++
    }
    return out
}

@Composable
fun MiniSpinner(size: Dp = 12.dp) {
    val p = LocalClaude.current
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        drawArc(
            color = p.accent,
            startAngle = angle,
            sweepAngle = 240f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = this.size.minDimension * 0.16f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            ),
        )
    }
}

@Composable
fun ChatItemView(item: ChatItem, onRetry: (() -> Unit)? = null) {
    val p = LocalClaude.current
    when (item.kind) {
        ItemKind.USER -> Column(Modifier.fillMaxWidth()) {
            Text(
                "Вы",
                style = MaterialTheme.typography.labelSmall,
                color = p.textTertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(p.cardAlt, RoundedCornerShape(14.dp))
                    .border(1.dp, p.border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = p.text,
                    )
                }
            }
        }
        ItemKind.ASSISTANT -> Row(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(top = 2.dp, end = 10.dp)) { ClaudeMark(18.dp) }
            Column(Modifier.weight(1f)) {
                SelectionContainer {
                    MarkdownView(item.text + if (item.streaming) " ▍" else "")
                }
            }
        }
        ItemKind.THINKING -> ThinkingLine(item.text, item.streaming)
        ItemKind.TOOL -> item.tool?.let { ToolCard(it, Modifier.padding(start = 26.dp)) }
        ItemKind.ERROR -> Column(
            Modifier
                .fillMaxWidth()
                .background(p.diffDelBg, RoundedCornerShape(12.dp))
                .border(1.dp, p.danger.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text("Ошибка", style = MaterialTheme.typography.labelMedium, color = p.danger)
            Spacer(Modifier.height(4.dp))
            SelectionContainer {
                Text(
                    item.text,
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, lineHeight = 17.sp),
                    color = p.diffDelText,
                )
            }
        }
        ItemKind.INFO -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                item.text,
                style = MaterialTheme.typography.labelSmall,
                color = p.textTertiary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        ItemKind.DIFF -> Unit
    }
}

@Composable
fun EmptyState(onExample: (String) -> Unit) {
    val p = LocalClaude.current
    val examples = listOf(
        "Что лежит в рабочей папке?",
        "Создай index.html с лендингом",
        "Найди все TODO в проекте",
        "Сделай скрипт бэкапа папки",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "✻",
                style = TextStyle(fontFamily = MonoFamily, fontSize = 26.sp),
                color = p.accent,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Welcome to Claude Code",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                    color = p.text,
                )
                Text(
                    "нативный · Android · bash, файлы, диффы",
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 11.5.sp),
                    color = p.textTertiary,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(
                "/model     сменить модель",
                "/clear     новая сессия",
                "/files     рабочая папка",
                "/logs      консоль и ошибки",
            ).forEach { line ->
                Text(
                    line,
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, lineHeight = 18.sp),
                    color = p.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionTitle("с чего начать")
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            examples.forEach { ex ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onExample(ex) }
                        .padding(horizontal = 4.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "›",
                        style = TextStyle(fontFamily = MonoFamily, fontSize = 15.sp),
                        color = p.accent,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(ex, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    val p = LocalClaude.current
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = p.textTertiary,
        modifier = modifier,
    )
}

fun relativeTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000 -> "сейчас"
        diff < 3_600_000 -> "${diff / 60_000} мин"
        diff < 86_400_000 -> "${diff / 3_600_000} ч"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000} дн"
        else -> android.text.format.DateFormat.format("dd.MM", ts).toString()
    }
}

@Composable
fun HorizontalChipsScroll(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

// ── Строка статуса в духе CC: модель · режим · контекст · cwd ───────────────

@Composable
fun StatusLine(
    model: String,
    mode: String,
    ctxPct: Int,
    cwd: String,
    onClick: () -> Unit,
) {
    val p = LocalClaude.current
    fun style(color: Color) = TextStyle(fontFamily = MonoFamily, fontSize = 10.5.sp, lineHeight = 14.sp, color = color)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✻", style = style(p.accent))
        Text(" · ", style = style(p.textTertiary))
        Text(model, style = style(p.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(" · ", style = style(p.textTertiary))
        Text(mode, style = style(p.textTertiary))
        if (ctxPct >= 0) {
            Text(" · ", style = style(p.textTertiary))
            Text(
                "ctx $ctxPct%",
                style = style(if (ctxPct >= 80) p.warn else p.textTertiary),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(cwd, style = style(p.textTertiary), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val ccThinkingVerbs = listOf(
    "Thinking", "Pondering", "Noodling", "Percolating", "Mulling",
    "Cogitating", "Ruminating", "Brewing", "Simmering", "Tinkering",
)

@Composable
fun ThinkingLine(text: String, streaming: Boolean) {
    val p = LocalClaude.current
    var idx by remember { mutableIntStateOf(0) }
    LaunchedEffect(streaming) {
        if (streaming) {
            while (true) {
                delay(2400)
                idx = (idx + 1) % ccThinkingVerbs.size
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 26.dp, top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "✻",
            style = TextStyle(fontFamily = MonoFamily, fontSize = 11.5.sp, lineHeight = 17.sp),
            color = p.accent.copy(alpha = 0.85f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            (if (streaming) ccThinkingVerbs[idx] else "Размышление") + "…",
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = p.textTertiary,
        )
        if (text.isNotBlank()) {
            Text(
                " " + text.take(220).replace("\n", " "),
                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                color = p.textTertiary.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
