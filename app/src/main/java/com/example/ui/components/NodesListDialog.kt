package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.local.NodeStatus
import java.util.Locale

@Composable
fun NodesListDialog(
    nodes: List<FtthNodeEntity>,
    onSelectNode: (FtthNodeEntity) -> Unit,
    onDeleteNode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<FtthNodeType?>(null) }
    var selectedStatusFilter by remember { mutableStateOf<NodeStatus?>(null) }
    var nodeToDelete by remember { mutableStateOf<FtthNodeEntity?>(null) }

    val filteredList = remember(nodes, searchQuery, selectedTypeFilter, selectedStatusFilter) {
        nodes.filter { node ->
            val matchesQuery = searchQuery.isBlank() ||
                    node.id.contains(searchQuery, ignoreCase = true) ||
                    node.name.contains(searchQuery, ignoreCase = true) ||
                    node.address.contains(searchQuery, ignoreCase = true)
            val matchesType = selectedTypeFilter == null || node.type == selectedTypeFilter
            val matchesStatus = selectedStatusFilter == null || node.status == selectedStatusFilter
            matchesQuery && matchesType && matchesStatus
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
                .fillMaxWidth(0.94f)
                .testTag("nodes_list_dialog")
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Liste des Nœuds relevés (${nodes.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Documents/Releve-Terrain/noeuds.json",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Champ de recherche
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Rechercher par réf, nom ou adresse...", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Effacer", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Filtres par type (défilement horizontal)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedTypeFilter == null,
                        onClick = { selectedTypeFilter = null },
                        label = { Text("Tous (${nodes.size})", fontSize = 11.sp) }
                    )
                    FtthNodeType.values().forEach { type ->
                        val count = nodes.count { it.type == type }
                        if (count > 0) {
                            FilterChip(
                                selected = selectedTypeFilter == type,
                                onClick = { selectedTypeFilter = if (selectedTypeFilter == type) null else type },
                                label = { Text("${type.label} ($count)", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = FtthNodeVisuals.getNodeColor(type).copy(alpha = 0.2f),
                                    selectedLabelColor = FtthNodeVisuals.getNodeColor(type)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Liste des nœuds
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (nodes.isEmpty()) "Aucun point relevé pour l'instant.\nUtilisez 'Piquet GPS' ou 'Pointer Carte' pour ajouter un point." else "Aucun point ne correspond à vos filtres.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .height(380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredList, key = { it.id }) { node ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectNode(node)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Pastille couleur type
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(FtthNodeVisuals.getNodeColor(node.type)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(FtthNodeVisuals.getNodeDrawableRes(node.type)),
                                            contentDescription = node.type.label,
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = node.id,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = FtthNodeVisuals.getNodeColor(node.type)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = FtthNodeVisuals.getStatusColor(node.status).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = FtthNodeVisuals.getStatusDisplayLabel(node.status, node.type),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = FtthNodeVisuals.getStatusColor(node.status),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }

                                        val nodeDetailInfo = when {
                                            (node.type == FtthNodeType.IMMEUBLE || node.type == FtthNodeType.VILLA) && node.buildingName.isNotBlank() ->
                                                node.buildingName
                                            (node.type == FtthNodeType.IMMEUBLE || node.type == FtthNodeType.VILLA) && node.name.isNotBlank() && node.name != node.id ->
                                                node.name
                                            node.operator.isNotBlank() -> "Opérateur : ${node.operator}"
                                            node.type == FtthNodeType.POTEAU -> "Nature : ${node.poleNature.replaceFirstChar { it.uppercase() }} (${node.poleHeight}m)"
                                            node.type == FtthNodeType.CHAMBRE -> "Chambre ${node.chamberType}"
                                            else -> node.type.label
                                        }
                                        Text(
                                            text = nodeDetailInfo,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )

                                        if (node.address.isNotBlank()) {
                                            Text(
                                                text = node.address,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(
                                                text = "${String.format(Locale.FRANCE, "%.5f", node.latitude)}, ${String.format(Locale.FRANCE, "%.5f", node.longitude)}",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            if (node.photos.isNotEmpty()) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(
                                                    imageVector = Icons.Default.PhotoCamera,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(12.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = "${node.photos.size}",
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = { nodeToDelete = node },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Supprimer",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Ouvrir",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Confirmation de suppression
                if (nodeToDelete != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Supprimer le nœud ${nodeToDelete!!.id} ?",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                androidx.compose.material3.Button(
                                    onClick = {
                                        onDeleteNode(nodeToDelete!!.id)
                                        nodeToDelete = null
                                    },
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = androidx.compose.material3.ButtonDefaults.TextButtonContentPadding
                                ) {
                                    Text("Confirmer", fontSize = 11.sp)
                                }
                                androidx.compose.material3.OutlinedButton(
                                    onClick = { nodeToDelete = null },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = androidx.compose.material3.ButtonDefaults.TextButtonContentPadding
                                ) {
                                    Text("Annuler", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
