package com.example.ui.components

import android.app.Activity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.R

// Palette identique au splash
private val LoadingBackground = Color(0xFFE3E8EB)
private val LoadingGrid = Color(0xFFD3DAE0)
private val LoadingRoad = Color(0xFFF6F8FA)
private val LoadingSky = Color(0xFF57B8E3)
private val LoadingNavy = Color(0xFF0D2D42)
private val LoadingRed = Color(0xFFDC2E2F)

/**
 * Écran de chargement des projets (couleurs du splash) : grille de carte discrète,
 * une route, et un radar de localisation (ondes bleu ciel autour d'un point rouge).
 * Bloque les touches pendant le chargement.
 */
@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    LoadingLightBars()

    val transition = rememberInfiniteTransition(label = "radar")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_t"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LoadingBackground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {},
        contentAlignment = Alignment.Center
    ) {
        // Fond : grille de carte et route
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 36.dp.toPx()
            val line = 1.dp.toPx()
            var x = step
            while (x < size.width) {
                drawLine(LoadingGrid, Offset(x, 0f), Offset(x, size.height), strokeWidth = line)
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(LoadingGrid, Offset(0f, y), Offset(size.width, y), strokeWidth = line)
                y += step
            }
            val road = Path().apply {
                moveTo(0f, size.height * 0.78f)
                cubicTo(
                    size.width * 0.35f, size.height * 0.62f,
                    size.width * 0.65f, size.height * 0.82f,
                    size.width, size.height * 0.42f
                )
            }
            drawPath(road, LoadingRoad, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        }

        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Radar
            Canvas(modifier = Modifier.size(180.dp)) {
                val c = center
                val maxR = size.minDimension / 2f
                val minR = 12.dp.toPx()

                drawCircle(
                    LoadingSky.copy(alpha = 0.3f),
                    radius = maxR * 0.7f,
                    center = c,
                    style = Stroke(width = 1.dp.toPx())
                )
                drawCircle(LoadingSky.copy(alpha = 0.25f), radius = 26.dp.toPx(), center = c)

                for (i in 0..2) {
                    val p = (t + i / 3f) % 1f
                    drawCircle(
                        color = LoadingSky.copy(alpha = (1f - p) * 0.85f),
                        radius = minR + (maxR - minR) * p,
                        center = c,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                drawCircle(LoadingRed, radius = 8.dp.toPx(), center = c)
                drawCircle(LoadingBackground, radius = 3.2.dp.toPx(), center = c)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.loading_projects),
                color = LoadingNavy,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.loading_map_data),
                color = LoadingNavy.copy(alpha = 0.65f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Icônes de barres système sombres sur fond clair, restaurées à la sortie de l'écran. */
@Composable
private fun LoadingLightBars() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (controller != null) {
                previousStatus?.let { controller.isAppearanceLightStatusBars = it }
                previousNav?.let { controller.isAppearanceLightNavigationBars = it }
            }
        }
    }
}
