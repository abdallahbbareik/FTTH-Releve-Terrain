package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.local.SurveyStatus
import com.example.data.local.SyncState
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

    // Form states
    var name by remember(node) { mutableStateOf(node.name) }
    var address by remember(node) { mutableStateOf(node.address) }
    var cadastre by remember(node) { mutableStateOf(node.referenceCadastre) }
    var status by remember(node) { mutableStateOf(node.status) }
    var notes by remember(node) { mutableStateOf(node.notes) }
    var photoCount by remember(node) { mutableIntStateOf(node.photoCount) }

    // Type specific states
    var poleMaterial by remember(node) { mutableStateOf(node.poleMaterial) }
    var poleHeight by remember(node) { mutableDoubleStateOf(node.poleHeightMeters) }
    var poleLoad by remember(node) { mutableIntStateOf(node.poleResidualLoadDan) }

    var chamberType by remember(node) { mutableStateOf(node.chamberType) }
    var chamberCover by remember(node) { mutableStateOf(node.chamberCoverState) }
    var chamberSat by remember(node) { mutableIntStateOf(node.chamberSaturationPercent) }
    var hasWater by remember(node) { mutableStateOf(node.hasWaterOrMud) }

    var bpeCap by remember(node) { mutableIntStateOf(node.bpeCapacityFO) }
    var bpeSpliced by remember(node) { mutableIntStateOf(node.bpeSplicedCount) }

    var pboType by remember(node) { mutableStateOf(node.pboType) }
    var pboCapacity by remember(node) { mutableIntStateOf(node.pboCapacityPorts) }
    var pboConnected by remember(node) { mutableIntStateOf(node.pboConnectedPorts) }
    var opticalPower by remember(node) { mutableDoubleStateOf(node.opticalPowerDbm) }

    var dwellings by remember(node) { mutableIntStateOf(node.buildingDwellings) }
    var hasPMI by remember(node) { mutableStateOf(node.hasPMI) }

    var dropType by remember(node) { mutableStateOf(node.dropCableType) }
    var conduitLength by remember(node) { mutableDoubleStateOf(node.privateConduitLengthMeters) }

    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                // Status Selector Chips
                Text(
                    text = "Statut de conformité du piquetage",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SurveyStatus.values().forEach { st ->
                        val isSel = status == st
                        FilterChip(
                            selected = isSel,
                            onClick = { status = st },
                            label = {
                                Text(
                                    text = when (st) {
                                        SurveyStatus.VALIDATED -> "Conforme"
                                        SurveyStatus.PENDING -> "À auditer"
                                        SurveyStatus.NON_CONFORMANT -> "Non conf."
                                        SurveyStatus.NEEDS_REPLACEMENT -> "Travaux"
                                    },
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

                Spacer(modifier = Modifier.height(16.dp))

                // GPS & Location Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Coordonnées GPS terrain",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "WGS84 • Réf SIG",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Lat: %.6f   •   Lon: %.6f".format(node.latitude, node.longitude),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // General Identification Fields
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Désignation / Nom du nœud") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("node_name_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Adresse terrain") },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = cadastre,
                        onValueChange = { cadastre = it },
                        label = { Text("Parcelle Cadastre") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // TYPE SPECIFIC TECHNICAL ATTRIBUTES SECTION
                Text(
                    text = "Spécifications techniques : ${node.type.label}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                when (node.type) {
                    FtthNodeType.POTEAU -> {
                        // Pole technical form
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Béton", "Bois", "Métal", "Mixte").forEach { mat ->
                                FilterChip(
                                    selected = poleMaterial == mat,
                                    onClick = { poleMaterial = mat },
                                    label = { Text(mat) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = poleHeight.toString(),
                                onValueChange = { poleHeight = it.toDoubleOrNull() ?: poleHeight },
                                label = { Text("Hauteur (m)") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = poleLoad.toString(),
                                onValueChange = { poleLoad = it.toIntOrNull() ?: poleLoad },
                                label = { Text("Charge résiduelle (daN)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    FtthNodeType.CHAMBRE -> {
                        // Chamber technical form
                        Text(text = "Modèle normalisé :", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("L0T", "L1T", "L2T", "K1C", "K2C").forEach { ch ->
                                FilterChip(
                                    selected = chamberType == ch,
                                    onClick = { chamberType = ch },
                                    label = { Text(ch) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Taux de saturation des masques : $chamberSat%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = chamberSat.toFloat(),
                            onValueChange = { chamberSat = it.toInt() },
                            valueRange = 0f..100f
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(text = "Présence d'eau ou boue (Hydrocurage requis)")
                            Spacer(modifier = Modifier.weight(1f))
                            Switch(checked = hasWater, onCheckedChange = { hasWater = it })
                        }
                    }

                    FtthNodeType.BOITIER_BPE -> {
                        // Splice box
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = bpeCap.toString(),
                                onValueChange = { bpeCap = it.toIntOrNull() ?: bpeCap },
                                label = { Text("Capacité totale FO") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = bpeSpliced.toString(),
                                onValueChange = { bpeSpliced = it.toIntOrNull() ?: bpeSpliced },
                                label = { Text("Épissures réalisées") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    FtthNodeType.PBO -> {
                        // PBO terminal box
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Aérien", "Façade", "Chambre", "Intérieur").forEach { t ->
                                FilterChip(
                                    selected = pboType == t,
                                    onClick = { pboType = t },
                                    label = { Text(t) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = pboCapacity.toString(),
                                onValueChange = { pboCapacity = it.toIntOrNull() ?: pboCapacity },
                                label = { Text("Capacité ports FO") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = pboConnected.toString(),
                                onValueChange = { pboConnected = it.toIntOrNull() ?: pboConnected },
                                label = { Text("Clients branchés") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = opticalPower.toString(),
                                onValueChange = { opticalPower = it.toDoubleOrNull() ?: opticalPower },
                                label = { Text("Puissance optique (dBm)") },
                                leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
                                modifier = Modifier.weight(1.2f)
                            )
                            val isSignalGood = opticalPower in -25.0..-15.0
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSignalGood) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (isSignalGood) "Signal Optimal" else "Atténuation !",
                                    color = if (isSignalGood) Color(0xFF166534) else Color(0xFF991B1B),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }

                    FtthNodeType.IMMEUBLE -> {
                        // Building
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = dwellings.toString(),
                                onValueChange = { dwellings = it.toIntOrNull() ?: dwellings },
                                label = { Text("Nombre de logements (EL)") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(text = "Présence Point de Mutualisation (PMI)")
                            Spacer(modifier = Modifier.weight(1f))
                            Switch(checked = hasPMI, onCheckedChange = { hasPMI = it })
                        }
                    }

                    FtthNodeType.VILLA -> {
                        // House
                        OutlinedTextField(
                            value = dropType,
                            onValueChange = { dropType = it },
                            label = { Text("Type de raccordement") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = conduitLength.toString(),
                            onValueChange = { conduitLength = it.toDoubleOrNull() ?: conduitLength },
                            label = { Text("Longueur de fourreau privatif (m)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Photos & Attachments
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = "Photos",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Photos du piquetage ($photoCount jointes)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Preuves photographiques d'appui et étiquetage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        OutlinedButton(
                            onClick = { photoCount++ },
                            modifier = Modifier.testTag("add_photo_button")
                        ) {
                            Text("+ Prendre photo")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Survey Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observations & remarques du technicien") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("survey_notes_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Audit metadata
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(node.updatedAt))
                    Text(
                        text = "Par: ${node.technicianName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "Dernière modif: $dateStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
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
                                address = address,
                                referenceCadastre = cadastre,
                                status = status,
                                notes = notes,
                                photoCount = photoCount,
                                poleMaterial = poleMaterial,
                                poleHeightMeters = poleHeight,
                                poleResidualLoadDan = poleLoad,
                                chamberType = chamberType,
                                chamberCoverState = chamberCover,
                                chamberSaturationPercent = chamberSat,
                                hasWaterOrMud = hasWater,
                                bpeCapacityFO = bpeCap,
                                bpeSplicedCount = bpeSpliced,
                                pboType = pboType,
                                pboCapacityPorts = pboCapacity,
                                pboConnectedPorts = pboConnected,
                                opticalPowerDbm = opticalPower,
                                buildingDwellings = dwellings,
                                hasPMI = hasPMI,
                                dropCableType = dropType,
                                privateConduitLengthMeters = conduitLength
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
                            Text(
                                text = "Cette action retirera ce nœud et ses liaisons de la base locale et du SIG cloud.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onDelete(node.id)
                                        showDeleteConfirm = false
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Oui, supprimer définitivement")
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
