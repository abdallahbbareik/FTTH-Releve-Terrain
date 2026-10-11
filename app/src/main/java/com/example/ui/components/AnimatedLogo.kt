package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

private val OvershootEasing = Easing { t ->
    val k = 2f
    val u = t - 1f
    u * u * ((k + 1f) * u + k) + 1f
}

private val Navy = Color(0xFF0D2D42)
private val SkyBlue = Color(0xFF5AB8E4)
private val PinRed = Color(0xFFDD3231)

private fun filledPath(data: String): Path =
    PathParser().parsePathString(data).toPath().apply { fillType = PathFillType.EvenOdd }

/**
 * Logo complet (lettres AB, fibre, épingle, « Relevé Terrain FTTH »), identique à l'image choisie.
 * Animation : la fibre se trace en 600 ms, l'épingle se pose avec un léger rebond,
 * puis le texte apparaît en fondu. Donner la hauteur avec aspectRatio(LogoPaths.ASPECT).
 */
@Composable
fun AnimatedLogo(modifier: Modifier = Modifier) {
    val fibreProgress = remember { Animatable(0f) }
    val pinScale = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            async { fibreProgress.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
            async {
                delay(500)
                pinScale.animateTo(1f, tween(300, easing = OvershootEasing))
            }
            async {
                delay(300)
                textAlpha.animateTo(1f, tween(500))
            }
        }
    }

    val letters = remember { filledPath(LogoPaths.LETTERS) }
    val pin = remember { filledPath(LogoPaths.PIN) }
    val text = remember { filledPath(LogoPaths.TEXT) }
    val fibre = remember {
        Path().apply {
            moveTo(288f, 600.5f)
            cubicTo(321.5f, 618.7f, 357f, 627.5f, 396f, 627.5f)
            cubicTo(510.4f, 625.9f, 611.3f, 558.9f, 727f, 581.5f)
        }
    }
    val measure = remember { PathMeasure().also { it.setPath(fibre, false) } }
    val fibreLength = remember { measure.length }
    val fibreSegment = remember { Path() }

    Canvas(
        modifier = modifier.semantics { contentDescription = "Relevé Terrain FTTH" }
    ) {
        val s = size.width / LogoPaths.WIDTH
        scale(scale = s, pivot = Offset.Zero) {
            translate(left = -LogoPaths.ORIGIN_X, top = -LogoPaths.ORIGIN_Y) {
                drawPath(letters, Navy)

                val p = fibreProgress.value
                if (p > 0f) {
                    fibreSegment.reset()
                    measure.getSegment(0f, fibreLength * p, fibreSegment, true)
                    drawPath(
                        fibreSegment,
                        SkyBlue.copy(alpha = 0.22f),
                        style = Stroke(width = LogoPaths.FIBRE_THICKNESS * 2f, cap = StrokeCap.Round)
                    )
                    drawPath(
                        fibreSegment,
                        SkyBlue,
                        style = Stroke(width = LogoPaths.FIBRE_THICKNESS, cap = StrokeCap.Round)
                    )
                }

                val ps = pinScale.value
                if (ps > 0f) {
                    scale(scale = ps, pivot = Offset(LogoPaths.PIN_TIP_X, LogoPaths.PIN_TIP_Y)) {
                        drawPath(pin, PinRed)
                    }
                }

                val ta = textAlpha.value
                if (ta > 0f) drawPath(text, Navy.copy(alpha = ta))
            }
        }
    }
}
