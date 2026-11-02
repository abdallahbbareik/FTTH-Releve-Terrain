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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import com.example.data.local.FtthNodeType
import com.example.data.util.KmzNodeClassifier
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
    var selectedLinkId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val selectedNode = remember(selectedNodeId, graph) {
        graph.nodes.find { it.id == selectedNodeId }
    }

    val selectedLink = remember(selectedLinkId, graph) {
        graph.links.find { it.id == selectedLinkId }
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

                    val isHighlighted = (selectedNodeId != null && (selectedNodeId == link.fromNodeId || selectedNodeId == link.toNodeId)) ||
                            (selectedLinkId == link.id)
                    val isDimmed = (selectedNodeId != null || selectedLinkId != null) && !isHighlighted

                    val startX = fromNode.x + 170f
                    val startY = fromNode.y + 45f
                    val endX = toNode.x
                    val endY = toNode.y + 45f

                    val isSameRow = Math.abs(startY - endY) < 5f

                    val path = Path().apply {
                        moveTo(startX, startY)
                        if (isSameRow) {
                            // Ligne horizontale continue pour les nœuds d'un même câble
                            lineTo(endX, endY)
                        } else {
                            // Ligne en forme de L pour les embranchements :
                            // 1. Ligne verticale pour l'espacement (du boîtier parent jusqu'au niveau Y de la ligne enfant)
                            // 2. Ligne horizontale du bas de la ligne verticale jusqu'au nœud enfant
                            val cornerRadius = 18f
                            val direction = if (endY > startY) 1f else -1f

                            lineTo(startX, endY - direction * cornerRadius)
                            quadraticTo(startX, endY, startX + cornerRadius, endY)
                            lineTo(endX, endY)
                        }
                    }

                    val lineColor = when {
                        isHighlighted -> Color(0xFFDC2626) // Rouge éclatant pour l'élément sélectionné
                        link.capacityFO.contains("144") || link.capacityFO.contains("288") -> Color(0xFF2563EB)
                        link.capacityFO.contains("72") || link.capacityFO.contains("48") -> Color(0xFFD97706)
                        else -> Color(0xFF0D9488)
                    }

                    val strokeWidth = if (isHighlighted) 4.5.dp.toPx() else 2.5.dp.toPx()
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

            // Dessin des Étiquettes « Cable » et « Support_Traversés » sur les Liaisons (selon hiérarchie utilisateur)
            val nodePosMap = graph.nodes.associateBy { it.id }
            for (link in graph.links) {
                val fromNode = nodePosMap[link.fromNodeId] ?: continue
                val toNode = nodePosMap[link.toNodeId] ?: continue

                val startX = fromNode.x + 170f
                val startY = fromNode.y + 45f
                val endX = toNode.x
                val endY = toNode.y + 45f

                val isSameRow = Math.abs(startY - endY) < 5f
                val isLinkSelected = selectedLinkId == link.id

                val cableTagX: Float
                val cableTagY: Float
                val suppTagX: Float
                val suppTagY: Float

                if (isSameRow) {
                    cableTagX = startX + 25f
                    cableTagY = startY - 24f

                    suppTagX = startX + 260f
                    suppTagY = endY + 8f
                } else {
                    // Sur le tronçon horizontal de la ligne en L (à Y = endY, après le virage vertical)
                    cableTagX = startX + 30f
                    cableTagY = endY - 24f

                    suppTagX = startX + 260f
                    suppTagY = endY + 8f
                }

                // Badge « Cable - Type - Longueur » (au-dessus du connecteur)
                val connectorLabel = if (link.segmentLengthMeters > 0) {
                    "${link.cableName} - ${link.installationType} - ${String.format(Locale.ROOT, "%.1fm", link.segmentLengthMeters)}"
                } else {
                    "${link.cableName} - ${link.installationType}"
                }

                val connColor = if (link.installationType.contains("Aérien", ignoreCase = true) || link.installationType.contains("Aerien", ignoreCase = true)) {
                    Color(0xFF15803D) // Vert foncé pour Aérien
                } else {
                    Color(0xFFC2410C) // Orangé foncé pour Conduite
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(cableTagX.roundToInt(), cableTagY.roundToInt()) }
                        .clickable {
                            selectedLinkId = if (isLinkSelected) null else link.id
                            selectedNodeId = null
                        }
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isLinkSelected) Color(0xFFDC2626) else connColor,
                        contentColor = Color.White,
                        shadowElevation = 3.dp
                    ) {
                        Text(
                            text = connectorLabel,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Badge « Supports Traversés » (en-dessous du connecteur)
                if (link.supportsTraversesStr.isNotBlank() && link.supportsTraversesStr != "-") {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(suppTagX.roundToInt(), suppTagY.roundToInt()) }
                            .clickable {
                                selectedLinkId = if (isLinkSelected) null else link.id
                                selectedNodeId = null
                            }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isLinkSelected) Color(0xFFDC2626) else Color(0xFF475569),
                            contentColor = Color.White,
                            shadowElevation = 3.dp
                        ) {
                            Text(
                                text = "Supports: ${link.supportsTraversesStr}",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Dessin des cartes de Nœuds (SRO, BPE et PBO) selon le style Excel Synoptique
            for (node in graph.nodes) {
                val isSelected = selectedNodeId == node.id
                val isMatchingSearch = matchingNodeIds.contains(node.id)
                val isDimmed = (selectedNodeId != null || selectedLinkId != null) && !isSelected &&
                        graph.links.none { (it.fromNodeId == node.id && it.toNodeId == selectedNodeId) || (it.toNodeId == node.id && it.fromNodeId == selectedNodeId) }

                val alpha = if (isDimmed) 0.35f else 1.0f
                val badgeColor = when (node.nodeTypeBadge) {
                    "SRO" -> SroPurple
                    "PBO" -> Color(0xFFD97706)
                    else -> BoitierTeal
                }

                val cardBorderColor = when {
                    isSelected -> Color(0xFFDC2626)
                    isMatchingSearch -> Color(0xFFF59E0B)
                    else -> badgeColor
                }

                val isAerien = node.hostingSupportName.contains("Poteau", ignoreCase = true) ||
                        node.hostingSupportName.contains("POT", ignoreCase = true) ||
                        node.hostingSupportName.contains("Aérien", ignoreCase = true) ||
                        node.rawNode.type == FtthNodeType.POTEAU

                val suppDisplayName = when {
                    node.hostingSupportName.isNotBlank() && node.hostingSupportName != "-" -> {
                        val cleaned = node.hostingSupportName.substringAfter("[").substringBefore("]").trim()
                        if (cleaned.isNotBlank()) cleaned else node.hostingSupportName
                    }
                    else -> node.name
                }

                val classifiedNode = KmzNodeClassifier.classifyNode(node.rawNode)
                val refText = when {
                    classifiedNode.reference.isNotBlank() && classifiedNode.reference != "-" -> classifiedNode.reference
                    node.nodeTypeBadge.isNotBlank() && node.nodeTypeBadge != "-" -> node.nodeTypeBadge
                    else -> "BPE"
                }
                val modeDePose = if (isAerien) "Aérien" else "GC"
                val refAndModeText = "$refText - $modeDePose"

                val headerBgColor = if (isAerien) Color(0xFFDCFCE7) else Color(0xFFFED7AA) // Vert clair pour Aérien, Orangé clair pour GC
                val headerTextColor = if (isAerien) Color(0xFF15803D) else Color(0xFF9A3412) // Vert foncé / Orangé foncé

                val inList = if (node.incomingCableName.isNotBlank() && node.incomingCableName != "-") {
                    node.incomingCableName.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }
                } else emptyList()

                val outList = if (node.outgoingCablesStr.isNotBlank() && node.outgoingCablesStr != "-") {
                    node.outgoingCablesStr.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }
                } else emptyList()

                val maxRows = maxOf(inList.size, outList.size, 1)
                val tablePairs = (0 until maxRows).map { i ->
                    val inName = inList.getOrNull(i) ?: inList.lastOrNull() ?: "-"
                    val outName = outList.getOrNull(i) ?: "-"
                    Pair(inName, outName)
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(node.x.roundToInt(), node.y.roundToInt()) }
                        .width(185.dp)
                        .graphicsLayer(alpha = alpha)
                        .clickable {
                            selectedNodeId = if (isSelected) null else node.id
                            selectedLinkId = null
                        }
                ) {
                    Card(
                        shape = RoundedCornerShape(6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFFFEF2F2) else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 3.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected || isMatchingSearch) 2.5.dp else 1.2.dp,
                            color = cardBorderColor
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 1. En-tête Excel : Support (Gauche) & Longueur cumulée (Droite)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(headerBgColor)
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = suppDisplayName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = headerTextColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = String.format(Locale.ROOT, "%.2f m", node.cumulativeDistanceMeters),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = headerTextColor,
                                    maxLines = 1
                                )
                            }

                            HorizontalDivider(color = headerTextColor.copy(alpha = 0.3f), thickness = 1.dp)

                            // 2. Nom du boîtier sur une ligne dédiée (ex: JOINT-SPLIT 1/8)
                            Text(
                                text = node.name,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E3A8A), // Bleu foncé
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )

                            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                            // 3. Référence - Mode de pose (ex: ODC48 - GC / PBO - Aérien)
                            Text(
                                text = refAndModeText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF334155),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )

                            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                            // 4. Coordonnées GPS (Longitude, Latitude, 0)
                            Text(
                                text = "${node.rawNode.longitude},${node.rawNode.latitude},0",
                                fontSize = 8.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = Color(0xFF475569),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )

                            HorizontalDivider(color = Color(0xFFCBD5E1), thickness = 1.dp)

                            // 4. Tableau Excel In / Out : Cable entré | Cables Sorties
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE0F2FE))
                            ) {
                                Text(
                                    text = "Cable entré",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0369A1),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                                VerticalDivider(
                                    modifier = Modifier.height(18.dp),
                                    color = Color(0xFFBAE6FD),
                                    thickness = 1.dp
                                )
                                Text(
                                    text = "Cables Sorties",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0369A1),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }

                            HorizontalDivider(color = Color(0xFFBAE6FD), thickness = 1.dp)

                            // 5. Lignes du Tableau In / Out
                            tablePairs.forEachIndexed { idx, pair ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (idx % 2 == 0) Color.White else Color(0xFFF8FAFC))
                                ) {
                                    Text(
                                        text = pair.first,
                                        fontSize = 8.sp,
                                        color = Color(0xFF1E293B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                    VerticalDivider(
                                        modifier = Modifier.height(16.dp),
                                        color = Color(0xFFE2E8F0),
                                        thickness = 1.dp
                                    )
                                    Text(
                                        text = pair.second,
                                        fontSize = 8.sp,
                                        color = Color(0xFF1E293B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                                if (idx < tablePairs.size - 1) {
                                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                                }
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

        // 4. Panneau de détails dépliable lors du clic sur un lien (Câble + Supports Traversés)
        AnimatedVisibility(
            visible = selectedLink != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (selectedLink != null) {
                val fromNode = graph.nodes.find { it.id == selectedLink.fromNodeId }
                val toNode = graph.nodes.find { it.id == selectedLink.toNodeId }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD97706))
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
                                    color = Color(0xFFD97706),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Cable, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Câble : ${selectedLink.cableName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TelecomNavy
                                    )
                                    Text(
                                        text = "Liaison : ${fromNode?.name ?: "?"} ➔ ${toNode?.name ?: "?"} (${selectedLink.capacityFO})",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = { selectedLinkId = null },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Fermer")
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFFBEB),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "SUPPORTS TRAVERSÉS (CHAMBRES / POTEAUX)",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = selectedLink.supportsTraversesStr,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TelecomNavy
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Légende hiérarchique en bas à gauche
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.White.copy(alpha = 0.92f),
            shadowElevation = 3.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(SroPurple, CircleShape))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("SRO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SroPurple)
                }
                Text("•", fontSize = 9.sp, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(BoitierTeal, CircleShape))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("BPE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BoitierTeal)
                }
                Text("•", fontSize = 9.sp, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFD97706), CircleShape))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("PBO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                }
                Text("•", fontSize = 9.sp, color = Color.Gray)
                Text("Cable ➔ Support_Traversés", fontSize = 8.5.sp, fontWeight = FontWeight.Medium, color = TelecomNavy)
            }
        }
    }
}
