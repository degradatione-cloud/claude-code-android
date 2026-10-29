package com.degradatione.claudecode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Composer(
    value: String,
    running: Boolean,
    modelName: String,
    autoApprove: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onModelClick: () -> Unit,
    onFilesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalClaude.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = p.card,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, p.border),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.text),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 24.dp, max = 150.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                "Спросите Claude…  (/ для команд)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = p.textTertiary,
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Chip(text = modelName, icon = Icons.Rounded.Bolt, onClick = onModelClick)
                if (autoApprove) {
                    Spacer(Modifier.width(6.dp))
                    Chip(text = "auto-accept", selected = true)
                }
                Spacer(Modifier.width(6.dp))
                IconTouch(
                    icon = Icons.Rounded.FolderOpen,
                    contentDescription = "Рабочая папка",
                    tint = p.textTertiary,
                    size = 18.dp,
                    onClick = onFilesClick,
                )
                Spacer(Modifier.weight(1f))
                if (running) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(p.cardAlt, CircleShape)
                            .border(1.dp, p.border, CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onStop,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Stop, "Остановить", tint = p.danger, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(if (value.isBlank()) p.borderStrong else p.accent, CircleShape)
                            .clickable(
                                enabled = value.isNotBlank(),
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onSend,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.ArrowUpward,
                            "Отправить",
                            tint = if (value.isBlank()) p.textTertiary else p.onAccent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClaudeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    singleLine: Boolean = true,
    mono: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val p = LocalClaude.current
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = p.textSecondary)
        Spacer(Modifier.size(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .background(p.cardAlt, RoundedCornerShape(12.dp))
                .border(1.dp, p.border, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = TextStyle(
                    fontFamily = if (mono) MonoFamily else ClaudeSans,
                    fontSize = 14.sp,
                    color = p.text,
                ),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(
                                placeholder,
                                style = TextStyle(
                                    fontFamily = if (mono) MonoFamily else ClaudeSans,
                                    fontSize = 14.sp,
                                ),
                                color = p.textTertiary,
                            )
                        }
                        inner()
                    }
                },
            )
            trailing?.invoke()
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalClaude.current
    Box(
        modifier
            .background(if (enabled) p.accent else p.borderStrong, RoundedCornerShape(12.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (enabled) p.onAccent else p.textTertiary,
        )
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalClaude.current
    Box(
        modifier
            .background(p.cardAlt, RoundedCornerShape(12.dp))
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = p.textSecondary)
    }
}

@Composable
fun RowActions(vararg content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        content.forEach { it() }
    }
}
