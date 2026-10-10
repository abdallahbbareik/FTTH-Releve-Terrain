package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.sin

@Composable
fun AnimatedFiberBackground(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fiber_bg_transition")
    
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fiber_phase"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fiber_pulse"
    )

    val nodes = remember {
        listOf(
            Triple(0.15f, 0.2f, 5f),
            Triple(0.85f, 0.15f, 7f),
            Triple(0.5f, 0.35f, 9f),
            Triple(0.25f, 0.7f, 6f),
            Triple(0.75f, 0.8f, 8f),
            Triple(0.5f, 0.9f, 7f),
            Triple(0.35f, 0.45f, 6f),
            Triple(0.65f, 0.55f, 7f),
            Triple(0.2f, 0.4f, 5f),
            Triple(0.8f, 0.6f, 6f)
        )
    }

    val connections = remember {
        listOf(
            Pair(0, 2), Pair(1, 2), Pair(2, 6), Pair(6, 3),
            Pair(2, 7), Pair(7, 4), Pair(4, 5), Pair(3, 5),
            Pair(0, 6), Pair(1, 7), Pair(8, 2), Pair(9, 7),
            Pair(8, 3), Pair(9, 4)
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Rich dark gradient background with fiber optic glow
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF1E293B),
                    Color(0xFF0F172A),
                    Color(0xFF020617)
                ),
                center = Offset(width * 0.5f, height * 0.4f),
                radius = maxOf(width, height) * 0.95f
            )
        )

        // Draw animated network cables / lines
        connections.forEachIndexed { index, (fromIdx, toIdx) ->
            val from = nodes[fromIdx]
            val to = nodes[toIdx]
            val startX = from.first * width
            val startY = from.second * height
            val endX = to.first * width
            val endY = to.second * height

            // Base optical line
            drawLine(
                color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = 2.5f
            )

            // Animated light pulse travelling along the fiber
            val progress = ((phase * (0.4f + index * 0.08f)) % 2f) / 2f
            val pulseX = startX + (endX - startX) * progress
            val pulseY = startY + (endY - startY) * progress

            drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.7f * (1f - kotlin.math.abs(progress - 0.5f) * 2f)),
                radius = 5f,
                center = Offset(pulseX, pulseY)
            )
        }

        // Draw glowing nodes (SRO, chambers, splitters)
        nodes.forEachIndexed { index, (nx, ny, radius) ->
            val cx = nx * width
            val cy = ny * height
            val animatedRadius = radius * (0.85f + 0.15f * sin(phase * 1.5f + index))

            // Outer glow ring
            drawCircle(
                color = Color(0xFF3B82F6).copy(alpha = 0.3f * pulse),
                radius = animatedRadius * 3.5f,
                center = Offset(cx, cy)
            )

            // Node core
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = animatedRadius,
                center = Offset(cx, cy)
            )

            // Inner bright reflection
            drawCircle(
                color = Color.White,
                radius = animatedRadius * 0.45f,
                center = Offset(cx, cy)
            )
        }
    }
}
