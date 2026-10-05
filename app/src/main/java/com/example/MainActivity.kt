package com.example

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.storage.TrackPhoto
import com.example.ui.components.AddLinkDialog
import com.example.ui.components.AddNodeDialog
import com.example.ui.components.ExportReportDialog
import com.example.ui.components.FilterLayerSheet
import com.example.ui.components.GpsTrackControlBar
import com.example.ui.components.ManualTrackEditorBar
import com.example.ui.components.MoveNodeEditorBar
import com.example.ui.components.MoveVertexEditorBar
import com.example.ui.components.NodeDetailSheet
import com.example.ui.components.PiquetageTopBar
import com.example.ui.components.StraightenEditorBar
import com.example.ui.components.SyncLogSheet
import com.example.ui.components.SyncStatusBanner
import com.example.ui.components.TrackDetailDialog
import com.example.ui.components.TracksListDialog
import com.example.ui.components.WorkflowGuideDialog
import com.example.ui.components.map.OsmMapView
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.FtthViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted) {
            viewModel.startGpsService()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA
            )
        )
    }

    val filteredNodes by viewModel.filteredNodes.collectAsStateWithLifecycle()
    val rawNodes by viewModel.rawNodes.collectAsStateWithLifecycle()
    val rawLinks by viewModel.rawLinks.collectAsStateWithLifecycle()
    val allTracks by viewModel.allTracks.collectAsStateWithLifecycle()
    val activeTrack by viewModel.activeTrack.collectAsStateWithLifecycle()
    val activeTrackPoints by viewModel.activeTrackPoints.collectAsStateWithLifecycle()

    val isManualTrackMode by viewModel.isManualTrackMode.collectAsStateWithLifecycle()
    val manualTrackPoints by viewModel.manualTrackPoints.collectAsStateWithLifecycle()

    val isStraightenMode by viewModel.isStraightenMode.collectAsStateWithLifecycle()
    val selectedStraightenTrack by viewModel.selectedStraightenTrack.collectAsStateWithLifecycle()
    val selectedStraightenIndices by viewModel.selectedStraightenIndices.collectAsStateWithLifecycle()

    val movingNode by viewModel.movingNode.collectAsStateWithLifecycle()
    val tempMoveNodePosition by viewModel.tempMoveNodePosition.collectAsStateWithLifecycle()

    val isMoveVertexMode by viewModel.isMoveVertexMode.collectAsStateWithLifecycle()
    val selectedTrackToMoveVertex by viewModel.selectedTrackToMoveVertex.collectAsStateWithLifecycle()
    val movingVertexIndex by viewModel.movingVertexIndex.collectAsStateWithLifecycle()
    val tempVertexPosition by viewModel.tempVertexPosition.collectAsStateWithLifecycle()

    val isPickOnMapMode by viewModel.isPickOnMapMode.collectAsStateWithLifecycle()
    val pendingStakePosition by viewModel.pendingStakePosition.collectAsStateWithLifecycle()
    val stakedPositionForForm by viewModel.stakedPositionForForm.collectAsStateWithLifecycle()

    val selectedNode by viewModel.selectedNode.collectAsStateWithLifecycle()
    val selectedTrackForDetail by viewModel.selectedTrackForDetail.collectAsStateWithLifecycle()
    val showTracksListDialog by viewModel.showTracksListDialog.collectAsStateWithLifecycle()

    var viewingTrackPhoto by remember { mutableStateOf<TrackPhoto?>(null) }

    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeMapLayer by viewModel.activeMapLayer.collectAsStateWithLifecycle()

    val isCableDrawingMode by viewModel.isCableDrawingMode.collectAsStateWithLifecycle()
    val cableFirstNode by viewModel.cableFirstNode.collectAsStateWithLifecycle()

    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val gpsStatus by viewModel.gpsStatus.collectAsStateWithLifecycle()
    val syncLogs by viewModel.syncLogs.collectAsStateWithLifecycle()

    val showAddNodeDialog by viewModel.showAddNodeDialog.collectAsStateWithLifecycle()
    val showFilterSheet by viewModel.showFilterSheet.collectAsStateWithLifecycle()
    val showSyncLogSheet by viewModel.showSyncLogSheet.collectAsStateWithLifecycle()
    val showExportDialog by viewModel.showExportDialog.collectAsStateWithLifecycle()
    val showConnectCableDialog by viewModel.showConnectCableDialog.collectAsStateWithLifecycle()
    val pendingLinkNodes by viewModel.pendingLinkNodes.collectAsStateWithLifecycle()

    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    var showWorkflowGuide by remember { mutableStateOf(false) }

    BackHandler(
        enabled = selectedNode != null || isCableDrawingMode || isManualTrackMode || isStraightenMode ||
                movingNode != null || isMoveVertexMode || showFilterSheet || showSyncLogSheet ||
                showExportDialog || showWorkflowGuide || showTracksListDialog || selectedTrackForDetail != null ||
                pendingStakePosition != null || isPickOnMapMode || viewingTrackPhoto != null
    ) {
        if (selectedNode != null) {
            viewModel.selectNode(null)
        } else if (viewingTrackPhoto != null) {
            viewingTrackPhoto = null
        } else if (movingNode != null) {
            viewModel.cancelMoveNode()
        } else if (isMoveVertexMode) {
            viewModel.cancelMoveVertex()
        } else if (pendingStakePosition != null || isPickOnMapMode) {
            viewModel.cancelStakingPosition()
        } else if (isManualTrackMode) {
            viewModel.cancelManualTrack()
        } else if (isStraightenMode) {
            viewModel.cancelStraightenMode()
        } else if (selectedTrackForDetail != null) {
            viewModel.closeTrackDetail()
        } else if (showTracksListDialog) {
            viewModel.dismissTracksList()
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

                // Bandeau d'état : Stockage Local Documents/Releve-Terrain
                SyncStatusBanner(
                    nodesCount = rawNodes.size,
                    linksCount = rawLinks.size,
                    tracksCount = allTracks.size,
                    onOpenLogs = { viewModel.openSyncLogSheet() }
                )

                // Barre de contrôle GPS & Tracés
                if (!isManualTrackMode && !isStraightenMode && movingNode == null && !isMoveVertexMode) {
                    GpsTrackControlBar(
                        gpsStatus = gpsStatus,
                        locationData = userLocation,
                        activeTrack = activeTrack,
                        totalTracksCount = allTracks.size,
                        onStartAutoTrack = { viewModel.startAutoGpsTrack() },
                        onStopAutoTrack = { viewModel.stopAutoGpsTrack() },
                        onStartManualTrack = { viewModel.startManualTrack() },
                        onPinAtGpsLocation = { viewModel.requestStakingAtGpsLocation() },
                        onPinOnMapLocation = { viewModel.startPickOnMapMode() },
                        onOpenTracksList = { viewModel.openTracksList() },
                        onRequestGpsPermission = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )
                }

                // Barre d'outils flottante : Mode Déplacement de Nœud
                if (movingNode != null) {
                    MoveNodeEditorBar(
                        node = movingNode!!,
                        tempPosition = tempMoveNodePosition,
                        onConfirmMove = { viewModel.confirmMoveNode() },
                        onCancel = { viewModel.cancelMoveNode() }
                    )
                }

                // Barre d'outils flottante : Mode Déplacement de Sommet de Trajet
                if (isMoveVertexMode) {
                    MoveVertexEditorBar(
                        trackName = selectedTrackToMoveVertex?.name ?: "Trajet",
                        vertexIndex = movingVertexIndex,
                        tempPosition = tempVertexPosition,
                        onConfirmMove = { viewModel.confirmMoveVertex() },
                        onCancel = { viewModel.cancelMoveVertex() }
                    )
                }

                // Barre d'outils flottante : Mode Tracé Manuel
                if (isManualTrackMode) {
                    ManualTrackEditorBar(
                        points = manualTrackPoints,
                        onUndoLastPoint = { viewModel.undoLastManualTrackPoint() },
                        onSaveTrack = { viewModel.saveManualTrack() },
                        onCancel = { viewModel.cancelManualTrack() }
                    )
                }

                // Barre d'outils flottante : Mode Redresser entre 2 sommets
                if (isStraightenMode) {
                    StraightenEditorBar(
                        trackName = selectedStraightenTrack?.name ?: "Trajet",
                        selectedIndices = selectedStraightenIndices,
                        onApplyStraighten = { viewModel.applyStraightenBetweenVertices() },
                        onCancel = { viewModel.cancelStraightenMode() }
                    )
                }
            }
        },
        floatingActionButton = {
            if (!isCableDrawingMode && !isManualTrackMode && !isStraightenMode && movingNode == null && !isMoveVertexMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.toggleCableDrawingMode() },
                        icon = { Icon(Icons.Default.Timeline, contentDescription = null) },
                        text = { Text("Lier Fibre", fontSize = 12.sp) },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.testTag("toggle_cable_fab")
                    )

                    ExtendedFloatingActionButton(
                        onClick = { viewModel.requestStakingAtGpsLocation() },
                        icon = { Icon(Icons.Default.AddLocationAlt, contentDescription = null) },
                        text = { Text("Piqueter", fontWeight = FontWeight.Bold) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("stake_at_gps_fab")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Vue Cartographique OsmDroid
            OsmMapView(
                nodes = filteredNodes,
                links = rawLinks,
                userLocation = userLocation,
                allTracks = allTracks,
                activeTrackPoints = activeTrackPoints,
                selectedNode = selectedNode,
                isManualTrackMode = isManualTrackMode,
                manualTrackPoints = manualTrackPoints,
                pendingStakePosition = pendingStakePosition,
                isStraightenMode = isStraightenMode,
                selectedStraightenIndices = selectedStraightenIndices,
                movingNode = movingNode,
                tempMoveNodePosition = tempMoveNodePosition,
                isMoveVertexMode = isMoveVertexMode,
                selectedTrackToMoveVertex = selectedTrackToMoveVertex,
                movingVertexIndex = movingVertexIndex,
                tempVertexPosition = tempVertexPosition,
                isPickOnMapMode = isPickOnMapMode,
                onCancelPickOnMapMode = { viewModel.cancelPickOnMapMode() },
                onNodeClick = { viewModel.selectNode(it) },
                onMapLongClick = { lat, lon -> viewModel.requestStakingAtMapPosition(lat, lon) },
                onManualTrackAddPoint = { lat, lon -> viewModel.addManualTrackPoint(lat, lon) },
                onConfirmStakingPosition = { lat, lon -> viewModel.confirmStakingPosition(lat, lon) },
                onCancelStakingPosition = { viewModel.cancelStakingPosition() },
                onStraightenVertexClicked = { idx -> viewModel.onStraightenVertexClicked(idx) },
                onMapClickForMove = { lat, lon ->
                    if (movingNode != null) viewModel.setTempNodeMovePosition(lat, lon)
                    else if (isMoveVertexMode) viewModel.setTempVertexPosition(lat, lon)
                },
                onSelectVertexToMove = { idx -> viewModel.selectVertexToMove(idx) },
                onTrackPhotoClick = { photo -> viewingTrackPhoto = photo },
                modifier = Modifier.fillMaxSize()
            )

            // Notification dynamique
            AnimatedVisibility(
                visible = bannerMessage != null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                if (bannerMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.95f),
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = bannerMessage!!,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fermer",
                                tint = MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { viewModel.dismissBanner() }
                            )
                        }
                    }
                }
            }

            // Fiche détaillée du nœud (avec modification de position et déplacement)
            if (selectedNode != null) {
                NodeDetailSheet(
                    node = selectedNode!!,
                    userLocation = userLocation,
                    onDismiss = { viewModel.selectNode(null) },
                    onSave = { updated -> viewModel.saveNode(updated) },
                    onDelete = { id -> viewModel.deleteNode(id) },
                    onStartMoveNodeOnMap = { nodeToMove -> viewModel.startMoveNode(nodeToMove) }
                )
            }

            // Dialogue d'ajout de nœud
            if (showAddNodeDialog) {
                val initLat = stakedPositionForForm?.first ?: userLocation?.latitude ?: 48.8566
                val initLon = stakedPositionForForm?.second ?: userLocation?.longitude ?: 2.3522
                AddNodeDialog(
                    latitude = initLat,
                    longitude = initLon,
                    existingNodesCount = rawNodes.size,
                    onDismiss = { viewModel.dismissAddNodeDialog() },
                    onNodeCreated = { newNode ->
                        viewModel.saveNode(newNode, isNew = true)
                        viewModel.dismissAddNodeDialog()
                    }
                )
            }

            // Dialogue Liste des trajets
            if (showTracksListDialog) {
                TracksListDialog(
                    tracks = allTracks,
                    onSelectTrack = { viewModel.openTrackDetail(it) },
                    onDismiss = { viewModel.dismissTracksList() }
                )
            }

            // Dialogue Fiche Trajet (Renommage, Photos sur trajet, Simplification RDP, Déplacement sommet)
            if (selectedTrackForDetail != null) {
                TrackDetailDialog(
                    track = selectedTrackForDetail!!,
                    userLocation = userLocation,
                    onDismiss = { viewModel.closeTrackDetail() },
                    onUpdateTrackName = { id, name -> viewModel.updateTrackName(id, name) },
                    onAddTrackPhoto = { id, path, lat, lon -> viewModel.addTrackPhoto(id, path, lat, lon) },
                    onDeleteTrackPhoto = { id, photoId -> viewModel.deleteTrackPhoto(id, photoId) },
                    onApplySimplification = { id, pts, tol -> viewModel.applyTrackSimplification(id, pts, tol) },
                    onRestoreOriginal = { id -> viewModel.restoreOriginalTrack(id) },
                    onStartStraightenMode = { t -> viewModel.startStraightenMode(t) },
                    onStartMoveVertexMode = { t -> viewModel.startMoveVertexMode(t) },
                    onDeleteTrack = { id -> viewModel.deleteTrack(id) }
                )
            }

            // Aperçu d'une photo le long du tracé cliquée sur la carte
            if (viewingTrackPhoto != null) {
                Dialog(onDismissRequest = { viewingTrackPhoto = null }) {
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Photo le long du tracé",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(Date(viewingTrackPhoto!!.timestamp)),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            AsyncImage(
                                model = File(viewingTrackPhoto!!.photoPath),
                                contentDescription = "Photo trajet",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { viewingTrackPhoto = null }) {
                                Text("Fermer")
                            }
                        }
                    }
                }
            }

            // Filtres
            if (showFilterSheet) {
                FilterLayerSheet(
                    filterState = filterState,
                    onFilterChanged = { viewModel.setFilterState(it) },
                    onDismiss = { viewModel.dismissFilterSheet() }
                )
            }

            // Journal local
            if (showSyncLogSheet) {
                SyncLogSheet(
                    logs = syncLogs,
                    isLiveActive = false,
                    onToggleLive = {},
                    onManualSync = {},
                    onDismiss = { viewModel.dismissSyncLogSheet() }
                )
            }

            // Exportation réelle
            if (showExportDialog) {
                ExportReportDialog(
                    nodes = rawNodes,
                    links = rawLinks,
                    onExportGeoJson = { ctx, cb -> viewModel.exportGeoJson(ctx, cb) },
                    onExportCsv = { ctx, cb -> viewModel.exportCsv(ctx, cb) },
                    onExportKml = { ctx, cb -> viewModel.exportKml(ctx, cb) },
                    onExportZip = { ctx, cb -> viewModel.exportCompleteZip(ctx, cb) },
                    onShareFile = { ctx, f, mime -> viewModel.shareExportFile(ctx, f, mime) },
                    onDismiss = { viewModel.dismissExportDialog() }
                )
            }

            if (showWorkflowGuide) {
                WorkflowGuideDialog(onDismiss = { showWorkflowGuide = false })
            }

            // Câblage
            if (showConnectCableDialog && pendingLinkNodes != null) {
                AddLinkDialog(
                    fromNode = pendingLinkNodes!!.first,
                    toNode = pendingLinkNodes!!.second,
                    onDismiss = { viewModel.dismissConnectCableDialog() },
                    onLinkCreated = { cableType, installationType, capacityFO ->
                        viewModel.createFiberLink(
                            pendingLinkNodes!!.first,
                            pendingLinkNodes!!.second,
                            cableType,
                            installationType,
                            capacityFO
                        )
                    }
                )
            }
        }
    }
}
