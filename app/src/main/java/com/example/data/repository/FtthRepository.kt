package com.example.data.repository

import android.content.Context
import com.example.data.export.RealExportService
import com.example.data.gps.GpsLocationData
import com.example.data.local.DefaultFtthData
import com.example.data.local.FtthDao
import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.local.GpsTrackEntity
import com.example.data.local.GpsTrackPointEntity
import com.example.data.local.SyncLogEntity
import com.example.data.local.SyncState
import com.example.data.photo.PhotoStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class StorageStatus(val label: String) {
    LOCAL_PERSISTED("Stockage Local Sécurisé (SQLite)"),
    SAVING("Enregistrement en cours...")
}

class FtthRepository(
    private val dao: FtthDao,
    private val appScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    val allNodes: Flow<List<FtthNodeEntity>> = dao.getAllNodes()
    val allLinks: Flow<List<FtthLinkEntity>> = dao.getAllLinks()
    val allSyncLogs: Flow<List<SyncLogEntity>> = dao.getAllSyncLogs()
    val allTracks: Flow<List<GpsTrackEntity>> = dao.getAllTracks()
    val activeTrack: Flow<GpsTrackEntity?> = dao.getActiveTrack()

    private val _storageStatus = MutableStateFlow(StorageStatus.LOCAL_PERSISTED)
    val storageStatus: StateFlow<StorageStatus> = _storageStatus.asStateFlow()

    init {
        // Preload default data if DB is empty
        appScope.launch {
            val count = dao.getNodeCount()
            if (count == 0) {
                dao.insertNodes(DefaultFtthData.getDefaultNodes())
                dao.insertLinks(DefaultFtthData.getDefaultLinks())
                DefaultFtthData.getDefaultLogs().forEach { dao.insertSyncLog(it) }
            }
        }
    }

    suspend fun saveNode(node: FtthNodeEntity, isNew: Boolean = false) {
        val updatedNode = node.copy(
            updatedAt = System.currentTimeMillis(),
            photoCount = node.photos.size,
            syncState = SyncState.SYNCED
        )
        dao.insertNode(updatedNode)

        dao.insertSyncLog(
            SyncLogEntity(
                timestamp = System.currentTimeMillis(),
                technician = node.technicianName,
                action = if (isNew) "CREATION_NOEUD" else "MODIFICATION",
                nodeId = node.id,
                details = "${node.type.label} : ${node.name} (${node.status.label})"
            )
        )
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

    suspend fun addPhotoToNode(nodeId: String, photoPath: String) {
        val node = dao.getNodeById(nodeId).first() ?: return
        val currentPhotos = node.photos.toMutableList()
        if (!currentPhotos.contains(photoPath)) {
            currentPhotos.add(photoPath)
            saveNode(node.copy(photos = currentPhotos, photoCount = currentPhotos.size))
        }
    }

    suspend fun removePhotoFromNode(nodeId: String, photoPath: String) {
        val node = dao.getNodeById(nodeId).first() ?: return
        val currentPhotos = node.photos.toMutableList()
        if (currentPhotos.remove(photoPath)) {
            PhotoStorageManager.deletePhoto(photoPath)
            saveNode(node.copy(photos = currentPhotos, photoCount = currentPhotos.size))
        }
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

    // --- GPS Tracks Recording ---

    fun getTrackPoints(trackId: String): Flow<List<GpsTrackPointEntity>> =
        dao.getTrackPoints(trackId)

    suspend fun startGpsTrack(name: String = "Cheminement ${System.currentTimeMillis() % 10000}"): String {
        // Stop any previous active track
        val active = dao.getActiveTrack().first()
        if (active != null) {
            dao.updateTrack(active.copy(isActive = false, endTime = System.currentTimeMillis()))
        }

        val trackId = "TRK-${System.currentTimeMillis()}"
        val newTrack = GpsTrackEntity(
            id = trackId,
            name = name,
            startTime = System.currentTimeMillis(),
            isActive = true
        )
        dao.insertTrack(newTrack)
        dao.insertSyncLog(
            SyncLogEntity(
                technician = "Tech-01 (Moi)",
                action = "DEBUT_TRAJET_GPS",
                nodeId = trackId,
                details = "Début enregistrement trace terrain : $name"
            )
        )
        return trackId
    }

    suspend fun recordLocationForActiveTrack(loc: GpsLocationData) {
        val active = dao.getActiveTrack().first() ?: return
        val points = dao.getTrackPointsSnapshot(active.id)

        var addedDistance = 0.0
        if (points.isNotEmpty()) {
            val last = points.last()
            addedDistance = calculateDistanceMeters(last.latitude, last.longitude, loc.latitude, loc.longitude)
            // Filter noise: skip if movement < 1m
            if (addedDistance < 1.0) return
        }

        val point = GpsTrackPointEntity(
            trackId = active.id,
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitude = loc.altitude,
            accuracy = loc.accuracy,
            speed = loc.speedKmh,
            timestamp = loc.timestamp
        )
        dao.insertTrackPoint(point)

        dao.updateTrack(
            active.copy(
                totalDistanceMeters = active.totalDistanceMeters + addedDistance,
                pointCount = active.pointCount + 1
            )
        )
    }

    suspend fun stopActiveGpsTrack() {
        val active = dao.getActiveTrack().first() ?: return
        dao.updateTrack(
            active.copy(
                isActive = false,
                endTime = System.currentTimeMillis()
            )
        )
        dao.insertSyncLog(
            SyncLogEntity(
                technician = "Tech-01 (Moi)",
                action = "FIN_TRAJET_GPS",
                nodeId = active.id,
                details = "Fin trace ${active.name} : ${String.format(java.util.Locale.FRANCE, "%.2f km", active.totalDistanceMeters / 1000.0)} (${active.pointCount} points)"
            )
        )
    }

    suspend fun deleteTrack(trackId: String) {
        dao.deleteTrackPoints(trackId)
        dao.deleteTrack(trackId)
    }

    // --- Real File Exports ---

    suspend fun exportGeoJson(context: Context): File = withContext(Dispatchers.IO) {
        val nodes = dao.getAllNodes().first()
        val links = dao.getAllLinks().first()
        val tracks = dao.getAllTracks().first()
        val tracksWithPoints = tracks.map { track ->
            track to dao.getTrackPointsSnapshot(track.id)
        }
        RealExportService.exportGeoJson(context, nodes, links, tracksWithPoints)
    }

    suspend fun exportCsv(context: Context): File = withContext(Dispatchers.IO) {
        val nodes = dao.getAllNodes().first()
        RealExportService.exportCsv(context, nodes)
    }

    suspend fun exportKml(context: Context): File = withContext(Dispatchers.IO) {
        val nodes = dao.getAllNodes().first()
        val links = dao.getAllLinks().first()
        RealExportService.exportKml(context, nodes, links)
    }

    suspend fun exportKmz(context: Context): File = withContext(Dispatchers.IO) {
        val nodes = dao.getAllNodes().first()
        val links = dao.getAllLinks().first()
        val tracks = dao.getAllTracks().first()
        val tracksWithPoints = tracks.map { track ->
            track to dao.getTrackPointsSnapshot(track.id)
        }
        RealExportService.exportKmz(context, nodes, links, tracksWithPoints)
    }

    suspend fun exportCompleteZip(context: Context): File = withContext(Dispatchers.IO) {
        val nodes = dao.getAllNodes().first()
        val links = dao.getAllLinks().first()
        val tracks = dao.getAllTracks().first()
        val tracksWithPoints = tracks.map { track ->
            track to dao.getTrackPointsSnapshot(track.id)
        }
        RealExportService.exportCompleteZip(context, nodes, links, tracksWithPoints)
    }

    fun shareExportedFile(context: Context, file: File, mimeType: String, title: String = "Exporter les données FTTH") {
        RealExportService.shareFile(context, file, mimeType, title)
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
                details = "Réinitialisation des données de piquetage FTTH"
            )
        )
    }

    companion object {
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
