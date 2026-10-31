package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.FtthNodeEntity
import com.example.data.util.BoitierConnectivity
import com.example.data.util.ClassifiedCable
import com.example.data.util.KmzNodeCategory
import com.example.data.util.SynopticGraph
import com.example.data.util.SynopticLink
import com.example.data.util.SynopticNode
import java.util.Locale
import kotlin.math.roundToInt

private val TelecomNavy = Color(0xFF1E3A8A)
private val TelecomCyan = Color(0xFF06B6D4)
private val SroPurple = Color(0xFF9333EA)
private val BoitierTeal = Color(0xFF0D9488)

@Composable
fun FtthSynopticCanvasView(
    graph: SynopticGraph,
    connectivities: Map<String, BoitierConnectivity>,
    classifiedCables: List<ClassifiedCable>,
    onSelectNodeOnMap: (FtthNodeEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val selectedNode = remember(selectedNodeId, graph) {
        graph.nodes.find { it.id == selectedNodeId }
    }

    val selectedConn = remember(selectedNodeId, connectivities) {
        if (selectedNodeId != null) connectivities[selectedNodeId] else null
    }

    // Nœuds filtrés pour la recherche
    val matchingNodeIds = remember(searchQuery, graph) {
        if (searchQuery.isBlank()) emptySet()
        else {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            graph.nodes.filter {
                it.name.lowercase(Locale.ROOT).contains(q) ||
                        it.incomingCableName.lowercase(Locale.ROOT).contains(q) ||
                        it.outgoingCablesStr.lowercase(Locale.ROOT).contains(q)
            }.map { it.id }.toSet()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
        // 1. Zone Canvas Graphique avec Pan et Zoom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.4f, 3.5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        ) {
            // Dessin des liaisons (câbles)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val nodePosMap = graph.nodes.associateBy { it.id }

                for (link in graph.links) {
                    val fromNode = nodePosMap[link.fromNodeId] ?: continue
                    val toNode = nodePosMap[link.toNodeId] ?: continue

                    val isHighlighted = selectedNodeId != null &&
                            (selectedNodeId == link.fromNodeId || selectedNodeId == link.toNodeId)
                    val isDimmed = selectedNodeId != null && !isHighlighted

                    val startX = fromNode.x + 140f
                    val startY = fromNode.y + 30f
                    val endX = toNode.x
                    val endY = toNode.y + 30f

                    val controlX1 = startX + (endX - startX) * 0.5f
                    val controlY1 = startY
                    val controlX2 = startX + (endX - startX) * 0.5f
                    val controlY2 = endY

                    val path = Path().apply {
                        moveTo(startX, startY)
                        cubicTo(controlX1, controlY1, controlX2, controlY2, endX, endY)
                    }

                    val lineColor = when {
                        isHighlighted -> Color(0xFFDC2626) // Rouge éclatant pour l'élément sélectionné
                        link.capacityFO.contains("144") || link.capacityFO.contains("288") -> Color(0xFF2563EB)
                        link.capacityFO.contains("72") || link.capacityFO.contains("48") -> Color(0xFFD97706)
                        else -> Color(0xFF0D9488)
                    }

                    val strokeWidth = if (isHighlighted) 4.dp.toPx() else 2.5.dp.toPx()
                    val alpha = if (isDimmed) 0.25f else 1.0f

                    drawPath(
                        path = path,
                        color = lineColor.copy(alpha = alpha),
                        style = Stroke(
                            width = strokeWidth,
                            cap = StrokeCap.Round,
                            pathEffect = if (isHighlighted) null else PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
                        )
                    )
                }
            }

            // Dessin des cartes de Nœuds (SRO et Boîtiers)
            for (node in graph.nodes) {
                val isSelected = selectedNodeId == node.id
                val isMatchingSearch = matchingNodeIds.contains(node.id)
                val isDimmed = selectedNodeId != null && !isSelected &&
                        graph.links.none { (it.fromNodeId == node.id && it.toNodeId == selectedNodeId) || (it.toNodeId == node.id && it.fromNodeId == selectedNodeId) }

                val alpha = if (isDimmed) 0.35f else 1.0f
                val cardBorderColor = when {
                    isSelected -> Color(0xFFDC2626)
                    isMatchingSearch -> Color(0xFFF59E0B)
                    node.isSro -> SroPurple
                    else -> BoitierTeal
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(node.x.roundToInt(), node.y.roundToInt()) }
                        .width(150.dp)
                        .graphicsLayer(alpha = alpha)
                        .clickable { selectedNodeId = if (isSelected) null else node.id }
                ) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFFFEF2F2) else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 3.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected || isMatchingSearch) 2.5.dp else 1.dp,
                            color = cardBorderColor
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (node.isSro) SroPurple.copy(alpha = 0.15f) else BoitierTeal.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (node.isSro) "SRO" else "Boîtier",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (node.isSro) SroPurple else BoitierTeal,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }

                                Icon(
                                    imageVector = if (node.isSro) Icons.Default.SettingsInputComponent else Icons.Default.Cable,
                                    contentDescription = null,
                                    tint = if (node.isSro) SroPurple else BoitierTeal,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = node.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = TelecomNavy,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (node.incomingCableName.isNotBlank() && node.incomingCableName != "-") {
                                Text(
                                    text = "In: ${node.incomingCableName}",
                                    fontSize = 9.sp,
                                    color = Color(0xFF0369A1),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (node.outgoingCablesStr.isNotBlank() && node.outgoingCablesStr != "-") {
                                Text(
                                    text = "Out: ${node.outgoingCablesStr}",
                                    fontSize = 9.sp,
                                    color = Color(0xFF0D9488),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Outils de Navigation (Barre de recherche + Contrôles de Zoom) en superposition
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.White,
                shadowElevation = 4.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Rechercher",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TelecomNavy),
                        modifier = Modifier.weight(1f).testTag("synoptic_search_input"),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Chercher un boîtier dans le synoptique...",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Effacer", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // Contrôles de Zoom en bas à droite
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = TelecomNavy,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .size(36.dp)
                    .clickable { scale = (scale + 0.25f).coerceAtMost(3.5f) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom in", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            Surface(
                shape = CircleShape,
                color = TelecomNavy,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .size(36.dp)
                    .clickable { scale = (scale - 0.25f).coerceAtLeast(0.4f) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom out", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            Surface(
                shape = CircleShape,
                color = TelecomCyan,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .size(36.dp)
                    .clickable {
                        scale = 1.0f
                        offsetX = 0f
                        offsetY = 0f
                        selectedNodeId = null
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        // 3. Panneau de détails dépliable lors du clic sur un nœud
        AnimatedVisibility(
            visible = selectedNode != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (selectedNode != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TelecomNavy)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (selectedNode.isSro) SroPurple else BoitierTeal,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (selectedNode.isSro) Icons.Default.SettingsInputComponent else Icons.Default.Cable,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = selectedNode.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TelecomNavy
                                    )
                                    Text(
                                        text = "Catégorie : ${selectedNode.category.label}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { onSelectNodeOnMap(selectedNode.rawNode) },
                                    colors = ButtonDefaults.buttonColors(containerColor = TelecomNavy),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Voir sur Carte", fontSize = 10.sp)
                                }

                                IconButton(
                                    onClick = { selectedNodeId = null },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Fermer")
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Câble d'entrée
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF0F9FF),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("CÂBLE ENTRÉE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = selectedNode.incomingCableName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TelecomNavy,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Câbles de sortie
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF0FDFA),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0D9488)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("CÂBLES SORTIE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D9488))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = selectedNode.outgoingCablesStr,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TelecomNavy,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
