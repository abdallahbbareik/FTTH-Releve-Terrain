package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.AddLinkDialog
import com.example.ui.components.AddNodeDialog
import com.example.ui.components.ExportReportDialog
import com.example.ui.components.FilterLayerSheet
import com.example.ui.components.NodeDetailSheet
import com.example.ui.components.PiquetageTopBar
import com.example.ui.components.SyncLogSheet
import com.example.ui.components.SyncStatusBanner
import com.example.ui.components.WorkflowGuideDialog
import com.example.ui.map.InteractiveFtthMap
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.FtthViewModel
import com.example.ui.viewmodel.MapLayerType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: FtthViewModel = viewModel()
                FtthMainScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun FtthMainScreen(viewModel: FtthViewModel) {
    val filteredNodes by viewModel.filteredNodes.collectAsStateWithLifecycle()
    val rawNodes by viewModel.rawNodes.collectAsStateWithLifecycle()
    val rawLinks by viewModel.rawLinks.collectAsStateWithLifecycle()
    val selectedNode by viewModel.selectedNode.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeMapLayer by viewModel.activeMapLayer.collectAsStateWithLifecycle()

    val isCableDrawingMode by viewModel.isCableDrawingMode.collectAsStateWithLifecycle()
    val cableFirstNode by viewModel.cableFirstNode.collectAsStateWithLifecycle()
    val stakedPosition by viewModel.stakedPosition.collectAsStateWithLifecycle()

    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val isLiveSyncActive by viewModel.isLiveSyncActive.collectAsStateWithLifecycle()
    val lastSyncTimestamp by viewModel.lastSyncTimestamp.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()

    val showAddNodeDialog by viewModel.showAddNodeDialog.collectAsStateWithLifecycle()
    val showFilterSheet by viewModel.showFilterSheet.collectAsStateWithLifecycle()
    val showSyncLogSheet by viewModel.showSyncLogSheet.collectAsStateWithLifecycle()
    val showExportDialog by viewModel.showExportDialog.collectAsStateWithLifecycle()
    val showConnectCableDialog by viewModel.showConnectCableDialog.collectAsStateWithLifecycle()
    val pendingLinkNodes by viewModel.pendingLinkNodes.collectAsStateWithLifecycle()
    val technicianLocation by viewModel.technicianLocation.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()

    var showWorkflowGuide by remember { mutableStateOf(false) }

    // Handle back button for any open sheets or selection
    BackHandler(
        enabled = selectedNode != null || isCableDrawingMode || showFilterSheet || showSyncLogSheet || showExportDialog || showWorkflowGuide
    ) {
        if (selectedNode != null) {
            viewModel.selectNode(null)
        } else if (isCableDrawingMode) {
            viewModel.toggleCableDrawingMode()
        } else if (showWorkflowGuide) {
            showWorkflowGuide = false
        } else if (showFilterSheet) {
            viewModel.dismissFilterSheet()
        } else if (showSyncLogSheet) {
            viewModel.dismissSyncLogSheet()
        } else if (showExportDialog) {
            viewModel.dismissExportDialog()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                PiquetageTopBar(
                    searchQuery = searchQuery,
                    totalNodesCount = rawNodes.size,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onOpenFilters = { viewModel.openFilterSheet() },
                    onOpenSyncLogs = { viewModel.openSyncLogSheet() },
                    onOpenExport = { viewModel.openExportDialog() },
                    onOpenWorkflowGuide = { showWorkflowGuide = true },
                    onResetDemo = { viewModel.resetDemoData() }
                )
                SyncStatusBanner(
                    syncStatus = syncStatus,
                    isLiveActive = isLiveSyncActive,
                    lastSyncTime = lastSyncTimestamp,
                    onTriggerSync = { viewModel.triggerManualSync() },
                    onOpenLogs = { viewModel.openSyncLogSheet() }
                )
            }
        },
        floatingActionButton = {
            if (!isCableDrawingMode) {
                ExtendedFloatingActionButton(
                    onClick = {
                        // Quick Stake at technician's current GPS location
                        viewModel.startStakingAt(technicianLocation.first, technicianLocation.second)
                    },
                    icon = { Icon(Icons.Default.AddLocationAlt, contentDescription = null) },
                    text = { Text("Piqueter ici", fontWeight = FontWeight.Bold) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("stake_at_gps_fab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Interactive Map View
            InteractiveFtthMap(
                nodes = filteredNodes,
                links = rawLinks,
                selectedNode = selectedNode,
                activeMapLayer = activeMapLayer,
                isCableDrawingMode = isCableDrawingMode,
                cableFirstNode = cableFirstNode,
                technicianPosition = technicianLocation,
                onNodeSelected = { viewModel.selectNode(it) },
                onMapLongPress = { lat, lon -> viewModel.startStakingAt(lat, lon) },
                onToggleCableMode = { viewModel.toggleCableDrawingMode() },
                onCycleMapLayer = {
                    val next = when (activeMapLayer) {
                        MapLayerType.CADASTRE -> MapLayerType.SATELLITE
                        MapLayerType.SATELLITE -> MapLayerType.RUES
                        MapLayerType.RUES -> MapLayerType.CADASTRE
                    }
                    viewModel.setMapLayer(next)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Dynamic Notification Banner
            AnimatedVisibility(
                visible = bannerMessage != null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                if (bannerMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.94f),
                        shadowElevation = 6.dp,
                        modifier = Modifier.testTag("user_notification_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.inversePrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = bannerMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.dismissBanner() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Fermer",
                                    tint = MaterialTheme.colorScheme.inverseOnSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Sheets and Dialogs
    selectedNode?.let { node ->
        NodeDetailSheet(
            node = node,
            onDismiss = { viewModel.selectNode(null) },
            onSave = { updated -> viewModel.saveNode(updated) },
            onDelete = { id -> viewModel.deleteNode(id) }
        )
    }

    if (showAddNodeDialog) {
        val (stakeLat, stakeLon) = stakedPosition ?: technicianLocation
        AddNodeDialog(
            latitude = stakeLat,
            longitude = stakeLon,
            existingNodesCount = rawNodes.size,
            onDismiss = { viewModel.dismissAddNodeDialog() },
            onNodeCreated = { newNode ->
                viewModel.saveNode(newNode, isNew = true)
                viewModel.dismissAddNodeDialog()
            }
        )
    }

    if (showConnectCableDialog && pendingLinkNodes != null) {
        val (from, to) = pendingLinkNodes!!
        AddLinkDialog(
            fromNode = from,
            toNode = to,
            onDismiss = { viewModel.dismissConnectCableDialog() },
            onLinkCreated = { cableType, installationType, capacityFO ->
                viewModel.createFiberLink(from, to, cableType, installationType, capacityFO)
            }
        )
    }

    if (showFilterSheet) {
        FilterLayerSheet(
            filterState = filterState,
            onFilterChanged = { viewModel.setFilterState(it) },
            onDismiss = { viewModel.dismissFilterSheet() }
        )
    }

    if (showSyncLogSheet) {
        SyncLogSheet(
            logs = syncLogs,
            isLiveActive = isLiveSyncActive,
            onToggleLive = { viewModel.toggleLiveSync() },
            onManualSync = { viewModel.triggerManualSync() },
            onDismiss = { viewModel.dismissSyncLogSheet() }
        )
    }

    if (showExportDialog) {
        ExportReportDialog(
            nodes = rawNodes,
            links = rawLinks,
            onDismiss = { viewModel.dismissExportDialog() }
        )
    }

    if (showWorkflowGuide) {
        WorkflowGuideDialog(
            onDismiss = { showWorkflowGuide = false }
        )
    }
}
