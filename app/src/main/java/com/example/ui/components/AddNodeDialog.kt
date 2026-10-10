package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.local.NodeConformity
import com.example.data.local.NodeStatus
import com.example.data.photo.PhotoStorageManager
import com.example.data.util.AddressHelper
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun AddNodeDialog(
    latitude: Double,
    longitude: Double,
    existingNodesCount: Int,
    existingNodes: List<FtthNodeEntity> = emptyList(),
    currentProject: String = "projet01",
    onDismiss: () -> Unit,
    onNodeCreated: (FtthNodeEntity) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedType by remember { mutableStateOf(FtthNodeType.POTEAU) }

    // Auto-generate suggested ID
    val generatedId = remember(selectedType, existingNodesCount) {
        val prefix = when (selectedType) {
            FtthNodeType.POTEAU -> "POT"
            FtthNodeType.CHAMBRE -> "CH"
            FtthNodeType.BOITIER -> "BTR"
            FtthNodeType.SRO -> "SRO"
            FtthNodeType.IMMEUBLE -> "IMM"
            FtthNodeType.VILLA -> "VIL"
        }
        "$prefix-${100 + existingNodesCount + 1}"
    }

    var nodeId by remember(generatedId) { mutableStateOf(generatedId) }
    val isDuplicateId = remember(nodeId, existingNodes) {
        existingNodes.any { it.id.equals(nodeId.trim(), ignoreCase = true) }
    }

    var operator by remember { mutableStateOf("Ooredoo") }
    var isCustomOperator by remember { mutableStateOf(false) }

    // Photos du piquetage initial
    var initialPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraFile != null && tempCameraFile!!.exists() && tempCameraFile!!.length() > 0) {
            val finalizedPath = PhotoStorageManager.syncAndFinalizePhoto(context, tempCameraFile!!)
            initialPhotos = initialPhotos + finalizedPath
        } else {
            tempCameraFile?.let { if (it.exists() && it.length() == 0L) it.delete() }
        }
    }

    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val savedPath = PhotoStorageManager.saveImportedPhoto(context, nodeId.ifBlank { "NOEUD" }, uri)
            if (savedPath != null) {
                initialPhotos = initialPhotos + savedPath
            }
        }
    }

    var status by remember { mutableStateOf(NodeStatus.EXISTANT) }
    var etat by remember { mutableStateOf(NodeConformity.CONFORME) }
    var address by remember { mutableStateOf("") }
    var buildingName by remember { mutableStateOf("") }
    var hasBoitierFtth by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    // Poteau
    var poleNature by remember { mutableStateOf("bois") }
    var poleHeight by remember { mutableIntStateOf(8) }

    // Chambre
    var chamberType by remember { mutableStateOf("L2T") }

    // Boîtier
    var boitierType by remember { mutableStateOf("PBO") }
    var isSaturated by remember { mutableStateOf(false) }
    var boitierSupport by remember { mutableStateOf("Poteau") }
    var hasSplitter by remember { mutableStateOf(false) }
    var splitterType by remember { mutableStateOf("1:8") }

    // SRO
    var sroType by remember { mutableStateOf("armoire de rue") }
    var sroCapacity by remember { mutableStateOf("360 FO") }

    // Immeuble
    var buildingFloors by remember { mutableIntStateOf(4) }
    var buildingDwellings by remember { mutableIntStateOf(12) }
    var buildingBoitiersEtage by remember { mutableIntStateOf(4) }
    var hasLocalTechnique by remember { mutableStateOf(true) }
    var hasGaineMontante by remember { mutableStateOf(true) }
    var syndicAuthorization by remember { mutableStateOf("Accord obtenu") }
    var syndicContact by remember { mutableStateOf("") }
    var buildingConnectionMode by remember { mutableStateOf("souterrain") }

    // Villa
    var villaConnectionMode by remember { mutableStateOf("aérien") }

    var isGeocoding by remember { mutableStateOf(false) }

    // Auto-fetch address upon opening dialog
    LaunchedEffect(latitude, longitude) {
        isGeocoding = true
        address = AddressHelper.getAddressForCoordinates(context, latitude, longitude)
        isGeocoding = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .testTag("add_node_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Piqueter un nouveau nœud FTTH",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "GPS: %.6f, %.6f".format(latitude, longitude),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Type Selection (Poteau, Chambre, Boîtier, SRO, Immeuble, Villa)
                Text(
                    text = "Type de nœud FTTH :",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val types = FtthNodeType.values()
                    for (i in types.indices step 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (j in i..minOf(i + 1, types.size - 1)) {
                                val t = types[j]
                                val isSelected = selectedType == t
                                val col = FtthNodeVisuals.getNodeColor(t)

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            selectedType = t
                                            if (t == FtthNodeType.IMMEUBLE || t == FtthNodeType.VILLA) {
                                                if (status != NodeStatus.RACCORDEE && status != NodeStatus.NON_RACCORDEE) {
                                                    status = NodeStatus.RACCORDEE
                                                }
                                            } else {
                                                if (status == NodeStatus.RACCORDEE || status == NodeStatus.NON_RACCORDEE) {
                                                    status = NodeStatus.EXISTANT
                                                }
                                            }
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) col.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, col) else null
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(col),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(FtthNodeVisuals.getNodeDrawableRes(t)),
                                                contentDescription = t.label,
                                                tint = Color.Unspecified,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = t.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // STATUT
                Text(
                    text = if (selectedType == FtthNodeType.IMMEUBLE || selectedType == FtthNodeType.VILLA) "Statut du bâtiment :" else "Statut :",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (selectedType == FtthNodeType.IMMEUBLE || selectedType == FtthNodeType.VILLA) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = status == NodeStatus.RACCORDEE || status == NodeStatus.EXISTANT,
                            onClick = { status = NodeStatus.RACCORDEE },
                            label = { Text("Raccordée", fontSize = 11.sp, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = status == NodeStatus.NON_RACCORDEE || status == NodeStatus.EN_CONSTRUCTION,
                            onClick = { status = NodeStatus.NON_RACCORDEE },
                            label = { Text("Non raccordée", fontSize = 11.sp, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = status == NodeStatus.EXISTANT,
                                onClick = { status = NodeStatus.EXISTANT },
                                label = { Text(NodeStatus.EXISTANT.label, fontSize = 11.sp, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = status == NodeStatus.A_POSER,
                                onClick = { status = NodeStatus.A_POSER },
                                label = { Text(NodeStatus.A_POSER.label, fontSize = 11.sp, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = status == NodeStatus.A_REMPLACER,
                                onClick = { status = NodeStatus.A_REMPLACER },
                                label = { Text(NodeStatus.A_REMPLACER.label, fontSize = 11.sp, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = status == NodeStatus.A_DEPOSER,
                                onClick = { status = NodeStatus.A_DEPOSER },
                                label = { Text(NodeStatus.A_DEPOSER.label, fontSize = 11.sp, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ETAT TECHNIQUE (Conforme, Non conforme)
                Text(text = "État technique :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NodeConformity.values().forEach { c ->
                        FilterChip(
                            selected = etat == c,
                            onClick = { etat = c },
                            label = { Text(c.label, fontSize = 11.sp, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Identifiant (unique et obligatoire)
                OutlinedTextField(
                    value = nodeId,
                    onValueChange = { nodeId = it },
                    label = { Text("Identifiant (ex: $generatedId)") },
                    isError = isDuplicateId || nodeId.isBlank(),
                    supportingText = {
                        if (isDuplicateId) {
                            Text("Cet identifiant existe déjà (doit être unique)", color = MaterialTheme.colorScheme.error)
                        } else if (nodeId.isBlank()) {
                            Text("L'identifiant est obligatoire", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_node_id_input"),
                    singleLine = true
                )

                // Nom du bâtiment (Immeuble ou Villa seulement)
                if (selectedType == FtthNodeType.IMMEUBLE || selectedType == FtthNodeType.VILLA) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = buildingName,
                        onValueChange = { buildingName = it },
                        label = { Text("Nom du bâtiment") },
                        placeholder = {
                            Text(if (selectedType == FtthNodeType.IMMEUBLE) "ex: Résidence Les Jardins" else "ex: Villa Jasmine")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_node_building_name_input"),
                        singleLine = true
                    )
                }

                // Opérateur pour Poteau et Chambre
                if (selectedType == FtthNodeType.POTEAU || selectedType == FtthNodeType.CHAMBRE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Opérateur :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    val operatorOptions = if (selectedType == FtthNodeType.POTEAU) {
                        listOf("Ooredoo", "Orange", "Tunisie Telecom", "STEG", "Autre")
                    } else {
                        listOf("Ooredoo", "Orange", "Tunisie Telecom", "Autre")
                    }
                    val knownOperators = listOf("Ooredoo", "Orange", "Tunisie Telecom", "STEG")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        operatorOptions.forEach { op ->
                            val isSelected = if (op == "Autre") {
                                isCustomOperator || (operator.isNotBlank() && !knownOperators.contains(operator))
                            } else {
                                !isCustomOperator && operator == op
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (op == "Autre") {
                                        isCustomOperator = true
                                        if (knownOperators.contains(operator)) {
                                            operator = ""
                                        }
                                    } else {
                                        isCustomOperator = false
                                        operator = op
                                    }
                                },
                                label = { Text(op, fontSize = 11.sp) }
                            )
                        }
                    }
                    if (isCustomOperator || (operator.isNotBlank() && !knownOperators.contains(operator))) {
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = if (knownOperators.contains(operator)) "" else operator,
                            onValueChange = { operator = it },
                            label = { Text("Nom de l'opérateur (Autre)") },
                            placeholder = { Text("Saisissez le nom de l'opérateur") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ADRESSE (Récupération auto)
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Adresse") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            isGeocoding = true
                            address = AddressHelper.getAddressForCoordinates(context, latitude, longitude)
                            isGeocoding = false
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                    enabled = !isGeocoding
                ) {
                    Icon(Icons.Default.AutoMode, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = if (isGeocoding) "Recherche..." else "Récupérer automatiquement", fontSize = 11.sp)
                }

                // BOITIER FTTH (Non, Oui - uniquement pour Poteau, Chambre, Immeuble - MASQUÉ pour Boîtier, Villa et SRO)
                if (selectedType != FtthNodeType.BOITIER && selectedType != FtthNodeType.VILLA && selectedType != FtthNodeType.SRO) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Boîtier FTTH présent :", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(text = if (hasBoitierFtth) "Oui" else "Non", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(checked = hasBoitierFtth, onCheckedChange = { hasBoitierFtth = it })
                            }
                            if (hasBoitierFtth) {
                                val supportLabel = if (selectedType == FtthNodeType.POTEAU) "Poteau" else if (selectedType == FtthNodeType.CHAMBRE) "Chambre" else "Façade"
                                val previewId = (if (nodeId.isNotBlank()) nodeId.trim() else generatedId) + "-B"
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "ℹ️ Un boîtier ($previewId) sera créé automatiquement à la même position avec Support = $supportLabel.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF0284C7)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Champs spécifiques
                when (selectedType) {
                    FtthNodeType.POTEAU -> {
                        Text(text = "Nature de l'appui :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                "bois" to "Bois",
                                "métal" to "Métal",
                                "béton" to "Béton",
                                "composite" to "Composite",
                                "façade" to "Façade"
                            ).forEach { (nat, lbl) ->
                                val isSelected = poleNature.equals(nat, ignoreCase = true) ||
                                        (nat == "façade" && poleNature.equals("facade", ignoreCase = true))
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { poleNature = nat },
                                    label = { Text(lbl, fontSize = 11.sp) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Hauteur :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7, 8, 9, 10).forEach { h ->
                                FilterChip(
                                    selected = poleHeight == h,
                                    onClick = { poleHeight = h },
                                    label = { Text("${h}m") }
                                )
                            }
                        }
                    }

                    FtthNodeType.CHAMBRE -> {
                        Text(text = "Type de chambre :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        val chamberOptions = listOf("L0T", "L1T", "L2T", "L3T", "L4T", "1/2 L4T", "K1C", "K2C", "Autre")
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (row in chamberOptions.chunked(5)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { ch ->
                                        val isSel = if (ch == "Autre") {
                                            !chamberOptions.dropLast(1).any { it.equals(chamberType, ignoreCase = true) }
                                        } else {
                                            chamberType.equals(ch, ignoreCase = true)
                                        }
                                        FilterChip(
                                            selected = isSel,
                                            onClick = {
                                                if (ch == "Autre") {
                                                    if (chamberOptions.dropLast(1).any { it.equals(chamberType, ignoreCase = true) }) {
                                                        chamberType = ""
                                                    }
                                                } else {
                                                    chamberType = ch
                                                }
                                            },
                                            label = { Text(ch, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = chamberType,
                            onValueChange = { chamberType = it },
                            label = { Text("Désignation format chambre (ex: L2T, K1C, 1/2 L4T)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    FtthNodeType.BOITIER -> {
                        Text(text = "Type boîtier :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        val boitierOptions = listOf("BPE", "PBO", "PRI", "Autre")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            boitierOptions.forEach { bt ->
                                val isSel = if (bt == "Autre") {
                                    !listOf("BPE", "PBO", "PRI").any { it.equals(boitierType, ignoreCase = true) }
                                } else {
                                    boitierType.equals(bt, ignoreCase = true)
                                }
                                FilterChip(
                                    selected = isSel,
                                    onClick = {
                                        if (bt == "Autre") {
                                            if (listOf("BPE", "PBO", "PRI").any { it.equals(boitierType, ignoreCase = true) }) {
                                                boitierType = ""
                                            }
                                        } else {
                                            boitierType = bt
                                        }
                                    },
                                    label = { Text(bt, fontSize = 11.sp) }
                                )
                            }
                        }
                        if (!listOf("BPE", "PBO", "PRI").any { it.equals(boitierType, ignoreCase = true) }) {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = boitierType,
                                onValueChange = { boitierType = it },
                                label = { Text("Préciser le type de boîtier") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Présence Splitter (Coupleur optique)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Présence Splitter (Coupleur) :",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (hasSplitter) "Oui" else "Non",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasSplitter) Color(0xFF16A34A) else MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(checked = hasSplitter, onCheckedChange = { hasSplitter = it })
                                }
                                if (hasSplitter) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(text = "Type de Splitter :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val splitterOptions = listOf("1:2", "1:4", "1:8", "1:16", "1:32", "1:64")
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        splitterOptions.forEach { sp ->
                                            FilterChip(
                                                selected = splitterType.equals(sp, ignoreCase = true),
                                                onClick = { splitterType = sp },
                                                label = { Text(sp, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = splitterType,
                                        onValueChange = { splitterType = it },
                                        label = { Text("Format splitter (ex: 1:8, 1:16, 1:32)") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Saturé :", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(text = if (isSaturated) "Oui" else "Non", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(checked = isSaturated, onCheckedChange = { isSaturated = it })
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Support :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Poteau", "Chambre", "Façade", "Sous-Sol").forEach { sup ->
                                FilterChip(
                                    selected = boitierSupport == sup,
                                    onClick = { boitierSupport = sup },
                                    label = { Text(sup) }
                                )
                            }
                        }
                    }

                    FtthNodeType.SRO -> {
                        Text(text = "Type SRO :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("armoire de rue" to "Armoire de rue", "local" to "Local technique").forEach { (st, lbl) ->
                                FilterChip(
                                    selected = sroType.equals(st, ignoreCase = true),
                                    onClick = { sroType = st },
                                    label = { Text(lbl) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = sroCapacity,
                            onValueChange = { sroCapacity = it },
                            label = { Text("Capacité (ex: 360 FO, 720 FO)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    FtthNodeType.IMMEUBLE -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = buildingFloors.toString(),
                                onValueChange = { buildingFloors = it.toIntOrNull() ?: buildingFloors },
                                label = { Text("Étages") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = buildingDwellings.toString(),
                                onValueChange = { buildingDwellings = it.toIntOrNull() ?: buildingDwellings },
                                label = { Text("Logements") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = buildingBoitiersEtage.toString(),
                            onValueChange = { buildingBoitiersEtage = it.toIntOrNull() ?: buildingBoitiersEtage },
                            label = { Text("Boîtier(s) d'Étage") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Local technique :", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Switch(checked = hasLocalTechnique, onCheckedChange = { hasLocalTechnique = it })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Gaine montante :", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Switch(checked = hasGaineMontante, onCheckedChange = { hasGaineMontante = it })
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Mode raccordement :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("façade" to "Façade", "souterrain" to "Souterrain", "aérien" to "Aérien").forEach { (md, lbl) ->
                                FilterChip(
                                    selected = buildingConnectionMode.equals(md, ignoreCase = true),
                                    onClick = { buildingConnectionMode = md },
                                    label = { Text(lbl) }
                                )
                            }
                        }
                    }

                    FtthNodeType.VILLA -> {
                        Text(text = "Raccordement :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("aérien" to "Aérien", "souterrain" to "Souterrain", "façade" to "Façade").forEach { (vm, lbl) ->
                                FilterChip(
                                    selected = villaConnectionMode.equals(vm, ignoreCase = true),
                                    onClick = { villaConnectionMode = vm },
                                    label = { Text(lbl) }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // COMMENTAIRE
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Commentaire") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // PHOTOS DU PIQUETAGE INITIAL (POUR TOUS LES NŒUDS)
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📷 Photos du nœud (${initialPhotos.size}) :",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val file = PhotoStorageManager.createNewPhotoFile(context, nodeId.ifBlank { "NOEUD" }, projectName = currentProject)
                                tempCameraFile = file
                                val uri = PhotoStorageManager.getUriForPhotoFile(context, file)
                                takePictureLauncher.launch(uri)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Prendre photo", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            pickVisualMediaLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Galerie", fontSize = 12.sp)
                    }
                }

                if (initialPhotos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(initialPhotos) { photoPath ->
                            val resolvedFile = PhotoStorageManager.resolvePhotoFile(context, photoPath)
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = resolvedFile,
                                    contentDescription = "Photo piquetage",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(2.dp)
                                        .size(22.dp)
                                        .clickable {
                                            initialPhotos = initialPhotos.filter { it != photoPath }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Supprimer photo",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Annuler")
                    }

                    Button(
                        onClick = {
                            val finalId = nodeId.trim().ifEmpty { generatedId }
                            val finalOp = if (selectedType == FtthNodeType.POTEAU || selectedType == FtthNodeType.CHAMBRE) operator.trim() else ""
                            val finalBuildingName = if (selectedType == FtthNodeType.IMMEUBLE || selectedType == FtthNodeType.VILLA) buildingName.trim() else ""
                            val finalName = if (finalBuildingName.isNotBlank()) finalBuildingName else finalId
                            val finalStatus = if (selectedType == FtthNodeType.IMMEUBLE || selectedType == FtthNodeType.VILLA) {
                                if (status == NodeStatus.NON_RACCORDEE || status == NodeStatus.EN_CONSTRUCTION) NodeStatus.NON_RACCORDEE else NodeStatus.RACCORDEE
                            } else {
                                status
                            }
                            val newNode = FtthNodeEntity(
                                id = finalId,
                                type = selectedType,
                                name = finalName,
                                buildingName = finalBuildingName,
                                latitude = latitude,
                                longitude = longitude,
                                status = finalStatus,
                                etat = etat,
                                address = address,
                                operator = finalOp,
                                hasBoitierFtth = if (selectedType == FtthNodeType.BOITIER || selectedType == FtthNodeType.VILLA || selectedType == FtthNodeType.SRO) false else hasBoitierFtth,
                                notes = notes,
                                photos = initialPhotos,
                                photoCount = initialPhotos.size,
                                poleNature = poleNature,
                                poleHeight = poleHeight,
                                chamberType = chamberType,
                                boitierType = boitierType,
                                isSaturated = isSaturated,
                                boitierSupport = boitierSupport,
                                hasSplitter = hasSplitter,
                                splitterType = splitterType,
                                sroType = sroType,
                                sroCapacity = sroCapacity,
                                buildingFloors = buildingFloors,
                                buildingDwellings = buildingDwellings,
                                buildingBoitiersEtage = buildingBoitiersEtage,
                                hasLocalTechnique = hasLocalTechnique,
                                hasGaineMontante = hasGaineMontante,
                                syndicAuthorization = syndicAuthorization,
                                syndicContact = syndicContact,
                                buildingConnectionMode = buildingConnectionMode,
                                villaConnectionMode = villaConnectionMode
                            )
                            onNodeCreated(newNode)
                        },
                        enabled = nodeId.isNotBlank() && !isDuplicateId,
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("confirm_create_node_button")
                    ) {
                        Text("Valider piquetage")
                    }
                }
            }
        }
    }
}
