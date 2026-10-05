package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.storage.StoredTrack
import com.example.data.storage.TrackPoint
import com.example.data.util.TrackGeometryHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TrackDetailDialog(
    track: StoredTrack,
    onDismiss: () -> Unit,
    onApplySimplification: (trackId: String, simplifiedPoints: List<TrackPoint>, tolerance: Double) -> Unit,
    onRestoreOriginal: (trackId: String) -> Unit,
    onStartStraightenMode: (track: StoredTrack) -> Unit,
    onDeleteTrack: (trackId: String) -> Unit
) {
    var selectedTolerance by remember { mutableDoubleStateOf(5.0) } // Tolérance par défaut 5m
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val currentPoints = if (track.points.isNotEmpty()) track.points else track.rawPoints

    // Calcul de l'aperçu Ramer-Douglas-Peucker en fonction de la tolérance sélectionnée
    val previewSimplifiedPoints = remember(currentPoints, selectedTolerance) {
        TrackGeometryHelper.simplifyRamerDouglasPeucker(currentPoints, selectedTolerance)
    }

    val previewDist = remember(previewSimplifiedPoints) {
        TrackGeometryHelper.computeTotalDistanceMeters(previewSimplifiedPoints)
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
                // Header
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(track.startTime)),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    val statusText = if (track.isSimplified) "Simplifié" else "Brut"
                    MetricCard("État", statusText, if (track.isSimplified) Color(0xFF059669) else Color(0xFF7C3AED), Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // --- SECTION 1 : SIMPLIFICATION ANTI-BRUIT GPS ---
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
                            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                        )

                        Text(
                            text = "Tolérance d'écart admissible :",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        // Chips 3m, 5m, 10m, 20m
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
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("apply_simplification_button")
                            ) {
                                Text("Valider la simplification", fontSize = 12.sp)
                            }

                            if (track.rawPoints.isNotEmpty() && track.isSimplified) {
                                OutlinedButton(
                                    onClick = { onRestoreOriginal(track.id) },
                                    modifier = Modifier.testTag("restore_original_track_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rétablir", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- SECTION 2 : REDRESSER ENTRE DEUX SOMMETS ---
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LinearScale,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Redresser entre deux sommets",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Touchez deux sommets sur la carte : tous les zigzags intermédiaires deviennent une ligne droite parfaite (idéal pour les rues rectilignes).",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        Button(
                            onClick = {
                                onStartStraightenMode(track)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("start_straighten_mode_button")
                        ) {
                            Icon(Icons.Default.LinearScale, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sélectionner 2 sommets sur la carte", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions finales : Supprimer le trajet
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
