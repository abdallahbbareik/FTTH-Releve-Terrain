package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

@Composable
fun SyncStatusBanner(
    currentProject: String = "projet01",
    nodesCount: Int,
    linksCount: Int,
    tracksCount: Int,
    onOpenLogs: () -> Unit,
    onOpenProjectFolder: () -> Unit = onOpenLogs,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF0F172A),
        modifier = modifier
            .fillMaxWidth()
            .testTag("sync_status_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF16A34A),
                modifier = Modifier.size(10.dp)
            ) {}

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenProjectFolder() }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "📁 $currentProject",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• Documents/Releve-Terrain/$currentProject",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp
                    )
                }

                Text(
                    text = "$nodesCount .noeuds • $tracksCount .infra_lineaire • $linksCount .cables",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFCBD5E1),
                    fontSize = 10.sp
                )
            }

            IconButton(
                onClick = onOpenProjectFolder,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("open_history_logs_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Dossier Releve-Terrain (Nœuds, Infra_lineaire, Câbles)",
                    tint = Color(0xFFE2E8F0),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
