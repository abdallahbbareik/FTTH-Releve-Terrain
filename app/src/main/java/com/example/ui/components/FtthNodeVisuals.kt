package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.local.FtthNodeType
import com.example.data.local.NodeConformity
import com.example.data.local.NodeStatus
import com.example.ui.theme.ColorBoitierBPE
import com.example.ui.theme.ColorChambre
import com.example.ui.theme.ColorImmeuble
import com.example.ui.theme.ColorPoteau
import com.example.ui.theme.ColorVilla
import com.example.ui.theme.StatusNeedsWork
import com.example.ui.theme.StatusNonConformant
import com.example.ui.theme.StatusPending
import com.example.ui.theme.StatusValidated
import com.example.ui.theme.TelecomBlue
import com.example.ui.theme.TelecomIndigo

object FtthNodeVisuals {
    fun getNodeColor(type: FtthNodeType): Color = when (type) {
        FtthNodeType.POTEAU -> ColorPoteau
        FtthNodeType.CHAMBRE -> ColorChambre
        FtthNodeType.BOITIER -> ColorBoitierBPE
        FtthNodeType.SRO -> Color(0xFF6366F1) // Indigo Violet for SRO
        FtthNodeType.IMMEUBLE -> ColorImmeuble
        FtthNodeType.VILLA -> ColorVilla
    }

    fun getNodeIcon(type: FtthNodeType): ImageVector = when (type) {
        FtthNodeType.POTEAU -> Icons.Default.FiberManualRecord
        FtthNodeType.CHAMBRE -> Icons.Default.Layers
        FtthNodeType.BOITIER -> Icons.Default.Widgets
        FtthNodeType.SRO -> Icons.Default.Storage
        FtthNodeType.IMMEUBLE -> Icons.Default.Apartment
        FtthNodeType.VILLA -> Icons.Default.Home
    }

    fun getStatusColor(status: NodeStatus): Color = when (status) {
        NodeStatus.EXISTANT -> Color(0xFF0284C7)        // Blue
        NodeStatus.EN_CONSTRUCTION -> Color(0xFF8B5CF6)  // Purple / Violet for En construction
        NodeStatus.A_POSER -> Color(0xFF16A34A)         // Green
        NodeStatus.A_REMPLACER -> Color(0xFFEA580C)     // Orange
        NodeStatus.A_DEPOSER -> Color(0xFFDC2626)       // Red
    }

    fun getConformityColor(etat: NodeConformity): Color = when (etat) {
        NodeConformity.CONFORME -> Color(0xFF16A34A)
        NodeConformity.NON_CONFORME -> Color(0xFFDC2626)
    }

    fun getConformityIcon(etat: NodeConformity): ImageVector = when (etat) {
        NodeConformity.CONFORME -> Icons.Default.CheckCircle
        NodeConformity.NON_CONFORME -> Icons.Default.Warning
    }
}
