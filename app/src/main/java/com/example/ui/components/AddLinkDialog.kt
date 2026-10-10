package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.FtthNodeEntity
import com.example.data.repository.FtthRepository
import com.example.data.storage.StoredTrack

@Composable
fun AddLinkDialog(
    fromNode: FtthNodeEntity,
    toNode: FtthNodeEntity,
    availableTracks: List<StoredTrack> = emptyList(),
    onDismiss: () -> Unit,
    onLinkCreated: (cableType: String, installationType: String, capacityFO: Int, associatedTrackId: String) -> Unit
) {
    var cableType by remember { mutableStateOf("Distribution") }
    var installationType by remember { mutableStateOf("Aérien") }
    var capacityFO by remember { mutableIntStateOf(24) }
    var associatedTrackId by remember { mutableStateOf("") }

    val selectedTrack = remember(associatedTrackId, availableTracks) {
        availableTracks.firstOrNull { it.id == associatedTrackId }
    }

    val distanceMeters = remember(fromNode, toNode, selectedTrack) {
        if (selectedTrack != null && selectedTrack.totalDistanceMeters > 0) {
            selectedTrack.totalDistanceMeters
        } else {
            FtthRepository.calculateDistanceMeters(
                fromNode.latitude, fromNode.longitude,
                toNode.latitude, toNode.longitude
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .testTag("add_link_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.AltRoute,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Ajouter Câble Fibre Optique",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Distance estimée : ${distanceMeters.toInt()} mètres" + if (selectedTrack != null) " (Suivie sur ${selectedTrack.name})" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Nodes connection card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Origine", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(text = fromNode.id, fontWeight = FontWeight.Bold, color = FtthNodeVisuals.getNodeColor(fromNode.type))
                            Text(text = fromNode.type.label, style = MaterialTheme.typography.bodySmall)
                        }

                        Text(text = "──────▶", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Extrémité", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(text = toNode.id, fontWeight = FontWeight.Bold, color = FtthNodeVisuals.getNodeColor(toNode.type))
                            Text(text = toNode.type.label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Cable Type
                Text(text = "Type de réseau FTTH :", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Transport", "Distribution", "Branchement").forEach { ct ->
                        FilterChip(
                            selected = cableType == ct,
                            onClick = {
                                cableType = ct
                                if (ct == "Transport") capacityFO = 144
                                else if (ct == "Distribution") capacityFO = 24
                                else capacityFO = 1
                            },
                            label = { Text(ct) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode de pose
                Text(text = "Mode de pose / Génie civil :", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Aérien", "Souterrain", "Façade").forEach { inst ->
                        FilterChip(
                            selected = installationType == inst,
                            onClick = { installationType = inst },
                            label = { Text(inst) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Choix de la trace (non-linéaire) empruntée
                Text(
                    text = "Tracé de l'infrastructure support (Génie civil / Façade / Aérien) :",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Permet de faire suivre au câble le tracé réel non-linéaire de la conduite ou de la façade",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = associatedTrackId.isEmpty(),
                        onClick = { associatedTrackId = "" },
                        label = { Text("Direct (Ligne droite)") }
                    )
                    availableTracks.forEach { trk ->
                        FilterChip(
                            selected = associatedTrackId == trk.id,
                            onClick = { associatedTrackId = trk.id },
                            label = { Text("${trk.name} (${trk.type})") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Capacity FO
                Text(text = "Capacité du câble (Fibre Optique) :", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 4, 6, 12, 24, 48, 72, 96, 144, 288).forEach { cap ->
                        FilterChip(
                            selected = capacityFO == cap,
                            onClick = { capacityFO = cap },
                            label = { Text("${cap}FO", fontWeight = if (capacityFO == cap) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Annuler")
                    }
                    Button(
                        onClick = { onLinkCreated(cableType, installationType, capacityFO, associatedTrackId) },
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("confirm_create_cable_button")
                    ) {
                        Text("Ajouter le câble")
                    }
                }
            }
        }
    }
}
