package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val FibreBase = Color(0xFF0B5A8A)
private val FibreLight = Color(0xFF4FC3F7)

/**
 * Animation d'attente : une impulsion de lumière parcourt une fibre entre
 * trois nœuds (BPE -> PBO -> client) et les allume à son passage.
 */
@Composable
fun FibreLoader(
    modifier: Modifier = Modifier,
    label: String? = null,
    labelColor: Color = FibreLight
) {
    val transition = rememberInfiniteTransition(label = "fibre_loader")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fibre_loader_progress"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(width = 220.dp, height = 48.dp)) {
            val y = size.height / 2f
            val xs = listOf(0.1f, 0.5f, 0.9f).map { it * size.width }

            drawLine(
                color = FibreBase,
                start = Offset(xs.first(), y),
                end = Offset(xs.last(), y),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )

            val px = xs.first() + (xs.last() - xs.first()) * progress

            xs.forEach { x ->
                val lit = (1f - abs(px - x) / size.width * 6f).coerceIn(0f, 1f)
                drawCircle(
                    color = lerp(FibreBase, FibreLight, lit),
                    radius = (7.dp + 3.dp * lit).toPx(),
                    center = Offset(x, y)
                )
            }

            drawCircle(FibreLight.copy(alpha = 0.2f), 12.dp.toPx(), Offset(px, y))
            drawCircle(FibreLight, 5.dp.toPx(), Offset(px, y))
        }

        if (label != null) {
            Spacer(Modifier.height(12.dp))
            Text(text = label, color = labelColor, fontSize = 14.sp)
        }
    }
}
