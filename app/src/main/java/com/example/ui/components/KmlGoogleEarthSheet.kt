package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.import.RealImportService.KmlFileInfo
import com.example.data.import.RealImportService.KmlFolderInfo
import com.example.data.local.FtthNodeEntity
import com.example.data.storage.StoredTrack
import java.util.Locale

/**
 * Composant de calques Google Earth reproduisant l'arborescence native
 * hiérarchique à 2 niveaux :
 * Niveau 1 : Nom du fichier KMZ / KML
 *   Niveau 2 : Dossier du fichier
 *     Niveau 3 : Tracés et points du dossier
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KmlGoogleEarthSheet(
    documentTitle: String,
    files: List<KmlFileInfo> = emptyList(),
    folders: List<KmlFolderInfo> = emptyList(),
    allTracks: List<StoredTrack>,
    allNodes: List<FtthNodeEntity>,
    onToggleFileVisibility: (String) -> Unit = {},
    onToggleFolderVisibility: (folderName: String, fileName: String?) -> Unit,
    onSetAllVisibility: (Boolean) -> Unit,
    onZoomToFile: (String) -> Unit = {},
    onZoomToFolder: (folderName: String, fileName: String?) -> Unit,
    onZoomToTrack: (StoredTrack) -> Unit,
    onZoomToNode: (FtthNodeEntity) -> Unit,
    onInspectTrack: ((StoredTrack) -> Unit)? = null,
    onInspectNode: ((FtthNodeEntity) -> Unit)? = null,
    onDeleteFile: (String) -> Unit = {},
    onDeleteFolder: ((folderName: String, fileName: String?) -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val expandedFiles = remember { mutableStateMapOf<String, Boolean>() }
    val expandedFolders = remember { mutableStateMapOf<String, Boolean>() }

    var fileToDelete by remember { mutableStateOf<String?>(null) }
    var folderToDelete by remember { mutableStateOf<Pair<String, String?>?>(null) }

    val displayFiles = remember(files, folders, documentTitle) {
        if (files.isNotEmpty()) {
            files
        } else if (folders.isNotEmpty()) {
            listOf(
                KmlFileInfo(
                    fileName = documentTitle.ifBlank { "Fichier importé" },
                    title = documentTitle.ifBlank { "Fichier importé" },
                    folders = folders,
                    tracksCount = folders.sumOf { it.tracksCount },
                    nodesCount = folders.sumOf { it.nodesCount },
                    totalDistanceMeters = folders.sumOf { it.totalDistanceMeters },
                    isVisible = folders.any { it.isVisible }
                )
            )
        } else {
            emptyList()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("kml_google_earth_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(bottom = 16.dp)
        ) {
            // --- EN-TÊTE GOOGLE EARTH STYLE ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("kml_sheet_close")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Arborescence KML / KMZ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${displayFiles.size} fichier${if (displayFiles.size > 1) "s" else ""}",
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Hiérarchie Google Earth",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // --- BARRE D'ACTIONS GLOBALES ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onSetAllVisibility(true) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("kml_show_all_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tout afficher", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { onSetAllVisibility(false) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("kml_hide_all_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tout masquer", fontSize = 12.sp)
                    }
                }

                val totalFolders = displayFiles.sumOf { it.folders.size }
                Text(
                    text = "$totalFolders dossier${if (totalFolders > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- LISTE DE L'ARBORESCENCE HIÉRARCHIQUE ---
            if (displayFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucun fichier KML / KMZ importé.\nImportez des fichiers géospatiaux pour afficher la hiérarchie.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp)
                ) {
                    items(displayFiles, key = { it.fileName }) { file ->
                        val isFileExpanded = expandedFiles[file.fileName] ?: true
                        val fileColor = MaterialTheme.colorScheme.primary

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .testTag("kml_file_${file.fileName}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (file.isVisible) {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                }
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // --- NIVEAU 1 : NOM DU FICHIER ---
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expandedFiles[file.fileName] = !isFileExpanded
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isFileExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                        contentDescription = if (isFileExpanded) "Replier" else "Déplier",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(fileColor.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = fileColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.fileName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (file.isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        val fileDetails = buildString {
                                            append("${file.folders.size} dossier${if (file.folders.size > 1) "s" else ""}")
                                            if (file.tracksCount > 0) append(" • ${file.tracksCount} tracé${if (file.tracksCount > 1) "s" else ""}")
                                            if (file.nodesCount > 0) append(" • ${file.nodesCount} point${if (file.nodesCount > 1) "s" else ""}")
                                        }
                                        Text(
                                            text = fileDetails,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Centrer sur le fichier
                                    IconButton(
                                        onClick = { onZoomToFile(file.fileName) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MyLocation,
                                            contentDescription = "Centrer sur le fichier",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Masquer/Afficher le fichier entier
                                    IconButton(
                                        onClick = { onToggleFileVisibility(file.fileName) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (file.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = if (file.isVisible) "Masquer" else "Afficher",
                                            tint = if (file.isVisible) fileColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Supprimer le fichier entier
                                    IconButton(
                                        onClick = { fileToDelete = file.fileName },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Supprimer le fichier",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // --- NIVEAU 2 : DOSSIERS SOUS LE FICHIER ---
                                AnimatedVisibility(visible = isFileExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 14.dp, end = 8.dp, bottom = 8.dp)
                                    ) {
                                        file.folders.forEach { folder ->
                                            val folderKey = "${file.fileName}::${folder.name}"
                                            val isFolderExpanded = expandedFolders[folderKey] ?: false

                                            val defaultFolderColor = MaterialTheme.colorScheme.secondary
                                            val folderColor = remember(folder.colorHex, defaultFolderColor) {
                                                val hex = folder.colorHex
                                                if (!hex.isNullOrBlank()) {
                                                    try {
                                                        Color(android.graphics.Color.parseColor(hex))
                                                    } catch (_: Exception) {
                                                        defaultFolderColor
                                                    }
                                                } else {
                                                    defaultFolderColor
                                                }
                                            }

                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 3.dp)
                                                    .testTag("kml_folder_${folder.name}"),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (folder.isVisible) {
                                                        MaterialTheme.colorScheme.surface
                                                    } else {
                                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
                                                    }
                                                ),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    // Ligne du dossier
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                expandedFolders[folderKey] = !isFolderExpanded
                                                            }
                                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isFolderExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(18.dp)
                                                        )

                                                        Spacer(modifier = Modifier.width(4.dp))

                                                        Box(
                                                            modifier = Modifier
                                                                .size(32.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(folderColor.copy(alpha = 0.18f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isFolderExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                                                contentDescription = null,
                                                                tint = folderColor,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }

                                                        Spacer(modifier = Modifier.width(8.dp))

                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = folder.name,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (folder.isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )

                                                            val details = buildString {
                                                                if (folder.tracksCount > 0) {
                                                                    append("${folder.tracksCount} tracé${if (folder.tracksCount > 1) "s" else ""}")
                                                                    if (folder.totalDistanceMeters > 0) {
                                                                        val distStr = if (folder.totalDistanceMeters < 1000.0) {
                                                                            "${folder.totalDistanceMeters.toInt()} m"
                                                                        } else {
                                                                            String.format(Locale.FRANCE, "%.2f km", folder.totalDistanceMeters / 1000.0)
                                                                        }
                                                                        append(" • ").append(distStr)
                                                                    }
                                                                }
                                                                if (folder.nodesCount > 0) {
                                                                    if (isNotEmpty()) append(" • ")
                                                                    append("${folder.nodesCount} point${if (folder.nodesCount > 1) "s" else ""}")
                                                                }
                                                            }

                                                            if (details.isNotBlank()) {
                                                                Text(
                                                                    text = details,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    fontSize = 11.sp,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }

                                                        // Centrer sur le dossier
                                                        IconButton(
                                                            onClick = { onZoomToFolder(folder.name, file.fileName) },
                                                            modifier = Modifier.size(34.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.MyLocation,
                                                                contentDescription = "Centrer",
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }

                                                        // Visibilité du dossier
                                                        IconButton(
                                                            onClick = { onToggleFolderVisibility(folder.name, file.fileName) },
                                                            modifier = Modifier.size(34.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (folder.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                                contentDescription = if (folder.isVisible) "Masquer" else "Afficher",
                                                                tint = if (folder.isVisible) folderColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }

                                                        // Supprimer le dossier
                                                        if (onDeleteFolder != null) {
                                                            IconButton(
                                                                onClick = { folderToDelete = Pair(folder.name, file.fileName) },
                                                                modifier = Modifier.size(34.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Delete,
                                                                    contentDescription = "Supprimer le dossier",
                                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // --- NIVEAU 3 : ÉLÉMENTS DU DOSSIER ---
                                                    AnimatedVisibility(visible = isFolderExpanded) {
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(start = 24.dp, end = 8.dp, bottom = 6.dp)
                                                        ) {
                                                            val folderTracks = allTracks.filter { trk ->
                                                                val matchFolder = trk.folderName.equals(folder.name, ignoreCase = true) ||
                                                                        ((folder.name == "Général" || folder.name == "Sans dossier") && trk.folderName.isBlank())
                                                                val trkFile = trk.sourceFile.ifBlank { documentTitle.ifBlank { "Fichier principal" } }
                                                                val matchFile = trkFile.equals(file.fileName, ignoreCase = true)
                                                                matchFolder && matchFile
                                                            }
                                                            val folderNodes = allNodes.filter { node ->
                                                                val matchFolder = node.folderName.equals(folder.name, ignoreCase = true) ||
                                                                        ((folder.name == "Général" || folder.name == "Sans dossier") && node.folderName.isBlank())
                                                                val nodeFile = node.sourceFile.ifBlank { documentTitle.ifBlank { "Fichier principal" } }
                                                                val matchFile = nodeFile.equals(file.fileName, ignoreCase = true)
                                                                matchFolder && matchFile
                                                            }

                                                            if (folderTracks.isEmpty() && folderNodes.isEmpty()) {
                                                                Text(
                                                                    text = "Dossier vide",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                                )
                                                            } else {
                                                                // Tracés / Lignes
                                                                folderTracks.forEach { track ->
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .clickable {
                                                                                onZoomToTrack(track)
                                                                                onDismiss()
                                                                            }
                                                                            .padding(vertical = 5.dp, horizontal = 4.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Route,
                                                                            contentDescription = null,
                                                                            tint = folderColor,
                                                                            modifier = Modifier.size(15.dp)
                                                                        )
                                                                        Spacer(modifier = Modifier.width(8.dp))
                                                                        Column(modifier = Modifier.weight(1f)) {
                                                                            Text(
                                                                                text = track.name,
                                                                                style = MaterialTheme.typography.bodySmall,
                                                                                fontWeight = FontWeight.Medium,
                                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                                maxLines = 1,
                                                                                overflow = TextOverflow.Ellipsis
                                                                            )
                                                                            val distLabel = if (track.totalDistanceMeters < 1000.0) {
                                                                                "${track.totalDistanceMeters.toInt()} m"
                                                                            } else {
                                                                                String.format(Locale.FRANCE, "%.2f km", track.totalDistanceMeters / 1000.0)
                                                                            }
                                                                            Text(
                                                                                text = "$distLabel • ${track.points.size} sommets",
                                                                                style = MaterialTheme.typography.bodySmall,
                                                                                fontSize = 10.sp,
                                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                            )
                                                                        }
                                                                        Row(
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                        ) {
                                                                            if (onInspectTrack != null) {
                                                                                IconButton(
                                                                                    onClick = {
                                                                                        onInspectTrack(track)
                                                                                        onDismiss()
                                                                                    },
                                                                                    modifier = Modifier.size(30.dp)
                                                                                ) {
                                                                                    Icon(
                                                                                        imageVector = Icons.Default.Info,
                                                                                        contentDescription = "Attributs",
                                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                                        modifier = Modifier.size(16.dp)
                                                                                    )
                                                                                }
                                                                            }
                                                                            Icon(
                                                                                imageVector = Icons.Default.MyLocation,
                                                                                contentDescription = "Zoomer",
                                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                                                modifier = Modifier.size(15.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                }

                                                                // Points / Nœuds
                                                                folderNodes.forEach { node ->
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .clickable {
                                                                                onZoomToNode(node)
                                                                                onDismiss()
                                                                            }
                                                                            .padding(vertical = 5.dp, horizontal = 4.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(11.dp)
                                                                                .clip(CircleShape)
                                                                                .background(folderColor)
                                                                        )
                                                                        Spacer(modifier = Modifier.width(8.dp))
                                                                        Column(modifier = Modifier.weight(1f)) {
                                                                            Text(
                                                                                text = node.name.ifBlank { node.id },
                                                                                style = MaterialTheme.typography.bodySmall,
                                                                                fontWeight = FontWeight.Medium,
                                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                                maxLines = 1,
                                                                                overflow = TextOverflow.Ellipsis
                                                                            )
                                                                            Text(
                                                                                text = "${String.format(Locale.ROOT, "%.5f", node.latitude)}, ${String.format(Locale.ROOT, "%.5f", node.longitude)}",
                                                                                style = MaterialTheme.typography.bodySmall,
                                                                                fontSize = 10.sp,
                                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                            )
                                                                        }
                                                                        Row(
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                                        ) {
                                                                            if (onInspectNode != null) {
                                                                                IconButton(
                                                                                    onClick = {
                                                                                        onInspectNode(node)
                                                                                        onDismiss()
                                                                                    },
                                                                                    modifier = Modifier.size(30.dp)
                                                                                ) {
                                                                                    Icon(
                                                                                        imageVector = Icons.Default.Info,
                                                                                        contentDescription = "Attributs",
                                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                                        modifier = Modifier.size(16.dp)
                                                                                    )
                                                                                }
                                                                            }
                                                                            Icon(
                                                                                imageVector = Icons.Default.MyLocation,
                                                                                contentDescription = "Zoomer",
                                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                                                modifier = Modifier.size(15.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGUE DE CONFIRMATION SUPPRESSION DU FICHIER ---
    if (fileToDelete != null) {
        val fName = fileToDelete ?: ""
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
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
                    text = "Supprimer le fichier ?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Voulez-vous supprimer le fichier « $fName » ?\n\nTous ses dossiers ainsi que tous leurs tracés et points seront définitivement supprimés.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDel = fName
                        fileToDelete = null
                        onDeleteFile(toDel)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer le fichier", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { fileToDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }

    // --- DIALOGUE DE CONFIRMATION SUPPRESSION D'UN DOSSIER ---
    if (folderToDelete != null) {
        val pair = folderToDelete
        val fName = pair?.first ?: ""
        val fFile = pair?.second
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            icon = {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text("Supprimer le dossier ?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Voulez-vous supprimer le dossier « $fName » et tous les tracés et points qu'il contient ?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        folderToDelete = null
                        onDeleteFolder?.invoke(fName, fFile)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Supprimer le dossier", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { folderToDelete = null }) {
                    Text("Annuler")
                }
            }
        )
    }
}
