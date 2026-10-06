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
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.storage.StoredTrack
import com.example.ui.theme.TelecomCyan
import com.example.ui.theme.TelecomNavy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectFolderDialog(
    nodes: List<FtthNodeEntity>,
    tracks: List<StoredTrack>,
    links: List<FtthLinkEntity>,
    onSelectNode: (FtthNodeEntity) -> Unit,
    onDeleteNode: (String) -> Unit,
    onSelectTrack: (StoredTrack) -> Unit,
    onDeleteTrack: (String) -> Unit,
    onSelectLink: (FtthLinkEntity) -> Unit,
    onDeleteLink: (String) -> Unit,
    onExportZip: () -> Unit,
    onExportKmz: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<FtthNodeType?>(null) }
    var itemToDelete by remember { mutableStateOf<Pair<String, String>?>(null) } // type ("node", "track", "link") to id

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp)
                .testTag("project_folder_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // En-tête Dossier
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(TelecomNavy),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = TelecomCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dossier : Documents/Releve-Terrain",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${nodes.size} points • ${tracks.size} trajets • ${links.size} liaisons",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_project_folder_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Onglets de navigation : Points / Trajets / Liaisons
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Points (${nodes.size})", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Trajets (${tracks.size})", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cable, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Liaisons (${links.size})", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Contenu selon l'onglet
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> {
                            // ONGLET 0 : POINTS PIQUETÉS
                            NodesListContent(
                                nodes = nodes,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { searchQuery = it },
                                selectedTypeFilter = selectedTypeFilter,
                                onTypeFilterChange = { selectedTypeFilter = it },
                                onSelectNode = onSelectNode,
                                onRequestDelete = { itemToDelete = Pair("node", it) }
                            )
                        }
                        1 -> {
                            // ONGLET 1 : TRAJETS / PARCOURS
                            TracksListContent(
                                tracks = tracks,
                                onSelectTrack = onSelectTrack,
                                onRequestDelete = { itemToDelete = Pair("track", it) }
                            )
                        }
                        2 -> {
                            // ONGLET 2 : LIAISONS FIBRE
                            LinksListContent(
                                links = links,
                                onSelectLink = onSelectLink,
                                onRequestDelete = { itemToDelete = Pair("link", it) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Boutons d'export rapide et fermeture
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onExportZip,
                            colors = ButtonDefaults.buttonColors(containerColor = TelecomNavy),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("folder_export_zip_button")
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = null, tint = TelecomCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export ZIP", fontSize = 12.sp, color = Color.White)
                        }
                        OutlinedButton(
                            onClick = onExportKmz,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("folder_export_kmz_button")
                        ) {
                            Text("KMZ", fontSize = 12.sp)
                        }
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Fermer", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Dialogue de confirmation de suppression
    if (itemToDelete != null) {
        val (type, id) = itemToDelete!!
        val label = when (type) {
            "node" -> "le nœud / point $id"
            "track" -> "le tracé $id"
            "link" -> "la liaison fibre $id"
            else -> "cet élément"
        }
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Confirmer la suppression") },
            text = { Text("Êtes-vous sûr de vouloir supprimer définitivement $label du dossier Documents/Releve-Terrain ?") },
            confirmButton = {
                Button(
                    onClick = {
                        when (type) {
                            "node" -> onDeleteNode(id)
                            "track" -> onDeleteTrack(id)
                            "link" -> onDeleteLink(id)
                        }
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun NodesListContent(
    nodes: List<FtthNodeEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedTypeFilter: FtthNodeType?,
    onTypeFilterChange: (FtthNodeType?) -> Unit,
    onSelectNode: (FtthNodeEntity) -> Unit,
    onRequestDelete: (String) -> Unit
) {
    val filtered = remember(nodes, searchQuery, selectedTypeFilter) {
        nodes.filter { node ->
            val matchType = selectedTypeFilter == null || node.type == selectedTypeFilter
            val matchQuery = searchQuery.isBlank() ||
                    node.id.contains(searchQuery, ignoreCase = true) ||
                    node.name.contains(searchQuery, ignoreCase = true) ||
                    node.address.contains(searchQuery, ignoreCase = true)
            matchType && matchQuery
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Rechercher point par ID, nom, adresse...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Effacer", modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedTypeFilter == null,
                onClick = { onTypeFilterChange(null) },
                label = { Text("Tous (${nodes.size})", fontSize = 11.sp) }
            )
            FtthNodeType.values().forEach { type ->
                val count = nodes.count { it.type == type }
                if (count > 0 || selectedTypeFilter == type) {
                    FilterChip(
                        selected = selectedTypeFilter == type,
                        onClick = { onTypeFilterChange(if (selectedTypeFilter == type) null else type) },
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

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (nodes.isEmpty()) "Aucun point relevé dans le projet" else "Aucun résultat trouvé",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { node ->
                    NodeCardItem(
                        node = node,
                        onSelectNode = { onSelectNode(node) },
                        onDeleteNode = { onRequestDelete(node.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NodeCardItem(
    node: FtthNodeEntity,
    onSelectNode: () -> Unit,
    onDeleteNode: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectNode() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(FtthNodeVisuals.getNodeColor(node.type).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = FtthNodeVisuals.getNodeIcon(node.type),
                    contentDescription = null,
                    tint = FtthNodeVisuals.getNodeColor(node.type),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = node.id,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = FtthNodeVisuals.getNodeColor(node.type).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = node.type.label,
                            color = FtthNodeVisuals.getNodeColor(node.type),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = if (node.address.isNotBlank()) node.address else "${node.latitude.format(5)}, ${node.longitude.format(5)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                if (node.photos.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF2563EB))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${node.photos.size} photo(s)", fontSize = 10.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            IconButton(
                onClick = onDeleteNode,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }

            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TracksListContent(
    tracks: List<StoredTrack>,
    onSelectTrack: (StoredTrack) -> Unit,
    onRequestDelete: (String) -> Unit
) {
    if (tracks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Default.Timeline, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Aucun trajet enregistré.\nUtilisez 'Démarrer GPS' ou 'Tracé Manuel' pour enregistrer des parcours.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tracks, key = { it.id }) { track ->
                val pts = if (track.points.isNotEmpty()) track.points else track.rawPoints
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTrack(track) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0284C7).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Timeline, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(20.dp))
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val distText = if (track.totalDistanceMeters >= 1000) {
                                String.format(Locale.FRANCE, "%.2f km", track.totalDistanceMeters / 1000.0)
                            } else {
                                "${track.totalDistanceMeters.toInt()} m"
                            }
                            Text(
                                text = "$distText • ${pts.size} sommets",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(track.startTime)),
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }

                        IconButton(
                            onClick = { onRequestDelete(track.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }

                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LinksListContent(
    links: List<FtthLinkEntity>,
    onSelectLink: (FtthLinkEntity) -> Unit,
    onRequestDelete: (String) -> Unit
) {
    if (links.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(Icons.Default.Cable, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Aucune liaison fibre enregistrée.\nUtilisez le mode 'Câbler' pour relier deux nœuds.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(links, key = { it.id }) { link ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectLink(link) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Cable, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = link.id,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0x3310B981)
                                ) {
                                    Text(
                                        text = "${link.capacityFO} FO",
                                        color = Color(0xFF059669),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${link.fromNodeId} ➔ ${link.toNodeId}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${link.installationType} • ${link.cableType} • ${link.lengthMeters.toInt()}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { onRequestDelete(link.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }

                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

private fun Double.format(decimals: Int): String = "%.${decimals}f".format(Locale.US, this)
