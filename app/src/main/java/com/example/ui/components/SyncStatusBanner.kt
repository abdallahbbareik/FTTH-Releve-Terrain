package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.RealtimeSyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncStatusBanner(
    syncStatus: RealtimeSyncStatus,
    isLiveActive: Boolean,
    lastSyncTime: Long,
    onTriggerSync: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val (bgColor, textColor, dotColor) = when (syncStatus) {
        RealtimeSyncStatus.ONLINE_SYNCED -> Triple(Color(0xFF0F172A), Color(0xFFE2E8F0), Color(0xFF22C55E))
        RealtimeSyncStatus.SYNCING -> Triple(Color(0xFF0C4A6E), Color(0xFFF0F9FF), Color(0xFF38BDF8))
        RealtimeSyncStatus.OFFLINE -> Triple(Color(0xFF451A03), Color(0xFFFEF3C7), Color(0xFFF59E0B))
        RealtimeSyncStatus.MODIFICATIONS_PENDING -> Triple(Color(0xFF3F3F46), Color(0xFFF4F4F5), Color(0xFFA1A1AA))
    }

    Surface(
        color = bgColor,
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
            // Pulsing live indicator
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = if (syncStatus == RealtimeSyncStatus.SYNCING || isLiveActive) pulseAlpha else 1f))
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenLogs() }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = syncStatus.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• Journal cloud",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.65f),
                        fontSize = 10.sp
                    )
                }

                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date(lastSyncTime))
                Text(
                    text = "Dernier échange serveur : $timeStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.65f),
                    fontSize = 10.sp
                )
            }

            IconButton(
                onClick = onTriggerSync,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("trigger_manual_sync_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Synchroniser maintenant",
                    tint = textColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
