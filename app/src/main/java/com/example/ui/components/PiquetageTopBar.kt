package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.example.ui.theme.TelecomCyan
import com.example.ui.theme.TelecomNavy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiquetageTopBar(
    searchQuery: String,
    totalNodesCount: Int,
    onSearchQueryChange: (String) -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSyncLogs: () -> Unit,
    onOpenProjectFolder: () -> Unit = onOpenSyncLogs,
    onOpenExport: () -> Unit,
    onOpenWorkflowGuide: () -> Unit,
    onExportZip: () -> Unit,
    onExportKmz: () -> Unit,
    onExportGeoJson: () -> Unit
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = TelecomNavy,
                titleContentColor = Color.White,
                actionIconContentColor = Color.White
            ),
            title = {
                if (isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Rechercher nœud, adresse, réf...", color = Color(0xFFA0AEC0), fontSize = 13.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("search_nodes_input"),
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Effacer", tint = Color.White)
                                }
                            }
                        }
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(TelecomCyan),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "FO",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = TelecomNavy
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Releve-Terrain",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "$totalNodesCount nœuds relevés",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            },
            actions = {
                IconButton(
                    onClick = onOpenProjectFolder,
                    modifier = Modifier.testTag("open_project_folder_topbar_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = "Dossier Relevé-Terrain (Points, Trajets, Liaisons)"
                    )
                }

                IconButton(
                    onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) onSearchQueryChange("")
                    },
                    modifier = Modifier.testTag("toggle_search_button")
                ) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Rechercher"
                    )
                }

                IconButton(
                    onClick = onOpenFilters,
                    modifier = Modifier.testTag("open_filters_button")
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = "Filtres")
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("overflow_menu_button")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export ZIP complet (GeoJSON, KMZ, Photos)", fontWeight = FontWeight.SemiBold) },
                            leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null, tint = TelecomCyan) },
                            onClick = {
                                menuExpanded = false
                                onExportZip()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export KMZ seul (Google Earth)") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null, tint = TelecomCyan) },
                            onClick = {
                                menuExpanded = false
                                onExportKmz()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export GeoJSON seul (SIG)") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onExportGeoJson()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Rapport & Statistiques avancés") },
                            leadingIcon = { Icon(Icons.Default.Assessment, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenExport()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Guide du Workflow Piquetage") },
                            leadingIcon = { Icon(Icons.Default.Route, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenWorkflowGuide()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Journal d'activité local") },
                            leadingIcon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenSyncLogs()
                            }
                        )
                    }
                }
            }
        )
    }
}
