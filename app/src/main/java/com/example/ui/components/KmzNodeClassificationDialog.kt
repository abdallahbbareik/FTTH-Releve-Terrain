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
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import com.example.data.util.BoitierCableConnectivityHelper
import com.example.data.util.BoitierConnectivity
import com.example.data.util.ClassifiedCable
import com.example.data.util.ClassifiedKmzNode
import com.example.data.util.KmzCableClassifier
import com.example.data.util.KmzNodeCategory
import com.example.data.util.KmzNodeClassifier
import android.widget.Toast
import com.example.ui.theme.TelecomCyan
import com.example.ui.theme.TelecomNavy
import org.json.JSONObject
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
    val classifiedCables = remember(tracks, nodes) {
        KmzCableClassifier.classifyAndGroupCables(tracks, nodes)
    }
    val cableStats = remember(classifiedCables) {
        KmzCableClassifier.computeStats(classifiedCables)
    }

    // 3. Connectivité Câbles Entrée / Sorties pour chaque Boîtier
    val boitierConnectivityMap = remember(nodes, classifiedCables) {
        BoitierCableConnectivityHelper.computeConnectivities(nodes, classifiedCables)
    }

    // 4. Graph Synoptique de l'architecture FTTH
    val synopticGraph = remember(nodes, classifiedCables, boitierConnectivityMap) {
        com.example.data.util.FtthSynopticGraphBuilder.buildGraph(nodes, classifiedCables, boitierConnectivityMap)
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<KmzNodeCategory?>(null) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showCsvExportDialog by remember { mutableStateOf(false) }
    var pendingCsvPayload by remember { mutableStateOf<Pair<String, String>?>(null) } // Pair(fileName, csvContent)

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null && pendingCsvPayload != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pendingCsvPayload!!.second.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Fichier CSV enregistré avec succès !", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Erreur lors de l'enregistrement : ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

    // Dialogue d'affichage des règles lorsque le point d'interrogation est cliqué
    if (showRulesDialog) {
        AlertDialog(
            onDismissRequest = { showRulesDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = TelecomCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (selectedTabIdx == 0) "Règles Qualification Nœuds" else "Règles Regroupement Câbles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = if (selectedTabIdx == 0) {
                        "1. Nom commence par \"SRO\" ➔ SRO\n" +
                        "2. TYPE_FONC = CHAMBRE ➔ CHAMBRE\n" +
                        "3. TYPE_FONC = APPUI ➔ POTEAU\n" +
                        "4. TYPE_FONC = POINT DE BRANCHEMENT OPTIQU ➔ Boîtier (sans sous-classe)\n" +
                        "5. Déduplication : SRO, Chambre et Poteau par position GPS identique (coordonnées) ; Boîtier par position GPS + Nom identiques.\n" +
                        "6. Raccordement Boîtiers & SRO : 1 Câble Entrée (fin ou câble en passage) et N Câbles Sortie (départ ou câble en passage). Non calculé pour Chambres et Poteaux."
                    } else {
                        "1. Regroupement par Nom & Géométrie : Les tronçons ayant le même nom de base et connectés bout-à-bout (distance < 5m) sont réagglomérés en un câble unique.\n" +
                        "2. Détection des Boîtiers : Un boîtier appartient à un câble uniquement s'il se trouve à l'une des extrémités d'un tronçon. Les boîtiers intermédiaires sont ignorés."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = { showRulesDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomNavy)
                ) {
                    Text("Fermer", fontSize = 12.sp)
                }
            }
        )
    }

    // Dialogue d'exportation CSV avec options de sauvegarde claire sur le téléphone
    if (showCsvExportDialog && pendingCsvPayload != null) {
        val (fileName, csvContent) = pendingCsvPayload!!

        AlertDialog(
            onDismissRequest = { showCsvExportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Enregistrer le fichier CSV",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Comment souhaitez-vous sauvegarder le fichier sur votre téléphone ?",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Option 1 : Téléchargements direct avec notification Toast
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val success = saveCsvToPublicDownloads(context, fileName, csvContent)
                                if (success) {
                                    Toast.makeText(
                                        context,
                                        "Fichier enregistré dans Téléchargements :\n$fileName",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(context, "Erreur lors de l'enregistrement", Toast.LENGTH_SHORT).show()
                                }
                                showCsvExportDialog = false
                            }
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = TelecomNavy, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Dossier Téléchargements (Direct)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TelecomNavy)
                                Text("Sauvegarde automatique dans Téléchargements", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Option 2 : Enregistrer sous (Choisir un dossier avec le sélecteur Android)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showCsvExportDialog = false
                                try {
                                    createDocumentLauncher.launch(fileName)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, tint = TelecomNavy, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Choisir un dossier (Enregistrer sous...)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Sélectionner un dossier spécifique sur la mémoire ou SD", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Option 3 : Partager via intent
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showCsvExportDialog = false
                                shareCsvFile(context, fileName, csvContent)
                            }
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Route, contentDescription = null, tint = TelecomNavy, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Partager le fichier", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("WhatsApp, Email, Google Drive, Bluetooth...", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showCsvExportDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline)
                ) {
                    Text("Annuler", fontSize = 12.sp)
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
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
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Assessment,
                                    contentDescription = null,
                                    tint = TelecomCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Classification KMZ (Nœuds & Câbles)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${nodeStats.totalNodes} nœuds | ${cableStats.totalCables} câbles (${cableStats.totalSegments} tronçons, ${String.format(Locale.ROOT, "%.0f m", cableStats.totalLengthMeters)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

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
                                fontSize = 12.sp,
                                color = if (selectedTabIdx == 0) TelecomNavy else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                    Tab(
                        selected = selectedTabIdx == 1,
                        onClick = { selectedTabIdx = 1; searchQuery = "" },
                        text = {
                            Text(
                                text = "Câbles FO (${classifiedCables.size})",
                                fontWeight = if (selectedTabIdx == 1) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                color = if (selectedTabIdx == 1) TelecomNavy else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                    Tab(
                        selected = selectedTabIdx == 2,
                        onClick = { selectedTabIdx = 2; searchQuery = "" },
                        text = {
                            Text(
                                text = "Synoptique (Graph)",
                                fontWeight = if (selectedTabIdx == 2) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp,
                                color = if (selectedTabIdx == 2) TelecomNavy else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 3. Actions compactes (Importer KMZ, Purger Doublons, Exporter CSV + Bouton ?)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f).height(34.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Importer", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (selectedTabIdx == 0) {
                        Button(
                            onClick = onRemoveDuplicates,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1.1f).height(34.dp)
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Purger", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
                        onClick = {
                            val payload = if (selectedTabIdx == 0) {
                                generateClassificationCsvPayload(classifiedNodes, boitierConnectivityMap)
                            } else {
                                generateCablesCsvPayload(classifiedCables, nodes)
                            }
                            pendingCsvPayload = payload
                            showCsvExportDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f).height(34.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CSV", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Bouton Point d'interrogation pour afficher les règles
                    Surface(
                        shape = CircleShape,
                        color = TelecomNavy.copy(alpha = 0.12f),
                        modifier = Modifier.size(34.dp).clickable { showRulesDialog = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = "Afficher les règles",
                                tint = TelecomNavy,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ONGLET 0 : CLASSIFICATION DES NŒUDS
                if (selectedTabIdx == 0) {
                    // KPI Grid Nœuds Compact
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        KpiStatCard("SRO", nodeStats.sroCount, nodeStats.getPercentage(nodeStats.sroCount), Color(0xFF9333EA))
                        KpiStatCard("CHAMBRE", nodeStats.chambreCount, nodeStats.getPercentage(nodeStats.chambreCount), Color(0xFF2563EB))
                        KpiStatCard("POTEAU", nodeStats.poteauCount, nodeStats.getPercentage(nodeStats.poteauCount), Color(0xFFD97706))
                        KpiStatCard("Boîtier", nodeStats.boitierCount, nodeStats.getPercentage(nodeStats.boitierCount), Color(0xFF0D9488))
                        KpiStatCard("Autre", nodeStats.autreCount, nodeStats.getPercentage(nodeStats.autreCount), Color(0xFF6B7280))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Recherche Nœuds Compacte
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.weight(1f).testTag("nodes_search_input"),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Rechercher un nœud (Nom, TYPE_FONC, Ref...)",
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
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Effacer",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

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
                                    Text("Nom nœud", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(2.0f))
                                    Text("Catégorie", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.2f))
                                    Text("Action", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                                }
                                HorizontalDivider()
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(filteredNodesList) { item ->
                                        ClassifiedNodeRow(
                                            item = item,
                                            connectivity = boitierConnectivityMap[item.id],
                                            onSelectOnMap = {
                                                onSelectNodeOnMap(item.rawNode)
                                                onDismiss()
                                            }
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }

                // ONGLET 1 : CLASSIFICATION DES CÂBLES (NOM & GÉOMÉTRIE / POINTS COMMUNS)
                if (selectedTabIdx == 1) {
                    // KPI Grid Câbles Compact (Câbles Uniques, Tronçons, Métrage)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        KpiStatCard("Câbles Uniques", cableStats.totalCables, 100f, Color(0xFF0284C7))
                        KpiStatCard("Tronçons Total", cableStats.totalSegments, 100f, Color(0xFF7C3AED))
                        KpiStatCard("Métrage (m)", cableStats.totalLengthMeters.toInt(), 100f, Color(0xFF059669))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Recherche Câbles Compacte
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.weight(1f).testTag("cables_search_input"),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Rechercher un câble (Nom, FO, Type...)",
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
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Effacer",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Tableau Câbles FO
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
                            var selectedCableForSupports by remember { mutableStateOf<com.example.data.util.CableSupportAnalysisResult?>(null) }

                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(TelecomNavy).padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Nom du Câble", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.3f))
                                    Text("Capacité FO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.8f))
                                    Text("Tronçons & Supports", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.3f))
                                    Text("Longueur", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.8f))
                                    Text("Action", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.Center)
                                }
                                HorizontalDivider()
                                LazyColumn(modifier = Modifier.fillMaxSize()) {
                                    items(filteredCablesList) { cable ->
                                        ClassifiedCableRow(
                                            cable = cable,
                                            nodes = nodes,
                                            onSelectTrackOnMap = {
                                                if (cable.subTracks.isNotEmpty()) {
                                                    onSelectTrackOnMap(cable.subTracks.first())
                                                    onDismiss()
                                                }
                                            },
                                            onViewSupports = { analysis ->
                                                selectedCableForSupports = analysis
                                            }
                                        )
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }
                                }

                                if (selectedCableForSupports != null) {
                                    com.example.ui.components.CableSupportsDialog(
                                        analysisResult = selectedCableForSupports!!,
                                        onDismiss = { selectedCableForSupports = null },
                                        onSelectNodeOnMap = { node ->
                                            onSelectNodeOnMap(node)
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ONGLET 2 : SYNOPTIQUE GRAPHIQUE FTTH (ARBORESCENCE SRO & BOÎTIERS)
                if (selectedTabIdx == 2) {
                    FtthSynopticCanvasView(
                        graph = synopticGraph,
                        connectivities = boitierConnectivityMap,
                        classifiedCables = classifiedCables,
                        onSelectNodeOnMap = { node ->
                            onSelectNodeOnMap(node)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxSize().weight(1f)
                    )
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
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = Modifier.width(92.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(6.dp).clip(CircleShape).background(color)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (percentage > 0f) {
                    Text(
                        text = String.format(Locale.ROOT, "%.0f%%", percentage),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassifiedNodeRow(
    item: ClassifiedKmzNode,
    connectivity: BoitierConnectivity? = null,
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
        // 1. Nom nœud (+ sub details + raccordement câbles)
        Column(modifier = Modifier.weight(2.0f)) {
            Text(
                text = item.name,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subDetails = listOf(item.typeFonc, item.reference)
                .filter { it.isNotBlank() && it != "-" }
                .joinToString(" • ")
            if (subDetails.isNotBlank()) {
                Text(
                    text = subDetails,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Raccordement Câbles Entrée / Sorties pour les boîtiers
            if (connectivity != null && (connectivity.incomingCable != null || connectivity.outgoingCables.isNotEmpty())) {
                val parts = mutableListOf<String>()
                if (connectivity.incomingCable != null) {
                    parts.add("Entrée: ${connectivity.incomingCableName}")
                }
                if (connectivity.outgoingCables.isNotEmpty()) {
                    parts.add("Sortie: ${connectivity.outgoingCablesStr}")
                }
                Text(
                    text = parts.joinToString(" • "),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0D9488),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 2. Catégorie
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

        // 3. Action
        Box(
            modifier = Modifier.width(44.dp),
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
    nodes: List<FtthNodeEntity>,
    onSelectTrackOnMap: () -> Unit,
    onViewSupports: (com.example.data.util.CableSupportAnalysisResult) -> Unit
) {
    val supportAnalysis = remember(cable, nodes) {
        com.example.data.util.CableSupportAnalyzer.analyzeClassifiedCableSupports(cable, nodes)
    }

    val cleanCableName = cable.name.removePrefix("::").removePrefix(":").removePrefix(" :").trim()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectTrackOnMap() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Nom du Câble
        Column(modifier = Modifier.weight(1.3f)) {
            Text(
                text = cleanCableName,
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

        // 2. Capacité FO
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF0284C7).copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7)),
            modifier = Modifier.weight(0.8f)
        ) {
            Text(
                text = cable.capacityFO,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0284C7),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // 3. Tronçons & Supports Traversés
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFFD97706).copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD97706)),
            modifier = Modifier
                .weight(1.3f)
                .clickable { onViewSupports(supportAnalysis) }
        ) {
            Text(
                text = "${supportAnalysis.supports.size} supp. (${supportAnalysis.totalChambresCount} Ch/${supportAnalysis.totalPoteauxCount} Pot)",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD97706),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 4. Longueur
        Text(
            text = String.format(Locale.ROOT, "%.0f m", cable.totalLengthMeters),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF059669),
            modifier = Modifier.weight(0.8f),
            maxLines = 1
        )

        // 5. Action Carte
        Box(
            modifier = Modifier.width(44.dp),
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

private fun generateClassificationCsvPayload(
    items: List<ClassifiedKmzNode>,
    connectivityMap: Map<String, BoitierConnectivity> = emptyMap()
): Pair<String, String> {
    val sortedItems = items.sortedBy { it.category.ordinal }

    val extraKeys = mutableSetOf<String>()
    for (item in sortedItems) {
        if (item.rawNode.kmlProperties.isNotBlank()) {
            try {
                val json = JSONObject(item.rawNode.kmlProperties)
                val keys = json.keys()
                while (keys.hasNext()) {
                    extraKeys.add(keys.next())
                }
            } catch (_: Exception) {}
        }
    }
    val extraKeysList = extraKeys.toList().sorted()

    val sb = StringBuilder()
    sb.append("\uFEFF") // UTF-8 BOM pour la gestion des accents dans Excel et Google Sheets
    sb.append("ID;Nom;Categorie_Calculee;TYPE_FONC;REFERENCE;Cable_Entree;Cables_Sortie;Regle_Appliquee;Latitude;Longitude;Fichier_Source")
    for (key in extraKeysList) {
        sb.append(";${key.replace(";", "_")}")
    }
    sb.append(";Notes_Complementaires\n")

    for (item in sortedItems) {
        val conn = connectivityMap[item.id]
        val cableEntree = conn?.incomingCableName ?: "-"
        val cablesSortie = conn?.outgoingCablesStr ?: "-"

        sb.append("${item.id};")
            .append("${item.name.replace(";", "_").replace("\n", " ")};")
            .append("${item.category.label};")
            .append("${item.typeFonc.replace(";", "_")};")
            .append("${item.reference.replace(";", "_")};")
            .append("${cableEntree.replace(";", "_")};")
            .append("${cablesSortie.replace(";", "_")};")
            .append("${item.ruleApplied.replace(";", "_")};")
            .append("${item.latitude};")
            .append("${item.longitude};")
            .append("${item.sourceFile.replace(";", "_")}")

        val jsonMap = mutableMapOf<String, String>()
        if (item.rawNode.kmlProperties.isNotBlank()) {
            try {
                val json = JSONObject(item.rawNode.kmlProperties)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    jsonMap[k] = json.optString(k, "").trim()
                }
            } catch (_: Exception) {}
        }

        for (key in extraKeysList) {
            val v = jsonMap[key] ?: ""
            sb.append(";${v.replace(";", "_").replace("\n", " ")}")
        }

        val cleanNotes = item.rawNode.notes.replace(";", "_").replace("\n", " | ")
        sb.append(";${cleanNotes}\n")
    }

    val fileName = "classification_noeuds_${System.currentTimeMillis()}.csv"
    return Pair(fileName, sb.toString())
}

private fun generateCablesCsvPayload(
    cables: List<ClassifiedCable>,
    nodes: List<FtthNodeEntity>
): Pair<String, String> {
    val sb = StringBuilder()
    sb.append("\uFEFF") // UTF-8 BOM pour la gestion des accents dans Excel et Google Sheets
    sb.append("ID;Nom_Cable;Nombre_Troncons;Capacite_FO;Type_Cable;Pose;Longueur_Metres;Origine_Depart;Extremite_Fin;Nombre_Supports;Supports_Traverses;Depart_Coords;Arrivee_Coords;Fichier_Source\n")

    for (c in cables) {
        val cleanCableName = c.name.removePrefix("::").removePrefix(":").removePrefix(" :").trim()
        val supportAnalysis = com.example.data.util.CableSupportAnalyzer.analyzeClassifiedCableSupports(c, nodes)

        val originStr = supportAnalysis.supports.firstOrNull()?.let { "${it.supportTypeLabel} [${it.node.name}]" } ?: "-"
        val destStr = supportAnalysis.supports.lastOrNull()?.let { "${it.supportTypeLabel} [${it.node.name}]" } ?: "-"

        val supportsStr = supportAnalysis.supports.joinToString(" -> ") { supp ->
            var str = "${supp.index}. ${supp.supportTypeLabel} [${supp.node.name}]"
            if (supp.hostedBoitiers.isNotEmpty()) {
                val boitierNames = supp.hostedBoitiers.joinToString(", ") { it.name }
                str += " (Boîtier: $boitierNames)"
            }
            str
        }.replace(";", "_").replace("\n", " ")

        sb.append("${c.id};")
            .append("${cleanCableName.replace(";", "_")};")
            .append("${c.segmentCount};")
            .append("${c.capacityFO};")
            .append("${c.cableType};")
            .append("${c.installationType};")
            .append("${c.totalLengthMeters};")
            .append("${originStr.replace(";", "_")};")
            .append("${destStr.replace(";", "_")};")
            .append("${supportAnalysis.supports.size};")
            .append("${supportsStr};")
            .append("${c.startCoordinatesLabel};")
            .append("${c.endCoordinatesLabel};")
            .append("${c.sourceFile}\n")
    }

    val fileName = "classification_cables_${System.currentTimeMillis()}.csv"
    return Pair(fileName, sb.toString())
}

private fun saveCsvToPublicDownloads(context: Context, fileName: String, csvContent: String): Boolean {
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { stream ->
                    stream.write(csvContent.toByteArray(Charsets.UTF_8))
                }
                true
            } else {
                false
            }
        } else {
            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) downloadDir.mkdirs()
            val file = File(downloadDir, fileName)
            file.writeText(csvContent, Charsets.UTF_8)
            true
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun shareCsvFile(context: Context, fileName: String, csvContent: String) {
    try {
        val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        if (!downloadDir.exists()) downloadDir.mkdirs()
        val file = File(downloadDir, fileName)
        file.writeText(csvContent, Charsets.UTF_8)

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Rapport CSV - $fileName")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Partager le fichier CSV"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Erreur lors du partage : ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
