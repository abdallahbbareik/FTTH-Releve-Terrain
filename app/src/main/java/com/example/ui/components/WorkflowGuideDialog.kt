package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class WorkflowStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun WorkflowGuideDialog(onDismiss: () -> Unit) {
    val steps = listOf(
        WorkflowStep(
            stepNumber = 1,
            title = "Reconnaissance & Calage GPS",
            description = "Activez la géolocalisation WGS84, visualisez le secteur SRO/PM et les couches cadastrales.",
            icon = Icons.Default.LocationOn,
            color = Color(0xFF0284C7)
        ),
        WorkflowStep(
            stepNumber = 2,
            title = "Piquetage des Nœuds d'Infrastructure",
            description = "Appui long sur la carte pour piqueter poteaux, chambres de tirage, boîtiers (BPE/PBO/PB), SRO, immeubles ou villas.",
            icon = Icons.Default.TouchApp,
            color = Color(0xFFEA580C)
        ),
        WorkflowStep(
            stepNumber = 3,
            title = "Audit Technique & Relevé Terrain",
            description = "Renseignez les champs : statut, état conforme, adresse automatique, boîtier FTTH, spécifications métier et photos.",
            icon = Icons.Default.CheckCircle,
            color = Color(0xFF16A34A)
        ),
        WorkflowStep(
            stepNumber = 4,
            title = "Traçage des Liaisons Fibres",
            description = "Activez le mode câblage pour relier les nœuds en fibre (Transport 144FO, Distribution 48FO, Branchement).",
            icon = Icons.AutoMirrored.Filled.AltRoute,
            color = Color(0xFF9333EA)
        ),
        WorkflowStep(
            stepNumber = 5,
            title = "Synchro Temps Réel & Export SIG",
            description = "Synchronisation automatique avec le référentiel central SIG et export des bordereaux GeoJSON / CSV.",
            icon = Icons.Default.CloudSync,
            color = Color(0xFF0D9488)
        )
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .testTag("workflow_guide_dialog")
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
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Workflow de Piquetage FTTH",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Protocole d'ingénierie terrain étape par étape",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fermer")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Steps list
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    steps.forEach { step ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = step.color.copy(alpha = 0.08f)
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
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(step.color),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = step.icon,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Étape ${step.stepNumber} : ${step.title}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = step.color
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = step.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("close_workflow_guide_button")
                ) {
                    Text("J'ai compris le workflow")
                }
            }
        }
    }
}
