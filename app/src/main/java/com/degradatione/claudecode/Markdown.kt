package com.degradatione.claudecode

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MdBlock {
    data class Para(val text: AnnotatedString) : MdBlock()
    data class Heading(val level: Int, val text: AnnotatedString) : MdBlock()
    data class Code(val lang: String, val code: String) : MdBlock()
    data class Bullets(val items: List<AnnotatedString>, val ordered: Boolean) : MdBlock()
    data class Quote(val text: AnnotatedString) : MdBlock()
    data class Rule(val dummy: Int = 0) : MdBlock()
}

private val codeSpanBg = SpanStyle(background = Color(0x22808080), fontFamily = MonoFamily)

fun parseInline(raw: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    val s = raw
    while (i < s.length) {
        when {
            s.startsWith("`", i) -> {
                val end = s.indexOf('`', i + 1)
                if (end > i) {
                    pushStyle(codeSpanBg)
                    append(s.substring(i + 1, end))
                    pop()
                    i = end + 1
                } else {
                    append('`'); i++
                }
            }
            s.startsWith("**", i) -> {
                val end = s.indexOf("**", i + 2)
                if (end > i) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold))
                    append(s.substring(i + 2, end))
                    pop()
                    i = end + 2
                } else {
                    append("**"); i += 2
                }
            }
            s.startsWith("~~", i) -> {
                val end = s.indexOf("~~", i + 2)
                if (end > i) {
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(s.substring(i + 2, end))
                    pop()
                    i = end + 2
                } else {
                    append("~~"); i += 2
                }
            }
            s.startsWith("*", i) || s.startsWith("_", i) -> {
                val marker = s[i].toString()
                val end = s.indexOf(marker, i + 1)
                if (end > i + 1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(s.substring(i + 1, end))
                    pop()
                    i = end + 1
                } else {
                    append(marker); i++
                }
            }
            s.startsWith("[", i) -> {
                val close = s.indexOf("](", i)
                val endParen = if (close > 0) s.indexOf(')', close) else -1
                if (close > 0 && endParen > close) {
                    val label = s.substring(i + 1, close)
                    val url = s.substring(close + 2, endParen)
                    runCatching {
                        withLink(LinkAnnotation.Url(url)) {
                            pushStyle(
                                SpanStyle(
                                    color = Color(0xFFD97757),
                                    textDecoration = TextDecoration.Underline,
                                )
                            )
                            append(label)
                            pop()
                        }
                    }.onFailure { append(label) }
                    i = endParen + 1
                } else {
                    append('['); i++
                }
            }
            else -> {
                append(s[i]); i++
            }
        }
    }
}

fun parseMarkdown(text: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val lines = text.replace("\r\n", "\n").split('\n')
    var idx = 0
    val para = StringBuilder()

    fun flushPara() {
        if (para.isNotBlank()) out += MdBlock.Para(parseInline(para.toString().trim()))
        para.setLength(0)
    }

    while (idx < lines.size) {
        val line = lines[idx]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                flushPara()
                val lang = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                idx++
                while (idx < lines.size && !lines[idx].trim().startsWith("```")) {
                    code.append(lines[idx]).append('\n')
                    idx++
                }
                out += MdBlock.Code(lang, code.toString().trimEnd())
            }
            trimmed.startsWith("#") -> {
                flushPara()
                val hashes = trimmed.takeWhile { it == '#' }.length
                out += MdBlock.Heading(hashes.coerceIn(1, 4), parseInline(trimmed.drop(hashes).trim()))
            }
            trimmed.startsWith("> ") -> {
                flushPara()
                out += MdBlock.Quote(parseInline(trimmed.removePrefix("> ").trim()))
            }
            trimmed == "---" || trimmed == "***" -> {
                flushPara()
                out += MdBlock.Rule()
            }
            trimmed.matches(Regex("^[-*+]\\s+.*")) || trimmed.matches(Regex("^\\d+[.)]\\s+.*")) -> {
                flushPara()
                val ordered = trimmed.first().isDigit()
                val items = mutableListOf<AnnotatedString>()
                while (idx < lines.size) {
                    val l = lines[idx].trim()
                    val m = if (ordered) Regex("^\\d+[.)]\\s+(.*)$") else Regex("^[-*+]\\s+(.*)$")
                    val match = m.find(l)
                    if (match == null) break
                    items += parseInline(match.groupValues[1])
                    idx++
                }
                out += MdBlock.Bullets(items, ordered)
                continue
            }
            trimmed.isEmpty() -> flushPara()
            else -> {
                if (para.isNotEmpty()) para.append('\n')
                para.append(trimmed)
            }
        }
        idx++
    }
    flushPara()
    return out
}

@Composable
fun MarkdownView(text: String, modifier: Modifier = Modifier) {
    val p = LocalClaude.current
    val blocks = parseMarkdown(text)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (b in blocks) {
            when (b) {
                is MdBlock.Para -> Text(
                    text = b.text,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 23.sp),
                    color = p.text,
                )
                is MdBlock.Heading -> Text(
                    text = b.text,
                    style = when (b.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp)
                        else -> MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp)
                    },
                    color = p.text,
                )
                is MdBlock.Code -> CodeBlock(b.lang, b.code)
                is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    b.items.forEachIndexed { i, item ->
                        Row {
                            Text(
                                text = if (b.ordered) "${i + 1}." else "•",
                                style = MaterialTheme.typography.bodyLarge,
                                color = p.textTertiary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 23.sp),
                                color = p.text,
                            )
                        }
                    }
                }
                is MdBlock.Quote -> Row {
                    Box(
                        Modifier
                            .padding(end = 10.dp)
                            .size(width = 2.dp, height = 20.dp)
                            .background(p.borderStrong)
                    )
                    Text(
                        text = b.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                        color = p.textSecondary,
                    )
                }
                is MdBlock.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .height(1.dp)
                        .background(p.border)
                )
            }
        }
    }
}

@Composable
fun CodeBlock(lang: String, code: String, modifier: Modifier = Modifier) {
    val p = LocalClaude.current
    val clipboard = LocalClipboardManager.current
    Column(
        modifier
            .fillMaxWidth()
            .background(p.codeBg, RoundedCornerShape(12.dp))
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = lang.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
                color = p.textTertiary,
                modifier = Modifier.weight(1f),
            )
            IconTouch(
                icon = Icons.Rounded.ContentCopy,
                contentDescription = "Скопировать код",
                tint = p.textTertiary,
                size = 18.dp,
            ) { clipboard.setText(AnnotatedString(code)) }
        }
        Box(
           Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
        ) {
            Text(
                text = code,
                style = TextStyle(fontFamily = MonoFamily, fontSize = 12.5.sp, lineHeight = 18.sp),
                color = p.text,
            )
        }
    }
}
