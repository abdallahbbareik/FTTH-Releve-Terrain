package com.example.ui.components

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.local.SurveyStatus

@Composable
fun AddNodeDialog(
    latitude: Double,
    longitude: Double,
    existingNodesCount: Int,
    onDismiss: () -> Unit,
    onNodeCreated: (FtthNodeEntity) -> Unit
) {
    var selectedType by remember { mutableStateOf(FtthNodeType.PBO) }

    // Auto-generate suggested ID
    val generatedId = remember(selectedType, existingNodesCount) {
        val prefix = when (selectedType) {
            FtthNodeType.POTEAU -> "POT"
            FtthNodeType.CHAMBRE -> "CH"
            FtthNodeType.BOITIER_BPE -> "BPE"
            FtthNodeType.PBO -> "PBO"
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
                FtthNodeType.CHAMBRE -> "Chambre L2T Trottoir"
                FtthNodeType.BOITIER_BPE -> "Boîtier BPE 48FO"
                FtthNodeType.PBO -> "PBO 8FO Aérien"
                FtthNodeType.IMMEUBLE -> "Immeuble Collectif"
                FtthNodeType.VILLA -> "Pavillon Individuel"
            }
        )
    }
    var address by remember { mutableStateOf("") }
    var cadastre by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

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
                // Title
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
                            text = "Piqueter un nouveau nœud",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Position: %.6f, %.6f".format(latitude, longitude),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Node Type Selection Grid
                Text(
                    text = "Type de nœud d'architecture FTTH :",
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
                                    color = if (isSelected) col.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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

                Spacer(modifier = Modifier.height(16.dp))

                // Input fields
                OutlinedTextField(
                    value = nodeId,
                    onValueChange = { nodeId = it },
                    label = { Text("Identifiant unique (Code SIG)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_node_id_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Désignation du nœud") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        label = { Text("Réf. Cadastre") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes de relevé initial") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
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
                                status = SurveyStatus.PENDING,
                                address = address,
                                referenceCadastre = cadastre,
                                notes = notes
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
