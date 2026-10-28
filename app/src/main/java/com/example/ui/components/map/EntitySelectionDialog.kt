package com.example.ui.components.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.isNativeKmlFeature
import com.example.data.storage.isNativeKmlFeature
import java.util.Locale

/**
 * Feuille de sélection d'entités superposées ou co-localisées au même point sur la carte.
 * Permet à l'utilisateur de choisir précisément quelle entité modifier ou consulter
 * lorsqu'un clic touche plusieurs objets à la fois.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntitySelectionDialog(
    items: List<NearbyEntityItem>,
    onSelectEntity: (MapSelectableEntity) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("entity_selection_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // En-tête
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "Entités à cet emplacement",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${items.size} entités se superposent sur ce point",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_entity_selection_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Plusieurs objets partagent les mêmes coordonnées. Touchez l'entité que vous souhaitez ouvrir ou modifier :",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Liste des entités superposées
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(items) { item ->
                    EntitySelectionRow(
                        item = item,
                        onClick = {
                            onSelectEntity(item.entity)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Annuler")
            }
        }
    }
}

@Composable
private fun EntitySelectionRow(
    item: NearbyEntityItem,
    onClick: () -> Unit
) {
    val displayName: String
    val classificationLabel: String
    val folderOrSubtitle: String
    val itemColor: Color
    val iconVector = when (val e = item.entity) {
        is MapSelectableEntity.NodeEntity -> {
            val node = e.node
            val isNative = node.isNativeKmlFeature()
            displayName = node.name.ifBlank { node.id }
            classificationLabel = if (isNative) "Point" else node.type.label
            folderOrSubtitle = when {
                node.folderName.isNotBlank() -> "Dossier : ${node.folderName}"
                node.address.isNotBlank() -> node.address
                else -> node.status.name
            }
            itemColor = try {
                if (!node.colorHex.isNullOrBlank()) Color(android.graphics.Color.parseColor(node.colorHex))
                else Color(0xFF0284C7)
            } catch (_: Exception) {
                Color(0xFF0284C7)
            }
            if (isNative) Icons.Default.LocationOn else Icons.Default.Place
        }
        is MapSelectableEntity.TrackEntity -> {
            val track = e.track
            val isNative = track.isNativeKmlFeature()
            val isPolygon = track.type.contains("Polygone", ignoreCase = true)
            displayName = track.name.ifBlank { track.id }
            classificationLabel = when {
                isPolygon -> "Polygone"
                isNative -> "Polyligne"
                else -> "Infra [${track.type}]"
            }
            val distLabel = if (track.totalDistanceMeters < 1000.0) "${track.totalDistanceMeters.toInt()} m" else String.format(Locale.FRANCE, "%.2f km", track.totalDistanceMeters / 1000.0)
            folderOrSubtitle = if (track.folderName.isNotBlank()) "Dossier : ${track.folderName} • $distLabel" else distLabel
            itemColor = try {
                if (!track.colorHex.isNullOrBlank()) Color(android.graphics.Color.parseColor(track.colorHex))
                else if (isPolygon) Color(0xFFD97706) else Color(0xFF2563EB)
            } catch (_: Exception) {
                Color(0xFF2563EB)
            }
            if (isPolygon) Icons.Default.Layers else Icons.Default.Route
        }
        is MapSelectableEntity.LinkEntity -> {
            val link = e.link
            displayName = "${link.id} (${link.capacityFO} FO)"
            classificationLabel = "Câble"
            folderOrSubtitle = "${link.cableType} • ${link.installationType}"
            itemColor = Color(0xFF16A34A)
            Icons.Default.Cable
        }
    }

    val distanceText = if (item.distanceMeters < 0.8) {
        "Emplacement exact (0 m)"
    } else {
        "Distance : ${item.distanceMeters.toInt()} m"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("entity_select_item_${displayName.replace(" ", "_")}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(itemColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = itemColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Surface(
                        color = itemColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = classificationLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = itemColor,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = folderOrSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = distanceText,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Sélectionner",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
