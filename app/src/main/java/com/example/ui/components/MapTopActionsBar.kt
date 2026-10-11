package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.TelecomCyan
import com.example.ui.theme.TelecomNavy

@Composable
fun MapTopActionsBar(
    isPickOnMapActive: Boolean = false,
    isManualTrackActive: Boolean = false,
    isAutoTrackActive: Boolean = false,
    isFilterActive: Boolean = false,
    onAddNodeClick: () -> Unit,
    onCreerGcClick: () -> Unit,
    onGcAutoClick: () -> Unit,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Arrière-plan transparent
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Ajouter noeud (ex-Pointer)
            TopActionButton(
                label = "Ajouter noeud",
                icon = Icons.Default.AddLocationAlt,
                isActive = isPickOnMapActive,
                activeColor = TelecomNavy,
                activeIconColor = TelecomCyan,
                onClick = onAddNodeClick,
                testTag = "map_action_add_node",
                modifier = Modifier.weight(1.15f)
            )

            // 2. Créer GC (ex-Infra-Lineaire : création manuelle)
            TopActionButton(
                label = "Créer GC",
                icon = Icons.Default.Edit,
                isActive = isManualTrackActive,
                activeColor = Color(0xFF0284C7),
                activeIconColor = Color.White,
                onClick = onCreerGcClick,
                testTag = "map_action_creer_gc",
                modifier = Modifier.weight(1.0f)
            )

            // 3. GC Auto (ex-Gps auto : enregistrement GPS continu)
            TopActionButton(
                label = "GC Auto",
                icon = Icons.Default.Route,
                isActive = isAutoTrackActive,
                activeColor = Color(0xFFDC2626),
                activeIconColor = Color.White,
                showPulse = isAutoTrackActive,
                onClick = onGcAutoClick,
                testTag = "map_action_gc_auto",
                modifier = Modifier.weight(1.0f)
            )

            // 4. Filtre (sans mot filtre : icône seule)
            TopIconOnlyButton(
                icon = Icons.Default.FilterList,
                isActive = isFilterActive,
                activeColor = TelecomNavy,
                activeIconColor = TelecomCyan,
                onClick = onFilterClick,
                testTag = "map_action_filter"
            )
        }
    }
}

@Composable
private fun TopActionButton(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    activeColor: Color,
    activeIconColor: Color,
    showPulse: Boolean = false,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    // Arrière-plan transparent avec bordure nette
    val bgColor by animateColorAsState(
        targetValue = if (isActive) activeColor else Color.Transparent,
        label = "btn_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) Color.White else TelecomNavy,
        label = "btn_content"
    )
    val borderColor = if (isActive) activeColor else TelecomNavy

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        shadowElevation = if (isActive) 3.dp else 0.dp,
        modifier = modifier
            .height(38.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (showPulse) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
                Spacer(modifier = Modifier.width(4.dp))
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) activeIconColor else TelecomNavy,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }

            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TopIconOnlyButton(
    icon: ImageVector,
    isActive: Boolean,
    activeColor: Color,
    activeIconColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) activeColor else Color.Transparent,
        label = "btn_bg_icon"
    )
    val borderColor = if (isActive) activeColor else TelecomNavy

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        shadowElevation = if (isActive) 3.dp else 0.dp,
        modifier = modifier
            .size(38.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = "Filtre",
                tint = if (isActive) activeIconColor else TelecomNavy,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
