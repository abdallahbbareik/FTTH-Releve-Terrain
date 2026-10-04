package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.local.NodeConformity
import com.example.data.local.NodeStatus
import com.example.data.local.SyncLogEntity
import com.example.data.repository.FtthRepository
import com.example.data.repository.RealtimeSyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MapLayerType(val label: String) {
    CADASTRE("SIG / Cadastre"),
    SATELLITE("Satellite"),
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
    private val database = AppDatabase.getDatabase(application)
    private val repository = FtthRepository(database.ftthDao(), viewModelScope)

    val syncStatus: StateFlow<RealtimeSyncStatus> = repository.syncStatus
    val isLiveSyncActive: StateFlow<Boolean> = repository.isLiveSyncActive
    val lastSyncTimestamp: StateFlow<Long> = repository.lastSyncTimestamp
    val syncLogs: StateFlow<List<SyncLogEntity>> = repository.allSyncLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawNodes: StateFlow<List<FtthNodeEntity>> = repository.allNodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawLinks: StateFlow<List<FtthLinkEntity>> = repository.allLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI state
    private val _selectedNode = MutableStateFlow<FtthNodeEntity?>(null)
    val selectedNode: StateFlow<FtthNodeEntity?> = _selectedNode.asStateFlow()

    private val _filterState = MutableStateFlow(MapFilterState())
    val filterState: StateFlow<MapFilterState> = _filterState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeMapLayer = MutableStateFlow(MapLayerType.CADASTRE)
    val activeMapLayer: StateFlow<MapLayerType> = _activeMapLayer.asStateFlow()

    // Cable Drawing Mode
    private val _isCableDrawingMode = MutableStateFlow(false)
    val isCableDrawingMode: StateFlow<Boolean> = _isCableDrawingMode.asStateFlow()

    private val _cableFirstNode = MutableStateFlow<FtthNodeEntity?>(null)
    val cableFirstNode: StateFlow<FtthNodeEntity?> = _cableFirstNode.asStateFlow()

    // Stake New Node at position
    private val _stakedPosition = MutableStateFlow<Pair<Double, Double>?>(null)
    val stakedPosition: StateFlow<Pair<Double, Double>?> = _stakedPosition.asStateFlow()

    // Dialog sheets
    private val _showAddNodeDialog = MutableStateFlow(false)
    val showAddNodeDialog: StateFlow<Boolean> = _showAddNodeDialog.asStateFlow()

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

    // Technician current GPS center
    private val _technicianLocation = MutableStateFlow(Pair(48.8566, 2.3522))
    val technicianLocation: StateFlow<Pair<Double, Double>> = _technicianLocation.asStateFlow()

    // User notification banner
    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    // Filtered nodes
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

    fun selectNode(node: FtthNodeEntity?) {
        if (_isCableDrawingMode.value && node != null) {
            val first = _cableFirstNode.value
            if (first == null) {
                _cableFirstNode.value = node
                _bannerMessage.value = "Sélectionnez le second nœud à raccorder par fibre"
            } else if (first.id != node.id) {
                // Trigger cable connection
                _pendingLinkNodes.value = Pair(first, node)
                _showConnectCableDialog.value = true
                _cableFirstNode.value = null
                _isCableDrawingMode.value = false
            }
        } else {
            _selectedNode.value = node
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterState(state: MapFilterState) {
        _filterState.value = state
    }

    fun setMapLayer(layer: MapLayerType) {
        _activeMapLayer.value = layer
    }

    fun toggleCableDrawingMode() {
        val willBeActive = !_isCableDrawingMode.value
        _isCableDrawingMode.value = willBeActive
        _cableFirstNode.value = null
        _bannerMessage.value = if (willBeActive) {
            "Mode Câblage : Touchez le 1er nœud puis le 2ème pour créer une liaison fibre"
        } else null
    }

    fun startStakingAt(lat: Double, lon: Double) {
        _stakedPosition.value = Pair(lat, lon)
        _showAddNodeDialog.value = true
    }

    fun dismissAddNodeDialog() {
        _showAddNodeDialog.value = false
        _stakedPosition.value = null
    }

    fun dismissFilterSheet() {
        _showFilterSheet.value = false
    }

    fun openFilterSheet() {
        _showFilterSheet.value = true
    }

    fun openSyncLogSheet() {
        _showSyncLogSheet.value = true
    }

    fun dismissSyncLogSheet() {
        _showSyncLogSheet.value = false
    }

    fun openExportDialog() {
        _showExportDialog.value = true
    }

    fun dismissExportDialog() {
        _showExportDialog.value = false
    }

    fun dismissConnectCableDialog() {
        _showConnectCableDialog.value = false
        _pendingLinkNodes.value = null
    }

    fun dismissBanner() {
        _bannerMessage.value = null
    }

    fun toggleLiveSync() {
        repository.toggleLiveSync()
    }

    fun triggerManualSync() {
        repository.triggerManualSync()
        _bannerMessage.value = "Synchronisation temps réel déclenchée avec succès"
    }

    fun resetDemoData() {
        viewModelScope.launch {
            repository.resetToDefaultDemo()
            _selectedNode.value = null
            _bannerMessage.value = "Données d'architecture FTTH réinitialisées"
        }
    }

    fun saveNode(node: FtthNodeEntity, isNew: Boolean = false) {
        viewModelScope.launch {
            repository.saveNode(node, isNew)
            _selectedNode.value = node
            _bannerMessage.value = "Nœud ${node.id} (${node.name}) enregistré"
        }
    }

    fun deleteNode(id: String) {
        viewModelScope.launch {
            repository.deleteNode(id)
            if (_selectedNode.value?.id == id) {
                _selectedNode.value = null
            }
            _bannerMessage.value = "Nœud $id supprimé"
        }
    }

    fun createFiberLink(
        fromNode: FtthNodeEntity,
        toNode: FtthNodeEntity,
        cableType: String,
        installationType: String,
        capacityFO: Int
    ) {
        viewModelScope.launch {
            val dist = FtthRepository.calculateDistanceMeters(
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
            repository.saveLink(link)
            _bannerMessage.value = "Liaison fibre ${link.id} créée : ${fromNode.id} ↔ ${toNode.id} (${link.lengthMeters.toInt()}m)"
            _showConnectCableDialog.value = false
            _pendingLinkNodes.value = null
        }
    }

    fun deleteLink(id: String) {
        viewModelScope.launch {
            repository.deleteLink(id)
            _bannerMessage.value = "Liaison $id supprimée"
        }
    }
}
