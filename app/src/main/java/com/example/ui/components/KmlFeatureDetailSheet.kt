package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.FtthNodeEntity
import com.example.data.storage.StoredTrack
import org.json.JSONObject
import java.util.Locale

/**
 * Modèle scellé représentant un objet KML/KMZ sélectionné en mode consultation (View Mode).
 */
sealed class KmlFeatureItem {
    data class PointFeature(val node: FtthNodeEntity) : KmlFeatureItem()
    data class LineFeature(val track: StoredTrack) : KmlFeatureItem()
}

/**
 * Fiche de consultation dédiée aux objets KML/KMZ natifs (Mode Affichage / View Mode).
 * Affiche l'ensemble des champs, attributs SIG (ExtendedData, SimpleData, description),
 * la géométrie et permet de centrer sur la carte, sans proposer de formulaire d'édition FTTH.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KmlFeatureDetailSheet(
    feature: KmlFeatureItem,
    onCenterOnMap: (lat: Double, lon: Double) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var fieldSearchQuery by remember { mutableStateOf("") }

    // Extraction des métadonnées de base
    val title: String
    val folderName: String
    val colorHex: String?
    val isPoint: Boolean
    val centerLat: Double
    val centerLon: Double
    val totalLengthMeters: Double?
    val verticesCount: Int?

    when (feature) {
        is KmlFeatureItem.PointFeature -> {
            isPoint = true
            val node = feature.node
            title = node.name.ifBlank { node.id }
            folderName = node.folderName
            colorHex = node.colorHex
            centerLat = node.latitude
            centerLon = node.longitude
            totalLengthMeters = null
            verticesCount = 1
        }
        is KmlFeatureItem.LineFeature -> {
            isPoint = false
            val track = feature.track
            title = track.name.ifBlank { track.id }
            folderName = track.folderName
            colorHex = track.colorHex
            val pts = if (track.points.isNotEmpty()) track.points else track.rawPoints
            centerLat = if (pts.isNotEmpty()) pts.first().latitude else 0.0
            centerLon = if (pts.isNotEmpty()) pts.first().longitude else 0.0
            totalLengthMeters = track.totalDistanceMeters
            verticesCount = pts.size
        }
    }

    val featureColor = remember(colorHex) {
        try {
            if (!colorHex.isNullOrBlank()) Color(android.graphics.Color.parseColor(colorHex))
            else Color(0xFF0284C7)
        } catch (_: Exception) {
            Color(0xFF0284C7)
        }
    }

    // Extraction de TOUS les attributs KML (kmlProperties JSON, notes, etc.)
    val allAttributes = remember(feature) {
        val map = linkedMapOf<String, String>()

        fun extractFromJson(jsonStr: String) {
            if (jsonStr.isBlank()) return
            try {
                val json = JSONObject(jsonStr)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = json.optString(k, "")
                    if (k.isNotBlank() && v.isNotBlank() && !map.containsKey(k)) {
                        map[k] = v
                    }
                }
            } catch (_: Exception) {}
        }

        fun extractFromNotes(notes: String) {
            if (notes.isBlank()) return
            val lines = notes.split("\n")
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("•") && trimmed.contains(":")) {
                    val withoutBullet = trimmed.removePrefix("•").trim()
                    val colonIdx = withoutBullet.indexOf(":")
                    if (colonIdx > 0) {
                        val k = withoutBullet.substring(0, colonIdx).trim()
                        val v = withoutBullet.substring(colonIdx + 1).trim()
                        if (k.isNotBlank() && v.isNotBlank() && !map.containsKey(k)) {
                            map[k] = v
                        }
                    }
                } else if (trimmed.contains("=") && !trimmed.startsWith("<")) {
                    val eqIdx = trimmed.indexOf("=")
                    if (eqIdx > 0) {
                        val k = trimmed.substring(0, eqIdx).trim()
                        val v = trimmed.substring(eqIdx + 1).trim()
                        if (k.isNotBlank() && v.isNotBlank() && !map.containsKey(k)) {
                            map[k] = v
                        }
                    }
                }
            }
        }

        when (feature) {
            is KmlFeatureItem.PointFeature -> {
                extractFromJson(feature.node.kmlProperties)
                extractFromNotes(feature.node.notes)
            }
            is KmlFeatureItem.LineFeature -> {
                extractFromJson(feature.track.kmlProperties)
                extractFromNotes(feature.track.notes)
            }
        }

        // Si aucun champ n'a été trouvé, ajouter les métadonnées de base
        if (map.isEmpty()) {
            map["Nom"] = title
            if (folderName.isNotBlank()) map["Dossier KML"] = folderName
            if (colorHex != null) map["Couleur"] = colorHex
            if (isPoint) {
                map["Latitude"] = String.format(Locale.ROOT, "%.6f", centerLat)
                map["Longitude"] = String.format(Locale.ROOT, "%.6f", centerLon)
            } else {
                map["Longueur"] = if ((totalLengthMeters ?: 0.0) < 1000.0) {
                    "${(totalLengthMeters ?: 0.0).toInt()} m"
                } else {
                    String.format(Locale.FRANCE, "%.2f km", (totalLengthMeters ?: 0.0) / 1000.0)
                }
                map["Nombre de sommets"] = "${verticesCount ?: 0}"
            }
        }

        map.toList()
    }

    val filteredAttributes = remember(allAttributes, fieldSearchQuery) {
        if (fieldSearchQuery.isBlank()) allAttributes
        else {
            val q = fieldSearchQuery.trim().lowercase(Locale.ROOT)
            allAttributes.filter { (k, v) ->
                k.lowercase(Locale.ROOT).contains(q) || v.lowercase(Locale.ROOT).contains(q)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("kml_feature_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(bottom = 16.dp)
        ) {
            // --- EN-TÊTE DU MODE CONSULTATION KML ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icône marqueur personnalisée selon le type (Point ou Ligne)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(featureColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPoint) Icons.Default.LocationOn else Icons.Default.Route,
                        contentDescription = null,
                        tint = featureColor,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "VIEW MODE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            color = featureColor.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isPoint) "Point KML" else "Ligne KML",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = featureColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (folderName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = folderName,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("kml_feature_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // --- CARTE D'ACTION RAPIDE : GÉOMÉTRIE ET CENTRAGE ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (isPoint) {
                            Text(
                                text = "Coordonnées GPS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${String.format(Locale.ROOT, "%.6f", centerLat)}, ${String.format(Locale.ROOT, "%.6f", centerLon)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            val distLabel = if ((totalLengthMeters ?: 0.0) < 1000.0) {
                                "${(totalLengthMeters ?: 0.0).toInt()} m"
                            } else {
                                String.format(Locale.FRANCE, "%.2f km", (totalLengthMeters ?: 0.0) / 1000.0)
                            }
                            Text(
                                text = "Géométrie du tracé",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$distLabel • ${verticesCount ?: 0} sommets",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Bouton copier coordonnées
                        IconButton(
                            onClick = {
                                val textToCopy = if (isPoint) {
                                    "${centerLat},${centerLon}"
                                } else {
                                    "$title ($totalLengthMeters m)"
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("KML Info", textToCopy))
                                Toast.makeText(context, "Copié dans le presse-papiers", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copier",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Bouton centrer sur la carte
                        Button(
                            onClick = {
                                onCenterOnMap(centerLat, centerLon)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Centrer", fontSize = 12.sp)
                        }
                    }
                }
            }

            // --- SECTION ATTRIBUTS / CHAMPS KML (TABLE DE PROPRIÉTÉS) ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tous les champs KML & SIG",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${filteredAttributes.size} champ${if (filteredAttributes.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // Champ de recherche rapide si plus de 4 attributs
                if (allAttributes.size > 4) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = fieldSearchQuery,
                        onValueChange = { fieldSearchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("kml_field_search_input"),
                        placeholder = { Text("Rechercher un champ ou une valeur...", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (fieldSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { fieldSearchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Effacer",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Liste déroulante des attributs KML
                if (filteredAttributes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (fieldSearchQuery.isNotBlank()) "Aucun champ ne correspond à « $fieldSearchQuery »" else "Aucun attribut étendu présent pour cet objet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredAttributes) { (key, value) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText(key, value))
                                        Toast.makeText(context, "« $key » copié", Toast.LENGTH_SHORT).show()
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(0.42f)) {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column(modifier = Modifier.weight(0.58f)) {
                                        Text(
                                            text = value,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bouton inférieur de fermeture
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Fermer la consultation")
                }
            }
        }
    }
}
