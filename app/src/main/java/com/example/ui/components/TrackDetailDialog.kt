package com.example.ui.components

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.gps.GpsLocationData
import com.example.data.photo.PhotoStorageManager
import com.example.data.storage.StoredTrack
import com.example.data.storage.TrackPhoto
import com.example.data.storage.TrackPoint
import com.example.data.util.TrackGeometryHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrackDetailDialog(
    track: StoredTrack,
    userLocation: GpsLocationData?,
    onDismiss: () -> Unit,
    onUpdateTrackName: (trackId: String, newName: String) -> Unit,
    onAddTrackPhoto: (trackId: String, photoPath: String, lat: Double, lon: Double) -> Unit,
    onDeleteTrackPhoto: (trackId: String, photoId: String) -> Unit,
    onApplySimplification: (trackId: String, simplifiedPoints: List<TrackPoint>, tolerance: Double) -> Unit,
    onRestoreOriginal: (trackId: String) -> Unit,
    onStartStraightenMode: (track: StoredTrack) -> Unit,
    onStartMoveVertexMode: (track: StoredTrack) -> Unit,
    onDeleteTrack: (trackId: String) -> Unit
) {
    val context = LocalContext.current

    var trackName by remember(track) { mutableStateOf(track.name) }
    var isEditingName by remember { mutableStateOf(false) }

    var selectedTolerance by remember { mutableDoubleStateOf(5.0) } // Tolérance par défaut 5m
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var viewingPhotoPath by remember { mutableStateOf<String?>(null) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraFile != null && tempCameraFile!!.exists()) {
            val photoLat = userLocation?.latitude ?: track.points.firstOrNull()?.latitude ?: 48.8566
            val photoLon = userLocation?.longitude ?: track.points.firstOrNull()?.longitude ?: 2.3522
            onAddTrackPhoto(track.id, tempCameraFile!!.absolutePath, photoLat, photoLon)
        }
    }

    val currentPoints = if (track.points.isNotEmpty()) track.points else track.rawPoints

    // Calcul de l'aperçu RDP
    val previewSimplifiedPoints = remember(currentPoints, selectedTolerance) {
        TrackGeometryHelper.simplifyRamerDouglasPeucker(currentPoints, selectedTolerance)
    }

    val pointsReducedCount = currentPoints.size - previewSimplifiedPoints.size
    val pointsReducedPercent = if (currentPoints.isNotEmpty()) {
        (pointsReducedCount.toDouble() / currentPoints.size.toDouble() * 100).toInt()
    } else 0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .testTag("track_detail_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header & Renommage du Trajet
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))

                    if (isEditingName) {
                        OutlinedTextField(
                            value = trackName,
                            onValueChange = { trackName = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            trailingIcon = {
                                IconButton(onClick = {
                                    onUpdateTrackName(track.id, trackName)
                                    isEditingName = false
                                }) {
                                    Icon(Icons.Default.Save, contentDescription = "Sauvegarder", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    } else {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = track.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = { isEditingName = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Renommer", modifier = Modifier.size(16.dp))
                                }
                            }
                            Text(
                                text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(track.startTime)),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val distKm = track.totalDistanceMeters / 1000.0
                    val distStr = if (distKm < 1.0) "${track.totalDistanceMeters.toInt()} m" else String.format(Locale.FRANCE, "%.2f km", distKm)
                    MetricCard("Distance", distStr, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    MetricCard("Sommets", "${currentPoints.size} pts", Color(0xFF16A34A), Modifier.weight(1f))
                    MetricCard("Photos", "${track.photos.size}", Color(0xFF0284C7), Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- SECTION PHOTOS LE LONG DU TRAJET ---
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Photos géoréférencées (${track.photos.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Dossier : Documents/Releve-Terrain/Photos/${track.id}/",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    val file = PhotoStorageManager.createNewPhotoFile(context, track.id)
                                    tempCameraFile = file
                                    val uri = PhotoStorageManager.getUriForPhotoFile(context, file)
                                    takePictureLauncher.launch(uri)
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = ButtonDefaults.TextButtonContentPadding
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Prendre photo", fontSize = 11.sp)
                            }
                        }

                        if (track.photos.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(track.photos) { photo ->
                                    Box(
                                        modifier = Modifier
                                            .size(76.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .clickable { viewingPhotoPath = photo.photoPath }
                                    ) {
                                        AsyncImage(
                                            model = File(photo.photoPath),
                                            contentDescription = "Photo trajet",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        IconButton(
                                            onClick = { onDeleteTrackPhoto(track.id, photo.id) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(22.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Supprimer",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Prenez des photos le long du parcours : elles apparaissent sur la carte avec leurs coordonnées GPS.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- SECTION MODIFICATION MANUELLE & REDRESSEMENT ---
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Ajustement & Modification du tracé",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Bouton Déplacer un sommet
                            OutlinedButton(
                                onClick = {
                                    onStartMoveVertexMode(track)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.NearMe, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Déplacer sommet", fontSize = 11.sp)
                            }

                            // Bouton Redresser entre 2 sommets
                            Button(
                                onClick = {
                                    onStartStraightenMode(track)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.LinearScale, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ligne droite (rue)", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- SECTION SIMPLIFICATION ANTI-BRUIT GPS ---
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Simplifier après coup (Anti-Bruit)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Supprime les petits zigzags et ne garde que les vrais changements de direction.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )

                        // Chips tolérance
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val tolerances = listOf(3.0, 5.0, 10.0, 20.0)
                            for (t in tolerances) {
                                val isSelected = selectedTolerance == t
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedTolerance = t },
                                    label = { Text("${t.toInt()} m", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Résultat d'aperçu avant validation
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Aperçu : ${currentPoints.size} pts ➔ ${previewSimplifiedPoints.size} sommets",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Gain : $pointsReducedCount points inutiles supprimés (-$pointsReducedPercent% de bruit).",
                                    fontSize = 11.sp,
                                    color = Color(0xFF16A34A)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onApplySimplification(track.id, previewSimplifiedPoints, selectedTolerance)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Valider la simplification", fontSize = 12.sp)
                            }

                            if (track.rawPoints.isNotEmpty() && track.isSimplified) {
                                OutlinedButton(
                                    onClick = { onRestoreOriginal(track.id) }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rétablir", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // Aperçu photo plein écran
                if (viewingPhotoPath != null) {
                    Dialog(onDismissRequest = { viewingPhotoPath = null }) {
                        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                AsyncImage(
                                    model = File(viewingPhotoPath!!),
                                    contentDescription = "Photo trajet",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = { viewingPhotoPath = null }) { Text("Fermer") }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Supprimer le trajet
                if (showDeleteConfirm) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Supprimer définitivement ce tracé ?",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showDeleteConfirm = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Annuler")
                                }
                                Button(
                                    onClick = {
                                        onDeleteTrack(track.id)
                                        onDismiss()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Supprimer")
                                }
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Supprimer ce tracé")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = color)
        }
    }
}
