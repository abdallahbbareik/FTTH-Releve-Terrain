package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AddLocationAlt
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.gps.GpsLocationData
import com.example.data.gps.GpsStatus
import com.example.data.storage.StoredTrack
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
    onStartManualTrack: () -> Unit,
    onPinAtGpsLocation: () -> Unit,
    onPinOnMapLocation: () -> Unit,
    onOpenTracksList: () -> Unit,
    onOpenNodesList: () -> Unit,
    onRequestGpsPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
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

            // Ligne 2 : Actions Terrain (Piqueter GPS, Tracé Manuel, Trace GPS Auto, Liste Trajets)
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

                // 3. Trace GPS Auto (Start / Stop)
                if (activeTrack == null) {
                    OutlinedButton(
                        onClick = onStartAutoTrack,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("start_auto_track_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Auto GPS", fontSize = 11.sp)
                    }
                } else {
                    Button(
                        onClick = onStopAutoTrack,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("stop_auto_track_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val distKm = activeTrack.totalDistanceMeters / 1000.0
                        Text(
                            text = if (distKm < 1.0) "${activeTrack.totalDistanceMeters.toInt()}m" else String.format(Locale.FRANCE, "%.1fkm", distKm),
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 4. Listes Points & Trajets
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
