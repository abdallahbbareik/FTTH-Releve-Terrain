package com.example.data.gps

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GpsLocationData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0.0f,
    val speedKmh: Float = 0.0f,
    val bearing: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis()
)

enum class GpsStatus(val label: String) {
    SEARCHING("Recherche satellites GPS..."),
    FIXED("GPS Fixé"),
    DISABLED("GPS désactivé sur l'appareil"),
    NO_PERMISSION("Permission GPS requise")
}

class GpsLocationService(private val context: Context) {

    private val _currentLocation = MutableStateFlow<GpsLocationData?>(null)
    val currentLocation: StateFlow<GpsLocationData?> = _currentLocation.asStateFlow()

    private val _gpsStatus = MutableStateFlow(GpsStatus.SEARCHING)
    val gpsStatus: StateFlow<GpsStatus> = _gpsStatus.asStateFlow()

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var locationManager: LocationManager? = null
    private var fallbackLocationListener: LocationListener? = null
    private var isStarted = false

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isStarted) return
        if (!hasLocationPermission()) {
            _gpsStatus.value = GpsStatus.NO_PERMISSION
            return
        }

        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true

        if (!isGpsEnabled) {
            _gpsStatus.value = GpsStatus.DISABLED
        } else {
            _gpsStatus.value = GpsStatus.SEARCHING
        }

        try {
            fusedClient = LocationServices.getFusedLocationProviderClient(context)

            // Try getting last known location immediately
            fusedClient?.lastLocation?.addOnSuccessListener { loc ->
                if (loc != null) {
                    updateLocation(loc)
                }
            }

            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(1.0f)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    updateLocation(location)
                }
            }

            fusedClient?.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
            isStarted = true
        } catch (e: Exception) {
            // Fallback to standard LocationManager if Play Services fails
            startFallbackLocationManager()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startFallbackLocationManager() {
        val lm = locationManager ?: context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        fallbackLocationListener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                updateLocation(loc)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) { _gpsStatus.value = GpsStatus.SEARCHING }
            override fun onProviderDisabled(provider: String) { _gpsStatus.value = GpsStatus.DISABLED }
        }

        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 1.0f, fallbackLocationListener!!)
            } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 1.0f, fallbackLocationListener!!)
            }
            isStarted = true
        } catch (e: Exception) {
            _gpsStatus.value = GpsStatus.DISABLED
        }
    }

    private fun updateLocation(loc: Location) {
        val speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0.0f
        _currentLocation.value = GpsLocationData(
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
            accuracy = if (loc.hasAccuracy()) loc.accuracy else 10.0f,
            speedKmh = speedKmh,
            bearing = if (loc.hasBearing()) loc.bearing else 0.0f,
            timestamp = loc.time
        )
        _gpsStatus.value = GpsStatus.FIXED
    }

    fun stop() {
        try {
            locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
            locationCallback = null
            fallbackLocationListener?.let { locationManager?.removeUpdates(it) }
            fallbackLocationListener = null
            isStarted = false
        } catch (e: Exception) {
            // Ignore on cleanup
        }
    }

    companion object {
        fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val results = FloatArray(1)
            Location.distanceBetween(lat1, lon1, lat2, lon2, results)
            return results[0].toDouble()
        }
    }
}
