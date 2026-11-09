package com.neelaxmi.focus.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neelaxmi.focus.core.DayBar
import java.time.format.TextStyle as DayStyle
import java.util.Locale

/** Circular progress with a rounded sweep. [progress] 0..1. Content is centred inside. */
@Composable
fun ProgressRing(
    progress: Float,
    size: Dp,
    stroke: Dp = 14.dp,
    colors: List<Color> = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val inset = sw / 2
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            val sweep = 360f * animated
            if (sweep > 0f) drawArc(brush = Brush.sweepGradient(colors), startAngle = -90f, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(inset, inset), size = arcSize, style = Stroke(sw, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Glass-style card: translucent surface, hairline highlight border, soft vertical gradient. (No true backdrop blur: it is not available on all supported API levels.) */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
    ) {
        Column(Modifier.background(Brush.verticalGradient(listOf(surface.copy(alpha = 0.92f), surface.copy(alpha = 0.62f)))).padding(20.dp)) { content() }
    }
}

@Composable
fun StatTile(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = accent)
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Seven bars, oldest first. Bars that met the goal use the primary colour; others the muted tertiary. */
@Composable
fun WeekBars(week: List<DayBar>, goalMin: Int, modifier: Modifier = Modifier) {
    val maxMin = maxOf(goalMin, week.maxOfOrNull { it.focusMin } ?: 0).coerceAtLeast(1)
    Row(modifier.fillMaxWidth().height(132.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        week.forEach { d ->
            val frac = d.focusMin.toFloat() / maxMin
            Column(Modifier.width(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.width(22.dp).height((88.dp * frac).coerceAtLeast(4.dp))
                    .background(if (d.goalMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary.copy(alpha = if (d.focusMin > 0) 0.8f else 0.25f), RoundedCornerShape(8.dp)))
                Text(d.date.dayOfWeek.getDisplayName(DayStyle.NARROW, Locale.getDefault()), Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


/** One bar per entry; tapping a bar selects it (the caller shows the value). A null value means "no data" (drawn as an outline, not as zero). */
@Composable
fun TapBars(values: List<Long?>, labels: List<String>, selected: Int?, onSelect: (Int) -> Unit, accent: Color, modifier: Modifier = Modifier) {
    val max = (values.filterNotNull().maxOrNull() ?: 0L).coerceAtLeast(1L)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight().clickable { onSelect(i) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                val frac by animateFloatAsState(if (v == null) 0f else v.toFloat() / max, tween(600), label = "bar$i")
                val isSel = selected == i
                Box(
                    Modifier.fillMaxWidth(0.7f).height((110.dp * frac).coerceAtLeast(if (v == null) 6.dp else 3.dp))
                        .background(if (v == null) muted.copy(alpha = 0.18f) else if (isSel) accent else accent.copy(alpha = 0.55f), RoundedCornerShape(6.dp)),
                )
                Text(labels.getOrElse(i) { "" }, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge, color = if (isSel) MaterialTheme.colorScheme.onSurface else muted, maxLines = 1)
            }
        }
    }
}

@Composable
fun ThinProgress(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val p by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "p")
    LinearProgressIndicator(progress = { p }, modifier = modifier.fillMaxWidth().height(8.dp), color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant)
}
