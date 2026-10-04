package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.local.FtthNodeType
import com.example.data.local.SurveyStatus
import com.example.ui.theme.ColorBoitierBPE
import com.example.ui.theme.ColorChambre
import com.example.ui.theme.ColorImmeuble
import com.example.ui.theme.ColorPBO
import com.example.ui.theme.ColorPoteau
import com.example.ui.theme.ColorVilla
import com.example.ui.theme.StatusNeedsWork
import com.example.ui.theme.StatusNonConformant
import com.example.ui.theme.StatusPending
import com.example.ui.theme.StatusValidated

object FtthNodeVisuals {
    fun getNodeColor(type: FtthNodeType): Color = when (type) {
        FtthNodeType.POTEAU -> ColorPoteau
        FtthNodeType.CHAMBRE -> ColorChambre
        FtthNodeType.BOITIER_BPE -> ColorBoitierBPE
        FtthNodeType.PBO -> ColorPBO
        FtthNodeType.IMMEUBLE -> ColorImmeuble
        FtthNodeType.VILLA -> ColorVilla
    }

    fun getNodeIcon(type: FtthNodeType): ImageVector = when (type) {
        FtthNodeType.POTEAU -> Icons.Default.FiberManualRecord
        FtthNodeType.CHAMBRE -> Icons.Default.Layers
        FtthNodeType.BOITIER_BPE -> Icons.Default.Widgets
        FtthNodeType.PBO -> Icons.Default.Inbox
        FtthNodeType.IMMEUBLE -> Icons.Default.Apartment
        FtthNodeType.VILLA -> Icons.Default.Home
    }

    fun getStatusColor(status: SurveyStatus): Color = when (status) {
        SurveyStatus.VALIDATED -> StatusValidated
        SurveyStatus.PENDING -> StatusPending
        SurveyStatus.NON_CONFORMANT -> StatusNonConformant
        SurveyStatus.NEEDS_REPLACEMENT -> StatusNeedsWork
    }

    fun getStatusIcon(status: SurveyStatus): ImageVector = when (status) {
        SurveyStatus.VALIDATED -> Icons.Default.CheckCircle
        SurveyStatus.PENDING -> Icons.Default.Warning
        SurveyStatus.NON_CONFORMANT -> Icons.Default.Warning
        SurveyStatus.NEEDS_REPLACEMENT -> Icons.Default.Build
    }
}
