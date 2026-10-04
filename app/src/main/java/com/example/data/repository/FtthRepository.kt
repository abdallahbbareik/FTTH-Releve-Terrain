package com.example.data.repository

import com.example.data.local.FtthDao
import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.local.SyncLogEntity
import com.example.data.local.SyncState
import com.example.data.local.DefaultFtthData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class RealtimeSyncStatus(val label: String) {
    ONLINE_SYNCED("En ligne • Synchronisé"),
    SYNCING("Synchronisation en cours..."),
    OFFLINE("Mode Hors-Ligne (Données locales sécurisées)"),
    MODIFICATIONS_PENDING("Modifications locales en attente d'envoi")
}

class FtthRepository(
    private val dao: FtthDao,
    private val appScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    val allNodes: Flow<List<FtthNodeEntity>> = dao.getAllNodes()
    val allLinks: Flow<List<FtthLinkEntity>> = dao.getAllLinks()
    val allSyncLogs: Flow<List<SyncLogEntity>> = dao.getAllSyncLogs()

    private val _syncStatus = MutableStateFlow(RealtimeSyncStatus.ONLINE_SYNCED)
    val syncStatus: StateFlow<RealtimeSyncStatus> = _syncStatus.asStateFlow()

    private val _isLiveSyncActive = MutableStateFlow(true)
    val isLiveSyncActive: StateFlow<Boolean> = _isLiveSyncActive.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(System.currentTimeMillis())
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    init {
        // Preload default data if DB is empty
        appScope.launch {
            val count = dao.getNodeCount()
            if (count == 0) {
                dao.insertNodes(DefaultFtthData.getDefaultNodes())
                dao.insertLinks(DefaultFtthData.getDefaultLinks())
                DefaultFtthData.getDefaultLogs().forEach { dao.insertSyncLog(it) }
            }
            startRealtimeSyncDaemon()
        }
    }

    private fun startRealtimeSyncDaemon() {
        appScope.launch {
            var counter = 0
            while (true) {
                delay(12000) // periodic pulse check
                if (_isLiveSyncActive.value) {
                    counter++
                    // Every 24s simulate a collaborative remote sync event from another team technician
                    if (counter % 2 == 0) {
                        simulateRemoteTeamActivity()
                    }
                }
            }
        }
    }

    private suspend fun simulateRemoteTeamActivity() {
        val remoteEvents = listOf(
            Triple("Tech-02 (Karim M.)", "PBO-402", "Mesure optique effectuée : -18.9 dBm (OK)"),
            Triple("Tech-03 (Sophie L.)", "POT-101", "Contrôle charge télécom validé par le bureau d'études"),
            Triple("Superviseur SIG", "CH-201", "Génie civil validé dans le système centralisé"),
            Triple("Tech-04 (Julien D.)", "VIL-602", "Fourreau vérifié : passage d'aiguille réussi")
        )
        val event = remoteEvents.random()
        dao.insertSyncLog(
            SyncLogEntity(
                timestamp = System.currentTimeMillis(),
                technician = event.first,
                action = "SYNC_RESEAU",
                nodeId = event.second,
                details = event.third
            )
        )
        _lastSyncTimestamp.value = System.currentTimeMillis()
    }

    suspend fun saveNode(node: FtthNodeEntity, isNew: Boolean = false) {
        val updatedNode = node.copy(
            updatedAt = System.currentTimeMillis(),
            syncState = if (_isLiveSyncActive.value) SyncState.SYNCING else SyncState.PENDING_UPLOAD
        )
        dao.insertNode(updatedNode)

        dao.insertSyncLog(
            SyncLogEntity(
                timestamp = System.currentTimeMillis(),
                technician = "Tech-01 (Moi)",
                action = if (isNew) "CREATION_NOEUD" else "MODIFICATION",
                nodeId = node.id,
                details = "${node.type.label} : ${node.name} (${node.status.label})"
            )
        )

        if (_isLiveSyncActive.value) {
            _syncStatus.value = RealtimeSyncStatus.SYNCING
            appScope.launch {
                delay(1200) // simulate network round-trip to SIG backend
                dao.insertNode(updatedNode.copy(syncState = SyncState.SYNCED))
                _syncStatus.value = RealtimeSyncStatus.ONLINE_SYNCED
                _lastSyncTimestamp.value = System.currentTimeMillis()
            }
        } else {
            _syncStatus.value = RealtimeSyncStatus.MODIFICATIONS_PENDING
        }
    }

    suspend fun deleteNode(id: String) {
        dao.deleteNodeById(id)
        dao.insertSyncLog(
            SyncLogEntity(
                timestamp = System.currentTimeMillis(),
                technician = "Tech-01 (Moi)",
                action = "SUPPRESSION",
                nodeId = id,
                details = "Suppression du nœud $id du tracé de piquetage"
            )
        )
    }

    suspend fun saveLink(link: FtthLinkEntity) {
        dao.insertLink(link)
        dao.insertSyncLog(
            SyncLogEntity(
                timestamp = System.currentTimeMillis(),
                technician = "Tech-01 (Moi)",
                action = "NOUVEAU_CABLE",
                nodeId = "${link.fromNodeId} -> ${link.toNodeId}",
                details = "Câble ${link.cableType} ${link.capacityFO}FO (${link.lengthMeters.toInt()}m - ${link.installationType})"
            )
        )
    }

    suspend fun deleteLink(id: String) {
        dao.deleteLinkById(id)
    }

    fun toggleLiveSync() {
        val newState = !_isLiveSyncActive.value
        _isLiveSyncActive.value = newState
        if (newState) {
            triggerManualSync()
        } else {
            _syncStatus.value = RealtimeSyncStatus.OFFLINE
        }
    }

    fun triggerManualSync() {
        appScope.launch {
            _syncStatus.value = RealtimeSyncStatus.SYNCING
            delay(1500)
            dao.insertSyncLog(
                SyncLogEntity(
                    timestamp = System.currentTimeMillis(),
                    technician = "Tech-01 (Moi)",
                    action = "SYNC_FORCEE",
                    nodeId = "TOUT_LE_PROJET",
                    details = "Synchronisation manuelle forcée avec le serveur SIG FTTH"
                )
            )
            _lastSyncTimestamp.value = System.currentTimeMillis()
            _syncStatus.value = RealtimeSyncStatus.ONLINE_SYNCED
        }
    }

    suspend fun resetToDefaultDemo() {
        dao.clearNodes()
        dao.clearLinks()
        dao.insertNodes(DefaultFtthData.getDefaultNodes())
        dao.insertLinks(DefaultFtthData.getDefaultLinks())
        dao.insertSyncLog(
            SyncLogEntity(
                timestamp = System.currentTimeMillis(),
                technician = "Système",
                action = "REINIT_DEMO",
                nodeId = "RESEAU",
                details = "Réinitialisation des données de piquetage FTTH de démonstration"
            )
        )
    }

    companion object {
        // Haversine formula to compute meters between 2 GPS coordinates
        fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0 // Earth radius in meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }
    }
}
