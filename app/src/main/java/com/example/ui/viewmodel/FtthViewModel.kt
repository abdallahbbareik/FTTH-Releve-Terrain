package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.export.RealExportService
import com.example.data.gps.GpsLocationData
import com.example.data.gps.GpsLocationService
import com.example.data.gps.GpsStatus
import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.local.NodeConformity
import com.example.data.local.NodeStatus
import com.example.data.local.SyncLogEntity
import com.example.data.storage.DocumentStorageManager
import com.example.data.storage.StoredTrack
import com.example.data.storage.TrackPoint
import com.example.data.util.TrackGeometryHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class MapLayerType(val label: String) {
    CADASTRE("Plan OpenStreetMap"),
    SATELLITE("Satellite Esri"),
    RUES("Plan Filaire")
}

data class MapFilterState(
    val showPoteaux: Boolean = true,
    val showChambres: Boolean = true,
    val showBoitiers: Boolean = true,
    val showSRO: Boolean = true,
    val showImmeubles: Boolean = true,
    val showVillas: Boolean = true,
    val showCables: Boolean = true,
    val selectedStatus: NodeStatus? = null,
    val selectedConformity: NodeConformity? = null
)

class FtthViewModel(application: Application) : AndroidViewModel(application) {

    // Gestionnaire de stockage direct SANS base de données dans "Documents/Releve-Terrain"
    private val docStorage = DocumentStorageManager(application)
    private val gpsService = GpsLocationService(application)

    // Données GPS Temps Réel
    val userLocation: StateFlow<GpsLocationData?> = gpsService.currentLocation
    val gpsStatus: StateFlow<GpsStatus> = gpsService.gpsStatus

    // Entités en mémoire synchronisées avec les fichiers JSON du dossier Documents/Releve-Terrain
    private val _rawNodes = MutableStateFlow<List<FtthNodeEntity>>(docStorage.loadNodes())
    val rawNodes: StateFlow<List<FtthNodeEntity>> = _rawNodes.asStateFlow()

    private val _rawLinks = MutableStateFlow<List<FtthLinkEntity>>(docStorage.loadLinks())
    val rawLinks: StateFlow<List<FtthLinkEntity>> = _rawLinks.asStateFlow()

    private val _allTracks = MutableStateFlow<List<StoredTrack>>(docStorage.loadTracks())
    val allTracks: StateFlow<List<StoredTrack>> = _allTracks.asStateFlow()

    // Trace GPS Automatique en direct
    private val _activeTrack = MutableStateFlow<StoredTrack?>(null)
    val activeTrack: StateFlow<StoredTrack?> = _activeTrack.asStateFlow()

    private val _activeTrackPoints = MutableStateFlow<List<TrackPoint>>(emptyList())
    val activeTrackPoints: StateFlow<List<TrackPoint>> = _activeTrackPoints.asStateFlow()

    // Mode Tracé Manuel sur la carte
    private val _isManualTrackMode = MutableStateFlow(false)
    val isManualTrackMode: StateFlow<Boolean> = _isManualTrackMode.asStateFlow()

    private val _manualTrackPoints = MutableStateFlow<List<TrackPoint>>(emptyList())
    val manualTrackPoints: StateFlow<List<TrackPoint>> = _manualTrackPoints.asStateFlow()

    // Mode Redresser entre deux sommets
    private val _isStraightenMode = MutableStateFlow(false)
    val isStraightenMode: StateFlow<Boolean> = _isStraightenMode.asStateFlow()

    private val _selectedStraightenTrack = MutableStateFlow<StoredTrack?>(null)
    val selectedStraightenTrack: StateFlow<StoredTrack?> = _selectedStraightenTrack.asStateFlow()

    private val _selectedStraightenIndices = MutableStateFlow<Pair<Int?, Int?>>(Pair(null, null))
    val selectedStraightenIndices: StateFlow<Pair<Int?, Int?>> = _selectedStraightenIndices.asStateFlow()

    // Position en cours de confirmation avant piquetage
    private val _pendingStakePosition = MutableStateFlow<Pair<Double, Double>?>(null)
    val pendingStakePosition: StateFlow<Pair<Double, Double>?> = _pendingStakePosition.asStateFlow()

    // Mode Sélection d'un point sur la Carte
    private val _isPickOnMapMode = MutableStateFlow(false)
    val isPickOnMapMode: StateFlow<Boolean> = _isPickOnMapMode.asStateFlow()

    // Mode Déplacement de Nœud sur la Carte
    private val _movingNode = MutableStateFlow<FtthNodeEntity?>(null)
    val movingNode: StateFlow<FtthNodeEntity?> = _movingNode.asStateFlow()

    private val _tempMoveNodePosition = MutableStateFlow<Pair<Double, Double>?>(null)
    val tempMoveNodePosition: StateFlow<Pair<Double, Double>?> = _tempMoveNodePosition.asStateFlow()

    // Mode Déplacement de Sommet de Trajet sur la Carte
    private val _isMoveVertexMode = MutableStateFlow(false)
    val isMoveVertexMode: StateFlow<Boolean> = _isMoveVertexMode.asStateFlow()

    private val _selectedTrackToMoveVertex = MutableStateFlow<StoredTrack?>(null)
    val selectedTrackToMoveVertex: StateFlow<StoredTrack?> = _selectedTrackToMoveVertex.asStateFlow()

    private val _movingVertexIndex = MutableStateFlow<Int?>(null)
    val movingVertexIndex: StateFlow<Int?> = _movingVertexIndex.asStateFlow()

    private val _tempVertexPosition = MutableStateFlow<Pair<Double, Double>?>(null)
    val tempVertexPosition: StateFlow<Pair<Double, Double>?> = _tempVertexPosition.asStateFlow()

    // Dialogues
    private val _selectedNode = MutableStateFlow<FtthNodeEntity?>(null)
    val selectedNode: StateFlow<FtthNodeEntity?> = _selectedNode.asStateFlow()

    private val _selectedTrackForDetail = MutableStateFlow<StoredTrack?>(null)
    val selectedTrackForDetail: StateFlow<StoredTrack?> = _selectedTrackForDetail.asStateFlow()

    private val _showTracksListDialog = MutableStateFlow(false)
    val showTracksListDialog: StateFlow<Boolean> = _showTracksListDialog.asStateFlow()

    private val _showNodesListDialog = MutableStateFlow(false)
    val showNodesListDialog: StateFlow<Boolean> = _showNodesListDialog.asStateFlow()

    private val _showProjectFolderDialog = MutableStateFlow(false)
    val showProjectFolderDialog: StateFlow<Boolean> = _showProjectFolderDialog.asStateFlow()

    private val _selectedLinkForDetail = MutableStateFlow<FtthLinkEntity?>(null)
    val selectedLinkForDetail: StateFlow<FtthLinkEntity?> = _selectedLinkForDetail.asStateFlow()

    private val _showAddNodeDialog = MutableStateFlow(false)
    val showAddNodeDialog: StateFlow<Boolean> = _showAddNodeDialog.asStateFlow()

    private val _stakedPositionForForm = MutableStateFlow<Pair<Double, Double>?>(null)
    val stakedPositionForForm: StateFlow<Pair<Double, Double>?> = _stakedPositionForForm.asStateFlow()

    private val _showFilterSheet = MutableStateFlow(false)
    val showFilterSheet: StateFlow<Boolean> = _showFilterSheet.asStateFlow()

    private val _showSyncLogSheet = MutableStateFlow(false)
    val showSyncLogSheet: StateFlow<Boolean> = _showSyncLogSheet.asStateFlow()

    private val _showExportDialog = MutableStateFlow(false)
    val showExportDialog: StateFlow<Boolean> = _showExportDialog.asStateFlow()

    private val _showConnectCableDialog = MutableStateFlow(false)
    val showConnectCableDialog: StateFlow<Boolean> = _showConnectCableDialog.asStateFlow()
    private val _pendingLinkNodes = MutableStateFlow<Pair<FtthNodeEntity, FtthNodeEntity>?>(null)
    val pendingLinkNodes: StateFlow<Pair<FtthNodeEntity, FtthNodeEntity>?> = _pendingLinkNodes.asStateFlow()

    private val _isCableDrawingMode = MutableStateFlow(false)
    val isCableDrawingMode: StateFlow<Boolean> = _isCableDrawingMode.asStateFlow()
    private val _cableFirstNode = MutableStateFlow<FtthNodeEntity?>(null)
    val cableFirstNode: StateFlow<FtthNodeEntity?> = _cableFirstNode.asStateFlow()

    private val _filterState = MutableStateFlow(MapFilterState())
    val filterState: StateFlow<MapFilterState> = _filterState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeMapLayer = MutableStateFlow(MapLayerType.CADASTRE)
    val activeMapLayer: StateFlow<MapLayerType> = _activeMapLayer.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    // Journal d'activité local
    private val _syncLogs = MutableStateFlow<List<SyncLogEntity>>(emptyList())
    val syncLogs: StateFlow<List<SyncLogEntity>> = _syncLogs.asStateFlow()

    val filteredNodes: StateFlow<List<FtthNodeEntity>> = combine(
        rawNodes,
        _filterState,
        _searchQuery
    ) { nodes, filters, query ->
        nodes.filter { node ->
            val matchesType = when (node.type) {
                FtthNodeType.POTEAU -> filters.showPoteaux
                FtthNodeType.CHAMBRE -> filters.showChambres
                FtthNodeType.BOITIER -> filters.showBoitiers
                FtthNodeType.SRO -> filters.showSRO
                FtthNodeType.IMMEUBLE -> filters.showImmeubles
                FtthNodeType.VILLA -> filters.showVillas
            }
            val matchesStatus = filters.selectedStatus == null || node.status == filters.selectedStatus
            val matchesConformity = filters.selectedConformity == null || node.etat == filters.selectedConformity
            val matchesQuery = query.isBlank() ||
                    node.name.contains(query, ignoreCase = true) ||
                    node.id.contains(query, ignoreCase = true) ||
                    node.address.contains(query, ignoreCase = true)

            matchesType && matchesStatus && matchesConformity && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        gpsService.start()

        // Enregistrement automatique des points si trace GPS active
        viewModelScope.launch {
            userLocation.collect { loc ->
                if (loc != null && _activeTrack.value != null) {
                    recordActiveTrackPoint(loc)
                }
            }
        }
    }

    fun startGpsService() {
        gpsService.start()
    }

    // --- SÉLECTION ET CONFIRMATION DE POSITION ---

    fun startPickOnMapMode() {
        _isPickOnMapMode.value = true
        _pendingStakePosition.value = null
        _bannerMessage.value = "Touchez la carte à l'emplacement souhaité pour le nouveau nœud"
    }

    fun cancelPickOnMapMode() {
        _isPickOnMapMode.value = false
    }

    fun requestStakingAtMapPosition(lat: Double, lon: Double) {
        _isPickOnMapMode.value = false
        _pendingStakePosition.value = Pair(lat, lon)
    }

    fun requestStakingAtGpsLocation() {
        _isPickOnMapMode.value = false
        val loc = userLocation.value
        if (loc != null) {
            _pendingStakePosition.value = Pair(loc.latitude, loc.longitude)
        } else {
            _bannerMessage.value = "Position GPS en cours d'acquisition..."
        }
    }

    fun updatePendingStakePosition(lat: Double, lon: Double) {
        _pendingStakePosition.value = Pair(lat, lon)
        _isPickOnMapMode.value = false
    }

    fun confirmStakingPosition(lat: Double, lon: Double) {
        _isPickOnMapMode.value = false
        _pendingStakePosition.value = null
        _stakedPositionForForm.value = Pair(lat, lon)
        _showAddNodeDialog.value = true
    }

    fun cancelStakingPosition() {
        _isPickOnMapMode.value = false
        _pendingStakePosition.value = null
    }

    // --- GESTION DES NOEUDS (DOCUMENTS/RELEVE-TERRAIN/NOEUDS.JSON) ---

    fun saveNode(node: FtthNodeEntity, isNew: Boolean = false, closeSheet: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = _rawNodes.value.toMutableList()
            val idx = list.indexOfFirst { it.id == node.id }
            if (idx >= 0) {
                list[idx] = node
            } else {
                list.add(0, node)
            }

            var createdAutoBoitierName: String? = null

            // Si Boîtier FTTH = Oui et le nœud parent n'est pas déjà un Boîtier :
            if (node.hasBoitierFtth && node.type != FtthNodeType.BOITIER) {
                val boitierId = "${node.id}-B"
                val boitierName = if (node.name.isNotBlank()) "${node.name}-B" else boitierId
                val support = when (node.type) {
                    FtthNodeType.POTEAU -> "Poteau"
                    FtthNodeType.CHAMBRE -> "Chambre"
                    else -> "Façade"
                }

                val existingIdx = list.indexOfFirst { it.id == boitierId }
                if (existingIdx >= 0) {
                    val existing = list[existingIdx]
                    list[existingIdx] = existing.copy(
                        latitude = node.latitude,
                        longitude = node.longitude,
                        boitierSupport = support,
                        address = node.address
                    )
                } else {
                    val autoBoitier = FtthNodeEntity(
                        id = boitierId,
                        type = FtthNodeType.BOITIER,
                        name = boitierName,
                        latitude = node.latitude,
                        longitude = node.longitude,
                        status = node.status,
                        etat = node.etat,
                        address = node.address,
                        hasBoitierFtth = false,
                        boitierSupport = support,
                        boitierType = if (node.type == FtthNodeType.POTEAU) "PBO" else "BPE",
                        technicianName = node.technicianName,
                        notes = "Boîtier créé automatiquement sur le support ${node.id} ($support)",
                        updatedAt = System.currentTimeMillis()
                    )
                    list.add(0, autoBoitier)
                    createdAutoBoitierName = boitierName
                }
            }

            _rawNodes.value = list
            docStorage.saveNodes(list)
            if (closeSheet) {
                _selectedNode.value = null
            } else {
                _selectedNode.value = node
            }

            if (createdAutoBoitierName != null) {
                val supportName = if (node.type == FtthNodeType.POTEAU) "Poteau" else if (node.type == FtthNodeType.CHAMBRE) "Chambre" else "Façade"
                _bannerMessage.value = "Nœud ${node.id} enregistré. Boîtier $createdAutoBoitierName créé automatiquement sur son support ($supportName). Pensez à le compléter !"
            } else {
                _bannerMessage.value = "Nœud ${node.id} enregistré dans Documents/Releve-Terrain"
            }
        }
    }

    fun deleteNode(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = _rawNodes.value.filter { it.id != id }
            _rawNodes.value = list
            docStorage.saveNodes(list)
            if (_selectedNode.value?.id == id) {
                _selectedNode.value = null
            }
            _bannerMessage.value = "Nœud $id supprimé"
        }
    }

    // --- DÉPLACEMENT MANUEL DE NŒUD SUR LA CARTE ---

    fun startMoveNode(node: FtthNodeEntity) {
        _movingNode.value = node
        _tempMoveNodePosition.value = null
        _selectedNode.value = null
        _bannerMessage.value = "Touchez la carte au nouvel emplacement pour le nœud ${node.id}"
    }

    fun setTempNodeMovePosition(lat: Double, lon: Double) {
        _tempMoveNodePosition.value = Pair(lat, lon)
    }

    fun confirmMoveNode() {
        val node = _movingNode.value ?: return
        val pos = _tempMoveNodePosition.value ?: return
        saveNode(node.copy(latitude = pos.first, longitude = pos.second))
        _movingNode.value = null
        _tempMoveNodePosition.value = null
        _bannerMessage.value = "Nœud ${node.id} déplacé avec succès !"
    }

    fun cancelMoveNode() {
        _movingNode.value = null
        _tempMoveNodePosition.value = null
    }

    // --- TRACÉ MANUEL DE TRAJET SUR LA CARTE ---

    fun startManualTrack() {
        _isManualTrackMode.value = true
        _manualTrackPoints.value = emptyList()
        _bannerMessage.value = "Mode Tracé Manuel : Touchez la carte pour ajouter des sommets"
    }

    fun addManualTrackPoint(lat: Double, lon: Double) {
        val current = _manualTrackPoints.value.toMutableList()
        current.add(TrackPoint(lat, lon))
        _manualTrackPoints.value = current
    }

    fun undoLastManualTrackPoint() {
        val current = _manualTrackPoints.value.toMutableList()
        if (current.isNotEmpty()) {
            current.removeAt(current.size - 1)
            _manualTrackPoints.value = current
        }
    }

    fun saveManualTrack(name: String = "Tracé Manuel ${System.currentTimeMillis() % 10000}") {
        viewModelScope.launch(Dispatchers.IO) {
            val pts = _manualTrackPoints.value
            if (pts.size < 2) return@launch

            val dist = TrackGeometryHelper.computeTotalDistanceMeters(pts)
            val track = StoredTrack(
                id = "TRK-${System.currentTimeMillis()}",
                name = name,
                startTime = System.currentTimeMillis(),
                endTime = System.currentTimeMillis(),
                totalDistanceMeters = dist,
                rawPoints = pts,
                points = pts,
                isSimplified = false,
                isActive = false
            )
            val all = _allTracks.value.toMutableList()
            all.add(0, track)
            _allTracks.value = all
            docStorage.saveTracks(all)

            _isManualTrackMode.value = false
            _manualTrackPoints.value = emptyList()
            _bannerMessage.value = "Trajet '$name' (${pts.size} sommets) enregistré dans Documents/Releve-Terrain/trajets.json"
        }
    }

    fun cancelManualTrack() {
        _isManualTrackMode.value = false
        _manualTrackPoints.value = emptyList()
    }

    // --- TRACE GPS AUTOMATIQUE ---

    fun startAutoGpsTrack(name: String = "Cheminement GPS ${System.currentTimeMillis() % 10000}") {
        val track = StoredTrack(
            id = "TRK-${System.currentTimeMillis()}",
            name = name,
            startTime = System.currentTimeMillis(),
            isActive = true
        )
        _activeTrack.value = track
        _activeTrackPoints.value = emptyList()
        _bannerMessage.value = "Enregistrement de la trace GPS démarré"
    }

    private fun recordActiveTrackPoint(loc: GpsLocationData) {
        val current = _activeTrackPoints.value.toMutableList()
        if (current.isNotEmpty()) {
            val last = current.last()
            val d = TrackGeometryHelper.calculateDistanceMeters(last.latitude, last.longitude, loc.latitude, loc.longitude)
            if (d < 1.0) return // Filtrer le bruit stationnaire < 1m
        }
        val pt = TrackPoint(loc.latitude, loc.longitude, loc.altitude, loc.accuracy, loc.speedKmh, loc.timestamp)
        current.add(pt)
        _activeTrackPoints.value = current

        val dist = TrackGeometryHelper.computeTotalDistanceMeters(current)
        _activeTrack.value = _activeTrack.value?.copy(
            totalDistanceMeters = dist,
            rawPoints = current,
            points = current
        )
    }

    fun stopAutoGpsTrack() {
        viewModelScope.launch(Dispatchers.IO) {
            val active = _activeTrack.value ?: return@launch
            val pts = _activeTrackPoints.value
            val finished = active.copy(
                endTime = System.currentTimeMillis(),
                isActive = false,
                rawPoints = pts,
                points = pts,
                totalDistanceMeters = TrackGeometryHelper.computeTotalDistanceMeters(pts)
            )
            val all = _allTracks.value.toMutableList()
            all.add(0, finished)
            _allTracks.value = all
            docStorage.saveTracks(all)

            _activeTrack.value = null
            _activeTrackPoints.value = emptyList()
            _selectedTrackForDetail.value = finished
            _bannerMessage.value = "Trace GPS terminée (${pts.size} points). Ouvrez la fiche pour la simplifier."
        }
    }

    // --- SIMPLIFICATION ANTI-BRUIT GPS (RAMER-DOUGLAS-PEUCKER) ---

    fun applyTrackSimplification(trackId: String, simplifiedPoints: List<TrackPoint>, tolerance: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = _allTracks.value.toMutableList()
            val idx = all.indexOfFirst { it.id == trackId }
            if (idx >= 0) {
                val t = all[idx]
                val dist = TrackGeometryHelper.computeTotalDistanceMeters(simplifiedPoints)
                val updated = t.copy(
                    points = simplifiedPoints,
                    isSimplified = true,
                    toleranceMeters = tolerance,
                    totalDistanceMeters = dist
                )
                all[idx] = updated
                _allTracks.value = all
                docStorage.saveTracks(all)
                _selectedTrackForDetail.value = updated
                _bannerMessage.value = "Trace simplifiée (tolérance ${tolerance.toInt()}m) : ${simplifiedPoints.size} sommets conservés."
            }
        }
    }

    fun restoreOriginalTrack(trackId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = _allTracks.value.toMutableList()
            val idx = all.indexOfFirst { it.id == trackId }
            if (idx >= 0) {
                val t = all[idx]
                val orig = t.rawPoints
                val dist = TrackGeometryHelper.computeTotalDistanceMeters(orig)
                val updated = t.copy(
                    points = orig,
                    isSimplified = false,
                    toleranceMeters = null,
                    totalDistanceMeters = dist
                )
                all[idx] = updated
                _allTracks.value = all
                docStorage.saveTracks(all)
                _selectedTrackForDetail.value = updated
                _bannerMessage.value = "Trace originale restaurée (${orig.size} points bruts)."
            }
        }
    }

    // --- REDRESSER ENTRE DEUX SOMMETS ---

    fun startStraightenMode(track: StoredTrack) {
        _isStraightenMode.value = true
        _selectedStraightenTrack.value = track
        _selectedStraightenIndices.value = Pair(null, null)
        _bannerMessage.value = "Redressement : Touchez le 1er sommet du tracé sur la carte"
    }

    fun onStraightenVertexClicked(index: Int) {
        val current = _selectedStraightenIndices.value
        if (current.first == null) {
            _selectedStraightenIndices.value = Pair(index, null)
            _bannerMessage.value = "Sommet 1 (#$index) sélectionné. Touchez le 2ème sommet sur la carte."
        } else if (current.second == null && current.first != index) {
            _selectedStraightenIndices.value = Pair(current.first, index)
            _bannerMessage.value = "Tronçon entre sommet #${current.first} et #$index sélectionné. Validez pour redresser en ligne droite."
        }
    }

    fun applyStraightenBetweenVertices() {
        viewModelScope.launch(Dispatchers.IO) {
            val track = _selectedStraightenTrack.value ?: return@launch
            val indices = _selectedStraightenIndices.value
            val idxA = indices.first ?: return@launch
            val idxB = indices.second ?: return@launch

            val pts = if (track.points.isNotEmpty()) track.points else track.rawPoints
            val straightened = TrackGeometryHelper.straightenBetweenVertices(pts, idxA, idxB)
            val dist = TrackGeometryHelper.computeTotalDistanceMeters(straightened)

            val all = _allTracks.value.toMutableList()
            val trackIdx = all.indexOfFirst { it.id == track.id }
            if (trackIdx >= 0) {
                val updated = track.copy(
                    points = straightened,
                    totalDistanceMeters = dist
                )
                all[trackIdx] = updated
                _allTracks.value = all
                docStorage.saveTracks(all)
                _bannerMessage.value = "Tronçon redressé en ligne droite entre sommet #$idxA et #$idxB !"
            }

            cancelStraightenMode()
        }
    }

    fun cancelStraightenMode() {
        _isStraightenMode.value = false
        _selectedStraightenTrack.value = null
        _selectedStraightenIndices.value = Pair(null, null)
    }

    // --- DÉPLACEMENT DE SOMMET DE TRACÉ SUR LA CARTE ---

    fun startMoveVertexMode(track: StoredTrack) {
        _isMoveVertexMode.value = true
        _selectedTrackToMoveVertex.value = track
        _movingVertexIndex.value = null
        _tempVertexPosition.value = null
        _bannerMessage.value = "Touchez le sommet à déplacer sur la carte"
    }

    fun selectVertexToMove(index: Int) {
        _movingVertexIndex.value = index
        _bannerMessage.value = "Sommet #$index sélectionné. Touchez le nouvel emplacement sur la carte."
    }

    fun setTempVertexPosition(lat: Double, lon: Double) {
        _tempVertexPosition.value = Pair(lat, lon)
    }

    fun confirmMoveVertex() {
        viewModelScope.launch(Dispatchers.IO) {
            val track = _selectedTrackToMoveVertex.value ?: return@launch
            val idx = _movingVertexIndex.value ?: return@launch
            val pos = _tempVertexPosition.value ?: return@launch

            val pts = (if (track.points.isNotEmpty()) track.points else track.rawPoints).toMutableList()
            if (idx in pts.indices) {
                pts[idx] = pts[idx].copy(latitude = pos.first, longitude = pos.second)
                val dist = TrackGeometryHelper.computeTotalDistanceMeters(pts)
                val updated = track.copy(points = pts, totalDistanceMeters = dist)

                val all = _allTracks.value.toMutableList()
                val tIdx = all.indexOfFirst { it.id == track.id }
                if (tIdx >= 0) {
                    all[tIdx] = updated
                    _allTracks.value = all
                    docStorage.saveTracks(all)
                    _bannerMessage.value = "Sommet #$idx déplacé avec succès !"
                }
            }
            cancelMoveVertex()
        }
    }

    fun cancelMoveVertex() {
        _isMoveVertexMode.value = false
        _selectedTrackToMoveVertex.value = null
        _movingVertexIndex.value = null
        _tempVertexPosition.value = null
    }

    // --- RENOMMAGE & PHOTOS DE TRAJET ---

    fun updateTrackName(trackId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = _allTracks.value.toMutableList()
            val idx = all.indexOfFirst { it.id == trackId }
            if (idx >= 0) {
                val updated = all[idx].copy(name = newName)
                all[idx] = updated
                _allTracks.value = all
                docStorage.saveTracks(all)
                if (_selectedTrackForDetail.value?.id == trackId) {
                    _selectedTrackForDetail.value = updated
                }
                _bannerMessage.value = "Nom du trajet mis à jour : $newName"
            }
        }
    }

    fun addTrackPhoto(trackId: String, photoPath: String, lat: Double, lon: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = _allTracks.value.toMutableList()
            val idx = all.indexOfFirst { it.id == trackId }
            if (idx >= 0) {
                val track = all[idx]
                val currentPhotos = track.photos.toMutableList()
                val photo = com.example.data.storage.TrackPhoto(
                    photoPath = photoPath,
                    latitude = lat,
                    longitude = lon
                )
                currentPhotos.add(photo)
                val updated = track.copy(photos = currentPhotos)
                all[idx] = updated
                _allTracks.value = all
                docStorage.saveTracks(all)
                if (_selectedTrackForDetail.value?.id == trackId) {
                    _selectedTrackForDetail.value = updated
                }
                _bannerMessage.value = "Photo enregistrée le long du trajet $trackId"
            }
        }
    }

    fun deleteTrackPhoto(trackId: String, photoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = _allTracks.value.toMutableList()
            val idx = all.indexOfFirst { it.id == trackId }
            if (idx >= 0) {
                val track = all[idx]
                val photo = track.photos.firstOrNull { it.id == photoId }
                if (photo != null) {
                    com.example.data.photo.PhotoStorageManager.deletePhoto(photo.photoPath)
                }
                val currentPhotos = track.photos.filter { it.id != photoId }
                val updated = track.copy(photos = currentPhotos)
                all[idx] = updated
                _allTracks.value = all
                docStorage.saveTracks(all)
                if (_selectedTrackForDetail.value?.id == trackId) {
                    _selectedTrackForDetail.value = updated
                }
                _bannerMessage.value = "Photo supprimée du trajet"
            }
        }
    }

    fun deleteTrack(trackId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = _allTracks.value.filter { it.id != trackId }
            _allTracks.value = all
            docStorage.saveTracks(all)
            _selectedTrackForDetail.value = null
            _bannerMessage.value = "Tracé supprimé"
        }
    }

    // --- LIAISONS / CÂBLES ---

    fun createFiberLink(
        fromNode: FtthNodeEntity,
        toNode: FtthNodeEntity,
        cableType: String,
        installationType: String,
        capacityFO: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val dist = TrackGeometryHelper.calculateDistanceMeters(
                fromNode.latitude, fromNode.longitude,
                toNode.latitude, toNode.longitude
            )
            val linkId = "LNK-${System.currentTimeMillis() % 10000}"
            val link = FtthLinkEntity(
                id = linkId,
                fromNodeId = fromNode.id,
                toNodeId = toNode.id,
                cableType = cableType,
                installationType = installationType,
                capacityFO = capacityFO,
                lengthMeters = (dist * 10).toInt() / 10.0,
                status = NodeStatus.EXISTANT
            )
            val list = _rawLinks.value.toMutableList()
            list.add(link)
            _rawLinks.value = list
            docStorage.saveLinks(list)

            _bannerMessage.value = "Liaison fibre ${link.id} enregistrée (${link.lengthMeters.toInt()}m)"
            _showConnectCableDialog.value = false
            _pendingLinkNodes.value = null
        }
    }

    // --- DIALOGUES & ACTIONS INTERFACE ---

    fun selectNode(node: FtthNodeEntity?) {
        if (_isCableDrawingMode.value && node != null) {
            val first = _cableFirstNode.value
            if (first == null) {
                _cableFirstNode.value = node
                _bannerMessage.value = "Sélectionnez le second nœud à relier"
            } else if (first.id != node.id) {
                _pendingLinkNodes.value = Pair(first, node)
                _showConnectCableDialog.value = true
                _cableFirstNode.value = null
                _isCableDrawingMode.value = false
            }
        } else {
            _selectedNode.value = node
        }
    }

    fun openTrackDetail(track: StoredTrack) {
        _selectedTrackForDetail.value = track
        _showTracksListDialog.value = false
    }

    fun closeTrackDetail() {
        _selectedTrackForDetail.value = null
    }

    fun openTracksList() {
        _showTracksListDialog.value = true
    }

    fun dismissTracksList() {
        _showTracksListDialog.value = false
    }

    fun openNodesList() {
        _showNodesListDialog.value = true
    }

    fun dismissNodesList() {
        _showNodesListDialog.value = false
    }

    fun openProjectFolder() {
        reloadPersistedData()
        _showProjectFolderDialog.value = true
    }

    fun dismissProjectFolder() {
        _showProjectFolderDialog.value = false
    }

    fun reloadPersistedData() {
        viewModelScope.launch(Dispatchers.IO) {
            val loadedNodes = docStorage.loadNodes()
            val loadedLinks = docStorage.loadLinks()
            val loadedTracks = docStorage.loadTracks()
            _rawNodes.value = loadedNodes
            _rawLinks.value = loadedLinks
            _allTracks.value = loadedTracks
        }
    }

    fun selectLinkForDetail(link: FtthLinkEntity?) {
        _selectedLinkForDetail.value = link
    }

    fun openLinkDetail(link: FtthLinkEntity) {
        _selectedLinkForDetail.value = link
    }

    fun dismissLinkDetail() {
        _selectedLinkForDetail.value = null
    }

    fun saveLink(link: FtthLinkEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = _rawLinks.value.toMutableList()
            val idx = list.indexOfFirst { it.id == link.id }
            if (idx >= 0) {
                list[idx] = link
            } else {
                list.add(link)
            }
            _rawLinks.value = list
            docStorage.saveLinks(list)
            _selectedLinkForDetail.value = null
            _bannerMessage.value = "Liaison ${link.id} mise à jour"
        }
    }

    fun deleteLink(linkId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = _rawLinks.value.filter { it.id != linkId }
            _rawLinks.value = list
            docStorage.saveLinks(list)
            _selectedLinkForDetail.value = null
            _bannerMessage.value = "Liaison $linkId supprimée"
        }
    }

    fun dismissAddNodeDialog() {
        _showAddNodeDialog.value = false
        _stakedPositionForForm.value = null
    }

    fun toggleCableDrawingMode() {
        val willBe = !_isCableDrawingMode.value
        _isCableDrawingMode.value = willBe
        _cableFirstNode.value = null
        _bannerMessage.value = if (willBe) "Mode Câblage : Touchez le 1er nœud puis le 2ème" else null
    }

    fun setFilterState(state: MapFilterState) { _filterState.value = state }
    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun setMapLayer(layer: MapLayerType) { _activeMapLayer.value = layer }
    fun openFilterSheet() { _showFilterSheet.value = true }
    fun dismissFilterSheet() { _showFilterSheet.value = false }
    fun openSyncLogSheet() { _showSyncLogSheet.value = true }
    fun dismissSyncLogSheet() { _showSyncLogSheet.value = false }
    fun openExportDialog() { _showExportDialog.value = true }
    fun dismissExportDialog() { _showExportDialog.value = false }
    fun dismissConnectCableDialog() { _showConnectCableDialog.value = false }
    fun dismissBanner() { _bannerMessage.value = null }

    fun resetDemoData() {
        viewModelScope.launch(Dispatchers.IO) {
            _rawNodes.value = com.example.data.local.DefaultFtthData.getDefaultNodes()
            _rawLinks.value = com.example.data.local.DefaultFtthData.getDefaultLinks()
            docStorage.saveNodes(_rawNodes.value)
            docStorage.saveLinks(_rawLinks.value)
            _bannerMessage.value = "Données réinitialisées dans Documents/Releve-Terrain"
        }
    }

    // --- EXPORTS REELS DEPUIS LES FICHIERS DU DOSSIER ---

    fun exportGeoJson(context: Context, onComplete: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = RealExportService.exportGeoJson(context, _rawNodes.value, _rawLinks.value)
            withContext(Dispatchers.Main) { onComplete(file) }
        }
    }

    fun exportCsv(context: Context, onComplete: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = RealExportService.exportCsv(context, _rawNodes.value)
            withContext(Dispatchers.Main) { onComplete(file) }
        }
    }

    fun exportKml(context: Context, onComplete: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = RealExportService.exportKml(context, _rawNodes.value, _rawLinks.value)
            withContext(Dispatchers.Main) { onComplete(file) }
        }
    }

    fun exportKmz(context: Context, onComplete: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = RealExportService.exportKmz(context, _rawNodes.value, _rawLinks.value)
            withContext(Dispatchers.Main) { onComplete(file) }
        }
    }

    fun exportCompleteZip(context: Context, onComplete: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = RealExportService.exportCompleteZip(context, _rawNodes.value, _rawLinks.value)
            withContext(Dispatchers.Main) { onComplete(file) }
        }
    }

    fun exportCompleteZipAndShare(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _bannerMessage.value = "Génération du dossier ZIP complet en cours..."
            val file = RealExportService.exportCompleteZip(context, _rawNodes.value, _rawLinks.value)
            withContext(Dispatchers.Main) {
                _bannerMessage.value = "Dossier ZIP généré (${file.length() / 1024} Ko)"
                RealExportService.shareFile(context, file, "application/zip", "Partager le dossier ZIP complet")
            }
        }
    }

    fun exportKmzAndShare(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _bannerMessage.value = "Génération du fichier KMZ Google Earth..."
            val file = RealExportService.exportKmz(context, _rawNodes.value, _rawLinks.value)
            withContext(Dispatchers.Main) {
                _bannerMessage.value = "KMZ généré (${file.length() / 1024} Ko)"
                RealExportService.shareFile(context, file, "application/vnd.google-earth.kmz", "Partager le fichier KMZ Google Earth")
            }
        }
    }

    fun exportGeoJsonAndShare(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _bannerMessage.value = "Génération du fichier GeoJSON SIG..."
            val file = RealExportService.exportGeoJson(context, _rawNodes.value, _rawLinks.value)
            withContext(Dispatchers.Main) {
                _bannerMessage.value = "GeoJSON généré (${file.length() / 1024} Ko)"
                RealExportService.shareFile(context, file, "application/geo+json", "Partager le fichier GeoJSON SIG")
            }
        }
    }

    fun shareExportFile(context: Context, file: File, mimeType: String) {
        RealExportService.shareFile(context, file, mimeType)
    }

    override fun onCleared() {
        super.onCleared()
        gpsService.stop()
    }
}
