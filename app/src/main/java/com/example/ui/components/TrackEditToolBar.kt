package com.example.ui.components

import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import com.example.data.local.FtthNodeEntity
import com.example.data.storage.TrackPoint
import com.example.data.util.TrackGeometryHelper
import java.util.Locale

import androidx.compose.material.icons.filled.AddAPhoto
import com.example.data.storage.TrackPhoto

@Composable
fun ManualTrackEditorBar(
    points: List<TrackPoint>,
    photos: List<TrackPhoto> = emptyList(),
    onAddPhoto: (() -> Unit)? = null,
    onUndoLastPoint: () -> Unit,
    onSaveTrack: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dist = TrackGeometryHelper.computeTotalDistanceMeters(points)
    val distStr = if (dist < 1000.0) "${dist.toInt()} m" else String.format(Locale.FRANCE, "%.2f km", dist / 1000.0)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("manual_track_editor_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (points.isEmpty()) "Tracé Manuel : Touchez un nœud ou la carte" else "Tracé Manuel en cours",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (points.isEmpty()) {
                            "Accrochage automatique aux nœuds actif 🧲"
                        } else {
                            "📍 ${points.size} sommets  •  📏 $distStr  •  📷 ${photos.size} photos"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(0.9f),
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Annuler", fontSize = 10.sp)
                }

                if (onAddPhoto != null) {
                    Button(
                        onClick = onAddPhoto,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.weight(1.1f),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Photo ici", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                OutlinedButton(
                    onClick = onUndoLastPoint,
                    enabled = points.isNotEmpty(),
                    modifier = Modifier.weight(0.9f),
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Retirer", fontSize = 10.sp)
                }

                Button(
                    onClick = onSaveTrack,
                    enabled = points.size >= 2,
                    modifier = Modifier.weight(1.2f),
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Enregistrer", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun StraightenEditorBar(
    trackName: String,
    selectedIndices: Pair<Int?, Int?>,
    onApplyStraighten: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasBoth = selectedIndices.first != null && selectedIndices.second != null

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("straighten_editor_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LinearScale,
                    contentDescription = null,
                    tint = Color(0xFFEAB308),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Redresser en ligne droite : $trackName",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val statusText = if (selectedIndices.first == null) {
                        "Touchez le 1er sommet sur la carte"
                    } else if (selectedIndices.second == null) {
                        "Sommet 1 (#${selectedIndices.first}) sélectionné. Touchez le 2ème sommet."
                    } else {
                        "Tronçon entre sommet #${selectedIndices.first} et #${selectedIndices.second}"
                    }
                    Text(text = statusText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Quitter", fontSize = 12.sp)
                }

                Button(
                    onClick = onApplyStraighten,
                    enabled = hasBoth,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    modifier = Modifier.weight(1.5f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Redresser ce tronçon", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MoveNodeEditorBar(
    node: FtthNodeEntity,
    tempPosition: Pair<Double, Double>?,
    onConfirmMove: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("move_node_editor_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NearMe,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Déplacer le nœud ${node.id} (${node.type.label})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val statusText = if (tempPosition == null) {
                        "Touchez la carte au nouvel emplacement souhaité"
                    } else {
                        "Nouvelle position : ${String.format(Locale.FRANCE, "%.6f, %.6f", tempPosition.first, tempPosition.second)}"
                    }
                    Text(text = statusText, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Annuler", fontSize = 12.sp)
                }

                Button(
                    onClick = onConfirmMove,
                    enabled = tempPosition != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    modifier = Modifier.weight(1.4f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Valider position", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MoveVertexEditorBar(
    trackName: String,
    vertexIndex: Int?,
    tempPosition: Pair<Double, Double>?,
    onConfirmMove: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("move_vertex_editor_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NearMe,
                    contentDescription = null,
                    tint = Color(0xFFEAB308),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Déplacer un sommet : $trackName",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val statusText = if (vertexIndex == null) {
                        "Touchez le sommet à déplacer sur la carte"
                    } else if (tempPosition == null) {
                        "Sommet #$vertexIndex sélectionné. Touchez le nouvel endroit sur la carte."
                    } else {
                        "Nouvel emplacement sélectionné pour le sommet #$vertexIndex"
                    }
                    Text(text = statusText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Annuler", fontSize = 12.sp)
                }

                Button(
                    onClick = onConfirmMove,
                    enabled = vertexIndex != null && tempPosition != null,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    modifier = Modifier.weight(1.4f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Valider sommet", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun TrackAdjustmentBottomBar(
    trackName: String,
    pointsCount: Int,
    distanceMeters: Double,
    selectedTolerance: Double = 5.0,
    onToleranceChanged: (Double) -> Unit = {},
    onSimplify: () -> Unit = {},
    isStraightenActive: Boolean = false,
    onToggleStraighten: () -> Unit = {},
    straightenStartIdx: Int? = null,
    isDeleteActive: Boolean = false,
    onToggleDelete: () -> Unit = {},
    isAddActive: Boolean = false,
    onToggleAdd: () -> Unit = {},
    canUndo: Boolean = false,
    onUndo: () -> Unit,
    selectedVertexIdx: Int? = null,
    onDeleteSelectedVertex: (() -> Unit)? = null,
    onResetOriginal: (() -> Unit)? = null,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val distStr = if (distanceMeters < 1000.0) "${distanceMeters.toInt()} m" else String.format(Locale.FRANCE, "%.2f km", distanceMeters / 1000.0)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .testTag("track_adjustment_bottom_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left column: Controls, chips, buttons, notification
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$trackName ($pointsCount pts • $distStr)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        fontSize = 12.sp
                    )
                }

                // Row: Tolerance chips (3, 5, 10, 15) + Simplifier
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val tolerances = listOf(3.0, 5.0, 10.0, 15.0)
                    for (t in tolerances) {
                        val isSelected = selectedTolerance == t
                        OutlinedButton(
                            onClick = { onToleranceChanged(t) },
                            modifier = Modifier.weight(1f).height(28.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("${t.toInt()}", fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }

                    Button(
                        onClick = onSimplify,
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Simplifier", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Row: Redresser, Supprimer, Ajouter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = onToggleStraighten,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isStraightenActive) Color(0xFFF59E0B) else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (isStraightenActive) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.weight(1f).height(28.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (isStraightenActive) "Redresser ✓" else "Redresser", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onToggleDelete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDeleteActive) Color(0xFFDC2626) else MaterialTheme.colorScheme.errorContainer,
                            contentColor = if (isDeleteActive) Color.White else MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.weight(1f).height(28.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (isDeleteActive) "Supprimer ✓" else "Supprimer", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onToggleAdd,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAddActive) Color(0xFF16A34A) else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (isAddActive) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.weight(1f).height(28.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (isAddActive) "Ajouter ✓" else "Ajouter", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Notification / Guide text
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isStraightenActive || selectedVertexIdx != null || isDeleteActive || isAddActive) Color(0xFFFEF3C7)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val guideText = when {
                        isDeleteActive -> "Mode Suppression : Touchez un sommet"
                        isAddActive -> "Mode Ajout : Touchez la carte"
                        isStraightenActive -> if (straightenStartIdx == null) "Touchez 1er sommet" else "Touchez 2ème sommet"
                        selectedVertexIdx != null -> "Sommet #${selectedVertexIdx + 1} sélectionné"
                        else -> "Glissez un sommet pour modifier"
                    }
                    Text(
                        text = guideText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF92400E),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        maxLines = 1
                    )
                }
            }

            // Right vertical column of 4 square/rounded action buttons: reload, undo, cancel, validate
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Reload / Reset
                IconButton(
                    onClick = { onResetOriginal?.invoke() },
                    enabled = onResetOriginal != null,
                    modifier = Modifier.size(36.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Reload",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 2. Undo / Retour en arrière
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.size(36.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (canUndo) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Undo,
                                contentDescription = "Retour en arrière",
                                tint = if (canUndo) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 3. Annuler
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(36.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Annuler",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 4. Valider
                IconButton(
                    onClick = onConfirm,
                    modifier = Modifier.size(36.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF16A34A)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Valider",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
