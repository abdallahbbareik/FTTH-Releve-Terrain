package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.data.local.FtthNodeType
import com.example.data.local.NodeConformity
import com.example.data.local.NodeStatus
import com.example.ui.viewmodel.MapFilterState

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FilterLayerSheet(
    filterState: MapFilterState,
    onFilterChanged: (MapFilterState) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    var showPoteaux by remember(filterState) { mutableStateOf(filterState.showPoteaux) }
    var showChambres by remember(filterState) { mutableStateOf(filterState.showChambres) }
    var showBoitiers by remember(filterState) { mutableStateOf(filterState.showBoitiers) }
    var showSRO by remember(filterState) { mutableStateOf(filterState.showSRO) }
    var showImmeubles by remember(filterState) { mutableStateOf(filterState.showImmeubles) }
    var showVillas by remember(filterState) { mutableStateOf(filterState.showVillas) }
    var showCables by remember(filterState) { mutableStateOf(filterState.showCables) }
    var showTracks by remember(filterState) { mutableStateOf(filterState.showTracks) }
    var selectedStatus by remember(filterState) { mutableStateOf(filterState.selectedStatus) }
    var selectedConformity by remember(filterState) { mutableStateOf(filterState.selectedConformity) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        modifier = Modifier.testTag("filter_layer_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Filtres des nœuds FTTH & Calques",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

    // Statut Filter (Tous, À poser, À remplacer, Raccordée, Non raccordée)
    val statusOptions = listOf(
        null to "Tous", 
        NodeStatus.A_POSER to "À poser", 
        NodeStatus.A_REMPLACER to "À remplacer",
        NodeStatus.RACCORDEE to "Raccordée",
        NodeStatus.NON_RACCORDEE to "Non raccordée"
    )
    Text(
        text = "Statut",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
    
    // Improved Status Filter Layout
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        statusOptions.forEach { (st, label) ->
            val isSelected = selectedStatus == st
            androidx.compose.material3.FilterChip(
                selected = isSelected,
                onClick = { selectedStatus = st },
                label = { Text(text = label, fontSize = 12.sp) }
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Conformité Filter (Tous, Conforme, Non conforme)
    val conformityOptions = listOf(null to "Tous", NodeConformity.CONFORME to "Conforme", NodeConformity.NON_CONFORME to "Non conf.")
    Text(
        text = "État",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
    
    // Improved Conformity Filter Layout
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        conformityOptions.forEach { (c, label) ->
            val isSelected = selectedConformity == c
            androidx.compose.material3.FilterChip(
                selected = isSelected,
                onClick = { selectedConformity = c },
                label = { Text(text = label, fontSize = 12.sp) }
            )
        }
    }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Entités visibles sur la carte (incluant infra linéaire)
            Text(
                text = "Entités visibles sur la carte :",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))

            FilterCheckboxItem("Poteaux & Appuis aériens", showPoteaux, FtthNodeVisuals.getNodeColor(FtthNodeType.POTEAU)) { showPoteaux = it }
            FilterCheckboxItem("Chambres de tirage", showChambres, FtthNodeVisuals.getNodeColor(FtthNodeType.CHAMBRE)) { showChambres = it }
            FilterCheckboxItem("Boîtiers FTTH (BPE / PBO / PB)", showBoitiers, FtthNodeVisuals.getNodeColor(FtthNodeType.BOITIER)) { showBoitiers = it }
            FilterCheckboxItem("SRO (Sous-Répartiteur Optique)", showSRO, FtthNodeVisuals.getNodeColor(FtthNodeType.SRO)) { showSRO = it }
            FilterCheckboxItem("Immeubles collectifs", showImmeubles, FtthNodeVisuals.getNodeColor(FtthNodeType.IMMEUBLE)) { showImmeubles = it }
            FilterCheckboxItem("Villas & Pavillons", showVillas, FtthNodeVisuals.getNodeColor(FtthNodeType.VILLA)) { showVillas = it }
            FilterCheckboxItem("Câbles fibres optiques", showCables, MaterialTheme.colorScheme.primary) { showCables = it }
            FilterCheckboxItem("Infra linéaire (Tracés)", showTracks, androidx.compose.ui.graphics.Color(0xFF0284C7)) { showTracks = it }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        showPoteaux = true
                        showChambres = true
                        showBoitiers = true
                        showSRO = true
                        showImmeubles = true
                        showVillas = true
                        showCables = true
                        showTracks = true
                        selectedStatus = null
                        selectedConformity = null
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Tout afficher")
                }

                Button(
                    onClick = {
                        onFilterChanged(
                            MapFilterState(
                                showPoteaux = showPoteaux,
                                showChambres = showChambres,
                                showBoitiers = showBoitiers,
                                showSRO = showSRO,
                                showImmeubles = showImmeubles,
                                showVillas = showVillas,
                                showCables = showCables,
                                showTracks = showTracks,
                                selectedStatus = selectedStatus,
                                selectedConformity = selectedConformity
                            )
                        )
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("apply_filters_button")
                ) {
                    Text("Appliquer")
                }
            }
        }
    }
}

@Composable
private fun FilterCheckboxItem(
    label: String,
    checked: Boolean,
    color: androidx.compose.ui.graphics.Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp), // Increased padding to provide more space
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = color,
            modifier = Modifier.size(10.dp) // Slightly smaller color indicator
        ) {}
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall, // Smaller text
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(24.dp) // Adjust switch size if needed via scale
        )
    }
}
