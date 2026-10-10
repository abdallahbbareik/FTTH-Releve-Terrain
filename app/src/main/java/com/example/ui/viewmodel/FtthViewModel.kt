package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.export.RealExportService
import com.example.data.gps.GpsForegroundService
import com.example.data.gps.GpsLocationData
import com.example.data.gps.GpsLocationService
import com.example.data.gps.GpsStatus
import com.example.data.gps.GpsTrackingBridge
import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.local.NodeConformity
import com.example.data.local.NodeStatus
import com.example.data.local.SyncLogEntity
import com.example.data.storage.DocumentStorageManager
import com.example.data.storage.ProjectInfo
import com.example.data.storage.StoredTrack
import com.example.data.storage.TrackPhoto
import com.example.data.storage.TrackPoint
import com.example.data.util.TrackGeometryHelper
import com.example.ui.components.BlockingTaskState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

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
    val showTracks: Boolean = true,
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

    // Nom et liste des projets dans Documents/Releve-Terrain
    private val _currentProject = MutableStateFlow(docStorage.currentProject)
    val currentProject: StateFlow<String> = _currentProject.asStateFlow()

    private val _allProjects = MutableStateFlow<List<ProjectInfo>>(docStorage.listAllProjects())
    val allProjects: StateFlow<List<ProjectInfo>> = _allProjects.asStateFlow()

    // Entités en mémoire synchronisées avec les fichiers JSON du projet actif
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

    private val _manualTrackPhotos = MutableStateFlow<List<TrackPhoto>>(emptyList())
    val manualTrackPhotos: StateFlow<List<TrackPhoto>> = _manualTrackPhotos.asStateFlow()

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

    // Mode Ajustement & Simplification de tracé (Fenêtre flottante persistante)
    private val _isAdjustTrackMode = MutableStateFlow(false)
    val isAdjustTrackMode: StateFlow<Boolean> = _isAdjustTrackMode.asStateFlow()

    private val _selectedAdjustTrack = MutableStateFlow<StoredTrack?>(null)
    val selectedAdjustTrack: StateFlow<StoredTrack?> = _selectedAdjustTrack.asStateFlow()

    private val _adjustTolerance = MutableStateFlow(5.0)
    val adjustTolerance: StateFlow<Double> = _adjustTolerance.asStateFlow()

    private val _isAdjustStraightenActive = MutableStateFlow(false)
    val isAdjustStraightenActive: StateFlow<Boolean> = _isAdjustStraightenActive.asStateFlow()

    private val _adjustStraightenStartIdx = MutableStateFlow<Int?>(null)
    val adjustStraightenStartIdx: StateFlow<Int?> = _adjustStraightenStartIdx.asStateFlow()

    private val _adjustHistory = mutableListOf<List<TrackPoint>>()
    private val _canUndoAdjust = MutableStateFlow(false)
    val canUndoAdjust: StateFlow<Boolean> = _canUndoAdjust.asStateFlow()

    private val _selectedAdjustVertexIdx = MutableStateFlow<Int?>(null)
    val selectedAdjustVertexIdx: StateFlow<Int?> = _selectedAdjustVertexIdx.asStateFlow()

    private val _isAdjustDeleteActive = MutableStateFlow(false)
    val isAdjustDeleteActive: StateFlow<Boolean> = _isAdjustDeleteActive.asStateFlow()

    private val _isAdjustAddActive = MutableStateFlow(false)
    val isAdjustAddActive: StateFlow<Boolean> = _isAdjustAddActive.asStateFlow()

    private val _isDragAllVerticesMode = MutableStateFlow(false)
    val isDragAllVerticesMode: StateFlow<Boolean> = _isDragAllVerticesMode.asStateFlow()

    private val _liveAdjustDistance = MutableStateFlow<Double?>(null)
    val liveAdjustDistance: StateFlow<Double?> = _liveAdjustDistance.asStateFlow()

    fun selectAdjustVertex(index: Int?) {
        _selectedAdjustVertexIdx.value = index
    }

    fun setDragAllVerticesMode(enabled: Boolean) {
        _isDragAllVerticesMode.value = enabled
        if (enabled) {
            showBanner("Mode Tous les sommets : Glissez n'importe quel sommet pour déplacer tout le tracé")
        } else {
            showBanner("Mode Sommet unique : Glissez un sommet individuel")
        }
    }

    fun shiftAdjustTrackAllVertices(deltaLat: Double, deltaLon: Double, isDragEnd: Boolean = true) {
        val track = _selectedAdjustTrack.value ?: return
        val pts = (if (track.points.isNotEmpty()) track.points else track.rawPoints).toMutableList()
        if (!isDragEnd) {
            for (i in pts.indices) {
                pts[i] = pts[i].copy(latitude = pts[i].latitude + deltaLat, longitude = pts[i].longitude + deltaLon)
            }
            val dist = TrackGeometryHelper.computeTotalDistanceMeters(pts)
            _liveAdjustDistance.value = dist
            return
        }
        _adjustHistory.add(pts.toList())
        _canUndoAdjust.value = true
        for (i in pts.indices) {
            pts[i] = pts[i].copy(latitude = pts[i].latitude + deltaLat, longitude = pts[i].longitude + deltaLon)
        }
        val dist = TrackGeometryHelper.computeTotalDistanceMeters(pts)
        val updated = track.copy(points = pts, totalDistanceMeters = dist)
        _selectedAdjustTrack.value = updated
        _liveAdjustDistance.value = null

        val all = _allTracks.value.toMutableList()
        val idx = all.indexOfFirst { it.id == track.id }
        if (idx >= 0) {
            all[idx] = updated
            _allTracks.value = all
        }
        showBanner("Tracé entier déplacé (${pts.size} sommets translatés)")
    }

    fun toggleAdjustDeleteMode() {
        val sel = _selectedAdjustVertexIdx.value
        if (sel != null) {
            deleteAdjustTrackVertex(sel)
            return
        }
        val current = _isAdjustDeleteActive.value
        _isAdjustDeleteActive.value = !current
        if (!current) {
            _isAdjustAddActive.value = false
            _isAdjustStraightenActive.value = false
            showBanner("Mode Suppression : Touchez un sommet sur la carte pour le supprimer")
        } else {
            showBanner("Mode Suppression désactivé")
        }
    }

    fun toggleAdjustAddMode() {
        val current = _isAdjustAddActive.value
        _isAdjustAddActive.value = !current
        if (!current) {
            _isAdjustDeleteActive.value = false
            _isAdjustStraightenActive.value = false
            val sel = _selectedAdjustVertexIdx.value
            if (sel != null) {
                showBanner("Mode Ajout actif : Touchez la carte pour insérer un sommet après le sommet #${sel + 1}")
            } else {
                showBanner("Mode Ajout actif : Touchez la carte sur le tracé pour insérer un nouveau sommet")
            }
        } else {
            showBanner("Mode Ajout désactivé")
        }
    }

    fun addAdjustTrackVertex(lat: Double, lon: Double) {
        val track = _selectedAdjustTrack.value ?: return
        val pts = (if (track.points.isNotEmpty()) track.points else track.rawPoints).toMutableList()

        _adjustHistory.add(pts.toList())
        _canUndoAdjust.value = true

        val insertIdx = TrackGeometryHelper.findBestInsertionIndex(pts, lat, lon)

        val newPt = TrackPoint(latitude = lat, longitude = lon)
        if (insertIdx >= pts.size) {
            pts.add(newPt)
        } else {
            pts.add(insertIdx, newPt)
        }

        val dist = TrackGeometryHelper.computeTotalDistanceMeters(pts)
        val updated = track.copy(points = pts, totalDistanceMeters = dist)
        _selectedAdjustTrack.value = updated
        _liveAdjustDistance.value = null

        val all = _allTracks.value.toMutableList()
        val tIdx = all.indexOfFirst { it.id == track.id }
        if (tIdx >= 0) {
            all[tIdx] = updated
            _allTracks.value = all
        }

        _selectedAdjustVertexIdx.value = insertIdx
        showBanner("Sommet #${insertIdx + 1} inséré au tracé (${pts.size} sommets au total)")
    }

    fun deleteAdjustTrackVertex(index: Int) {
        val track = _selectedAdjustTrack.value ?: return
        val pts = (if (track.points.isNotEmpty()) track.points else track.rawPoints).toMutableList()
        if (pts.size <= 2) {
            showBanner("Un tracé doit comporter au moins 2 sommets")
            return
        }
        if (index in pts.indices) {
            _adjustHistory.add(pts.toList())
            _canUndoAdjust.value = true
            pts.removeAt(index)
            val dist = TrackGeometryHelper.computeTotalDistanceMeters(pts)
            val updated = track.copy(points = pts, totalDistanceMeters = dist)
            _selectedAdjustTrack.value = updated
            _liveAdjustDistance.value = null

            val all = _allTracks.value.toMutableList()
            val tIdx = all.indexOfFirst { it.id == track.id }
            if (tIdx >= 0) {
                all[tIdx] = updated
                _allTracks.value = all
            }
            _selectedAdjustVertexIdx.value = null
            showBanner("Sommet #${index + 1} supprimé (${pts.size} sommets restants)")
        }
    }

    fun startTrackAdjustment(track: StoredTrack) {
        _isAdjustTrackMode.value = true
        _selectedAdjustTrack.value = track
        _selectedAdjustVertexIdx.value = null
        _isDragAllVerticesMode.value = false
        _liveAdjustDistance.value = null
        _adjustTolerance.value = 5.0
        _isAdjustStraightenActive.value = false
        _adjustStraightenStartIdx.value = null
        _isAdjustDeleteActive.value = false
        _isAdjustAddActive.value = false
        _adjustHistory.clear()
        val curPts = if (track.points.isNotEmpty()) track.points else track.rawPoints
        _adjustHistory.add(curPts)
        _canUndoAdjust.value = false

        if (curPts.isNotEmpty()) {
            val mid = curPts[curPts.size / 2]
            _mapFocusTarget.value = Pair(mid.latitude, mid.longitude)
        }
        showBanner("Modification tracé : Glissez directement les sommets sur la carte")
    }

    fun setAdjustTolerance(tolerance: Double) {
        _adjustTolerance.value = tolerance
    }

    fun updateAdjustTrackVertex(index: Int, lat: Double, lon: Double, isDragEnd: Boolean = true) {
        val track = _selectedAdjustTrack.value ?: return
        val pts = (if (track.points.isNotEmpty()) track.points else track.rawPoints).toMutableList()
        if (index in pts.indices) {
            if (!isDragEnd) {
                pts[index] = pts[index].copy(latitude = lat, longitude = lon)
                val dist = TrackGeometryHelper.computeTotalDistanceMeters(pts)
                _liveAdjustDistance.value = dist
                return
            }
            _adjustHistory.add(pts.toList())
            _canUndoAdjust.value = true
            pts[index] = pts[index].copy(latitude = lat, longitude = lon)
            val dist = TrackGeometryHelper.computeTotalDistanceMeters(pts)
            val updated = track.copy(points = pts, totalDistanceMeters = dist)
            _selectedAdjustTrack.value = updated
            _liveAdjustDistance.value = null

            val all = _allTracks.value.toMutableList()
            val idx = all.indexOfFirst { it.id == track.id }
            if (idx >= 0) {
                all[idx] = updated
                _allTracks.value = all
            }
            showBanner("Sommet #${index + 1} repositionné (${String.format(Locale.FRANCE, "%.1f m", dist)})")
        }
    }

    fun applyCurrentTrackSimplification() {
        val track = _selectedAdjustTrack.value ?: return
        val currentPoints = if (track.points.isNotEmpty()) track.points else track.rawPoints
        if (currentPoints.size <= 2) {
            showBanner("Le tracé a déjà le nombre minimum de sommets")
            return
        }
        _adjustHistory.add(currentPoints)
        _canUndoAdjust.value = true

        val simplified = TrackGeometryHelper.simplifyRamerDouglasPeucker(currentPoints, _adjustTolerance.value)
        val reduced = currentPoints.size - simplified.size
        val dist = TrackGeometryHelper.computeTotalDistanceMeters(simplified)
        val updated = track.copy(
            points = simplified,
            isSimplified = true,
            toleranceMeters = _adjustTolerance.value,
            totalDistanceMeters = dist
        )
        _selectedAdjustTrack.value = updated
        val all = _allTracks.value.toMutableList()
        val idx = all.indexOfFirst { it.id == track.id }
        if (idx >= 0) {
            all[idx] = updated
            _allTracks.value = all
        }
        showBanner("Tracé simplifié : ${currentPoints.size} → ${simplified.size} sommets (-$reduced sommets)")
    }

    fun toggleAdjustStraightenMode() {
        val current = _isAdjustStraightenActive.value
        _isAdjustStraightenActive.value = !current
        _adjustStraightenStartIdx.value = null
        if (!current) {
            _isAdjustDeleteActive.value = false
            _isAdjustAddActive.value = false
            showBanner("Redresser : Touchez le sommet de départ (il devient orange), puis le sommet d'arrivée")
        } else {
            showBanner("Outil Redresser désactivé")
        }
    }

    fun onAdjustVertexClicked(index: Int) {
        if (_isAdjustDeleteActive.value) {
            deleteAdjustTrackVertex(index)
            return
        }
        if (!_isAdjustStraightenActive.value) return
        val startIdx = _adjustStraightenStartIdx.value
        if (startIdx == null) {
            _adjustStraightenStartIdx.value = index
            showBanner("Sommet de départ #${index + 1} sélectionné (orange). Touchez le sommet d'arrivée.")
        } else if (startIdx == index) {
            _adjustStraightenStartIdx.value = null
            showBanner("Sélection annulée. Touchez le sommet de départ.")
        } else {
            val track = _selectedAdjustTrack.value ?: return
            val pts = if (track.points.isNotEmpty()) track.points else track.rawPoints
            _adjustHistory.add(pts)
            _canUndoAdjust.value = true

            val (minIdx, maxIdx) = if (startIdx < index) Pair(startIdx, index) else Pair(index, startIdx)
            val straightened = TrackGeometryHelper.straightenBetweenVertices(pts, minIdx, maxIdx)
            val dist = TrackGeometryHelper.computeTotalDistanceMeters(straightened)
            val updated = track.copy(points = straightened, totalDistanceMeters = dist)
            _selectedAdjustTrack.value = updated

            val all = _allTracks.value.toMutableList()
            val tIdx = all.indexOfFirst { it.id == track.id }
            if (tIdx >= 0) {
                all[tIdx] = updated
                _allTracks.value = all
            }

            _adjustStraightenStartIdx.value = null
            _isAdjustStraightenActive.value = false
            showBanner("Tronçon #${minIdx + 1} à #${maxIdx + 1} redressé en ligne droite !")
        }
    }

    fun undoLastAdjustAction() {
        if (_adjustHistory.isNotEmpty()) {
            val prevPoints = _adjustHistory.removeAt(_adjustHistory.lastIndex)
            val track = _selectedAdjustTrack.value ?: return
            val dist = TrackGeometryHelper.computeTotalDistanceMeters(prevPoints)
            val updated = track.copy(
                points = prevPoints,
                totalDistanceMeters = dist,
                isSimplified = prevPoints.size < track.rawPoints.size
            )
            _selectedAdjustTrack.value = updated
            val all = _allTracks.value.toMutableList()
            val idx = all.indexOfFirst { it.id == track.id }
            if (idx >= 0) {
                all[idx] = updated
                _allTracks.value = all
            }
            _canUndoAdjust.value = _adjustHistory.isNotEmpty()
            _adjustStraightenStartIdx.value = null
            _selectedAdjustVertexIdx.value = null
            _liveAdjustDistance.value = null
            showBanner("Action annulée ↶ (${prevPoints.size} sommets)")
        } else {
            restoreOriginalTrack()
        }
    }

    fun restoreOriginalTrack() {
        val track = _selectedAdjustTrack.value ?: return
        if (track.rawPoints.isEmpty()) return
        _adjustHistory.add(track.points)
        _canUndoAdjust.value = true
        val updated = track.copy(
            points = track.rawPoints,
            isSimplified = false,
            totalDistanceMeters = TrackGeometryHelper.computeTotalDistanceMeters(track.rawPoints)
        )
        _selectedAdjustTrack.value = updated
        _selectedAdjustVertexIdx.value = null
        _liveAdjustDistance.value = null
        val all = _allTracks.value.toMutableList()
        val idx = all.indexOfFirst { it.id == track.id }
        if (idx >= 0) {
            all[idx] = updated
            _allTracks.value = all
        }
        showBanner("Tracé d'origine rétabli (${track.rawPoints.size} sommets)")
    }

    fun cancelTrackAdjustment() {
        if (_adjustHistory.isNotEmpty()) {
            val initial = _adjustHistory.first()
            val track = _selectedAdjustTrack.value
            if (track != null) {
                val restored = track.copy(
                    points = initial,
                    totalDistanceMeters = TrackGeometryHelper.computeTotalDistanceMeters(initial)
                )
                val all = _allTracks.value.toMutableList()
                val idx = all.indexOfFirst { it.id == track.id }
                if (idx >= 0) {
                    all[idx] = restored
                    _allTracks.value = all
                    viewModelScope.launch(Dispatchers.IO) { docStorage.saveTracks(all) }
                }
            }
        }
        _isAdjustTrackMode.value = false
        _selectedAdjustTrack.value = null
        _selectedAdjustVertexIdx.value = null
        _isDragAllVerticesMode.value = false
        _liveAdjustDistance.value = null
        _isAdjustStraightenActive.value = false
        _adjustStraightenStartIdx.value = null
        _isAdjustDeleteActive.value = false
        _isAdjustAddActive.value = false
        _adjustHistory.clear()
        _canUndoAdjust.value = false
        showBanner("Modification annulée")
    }

    fun confirmTrackAdjustment() {
        val track = _selectedAdjustTrack.value
        if (track != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val all = _allTracks.value.toMutableList()
                val idx = all.indexOfFirst { it.id == track.id }
                if (idx >= 0) {
                    all[idx] = track
                    _allTracks.value = all
                    docStorage.saveTracks(all)
                }
            }
        }
        _isAdjustTrackMode.value = false
        _selectedAdjustTrack.value = null
        _selectedAdjustVertexIdx.value = null
        _isDragAllVerticesMode.value = false
        _liveAdjustDistance.value = null
        _isAdjustStraightenActive.value = false
        _adjustStraightenStartIdx.value = null
        _isAdjustDeleteActive.value = false
        _isAdjustAddActive.value = false
        _adjustHistory.clear()
        _canUndoAdjust.value = false
        showBanner("Modifications du tracé enregistrées avec succès ✓")
    }

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

    private val _mapFocusTarget = MutableStateFlow<Pair<Double, Double>?>(null)
    val mapFocusTarget: StateFlow<Pair<Double, Double>?> = _mapFocusTarget.asStateFlow()

    private val _activeMapLayer = MutableStateFlow(MapLayerType.CADASTRE)
    val activeMapLayer: StateFlow<MapLayerType> = _activeMapLayer.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    // Tâche bloquante avec barre de progression
    private val _blockingTaskState = MutableStateFlow<BlockingTaskState?>(null)
    val blockingTaskState: StateFlow<BlockingTaskState?> = _blockingTaskState.asStateFlow()

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

    val filteredLinks: StateFlow<List<FtthLinkEntity>> = combine(
        rawLinks,
        _filterState
    ) { links, filters ->
        if (filters.showCables) links else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTracks: StateFlow<List<StoredTrack>> = combine(
        allTracks,
        _filterState
    ) { tracks, filters ->
        if (filters.showTracks) tracks else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        gpsService.start()

        // Synchronisation bidirectionnelle avec GpsTrackingBridge pour la résilience totale en arrière-plan et écran éteint
        if (GpsTrackingBridge.isTrackingRunning.value) {
            _activeTrack.value = GpsTrackingBridge.activeTrack.value
            _activeTrackPoints.value = GpsTrackingBridge.activeTrackPoints.value
        }

        viewModelScope.launch {
            GpsTrackingBridge.activeTrack.collect { track ->
                _activeTrack.value = track
            }
        }
        viewModelScope.launch {
            GpsTrackingBridge.activeTrackPoints.collect { pts ->
                _activeTrackPoints.value = pts
            }
        }
        viewModelScope.launch {
            GpsTrackingBridge.stopRequested.collect {
                stopAutoGpsTrack()
            }
        }

        // Disparition automatique des messages de notification après exactement 2 secondes (2000 ms)
        viewModelScope.launch {
            _bannerMessage.collectLatest { msg ->
                if (msg != null) {
                    delay(2000L)
                    _bannerMessage.value = null
                }
            }
        }

        // Fallback d'enregistrement si l'application est au premier plan et que le service n'a pas encore démarré
        viewModelScope.launch {
            userLocation.collect { loc ->
                if (loc != null && _activeTrack.value != null && !GpsTrackingBridge.isTrackingRunning.value) {
                    recordActiveTrackPoint(loc)
                }
            }
        }

        // Restauration et synchronisation automatique des projets et entités depuis Documents/Releve-Terrain
        viewModelScope.launch(Dispatchers.IO) {
            val projects = docStorage.listAllProjects()
            _allProjects.value = projects
            val curProj = docStorage.currentProject
            _currentProject.value = curProj
            val nodes = docStorage.loadNodes(curProj)
            val links = docStorage.loadLinks(curProj)
            val tracks = docStorage.loadTracks(curProj)
            _rawNodes.value = nodes
            _rawLinks.value = links
            _allTracks.value = tracks
            if (nodes.isNotEmpty() || tracks.isNotEmpty() || links.isNotEmpty()) {
                _bannerMessage.value = "Données chargées : ${nodes.size} nœuds, ${tracks.size} infra_lineaire, ${links.size} câbles ($curProj)"
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

    fun saveNode(node: FtthNodeEntity, oldId: String? = null, isNew: Boolean = false, closeSheet: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = _rawNodes.value.toMutableList()
            val targetId = oldId ?: node.id
            val idx = list.indexOfFirst { it.id == targetId }
            if (idx >= 0) {
                list[idx] = node
            } else {
                val existingById = list.indexOfFirst { it.id == node.id }
                if (existingById >= 0) {
                    list[existingById] = node
                } else {
                    list.add(0, node)
                }
            }

            // Si l'identifiant a été modifié, mettre à jour les liaisons (câbles) associées
            if (oldId != null && oldId != node.id) {
                val currentLinks = _rawLinks.value
                val updatedLinks = currentLinks.map { link ->
                    when {
                        link.fromNodeId == oldId -> link.copy(fromNodeId = node.id)
                        link.toNodeId == oldId -> link.copy(toNodeId = node.id)
                        else -> link
                    }
                }
                if (updatedLinks != currentLinks) {
                    _rawLinks.value = updatedLinks
                    docStorage.saveLinks(updatedLinks)
                }
            }

            var createdAutoBoitier: FtthNodeEntity? = null

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
                    val updated = existing.copy(
                        latitude = node.latitude,
                        longitude = node.longitude,
                        boitierSupport = support,
                        address = node.address
                    )
                    list[existingIdx] = updated
                    createdAutoBoitier = updated
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
                    createdAutoBoitier = autoBoitier
                }
            }

            _rawNodes.value = list
            docStorage.saveNodes(list)

            if (createdAutoBoitier != null) {
                // Ouverture immédiate du boîtier créé en modification pour permettre à l'utilisateur de saisir ses informations
                _selectedNode.value = createdAutoBoitier
                _mapFocusTarget.value = Pair(createdAutoBoitier.latitude, createdAutoBoitier.longitude)
                val supportName = if (node.type == FtthNodeType.POTEAU) "Poteau" else if (node.type == FtthNodeType.CHAMBRE) "Chambre" else "Façade"
                _bannerMessage.value = "Nœud ${node.id} enregistré. Boîtier ${createdAutoBoitier.id} créé et ouvert en modification ($supportName) : complétez ses spécifications."
            } else if (closeSheet) {
                _selectedNode.value = null
                _bannerMessage.value = "Nœud ${node.id} enregistré dans Documents/Releve-Terrain"
            } else {
                _selectedNode.value = node
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
        _manualTrackPhotos.value = emptyList()
        showBanner("Mode Tracé Manuel : Touchez un nœud ou la carte pour démarrer le tracé")
    }

    fun addManualTrackPoint(lat: Double, lon: Double) {
        // Détection d'accrochage magnétique si un nœud existant est très proche (< 15 mètres)
        val nearestNode = _rawNodes.value.minByOrNull {
            TrackGeometryHelper.calculateDistanceMeters(it.latitude, it.longitude, lat, lon)
        }
        val isSnapped = nearestNode != null &&
                TrackGeometryHelper.calculateDistanceMeters(nearestNode.latitude, nearestNode.longitude, lat, lon) <= 15.0

        val targetLat = if (isSnapped) nearestNode!!.latitude else lat
        val targetLon = if (isSnapped) nearestNode!!.longitude else lon

        val current = _manualTrackPoints.value.toMutableList()
        current.add(TrackPoint(targetLat, targetLon))
        _manualTrackPoints.value = current

        if (isSnapped) {
            val node = nearestNode!!
            val nameDisplay = if (node.name.isNotBlank()) " (${node.name})" else " (${node.type.label})"
            showBanner("Tracé accroché au nœud ${node.id}$nameDisplay")
        }
    }

    fun undoLastManualTrackPoint() {
        val current = _manualTrackPoints.value.toMutableList()
        if (current.isNotEmpty()) {
            current.removeAt(current.size - 1)
            _manualTrackPoints.value = current
        }
    }

    fun addManualTrackPhoto(photoPath: String) {
        val currentPhotos = _manualTrackPhotos.value.toMutableList()
        val userLoc = userLocation.value
        val lat = userLoc?.latitude ?: _manualTrackPoints.value.lastOrNull()?.latitude ?: 34.0
        val lon = userLoc?.longitude ?: _manualTrackPoints.value.lastOrNull()?.longitude ?: 9.5375
        val photo = TrackPhoto(
            photoPath = photoPath,
            latitude = lat,
            longitude = lon
        )
        currentPhotos.add(photo)
        _manualTrackPhotos.value = currentPhotos
        _bannerMessage.value = "Photo ajoutée au tracé manuel (${currentPhotos.size} photos)"
    }

    fun saveManualTrack(name: String = "Tracé Manuel ${System.currentTimeMillis() % 10000}") {
        viewModelScope.launch(Dispatchers.IO) {
            val pts = _manualTrackPoints.value
            if (pts.size < 2) return@launch

            val dist = TrackGeometryHelper.computeTotalDistanceMeters(pts)
            val photos = _manualTrackPhotos.value
            val track = StoredTrack(
                id = "TRK-${System.currentTimeMillis()}",
                name = name,
                type = "GC",
                etat = "Conforme",
                conduitAudit = "Libres",
                conduitType = "PEHD",
                conduitCount = 1,
                conduitDiameters = listOf("Ø 40"),
                startTime = System.currentTimeMillis(),
                endTime = System.currentTimeMillis(),
                totalDistanceMeters = dist,
                rawPoints = pts,
                points = pts,
                photos = photos,
                isSimplified = false,
                isActive = false
            )
            val all = _allTracks.value.toMutableList()
            all.add(0, track)
            _allTracks.value = all
            docStorage.saveTracks(all)

            _isManualTrackMode.value = false
            _manualTrackPoints.value = emptyList()
            _manualTrackPhotos.value = emptyList()
            _selectedTrackForDetail.value = track
            _bannerMessage.value = "Infra linéaire '$name' (${pts.size} sommets, ${photos.size} photos) enregistrée. Fiche ouverte pour configuration."
        }
    }

    fun cancelManualTrack() {
        _isManualTrackMode.value = false
        _manualTrackPoints.value = emptyList()
        _manualTrackPhotos.value = emptyList()
    }

    // --- TRACE GPS AUTOMATIQUE ---

    fun startAutoGpsTrack(name: String = "Infra linéaire GPS ${System.currentTimeMillis() % 10000}") {
        val track = StoredTrack(
            id = "INF-${System.currentTimeMillis()}",
            name = name,
            type = "GC",
            etat = "Conforme",
            conduitAudit = "Libres",
            conduitType = "PEHD",
            conduitCount = 1,
            conduitDiameters = listOf("Ø 40"),
            startTime = System.currentTimeMillis(),
            isActive = true
        )
        _activeTrack.value = track
        _activeTrackPoints.value = emptyList()
        GpsTrackingBridge.startSession(track)
        GpsForegroundService.startTracking(getApplication(), name)
        _bannerMessage.value = "Enregistrement infra linéaire démarré (actif en arrière-plan et écran verrouillé)"
    }

    fun addActiveTrackPhoto(photoPath: String) {
        val active = _activeTrack.value ?: return
        val userLoc = userLocation.value
        val lat = userLoc?.latitude ?: _activeTrackPoints.value.lastOrNull()?.latitude ?: 34.0
        val lon = userLoc?.longitude ?: _activeTrackPoints.value.lastOrNull()?.longitude ?: 9.5375
        val photo = TrackPhoto(
            photoPath = photoPath,
            latitude = lat,
            longitude = lon
        )
        GpsTrackingBridge.addPhoto(photo)
        val count = (_activeTrack.value?.photos?.size ?: 0)
        _bannerMessage.value = "Photo géoréférencée ajoutée au tracé ($count photos)"
    }

    fun cancelActiveTrack() {
        GpsForegroundService.stopTracking(getApplication())
        GpsTrackingBridge.stopSession()
        _activeTrack.value = null
        _activeTrackPoints.value = emptyList()
        _bannerMessage.value = "Enregistrement du tracé annulé"
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
            val active = _activeTrack.value ?: GpsTrackingBridge.activeTrack.value ?: return@launch
            GpsForegroundService.stopTracking(getApplication())
            val pts = _activeTrackPoints.value.ifEmpty { GpsTrackingBridge.activeTrackPoints.value }
            val finished = active.copy(
                endTime = System.currentTimeMillis(),
                isActive = false,
                rawPoints = pts,
                points = pts,
                totalDistanceMeters = TrackGeometryHelper.computeTotalDistanceMeters(pts)
            )
            GpsTrackingBridge.stopSession()
            val all = _allTracks.value.toMutableList()
            all.add(0, finished)
            _allTracks.value = all
            docStorage.saveTracks(all)

            _activeTrack.value = null
            _activeTrackPoints.value = emptyList()
            _selectedTrackForDetail.value = finished
            _bannerMessage.value = "Trace GPS terminée (${pts.size} points, ${finished.photos.size} photos). Fiche ouverte pour configuration."
        }
    }

    fun saveTrack(updatedTrack: StoredTrack) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = _allTracks.value.toMutableList()
            val idx = all.indexOfFirst { it.id == updatedTrack.id }
            if (idx >= 0) {
                all[idx] = updatedTrack
            } else {
                all.add(0, updatedTrack)
            }
            _allTracks.value = all
            docStorage.saveTracks(all)
            if (_selectedTrackForDetail.value?.id == updatedTrack.id) {
                _selectedTrackForDetail.value = updatedTrack
            }
            _bannerMessage.value = "Trajet '${updatedTrack.name}' (${updatedTrack.type}) mis à jour"
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
        capacityFO: Int,
        associatedTrackId: String = ""
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
                associatedTrackId = associatedTrackId,
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
        if (node == null) {
            _selectedNode.value = null
            return
        }

        // 1. Mode Tracé Manuel : accrochage immédiat du tracé au nœud (bloque l'ouverture de la fiche)
        if (_isManualTrackMode.value) {
            addManualTrackPoint(node.latitude, node.longitude)
            val nameDisplay = if (node.name.isNotBlank()) " (${node.name})" else " (${node.type.label})"
            showBanner("Tracé accroché au nœud ${node.id}$nameDisplay")
            return
        }

        // 2. Mode Prise de Trace GPS Automatique : accrochage de la trace au nœud (bloque l'ouverture de la fiche)
        if (_activeTrack.value != null) {
            GpsTrackingBridge.addSnapPoint(node.latitude, node.longitude)
            val nameDisplay = if (node.name.isNotBlank()) " (${node.name})" else " (${node.type.label})"
            showBanner("Trace GPS accrochée au nœud ${node.id}$nameDisplay")
            return
        }

        // 3. Mode Câblage
        if (_isCableDrawingMode.value) {
            val first = _cableFirstNode.value
            if (first == null) {
                _cableFirstNode.value = node
                showBanner("Sélectionnez le second nœud à relier")
            } else if (first.id != node.id) {
                _pendingLinkNodes.value = Pair(first, node)
                _showConnectCableDialog.value = true
                _cableFirstNode.value = null
                _isCableDrawingMode.value = false
            }
            return
        }

        // 4. Bloquer l'ouverture de la fiche si une autre action cartographique spécifique est en cours
        if (_movingNode.value != null || _isMoveVertexMode.value || _isStraightenMode.value ||
            _isPickOnMapMode.value || _pendingStakePosition.value != null
        ) {
            return
        }

        // 5. Mode Normal : ouverture de la fiche de consultation/modification du nœud
        _selectedNode.value = node
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
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            val exactMatch = _rawNodes.value.firstOrNull { it.id.equals(trimmed, ignoreCase = true) }
            if (exactMatch != null) {
                _mapFocusTarget.value = Pair(exactMatch.latitude, exactMatch.longitude)
                _bannerMessage.value = "Entité ${exactMatch.id} trouvée : centrage et zoom sur la carte"
            }
        }
    }

    fun zoomAndFocusOnNode(node: FtthNodeEntity) {
        _mapFocusTarget.value = Pair(node.latitude, node.longitude)
        _selectedNode.value = node
        _bannerMessage.value = "Zoom sur ${node.id} (${node.name})"
    }

    fun clearMapFocusTarget() {
        _mapFocusTarget.value = null
    }

    // --- GESTION DES PROJETS FTTH (Documents/Releve-Terrain/<projet>) ---

    fun refreshProjectsList() {
        viewModelScope.launch(Dispatchers.IO) {
            _allProjects.value = docStorage.listAllProjects()
            _currentProject.value = docStorage.currentProject
        }
    }

    fun switchProject(projectName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            docStorage.setCurrentProject(projectName)
            _currentProject.value = docStorage.currentProject

            val nodes = docStorage.loadNodes(projectName)
            val links = docStorage.loadLinks(projectName)
            val tracks = docStorage.loadTracks(projectName)

            _rawNodes.value = nodes
            _rawLinks.value = links
            _allTracks.value = tracks
            _allProjects.value = docStorage.listAllProjects()

            _selectedNode.value = null
            _selectedTrackForDetail.value = null
            _selectedLinkForDetail.value = null

            if (nodes.isNotEmpty()) {
                val first = nodes.first()
                _mapFocusTarget.value = Pair(first.latitude, first.longitude)
            }
            _bannerMessage.value = "Projet '$projectName' ouvert (${nodes.size} nœuds, ${tracks.size} infra_lineaire, ${links.size} câbles)"
        }
    }

    fun createNewProject(projectName: String, copyCurrent: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = docStorage.createProject(projectName, copyCurrent)
            if (success) {
                switchProject(projectName)
                _bannerMessage.value = "Nouveau projet '$projectName' créé avec succès !"
            }
        }
    }

    fun deleteProject(projectName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _blockingTaskState.value = BlockingTaskState(
                isRunning = true,
                title = "Suppression du projet",
                message = "Préparation de la suppression de '$projectName'...",
                progress = 0.10f
            )
            try {
                docStorage.deleteProjectFast(projectName) { progress, message ->
                    _blockingTaskState.value = BlockingTaskState(
                        isRunning = true,
                        title = "Suppression du projet",
                        message = message,
                        progress = progress
                    )
                }

                _blockingTaskState.value = BlockingTaskState(
                    isRunning = true,
                    title = "Suppression du projet",
                    message = "Chargement des données du projet actif...",
                    progress = 0.90f
                )

                val nextProject = docStorage.currentProject
                _currentProject.value = nextProject
                val nodes = docStorage.loadNodes(nextProject)
                val links = docStorage.loadLinks(nextProject)
                val tracks = docStorage.loadTracks(nextProject)

                _rawNodes.value = nodes
                _rawLinks.value = links
                _allTracks.value = tracks
                _allProjects.value = docStorage.listAllProjects()

                _blockingTaskState.value = BlockingTaskState(
                    isRunning = true,
                    title = "Suppression terminée",
                    message = "Projet '$projectName' supprimé avec succès.",
                    progress = 1.0f
                )
                delay(300L)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _blockingTaskState.value = null
                _bannerMessage.value = "Projet '$projectName' supprimé"
            }
        }
    }

    fun renameProject(oldName: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = docStorage.renameProject(oldName, newName)
            if (success) {
                _currentProject.value = docStorage.currentProject
                _allProjects.value = docStorage.listAllProjects()
                _bannerMessage.value = "Projet renommé en '$newName'"
            }
        }
    }

    fun setMapLayer(layer: MapLayerType) { _activeMapLayer.value = layer }
    fun openFilterSheet() { _showFilterSheet.value = true }
    fun dismissFilterSheet() { _showFilterSheet.value = false }
    fun openSyncLogSheet() { _showSyncLogSheet.value = true }
    fun dismissSyncLogSheet() { _showSyncLogSheet.value = false }
    fun openExportDialog() { _showExportDialog.value = true }
    fun dismissExportDialog() { _showExportDialog.value = false }
    fun dismissConnectCableDialog() { _showConnectCableDialog.value = false }
    fun dismissBanner() { _bannerMessage.value = null }
    fun showBanner(message: String) {
        if (_bannerMessage.value == message) {
            _bannerMessage.value = null
        }
        _bannerMessage.value = message
    }

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
