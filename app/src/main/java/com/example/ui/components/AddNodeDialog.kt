package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.local.NodeConformity
import com.example.data.local.NodeStatus
import com.example.data.util.AddressHelper
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AddNodeDialog(
    latitude: Double,
    longitude: Double,
    existingNodesCount: Int,
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
    var name by remember(selectedType) {
        mutableStateOf(
            when (selectedType) {
                FtthNodeType.POTEAU -> "Poteau Appui Aérien"
                FtthNodeType.CHAMBRE -> "Chambre Trottoir"
                FtthNodeType.BOITIER -> "Boîtier Optique"
                FtthNodeType.SRO -> "Armoire SRO"
                FtthNodeType.IMMEUBLE -> "Immeuble Collectif"
                FtthNodeType.VILLA -> "Pavillon Individuel"
            }
        )
    }
    var status by remember { mutableStateOf(NodeStatus.A_POSER) }
    var etat by remember { mutableStateOf(NodeConformity.CONFORME) }
    var address by remember { mutableStateOf("") }
    var hasBoitierFtth by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    // Poteau
    var poleNature by remember { mutableStateOf("béton") }
    var poleHeight by remember { mutableIntStateOf(8) }

    // Chambre
    var chamberType by remember { mutableStateOf("L2T") }

    // Boîtier
    var boitierType by remember { mutableStateOf("PBO") }
    var isSaturated by remember { mutableStateOf(false) }
    var boitierSupport by remember { mutableStateOf("Poteau") }

    // SRO
    var sroType by remember { mutableStateOf("armoire de rue") }
    var sroCapacity by remember { mutableStateOf("360 FO") }

    // Immeuble
    var buildingFloors by remember { mutableIntStateOf(4) }
    var buildingDwellings by remember { mutableIntStateOf(12) }
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
                                        .clickable { selectedType = t },
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
                                                imageVector = FtthNodeVisuals.getNodeIcon(t),
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
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

                // STATUT (Existant, À poser, À remplacer, À déposer)
                Text(text = "Statut :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NodeStatus.values().forEach { st ->
                        FilterChip(
                            selected = status == st,
                            onClick = { status = st },
                            label = { Text(st.label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ETAT (Conforme, Non conforme)
                Text(text = "État :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NodeConformity.values().forEach { c ->
                        FilterChip(
                            selected = etat == c,
                            onClick = { etat = c },
                            label = { Text(c.label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Identifiant & Nom
                OutlinedTextField(
                    value = nodeId,
                    onValueChange = { nodeId = it },
                    label = { Text("Code Identifiant (ex: $generatedId)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du nœud") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

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

                // BOITIER FTTH (Non, Oui - sauf Villa et SRO)
                if (selectedType != FtthNodeType.VILLA && selectedType != FtthNodeType.SRO) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Boîtier FTTH présent :", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(text = if (hasBoitierFtth) "Oui" else "Non", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(checked = hasBoitierFtth, onCheckedChange = { hasBoitierFtth = it })
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Champs spécifiques
                when (selectedType) {
                    FtthNodeType.POTEAU -> {
                        Text(text = "Nature :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("bois", "métal", "béton", "façade").forEach { nat ->
                                FilterChip(
                                    selected = poleNature == nat,
                                    onClick = { poleNature = nat },
                                    label = { Text(nat.capitalize(Locale.ROOT)) }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("L0T", "L1T", "L2T", "K1C", "K2C").forEach { ch ->
                                FilterChip(
                                    selected = chamberType == ch,
                                    onClick = { chamberType = ch },
                                    label = { Text(ch) }
                                )
                            }
                        }
                    }

                    FtthNodeType.BOITIER -> {
                        Text(text = "Type boîtier :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("BPE", "PBO", "PB", "autre").forEach { bt ->
                                FilterChip(
                                    selected = boitierType == bt,
                                    onClick = { boitierType = bt },
                                    label = { Text(bt) }
                                )
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
                            listOf("armoire de rue", "local").forEach { st ->
                                FilterChip(
                                    selected = sroType == st,
                                    onClick = { sroType = st },
                                    label = { Text(st.capitalize(Locale.ROOT)) }
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
                            listOf("façade", "souterrain", "aérien").forEach { md ->
                                FilterChip(
                                    selected = buildingConnectionMode == md,
                                    onClick = { buildingConnectionMode = md },
                                    label = { Text(md.capitalize(Locale.ROOT)) }
                                )
                            }
                        }
                    }

                    FtthNodeType.VILLA -> {
                        Text(text = "Raccordement :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("aérien", "souterrain", "façade").forEach { vm ->
                                FilterChip(
                                    selected = villaConnectionMode == vm,
                                    onClick = { villaConnectionMode = vm },
                                    label = { Text(vm.capitalize(Locale.ROOT)) }
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
                            val newNode = FtthNodeEntity(
                                id = nodeId.trim().ifEmpty { generatedId },
                                type = selectedType,
                                name = name.trim().ifEmpty { selectedType.label },
                                latitude = latitude,
                                longitude = longitude,
                                status = status,
                                etat = etat,
                                address = address,
                                hasBoitierFtth = if (selectedType == FtthNodeType.VILLA || selectedType == FtthNodeType.SRO) false else hasBoitierFtth,
                                notes = notes,
                                poleNature = poleNature,
                                poleHeight = poleHeight,
                                chamberType = chamberType,
                                boitierType = boitierType,
                                isSaturated = isSaturated,
                                boitierSupport = boitierSupport,
                                sroType = sroType,
                                sroCapacity = sroCapacity,
                                buildingFloors = buildingFloors,
                                buildingDwellings = buildingDwellings,
                                hasLocalTechnique = hasLocalTechnique,
                                hasGaineMontante = hasGaineMontante,
                                syndicAuthorization = syndicAuthorization,
                                syndicContact = syndicContact,
                                buildingConnectionMode = buildingConnectionMode,
                                villaConnectionMode = villaConnectionMode
                            )
                            onNodeCreated(newNode)
                        },
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
