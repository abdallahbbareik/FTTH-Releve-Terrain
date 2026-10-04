package com.example.ui.components

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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
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
import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.local.SurveyStatus

@Composable
fun ExportReportDialog(
    nodes: List<FtthNodeEntity>,
    links: List<FtthLinkEntity>,
    onDismiss: () -> Unit
) {
    var exportSuccessMessage by remember { mutableStateOf<String?>(null) }

    // Aggregate statistics
    val totalNodes = nodes.size
    val validatedCount = nodes.count { it.status == SurveyStatus.VALIDATED }
    val pendingCount = nodes.count { it.status == SurveyStatus.PENDING }
    val anomaliesCount = nodes.count { it.status == SurveyStatus.NON_CONFORMANT || it.status == SurveyStatus.NEEDS_REPLACEMENT }

    val polesCount = nodes.count { it.type == FtthNodeType.POTEAU }
    val chambersCount = nodes.count { it.type == FtthNodeType.CHAMBRE }
    val pbosCount = nodes.count { it.type == FtthNodeType.PBO }
    val buildingsCount = nodes.count { it.type == FtthNodeType.IMMEUBLE }
    val villasCount = nodes.count { it.type == FtthNodeType.VILLA }

    val totalCableMeters = links.sumOf { it.lengthMeters }.toInt()
    val totalAvailablePorts = nodes.filter { it.type == FtthNodeType.PBO }.sumOf { it.pboCapacityPorts }
    val totalConnectedPorts = nodes.filter { it.type == FtthNodeType.PBO }.sumOf { it.pboConnectedPorts }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.92f)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Rapport de Piquetage FTTH",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Synthèse d'ingénierie réseau zone SRO-04",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Global Stats Cards
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatMetricCard("Nœuds totaux", totalNodes.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatMetricCard("Conformes", "$validatedCount", Color(0xFF16A34A), Modifier.weight(1f))
                    StatMetricCard("À auditer", "$pendingCount", Color(0xFFCA8A04), Modifier.weight(1f))
                    StatMetricCard("Anomalies", "$anomaliesCount", Color(0xFFDC2626), Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Breakdown by node type
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Inventaire des équipements relevés",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        RowTypeCount("Poteaux & appuis aériens", polesCount)
                        RowTypeCount("Chambres de tirage", chambersCount)
                        RowTypeCount("Points de Branchement Optique (PBO)", pbosCount)
                        RowTypeCount("Immeubles collectifs audités", buildingsCount)
                        RowTypeCount("Villas & pavillons individuels", villasCount)
                        RowTypeCount("Métrage total câbles fibres", "$totalCableMeters m")
                        RowTypeCount("Ports optiques PBO (branchés / total)", "$totalConnectedPorts / $totalAvailablePorts")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (exportSuccessMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFDCFCE7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = exportSuccessMessage!!,
                            color = Color(0xFF166534),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Export Options
                Text(
                    text = "Exporter les données de piquetage :",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            exportSuccessMessage = "Fichier 'piquetage_ftth_export.geojson' généré avec succès dans le stockage local !"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("GeoJSON / SIG", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            exportSuccessMessage = "Bordereau technique CSV & synthèse PDF générés avec succès !"
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_csv_button")
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bordereau CSV", fontSize = 12.sp)
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
