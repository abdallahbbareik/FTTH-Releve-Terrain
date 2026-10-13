package com.example.ui.components

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import coil.compose.AsyncImage
import com.example.data.gps.GpsLocationData
import com.example.data.photo.PhotoStorageManager
import com.example.data.storage.StoredTrack
import com.example.data.storage.TrackPhoto
import com.example.ui.theme.TelecomCyan
import com.example.ui.theme.TelecomNavy
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackCardSheet(
    track: StoredTrack,
    userLocation: GpsLocationData? = null,
    onDismiss: () -> Unit,
    onSave: (updatedTrack: StoredTrack) -> Unit = {},
    onSaveNotes: (updatedNotes: String) -> Unit = {},
    onAddTrackPhoto: (photoPath: String, lat: Double, lon: Double) -> Unit = { _, _, _ -> },
    onDeleteTrackPhoto: (photoId: String) -> Unit = {},
    onOpenFullEdit: () -> Unit,
    onDeleteTrack: (trackId: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var trackName by remember(track) { mutableStateOf(track.name) }
    var notesText by remember(track) { mutableStateOf(track.notes) }
    var currentPhotos by remember(track) { mutableStateOf(track.photos) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var viewingPhotoPath by remember { mutableStateOf<String?>(null) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }

    val trackColor = when (track.type) {
        "Aérien" -> Color(0xFF7C3AED)
        "Façade" -> Color(0xFF0D9488)
        else -> Color(0xFF0284C7) // GC par défaut
    }

    val lengthMeters = track.totalDistanceMeters
    val distKm = lengthMeters / 1000.0
    val distStr = if (lengthMeters < 1000.0) "${lengthMeters.toInt()} m" else String.format(Locale.FRANCE, "%.2f km", distKm)
    val ptsCount = if (track.points.isNotEmpty()) track.points.size else track.rawPoints.size

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraFile != null && tempCameraFile!!.exists() && tempCameraFile!!.length() > 0) {
            val finalized = PhotoStorageManager.syncAndFinalizePhoto(context, tempCameraFile!!)
            val photoLat = userLocation?.latitude ?: track.points.firstOrNull()?.latitude ?: 34.0
            val photoLon = userLocation?.longitude ?: track.points.firstOrNull()?.longitude ?: 9.5375
            val newTrackPhoto = TrackPhoto(
                photoPath = finalized,
                latitude = photoLat,
                longitude = photoLon
            )
            currentPhotos = currentPhotos + newTrackPhoto
            onAddTrackPhoto(finalized, photoLat, photoLon)
        } else {
            tempCameraFile?.let { if (it.exists() && it.length() == 0L) it.delete() }
        }
    }

    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val saved = PhotoStorageManager.saveImportedPhoto(context, track.id, uri)
            if (saved != null) {
                val photoLat = userLocation?.latitude ?: track.points.firstOrNull()?.latitude ?: 34.0
                val photoLon = userLocation?.longitude ?: track.points.firstOrNull()?.longitude ?: 9.5375
                val newTrackPhoto = TrackPhoto(
                    photoPath = saved,
                    latitude = photoLat,
                    longitude = photoLon
                )
                currentPhotos = currentPhotos + newTrackPhoto
                onAddTrackPhoto(saved, photoLat, photoLon)
            }
        }
    }

    fun buildUpdatedTrack(): StoredTrack {
        return track.copy(
            name = trackName.trim().ifEmpty { track.name },
            notes = notesText,
            photos = currentPhotos
        )
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (notesText != track.notes) {
                onSaveNotes(notesText)
            }
            onDismiss()
        },
        sheetState = sheetState,
        dragHandle = null,
        modifier = Modifier.testTag("track_card_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // 1. En-tête : Nom du tracé, Type, État, Distance
            Surface(
                color = trackColor.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(trackColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = "Trajet",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = trackName.ifBlank { track.id },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = trackColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = track.type.ifBlank { "GC" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = "${track.etat.ifBlank { "Conforme" }} • $distStr • $ptsCount sommets",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (track.type == "GC" && track.conduitType.isNotBlank()) {
                            Text(
                                text = "${track.conduitCount} cond. ${track.conduitType} (${track.conduitDiameters.joinToString(", ")}) • ${track.conduitAudit}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_track_card_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                // 2. Photos le long du tracé
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Photos du tracé (${currentPhotos.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.weight(1f))

                            // Bouton Appareil Photo
                            FilledTonalButton(
                                onClick = {
                                    try {
                                        val f = PhotoStorageManager.createNewPhotoFile(context, track.id)
                                        tempCameraFile = f
                                        val uri = PhotoStorageManager.getUriForPhotoFile(context, f)
                                        takePictureLauncher.launch(uri)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("track_card_take_photo_button")
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Photo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Bouton Galerie
                            OutlinedButton(
                                onClick = {
                                    pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("track_card_gallery_button")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Galerie", fontSize = 11.sp)
                            }
                        }

                        // Galerie horizontale des photos
                        if (currentPhotos.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(currentPhotos, key = { it.id }) { photoItem ->
                                    val resolvedFile = PhotoStorageManager.resolvePhotoFile(context, photoItem.photoPath)
                                    Box(
                                        modifier = Modifier
                                            .size(76.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                            .clickable { viewingPhotoPath = photoItem.photoPath }
                                    ) {
                                        AsyncImage(
                                            model = resolvedFile,
                                            contentDescription = "Photo tracé",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Bouton Supprimer la photo
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(2.dp)
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.65f))
                                                .clickable {
                                                    currentPhotos = currentPhotos.filter { it.id != photoItem.id }
                                                    onDeleteTrackPhoto(photoItem.id)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Supprimer photo",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Observations / Notes du tracé
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Note,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Observations / Notes",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = notesText,
                            onValueChange = {
                                notesText = it
                                onSaveNotes(it)
                            },
                            placeholder = { Text("Ajouter une observation sur ce tracé (accès, contraintes, état des fourreaux...)", fontSize = 12.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(86.dp)
                                .testTag("track_notes_input"),
                            shape = RoundedCornerShape(10.dp),
                            maxLines = 3
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Boutons Enregistrer, Modifier et Supprimer (identique au principe des noeuds)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Bouton Supprimer
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("delete_track_card_button")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Supprimer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Bouton Modifier (ouvre la fiche détaillée complète)
                    OutlinedButton(
                        onClick = {
                            if (notesText != track.notes) onSaveNotes(notesText)
                            onOpenFullEdit()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(48.dp)
                            .testTag("edit_track_card_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Modifier", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Bouton Enregistrer (sauvegarde notes & photos, et ferme la fenêtre)
                    Button(
                        onClick = {
                            val updated = buildUpdatedTrack()
                            onSave(updated)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TelecomNavy,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("save_track_card_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp), tint = TelecomCyan)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Enregistrer", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // Confirmation de suppression
                if (showDeleteConfirm) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Confirmer la suppression de ${track.name} ?",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Cette action supprimera définitivement le tracé et ses sommets.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showDeleteConfirm = false },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Annuler")
                                }
                                Button(
                                    onClick = {
                                        onDeleteTrack(track.id)
                                        showDeleteConfirm = false
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
                }
            }
        }
    }

    // Modal de visualisation d'une photo en grand
    if (viewingPhotoPath != null) {
        val resolvedPreview = PhotoStorageManager.resolvePhotoFile(context, viewingPhotoPath!!)
        val photoDetails = PhotoStorageManager.getPhotoDetails(resolvedPreview.absolutePath)
        Dialog(onDismissRequest = { viewingPhotoPath = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Photo du tracé",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Résolution : ${photoDetails.resolutionLabel}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    AsyncImage(
                        model = resolvedPreview,
                        contentDescription = "Aperçu photo tracé",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewingPhotoPath = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Fermer")
                    }
                }
            }
        }
    }
}
