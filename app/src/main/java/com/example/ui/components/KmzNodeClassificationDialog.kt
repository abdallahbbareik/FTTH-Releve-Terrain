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
import androidx.compose.material.icons.filled.Refresh
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
import com.example.data.util.CableSupportAnalyzer
import com.example.data.util.ClassifiedCable
import com.example.data.util.ClassifiedKmzNode
import com.example.data.util.FtthSynopticExporter
import com.example.data.util.KmzAnalysisPersistenceManager
import com.example.data.util.KmzCableClassifier
import com.example.data.util.KmzNodeCategory
import com.example.data.util.KmzNodeClassifier
import android.widget.Toast
import com.example.ui.theme.TelecomCyan
import com.example.ui.theme.TelecomNavy
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class ClassificationExportPayload(
    val fileName: String,
    val bytes: ByteArray,
    val mimeType: String,
    val title: String,
    val description: String,
    val iconColor: Color
)

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

    val initialNodes = remember(nodes) {
        nodes.map { KmzNodeClassifier.classifyNode(it) }
    }
    val initialNodeStats = remember(initialNodes) {
        KmzNodeClassifier.computeStats(initialNodes)
    }

    var isLoading by remember { mutableStateOf(false) }
    var isMemorizedDataLoaded by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    var classifiedNodes by remember(nodes) { mutableStateOf(initialNodes) }
    var nodeStats by remember(nodes) { mutableStateOf(initialNodeStats) }
    var classifiedCables by remember(tracks, nodes) { mutableStateOf<List<ClassifiedCable>>(emptyList()) }
    var cableStats by remember(tracks, nodes) { mutableStateOf(KmzCableClassifier.computeStats(emptyList())) }
    var boitierConnectivityMap by remember { mutableStateOf<Map<String, BoitierConnectivity>>(emptyMap()) }
    var synopticGraph by remember { mutableStateOf(com.example.data.util.SynopticGraph(emptyList(), emptyList(), 0, 800f, 600f)) }

    androidx.compose.runtime.LaunchedEffect(nodes, tracks, refreshTrigger) {
        isLoading = true
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            try {
                // 1. Tenter d'abord de charger les données mémorisées (gain de temps instantané sans refaire les calculs)
                if (refreshTrigger == 0) {
                    val cached = KmzAnalysisPersistenceManager.getAnalysis(context, nodes, tracks)
                    if (cached != null) {
                        val cStats = KmzCableClassifier.computeStats(cached.cables)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            classifiedCables = cached.cables
                            cableStats = cStats
                            boitierConnectivityMap = cached.connectivities
                            synopticGraph = cached.synopticGraph
                            isMemorizedDataLoaded = true
                            isLoading = false
                        }
                        return@withContext
                    }
                }

                // 2. Si pas de données mémorisées ou réactualisation demandée : calcul complet
                isMemorizedDataLoaded = false
                KmzCableClassifier.clearPointsCache()
                CableSupportAnalyzer.clearCache()

                // Câbles (regroupement et classification)
                val cCables = KmzCableClassifier.classifyAndGroupCables(tracks, nodes)
                val cStats = KmzCableClassifier.computeStats(cCables)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    classifiedCables = cCables
                    cableStats = cStats
                }

                // Connectivités boîtiers et anomalies APD
                val connMap = BoitierCableConnectivityHelper.computeConnectivities(nodes, cCables)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    boitierConnectivityMap = connMap
                }

                // Graph Synoptique FTTH
                val sGraph = com.example.data.util.FtthSynopticGraphBuilder.buildGraph(nodes, cCables, connMap)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    synopticGraph = sGraph
                    isLoading = false
                    isMemorizedDataLoaded = true
                }

                // 3. Mémoriser les données traitées sur disque pour tous les prochains lancements !
                val sig = KmzAnalysisPersistenceManager.computeSignature(nodes, tracks)
                KmzAnalysisPersistenceManager.saveAnalysis(context, sig, cCables, connMap, sGraph)

            } catch (e: Exception) {
                android.util.Log.e("KmzClassification", "Erreur lors du traitement KMZ en arrière-plan", e)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<KmzNodeCategory?>(null) }
    var selectedCableRoleFilter by remember { mutableStateOf("TOUS") }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showCsvExportDialog by remember { mutableStateOf(false) }
    var pendingExportPayload by remember { mutableStateOf<ClassificationExportPayload?>(null) }

    val createXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri: Uri? ->
        if (uri != null && pendingExportPayload != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pendingExportPayload!!.bytes)
                }
                Toast.makeText(context, "${pendingExportPayload!!.title} enregistré avec succès !", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Erreur lors de l'enregistrement : ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null && pendingExportPayload != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pendingExportPayload!!.bytes)
                }
                Toast.makeText(context, "${pendingExportPayload!!.title} enregistré avec succès !", Toast.LENGTH_LONG).show()
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

    // Filtrage dynamique pour les Câbles (avec rôle Parent/Enfant et recherche avancée)
    val filteredCablesList = remember(classifiedCables, searchQuery, selectedCableRoleFilter) {
        val queryLower = searchQuery.trim().lowercase(Locale.ROOT)
        classifiedCables.filter { c ->
            val matchesRole = when (selectedCableRoleFilter) {
                "PARENTS" -> c.isParent
                "ENFANTS" -> c.isChild
                "ISOLES" -> !c.isParent && !c.isChild
                else -> true
            }
            val matchesQuery = queryLower.isBlank() ||
                    c.name.lowercase(Locale.ROOT).contains(queryLower) ||
                    c.capacityFO.lowercase(Locale.ROOT).contains(queryLower) ||
                    c.cableType.lowercase(Locale.ROOT).contains(queryLower) ||
                    c.installationType.lowercase(Locale.ROOT).contains(queryLower) ||
                    (c.parentCableName?.lowercase(Locale.ROOT)?.contains(queryLower) == true) ||
                    (c.originNodeName?.lowercase(Locale.ROOT)?.contains(queryLower) == true) ||
                    (c.destinationNodeName?.lowercase(Locale.ROOT)?.contains(queryLower) == true)

            matchesRole && matchesQuery
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
                        "5. Déduplication : SRO, Chambre et Poteau par position GPS identique ; Boîtier par position GPS + Nom identiques.\n" +
                        "6. Raccordement APD aux EXTRÉMITÉS des tronçons : Seuil de conformité ≤ 1m. Si décalé > 2m, noté comme Alerte Anomalie APD."
                    } else {
                        "1. Regroupement Nom & Géométrie : Les tronçons ayant le même nom de base et connectés bout-à-bout (distance ≤ 1m) sont réagglomérés en un câble unique.\n" +
                        "2. Câbles Parents / Principaux : Câbles raccordés directement au SRO (Niveau 0).\n" +
                        "3. Câbles Enfants / Dérivés : Câbles dérivant d'un boîtier (ou piqué sur un câble parent). Classifiés par génération (Enfant N1, N2...) avec traçabilité du câble parent et du boîtier d'origine.\n" +
                        "4. Détection des Boîtiers : Un boîtier appartient à un câble uniquement s'il se trouve à l'une des extrémités d'un tronçon (≤ 1m)."
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

    // Dialogue d'exportation (Excel Multi-Feuilles ou PDF Synoptique)
    if (showCsvExportDialog && pendingExportPayload != null) {
        val payload = pendingExportPayload!!

        AlertDialog(
            onDismissRequest = { showCsvExportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = payload.iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = payload.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = payload.description,
                        fontSize = 11.sp,
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
                                val success = saveExportFileToPublicDownloads(context, payload.fileName, payload.bytes, payload.mimeType)
                                if (success) {
                                    Toast.makeText(
                                        context,
                                        "${payload.title} enregistré dans Téléchargements :\n${payload.fileName}",
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
                                    if (payload.mimeType == "application/pdf") {
                                        createPdfLauncher.launch(payload.fileName)
                                    } else {
                                        createXlsxLauncher.launch(payload.fileName)
                                    }
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
                                shareExportFile(context, payload.fileName, payload.bytes, payload.mimeType, payload.title)
                            }
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Route, contentDescription = null, tint = TelecomNavy, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Partager le fichier", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("WhatsApp, Email, Google Drive, Visualiseur PDF / Excel...", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${nodeStats.totalNodes} nœuds | ${cableStats.totalCables} câbles (${cableStats.totalSegments} tronçons, ${String.format(Locale.ROOT, "%.0f m", cableStats.totalLengthMeters)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isMemorizedDataLoaded && !isLoading && cableStats.totalCables > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFE8F5E9)
                                    ) {
                                        Text(
                                            text = "⚡ Mémorisé",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                KmzAnalysisPersistenceManager.clearCache(context)
                                refreshTrigger++
                                Toast.makeText(context, "Réanalyse complète du KMZ en cours...", Toast.LENGTH_SHORT).show()
                            },
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Réanalyser le KMZ",
                                tint = TelecomNavy
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer")
                        }
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

                    if (selectedTabIdx == 2) {
                        // Export PDF Synoptique
                        Button(
                            onClick = {
                                val payload = com.example.data.util.FtthSynopticExporter.generateSynopticPdfPayload(
                                    graph = synopticGraph,
                                    connectivities = boitierConnectivityMap,
                                    cables = classifiedCables
                                )
                                pendingExportPayload = ClassificationExportPayload(
                                    fileName = payload.first,
                                    bytes = payload.second,
                                    mimeType = "application/pdf",
                                    title = "Plan Synoptique FTTH (.pdf)",
                                    description = "Document PDF haute résolution avec plan synoptique vectoriel grand format (A3 Paysage) et bordereau des tronçons de câbles.",
                                    iconColor = Color(0xFFDC2626)
                                )
                                showCsvExportDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1.1f).height(34.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF Synoptique", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Export Excel Synoptique
                        Button(
                            onClick = {
                                val payload = com.example.data.util.FtthSynopticExporter.generateSynopticExcelPayload(
                                    graph = synopticGraph,
                                    connectivities = boitierConnectivityMap,
                                    cables = classifiedCables,
                                    nodes = nodes
                                )
                                pendingExportPayload = ClassificationExportPayload(
                                    fileName = payload.first,
                                    bytes = payload.second,
                                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    title = "Synoptique Excel (.xlsx)",
                                    description = "Classeur Excel avec arborescence hiérarchique, cheminement des tronçons, capacités FO et supports traversés.",
                                    iconColor = Color(0xFF059669)
                                )
                                showCsvExportDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1.1f).height(34.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excel Synoptique", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(
                            onClick = {
                                val payload = com.example.data.util.ExcelExporterHelper.generateExcelPayload(
                                    classifiedNodes = classifiedNodes,
                                    connectivityMap = boitierConnectivityMap,
                                    classifiedCables = classifiedCables,
                                    nodes = nodes
                                )
                                pendingExportPayload = ClassificationExportPayload(
                                    fileName = payload.first,
                                    bytes = payload.second,
                                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    title = "Rapport Excel Qualification FTTH",
                                    description = "Le fichier Excel (.xlsx standard) contiendra 8 feuilles dédiées (SRO, Boîtiers, Chambres, Poteaux, Câbles, Anomalies APD...).",
                                    iconColor = Color(0xFF059669)
                                )
                                showCsvExportDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.weight(1.1f).height(34.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excel (.xlsx)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
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
                    // KPI Grid Câbles Compact (Câbles Total, Parents, Enfants, Tronçons, Métrage)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        KpiStatCard(
                            label = "Câbles Total",
                            count = cableStats.totalCables,
                            percentage = 100f,
                            color = Color(0xFF0284C7),
                            isSelected = selectedCableRoleFilter == "TOUS",
                            onClick = { selectedCableRoleFilter = "TOUS" }
                        )
                        KpiStatCard(
                            label = "Parents (SRO)",
                            count = cableStats.parentCablesCount,
                            percentage = cableStats.getPercentage(cableStats.parentCablesCount),
                            color = Color(0xFF10B981),
                            isSelected = selectedCableRoleFilter == "PARENTS",
                            onClick = {
                                selectedCableRoleFilter = if (selectedCableRoleFilter == "PARENTS") "TOUS" else "PARENTS"
                            }
                        )
                        KpiStatCard(
                            label = "Enfants (Dérivés)",
                            count = cableStats.childCablesCount,
                            percentage = cableStats.getPercentage(cableStats.childCablesCount),
                            color = Color(0xFF3B82F6),
                            isSelected = selectedCableRoleFilter == "ENFANTS",
                            onClick = {
                                selectedCableRoleFilter = if (selectedCableRoleFilter == "ENFANTS") "TOUS" else "ENFANTS"
                            }
                        )
                        KpiStatCard(
                            label = "Tronçons Total",
                            count = cableStats.totalSegments,
                            percentage = 100f,
                            color = Color(0xFF7C3AED)
                        )
                        KpiStatCard(
                            label = "Métrage (m)",
                            count = cableStats.totalLengthMeters.toInt(),
                            percentage = 100f,
                            color = Color(0xFF059669)
                        )
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
                                            text = "Rechercher (Nom, Câble Parent, Boîtier, FO...)",
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

                    Spacer(modifier = Modifier.height(4.dp))

                    // Filtres par Rôle Hiérarchique (Chips interactifs)
                    val isolatedCablesCount = classifiedCables.count { !it.isParent && !it.isChild }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CableRoleChip("Tous (${classifiedCables.size})", "TOUS", selectedCableRoleFilter, Color(0xFF0284C7)) { selectedCableRoleFilter = it }
                        CableRoleChip("Parents SRO (${cableStats.parentCablesCount})", "PARENTS", selectedCableRoleFilter, Color(0xFF10B981)) { selectedCableRoleFilter = it }
                        CableRoleChip("Enfants Dérivés (${cableStats.childCablesCount})", "ENFANTS", selectedCableRoleFilter, Color(0xFF3B82F6)) { selectedCableRoleFilter = it }
                        if (isolatedCablesCount > 0) {
                            CableRoleChip("Non Raccordés ($isolatedCablesCount)", "ISOLES", selectedCableRoleFilter, Color(0xFF6B7280)) { selectedCableRoleFilter = it }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Tableau Câbles FO
                    Surface(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        if (filteredCablesList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (isLoading) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        androidx.compose.material3.CircularProgressIndicator(
                                            color = TelecomNavy,
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Analyse et regroupement des câbles...",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Aucun câble ne correspond aux critères.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            var selectedCableForSupports by remember { mutableStateOf<com.example.data.util.CableSupportAnalysisResult?>(null) }

                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(TelecomNavy).padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Nom & Rôle Câble", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.5f))
                                    Text("Capacité", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                                    Text("Tronçons & Supports", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.2f))
                                    Text("Longueur", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(0.7f))
                                    Text("Action", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
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
                    if (synopticGraph.nodes.isEmpty() && isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    color = TelecomNavy,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Calcul et tracé du synoptique FTTH en cours...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        FtthSynopticCanvasView(
                            graph = synopticGraph,
                            connectivities = boitierConnectivityMap,
                            classifiedCables = classifiedCables,
                            onSelectNodeOnMap = { node ->
                                onSelectNodeOnMap(node)
                                onDismiss()
                            },
                            onExportPdf = {
                                val payload = com.example.data.util.FtthSynopticExporter.generateSynopticPdfPayload(
                                    graph = synopticGraph,
                                    connectivities = boitierConnectivityMap,
                                    cables = classifiedCables
                                )
                                pendingExportPayload = ClassificationExportPayload(
                                    fileName = payload.first,
                                    bytes = payload.second,
                                    mimeType = "application/pdf",
                                    title = "Plan Synoptique FTTH (.pdf)",
                                    description = "Document PDF haute résolution avec plan synoptique vectoriel grand format (A3 Paysage) et bordereau des tronçons de câbles.",
                                    iconColor = Color(0xFFDC2626)
                                )
                                showCsvExportDialog = true
                            },
                            onExportExcel = {
                                val payload = com.example.data.util.FtthSynopticExporter.generateSynopticExcelPayload(
                                    graph = synopticGraph,
                                    connectivities = boitierConnectivityMap,
                                    cables = classifiedCables,
                                    nodes = nodes
                                )
                                pendingExportPayload = ClassificationExportPayload(
                                    fileName = payload.first,
                                    bytes = payload.second,
                                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    title = "Synoptique Excel (.xlsx)",
                                    description = "Classeur Excel avec arborescence hiérarchique, cheminement des tronçons, capacités FO et supports traversés.",
                                    iconColor = Color(0xFF059669)
                                )
                                showCsvExportDialog = true
                            },
                            modifier = Modifier.fillMaxSize().weight(1f)
                        )
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
    color: Color,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) color.copy(alpha = 0.25f) else color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) color else color.copy(alpha = 0.4f)),
        modifier = Modifier
            .width(96.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
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
private fun CableRoleChip(
    label: String,
    roleKey: String,
    selectedRole: String,
    color: Color,
    onSelectRole: (String) -> Unit
) {
    val active = selectedRole == roleKey
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) color else color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color),
        modifier = Modifier
            .clickable { onSelectRole(roleKey) }
            .padding(vertical = 1.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) Color.White else color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
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

            // Support d'accueil (Chambre ou Poteau) pour les boîtiers
            if (connectivity != null && connectivity.hostingSupportName.isNotBlank() && connectivity.hostingSupportName != "-") {
                Text(
                    text = "Support: ${connectivity.hostingSupportName}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1D4ED8),
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
                if (connectivity.anomalieApd.isNotBlank() && connectivity.anomalieApd != "-") {
                    val isAnomaly = connectivity.anomalieApd.contains("Anomalie", ignoreCase = true)
                    Text(
                        text = connectivity.anomalieApd,
                        fontSize = 8.5.sp,
                        fontWeight = if (isAnomaly) FontWeight.Bold else FontWeight.Normal,
                        color = if (isAnomaly) Color(0xFFD97706) else Color(0xFF059669),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Nom du Câble & Hiérarchie Parent / Enfant
        Column(modifier = Modifier.weight(1.5f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = cleanCableName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(4.dp))
                // Badge Hiérarchique
                val (roleLabel, roleColor) = when {
                    cable.isParent -> "Parent" to Color(0xFF10B981)
                    cable.isChild -> "Enfant N${cable.hierarchyLevel}" to Color(0xFF3B82F6)
                    else -> "Isolé" to Color(0xFF6B7280)
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = roleColor.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, roleColor)
                ) {
                    Text(
                        text = roleLabel,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = roleColor,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            // Lignée de dérivation (Parent / Origine)
            if (cable.isChild && !cable.parentCableName.isNullOrBlank()) {
                val orig = cable.originNodeName?.takeIf { it.isNotBlank() && it != "-" } ?: "Boîtier"
                Text(
                    text = "↳ Dérive de: ${cable.parentCableName} [$orig]",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2563EB),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (cable.isParent) {
                val orig = cable.originNodeName?.takeIf { it.isNotBlank() && it != "-" } ?: "SRO"
                Text(
                    text = "Origine: [$orig]",
                    fontSize = 8.5.sp,
                    color = Color(0xFF059669),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "${cable.cableType} • ${cable.installationType}",
                fontSize = 9.sp,
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
            modifier = Modifier.weight(0.7f)
        ) {
            Text(
                text = cable.capacityFO,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0284C7),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 3. Tronçons & Supports Traversés
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFFD97706).copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD97706)),
            modifier = Modifier
                .weight(1.2f)
                .clickable { onViewSupports(supportAnalysis) }
        ) {
            Text(
                text = "${supportAnalysis.supports.size} supp. (${supportAnalysis.totalChambresCount} Ch/${supportAnalysis.totalPoteauxCount} Pot)",
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD97706),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 4. Longueur
        Text(
            text = String.format(Locale.ROOT, "%.0f m", cable.totalLengthMeters),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF059669),
            modifier = Modifier.weight(0.7f),
            maxLines = 1,
            textAlign = TextAlign.End
        )

        // 5. Action Carte
        Box(
            modifier = Modifier.width(40.dp),
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
    sb.append("ID;Nom;Categorie_Calculee;TYPE_FONC;REFERENCE;Cable_Entree;Cables_Sortie;Accrochage_APD;Regle_Appliquee;Latitude;Longitude;Fichier_Source")
    for (key in extraKeysList) {
        sb.append(";${key.replace(";", "_")}")
    }
    sb.append(";Notes_Complementaires\n")

    for (item in sortedItems) {
        val conn = connectivityMap[item.id]
        val cableEntree = conn?.incomingCableName ?: "-"
        val cablesSortie = conn?.outgoingCablesStr ?: "-"
        val anomalieApd = conn?.anomalieApd ?: "-"

        sb.append("${item.id};")
            .append("${item.name.replace(";", "_").replace("\n", " ")};")
            .append("${item.category.label};")
            .append("${item.typeFonc.replace(";", "_")};")
            .append("${item.reference.replace(";", "_")};")
            .append("${cableEntree.replace(";", "_")};")
            .append("${cablesSortie.replace(";", "_")};")
            .append("${anomalieApd.replace(";", "_")};")
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

private fun saveExportFileToPublicDownloads(context: Context, fileName: String, bytes: ByteArray, mimeType: String): Boolean {
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { stream ->
                    stream.write(bytes)
                }
                true
            } else {
                false
            }
        } else {
            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) downloadDir.mkdirs()
            val file = File(downloadDir, fileName)
            file.writeBytes(bytes)
            true
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun shareExportFile(context: Context, fileName: String, bytes: ByteArray, mimeType: String, subject: String) {
    try {
        val cacheFile = File(context.cacheDir, fileName)
        cacheFile.writeBytes(bytes)

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            cacheFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, "Partager $fileName"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Erreur lors du partage : ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

