package com.example.data.gps

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.util.TrackGeometryHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale

class GpsForegroundService : Service() {

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var locationManager: LocationManager? = null
    private var fallbackListener: LocationListener? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var trackName: String = "Tracé FTTH"
    private var recordedPointsCount: Int = 0
    private var recordedDistanceMeters: Double = 0.0
    private var lastRecordedLat: Double? = null
    private var lastRecordedLon: Double? = null
    private var lastRecordedTime: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            if (GpsTrackingBridge.isTrackingRunning.value) {
                trackName = GpsTrackingBridge.currentTrackName.ifBlank { "Tracé FTTH" }
                startForegroundTracking()
                return START_STICKY
            } else {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        when (intent.action) {
            ACTION_STOP_TRACKING -> {
                GpsTrackingBridge.requestStopTracking()
                stopForegroundTracking()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_STATS -> {
                val pts = intent.getIntExtra(EXTRA_POINTS_COUNT, recordedPointsCount)
                val dist = intent.getDoubleExtra(EXTRA_DISTANCE_METERS, recordedDistanceMeters)
                recordedPointsCount = pts
                recordedDistanceMeters = dist
                updateNotification()
                return START_STICKY
            }
            ACTION_START_TRACKING -> {
                val name = intent.getStringExtra(EXTRA_TRACK_NAME) ?: "Tracé FTTH"
                trackName = name
                startForegroundTracking()
                return START_STICKY
            }
            else -> {
                val name = intent.getStringExtra(EXTRA_TRACK_NAME) ?: "Tracé FTTH"
                trackName = name
                startForegroundTracking()
                return START_STICKY
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Enregistrement Tracé GPS",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintient l'enregistrement du tracé GPS actif lorsque l'écran est éteint et le téléphone en poche"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, GpsForegroundService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val distStr = if (recordedDistanceMeters < 1000.0) {
            "${recordedDistanceMeters.toInt()} m"
        } else {
            String.format(Locale.FRANCE, "%.2f km", recordedDistanceMeters / 1000.0)
        }

        val contentText = if (recordedPointsCount > 0) {
            "$recordedPointsCount pts relevés • $distStr (écran verrouillé OK)"
        } else {
            "Relevé terrain actif en continu (écran éteint / en poche OK)"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracé GPS actif : $trackName")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_media_pause, "Arrêter", pendingStop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    @SuppressLint("MissingPermission", "WakelockTimeout")
    private fun startForegroundTracking() {
        // 1. Démarrer le service en premier plan immédiatement pour que le système ne le tue pas
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 2. Acquérir WakeLock pour empêcher le CPU d'entrer en veille quand l'écran est éteint dans la poche
        acquireWakeLock()

        // 3. Démarrer les mises à jour de position haute précision
        startLocationUpdates()

        GpsTrackingBridge.onServiceStarted(trackName)
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ftth:GpsTrackingWakeLock").apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire(12 * 3600 * 1000L) // 12h max timeout
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // A. Google Play Services Fused Location
        try {
            fusedClient = LocationServices.getFusedLocationProviderClient(this)

            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L)
                .setMinUpdateIntervalMillis(1000L)
                .setMaxUpdateDelayMillis(1500L)
                .setMinUpdateDistanceMeters(1.0f)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    handleNewLocation(location)
                }
            }

            fusedClient?.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // B. LocationManager natif GPS_PROVIDER (garantie absolue quand l'écran est éteint et dans la poche)
        startNativeLocationManager()
    }

    @SuppressLint("MissingPermission")
    private fun startNativeLocationManager() {
        try {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
            fallbackListener = object : LocationListener {
                override fun onLocationChanged(loc: Location) {
                    handleNewLocation(loc)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1500L,
                    1.0f,
                    fallbackListener!!,
                    Looper.getMainLooper()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleNewLocation(loc: Location) {
        // Filtrer les doublons trop rapprochés dans le temps si FusedLocation et Native GPS renvoient la même position
        val now = System.currentTimeMillis()
        if (now - lastRecordedTime < 800L && lastRecordedLat != null && lastRecordedLon != null) {
            val d = TrackGeometryHelper.calculateDistanceMeters(lastRecordedLat!!, lastRecordedLon!!, loc.latitude, loc.longitude)
            if (d < 1.0) return
        }

        // Met à jour la position partagée (mise à jour de userLocation dans FtthViewModel et carte)
        GpsLocationService.updateSharedLocation(loc)

        // Enregistre le point dans le bridge thread-safe
        val added = GpsTrackingBridge.onLocationReceived(loc)
        if (added) {
            lastRecordedTime = now
            lastRecordedLat = loc.latitude
            lastRecordedLon = loc.longitude
            recordedPointsCount = GpsTrackingBridge.recordedPointsCount
            recordedDistanceMeters = GpsTrackingBridge.recordedDistanceMeters
            updateNotification()
        }
    }

    private fun stopForegroundTracking() {
        try {
            locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
            locationCallback = null
            fallbackListener?.let { locationManager?.removeUpdates(it) }
            fallbackListener = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        releaseWakeLock()
        GpsTrackingBridge.onServiceStopped()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        stopForegroundTracking()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "ftth_gps_tracking_channel"
        const val NOTIFICATION_ID = 9001

        const val ACTION_START_TRACKING = "com.example.action.START_GPS_TRACKING"
        const val ACTION_STOP_TRACKING = "com.example.action.STOP_GPS_TRACKING"
        const val ACTION_UPDATE_STATS = "com.example.action.UPDATE_STATS"

        const val EXTRA_TRACK_NAME = "extra_track_name"
        const val EXTRA_POINTS_COUNT = "extra_points_count"
        const val EXTRA_DISTANCE_METERS = "extra_distance_meters"

        fun startTracking(context: Context, trackName: String) {
            val intent = Intent(context, GpsForegroundService::class.java).apply {
                action = ACTION_START_TRACKING
                putExtra(EXTRA_TRACK_NAME, trackName)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopTracking(context: Context) {
            val intent = Intent(context, GpsForegroundService::class.java).apply {
                action = ACTION_STOP_TRACKING
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun updateStats(context: Context, pointsCount: Int, distanceMeters: Double) {
            val intent = Intent(context, GpsForegroundService::class.java).apply {
                action = ACTION_UPDATE_STATS
                putExtra(EXTRA_POINTS_COUNT, pointsCount)
                putExtra(EXTRA_DISTANCE_METERS, distanceMeters)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                // Ignore if service not running
            }
        }
    }
}
