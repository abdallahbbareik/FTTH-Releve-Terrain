package com.example.ui.components

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.runtime.mutableIntStateOf
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackDetailDialog(
    track: StoredTrack,
    userLocation: GpsLocationData?,
    onDismiss: () -> Unit,
    onSaveTrack: (updatedTrack: StoredTrack) -> Unit = {},
    onUpdateTrackName: (trackId: String, newName: String) -> Unit = { _, _ -> },
    onAddTrackPhoto: (trackId: String, photoPath: String, lat: Double, lon: Double) -> Unit,
    onDeleteTrackPhoto: (trackId: String, photoId: String) -> Unit,
    onApplySimplification: (trackId: String, simplifiedPoints: List<TrackPoint>, tolerance: Double) -> Unit,
    onRestoreOriginal: (trackId: String) -> Unit,
    onStartStraightenMode: (track: StoredTrack) -> Unit,
    onStartMoveVertexMode: (track: StoredTrack) -> Unit,
    onDeleteTrack: (trackId: String) -> Unit
) {
    val context = LocalContext.current

    // Données générales du trajet
    var trackName by remember(track) { mutableStateOf(track.name) }
    var isEditingName by remember { mutableStateOf(false) }

    // Type : GC, Aérien, Façade
    var selectedType by remember(track) { mutableStateOf(track.type.ifBlank { "GC" }) }

    // État technique : Conforme, Non conforme
    var selectedEtat by remember(track) { mutableStateOf(track.etat.ifBlank { "Conforme" }) }

    // Champs spécifiques GC
    // 1- Audit Conduites : Libres, Occupés, Bouchés
    var conduitAudit by remember(track) { mutableStateOf(track.conduitAudit.ifBlank { "Libres" }) }

    // 2- Type Conduites : PEHD, PVC, Autre
    var conduitTypeChoice by remember(track) {
        val t = track.conduitType
        if (t == "PEHD" || t == "PVC") mutableStateOf(t)
        else if (t.isNotBlank()) mutableStateOf("Autre")
        else mutableStateOf("PEHD")
    }
    var customConduitType by remember(track) {
        val t = track.conduitType
        if (t != "PEHD" && t != "PVC" && t.isNotBlank()) mutableStateOf(t)
        else mutableStateOf("")
    }

    // 3- Nombre Conduites
    var conduitCount by remember(track) { mutableIntStateOf(track.conduitCount.coerceAtLeast(1)) }

    // 4- Diamètre Conduites (mm) (choix multiple) : Ø 30, Ø 40, Ø 50, Ø 80, Ø 100, autre
    val standardDiameters = listOf("Ø 30", "Ø 40", "Ø 50", "Ø 80", "Ø 100")
    var selectedDiameters by remember(track) {
        mutableStateOf(track.conduitDiameters.ifEmpty { listOf("Ø 40") }.toSet())
    }
    var customDiameterInput by remember { mutableStateOf("") }
    var showAddCustomDiameter by remember { mutableStateOf(false) }

    var selectedTolerance by remember { mutableDoubleStateOf(5.0) } // Tolérance par défaut 5m
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var viewingPhotoPath by remember { mutableStateOf<String?>(null) }
    var tempCameraFile by remember { mutableStateOf<File?>(null) }

    val currentPoints = if (track.points.isNotEmpty()) track.points else track.rawPoints
    val lengthMeters = track.totalDistanceMeters
    val distKm = lengthMeters / 1000.0
    val distStr = if (lengthMeters < 1000.0) "${lengthMeters.toInt()} m" else String.format(Locale.FRANCE, "%.2f km", distKm)

    fun buildUpdatedTrack(): StoredTrack {
        val finalConduitType = if (conduitTypeChoice == "Autre") {
            if (customConduitType.isNotBlank()) customConduitType.trim() else "Autre"
        } else {
            conduitTypeChoice
        }
        return track.copy(
            name = trackName.trim().ifEmpty { track.name },
            type = selectedType,
            etat = selectedEtat,
            conduitAudit = conduitAudit,
            conduitType = finalConduitType,
            conduitCount = conduitCount,
            conduitDiameters = selectedDiameters.toList()
        )
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraFile != null && tempCameraFile!!.exists() && tempCameraFile!!.length() > 0) {
            val finalized = PhotoStorageManager.syncAndFinalizePhoto(context, tempCameraFile!!)
            val photoLat = userLocation?.latitude ?: track.points.firstOrNull()?.latitude ?: 34.0
            val photoLon = userLocation?.longitude ?: track.points.firstOrNull()?.longitude ?: 9.5375
            onAddTrackPhoto(track.id, finalized, photoLat, photoLon)
        } else {
            tempCameraFile?.let { if (it.exists() && it.length() == 0L) it.delete() }
        }
    }

    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val saved = PhotoStorageManager.saveImportedPhoto(context, track.id, uri)
            if (saved != null) {
                val photoLat = userLocation?.latitude ?: track.points.firstOrNull()?.latitude ?: 34.0
                val photoLon = userLocation?.longitude ?: track.points.firstOrNull()?.longitude ?: 9.5375
                onAddTrackPhoto(track.id, saved, photoLat, photoLon)
            }
        }
    }

    // Calcul de l'aperçu RDP
    val previewSimplifiedPoints = remember(currentPoints, selectedTolerance) {
        TrackGeometryHelper.simplifyRamerDouglasPeucker(currentPoints, selectedTolerance)
    }

    val pointsReducedCount = currentPoints.size - previewSimplifiedPoints.size
    val pointsReducedPercent = if (currentPoints.isNotEmpty()) {
        (pointsReducedCount.toDouble() / currentPoints.size.toDouble() * 100).toInt()
    } else 0

    Dialog(
        onDismissRequest = {
            onSaveTrack(buildUpdatedTrack())
            onDismiss()
        },
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
                        modifier = Modifier.size(26.dp)
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
                                    onSaveTrack(buildUpdatedTrack())
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
                                    text = trackName,
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
                                text = "Infra linéaire FTTH • " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(track.startTime)),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = {
                        onSaveTrack(buildUpdatedTrack())
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard("Longueur (Auto)", distStr, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    MetricCard("Sommets", "${currentPoints.size} pts", Color(0xFF16A34A), Modifier.weight(1f))
                    MetricCard("Photos", "${track.photos.size}", Color(0xFF0284C7), Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- 1. TYPE DE TRAJET (GC, Aérien, Façade) ---
                Text(
                    text = "Type de trajet :",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val trackTypes = listOf("GC" to "GC (Génie Civil)", "Aérien" to "Aérien", "Façade" to "Façade")
                    for ((typeKey, typeLabel) in trackTypes) {
                        val isSelected = selectedType == typeKey
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedType = typeKey
                                onSaveTrack(buildUpdatedTrack().copy(type = typeKey))
                            },
                            label = { Text(typeLabel, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- 2. ÉTAT TECHNIQUE (Conforme, Non conforme) ---
                Text(
                    text = "État technique :",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isConforme = selectedEtat == "Conforme"
                    FilterChip(
                        selected = isConforme,
                        onClick = {
                            selectedEtat = "Conforme"
                            onSaveTrack(buildUpdatedTrack().copy(etat = "Conforme"))
                        },
                        leadingIcon = {
                            Surface(shape = CircleShape, color = Color(0xFF16A34A), modifier = Modifier.size(10.dp)) {}
                        },
                        label = { Text("Conforme", fontWeight = if (isConforme) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFDCFCE7),
                            selectedLabelColor = Color(0xFF166534)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    val isNonConforme = selectedEtat == "Non conforme"
                    FilterChip(
                        selected = isNonConforme,
                        onClick = {
                            selectedEtat = "Non conforme"
                            onSaveTrack(buildUpdatedTrack().copy(etat = "Non conforme"))
                        },
                        leadingIcon = {
                            Surface(shape = CircleShape, color = Color(0xFFDC2626), modifier = Modifier.size(10.dp)) {}
                        },
                        label = { Text("Non conforme", fontWeight = if (isNonConforme) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFEE2E2),
                            selectedLabelColor = Color(0xFF991B1B)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- 3. CHAMPS SPÉCIFIQUES SELON TYPE DE TRAJET ---
                if (selectedType == "GC") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Caractéristiques Génie Civil (GC)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // 1. Audit Conduites (libres, occupés, bouchés)
                            Text(text = "1. Audit Conduites :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("Libres", "Occupés", "Bouchés").forEach { auditOpt ->
                                    val isSelected = conduitAudit == auditOpt
                                    val chipColor = when (auditOpt) {
                                        "Libres" -> Color(0xFF16A34A)
                                        "Occupés" -> Color(0xFF2563EB)
                                        else -> Color(0xFFDC2626)
                                    }
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            conduitAudit = auditOpt
                                            onSaveTrack(buildUpdatedTrack().copy(conduitAudit = auditOpt))
                                        },
                                        label = { Text(auditOpt, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        leadingIcon = {
                                            Surface(shape = CircleShape, color = chipColor, modifier = Modifier.size(8.dp)) {}
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 2. Type Conduites (PEHD, PVC, Autre)
                            Text(text = "2. Type Conduites :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("PEHD", "PVC", "Autre").forEach { tOpt ->
                                    val isSelected = conduitTypeChoice == tOpt
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            conduitTypeChoice = tOpt
                                            onSaveTrack(buildUpdatedTrack())
                                        },
                                        label = { Text(tOpt, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            if (conduitTypeChoice == "Autre") {
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = customConduitType,
                                    onValueChange = {
                                        customConduitType = it
                                        onSaveTrack(buildUpdatedTrack())
                                    },
                                    label = { Text("Précisez le type de conduite (ex: Acier, Fonte, etc.)", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 3. Nombre Conduites
                            Text(text = "3. Nombre Conduites :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (conduitCount > 1) {
                                            conduitCount--
                                            onSaveTrack(buildUpdatedTrack().copy(conduitCount = conduitCount))
                                        }
                                    },
                                    modifier = Modifier.size(36.dp),
                                    contentPadding = ButtonDefaults.TextButtonContentPadding
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Moins", modifier = Modifier.size(16.dp))
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.width(60.dp).height(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(text = "$conduitCount", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        conduitCount++
                                        onSaveTrack(buildUpdatedTrack().copy(conduitCount = conduitCount))
                                    },
                                    modifier = Modifier.size(36.dp),
                                    contentPadding = ButtonDefaults.TextButtonContentPadding
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Plus", modifier = Modifier.size(16.dp))
                                }

                                Text(
                                    text = if (conduitCount == 1) "1 conduite" else "$conduitCount conduites",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 4. Diamètre Conduites (mm) (choix multiple) : Ø 30, Ø 40, Ø 50, Ø 80, Ø 100, autre
                            Text(text = "4. Diamètre Conduites (mm) (Choix multiple) :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                standardDiameters.forEach { dia ->
                                    val isSelected = selectedDiameters.contains(dia)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            val newSet = selectedDiameters.toMutableSet()
                                            if (isSelected) {
                                                if (newSet.size > 1) newSet.remove(dia)
                                            } else {
                                                newSet.add(dia)
                                            }
                                            selectedDiameters = newSet
                                            onSaveTrack(buildUpdatedTrack().copy(conduitDiameters = newSet.toList()))
                                        },
                                        label = { Text(dia, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) }
                                    )
                                }

                                // Diamètres personnalisés déjà saisis
                                selectedDiameters.filter { it !in standardDiameters }.forEach { customDia ->
                                    FilterChip(
                                        selected = true,
                                        onClick = {
                                            val newSet = selectedDiameters.toMutableSet()
                                            newSet.remove(customDia)
                                            selectedDiameters = newSet
                                            onSaveTrack(buildUpdatedTrack().copy(conduitDiameters = newSet.toList()))
                                        },
                                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                        label = { Text(customDia, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                    )
                                }

                                // Bouton pour ajouter un diamètre personnalisé
                                OutlinedButton(
                                    onClick = { showAddCustomDiameter = !showAddCustomDiameter },
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = ButtonDefaults.TextButtonContentPadding
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Autre", fontSize = 11.sp)
                                }
                            }

                            if (showAddCustomDiameter) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customDiameterInput,
                                        onValueChange = { customDiameterInput = it },
                                        label = { Text("Ex: Ø 63, Ø 110...", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            val trimmed = customDiameterInput.trim()
                                            if (trimmed.isNotBlank()) {
                                                val formatted = if (trimmed.startsWith("Ø")) trimmed else "Ø $trimmed"
                                                val newSet = selectedDiameters.toMutableSet()
                                                newSet.add(formatted)
                                                selectedDiameters = newSet
                                                customDiameterInput = ""
                                                showAddCustomDiameter = false
                                                onSaveTrack(buildUpdatedTrack().copy(conduitDiameters = newSet.toList()))
                                            }
                                        },
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text("Ajouter", fontSize = 11.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 5. Longueur Conduite (récupérée automatique)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "5. Longueur (Auto) :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = distStr,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Type Aérien ou Façade
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Caractéristiques Trajet ${selectedType}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 1. Longueur automatique
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = "1. Longueur (Auto) :", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = distStr,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- 4. SECTION PHOTOS LE LONG DU TRAJET ---
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
                                val projName = remember { PhotoStorageManager.getProjectName(context) }
                                Text(
                                    text = "Dossier : releve-terrain/$projName/photos/",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        try {
                                            val file = PhotoStorageManager.createNewPhotoFile(context, track.id)
                                            tempCameraFile = file
                                            val uri = PhotoStorageManager.getUriForPhotoFile(context, file)
                                            takePictureLauncher.launch(uri)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            try {
                                                pickVisualMediaLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            } catch (ignored: Exception) {}
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = ButtonDefaults.TextButtonContentPadding
                                ) {
                                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Photo ici", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        pickVisualMediaLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = ButtonDefaults.TextButtonContentPadding
                                ) {
                                    Text("Galerie", fontSize = 11.sp)
                                }
                            }
                        }

                        if (track.photos.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(track.photos) { photo ->
                                    val resolvedFile = PhotoStorageManager.resolvePhotoFile(context, photo.photoPath)
                                    val itemDetails = remember(photo.photoPath) { PhotoStorageManager.getPhotoDetails(resolvedFile.absolutePath) }
                                    Box(
                                        modifier = Modifier
                                            .size(76.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .clickable { viewingPhotoPath = photo.photoPath }
                                    ) {
                                        AsyncImage(
                                            model = resolvedFile,
                                            contentDescription = "Photo trajet",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        if (itemDetails.width > 0) {
                                            Surface(
                                                shape = RoundedCornerShape(3.dp),
                                                color = Color.Black.copy(alpha = 0.7f),
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(bottom = 3.dp)
                                            ) {
                                                Text(
                                                    text = if (itemDetails.megaPixelsLabel.isNotEmpty()) itemDetails.megaPixelsLabel else "${itemDetails.width}x${itemDetails.height}",
                                                    color = Color.White,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }

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
                                text = "Prenez des photos le long du parcours : chaque photo est marquée par un point bleu sur la carte avec ses coordonnées GPS.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- 5. SECTION MODIFICATION MANUELLE & REDRESSEMENT ---
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Ajustement & Outils du tracé",
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
                                    onSaveTrack(buildUpdatedTrack())
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
                                    onSaveTrack(buildUpdatedTrack())
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

                // --- 6. SECTION SIMPLIFICATION ANTI-BRUIT GPS ---
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
                val currentViewingPhoto = viewingPhotoPath
                if (currentViewingPhoto != null) {
                    val resolvedPreview = PhotoStorageManager.resolvePhotoFile(context, currentViewingPhoto)
                    val photoDetails = PhotoStorageManager.getPhotoDetails(resolvedPreview.absolutePath)
                    Dialog(onDismissRequest = { viewingPhotoPath = null }) {
                        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Photo de l'infra linéaire",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Résolution : ${photoDetails.resolutionLabel}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (photoDetails.qualityLabel.isNotEmpty()) {
                                    Text(
                                        text = "Qualité : ${photoDetails.qualityLabel}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF16A34A),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = "Dossier : releve-terrain/projet/photos/${resolvedPreview.name}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                AsyncImage(
                                    model = resolvedPreview,
                                    contentDescription = "Photo infra linéaire",
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

                // Bouton principal d'enregistrement
                Button(
                    onClick = {
                        onSaveTrack(buildUpdatedTrack())
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Enregistrer les modifications", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

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
