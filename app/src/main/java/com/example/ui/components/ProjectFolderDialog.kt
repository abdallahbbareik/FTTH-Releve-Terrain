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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.data.storage.PublicStorageHelper
import java.io.File
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Layers
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
import com.example.data.local.isNativeKmlFeature
import com.example.data.storage.ProjectInfo
import com.example.data.storage.StoredTrack
import com.example.data.storage.isNativeKmlFeature
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
    onImportFile: (Uri) -> Unit = {},
    kmlDocumentTitle: String = "",
    kmlFiles: List<com.example.data.import.RealImportService.KmlFileInfo> = emptyList(),
    kmlFolders: List<com.example.data.import.RealImportService.KmlFolderInfo> = emptyList(),
    onDeleteKmlFile: () -> Unit = {},
    onOpenKmlGoogleEarth: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val dialogBg = if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFFAFAF8)
    val borderColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFC5D0DC)
    val cardBg = if (isDarkTheme) Color(0xFF334155) else Color(0xFFF0F4F9)
    val textColor = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF062A3D)
    val secondaryTextColor = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF5B6B7B)
    val dividerColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0)

    val ftthNodes = remember(nodes) { nodes.filter { !it.isNativeKmlFeature() } }
    val ftthTracks = remember(tracks) { tracks.filter { !it.isNativeKmlFeature() } }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Projets, 1: Nœuds, 2: Infra, 3: Câbles
    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<FtthNodeType?>(null) }

    // Dialogues de gestion projet
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var newProjectNameInput by remember { mutableStateOf("") }
    var copyCurrentDataCheck by remember { mutableStateOf(false) }
    var showDeleteKmlFileDialog by remember { mutableStateOf(false) }

    var projectToRename by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }

    var projectToDelete by remember { mutableStateOf<String?>(null) }
    var itemToDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Dialogue Définir Emplacement de Stockage
    val context = LocalContext.current
    var showStorageLocationDialog by remember { mutableStateOf(false) }
    var storagePathInput by remember { mutableStateOf(currentStoragePath) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}
            val chosen = PublicStorageHelper.getPathFromTreeUri(context, uri)
            if (chosen.isNotBlank()) {
                onSetStorageLocation(chosen)
                storagePathInput = chosen
                showStorageLocationDialog = false
            }
        }
    }

    val projectImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportFile(uri)
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = dialogBg,
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 4.dp)
                .border(2.dp, borderColor, RoundedCornerShape(22.dp))
                .testTag("project_folder_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                // En-tête Compact
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 4.dp, start = 2.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isDarkTheme) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF062A3D)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = if (isDarkTheme) Color(0xFF38BDF8) else Color(0xFF4FC3F7),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentProject,
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color.White else Color(0xFF062A3D)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isDarkTheme) Color(0xFF14532D) else Color(0xFFE8F5EC)
                            ) {
                                Text(
                                    text = "Actif",
                                    color = if (isDarkTheme) Color(0xFF4ADE80) else Color(0xFF2EAD4B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Download/Releve-Terrain/$currentProject",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF5B6B7B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Bouton X de fermeture
                    Surface(
                        shape = CircleShape,
                        color = if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0),
                        modifier = Modifier.size(32.dp)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_project_folder_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer",
                                tint = if (isDarkTheme) Color.White else Color(0xFF0F172A),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Stats Grid Compact (3 boxes: Nœuds, Infra, Câbles)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Nœuds
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 1 },
                        shape = RoundedCornerShape(12.dp),
                        color = cardBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = if (isDarkTheme) Color(0xFF38BDF8) else Color(0xFF0B7BC1),
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = "${ftthNodes.size}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 14.sp,
                                    color = textColor
                                )
                                Text(
                                    text = "Nœuds",
                                    fontSize = 10.sp,
                                    color = secondaryTextColor
                                )
                            }
                        }
                    }

                    // Infra
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 2 },
                        shape = RoundedCornerShape(12.dp),
                        color = cardBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = null,
                                tint = if (isDarkTheme) Color(0xFF38BDF8) else Color(0xFF0B7BC1),
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = "${ftthTracks.size}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 14.sp,
                                    color = textColor
                                )
                                Text(
                                    text = "Infra",
                                    fontSize = 10.sp,
                                    color = secondaryTextColor
                                )
                            }
                        }
                    }

                    // Câbles
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 3 },
                        shape = RoundedCornerShape(12.dp),
                        color = cardBg
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = if (isDarkTheme) Color(0xFF38BDF8) else Color(0xFF0B7BC1),
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = "${links.size}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 14.sp,
                                    color = textColor
                                )
                                Text(
                                    text = "Câbles",
                                    fontSize = 10.sp,
                                    color = secondaryTextColor
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Segmented Tabs Bar Compact
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(cardBg, RoundedCornerShape(24.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val activeTabBg = if (isDarkTheme) Color(0xFF0284C7) else Color(0xFF062A3D)
                    val inactiveTabTextColor = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF5B6B7B)

                    // Tab 0: Projets
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (selectedTab == 0) activeTabBg else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Projets ${allProjects.size}",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) Color.White else inactiveTabTextColor
                        )
                    }
                    // Tab 1: Nœuds
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (selectedTab == 1) activeTabBg else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Nœuds",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) Color.White else inactiveTabTextColor
                        )
                    }
                    // Tab 2: Infra
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (selectedTab == 2) activeTabBg else Color.Transparent)
                            .clickable { selectedTab = 2 }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Infra",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 2) Color.White else inactiveTabTextColor
                        )
                    }
                    // Tab 3: Câbles
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (selectedTab == 3) activeTabBg else Color.Transparent)
                            .clickable { selectedTab = 3 }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Câbles",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 3) Color.White else inactiveTabTextColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Contenu Selon l'Onglet
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTab) {
                        0 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                // Carte Stockage Compact
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = cardBg
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isDarkTheme) Color(0xFF1E293B) else Color(0xFFDCEAF7)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = if (isDarkTheme) Color(0xFF38BDF8) else Color(0xFF0B7BC1),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = "Stockage (Download)", fontSize = 10.sp, color = secondaryTextColor)
                                                Text(
                                                    text = currentStoragePath.ifBlank { "Download/Releve-Terrain/" },
                                                    fontSize = 11.sp,
                                                    color = textColor,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1.35f)
                                                    .clickable {
                                                        try {
                                                            folderPickerLauncher.launch(null)
                                                        } catch (e: Exception) {
                                                            storagePathInput = currentStoragePath
                                                            showStorageLocationDialog = true
                                                        }
                                                    },
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color(0xFF0284C7)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.FolderOpen,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Parcourir dossier",
                                                        fontSize = 11.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }

                                            Surface(
                                                modifier = Modifier
                                                    .weight(1.05f)
                                                    .clickable {
                                                        storagePathInput = currentStoragePath
                                                        showStorageLocationDialog = true
                                                    },
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isDarkTheme) Color(0xFF1E293B) else Color.White,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isDarkTheme) Color(0xFF475569) else Color(0xFFC5D0DC))
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "Raccourcis...",
                                                        fontSize = 11.sp,
                                                        color = if (isDarkTheme) Color(0xFF38BDF8) else Color(0xFF0B5A8A),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Carte Import KMZ / KML / GeoJSON (Toujours visible, arborescence accessible via la carte)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isDarkTheme) Color(0xFF1E1B4B) else Color(0xFFF5F3FF),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth().testTag("folder_import_card")
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(
                                                    imageVector = Icons.Default.FileUpload,
                                                    contentDescription = null,
                                                    tint = Color(0xFF7C3AED),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Import KMZ / KML / GeoJSON",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = if (isDarkTheme) Color.White else Color(0xFF1E1B4B)
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color(0xFF7C3AED).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "Mode Natif",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 9.sp,
                                                    color = Color(0xFF7C3AED),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val subtitle = if (kmlFiles.isNotEmpty()) {
                                            "${kmlFiles.size} fichier(s) importé(s) dans ce projet • L'arborescence se consulte sur la carte"
                                        } else {
                                            "Affichage fidèle des données brutes avec arborescence des dossiers."
                                        }
                                        Text(
                                            text = subtitle,
                                            fontSize = 10.sp,
                                            color = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF475569)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    try {
                                                        projectImportLauncher.launch(arrayOf("*/*"))
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp, horizontal = 10.dp),
                                                modifier = Modifier.weight(1f).testTag("folder_import_file_button")
                                            ) {
                                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Importer un fichier KMZ ou GeoJSON", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }

                                            if (kmlDocumentTitle.isNotBlank() || kmlFolders.isNotEmpty() || kmlFiles.isNotEmpty()) {
                                                Button(
                                                    onClick = { showDeleteKmlFileDialog = true },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp, horizontal = 8.dp),
                                                    modifier = Modifier.testTag("delete_imported_file_button")
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Effacer les données importées", modifier = Modifier.size(16.dp), tint = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Header Mes Projets
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Mes projets (${allProjects.size})",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                    Surface(
                                        modifier = Modifier.clickable {
                                            val nextNum = (allProjects.size + 1).toString().padStart(2, '0')
                                            newProjectNameInput = "projet$nextNum"
                                            copyCurrentDataCheck = false
                                            showCreateProjectDialog = true
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFF0B7BC1)
                                    ) {
                                        Text(
                                            text = "+ Nouveau",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Liste des Projets (Column)
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
                        }
                        1 -> {
                            NodesListContent(
                                nodes = ftthNodes,
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
                                tracks = ftthTracks,
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

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = dividerColor, thickness = 1.dp)
                Spacer(modifier = Modifier.height(6.dp))

                // Boutons d'export rapide
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Export ZIP
                    Surface(
                        modifier = Modifier
                            .weight(2.1f)
                            .height(42.dp)
                            .clickable(onClick = onExportZip),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDarkTheme) Color(0xFF0284C7) else Color(0xFF062A3D)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color(0xFF4FC3F7),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Export ZIP",
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // KMZ
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clickable(onClick = onExportKmz),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, if (isDarkTheme) Color(0xFF38BDF8) else Color(0xFF0B7BC1))
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "KMZ",
                                fontSize = 13.sp,
                                color = if (isDarkTheme) Color(0xFF38BDF8) else Color(0xFF0B7BC1),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // GeoJSON
                    Surface(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(42.dp)
                            .clickable(onClick = onExportGeoJson),
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, if (isDarkTheme) Color(0xFF38BDF8) else Color(0xFF0B7BC1))
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "GeoJSON",
                                fontSize = 13.sp,
                                color = if (isDarkTheme) Color(0xFF38BDF8) else Color(0xFF0B7BC1),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Définir Emplacement d'enregistrement avec navigateur de fichiers
    if (showStorageLocationDialog) {
        AlertDialog(
            onDismissRequest = { showStorageLocationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF0284C7))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Emplacement d'enregistrement", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Sélectionnez ou parcourez le dossier cible où stocker vos projets FTTH (.noeuds, .infra_lineaire, .cables) :",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Bouton Principal : Parcourir (Explorateur Android SAF)
                    Button(
                        onClick = {
                            try {
                                folderPickerLauncher.launch(null)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Parcourir les dossiers de l'appareil", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    Text("Raccourcis d'emplacements rapides :", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

                    // Preset 1: Download/Releve-Terrain
                    Surface(
                        onClick = {
                            val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Releve-Terrain")
                            storagePathInput = downloadDir.absolutePath
                            onSetStorageLocation(storagePathInput)
                            showStorageLocationDialog = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Dossier Téléchargements (Par défaut)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text("Download/Releve-Terrain", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Preset 2: Documents/Releve-Terrain
                    Surface(
                        onClick = {
                            val docsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Releve-Terrain")
                            storagePathInput = docsDir.absolutePath
                            onSetStorageLocation(storagePathInput)
                            showStorageLocationDialog = false
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF0B7BC1), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Dossier Documents", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text("Documents/Releve-Terrain", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Champ de saisie ou confirmation du chemin sélectionné
                    OutlinedTextField(
                        value = storagePathInput,
                        onValueChange = { storagePathInput = it },
                        label = { Text("Chemin actuellement sélectionné") },
                        singleLine = true,
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
                    Text("Valider cet emplacement", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStorageLocationDialog = false }) {
                    Text("Fermer")
                }
            }
        )
    }

    // Modal Création d'un Nouveau Projet
    if (showCreateProjectDialog) {
        val trimmedNewProject = newProjectNameInput.trim()
        val isDuplicate = remember(trimmedNewProject, allProjects) {
            trimmedNewProject.isNotBlank() && allProjects.any { it.name.trim().equals(trimmedNewProject, ignoreCase = true) }
        }
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
                        isError = isDuplicate,
                        supportingText = {
                            if (isDuplicate) {
                                Text(
                                    text = "Un projet portant ce nom existe déjà.",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp
                                )
                            }
                        },
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
                        if (trimmedNewProject.isNotBlank() && !isDuplicate) {
                            onCreateProject(trimmedNewProject, copyCurrentDataCheck)
                            showCreateProjectDialog = false
                        }
                    },
                    enabled = trimmedNewProject.isNotBlank() && !isDuplicate,
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

    // Modal Confirmation Suppression du Fichier KML et de tous ses dossiers
    if (showDeleteKmlFileDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteKmlFileDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Supprimer le fichier importé ?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Voulez-vous supprimer le fichier « ${kmlDocumentTitle.ifBlank { "Fichier importé" }} » ?\n\nTous les dossiers associés (${kmlFolders.size} dossiers) ainsi que tous leurs tracés et points seront définitivement supprimés du projet."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteKmlFileDialog = false
                        onDeleteKmlFile()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer tout", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteKmlFileDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Modal Renommer un Projet
    if (projectToRename != null) {
        val trimmedRename = renameInput.trim()
        val isDuplicateRename = remember(trimmedRename, allProjects, projectToRename) {
            trimmedRename.isNotBlank() && allProjects.any {
                it.name.trim().equals(trimmedRename, ignoreCase = true) &&
                !it.name.trim().equals(projectToRename?.trim(), ignoreCase = true)
            }
        }
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
                        isError = isDuplicateRename,
                        supportingText = {
                            if (isDuplicateRename) {
                                Text(
                                    text = "Un autre projet porte déjà ce nom.",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (trimmedRename.isNotBlank() && !isDuplicateRename && projectToRename != null) {
                            onRenameProject(projectToRename!!, trimmedRename)
                            projectToRename = null
                        }
                    },
                    enabled = trimmedRename.isNotBlank() && !isDuplicateRename
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
    Column(
        modifier = Modifier.padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        projects.forEach { proj ->
            val isCurrent = proj.name == currentProject
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (!isCurrent) onSelectProject(proj.name) },
                shape = RoundedCornerShape(14.dp),
                color = if (isCurrent) (if (isDarkTheme) Color(0xFF1B4D3E) else Color(0xFFE8F5EC)) else (if (isDarkTheme) Color(0xFF1E293B) else Color.White),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isCurrent) (if (isDarkTheme) Color(0xFF2EAD4B) else Color(0xFFB7E0C2)) else (if (isDarkTheme) Color(0xFF334155) else Color(0xFFE2E8F0))
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2EAD4B)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, if (isDarkTheme) Color(0xFF334155) else Color(0xFFC5D0DC), CircleShape)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = proj.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isDarkTheme) Color(0xFFF8FAFC) else Color(0xFF062A3D)
                            )
                            if (isCurrent) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF2EAD4B)
                                ) {
                                    Text(
                                        text = "EN COURS",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${proj.nodesCount} nœuds · ${proj.tracksCount} infra · ${proj.linksCount} câbles",
                            fontSize = 11.sp,
                            color = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF5B6B7B)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = { onRenameProject(proj.name) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DriveFileRenameOutline,
                                contentDescription = "Renommer",
                                tint = if (isDarkTheme) Color(0xFFCBD5E1) else Color(0xFF062A3D),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        IconButton(
                            onClick = { onDeleteProject(proj.name) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Supprimer",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(17.dp)
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
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Effacer", modifier = Modifier.size(16.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
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
