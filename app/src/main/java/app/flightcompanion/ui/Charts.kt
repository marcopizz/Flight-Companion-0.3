package app.flightcompanion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ChartColors = listOf(
    Color(0xFFE8501A), Color(0xFF17304D), Color(0xFFF2B200), Color(0xFF1F7A6B), Color(0xFF8C6A4F), Color(0xFF6B7A90)
)

/** Grafico ad anello con legenda. data: etichetta -> valore. */
@Composable
fun DonutChart(
    title: String,
    data: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    diameter: Dp = 110.dp
) {
    val total = data.sumOf { it.second }
    Column(modifier) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))
        if (total == 0) {
            Text("Nessun dato", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(diameter)) {
                    val stroke = size.minDimension * 0.2f
                    val inset = stroke / 2f
                    var start = -90f
                    data.forEachIndexed { i, (_, v) ->
                        val sweep = 360f * v / total
                        drawArc(
                            color = ChartColors[i % ChartColors.size],
                            startAngle = start,
                            sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f),
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke, cap = StrokeCap.Butt)
                        )
                        start += sweep
                    }
                }
                Text("$total", style = MaterialTheme.typography.headlineSmall)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                data.forEachIndexed { i, (label, v) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(ChartColors[i % ChartColors.size]))
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${(100.0 * v / total).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** Anello singolo per una percentuale (es. quota del totale). */
@Composable
fun ShareRing(fraction: Float, label: String, modifier: Modifier = Modifier, diameter: Dp = 84.dp) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = size.minDimension * 0.16f
            val inset = stroke / 2f
            val sz = Size(size.width - stroke, size.height - stroke)
            drawArc(track, -90f, 360f, false, Offset(inset, inset), sz, style = Stroke(stroke))
            drawArc(fill, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), sz, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
