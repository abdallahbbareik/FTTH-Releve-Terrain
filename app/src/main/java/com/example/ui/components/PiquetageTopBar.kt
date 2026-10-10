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
import androidx.compose.material.icons.filled.PhotoCamera
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TelecomCyan
import com.example.ui.theme.TelecomNavy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.TextStyle
import com.example.data.local.FtthNodeEntity
import com.example.data.photo.PhotoResolutionMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiquetageTopBar(
    currentProject: String = "projet01",
    searchQuery: String,
    totalNodesCount: Int,
    matchingNodes: List<FtthNodeEntity> = emptyList(),
    currentPhotoResolutionMode: PhotoResolutionMode = PhotoResolutionMode.NATIVE,
    onSearchQueryChange: (String) -> Unit,
    onSelectNode: (FtthNodeEntity) -> Unit = {},
    onOpenFilters: () -> Unit,
    onOpenSyncLogs: () -> Unit,
    onOpenProjectFolder: () -> Unit = onOpenSyncLogs,
    onOpenExport: () -> Unit,
    onOpenWorkflowGuide: () -> Unit,
    onOpenPhotoResolutionConfig: () -> Unit = {},
    onExportZip: () -> Unit,
    onExportKmz: () -> Unit,
    onExportGeoJson: () -> Unit
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
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
                        placeholder = { Text("Réf (POT-101...), nom, adresse...", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("search_nodes_input"),
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
                            unfocusedContainerColor = Color(0xFF1E293B),
                            focusedPlaceholderColor = Color(0xFF94A3B8),
                            unfocusedPlaceholderColor = Color(0xFF94A3B8)
                        ),
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Effacer", tint = Color.White)
                                }
                            }
                        }
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onOpenProjectFolder() }
                    ) {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Releve-Terrain",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF1E293B)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = currentProject,
                                            color = TelecomCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "$totalNodesCount nœuds dans $currentProject",
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
                            text = {
                                Column {
                                    Text("Qualité & Résolution des photos", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = "${currentPhotoResolutionMode.title} (${currentPhotoResolutionMode.subtitle})",
                                        fontSize = 11.sp,
                                        color = TelecomCyan
                                    )
                                }
                            },
                            leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = TelecomCyan) },
                            onClick = {
                                menuExpanded = false
                                onOpenPhotoResolutionConfig()
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

        // Liste déroulante des résultats de recherche pour zoomer directement sur la référence
        AnimatedVisibility(visible = isSearchActive && searchQuery.isNotBlank()) {
            Surface(
                color = Color(0xFF0F172A),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Résultats (${matchingNodes.size}) : touchez pour zoomer",
                            style = MaterialTheme.typography.labelSmall,
                            color = TelecomCyan,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer recherche", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }

                    if (matchingNodes.isEmpty()) {
                        Text(
                            text = "Aucune entité trouvée pour \"$searchQuery\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.height(minOf(200.dp, (matchingNodes.size * 52).dp))) {
                            items(matchingNodes.take(10)) { node ->
                                val nodeCol = FtthNodeVisuals.getNodeColor(node.type)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectNode(node)
                                            isSearchActive = false
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(nodeCol),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(FtthNodeVisuals.getNodeDrawableRes(node.type)),
                                            contentDescription = node.type.label,
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = node.id,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (node.operator.isNotBlank()) "${node.type.label} (${node.operator})" else node.type.label,
                                                fontSize = 12.sp,
                                                color = Color(0xFFCBD5E1),
                                                maxLines = 1
                                            )
                                        }
                                        if (node.address.isNotBlank()) {
                                            Text(
                                                text = node.address,
                                                fontSize = 10.sp,
                                                color = Color(0xFF94A3B8),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Place,
                                        contentDescription = "Zoomer sur la carte",
                                        tint = TelecomCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                HorizontalDivider(color = Color(0xFF1E293B))
                            }
                        }
                    }
                }
            }
        }
    }
}
