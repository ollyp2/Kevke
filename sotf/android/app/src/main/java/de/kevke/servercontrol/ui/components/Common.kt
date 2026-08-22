package de.kevke.servercontrol.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.kevke.servercontrol.ui.theme.CornerRadius
import de.kevke.servercontrol.ui.theme.LineIcons
import de.kevke.servercontrol.ui.theme.LocalAppColors
import androidx.compose.foundation.Canvas

@Composable
fun LineIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = LocalAppColors.current.onBackground,
    size: androidx.compose.ui.unit.Dp = 24.dp,
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalAppColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CornerRadius))
            .background(c.surface)
            .border(1.dp, c.outline, RoundedCornerShape(CornerRadius))
            .padding(16.dp),
        content = content,
    )
}

/**
 * The centrepiece: one large round power control. Ring shows state, the
 * glyph inside is the standard power mark. Busy states pulse the ring.
 */
@Composable
fun PowerButton(
    running: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    val ringColor = when {
        busy -> c.warn.copy(alpha = pulseAlpha)
        running -> c.ok
        else -> c.onMuted.copy(alpha = 0.5f)
    }
    val glyphColor = when {
        busy -> c.warn
        running -> c.ok
        else -> c.onMuted
    }

    Box(
        modifier = modifier
            .size(196.dp)
            .clip(CircleShape)
            .background(c.surface)
            .border(1.dp, c.outline, CircleShape)
            .clickable(enabled = enabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(176.dp)) {
            drawCircle(
                color = ringColor,
                radius = size.minDimension / 2f - 4.dp.toPx(),
                style = Stroke(width = 2.5.dp.toPx()),
            )
        }
        LineIcon(LineIcons.Power, tint = glyphColor, size = 72.dp)
    }
}

/** Small status lamp — a stroked ring with a filled core. */
@Composable
fun StatusLamp(state: String, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    val (color, label) = when (state) {
        "RUNNING" -> c.ok to "Online"
        "TERMINATED" -> c.onMuted to "Aus"
        "STAGING" -> c.warn to "Startet"
        "STOPPING" -> c.warn to "Faehrt herunter"
        else -> c.danger to "Unbekannt"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Canvas(Modifier.size(12.dp)) {
            drawCircle(color = color, radius = size.minDimension / 2f, style = Stroke(1.5.dp.toPx()))
            drawCircle(color = color, radius = size.minDimension / 4f)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.onMuted)
    }
}

/** Outlined action button — stroke only, no filled slab. */
@Composable
fun LineButton(
    label: String,
    icon: ImageVector? = null,
    tint: Color? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalAppColors.current
    val fg = (tint ?: c.accent).let { if (enabled) it else it.copy(alpha = 0.4f) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(CornerRadius))
            .border(1.dp, fg.copy(alpha = 0.5f), RoundedCornerShape(CornerRadius))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        icon?.let {
            LineIcon(it, tint = fg, size = 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, color = fg)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Ja, loeschen",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalAppColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceRaised,
        shape = RoundedCornerShape(CornerRadius),
        title = { Text(title, color = c.onBackground) },
        text = { Text(message, color = c.onMuted) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = c.danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen", color = c.onMuted) }
        },
    )
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    val c = LocalAppColors.current
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = c.onMuted,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp),
    )
}

@Composable
fun SegmentedRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalAppColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(CornerRadius))
            .border(1.dp, c.outline, RoundedCornerShape(CornerRadius)),
    ) {
        options.forEachIndexed { i, option ->
            val active = i == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(if (active) c.accent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) c.accent else c.onMuted,
                )
            }
        }
    }
}
