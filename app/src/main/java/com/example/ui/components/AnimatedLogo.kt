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
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

private val OvershootEasing = Easing { t ->
    val k = 2f
    val u = t - 1f
    u * u * ((k + 1f) * u + k) + 1f
}

private const val LETTER_A = "M30,70 L40,38 L50,70 M34,60 L46,60"
private const val LETTER_B =
    "M58,38 L58,70 M58,38 L66,38 C73,38 73,53 66,53 L58,53 M58,53 L67,53 C75,53 75,70 67,70 L58,70"
private const val FIBRE = "M28,76 C44,86 64,70 80,74"
private const val PIN = "M78,28 c-4,0 -6,3 -6,6 c0,4 6,10 6,10 c0,0 6,-6 6,-10 c0,-3 -2,-6 -6,-6 z"

private val White = Color(0xFFFFFFFF)
private val SkyBlue = Color(0xFF4FC3F7)
private val PinRed = Color(0xFFE53935)

/**
 * Logo « AB » : la fibre lumineuse se trace en 600 ms, puis l'épingle rouge
 * apparaît avec un léger rebond. Même dessin que ic_splash_ab.xml (viewport 108 x 108).
 */
@Composable
fun AnimatedLogo(modifier: Modifier = Modifier) {
    val fibreProgress = remember { Animatable(0f) }
    val pinScale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            async {
                fibreProgress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
            }
            async {
                delay(500)
                pinScale.animateTo(1f, tween(300, easing = OvershootEasing))
            }
        }
    }

    val letterA = remember { PathParser().parsePathString(LETTER_A).toPath() }
    val letterB = remember { PathParser().parsePathString(LETTER_B).toPath() }
    val fibre = remember { PathParser().parsePathString(FIBRE).toPath() }
    val pin = remember { PathParser().parsePathString(PIN).toPath() }
    val measure = remember { PathMeasure().also { it.setPath(fibre, false) } }
    val fibreLength = remember { measure.length }
    val fibreSegment = remember { Path() }

    Canvas(modifier = modifier) {
        val s = size.minDimension / 108f

        scale(scale = s, pivot = Offset.Zero) {
            val letterStroke = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(letterA, White, style = letterStroke)
            drawPath(letterB, White, style = letterStroke)

            val p = fibreProgress.value
            if (p > 0f) {
                fibreSegment.reset()
                measure.getSegment(0f, fibreLength * p, fibreSegment, true)
                drawPath(
                    fibreSegment,
                    SkyBlue.copy(alpha = 0.2f),
                    style = Stroke(width = 6f, cap = StrokeCap.Round)
                )
                drawPath(
                    fibreSegment,
                    SkyBlue,
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                )
            }

            val ps = pinScale.value
            if (ps > 0f) {
                scale(scale = ps, pivot = Offset(78f, 44f)) {
                    drawPath(pin, PinRed)
                    drawCircle(White, radius = 2f, center = Offset(78f, 31.5f))
                }
            }
        }
    }
}
