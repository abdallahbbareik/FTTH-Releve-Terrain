package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.FtthNodeEntity
import com.example.data.storage.StoredTrack
import com.example.data.util.ClassifiedCable
import com.example.data.util.ClassifiedKmzNode
import com.example.data.util.KmzCableClassifier
import com.example.data.util.KmzNodeCategory
import com.example.data.util.KmzNodeClassifier
import com.example.ui.theme.TelecomCyan
import com.example.ui.theme.TelecomNavy
import java.io.File
import java.util.Locale

@Composable
fun KmzNodeClassificationDialog(
    nodes: List<FtthNodeEntity>,
    tracks: List<StoredTrack> = emptyList(),
    onDismiss: () -> Unit,
    onSelectNodeOnMap: (FtthNodeEntity) -> Unit,
    onSelectTrackOnMap: (StoredTrack) -> Unit = {},
    onImportKmzFile: (Uri) -> Unit = {},
    onRemoveDuplicates: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedTabIdx by remember { mutableStateOf(0) } // 0 = Nœuds, 1 = Câbles

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportKmzFile(uri)
        }
    }

    // 1. Classification des Nœuds
    val classifiedNodes = remember(nodes) {
        nodes.map { KmzNodeClassifier.classifyNode(it) }
    }
    val nodeStats = remember(classifiedNodes) {
        KmzNodeClassifier.computeStats(classifiedNodes)
    }

    // 2. Classification et Regroupement des Câbles (par Nom et Géométrie/Point commun)
    val classifiedCables = remember(tracks) {
        KmzCableClassifier.classifyAndGroupCables(tracks)
    }
    val cableStats = remember(classifiedCables) {
        KmzCableClassifier.computeStats(classifiedCables)
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<KmzNodeCategory?>(null) }

    // Filtrage dynamique pour les Nœuds
    val filteredNodesList = remember(classifiedNodes, searchQuery, selectedCategoryFilter) {
        classifiedNodes.filter { item ->
            val matchesCategory = selectedCategoryFilter == null || item.category == selectedCategoryFilter
            val queryLower = searchQuery.trim().lowercase(Locale.ROOT)
            val matchesQuery = queryLower.isBlank() ||
                    item.name.lowercase(Locale.ROOT).contains(queryLower) ||
                    item.id.lowercase(Locale.ROOT).contains(queryLower) ||
                    item.typeFonc.lowercase(Locale.ROOT).contains(queryLower) ||
                    item.reference.lowercase(Locale.ROOT).contains(queryLower) ||
                    item.ruleApplied.lowercase(Locale.ROOT).contains(queryLower)

            matchesCategory && matchesQuery
        }
    }

    // Filtrage dynamique pour les Câbles
    val filteredCablesList = remember(classifiedCables, searchQuery) {
        val queryLower = searchQuery.trim().lowercase(Locale.ROOT)
        if (queryLower.isBlank()) classifiedCables
        else classifiedCables.filter { c ->
            c.name.lowercase(Locale.ROOT).contains(queryLower) ||
                    c.capacityFO.lowercase(Locale.ROOT).contains(queryLower) ||
                    c.cableType.lowercase(Locale.ROOT).contains(queryLower) ||
                    c.installationType.lowercase(Locale.ROOT).contains(queryLower)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // 1. En-tête avec titre et fermeture
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = TelecomNavy,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Assessment,
                                    contentDescription = null,
                                    tint = TelecomCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Classification KMZ (Nœuds & Câbles)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${nodeStats.totalNodes} nœuds | ${cableStats.totalCables} câbles (${cableStats.totalSegments} tronçons, String.format(%.0fm))",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Onglets : [Nœuds] vs [Câbles (Nom & Géométrie)]
                TabRow(
                    selectedTabIndex = selectedTabIdx,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIdx]),
                            color = TelecomCyan
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTabIdx == 0,
                        onClick = { selectedTabIdx = 0; searchQuery = "" },
                        text = {
                            Text(
                                text = "Nœuds (${nodeStats.totalNodes})",
                                fontWeight = if (selectedTabIdx == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIdx == 0) TelecomNavy else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                    Tab(
                        selected = selectedTabIdx == 1,
                        onClick = { selectedTabIdx = 1; searchQuery = "" },
                        text = {
                            Text(
                                text = "Câbles Optiques (${classifiedCables.size})",
                                fontWeight = if (selectedTabIdx == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIdx == 1) TelecomNavy else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Actions (Importer KMZ, Purger Doublons, Exporter CSV)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                filePickerLauncher.launch(arrayOf("*/*"))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TelecomNavy),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Importer KMZ", fontSize = 12.sp)
                    }

                    if (selectedTabIdx == 0) {
                        Button(
                            onClick = onRemoveDuplicates,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Purger Doublons", fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = {
                            if (selectedTabIdx == 0) {
                                exportClassificationToCsv(context, classifiedNodes)
                            } else {
                                exportCablesToCsv(context, classifiedCables)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (selectedTabIdx == 0) "CSV Nœuds" else "CSV Câbles", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ONGLET 0 : CLASSIFICATION DES NŒUDS
                if (selectedTabIdx == 0) {
                    // Panneau des Règles
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = TelecomCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Règles Nœuds :",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "• Nom commence par \"SRO\" ➔ SRO | TYPE_FONC = CHAMBRE ➔ CHAMBRE\n" +
                                        "• TYPE_FONC = APPUI ➔ POTEAU | TYPE_FONC = POINT DE BRANCHEMENT OPTIQU ➔ Boîtier",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // KPI Grid Nœuds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiStatCard("SRO", nodeStats.sroCount, nodeStats.getPercentage(nodeStats.sroCount), Color(0xFF9333EA))
                        KpiStatCard("CHAMBRE", nodeStats.chambreCount, nodeStats.getPercentage(nodeStats.chambreCount), Color(0xFF2563EB))
                        KpiStatCard("POTEAU", nodeStats.poteauCount, nodeStats.getPercentage(nodeStats.poteauCount), Color(0xFFD97706))
                        KpiStatCard("Boîtier", nodeStats.boitierCount, nodeStats.getPercentage(nodeStats.boitierCount), Color(0xFF0D9488))
                        KpiStatCard("Autre", nodeStats.autreCount, nodeStats.getPercentage(nodeStats.autreCount), Color(0xFF6B7280))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Recherche Nœuds
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Rechercher un nœud (Nom, TYPE_FONC, REFERENCE...)", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("nodes_search_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tableau Nœuds
                    Surface(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        if (filteredNodesList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Aucun nœud à afficher.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(TelecomNavy).padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Nœud / Nom", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.3f))
                                    Text("TYPE_FONC", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.2f))
                                    Text("REFERENCE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.1f))
                                    Text("Catégorie", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.2f))
                                    Text("Action", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(50.dp), textAlign = TextAlign.Center)
                                }
                                HorizontalDivider()
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(filteredNodesList) { item ->
                                        ClassifiedNodeRow(item = item, onSelectOnMap = {
                                            onSelectNodeOnMap(item.rawNode)
                                            onDismiss()
                                        })
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }

                // ONGLET 1 : CLASSIFICATION DES CÂBLES (NOM & GÉOMÉTRIE / POINTS COMMUNS)
                if (selectedTabIdx == 1) {
                    // Panneau d'explication Câbles
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Route,
                                    contentDescription = null,
                                    tint = TelecomCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Règles Câbles (Regroupement Nom & Points Communs) :",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "• Les tronçons de câble ayant le même nom de base et raccordés bout-à-bout (points géométriques communs) sont réagglomérés en un câble unique avec calcul du métrage cumulé.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // KPI Grid Câbles
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiStatCard("Câbles Uniques", cableStats.totalCables, 100f, Color(0xFF0284C7))
                        KpiStatCard("Tronçons Total", cableStats.totalSegments, 100f, Color(0xFF7C3AED))
                        KpiStatCard("Métrage (m)", cableStats.totalLengthMeters.toInt(), 100f, Color(0xFF059669))
                        KpiStatCard("144 FO", cableStats.fo144Count, 0f, Color(0xFF9333EA))
                        KpiStatCard("72 FO", cableStats.fo72Count, 0f, Color(0xFF2563EB))
                        KpiStatCard("48 FO", cableStats.fo48Count, 0f, Color(0xFFD97706))
                        KpiStatCard("24 FO", cableStats.fo24Count, 0f, Color(0xFF0D9488))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Recherche Câbles
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Rechercher un câble (Nom, Capacité FO, Type...)", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("cables_search_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tableau Câbles
                    Surface(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        if (filteredCablesList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Aucun câble détecté dans les tracés/lignes KMZ.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(TelecomNavy).padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Nom du Câble", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.4f))
                                    Text("Tronçons", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.9f))
                                    Text("Capacité FO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.0f))
                                    Text("Longueur", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.0f))
                                    Text("Action", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(50.dp), textAlign = TextAlign.Center)
                                }
                                HorizontalDivider()
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(filteredCablesList) { cable ->
                                        ClassifiedCableRow(cable = cable, onSelectTrackOnMap = {
                                            if (cable.subTracks.isNotEmpty()) {
                                                onSelectTrackOnMap(cable.subTracks.first())
                                                onDismiss()
                                            }
                                        })
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiStatCard(
    label: String,
    count: Int,
    percentage: Float,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        modifier = Modifier.width(115.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(8.dp).clip(CircleShape).background(color)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = count.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (percentage > 0f) {
                Text(
                    text = String.format(Locale.ROOT, "%.1f %%", percentage),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ClassifiedNodeRow(
    item: ClassifiedKmzNode,
    onSelectOnMap: () -> Unit
) {
    val badgeColor = parseHexColor(item.category.badgeColorHex)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectOnMap() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.3f)) {
            Text(
                text = item.name,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "ID: ${item.id}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = item.typeFonc,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.2f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = item.reference,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Column(modifier = Modifier.weight(1.2f)) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeColor.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor)
            ) {
                Text(
                    text = item.category.label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Box(
            modifier = Modifier.width(50.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onSelectOnMap,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Voir sur carte",
                    tint = TelecomCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ClassifiedCableRow(
    cable: ClassifiedCable,
    onSelectTrackOnMap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectTrackOnMap() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Nom du Câble
        Column(modifier = Modifier.weight(1.4f)) {
            Text(
                text = cable.name,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${cable.cableType} • ${cable.installationType}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Nombre de tronçons
        Text(
            text = "${cable.segmentCount} tronçon${if (cable.segmentCount > 1) "s" else ""}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (cable.segmentCount > 1) TelecomNavy else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Capacité FO
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF0284C7).copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7)),
            modifier = Modifier.weight(1.0f)
        ) {
            Text(
                text = cable.capacityFO,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0284C7),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Longueur
        Text(
            text = String.format(Locale.ROOT, "%.0f m", cable.totalLengthMeters),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF059669),
            modifier = Modifier.weight(1.0f),
            maxLines = 1
        )

        // Action Carte
        Box(
            modifier = Modifier.width(50.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onSelectTrackOnMap,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Voir sur carte",
                    tint = TelecomCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun parseHexColor(hex: String): Color {
    return try {
        val clean = hex.removePrefix("#")
        val colorInt = android.graphics.Color.parseColor("#$clean")
        Color(colorInt)
    } catch (_: Exception) {
        TelecomNavy
    }
}

private fun exportClassificationToCsv(context: Context, items: List<ClassifiedKmzNode>) {
    val sb = StringBuilder()
    sb.append("ID;Nom;TYPE_FONC;REFERENCE;Categorie_Calculee;Regle_Appliquee;Latitude;Longitude;Fichier_Source\n")
    for (item in items) {
        sb.append("${item.id};")
            .append("${item.name.replace(";", "_")};")
            .append("${item.typeFonc.replace(";", "_")};")
            .append("${item.reference.replace(";", "_")};")
            .append("${item.category.label};")
            .append("${item.ruleApplied.replace(";", "_")};")
            .append("${item.latitude};")
            .append("${item.longitude};")
            .append("${item.sourceFile}\n")
    }

    try {
        val fileName = "classification_noeuds_${System.currentTimeMillis()}.csv"
        val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadDir, fileName)
        file.writeText(sb.toString(), Charsets.UTF_8)

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Rapport de Classification des Nœuds KMZ")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Partager le tableau des nœuds"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun exportCablesToCsv(context: Context, cables: List<ClassifiedCable>) {
    val sb = StringBuilder()
    sb.append("ID;Nom_Cable;Nombre_Troncons;Capacite_FO;Type_Cable;Pose;Longueur_Metres;Depart_Coords;Arrivee_Coords;Fichier_Source\n")
    for (c in cables) {
        sb.append("${c.id};")
            .append("${c.name.replace(";", "_")};")
            .append("${c.segmentCount};")
            .append("${c.capacityFO};")
            .append("${c.cableType};")
            .append("${c.installationType};")
            .append("${c.totalLengthMeters};")
            .append("${c.startCoordinatesLabel};")
            .append("${c.endCoordinatesLabel};")
            .append("${c.sourceFile}\n")
    }

    try {
        val fileName = "classification_cables_${System.currentTimeMillis()}.csv"
        val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadDir, fileName)
        file.writeText(sb.toString(), Charsets.UTF_8)

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Rapport de Classification des Câbles KMZ")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Partager le tableau des câbles"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
