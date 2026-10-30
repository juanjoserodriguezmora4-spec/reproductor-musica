package com.bismopower.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bismopower.app.ui.theme.LocalBismo
import com.bismopower.app.ui.theme.T
import java.util.Locale

@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalBismo.current
    Row(
        modifier
            .fillMaxWidth()
            .glass(Corners.button, shadow = false)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) c.selected else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = T.label, color = if (on) c.text else c.muted, maxLines = 1)
            }
        }
    }
}

@Composable
fun IconChip(
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color? = null,
    size: Dp = 36.dp,
    description: String? = null,
) {
    val c = LocalBismo.current
    Box(
        Modifier
            .size(size)
            .glass(RoundedCornerShape(12.dp), strong = true, shadow = false)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint ?: c.text, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = LocalBismo.current
    Row(
        modifier
            .clip(Corners.button)
            .background(if (enabled) c.accent else c.track)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = c.onAccent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = T.label, color = if (enabled) c.onAccent else c.muted)
    }
}

@Composable
fun ProgressLine(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 5.dp,
) {
    val c = LocalBismo.current
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(c.track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(height))
                .background(color),
        )
    }
}

@Composable
fun Badge(
    text: String,
    color: Color? = null,
) {
    val c = LocalBismo.current
    val col = color ?: c.accent
    Text(
        text,
        style = T.tiny,
        color = col,
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(col.copy(alpha = 0.14f))
                .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val c = LocalBismo.current
    Column(
        modifier
            .fillMaxWidth()
            .glass()
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(c.accentSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = c.accent, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(12.dp))
        Text(title, style = T.title, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text(text, style = T.small, color = c.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            PrimaryButton(action, onAction)
        }
    }
}

@Composable
fun ListRow(
    title: String,
    subtitle: String,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
    onClick: (() -> Unit)? = null,
    highlight: Boolean = false,
) {
    val c = LocalBismo.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 7.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = T.title, color = if (highlight) c.accent else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = T.tiny, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
fun RadioDot(on: Boolean) {
    val c = LocalBismo.current
    Box(
        Modifier
            .size(18.dp)
            .clip(CircleShape)
            .border(if (on) 5.dp else 2.dp, if (on) c.accent else c.muted, CircleShape),
    )
}

private val es = Locale.forLanguageTag("es-CO")

fun fmtDuration(ms: Long): String {
    val sec = ms / 1000
    if (sec <= 0) return "0:00"
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(es, "%d:%02d:%02d", h, m, s) else String.format(es, "%d:%02d", m, s)
}

fun plural(
    n: Int,
    one: String,
    many: String,
): String = if (n == 1) "1 $one" else "$n $many"
