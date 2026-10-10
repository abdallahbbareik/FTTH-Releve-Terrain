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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.storage.ProjectInfo
import com.example.data.storage.StoredTrack
import com.example.ui.theme.TelecomCyan
import com.example.ui.theme.TelecomNavy
import java.util.Locale

@Composable
fun ProjectFolderDialog(
    currentProject: String,
    allProjects: List<ProjectInfo>,
    nodes: List<FtthNodeEntity>,
    tracks: List<StoredTrack>,
    links: List<FtthLinkEntity>,
    onSwitchProject: (String) -> Unit,
    onCreateProject: (String, Boolean) -> Unit,
    onDeleteProject: (String) -> Unit,
    onRenameProject: (String, String) -> Unit,
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
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Projets, 1: Nœuds, 2: Infra_lineaire, 3: Câbles
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<FtthNodeType?>(null) }

    // Dialogues de gestion projet
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var newProjectNameInput by remember { mutableStateOf("") }
    var copyCurrentDataCheck by remember { mutableStateOf(false) }

    var projectToRename by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }

    var projectToDelete by remember { mutableStateOf<String?>(null) }
    var itemToDelete by remember { mutableStateOf<Pair<String, String>?>(null) } // type to id

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
                // En-tête Dossier & Projet Actif
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(TelecomNavy),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = TelecomCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Projet : $currentProject",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFDCFCE7)
                            ) {
                                Text(
                                    text = "Actif",
                                    color = Color(0xFF166534),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Documents/Releve-Terrain/$currentProject (${nodes.size} nœuds • ${tracks.size} infra_lineaire • ${links.size} câbles)",
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

                Spacer(modifier = Modifier.height(12.dp))

                // Bouton d'action rapide Créer Nouveau Projet
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Arborescence & Fichiers",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(
                        onClick = {
                            val nextNum = (allProjects.size + 1).toString().padStart(2, '0')
                            newProjectNameInput = "projet$nextNum"
                            copyCurrentDataCheck = false
                            showCreateProjectDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TelecomNavy),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(34.dp).testTag("create_new_project_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = TelecomCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Nouveau Projet", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Onglets de navigation : Projets / Nœuds / Infra_lineaire / Câbles
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Projets (${allProjects.size})", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Nœuds (${nodes.size})", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timeline, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Infra_lineaire (${tracks.size})", fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cable, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Câbles (${links.size})", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Contenu selon l'onglet
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> {
                            // ONGLET 0 : LISTE DES PROJETS
                            ProjectsListContent(
                                currentProject = currentProject,
                                projects = allProjects,
                                onSelectProject = { projName ->
                                    onSwitchProject(projName)
                                },
                                onRenameProject = { projName ->
                                    projectToRename = projName
                                    renameInput = projName
                                },
                                onDeleteProject = { projName ->
                                    projectToDelete = projName
                                }
                            )
                        }
                        1 -> {
                            // ONGLET 1 : NOEUDS (.noeuds / noeuds.json)
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
                        2 -> {
                            // ONGLET 2 : INFRA_LINEAIRE (.infra_lineaire / infra_lineaire.json)
                            TracksListContent(
                                tracks = tracks,
                                onSelectTrack = onSelectTrack,
                                onRequestDelete = { itemToDelete = Pair("track", it) }
                            )
                        }
                        3 -> {
                            // ONGLET 3 : CÂBLES (.cables / cables.json)
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
                            Text("Export ZIP ($currentProject)", fontSize = 12.sp, color = Color.White)
                        }
                        OutlinedButton(
                            onClick = onExportKmz,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("folder_export_kmz_button")
                        ) {
                            Text("KMZ", fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Fermer", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Modal Création d'un Nouveau Projet
    if (showCreateProjectDialog) {
        AlertDialog(
            onDismissRequest = { showCreateProjectDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = TelecomNavy)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Créer un nouveau projet", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Le projet sera créé en tant que sous-dossier dans Documents/Releve-Terrain/ contenant ses propres .noeuds, .infra_lineaire et .cables.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newProjectNameInput,
                        onValueChange = { newProjectNameInput = it },
                        label = { Text("Nom du projet (ex: projet02, Zone_Sud...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { copyCurrentDataCheck = !copyCurrentDataCheck }
                    ) {
                        Checkbox(
                            checked = copyCurrentDataCheck,
                            onCheckedChange = { copyCurrentDataCheck = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dupliquer les données de '$currentProject'", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newProjectNameInput.trim()
                        if (trimmed.isNotBlank()) {
                            onCreateProject(trimmed, copyCurrentDataCheck)
                            showCreateProjectDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomNavy)
                ) {
                    Text("Créer et Ouvrir", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateProjectDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Modal Renommer un Projet
    if (projectToRename != null) {
        AlertDialog(
            onDismissRequest = { projectToRename = null },
            title = { Text("Renommer le projet") },
            text = {
                Column {
                    Text("Entrez le nouveau nom pour le sous-dossier du projet :", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renameInput.trim()
                        if (trimmed.isNotBlank() && projectToRename != null) {
                            onRenameProject(projectToRename!!, trimmed)
                            projectToRename = null
                        }
                    }
                ) {
                    Text("Renommer")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToRename = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Modal Confirmation Suppression Projet
    if (projectToDelete != null) {
        val targetProj = projectToDelete!!
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Supprimer le dossier du projet ?") },
            text = {
                Text("Voulez-vous vraiment supprimer le projet '$targetProj' et tous ses fichiers (.noeuds, .infra_lineaire, .cables) ? Cette action supprimera définitivement le dossier.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProject(targetProj)
                        projectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Supprimer définitivement", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Modal Confirmation Suppression Élément
    if (itemToDelete != null) {
        val (type, id) = itemToDelete!!
        val typeLabel = when (type) {
            "node" -> "le nœud $id"
            "track" -> "l'infra linéaire"
            "link" -> "le câble"
            else -> "cet élément"
        }
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Supprimer $typeLabel ?") },
            text = { Text("L'élément sera définitivement retiré du projet '$currentProject' et du stockage.") },
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Supprimer", color = Color.White)
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

/**
 * Onglet Liste des Projets (Documents/Releve-Terrain/<projet>)
 */
@Composable
private fun ProjectsListContent(
    currentProject: String,
    projects: List<ProjectInfo>,
    onSelectProject: (String) -> Unit,
    onRenameProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = TelecomNavy, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dossier racine : Documents/Releve-Terrain",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Chaque projet dispose de son sous-dossier et de ses fichiers (.noeuds, .infra_lineaire, .cables).",
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }
        }

        items(projects, key = { it.name }) { proj ->
            val isCurrent = proj.name == currentProject
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent) Color(0xFFF0FDF4) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (!isCurrent) onSelectProject(proj.name) }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCurrent) Icons.Default.CheckCircle else Icons.Default.Folder,
                            contentDescription = null,
                            tint = if (isCurrent) Color(0xFF16A34A) else TelecomNavy,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = proj.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isCurrent) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF16A34A)
                                    ) {
                                        Text(
                                            text = "EN COURS",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "📁 ${proj.name}  •  ${proj.nodesCount} nœuds • ${proj.tracksCount} infra_lineaire • ${proj.linksCount} câbles",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Boutons actions projet
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { onRenameProject(proj.name) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Renommer", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = { onDeleteProject(proj.name) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (!isCurrent) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { onSelectProject(proj.name) },
                            colors = ButtonDefaults.buttonColors(containerColor = TelecomNavy),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().height(32.dp)
                        ) {
                            Text("Ouvrir ce projet", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Onglet Liste des Nœuds (.noeuds / noeuds.json)
 */
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
    val filtered = nodes.filter { node ->
        val matchesType = selectedTypeFilter == null || node.type == selectedTypeFilter
        val matchesQuery = searchQuery.isBlank() ||
                node.name.contains(searchQuery, ignoreCase = true) ||
                node.id.contains(searchQuery, ignoreCase = true) ||
                node.address.contains(searchQuery, ignoreCase = true)
        matchesType && matchesQuery
    }

    Column {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Rechercher un nœud dans .noeuds...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedTypeFilter == null,
                onClick = { onTypeFilterChange(null) },
                label = { Text("Tous (${nodes.size})", fontSize = 11.sp) }
            )
            FtthNodeType.values().forEach { type ->
                val count = nodes.count { it.type == type }
                FilterChip(
                    selected = selectedTypeFilter == type,
                    onClick = { onTypeFilterChange(if (selectedTypeFilter == type) null else type) },
                    label = { Text("${type.label} ($count)", fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (nodes.isEmpty()) "Aucun nœud dans ce projet" else "Aucun résultat trouvé",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(filtered, key = { it.id }) { node ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onSelectNode(node) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = FtthNodeVisuals.getNodeColor(node.type),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(FtthNodeVisuals.getNodeDrawableRes(node.type)),
                                        contentDescription = node.type.label,
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (node.operator.isNotBlank()) "${node.id} • ${node.operator}" else "${node.id} • ${node.type.label}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${node.type.label} • ${node.status.label} • ${node.etat.label}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (node.address.isNotBlank()) {
                                    Text(text = node.address, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            IconButton(
                                onClick = { onRequestDelete(node.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Onglet Liste des Infra Linéaires (.infra_lineaire / infra_lineaire.json)
 */
@Composable
private fun TracksListContent(
    tracks: List<StoredTrack>,
    onSelectTrack: (StoredTrack) -> Unit,
    onRequestDelete: (String) -> Unit
) {
    if (tracks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Aucune infra linéaire enregistrée dans ce projet.\nUtilisez 'Démarrer Infra_lineaire GPS' ou 'Infra_lineaire Manuel' sur la carte.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tracks, key = { it.id }) { track ->
                val pts = if (track.points.isNotEmpty()) track.points else track.rawPoints
                val distMeters = track.totalDistanceMeters
                val distStr = if (distMeters < 1000.0) "${distMeters.toInt()} m" else String.format(Locale.FRANCE, "%.2f km", distMeters / 1000.0)

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onSelectTrack(track) }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (track.etat == "Conforme") Color(0xFF16A34A) else Color(0xFFDC2626),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Timeline, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = track.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = track.type,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "$distStr • ${pts.size} sommets • ${track.photos.size} photos • ${track.etat}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (track.type == "GC") {
                                Text(
                                    text = "GC: ${track.conduitAudit} • ${track.conduitType} • ${track.conduitCount} cond. (${track.conduitDiameters.joinToString()})",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        IconButton(
                            onClick = { onRequestDelete(track.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Onglet Liste des Câbles (.cables / cables.json)
 */
@Composable
private fun LinksListContent(
    links: List<FtthLinkEntity>,
    onSelectLink: (FtthLinkEntity) -> Unit,
    onRequestDelete: (String) -> Unit
) {
    if (links.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Aucun câble dans ce projet.\nUtilisez 'Relier par câble' depuis la fiche d'un nœud.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(links, key = { it.id }) { link ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onSelectLink(link) }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = TelecomNavy,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Cable, contentDescription = null, tint = TelecomCyan, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Câble ${link.cableType} (${link.id})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${link.installationType} • ${link.capacityFO} FO • ${link.lengthMeters.toInt()} m",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "De ${link.fromNodeId} ➔ ${link.toNodeId}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        IconButton(
                            onClick = { onRequestDelete(link.id) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
