package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.local.NodeConformity
import com.example.data.local.NodeStatus
import com.example.data.util.AddressHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeDetailSheet(
    node: FtthNodeEntity,
    onDismiss: () -> Unit,
    onSave: (FtthNodeEntity) -> Unit,
    onDelete: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Champs communs à tous les types
    var name by remember(node) { mutableStateOf(node.name) }
    var status by remember(node) { mutableStateOf(node.status) }
    var etat by remember(node) { mutableStateOf(node.etat) }
    var address by remember(node) { mutableStateOf(node.address) }
    var hasBoitierFtth by remember(node) { mutableStateOf(node.hasBoitierFtth) }
    var notes by remember(node) { mutableStateOf(node.notes) }
    var photoCount by remember(node) { mutableIntStateOf(node.photoCount) }

    // Poteau
    var poleNature by remember(node) { mutableStateOf(node.poleNature) }
    var poleHeight by remember(node) { mutableIntStateOf(node.poleHeight) }

    // Chambre
    var chamberType by remember(node) { mutableStateOf(node.chamberType) }

    // Boîtier
    var boitierType by remember(node) { mutableStateOf(node.boitierType) }
    var isSaturated by remember(node) { mutableStateOf(node.isSaturated) }
    var boitierSupport by remember(node) { mutableStateOf(node.boitierSupport) }

    // SRO
    var sroType by remember(node) { mutableStateOf(node.sroType) }
    var sroCapacity by remember(node) { mutableStateOf(node.sroCapacity) }

    // Immeuble
    var buildingFloors by remember(node) { mutableIntStateOf(node.buildingFloors) }
    var buildingDwellings by remember(node) { mutableIntStateOf(node.buildingDwellings) }
    var hasLocalTechnique by remember(node) { mutableStateOf(node.hasLocalTechnique) }
    var hasGaineMontante by remember(node) { mutableStateOf(node.hasGaineMontante) }
    var syndicAuthorization by remember(node) { mutableStateOf(node.syndicAuthorization) }
    var syndicContact by remember(node) { mutableStateOf(node.syndicContact) }
    var buildingConnectionMode by remember(node) { mutableStateOf(node.buildingConnectionMode) }

    // Villa/Pavillon
    var villaConnectionMode by remember(node) { mutableStateOf(node.villaConnectionMode) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isGeocoding by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        modifier = Modifier.testTag("node_detail_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Header bar
            Surface(
                color = FtthNodeVisuals.getNodeColor(node.type).copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(FtthNodeVisuals.getNodeColor(node.type)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = FtthNodeVisuals.getNodeIcon(node.type),
                            contentDescription = node.type.label,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = node.id,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = FtthNodeVisuals.getNodeColor(node.type)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = node.type.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = node.type.category,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_detail_sheet_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }
            }

            Column(modifier = Modifier.padding(20.dp)) {

                // 1. STATUT (Existant, À poser, À remplacer, À déposer)
                Text(
                    text = "Statut :",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NodeStatus.values().forEach { st ->
                        val isSel = status == st
                        FilterChip(
                            selected = isSel,
                            onClick = { status = st },
                            label = {
                                Text(
                                    text = st.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FtthNodeVisuals.getStatusColor(st).copy(alpha = 0.2f),
                                selectedLabelColor = FtthNodeVisuals.getStatusColor(st)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. ETAT (Conforme, Non conforme)
                Text(
                    text = "État :",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NodeConformity.values().forEach { c ->
                        val isSel = etat == c
                        val col = FtthNodeVisuals.getConformityColor(c)
                        FilterChip(
                            selected = isSel,
                            onClick = { etat = c },
                            label = {
                                Text(
                                    text = c.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = FtthNodeVisuals.getConformityIcon(c),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = col
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = col.copy(alpha = 0.2f),
                                selectedLabelColor = col
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Coordonnées GPS (WGS84)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GPS : %.6f, %.6f".format(node.latitude, node.longitude),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. NOM DU NŒUD
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom du nœud") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("node_name_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 4. ADRESSE (AVEC RÉCUPÉRATION AUTOMATIQUE)
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Adresse") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("node_address_input"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            isGeocoding = true
                            address = AddressHelper.getAddressForCoordinates(context, node.latitude, node.longitude)
                            isGeocoding = false
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                    enabled = !isGeocoding
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoMode,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isGeocoding) "Recherche..." else "Récupérer automatiquement",
                        fontSize = 11.sp
                    )
                }

                // 5. BOITIER FTTH (NON, OUI - SAUF VILLA ET SRO)
                if (node.type != FtthNodeType.VILLA && node.type != FtthNodeType.SRO) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Boîtier FTTH présent :",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (hasBoitierFtth) "Oui" else "Non",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasBoitierFtth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(
                                checked = hasBoitierFtth,
                                onCheckedChange = { hasBoitierFtth = it }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // CHAMPS SPÉCIFIQUES PAR TYPE DE NŒUD
                Text(
                    text = "Spécifications métier : ${node.type.label}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                when (node.type) {
                    // POTEAU : Nature (bois, métal, béton, façade), Hauteur (7, 8, 9, 10)
                    FtthNodeType.POTEAU -> {
                        Text(text = "Nature de l'appui :", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("bois" to "Bois", "métal" to "Métal", "béton" to "Béton", "façade" to "Façade").forEach { (nat, lbl) ->
                                FilterChip(
                                    selected = poleNature.equals(nat, ignoreCase = true),
                                    onClick = { poleNature = nat },
                                    label = { Text(lbl) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Hauteur de l'appui (mètres) :", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
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

                    // CHAMBRE : Type (L0T, L1T, L2T, K1C, K2C, etc.)
                    FtthNodeType.CHAMBRE -> {
                        Text(text = "Type de chambre :", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("L0T", "L1T", "L2T", "K1C", "K2C").forEach { ch ->
                                FilterChip(
                                    selected = chamberType.equals(ch, ignoreCase = true),
                                    onClick = { chamberType = ch },
                                    label = { Text(ch) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = chamberType,
                            onValueChange = { chamberType = it },
                            label = { Text("Autre type / format chambre") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    // BOÎTIER : Type (BPE, PBO, PB, autre), Saturé (Non, Oui), Support (Poteau, Chambre, Façade, Sous-Sol)
                    FtthNodeType.BOITIER -> {
                        Text(text = "Type de boîtier :", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("BPE", "PBO", "PB", "autre").forEach { bt ->
                                FilterChip(
                                    selected = boitierType.equals(bt, ignoreCase = true),
                                    onClick = { boitierType = bt },
                                    label = { Text(bt.uppercase(Locale.ROOT)) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "Boîtier saturé :", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = if (isSaturated) "Oui (saturation 100%)" else "Non (ports libres disponibles)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSaturated) Color(0xFFDC2626) else Color(0xFF16A34A)
                                    )
                                }
                                Switch(checked = isSaturated, onCheckedChange = { isSaturated = it })
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Support de fixation :", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Poteau", "Chambre", "Façade", "Sous-Sol").forEach { sup ->
                                FilterChip(
                                    selected = boitierSupport.equals(sup, ignoreCase = true),
                                    onClick = { boitierSupport = sup },
                                    label = { Text(sup) }
                                )
                            }
                        }
                    }

                    // SRO : Type (armoire de rue, local), Capacité
                    FtthNodeType.SRO -> {
                        Text(text = "Type de SRO :", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("armoire de rue" to "Armoire de rue", "local" to "Local technique").forEach { (st, lbl) ->
                                FilterChip(
                                    selected = sroType.equals(st, ignoreCase = true),
                                    onClick = { sroType = st },
                                    label = { Text(lbl) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = sroCapacity,
                            onValueChange = { sroCapacity = it },
                            label = { Text("Capacité nominale (ex: 360 FO, 720 FO, 1000 FO)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("144 FO", "360 FO", "720 FO", "1000 FO").forEach { cap ->
                                FilterChip(
                                    selected = sroCapacity == cap,
                                    onClick = { sroCapacity = cap },
                                    label = { Text(cap) }
                                )
                            }
                        }
                    }

                    // IMMEUBLE : Nombre étages, Nombre logement, Local technique (Oui/Non), Gaine montante (oui/non), Autorisation syndique, Contact syndic (facultatif), Mode de raccordement (façade, souterrain, aérien)
                    FtthNodeType.IMMEUBLE -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = buildingFloors.toString(),
                                onValueChange = { buildingFloors = it.toIntOrNull() ?: buildingFloors },
                                label = { Text("Nombre étages") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = buildingDwellings.toString(),
                                onValueChange = { buildingDwellings = it.toIntOrNull() ?: buildingDwellings },
                                label = { Text("Nombre logements") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "Local technique :", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(text = if (hasLocalTechnique) "Oui" else "Non", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(checked = hasLocalTechnique, onCheckedChange = { hasLocalTechnique = it })
                                }
                                HorizontalDivider()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "Gaine montante :", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(text = if (hasGaineMontante) "Oui" else "Non", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Switch(checked = hasGaineMontante, onCheckedChange = { hasGaineMontante = it })
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Autorisation syndic :", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Accord obtenu", "En attente", "Refusé").forEach { auth ->
                                FilterChip(
                                    selected = syndicAuthorization.equals(auth, ignoreCase = true),
                                    onClick = { syndicAuthorization = auth },
                                    label = { Text(auth) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = syndicContact,
                            onValueChange = { syndicContact = it },
                            label = { Text("Contact syndic (facultatif)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Mode de raccordement :", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
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

                    // VILLA / PAVILLON : Raccordement : aérien, souterrain, façade
                    FtthNodeType.VILLA -> {
                        Text(text = "Mode de raccordement :", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                Spacer(modifier = Modifier.height(16.dp))

                // 6. COMMENTAIRE
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Commentaire") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("survey_notes_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Photos & Attachments
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = "Photos",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Photos du relevé ($photoCount jointes)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedButton(
                            onClick = { photoCount++ },
                            modifier = Modifier.testTag("add_photo_button")
                        ) {
                            Text("+ Photo")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons: Save & Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("delete_node_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Supprimer")
                    }

                    Button(
                        onClick = {
                            val updated = node.copy(
                                name = name,
                                status = status,
                                etat = etat,
                                address = address,
                                hasBoitierFtth = if (node.type == FtthNodeType.VILLA || node.type == FtthNodeType.SRO) false else hasBoitierFtth,
                                notes = notes,
                                photoCount = photoCount,
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
                            onSave(updated)
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("save_node_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enregistrer")
                    }
                }

                if (showDeleteConfirm) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Confirmer la suppression de ${node.id} ?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onDelete(node.id)
                                        showDeleteConfirm = false
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Oui, supprimer")
                                }
                                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                                    Text("Annuler")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
