package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.ui.text.style.TextOverflow
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
    onExportGeoJson: () -> Unit = {},
    currentStoragePath: String = "",
    onSetStorageLocation: (String) -> Unit = {},
    isDarkTheme: Boolean = false,
    onDismiss: () -> Unit
) {
    val dialogBg = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFFAFAF8)
    val borderColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFC5D0DC)
    val cardBg = if (isDarkTheme) Color(0xFF334155) else Color(0xFFF0F4F9)
    val textColor = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF062A3D)
    val secondaryTextColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF5B6B7B)

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Projets, 1: Nœuds, 2: Infra, 3: Câbles
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<FtthNodeType?>(null) }

    // Dialogues de gestion projet
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var newProjectNameInput by remember { mutableStateOf("") }
    var copyCurrentDataCheck by remember { mutableStateOf(false) }

    var projectToRename by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }

    var projectToDelete by remember { mutableStateOf<String?>(null) }
    var itemToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Dialogue Définir Emplacement de Stockage
    var showStorageLocationDialog by remember { mutableStateOf(false) }
    var storagePathInput by remember { mutableStateOf(currentStoragePath) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = dialogBg,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 16.dp)
                .border(3.dp, borderColor, RoundedCornerShape(28.dp))
                .testTag("project_folder_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // En-tête Dossier & Projet Actif
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 2.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF062A3D)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Color(0xFF4FC3F7),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentProject,
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF062A3D)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFE8F5EC)
                            ) {
                                Text(
                                    text = "Actif",
                                    color = Color(0xFF2EAD4B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Download/Releve-Terrain/$currentProject",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            color = Color(0xFF5B6B7B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_project_folder_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = Color(0xFF062A3D),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats Grid (3 boxes: Nœuds, Infra, Câbles)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Nœuds
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 1 },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF0F4F9)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp, 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = Color(0xFF0B7BC1),
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "${nodes.size}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 16.sp,
                                    color = Color(0xFF062A3D)
                                )
                                Text(
                                    text = "Nœuds",
                                    fontSize = 11.sp,
                                    color = Color(0xFF5B6B7B)
                                )
                            }
                        }
                    }

                    // Infra
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 2 },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF0F4F9)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp, 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = null,
                                tint = Color(0xFF0B7BC1),
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "${tracks.size}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 16.sp,
                                    color = Color(0xFF062A3D)
                                )
                                Text(
                                    text = "Infra",
                                    fontSize = 11.sp,
                                    color = Color(0xFF5B6B7B)
                                )
                            }
                        }
                    }

                    // Câbles
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 3 },
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF0F4F9)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp, 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = Color(0xFF0B7BC1),
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "${links.size}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 16.sp,
                                    color = Color(0xFF062A3D)
                                )
                                Text(
                                    text = "Câbles",
                                    fontSize = 11.sp,
                                    color = Color(0xFF5B6B7B)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Segmented Tabs Bar matching HTML
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF0F4F9), RoundedCornerShape(30.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Tab 0: Projets
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .clip(RoundedCornerShape(30.dp))
                            .background(if (selectedTab == 0) Color(0xFF062A3D) else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Projets ${allProjects.size}",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedTab == 0) Color.White else Color(0xFF5B6B7B)
                        )
                    }
                    // Tab 1: Nœuds
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(30.dp))
                            .background(if (selectedTab == 1) Color(0xFF062A3D) else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nœuds",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedTab == 1) Color.White else Color(0xFF5B6B7B)
                        )
                    }
                    // Tab 2: Infra
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(30.dp))
                            .background(if (selectedTab == 2) Color(0xFF062A3D) else Color.Transparent)
                            .clickable { selectedTab = 2 }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Infra",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 2) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedTab == 2) Color.White else Color(0xFF5B6B7B)
                        )
                    }
                    // Tab 3: Câbles
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(30.dp))
                            .background(if (selectedTab == 3) Color(0xFF062A3D) else Color.Transparent)
                            .clickable { selectedTab = 3 }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Câbles",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 3) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedTab == 3) Color.White else Color(0xFF5B6B7B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Storage card (matching HTML + custom location feature)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFF0F4F9)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFDCEAF7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = Color(0xFF0B7BC1),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Stockage (Download)", fontSize = 11.sp, color = Color(0xFF5B6B7B))
                                Text(
                                    text = currentStoragePath.ifBlank { "Download/Releve-Terrain/" },
                                    fontSize = 13.sp,
                                    color = Color(0xFF062A3D),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    storagePathInput = currentStoragePath
                                    showStorageLocationDialog = true
                                },
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFC5D0DC))
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "Changer l'emplacement d'enregistrement", fontSize = 12.sp, color = Color(0xFF0B5A8A), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mes projets header & button (visible when tab == 0)
                if (selectedTab == 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mes projets",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF062A3D)
                        )
                        Surface(
                            modifier = Modifier.clickable {
                                val nextNum = (allProjects.size + 1).toString().padStart(2, '0')
                                newProjectNameInput = "projet$nextNum"
                                copyCurrentDataCheck = false
                                showCreateProjectDialog = true
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF0B7BC1)
                        ) {
                            Text(
                                text = "+ Nouveau",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Contenu selon l'onglet avec hauteur contrôlée
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTab) {
                        0 -> {
                            ProjectsListContent(
                                currentProject = currentProject,
                                projects = allProjects,
                                isDarkTheme = isDarkTheme,
                                onSelectProject = onSwitchProject,
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
                            TracksListContent(
                                tracks = tracks,
                                onSelectTrack = onSelectTrack,
                                onRequestDelete = { itemToDelete = Pair("track", it) }
                            )
                        }
                        3 -> {
                            LinksListContent(
                                links = links,
                                onSelectLink = onSelectLink,
                                onRequestDelete = { itemToDelete = Pair("link", it) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Boutons d'export rapide et fermeture (matching HTML)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Export ZIP
                    Surface(
                        modifier = Modifier
                            .weight(2.1f)
                            .height(50.dp)
                            .clickable(onClick = onExportZip),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF062A3D)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color(0xFF4FC3F7),
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Export ZIP",
                                fontSize = 15.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // KMZ
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clickable(onClick = onExportKmz),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF0B7BC1))
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "KMZ",
                                fontSize = 14.sp,
                                color = Color(0xFF0B7BC1),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // GeoJSON
                    Surface(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(50.dp)
                            .clickable(onClick = onExportGeoJson),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF0B7BC1))
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "GeoJSON",
                                fontSize = 14.sp,
                                color = Color(0xFF0B7BC1),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Définir Emplacement d'enregistrement
    if (showStorageLocationDialog) {
        AlertDialog(
            onDismissRequest = { showStorageLocationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = TelecomNavy)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Définir l'emplacement", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Entrez le chemin du dossier cible sur l'appareil pour l'enregistrement des projets :",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = storagePathInput,
                        onValueChange = { storagePathInput = it },
                        label = { Text("Chemin du dossier (ex: /storage/emulated/0/Download/Releve-Terrain)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = storagePathInput.trim()
                        if (trimmed.isNotBlank()) {
                            onSetStorageLocation(trimmed)
                            showStorageLocationDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelecomNavy)
                ) {
                    Text("Enregistrer", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStorageLocationDialog = false }) {
                    Text("Annuler")
                }
            }
        )
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
                        text = "Le projet sera créé en tant que sous-dossier contenant ses propres .noeuds, .infra_lineaire et .cables.",
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
 * Onglet Liste des Projets (matching HTML design)
 */
@Composable
private fun ProjectsListContent(
    currentProject: String,
    projects: List<ProjectInfo>,
    isDarkTheme: Boolean = false,
    onSelectProject: (String) -> Unit,
    onRenameProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        items(projects, key = { it.name }) { proj ->
            val isCurrent = proj.name == currentProject
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (!isCurrent) onSelectProject(proj.name) },
                shape = RoundedCornerShape(18.dp),
                color = if (isCurrent) (if (isDarkTheme) Color(0xFF1B4D3E) else Color(0xFFE8F5EC)) else (if (isDarkTheme) Color(0xFF1E293B) else Color.White),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isCurrent) (if (isDarkTheme) Color(0xFF2EAD4B) else Color(0xFFB7E0C2)) else (if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                )
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2EAD4B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .border(2.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFC5D0DC), CircleShape)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = proj.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF062A3D)
                            )
                            if (isCurrent) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(0xFF2EAD4B)
                                ) {
                                    Text(
                                        text = "EN COURS",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${proj.nodesCount} nœuds · ${proj.tracksCount} infra · ${proj.linksCount} câbles",
                            fontSize = 12.sp,
                            color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF5B6B7B)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { onRenameProject(proj.name) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DriveFileRenameOutline,
                                contentDescription = "Renommer",
                                tint = Color(0xFF062A3D),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(
                            onClick = { onDeleteProject(proj.name) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Supprimer",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(19.dp)
                            )
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

    // Suggestions basées sur la recherche (nom, ID ou adresse, max 5)
    val suggestions = if (searchQuery.isNotBlank()) {
        nodes.filter { node ->
            node.name.contains(searchQuery, ignoreCase = true) || 
            node.id.contains(searchQuery, ignoreCase = true) ||
            node.address.contains(searchQuery, ignoreCase = true)
        }.take(5)
    } else {
        emptyList()
    }

    Column {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Rechercher (nom, ID, adresse)...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        // Affichage des suggestions amélioré
        if (suggestions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Suggestions :", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                suggestions.forEach { node ->
                    FilterChip(
                        selected = false,
                        onClick = { onSelectNode(node) },
                        label = { Text("${node.id} - ${node.name.ifBlank { node.type.label }}", fontSize = 11.sp) }
                    )
                }
            }
        }

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
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tracks, key = { it.id }) { track ->
                val pts = track.points.ifEmpty { track.rawPoints }
                val distMeters = track.totalDistanceMeters
                val distStr = if (distMeters < 1000.0) {
                    "${distMeters.toInt()} m"
                } else {
                    String.format(Locale.US, "%.2f km", distMeters / 1000.0)
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().clickable { 
                        try {
                            onSelectTrack(track)
                        } catch (e: Exception) {
                            android.util.Log.e("TracksListContent", "Error selecting track", e)
                        }
                    }
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
