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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timeline
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
import com.example.data.storage.StoredTrack
import java.io.File

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FileUpload

@Composable
fun ExportReportDialog(
    nodes: List<FtthNodeEntity>,
    links: List<FtthLinkEntity>,
    tracks: List<StoredTrack> = emptyList(),
    onExportGeoJson: (Context, (File) -> Unit) -> Unit,
    onExportTracksGeoJson: (Context, (File) -> Unit) -> Unit,
    onExportNodesByType: (Context, (File) -> Unit) -> Unit,
    onExportKmz: (Context, (File) -> Unit) -> Unit,
    onExportCsv: (Context, (File) -> Unit) -> Unit,
    onExportKml: (Context, (File) -> Unit) -> Unit,
    onExportZip: (Context, (File) -> Unit) -> Unit,
    onShareFile: (Context, File, String) -> Unit,
    onImportFile: (Uri) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }
    var currentOperationLabel by remember { mutableStateOf("Génération du fichier sur le disque...") }
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var exportFeedback by remember { mutableStateOf<String?>(null) }

    val totalNodes = nodes.size
    val totalPhotos = nodes.sumOf { it.photos.size }
    val conformesCount = nodes.count { it.etat == NodeConformity.CONFORME }
    val nonConformesCount = nodes.count { it.etat == NodeConformity.NON_CONFORME }

    val polesCount = nodes.count { it.type == FtthNodeType.POTEAU }
    val chambersCount = nodes.count { it.type == FtthNodeType.CHAMBRE }
    val boitiersCount = nodes.count { it.type == FtthNodeType.BOITIER }
    val sroCount = nodes.count { it.type == FtthNodeType.SRO }
    val buildingsCount = nodes.count { it.type == FtthNodeType.IMMEUBLE }
    val villasCount = nodes.count { it.type == FtthNodeType.VILLA }
    val totalCableMeters = links.sumOf { it.lengthMeters }.toInt()
    val totalTracksCount = tracks.distinctBy { it.id }.size
    val totalTrackMeters = tracks.distinctBy { it.id }.sumOf { it.totalDistanceMeters }.toInt()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportFile(uri)
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .testTag("export_report_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Rapport, Exports & Imports FTTH",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "SIG, KMZ 8 couches, Import KMZ/GeoJSON dynamique",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==================== SECTION IMPORT KMZ / GEOJSON ====================
                Text(
                    text = "Importation de données géospatiales (KMZ, KML, GeoJSON) :",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = {
                        try {
                            importLauncher.launch(arrayOf("*/*"))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("import_file_button")
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Importer un fichier KMZ, KML ou GeoJSON", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(14.dp))

                Spacer(modifier = Modifier.height(12.dp))

                // Stats Metrics Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatMetricCard("Nœuds", totalNodes.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatMetricCard("Câbles", "${links.size} ($totalCableMeters m)", Color(0xFF0284C7), Modifier.weight(1.1f))
                    StatMetricCard("Traces GPS", "$totalTracksCount ($totalTrackMeters m)", Color(0xFFEA580C), Modifier.weight(1.1f))
                    StatMetricCard("Photos", totalPhotos.toString(), Color(0xFF7C3AED), Modifier.weight(0.9f))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Engineering details Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Couches et données du relevé",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "$conformesCount conformes / $nonConformesCount anom.",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = if (nonConformesCount > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        RowTypeCount("Poteaux & appuis aériens", polesCount)
                        RowTypeCount("Chambres de tirage", chambersCount)
                        RowTypeCount("Boîtiers FTTH (BPE / PBO / PB)", boitiersCount)
                        RowTypeCount("SRO (Sous-Répartiteurs Optiques)", sroCount)
                        RowTypeCount("Immeubles collectifs", buildingsCount)
                        RowTypeCount("Villas & pavillons individuels", villasCount)
                        RowTypeCount("Liaisons fibre optique", "${links.size} (${totalCableMeters} m)")
                        RowTypeCount("Trajets & traces GPS enregistrés", "$totalTracksCount (${totalTrackMeters} m)")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Feedback status
                if (exportFeedback != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDCFCE7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
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
                                        text = "${exportedFile!!.name} • ${exportedFile!!.length() / 1024} Ko",
                                        fontSize = 11.sp,
                                        color = Color(0xFF166534).copy(alpha = 0.85f)
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
                                            exportedFile!!.name.endsWith(".kmz") -> "application/vnd.google-earth.kmz"
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
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (isExporting) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = currentOperationLabel, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ==================== SECTION 1 : DOSSIER COMPLET ZIP ====================
                Text(
                    text = "Pack complet (Tout-en-un) :",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        isExporting = true
                        currentOperationLabel = "Génération du dossier ZIP complet avec toutes les couches..."
                        onExportZip(context) { file ->
                            isExporting = false
                            exportedFile = file
                            exportFeedback = "Dossier ZIP complet généré (Toutes couches KMZ, GeoJSON, par type de nœud, trajets, photos) !"
                            onShareFile(context, file, "application/zip")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_zip_button")
                ) {
                    Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Dossier ZIP Complet (Toutes couches + Photos)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==================== SECTION 2 : CARTOGRAPHIE GOOGLE EARTH ====================
                Text(
                    text = "Cartographie Google Earth (KMZ / KML) :",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            isExporting = true
                            currentOperationLabel = "Génération du fichier KMZ avec toutes les 8 couches..."
                            onExportKmz(context) { file ->
                                isExporting = false
                                exportedFile = file
                                exportFeedback = "Fichier KMZ '${file.name}' généré avec toutes les couches !"
                                onShareFile(context, file, "application/vnd.google-earth.kmz")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("KMZ 8 Couches", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            isExporting = true
                            currentOperationLabel = "Génération du fichier KML structuré..."
                            onExportKml(context) { file ->
                                isExporting = false
                                exportedFile = file
                                exportFeedback = "Fichier KML '${file.name}' généré avec succès !"
                                onShareFile(context, file, "application/vnd.google-earth.kml+xml")
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("KML Couches", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==================== SECTION 3 : SIG GEOJSON ====================
                Text(
                    text = "Couches SIG GeoJSON :",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Bouton 1 : GeoJSON des Trajets GPS
                OutlinedButton(
                    onClick = {
                        isExporting = true
                        currentOperationLabel = "Génération du fichier GeoJSON des trajets GPS..."
                        onExportTracksGeoJson(context) { file ->
                            isExporting = false
                            exportedFile = file
                            exportFeedback = "Fichier Trajets GPS GeoJSON '${file.name}' généré ($totalTracksCount trajets) !"
                            onShareFile(context, file, "application/geo+json")
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Timeline, contentDescription = null, tint = Color(0xFFEA580C), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Trajets & Traces GPS (GeoJSON)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bouton 2 : GeoJSON par type de nœud
                OutlinedButton(
                    onClick = {
                        isExporting = true
                        currentOperationLabel = "Génération des fichiers GeoJSON par type de nœud..."
                        onExportNodesByType(context) { file ->
                            isExporting = false
                            exportedFile = file
                            exportFeedback = "GeoJSON par type de nœud générés dans l'archive '${file.name}' !"
                            onShareFile(context, file, "application/zip")
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export GeoJSON par Type de Nœud (Poteau, Chambre, PBO...)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bouton 3 : GeoJSON global
                OutlinedButton(
                    onClick = {
                        isExporting = true
                        currentOperationLabel = "Génération du GeoJSON global complet..."
                        onExportGeoJson(context) { file ->
                            isExporting = false
                            exportedFile = file
                            exportFeedback = "GeoJSON complet '${file.name}' généré (tous les champs inclus) !"
                            onShareFile(context, file, "application/geo+json")
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GeoJSON Global (Nœuds + Câbles + Traces)", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==================== SECTION 4 : BORDEREAU TABULAIRE CSV ====================
                Text(
                    text = "Bordereau tabulaire (Excel / SIG) :",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        isExporting = true
                        currentOperationLabel = "Génération du bordereau CSV complet..."
                        onExportCsv(context) { file ->
                            isExporting = false
                            exportedFile = file
                            exportFeedback = "Bordereau CSV '${file.name}' généré (35 colonnes complètes) !"
                            onShareFile(context, file, "text/csv")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_csv_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bordereau CSV (Tous les champs remplis)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1)
            Text(text = label, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun RowTypeCount(label: String, count: Any) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
        Text(text = count.toString(), style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
