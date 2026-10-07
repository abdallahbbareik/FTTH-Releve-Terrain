package com.example.ui.components

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.gps.GpsLocationData
import com.example.data.gps.GpsStatus
import com.example.data.photo.PhotoStorageManager
import com.example.data.storage.StoredTrack
import java.io.File
import java.util.Locale

@Composable
fun GpsTrackControlBar(
    gpsStatus: GpsStatus,
    locationData: GpsLocationData?,
    activeTrack: StoredTrack?,
    totalTracksCount: Int,
    totalNodesCount: Int,
    onStartAutoTrack: () -> Unit,
    onStopAutoTrack: () -> Unit,
    onCancelActiveTrack: (() -> Unit)? = null,
    onAddActiveTrackPhoto: ((photoPath: String) -> Unit)? = null,
    onStartManualTrack: () -> Unit,
    onPinAtGpsLocation: () -> Unit,
    onPinOnMapLocation: () -> Unit,
    onOpenTracksList: () -> Unit,
    onOpenNodesList: () -> Unit,
    onRequestGpsPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var tempCameraFile by remember { mutableStateOf<File?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraFile != null && tempCameraFile!!.exists() && tempCameraFile!!.length() > 0) {
            onAddActiveTrackPhoto?.invoke(tempCameraFile!!.absolutePath)
        } else {
            tempCameraFile?.let { if (it.exists() && it.length() == 0L) it.delete() }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("gps_track_control_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {

            // --- MODE 1 : ENREGISTREMENT GPS EN COURS ---
            if (activeTrack != null) {
                val distMeters = activeTrack.totalDistanceMeters
                val distStr = if (distMeters < 1000.0) "${distMeters.toInt()} m" else String.format(Locale.FRANCE, "%.2f km", distMeters / 1000.0)
                val ptsCount = activeTrack.points.size.coerceAtLeast(activeTrack.rawPoints.size)
                val photosCount = activeTrack.photos.size

                // Ligne 1 : Enregistrement actif & Compteur complet (Points, Distance, Photos)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFDC2626),
                        modifier = Modifier.size(10.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Enregistrement tracé :",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFDC2626)
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    // Compteur en haut
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "📍 $ptsCount pts  •  📏 $distStr  •  📷 $photosCount photos",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Ligne 2 : Actions pendant l'enregistrement (Photo ici, Terminer, Annuler)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bouton "Photo ici"
                    Button(
                        onClick = {
                            try {
                                val file = PhotoStorageManager.createNewPhotoFile(context, activeTrack.id)
                                tempCameraFile = file
                                val uri = PhotoStorageManager.getUriForPhotoFile(context, file)
                                takePictureLauncher.launch(uri)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(38.dp)
                            .testTag("track_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Photo ici", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                    }

                    // Bouton "Terminer"
                    Button(
                        onClick = onStopAutoTrack,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(38.dp)
                            .testTag("stop_auto_track_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Terminer ($distStr)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                    }

                    if (onCancelActiveTrack != null) {
                        IconButton(
                            onClick = onCancelActiveTrack,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Annuler", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                // --- MODE 2 : VEILLE / PIQUETAGE NORMAL ---
                // Ligne 1 : Statut GPS & Précision
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (statusColor, statusText) = when (gpsStatus) {
                        GpsStatus.FIXED -> {
                            val acc = locationData?.accuracy ?: 10f
                            if (acc <= 5f) Color(0xFF16A34A) to "GPS Fixé (±${acc.toInt()}m - Précis)"
                            else if (acc <= 15f) Color(0xFFEAB308) to "GPS Fixé (±${acc.toInt()}m)"
                            else Color(0xFFEA580C) to "GPS Faible (±${acc.toInt()}m)"
                        }
                        GpsStatus.SEARCHING -> Color(0xFF3B82F6) to "Recherche satellites GPS..."
                        GpsStatus.DISABLED -> Color(0xFFDC2626) to "GPS Désactivé"
                        GpsStatus.NO_PERMISSION -> Color(0xFFDC2626) to "Permission GPS requise"
                    }

                    Surface(
                        shape = CircleShape,
                        color = statusColor,
                        modifier = Modifier.size(10.dp)
                    ) {}

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    if (gpsStatus == GpsStatus.NO_PERMISSION) {
                        OutlinedButton(
                            onClick = onRequestGpsPermission,
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Activer", fontSize = 11.sp)
                        }
                    } else if (locationData != null) {
                        Text(
                            text = "${String.format(Locale.FRANCE, "%.4f", locationData.latitude)}, ${String.format(Locale.FRANCE, "%.4f", locationData.longitude)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Ligne 2 : Actions Terrain (Piqueter GPS, Pointer Carte, Tracé Manuel, Auto GPS, Listes)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Piqueter à ma position GPS
                    Button(
                        onClick = onPinAtGpsLocation,
                        enabled = locationData != null,
                        modifier = Modifier
                            .weight(1.05f)
                            .height(36.dp)
                            .testTag("pin_at_gps_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddLocationAlt,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "Piquet GPS", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // 2. Pointer un point sur la carte
                    OutlinedButton(
                        onClick = onPinOnMapLocation,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("pin_on_map_button"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddLocationAlt,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "Pointer Carte", fontSize = 10.sp)
                    }

                    // 3. Tracer manuellement sur la carte
                    OutlinedButton(
                        onClick = onStartManualTrack,
                        modifier = Modifier
                            .weight(0.95f)
                            .height(36.dp)
                            .testTag("manual_track_start_button"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditLocation,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "Tracé", fontSize = 10.sp)
                    }

                    // 4. Trace GPS Auto
                    OutlinedButton(
                        onClick = onStartAutoTrack,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("start_auto_track_button"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "Auto GPS", fontSize = 10.sp)
                    }

                    // 5. Listes Points & Trajets
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onOpenNodesList,
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("open_nodes_list_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = ButtonDefaults.TextButtonContentPadding
                        ) {
                            Text(text = "Pts($totalNodesCount)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        if (totalTracksCount > 0) {
                            IconButton(
                                onClick = onOpenTracksList,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("open_tracks_list_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = "Trajets",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
