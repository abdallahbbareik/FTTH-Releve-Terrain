package com.example.ui.components

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
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Titre, métriques et boutons d'action d'historique
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Modification tracé : $trackName",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "📍 $pointsCount sommets  •  📏 $distStr",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Bouton Rétablir d'origine (🔄) si disponible
                if (onResetOriginal != null) {
                    IconButton(
                        onClick = onResetOriginal,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Rétablir le tracé initial",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Bouton Annuler/Défaire dernière modif (↶)
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.size(32.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (canUndo) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Undo,
                                contentDescription = "Annuler la dernière modification",
                                tint = if (canUndo) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Ligne 2 : Outils Simplifier & Redresser
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Bouton Redresser
                Button(
                    onClick = onToggleStraighten,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isStraightenActive) Color(0xFFF59E0B) else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isStraightenActive) Color.Black else MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.height(30.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Icon(Icons.Default.LinearScale, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isStraightenActive) "📏 Redresser actif" else "📏 Redresser",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Tolérances pour simplifier
                val tolerances = listOf(3.0, 5.0, 10.0, 15.0, 20.0)
                for (t in tolerances) {
                    val isSelected = selectedTolerance == t
                    OutlinedButton(
                        onClick = { onToleranceChanged(t) },
                        modifier = Modifier.weight(1f).height(30.dp),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("${t.toInt()}m", fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal)
                    }
                }

                // Bouton Simplifier
                Button(
                    onClick = onSimplify,
                    modifier = Modifier.height(30.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("✂ Simplifier", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Guide dynamique et actions sur le sommet sélectionné
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isStraightenActive || selectedVertexIdx != null) Color(0xFFFEF3C7)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val guideText = when {
                        isStraightenActive -> if (straightenStartIdx == null) "📏 Touchez le 1er sommet (orange) à aligner" else "Sommet #${straightenStartIdx + 1} sélectionné. Touchez le 2ème sommet pour aligner."
                        selectedVertexIdx != null -> "📍 Sommet #${selectedVertexIdx + 1} sélectionné (glissez pour déplacer)"
                        else -> "🖐 Glissez directement un sommet sur la carte pour modifier sa position."
                    }
                    val textColor = when {
                        isStraightenActive || selectedVertexIdx != null -> Color(0xFF92400E)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        text = guideText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )

                    if (!isStraightenActive && selectedVertexIdx != null && pointsCount > 2 && onDeleteSelectedVertex != null) {
                        OutlinedButton(
                            onClick = onDeleteSelectedVertex,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                            modifier = Modifier.height(26.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Supprimer", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Ligne 4 : Boutons principaux : Annuler & Valider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Annuler", fontSize = 12.sp)
                }

                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    modifier = Modifier.weight(1.4f).height(40.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Valider ($pointsCount pts)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
