package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.FtthNodeEntity
import com.example.data.util.CableSupportAnalysisResult
import com.example.data.util.IntersectedSupport
import com.example.ui.theme.TelecomCyan
import com.example.ui.theme.TelecomNavy
import java.util.Locale

/**
 * Dialogue dédié à la visualisation détaillée du parcours d'un câble et de ses supports traversés (Chambres & Poteaux)
 * du point de départ (0m) jusqu'à son arrivée.
 */
@Composable
fun CableSupportsDialog(
    analysisResult: CableSupportAnalysisResult,
    onDismiss: () -> Unit,
    onSelectNodeOnMap: (FtthNodeEntity) -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 1. En-tête
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = TelecomNavy,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Route,
                                    contentDescription = null,
                                    tint = TelecomCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Supports Traversés (Départ ➔ Fin)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            val cleanCableName = analysisResult.cableName.removePrefix("::").removePrefix(":").removePrefix(" :").trim()
                            Text(
                                text = "Câble : $cleanCableName",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TelecomCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cable_supports_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Synthèse KPI des supports et boîtiers traversés
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Total Supports
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${analysisResult.supports.size}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TelecomNavy
                            )
                            Text(
                                text = "Supports",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // SRO (si présent)
                        if (analysisResult.totalSroCount > 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${analysisResult.totalSroCount}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF9333EA)
                                )
                                Text(
                                    text = "SRO",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF9333EA)
                                )
                            }
                        }

                        // Chambres
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${analysisResult.totalChambresCount}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0284C7) // Bleu Chambre
                            )
                            Text(
                                text = "Chambres",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0284C7)
                            )
                        }

                        // Poteaux
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${analysisResult.totalPoteauxCount}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFD97706) // Ambre / Poteau
                            )
                            Text(
                                text = "Poteaux",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706)
                            )
                        }

                        // Boîtiers
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${analysisResult.totalBoitiersCount}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0D9488) // Teal / Sarcelle Boîtier
                            )
                            Text(
                                text = "Boîtiers",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0D9488)
                            )
                        }

                        // Longueur Câble
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format(Locale.ROOT, "%.0f m", analysisResult.totalLengthMeters),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF059669) // Émeraude
                            )
                            Text(
                                text = "Longueur",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Boutons d'export / copie d'itinéraire
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = {
                            val sb = StringBuilder()
                            sb.append("PARCOURS DU CÂBLE : ").append(analysisResult.cableName).append("\n")
                            sb.append("Longueur : ").append(String.format(Locale.ROOT, "%.0f m", analysisResult.totalLengthMeters)).append("\n")
                            sb.append("Supports traversés : ").append(analysisResult.supports.size).append(" (")
                                .append(analysisResult.totalChambresCount).append(" Chambres, ")
                                .append(analysisResult.totalPoteauxCount).append(" Poteaux, ")
                                .append(analysisResult.totalSroCount).append(" SRO, ")
                                .append(analysisResult.totalBoitiersCount).append(" Boîtiers)\n\n")

                            for (s in analysisResult.supports) {
                                sb.append("#").append(s.index).append(" [").append(s.supportTypeLabel).append("] ")
                                    .append(s.node.name)
                                    .append(" • Cumul : ").append(String.format(Locale.ROOT, "%.0f m", s.cumulativeDistanceMeters))
                                    .append(" (+").append(String.format(Locale.ROOT, "%.0f m", s.intervalDistanceMeters)).append(")\n")
                                for (b in s.hostedBoitiers) {
                                    sb.append("    └─ Boîtier : ").append(b.name).append("\n")
                                }
                            }

                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Supports Câble", sb.toString()))
                            Toast.makeText(context, "Itinéraire copié dans le presse-papiers", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copier le parcours", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Timeline des supports du départ à la fin
                if (analysisResult.supports.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aucune chambre ni poteau détecté le long de ce câble dans un rayon de 1 m.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(analysisResult.supports) { support ->
                            SupportTimelineCard(
                                support = support,
                                onSelectOnMap = { selectedNode ->
                                    onSelectNodeOnMap(selectedNode)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportTimelineCard(
    support: IntersectedSupport,
    onSelectOnMap: (FtthNodeEntity) -> Unit
) {
    val typeColor = when {
        support.isSro -> Color(0xFF9333EA)     // Violet SRO
        support.isChambre -> Color(0xFF0284C7) // Bleu Chambre
        support.isPoteau -> Color(0xFFD97706)  // Ambre Poteau
        support.isBoitier -> Color(0xFF0D9488) // Teal Boîtier
        else -> Color(0xFF7C3AED)              // Violet Autre
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectOnMap(support.node) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge d'ordre (1, 2, 3...)
                Surface(
                    shape = CircleShape,
                    color = typeColor.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, typeColor),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${support.index}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = typeColor
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Infos du support
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = typeColor,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = support.supportTypeLabel,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }

                        Text(
                            text = support.node.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Cumul : ${String.format(Locale.ROOT, "%.0f m", support.cumulativeDistanceMeters)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(+${String.format(Locale.ROOT, "%.0f m", support.intervalDistanceMeters)})",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Bouton de centrage carte
                IconButton(
                    onClick = { onSelectOnMap(support.node) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "Voir sur la carte",
                        tint = TelecomCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Section Boîtier(s) hébergé(s) sur ce support
            if (support.hostedBoitiers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "📦 Boîtier(s) hébergé(s) (${support.hostedBoitiers.size}) :",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D9488)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (boitier in support.hostedBoitiers) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0D9488).copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectOnMap(boitier) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = Color(0xFF0D9488),
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Text(
                                            text = "Boîtier",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }

                                    Text(
                                        text = boitier.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = "Voir le boîtier",
                                    tint = Color(0xFF0D9488),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
