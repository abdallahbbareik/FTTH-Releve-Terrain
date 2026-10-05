package com.example.ui.components

import android.content.Context
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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.local.NodeConformity
import com.example.data.local.NodeStatus
import java.io.File

@Composable
fun ExportReportDialog(
    nodes: List<FtthNodeEntity>,
    links: List<FtthLinkEntity>,
    onExportGeoJson: (Context, (File) -> Unit) -> Unit,
    onExportCsv: (Context, (File) -> Unit) -> Unit,
    onExportKml: (Context, (File) -> Unit) -> Unit,
    onExportZip: (Context, (File) -> Unit) -> Unit,
    onShareFile: (Context, File, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var exportFeedback by remember { mutableStateOf<String?>(null) }

    val totalNodes = nodes.size
    val totalPhotos = nodes.sumOf { it.photos.size }
    val conformesCount = nodes.count { it.etat == NodeConformity.CONFORME }
    val nonConformesCount = nodes.count { it.etat == NodeConformity.NON_CONFORME }
    val aPoserCount = nodes.count { it.status == NodeStatus.A_POSER }
    val existantCount = nodes.count { it.status == NodeStatus.EXISTANT }

    val polesCount = nodes.count { it.type == FtthNodeType.POTEAU }
    val chambersCount = nodes.count { it.type == FtthNodeType.CHAMBRE }
    val boitiersCount = nodes.count { it.type == FtthNodeType.BOITIER }
    val sroCount = nodes.count { it.type == FtthNodeType.SRO }
    val buildingsCount = nodes.count { it.type == FtthNodeType.IMMEUBLE }
    val villasCount = nodes.count { it.type == FtthNodeType.VILLA }
    val totalCableMeters = links.sumOf { it.lengthMeters }.toInt()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .testTag("export_report_dialog")
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
                        imageVector = Icons.Default.Assessment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Rapport & Export Réel SIG",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats Metrics Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatMetricCard("Nœuds", totalNodes.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatMetricCard("Conformes", conformesCount.toString(), Color(0xFF16A34A), Modifier.weight(1f))
                    StatMetricCard("Anomalies", nonConformesCount.toString(), Color(0xFFDC2626), Modifier.weight(1f))
                    StatMetricCard("Photos", totalPhotos.toString(), Color(0xFF7C3AED), Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Details Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Synthèse d'ingénierie FTTH",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        RowTypeCount("Poteaux & appuis aériens", polesCount)
                        RowTypeCount("Chambres de tirage", chambersCount)
                        RowTypeCount("Boîtiers FTTH (BPE / PBO / PB)", boitiersCount)
                        RowTypeCount("SRO (Sous-Répartiteur Optique)", sroCount)
                        RowTypeCount("Immeubles collectifs", buildingsCount)
                        RowTypeCount("Villas & pavillons", villasCount)
                        RowTypeCount("Métrage câbles fibre optique", "$totalCableMeters m")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Export Feedback status
                if (exportFeedback != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDCFCE7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = exportFeedback!!,
                                    color = Color(0xFF166534),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (exportedFile != null) {
                                    Text(
                                        text = "Taille : ${exportedFile!!.length() / 1024} Ko",
                                        fontSize = 11.sp,
                                        color = Color(0xFF166534).copy(alpha = 0.8f)
                                    )
                                }
                            }

                            if (exportedFile != null) {
                                IconButton(
                                    onClick = {
                                        val mime = when {
                                            exportedFile!!.name.endsWith(".geojson") -> "application/geo+json"
                                            exportedFile!!.name.endsWith(".csv") -> "text/csv"
                                            exportedFile!!.name.endsWith(".kml") -> "application/vnd.google-earth.kml+xml"
                                            else -> "application/zip"
                                        }
                                        onShareFile(context, exportedFile!!, mime)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Partager",
                                        tint = Color(0xFF166534)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (isExporting) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "Génération du fichier sur le disque...", fontSize = 12.sp)
                    }
                }

                // Export Buttons
                Text(
                    text = "Générer et partager les fichiers réels :",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Row 1: GeoJSON & CSV
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            isExporting = true
                            onExportGeoJson(context) { file ->
                                isExporting = false
                                exportedFile = file
                                exportFeedback = "Fichier '${file.name}' généré avec succès !"
                                onShareFile(context, file, "application/geo+json")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("GeoJSON / SIG", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            isExporting = true
                            onExportCsv(context) { file ->
                                isExporting = false
                                exportedFile = file
                                exportFeedback = "Bordereau '${file.name}' généré avec succès !"
                                onShareFile(context, file, "text/csv")
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_csv_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bordereau CSV", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row 2: KML & Complete ZIP
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            isExporting = true
                            onExportKml(context) { file ->
                                isExporting = false
                                exportedFile = file
                                exportFeedback = "Fichier KML '${file.name}' généré !"
                                onShareFile(context, file, "application/vnd.google-earth.kml+xml")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Google Earth KML", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            isExporting = true
                            onExportZip(context) { file ->
                                isExporting = false
                                exportedFile = file
                                exportFeedback = "Dossier ZIP complet (${totalPhotos} photos) généré !"
                                onShareFile(context, file, "application/zip")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_zip_button")
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dossier ZIP + Photos", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMetricCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = color)
        }
    }
}

@Composable
private fun RowTypeCount(label: String, count: Any) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(text = count.toString(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}
