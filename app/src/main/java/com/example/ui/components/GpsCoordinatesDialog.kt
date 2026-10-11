package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.gps.GpsLocationData
import com.example.data.gps.GpsStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GpsCoordinatesDialog(
    locationData: GpsLocationData?,
    gpsStatus: GpsStatus,
    onRequestPermission: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val latStr = locationData?.let { String.format(Locale.ROOT, "%.6f°", it.latitude) } ?: "Non disponible"
    val lonStr = locationData?.let { String.format(Locale.ROOT, "%.6f°", it.longitude) } ?: "Non disponible"
    val altStr = locationData?.let { String.format(Locale.ROOT, "%.1f m", it.altitude) } ?: "-"
    val accStr = locationData?.let { String.format(Locale.ROOT, "± %.1f m", it.accuracy) } ?: "-"
    val speedStr = locationData?.let { String.format(Locale.ROOT, "%.1f km/h", it.speedKmh) } ?: "0 km/h"
    val timeStr = locationData?.let {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it.timestamp))
    } ?: "-"

    val isFixed = locationData != null && (gpsStatus == GpsStatus.FIXED || locationData.accuracy < 20f)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("gps_coordinates_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isFixed) Color(0xFF16A34A).copy(alpha = 0.15f) else Color(0xFFEA580C).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = if (isFixed) Color(0xFF16A34A) else Color(0xFFEA580C),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Coordonnées GPS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isFixed) Color(0xFF16A34A) else Color(0xFFEA580C))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = gpsStatus.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Grille d'informations coordonnées
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Latitude", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(latStr, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Longitude", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(lonStr, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Précision", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isFixed) Color(0xFF16A34A).copy(alpha = 0.15f) else Color(0xFFEA580C).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = accStr,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFixed) Color(0xFF16A34A) else Color(0xFFEA580C),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Altitude", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(altStr, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Vitesse", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(speedStr, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Dernier fix", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(timeStr, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Boutons d'action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (locationData != null) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("GPS", "${locationData.latitude}, ${locationData.longitude}")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Coordonnées copiées !", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = locationData != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copier", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("OK", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
