package com.example.ui.components.synoptic

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.apd.ApdAnalysisResult
import com.example.data.apd.ApdAnomaly
import com.example.data.apd.ApdCable
import com.example.data.apd.ApdObjectType
import com.example.data.apd.ApdPoint
import java.util.Locale

/**
 * Écran principal de visualisation interactive du Synoptique FTTH APD
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FtthSynopticScreen(
    result: ApdAnalysisResult,
    documentTitle: String,
    onBackClick: () -> Unit,
    onExportPdfClick: () -> Unit,
    onExportExcelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Synoptique", "BPE/PBO (${result.bpePboList.size})", "Câbles (${result.cablesList.size})", "Anomalies (${result.anomalies.size})")

    var selectedItemDetail by remember { mutableStateOf<Any?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Synoptique FTTH APD",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = documentTitle.ifBlank { "Projet FTTH" },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour", tint = Color.White)
                    }
                },
                actions = {
                    Button(
                        onClick = onExportPdfClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF", fontSize = 12.sp)
                    }
                    Button(
                        onClick = onExportExcelClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Excel", fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Barre d'onglets
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            if (index == 3 && result.anomalies.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text("${result.anomalies.size}") } }) {
                                    Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal)
                                }
                            } else {
                                Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    )
                }
            }

            // Contenu selon l'onglet
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> SynopticCanvasView(
                        result = result,
                        searchQuery = searchQuery,
                        onSelectPoint = { selectedItemDetail = it },
                        onSelectCable = { selectedItemDetail = it }
                    )
                    1 -> BpePboListView(
                        bpeList = result.bpePboList,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onSelectPoint = { selectedItemDetail = it }
                    )
                    2 -> CablesListView(
                        cablesList = result.cablesList,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onSelectCable = { selectedItemDetail = it }
                    )
                    3 -> AnomaliesListView(
                        anomalies = result.anomalies,
                        onSelectAnomaly = { selectedItemDetail = it }
                    )
                }

                // Fiche détaillée au clic
                if (selectedItemDetail != null) {
                    DetailInspectorCard(
                        item = selectedItemDetail!!,
                        onDismiss = { selectedItemDetail = null },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Composant de dessin interactif Canvas pour l'arborescence du Synoptique
 */
@Composable
fun SynopticCanvasView(
    result: ApdAnalysisResult,
    searchQuery: String,
    onSelectPoint: (ApdPoint) -> Unit,
    onSelectCable: (ApdCable) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offsetX by remember { mutableFloatStateOf(50f) }
    var offsetY by remember { mutableFloatStateOf(100f) }

    val textMeasurer = rememberTextMeasurer()

    val boxWidth = 220f
    val boxHeight = 160f
    val colSpacing = 160f
    val rowSpacing = 220f

    val bpeMap = remember(result) { result.bpePboList.associateBy { it.id } }
    val cableMap = remember(result) { result.cablesList.associateBy { it.nom } }

    val sroIdx = remember(result) { result.bpePboList.indexOfFirst { it.objectType == ApdObjectType.SRO } }
    val sroPoint = if (sroIdx >= 0) result.bpePboList[sroIdx] else null

    val layoutRowsMap = remember(result) {
        com.example.data.apd.FtthApdTopologyEngine.layoutRows(result.childrenOf, result.roots)
    }

    val parentOf = remember(result) {
        val pMap = mutableMapOf<String, String>()
        for ((parent, kids) in result.childrenOf) {
            for (k in kids) {
                pMap[k] = parent
            }
        }
        pMap
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.15f, 4.0f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
        ) {
            val scaleMatrix = scale
            val transX = offsetX
            val transY = offsetY

            // 1. Dessiner SRO Racine à gauche (Colonne 0)
            val sroX = transX
            val sroY = transY + 100f * scaleMatrix

            drawApdBox(
                px = sroX,
                py = sroY,
                point = sroPoint ?: ApdPoint(0, "SRO Principal", "SRO", 36.8, 10.1, ApdObjectType.SRO),
                isGc = true,
                isFlagged = false,
                scale = scaleMatrix,
                textMeasurer = textMeasurer,
                boxWidth = boxWidth,
                boxHeight = boxHeight
            )

            val jointPositions = mutableMapOf<Pair<String, Int>, Offset>()
            val cableStartPositions = mutableMapOf<String, Offset>()

            // Classer les câbles selon le rang de leur ligne dans l'arborescence
            val sortedCables = result.cablesList.sortedBy { layoutRowsMap[it.nom] ?: 999 }

            for (cable in sortedCables) {
                val r = layoutRowsMap[cable.nom] ?: 0
                val cableY = transY + (r * rowSpacing + 50f) * scaleMatrix

                val chain = result.chains[cable.nom]
                val joints = chain?.joints ?: emptyList()

                val parentNom = parentOf[cable.nom]
                val attachJointIdx = result.attachOf[cable.nom]

                val startPos = if (parentNom != null && attachJointIdx != null && jointPositions.containsKey(Pair(parentNom, attachJointIdx))) {
                    jointPositions[Pair(parentNom, attachJointIdx)]!!
                } else if (parentNom != null && cableStartPositions.containsKey(parentNom)) {
                    cableStartPositions[parentNom]!!
                } else {
                    Offset(sroX + boxWidth * scaleMatrix, sroY + (boxHeight / 2f) * scaleMatrix)
                }

                val cableLineStartX = startPos.x
                val cableLineEndX = cableLineStartX + colSpacing * scaleMatrix
                val cableLineY = cableY + (boxHeight / 2f) * scaleMatrix

                cableStartPositions[cable.nom] = Offset(cableLineEndX, cableLineY)

                // Dessiner le segment de liaison entre le parent et le câble
                drawCableSegment(
                    startX = startPos.x,
                    startY = startPos.y,
                    endX = cableLineEndX,
                    endY = cableLineY,
                    cableNom = cable.nom,
                    isAerien = cable.typeStruc.contains("AERIEN", ignoreCase = true),
                    isSuspect = result.anomalies.any { it.targetName == cable.nom },
                    lengthMeters = cable.longueurMeters,
                    scale = scaleMatrix,
                    textMeasurer = textMeasurer
                )

                var currentJointX = cableLineEndX
                for (jIdx in joints) {
                    val jointPoint = bpeMap[jIdx]
                    val boxCenterX = currentJointX + (boxWidth / 2f) * scaleMatrix
                    val boxCenterY = cableY + (boxHeight / 2f) * scaleMatrix

                    jointPositions[Pair(cable.nom, jIdx)] = Offset(boxCenterX, boxCenterY)

                    if (jointPoint != null) {
                        val isFlagged = result.anomalies.any { it.targetName == jointPoint.name || it.targetName == jointPoint.supportName }
                        val isGc = !jointPoint.supportType.contains("Aerien", ignoreCase = true)

                        drawApdBox(
                            px = currentJointX,
                            py = cableY,
                            point = jointPoint,
                            isGc = isGc,
                            isFlagged = isFlagged,
                            scale = scaleMatrix,
                            textMeasurer = textMeasurer,
                            boxWidth = boxWidth,
                            boxHeight = boxHeight
                        )
                    }

                    currentJointX += (boxWidth + colSpacing) * scaleMatrix
                }
            }
        }

        // Boutons de navigation (Zoom +, Zoom -, Reset)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = { scale = (scale * 1.25f).coerceAtMost(4.0f) },
                modifier = Modifier.size(42.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom +")
            }
            FloatingActionButton(
                onClick = { scale = (scale / 1.25f).coerceAtLeast(0.15f) },
                modifier = Modifier.size(42.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom -")
            }
            FloatingActionButton(
                onClick = {
                    scale = 1.0f
                    offsetX = 50f
                    offsetY = 100f
                },
                modifier = Modifier.size(42.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Recentrer")
            }
        }
    }
}

private fun DrawScope.drawApdBox(
    px: Float,
    py: Float,
    point: ApdPoint,
    isGc: Boolean,
    isFlagged: Boolean,
    scale: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    boxWidth: Float,
    boxHeight: Float
) {
    val w = boxWidth * scale
    val h = boxHeight * scale

    val fillColor = if (isGc) Color(0xFFFDE9D9) else Color(0xFFD9EAD3)
    val borderColor = if (isFlagged) Color.Red else Color(0xFF1E293B)
    val strokeW = if (isFlagged) 3f * scale else 1.5f * scale

    // Remplissage du boîtier
    drawRect(
        color = fillColor,
        topLeft = Offset(px, py),
        size = Size(w, h)
    )

    // Contour du boîtier
    drawRect(
        color = borderColor,
        topLeft = Offset(px, py),
        size = Size(w, h),
        style = Stroke(width = strokeW)
    )

    val textSize = (10f * scale).coerceAtLeast(8f)
    val titleSize = (11f * scale).coerceAtLeast(9f)

    val maxTextWidth = maxOf(20, (w - 12f * scale).toInt())
    val constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxTextWidth)

    // En-tête : Support
    val title = point.supportName.ifBlank { point.name }
    val titleLayout = textMeasurer.measure(
        text = title,
        style = TextStyle(color = Color(0xC55A11), fontSize = titleSize.sp, fontWeight = FontWeight.Bold),
        constraints = constraints
    )
    drawText(textLayoutResult = titleLayout, topLeft = Offset(px + 6f * scale, py + 4f * scale))

    // Nom du boîtier
    val nameLayout = textMeasurer.measure(
        text = point.name,
        style = TextStyle(color = Color(0xFF00863B), fontSize = textSize.sp, fontWeight = FontWeight.Bold),
        constraints = constraints
    )
    drawText(textLayoutResult = nameLayout, topLeft = Offset(px + 6f * scale, py + 24f * scale))

    // Type d'objet
    val typeLayout = textMeasurer.measure(
        text = "Type : ${point.objectType.label}",
        style = TextStyle(color = Color(0xFF1F4E78), fontSize = textSize.sp),
        constraints = constraints
    )
    drawText(textLayoutResult = typeLayout, topLeft = Offset(px + 6f * scale, py + 44f * scale))

    // Coordonnées
    val coordsLayout = textMeasurer.measure(
        text = String.format(Locale.FRANCE, "%.5f, %.5f", point.latitude, point.longitude),
        style = TextStyle(color = Color.DarkGray, fontSize = (textSize - 1f).sp),
        constraints = constraints
    )
    drawText(textLayoutResult = coordsLayout, topLeft = Offset(px + 6f * scale, py + 64f * scale))

    // Câble Entré
    if (point.cableEntre.isNotBlank()) {
        val entreLayout = textMeasurer.measure(
            text = "Entré : ${point.cableEntre}",
            style = TextStyle(color = Color.Black, fontSize = textSize.sp, fontWeight = FontWeight.Medium),
            constraints = constraints
        )
        drawText(textLayoutResult = entreLayout, topLeft = Offset(px + 6f * scale, py + 88f * scale))
    }

    // Câbles Sorties
    if (point.cablesSortie.isNotBlank()) {
        val sortieLayout = textMeasurer.measure(
            text = "Sortie : ${point.cablesSortie}",
            style = TextStyle(color = Color(0xFF1E293B), fontSize = textSize.sp, fontWeight = FontWeight.Medium),
            constraints = constraints
        )
        drawText(textLayoutResult = sortieLayout, topLeft = Offset(px + 6f * scale, py + 108f * scale))
    }
}

private fun DrawScope.drawCableSegment(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    cableNom: String,
    isAerien: Boolean,
    isSuspect: Boolean,
    lengthMeters: Double,
    scale: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val lineColor = when {
        isSuspect -> Color.Red
        isAerien -> Color(0xFF375623) // Vert foncé Aérien
        else -> Color(0xC55A11) // Orangé foncé Conduite/GC
    }

    val strokeW = 3f * scale

    val path = Path().apply {
        moveTo(startX, startY)
        if (kotlin.math.abs(startY - endY) > 2f) {
            val midX = (startX + endX) / 2f
            lineTo(midX, startY)
            lineTo(midX, endY)
            lineTo(endX, endY)
        } else {
            lineTo(endX, endY)
        }
    }

    drawPath(
        path = path,
        color = lineColor,
        style = Stroke(width = strokeW)
    )

    // Libellé du câble au-dessus de la ligne
    val midX = (startX + endX) / 2f
    val midY = (startY + endY) / 2f - 14f * scale
    val label = "$cableNom (${lengthMeters.toInt()}m)"

    val labelLayout = textMeasurer.measure(
        text = label,
        style = TextStyle(color = lineColor, fontSize = (10f * scale).coerceAtLeast(8f).sp, fontWeight = FontWeight.Bold),
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxOf(40, (kotlin.math.abs(endX - startX)).toInt()))
    )
    drawText(textLayoutResult = labelLayout, topLeft = Offset(midX - 30f * scale, midY))
}

/**
 * Vue sous forme de Liste pour BPE/PBO
 */
@Composable
fun BpePboListView(
    bpeList: List<ApdPoint>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectPoint: (ApdPoint) -> Unit
) {
    val filtered = remember(bpeList, searchQuery) {
        if (searchQuery.isBlank()) bpeList
        else bpeList.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.supportName.contains(searchQuery, ignoreCase = true) ||
            it.cablesSortie.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Rechercher BPE, PBO, Support...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().testTag("bpe_pbo_search_input")
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered) { pt ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (pt.objectType == ApdObjectType.SRO) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth().clickable { onSelectPoint(pt) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = pt.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (pt.objectType) {
                                    ApdObjectType.SRO -> MaterialTheme.colorScheme.primary
                                    ApdObjectType.BPE -> Color(0xFF0284C7)
                                    ApdObjectType.BPE_PBO -> Color(0xFFD97706)
                                    else -> Color(0xFF059669)
                                }
                            ) {
                                Text(
                                    text = pt.objectType.label,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (pt.supportName.isNotBlank()) {
                            Text("Support : ${pt.supportName} (${pt.supportType})", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (pt.cableEntre.isNotBlank()) {
                            Text("Câble Entré : ${pt.cableEntre}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        if (pt.cablesSortie.isNotBlank()) {
                            Text("Câbles Sortie : ${pt.cablesSortie}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Vue sous forme de Liste pour les Câbles
 */
@Composable
fun CablesListView(
    cablesList: List<ApdCable>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelectCable: (ApdCable) -> Unit
) {
    val filtered = remember(cablesList, searchQuery) {
        if (searchQuery.isBlank()) cablesList
        else cablesList.filter {
            it.nom.contains(searchQuery, ignoreCase = true) ||
            it.originName.contains(searchQuery, ignoreCase = true) ||
            it.supportsTraverses.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Rechercher câble, origine, supports...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().testTag("cables_search_input")
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered) { cable ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectCable(cable) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = cable.nom,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${cable.longueurMeters.toInt()} m",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        if (cable.originName.isNotBlank()) {
                            Text("Origine : ${cable.originName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            text = "Chambres : ${cable.nbChTraversees} • Poteaux : ${cable.nbPotTraversees} • LOG Total : ${cable.logTotalMeters.toInt()}m",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Vue sous forme de Liste pour les Anomalies APD
 */
@Composable
fun AnomaliesListView(
    anomalies: List<ApdAnomaly>,
    onSelectAnomaly: (ApdAnomaly) -> Unit
) {
    if (anomalies.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Aucune anomalie détectée sur le projet ✓", color = Color(0xFF059669), fontWeight = FontWeight.Bold)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(anomalies) { anomaly ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    modifier = Modifier.fillMaxWidth().clickable { onSelectAnomaly(anomaly) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = anomaly.type, fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 13.sp)
                            Text(text = "Cible : ${anomaly.targetName}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text(text = anomaly.detail, fontSize = 11.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fiche d'inspection au clic sur un composant du Synoptique
 */
@Composable
fun DetailInspectorCard(
    item: Any,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (item) {
                is ApdPoint -> {
                    Text(text = "Fiche Boîtier : ${item.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Type : ${item.objectType.label}", fontSize = 12.sp)
                    Text("Support : ${item.supportName.ifBlank { "N/A" }} (${item.supportType})", fontSize = 12.sp)
                    Text("Coordonnées : ${String.format(Locale.FRANCE, "%.6f, %.6f", item.latitude, item.longitude)}", fontSize = 12.sp)
                    if (item.cableEntre.isNotBlank()) Text("Câble Entré : ${item.cableEntre}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    if (item.cablesSortie.isNotBlank()) Text("Câbles Sortie : ${item.cablesSortie}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
                is ApdCable -> {
                    Text(text = "Fiche Câble : ${item.nom}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Longueur : ${item.longueurMeters} m • LOG Total : ${item.logTotalMeters} m", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Origine : ${item.originName.ifBlank { "N/A" }}", fontSize = 12.sp)
                    Text("Supports traversés : ${item.supportsTraverses.ifBlank { "Néant" }}", fontSize = 12.sp)
                }
                is ApdAnomaly -> {
                    Text(text = "Anomalie APD : ${item.type}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Red)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Élément concerné : ${item.targetName}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Détail : ${item.detail}", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Fermer")
            }
        }
    }
}
