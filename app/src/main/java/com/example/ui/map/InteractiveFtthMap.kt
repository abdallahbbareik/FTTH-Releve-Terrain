package com.example.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.ui.components.FtthNodeVisuals
import com.example.ui.viewmodel.MapLayerType
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun InteractiveFtthMap(
    nodes: List<FtthNodeEntity>,
    links: List<FtthLinkEntity>,
    selectedNode: FtthNodeEntity?,
    activeMapLayer: MapLayerType,
    isCableDrawingMode: Boolean,
    cableFirstNode: FtthNodeEntity?,
    technicianPosition: Pair<Double, Double>,
    onNodeSelected: (FtthNodeEntity?) -> Unit,
    onMapLongPress: (Double, Double) -> Unit,
    onToggleCableMode: () -> Unit,
    onCycleMapLayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Reference center of survey area
    val centerLat = 48.8566
    val centerLon = 2.3522

    // Meters per pixel at zoom 1.0 (approx 2 pixels per meter in field view)
    val basePixelsPerMeter = 2.4f

    var zoom by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }

    // Helper functions for Geo projection
    fun toScreenOffset(lat: Double, lon: Double, width: Float, height: Float): Offset {
        val dx = (lon - centerLon) * 111320.0 * cos(Math.toRadians(centerLat))
        val dy = (lat - centerLat) * 110540.0
        val scale = basePixelsPerMeter * zoom
        val px = width / 2f + (dx * scale).toFloat() + panOffsetX
        val py = height / 2f - (dy * scale).toFloat() + panOffsetY
        return Offset(px, py)
    }

    fun toGeoCoordinates(screenX: Float, screenY: Float, width: Float, height: Float): Pair<Double, Double> {
        val scale = basePixelsPerMeter * zoom
        val dxMeters = (screenX - width / 2f - panOffsetX) / scale
        val dyMeters = -(screenY - height / 2f - panOffsetY) / scale
        val lon = centerLon + dxMeters / (111320.0 * cos(Math.toRadians(centerLat)))
        val lat = centerLat + dyMeters / 110540.0
        return Pair(lat, lon)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("interactive_ftth_map")
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(0.35f, 4.5f)
                        panOffsetX += pan.x
                        panOffsetY += pan.y
                    }
                }
                .pointerInput(nodes, links, isCableDrawingMode) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()

                            // Hit-test on nodes (radius 36 pixels)
                            var clickedNode: FtthNodeEntity? = null
                            for (node in nodes) {
                                val nodePos = toScreenOffset(node.latitude, node.longitude, width, height)
                                val dist = hypot(nodePos.x - tapOffset.x, nodePos.y - tapOffset.y)
                                if (dist < 36f * zoom.coerceIn(0.8f, 1.4f)) {
                                    clickedNode = node
                                    break
                                }
                            }
                            onNodeSelected(clickedNode)
                        },
                        onLongPress = { pressOffset ->
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            val (lat, lon) = toGeoCoordinates(pressOffset.x, pressOffset.y, width, height)
                            onMapLongPress(lat, lon)
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height

            // 1. Draw Background Map Layer
            drawMapBaseLayer(activeMapLayer, width, height, zoom, panOffsetX, panOffsetY)

            // 2. Draw Cadastral parcels and street names
            drawCadastralNetwork(activeMapLayer, width, height, zoom, panOffsetX, panOffsetY)

            // 3. Draw Optical Cables / Links
            val nodeMap = nodes.associateBy { it.id }
            for (link in links) {
                val fromNode = nodeMap[link.fromNodeId]
                val toNode = nodeMap[link.toNodeId]
                if (fromNode != null && toNode != null) {
                    val p1 = toScreenOffset(fromNode.latitude, fromNode.longitude, width, height)
                    val p2 = toScreenOffset(toNode.latitude, toNode.longitude, width, height)
                    drawOpticalCable(link, p1, p2, zoom)
                }
            }

            // 4. Draw preview line if drawing cable
            if (isCableDrawingMode && cableFirstNode != null) {
                val p1 = toScreenOffset(cableFirstNode.latitude, cableFirstNode.longitude, width, height)
                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = 28f,
                    center = p1,
                    style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                )
            }

            // 5. Draw FTTH Nodes
            for (node in nodes) {
                val pos = toScreenOffset(node.latitude, node.longitude, width, height)
                val isSelected = selectedNode?.id == node.id
                val isCableFirst = cableFirstNode?.id == node.id

                drawFtthNodeMarker(
                    node = node,
                    screenPos = pos,
                    isSelected = isSelected || isCableFirst,
                    zoom = zoom
                )
            }

            // 6. Draw Technician GPS Location
            val techPos = toScreenOffset(technicianPosition.first, technicianPosition.second, width, height)
            drawTechnicianGpsMarker(techPos)
        }

        // Map HUD Overlay Controls (Top & Floating buttons)
        MapHudControls(
            zoom = zoom,
            activeLayer = activeMapLayer,
            isCableMode = isCableDrawingMode,
            onZoomIn = { zoom = (zoom * 1.25f).coerceIn(0.35f, 4.5f) },
            onZoomOut = { zoom = (zoom / 1.25f).coerceIn(0.35f, 4.5f) },
            onRecenter = {
                panOffsetX = 0f
                panOffsetY = 0f
                zoom = 1.0f
            },
            onCycleLayer = onCycleMapLayer,
            onToggleCableMode = onToggleCableMode,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// -------------------------------------------------------------
// Canvas Drawing Helpers
// -------------------------------------------------------------

private fun DrawScope.drawMapBaseLayer(
    layer: MapLayerType,
    width: Float,
    height: Float,
    zoom: Float,
    panX: Float,
    panY: Float
) {
    when (layer) {
        MapLayerType.CADASTRE -> {
            // Cadastral soft parchment background
            drawRect(Color(0xFFF7F5F0))

            // Road grid
            val roadColor = Color(0xFFE5DFD5)
            val curbColor = Color(0xFFD6CFC3)

            // Avenue des Lilas (Main axis: West to East)
            val aveY = height / 2f + panY
            drawRect(
                color = roadColor,
                topLeft = Offset(0f, aveY - 45f * zoom),
                size = Size(width, 90f * zoom)
            )
            drawLine(curbColor, Offset(0f, aveY - 45f * zoom), Offset(width, aveY - 45f * zoom), strokeWidth = 2f)
            drawLine(curbColor, Offset(0f, aveY + 45f * zoom), Offset(width, aveY + 45f * zoom), strokeWidth = 2f)
            // Centerline
            drawLine(
                Color(0xFFFFFFFF),
                Offset(0f, aveY),
                Offset(width, aveY),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 15f))
            )

            // Rue de la Fontaine (North axis)
            val rueX = width / 2f + 40f * zoom + panX
            drawRect(
                color = roadColor,
                topLeft = Offset(rueX - 35f * zoom, 0f),
                size = Size(70f * zoom, aveY + 45f * zoom)
            )
            drawLine(curbColor, Offset(rueX - 35f * zoom, 0f), Offset(rueX - 35f * zoom, aveY - 45f * zoom), strokeWidth = 2f)
            drawLine(curbColor, Offset(rueX + 35f * zoom, 0f), Offset(rueX + 35f * zoom, aveY - 45f * zoom), strokeWidth = 2f)

            // Allée des Roses (South East axis)
            val alleeX = width / 2f + 160f * zoom + panX
            drawRect(
                color = roadColor,
                topLeft = Offset(alleeX - 25f * zoom, aveY - 45f * zoom),
                size = Size(50f * zoom, height - aveY + 45f * zoom)
            )
        }

        MapLayerType.SATELLITE -> {
            // Dark satellite / aerial photo simulation
            drawRect(Color(0xFF1E2822))

            // Terrain vegetation & urban blocks
            drawCircle(Color(0xFF233527), radius = 220f * zoom, center = Offset(width * 0.3f + panX, height * 0.4f + panY))
            drawCircle(Color(0xFF1B2E24), radius = 280f * zoom, center = Offset(width * 0.7f + panX, height * 0.7f + panY))

            // Asphalt road corridors
            val aveY = height / 2f + panY
            drawRect(
                color = Color(0xFF2C3237),
                topLeft = Offset(0f, aveY - 40f * zoom),
                size = Size(width, 80f * zoom)
            )
            val rueX = width / 2f + 40f * zoom + panX
            drawRect(
                color = Color(0xFF2C3237),
                topLeft = Offset(rueX - 30f * zoom, 0f),
                size = Size(60f * zoom, aveY + 40f * zoom)
            )
            val alleeX = width / 2f + 160f * zoom + panX
            drawRect(
                color = Color(0xFF2C3237),
                topLeft = Offset(alleeX - 22f * zoom, aveY - 40f * zoom),
                size = Size(44f * zoom, height - aveY + 40f * zoom)
            )
        }

        MapLayerType.RUES -> {
            // Clean high contrast architectural map
            drawRect(Color(0xFFF1F5F9))

            val aveY = height / 2f + panY
            drawRect(
                color = Color(0xFFFFFFFF),
                topLeft = Offset(0f, aveY - 40f * zoom),
                size = Size(width, 80f * zoom)
            )
            drawLine(Color(0xFFCBD5E1), Offset(0f, aveY - 40f * zoom), Offset(width, aveY - 40f * zoom), strokeWidth = 3f)
            drawLine(Color(0xFFCBD5E1), Offset(0f, aveY + 40f * zoom), Offset(width, aveY + 40f * zoom), strokeWidth = 3f)

            val rueX = width / 2f + 40f * zoom + panX
            drawRect(
                color = Color(0xFFFFFFFF),
                topLeft = Offset(rueX - 30f * zoom, 0f),
                size = Size(60f * zoom, aveY + 40f * zoom)
            )
            drawLine(Color(0xFFCBD5E1), Offset(rueX - 30f * zoom, 0f), Offset(rueX - 30f * zoom, aveY - 40f * zoom), strokeWidth = 3f)
            drawLine(Color(0xFFCBD5E1), Offset(rueX + 30f * zoom, 0f), Offset(rueX + 30f * zoom, aveY - 40f * zoom), strokeWidth = 3f)
        }
    }
}

private fun DrawScope.drawCadastralNetwork(
    layer: MapLayerType,
    width: Float,
    height: Float,
    zoom: Float,
    panX: Float,
    panY: Float
) {
    val parcelLineColor = if (layer == MapLayerType.SATELLITE) Color(0x55FFFFFF) else Color(0x6694A3B8)
    val buildingColor = when (layer) {
        MapLayerType.SATELLITE -> Color(0x66475569)
        MapLayerType.CADASTRE -> Color(0xFFEBE6DC)
        MapLayerType.RUES -> Color(0xFFE2E8F0)
    }

    val aveY = height / 2f + panY
    val rueX = width / 2f + 40f * zoom + panX

    // Building footprint: Residence Le Belvédère (IMM-501)
    val immX = rueX + 50f * zoom
    val immY = aveY - 140f * zoom
    drawRoundRect(
        color = buildingColor,
        topLeft = Offset(immX, immY),
        size = Size(110f * zoom, 70f * zoom),
        cornerRadius = CornerRadius(6f, 6f)
    )
    drawRoundRect(
        color = parcelLineColor,
        topLeft = Offset(immX, immY),
        size = Size(110f * zoom, 70f * zoom),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = 1.5f)
    )

    // Villa 601 footprint
    val vil1X = width / 2f + 195f * zoom + panX
    val vil1Y = aveY + 70f * zoom
    drawRoundRect(
        color = buildingColor,
        topLeft = Offset(vil1X, vil1Y),
        size = Size(65f * zoom, 50f * zoom),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawRoundRect(
        color = parcelLineColor,
        topLeft = Offset(vil1X, vil1Y),
        size = Size(65f * zoom, 50f * zoom),
        cornerRadius = CornerRadius(4f, 4f),
        style = Stroke(width = 1.5f)
    )

    // Villa 602 footprint
    val vil2X = width / 2f + 70f * zoom + panX
    val vil2Y = aveY + 80f * zoom
    drawRoundRect(
        color = buildingColor,
        topLeft = Offset(vil2X, vil2Y),
        size = Size(60f * zoom, 45f * zoom),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawRoundRect(
        color = parcelLineColor,
        topLeft = Offset(vil2X, vil2Y),
        size = Size(60f * zoom, 45f * zoom),
        cornerRadius = CornerRadius(4f, 4f),
        style = Stroke(width = 1.5f)
    )

    // Street text labels
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = if (layer == MapLayerType.SATELLITE) 0xCCFFFFFF.toInt() else 0xCC475569.toInt()
            textSize = 28f * zoom.coerceIn(0.7f, 1.2f)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            isAntiAlias = true
        }
        drawText("Avenue des Lilas", 60f + panX, aveY + 8f, paint)
        drawText("Rue de la Fontaine", rueX - 10f, aveY - 180f * zoom, paint)
        drawText("Allée des Roses", width / 2f + 200f * zoom + panX, aveY + 180f * zoom, paint)
    }
}

private fun DrawScope.drawOpticalCable(
    link: FtthLinkEntity,
    p1: Offset,
    p2: Offset,
    zoom: Float
) {
    val cableColor = when (link.cableType.lowercase()) {
        "transport" -> Color(0xFF8B5CF6)     // Purple 144FO
        "distribution" -> Color(0xFF0284C7)  // Cyan Blue 48FO/24FO
        else -> Color(0xFFF97316)            // Orange Branchement 1FO
    }

    val strokeW = when (link.cableType.lowercase()) {
        "transport" -> 5.5f * zoom.coerceIn(0.8f, 1.3f)
        "distribution" -> 4.0f * zoom.coerceIn(0.8f, 1.3f)
        else -> 2.5f * zoom.coerceIn(0.8f, 1.3f)
    }

    val pathEffect = if (link.installationType.contains("Aérien", ignoreCase = true)) {
        PathEffect.dashPathEffect(floatArrayOf(14f, 8f))
    } else null

    // Draw shadow/glow
    drawLine(
        color = Color(0x33000000),
        start = p1 + Offset(2f, 2f),
        end = p2 + Offset(2f, 2f),
        strokeWidth = strokeW,
        pathEffect = pathEffect
    )

    // Draw main fiber cable
    drawLine(
        color = cableColor,
        start = p1,
        end = p2,
        strokeWidth = strokeW,
        pathEffect = pathEffect
    )

    // Draw midpoint label pill (Length + Fiber Count)
    val mid = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
    if (zoom >= 0.7f) {
        val labelText = "${link.lengthMeters.toInt()}m • ${link.capacityFO}FO"
        drawContext.canvas.nativeCanvas.apply {
            val paintText = android.graphics.Paint().apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 22f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            val bgPaint = android.graphics.Paint().apply {
                color = 0xEE0F172A.toInt()
                style = android.graphics.Paint.Style.FILL
                isAntiAlias = true
            }
            val textWidth = paintText.measureText(labelText)
            val rect = android.graphics.RectF(
                mid.x - textWidth / 2 - 12f,
                mid.y - 16f,
                mid.x + textWidth / 2 + 12f,
                mid.y + 12f
            )
            drawRoundRect(rect, 8f, 8f, bgPaint)
            drawText(labelText, mid.x, mid.y + 5f, paintText)
        }
    }
}

private fun DrawScope.drawFtthNodeMarker(
    node: FtthNodeEntity,
    screenPos: Offset,
    isSelected: Boolean,
    zoom: Float
) {
    val nodeColor = FtthNodeVisuals.getNodeColor(node.type)
    val statusColor = FtthNodeVisuals.getStatusColor(node.status)
    val markerRadius = 20f * zoom.coerceIn(0.85f, 1.4f)

    // Selection pulse ring
    if (isSelected) {
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = markerRadius + 14f,
            center = screenPos,
            style = Stroke(width = 3.5f)
        )
        drawCircle(
            color = Color(0x3300E5FF),
            radius = markerRadius + 10f,
            center = screenPos
        )
    }

    // Shadow
    drawCircle(
        color = Color(0x40000000),
        radius = markerRadius,
        center = screenPos + Offset(2f, 3f)
    )

    // Main node body based on type
    when (node.type) {
        FtthNodeType.POTEAU -> {
            // Pole: Circle with inner post cross
            drawCircle(color = nodeColor, radius = markerRadius, center = screenPos)
            drawCircle(color = Color.White, radius = markerRadius * 0.45f, center = screenPos)
            drawLine(nodeColor, screenPos - Offset(markerRadius * 0.7f, 0f), screenPos + Offset(markerRadius * 0.7f, 0f), strokeWidth = 3f)
            drawLine(nodeColor, screenPos - Offset(0f, markerRadius * 0.7f), screenPos + Offset(0f, markerRadius * 0.7f), strokeWidth = 3f)
        }

        FtthNodeType.CHAMBRE -> {
            // Chamber: Rounded Rectangle with manhole hatch lines
            val size = markerRadius * 2f
            val half = size / 2f
            drawRoundRect(
                color = nodeColor,
                topLeft = screenPos - Offset(half, half),
                size = Size(size, size),
                cornerRadius = CornerRadius(6f, 6f)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = screenPos - Offset(half * 0.6f, half * 0.6f),
                size = Size(size * 0.6f, size * 0.6f),
                cornerRadius = CornerRadius(3f, 3f),
                style = Stroke(width = 2.5f)
            )
        }

        FtthNodeType.BOITIER -> {
            // Boîtier (BPE / PBO / PB): Hexagonal / Terminal badge shape
            val size = markerRadius * 2.1f
            drawRoundRect(
                color = nodeColor,
                topLeft = screenPos - Offset(size / 2f, size / 2f),
                size = Size(size, size),
                cornerRadius = CornerRadius(8f, 8f)
            )
            drawCircle(color = Color.White, radius = markerRadius * 0.4f, center = screenPos)
        }

        FtthNodeType.SRO -> {
            // SRO: Central Optical Cabinet / Armoire de rue (dual doors)
            val w = markerRadius * 2.5f
            val h = markerRadius * 2.2f
            drawRoundRect(
                color = nodeColor,
                topLeft = screenPos - Offset(w / 2f, h / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(5f, 5f)
            )
            // Cabinet door divider
            drawLine(
                color = Color.White,
                start = screenPos - Offset(0f, h * 0.45f),
                end = screenPos + Offset(0f, h * 0.45f),
                strokeWidth = 2.5f
            )
            // Optical rack slots
            drawCircle(color = Color(0xFF38BDF8), radius = markerRadius * 0.25f, center = screenPos - Offset(w * 0.25f, 0f))
            drawCircle(color = Color(0xFF38BDF8), radius = markerRadius * 0.25f, center = screenPos + Offset(w * 0.25f, 0f))
        }

        FtthNodeType.IMMEUBLE -> {
            // Collective Building: Stately rectangle with entrance cutout
            val w = markerRadius * 2.3f
            val h = markerRadius * 1.9f
            drawRoundRect(
                color = nodeColor,
                topLeft = screenPos - Offset(w / 2f, h / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(5f, 5f)
            )
            drawRect(
                color = Color.White,
                topLeft = screenPos - Offset(w * 0.2f, -h * 0.15f),
                size = Size(w * 0.4f, h * 0.35f)
            )
        }

        FtthNodeType.VILLA -> {
            // Villa: House shape (Triangle roof + square body)
            val path = Path().apply {
                moveTo(screenPos.x, screenPos.y - markerRadius * 1.1f)
                lineTo(screenPos.x + markerRadius * 1.1f, screenPos.y - markerRadius * 0.1f)
                lineTo(screenPos.x + markerRadius * 0.9f, screenPos.y + markerRadius * 0.9f)
                lineTo(screenPos.x - markerRadius * 0.9f, screenPos.y + markerRadius * 0.9f)
                lineTo(screenPos.x - markerRadius * 1.1f, screenPos.y - markerRadius * 0.1f)
                close()
            }
            drawPath(path, color = nodeColor)
            drawPath(path, color = Color.White, style = Stroke(width = 2.5f))
        }
    }

    // Status & Conformity Indicator Dot (Top Right)
    val statusPos = screenPos + Offset(markerRadius * 0.85f, -markerRadius * 0.85f)
    val conformityColor = FtthNodeVisuals.getConformityColor(node.etat)
    drawCircle(color = Color.White, radius = 9.5f, center = statusPos)
    drawCircle(color = statusColor, radius = 8f, center = statusPos)
    drawCircle(color = conformityColor, radius = 5f, center = statusPos)

    // Node ID Label Pill beneath marker
    drawContext.canvas.nativeCanvas.apply {
        val paintText = android.graphics.Paint().apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 21f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val textWidth = paintText.measureText(node.id)
        val pillBg = android.graphics.Paint().apply {
            color = 0xDD0F172A.toInt()
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
        val pillBorder = android.graphics.Paint().apply {
            color = if (isSelected) 0xFF00E5FF.toInt() else 0x55FFFFFF.toInt()
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        val labelY = screenPos.y + markerRadius + 18f
        val rect = android.graphics.RectF(
            screenPos.x - textWidth / 2 - 10f,
            labelY - 16f,
            screenPos.x + textWidth / 2 + 10f,
            labelY + 9f
        )
        drawRoundRect(rect, 6f, 6f, pillBg)
        drawRoundRect(rect, 6f, 6f, pillBorder)
        drawText(node.id, screenPos.x, labelY, paintText)
    }
}

private fun DrawScope.drawTechnicianGpsMarker(pos: Offset) {
    // GPS pulse circle
    drawCircle(
        color = Color(0x330284C7),
        radius = 35f,
        center = pos
    )
    drawCircle(
        color = Color(0x660284C7),
        radius = 22f,
        center = pos
    )
    // Blue dot with white rim
    drawCircle(
        color = Color.White,
        radius = 11f,
        center = pos
    )
    drawCircle(
        color = Color(0xFF0284C7),
        radius = 8f,
        center = pos
    )
}

// -------------------------------------------------------------
// Floating Map HUD Controls
// -------------------------------------------------------------

@Composable
private fun MapHudControls(
    zoom: Float,
    activeLayer: MapLayerType,
    isCableMode: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
    onCycleLayer: () -> Unit,
    onToggleCableMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.padding(16.dp)) {
        // Top right: Layer toggle & Cable drawing mode button
        Column(
            modifier = Modifier.align(Alignment.TopEnd),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Layer button with current layer label
            Surface(
                onClick = onCycleLayer,
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 4.dp,
                modifier = Modifier.testTag("map_layer_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Changer couche cartographique",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = activeLayer.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Cable Link Drawing Mode FAB
            FloatingActionButton(
                onClick = onToggleCableMode,
                containerColor = if (isCableMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surface,
                contentColor = if (isCableMode) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("cable_drawing_mode_fab")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.AltRoute,
                    contentDescription = "Mode création liaison fibre",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Bottom Left: Scale Bar & North Compass
        Column(
            modifier = Modifier.align(Alignment.BottomStart),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Mini North Compass
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shadowElevation = 3.dp,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Nord",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Distance Scale Bar
            val metersPer100px = (100f / (2.4f * zoom)).roundToInt()
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$metersPer100px m",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.onSurface)
                    )
                }
            }
        }

        // Bottom Right: Zoom In, Zoom Out & My Location GPS
        Column(
            modifier = Modifier.align(Alignment.BottomEnd),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFloatingActionButton(
                onClick = onRecenter,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("recenter_gps_button")
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Me géolocaliser")
            }

            SmallFloatingActionButton(
                onClick = onZoomIn,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("zoom_in_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom avant")
            }

            SmallFloatingActionButton(
                onClick = onZoomOut,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("zoom_out_button")
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom arrière")
            }
        }
    }
}
