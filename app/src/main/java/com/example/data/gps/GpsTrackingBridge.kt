package com.example.data.gps

import android.location.Location
import com.example.data.storage.StoredTrack
import com.example.data.storage.TrackPhoto
import com.example.data.storage.TrackPoint
import com.example.data.util.TrackGeometryHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

object GpsTrackingBridge {
    private val _isTrackingRunning = MutableStateFlow(false)
    val isTrackingRunning: StateFlow<Boolean> = _isTrackingRunning.asStateFlow()

    private val _stopRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stopRequested: SharedFlow<Unit> = _stopRequested.asSharedFlow()

    private val _trackLocationFlow = MutableSharedFlow<Location>(extraBufferCapacity = 64)
    val trackLocationFlow: SharedFlow<Location> = _trackLocationFlow.asSharedFlow()

    private val _activeTrack = MutableStateFlow<StoredTrack?>(null)
    val activeTrack: StateFlow<StoredTrack?> = _activeTrack.asStateFlow()

    private val _activeTrackPoints = MutableStateFlow<List<TrackPoint>>(emptyList())
    val activeTrackPoints: StateFlow<List<TrackPoint>> = _activeTrackPoints.asStateFlow()

    @Volatile
    var currentTrackName: String = ""
    @Volatile
    var recordedPointsCount: Int = 0
    @Volatile
    var recordedDistanceMeters: Double = 0.0

    private val pointsLock = Any()
    private val pointsList = mutableListOf<TrackPoint>()

    fun startSession(track: StoredTrack) {
        synchronized(pointsLock) {
            pointsList.clear()
            currentTrackName = track.name
            recordedPointsCount = 0
            recordedDistanceMeters = 0.0
            _activeTrackPoints.value = emptyList()
            _activeTrack.value = track
            _isTrackingRunning.value = true
        }
    }

    fun onServiceStarted(trackName: String) {
        currentTrackName = trackName
        _isTrackingRunning.value = true
    }

    fun onServiceStopped() {
        _isTrackingRunning.value = false
    }

    fun stopSession(): StoredTrack? {
        synchronized(pointsLock) {
            val track = _activeTrack.value
            _isTrackingRunning.value = false
            _activeTrack.value = null
            _activeTrackPoints.value = emptyList()
            pointsList.clear()
            return track
        }
    }

    fun onLocationReceived(location: Location): Boolean {
        _trackLocationFlow.tryEmit(location)
        if (!_isTrackingRunning.value) return false

        synchronized(pointsLock) {
            val last = pointsList.lastOrNull()
            if (last != null) {
                val d = TrackGeometryHelper.calculateDistanceMeters(last.latitude, last.longitude, location.latitude, location.longitude)
                if (d < 1.0) return false // Filtrer le bruit stationnaire < 1m
            }
            val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0.0f
            val pt = TrackPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else 0.0,
                accuracy = if (location.hasAccuracy()) location.accuracy else 10.0f,
                speed = speedKmh,
                timestamp = location.time
            )
            pointsList.add(pt)
            val currentList = pointsList.toList()
            val dist = TrackGeometryHelper.computeTotalDistanceMeters(currentList)
            recordedPointsCount = currentList.size
            recordedDistanceMeters = dist
            _activeTrackPoints.value = currentList
            _activeTrack.value = _activeTrack.value?.copy(
                totalDistanceMeters = dist,
                rawPoints = currentList,
                points = currentList
            )
            return true
        }
    }

    fun addSnapPoint(latitude: Double, longitude: Double) {
        synchronized(pointsLock) {
            val pt = TrackPoint(
                latitude = latitude,
                longitude = longitude,
                timestamp = System.currentTimeMillis()
            )
            pointsList.add(pt)
            val currentList = pointsList.toList()
            val dist = TrackGeometryHelper.computeTotalDistanceMeters(currentList)
            recordedPointsCount = currentList.size
            recordedDistanceMeters = dist
            _activeTrackPoints.value = currentList
            _activeTrack.value = _activeTrack.value?.copy(
                totalDistanceMeters = dist,
                rawPoints = currentList,
                points = currentList
            )
        }
    }

    fun addPhoto(photo: TrackPhoto) {
        synchronized(pointsLock) {
            val track = _activeTrack.value ?: return
            val currentPhotos = track.photos.toMutableList()
            currentPhotos.add(photo)
            _activeTrack.value = track.copy(photos = currentPhotos)
        }
    }

    fun requestStopTracking() {
        _stopRequested.tryEmit(Unit)
    }

    fun updateStats(pointsCount: Int, distanceMeters: Double) {
        recordedPointsCount = pointsCount
        recordedDistanceMeters = distanceMeters
    }
}
