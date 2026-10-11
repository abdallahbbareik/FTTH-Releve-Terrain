package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.gps.GpsLocationData
import com.example.data.gps.GpsStatus
import com.example.data.local.FtthNodeEntity
import com.example.ui.theme.TelecomCyan
import com.example.ui.theme.TelecomNavy
import java.util.Locale

@Composable
fun CompactPiquetageTopBar(
    currentProject: String = "projet01",
    userLocation: GpsLocationData?,
    gpsStatus: GpsStatus,
    searchQuery: String = "",
    matchingNodes: List<FtthNodeEntity> = emptyList(),
    onSearchQueryChange: (String) -> Unit = {},
    onSelectNode: (FtthNodeEntity) -> Unit = {},
    onOpenProjectFolder: () -> Unit,
    onOpenGpsDetails: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSyncLogs: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenWorkflowGuide: () -> Unit,
    onOpenPhotoResolutionConfig: () -> Unit = {},
    onExportZip: () -> Unit,
    onExportKmz: () -> Unit,
    onExportGeoJson: () -> Unit,
    onStartAutoTrack: () -> Unit = {},
    onStartManualTrack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    // Statut GPS pour la pastille
    val isGpsFixed = userLocation != null && (gpsStatus == GpsStatus.FIXED || userLocation.accuracy < 25f)
    val dotColor = when {
        isGpsFixed -> Color(0xFF16A34A) // Vert
        gpsStatus == GpsStatus.SEARCHING -> Color(0xFFEA580C) // Orange
        else -> Color(0xFFDC2626) // Rouge
    }

    val accuracyText = if (userLocation != null && userLocation.accuracy > 0f) {
        val accInt = userLocation.accuracy.toInt().coerceAtLeast(1)
        "±$accInt m"
    } else if (gpsStatus == GpsStatus.SEARCHING) {
        "GPS..."
    } else {
        "GPS off"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = TelecomNavy,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Menu (icône hamburger 3 traits)
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("compact_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu principal",
                            tint = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .width(260.dp)
                    ) {
                        // Changer de projet
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text("Projet actif", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(currentProject, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = TelecomNavy)
                            },
                            onClick = {
                                menuExpanded = false
                                onOpenProjectFolder()
                            }
                        )

                        HorizontalDivider()

                        // Rechercher un point
                        DropdownMenuItem(
                            text = { Text("Rechercher un point") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                isSearchActive = !isSearchActive
                            }
                        )

                        // Filtrer les calques
                        DropdownMenuItem(
                            text = { Text("Filtrer la carte") },
                            leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenFilters()
                            }
                        )

                        // Enregistrement de trajet continu
                        DropdownMenuItem(
                            text = { Text("Tracer infra GPS (auto)") },
                            leadingIcon = { Icon(Icons.Default.Route, contentDescription = null, tint = Color(0xFF16A34A)) },
                            onClick = {
                                menuExpanded = false
                                onStartAutoTrack()
                            }
                        )

                        // Tracé manuel
                        DropdownMenuItem(
                            text = { Text("Tracé manuel sur carte") },
                            leadingIcon = { Icon(Icons.Default.Timeline, contentDescription = null, tint = TelecomCyan) },
                            onClick = {
                                menuExpanded = false
                                onStartManualTrack()
                            }
                        )

                        HorizontalDivider()

                        // Exportation
                        DropdownMenuItem(
                            text = { Text("Exporter le projet") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenExport()
                            }
                        )

                        // Fichiers et journaux locaux
                        DropdownMenuItem(
                            text = { Text("Fichiers & Synchronisation") },
                            leadingIcon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenSyncLogs()
                            }
                        )

                        // Résolution photos
                        DropdownMenuItem(
                            text = { Text("Résolution des photos") },
                            leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenPhotoResolutionConfig()
                            }
                        )

                        // Guide méthodologique
                        DropdownMenuItem(
                            text = { Text("Guide méthodologique") },
                            leadingIcon = { Icon(Icons.Default.Assessment, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenWorkflowGuide()
                            }
                        )
                    }
                }

                // 2. Bouton Recherche direct devant le projet
                IconButton(
                    onClick = { isSearchActive = !isSearchActive },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("top_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Rechercher",
                        tint = if (isSearchActive) TelecomCyan else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 3. Projet (touche pour changer)
                Surface(
                    onClick = onOpenProjectFolder,
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .testTag("project_selector_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Projet",
                            tint = TelecomCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentProject,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Changer",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 3. État GPS (point vert et ±3 m). Touche pour afficher les coordonnées
                Surface(
                    onClick = onOpenGpsDetails,
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, dotColor.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .testTag("gps_status_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        // Point vert (ou orange/rouge)
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )

                        Spacer(modifier = Modifier.width(7.dp))

                        // Précision ex: "±3 m"
                        Text(
                            text = accuracyText,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Barre de recherche dépliable si activée
            AnimatedVisibility(visible = isSearchActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Rechercher POT-101, BTR...", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("compact_search_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = TelecomCyan,
                            focusedBorderColor = TelecomCyan,
                            unfocusedBorderColor = Color(0xFF64748B),
                            focusedContainerColor = Color(0xFF1E293B),
                            unfocusedContainerColor = Color(0xFF1E293B)
                        ),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Effacer", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = { isSearchActive = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer recherche", tint = Color.White)
                    }
                }
            }
        }
    }
}
