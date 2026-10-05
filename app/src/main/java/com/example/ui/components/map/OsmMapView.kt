package com.example.ui.components.map

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.gps.GpsLocationData
import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.storage.StoredTrack
import com.example.data.storage.TrackPoint
import com.example.data.util.MapMarkerHelper
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import java.util.Locale

// Tuiles Satellite Esri World Imagery
val ESRI_WORLD_IMAGERY = object : OnlineTileSourceBase(
    "EsriWorldImagery",
    0, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return "$baseUrl${MapTileIndex.getZoom(pMapTileIndex)}/${MapTileIndex.getY(pMapTileIndex)}/${MapTileIndex.getX(pMapTileIndex)}"
    }
}

enum class OsmLayerType(val label: String) {
    PLAN("Plan OpenStreetMap"),
    SATELLITE_ESRI("Satellite Esri")
}

@Composable
fun OsmMapView(
    nodes: List<FtthNodeEntity>,
    links: List<FtthLinkEntity>,
    userLocation: GpsLocationData?,
    allTracks: List<StoredTrack>,
    activeTrackPoints: List<TrackPoint>,
    selectedNode: FtthNodeEntity?,
    isManualTrackMode: Boolean,
    manualTrackPoints: List<TrackPoint>,
    pendingStakePosition: Pair<Double, Double>?,
    isStraightenMode: Boolean,
    selectedStraightenIndices: Pair<Int?, Int?>,
    onNodeClick: (FtthNodeEntity) -> Unit,
    onMapLongClick: (latitude: Double, longitude: Double) -> Unit,
    onManualTrackAddPoint: (latitude: Double, longitude: Double) -> Unit,
    onConfirmStakingPosition: (latitude: Double, longitude: Double) -> Unit,
    onCancelStakingPosition: () -> Unit,
    onStraightenVertexClicked: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentLayer by remember { mutableStateOf(OsmLayerType.PLAN) }
    var mapOrientation by remember { mutableFloatStateOf(0f) }

    // Initialisation configuration OsmDroid
    remember {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(16.5)

            // Centre initial
            val defaultLat = nodes.firstOrNull()?.latitude ?: 48.8580
            val defaultLon = nodes.firstOrNull()?.longitude ?: 2.3522
            controller.setCenter(GeoPoint(defaultLat, defaultLon))
        }
    }

    // Gestion du cycle de vie
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDetach()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    // Changement de fond de carte (Plan OSM vs Satellite Esri)
    LaunchedEffect(currentLayer) {
        when (currentLayer) {
            OsmLayerType.PLAN -> mapView.setTileSource(TileSourceFactory.MAPNIK)
            OsmLayerType.SATELLITE_ESRI -> mapView.setTileSource(ESRI_WORLD_IMAGERY)
        }
        mapView.invalidate()
    }

    // Mise à jour des calques (Overlays)
    LaunchedEffect(
        nodes, links, userLocation, allTracks, activeTrackPoints, selectedNode,
        isManualTrackMode, manualTrackPoints, pendingStakePosition, isStraightenMode, selectedStraightenIndices
    ) {
        mapView.overlays.clear()

        // 1. Détection de rotation tactile (2 doigts)
        val rotationGesture = RotationGestureOverlay(mapView)
        rotationGesture.isEnabled = true
        mapView.overlays.add(rotationGesture)

        // 2. Boussole & Flèche du Nord intégrée
        val compassOverlay = CompassOverlay(context, InternalCompassOrientationProvider(context), mapView)
        compassOverlay.enableCompass()
        mapView.overlays.add(compassOverlay)

        // 3. Gestionnaire des clics sur la carte
        val eventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (p != null) {
                    if (isManualTrackMode) {
                        onManualTrackAddPoint(p.latitude, p.longitude)
                        return true
                    }
                }
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null && !isManualTrackMode) {
                    onMapLongClick(p.latitude, p.longitude)
                    return true
                }
                return false
            }
        }
        mapView.overlays.add(MapEventsOverlay(eventsReceiver))

        val nodesMap = nodes.associateBy { it.id }

        // 4. Liaisons / Câbles Fibre Optique
        for (link in links) {
            val from = nodesMap[link.fromNodeId]
            val to = nodesMap[link.toNodeId]
            if (from != null && to != null) {
                val polyline = Polyline(mapView).apply {
                    addPoint(GeoPoint(from.latitude, from.longitude))
                    addPoint(GeoPoint(to.latitude, to.longitude))
                    outlinePaint.strokeWidth = 6f
                    outlinePaint.isAntiAlias = true
                    outlinePaint.color = when (link.installationType.lowercase(Locale.ROOT)) {
                        "aérien" -> android.graphics.Color.parseColor("#EA580C")   // Orange
                        "façade" -> android.graphics.Color.parseColor("#0284C7")   // Cyan/Bleu
                        else -> android.graphics.Color.parseColor("#16A34A")       // Souterrain : Vert
                    }
                    title = "${link.id} (${link.capacityFO} FO - ${link.cableType})"
                }
                mapView.overlays.add(polyline)
            }
        }

        // 5. Tracés GPS enregistrés
        for (track in allTracks) {
            val pts = if (track.points.isNotEmpty()) track.points else track.rawPoints
            if (pts.size >= 2) {
                val polyline = Polyline(mapView).apply {
                    for (pt in pts) {
                        addPoint(GeoPoint(pt.latitude, pt.longitude))
                    }
                    outlinePaint.strokeWidth = 7f
                    outlinePaint.isAntiAlias = true
                    outlinePaint.color = if (track.isSimplified) android.graphics.Color.parseColor("#059669") else android.graphics.Color.parseColor("#7C3AED")
                    title = "${track.name} (${String.format(Locale.FRANCE, "%.2f km", track.totalDistanceMeters / 1000.0)})"
                }
                mapView.overlays.add(polyline)

                // En mode redressement : afficher les sommets cliquables
                if (isStraightenMode) {
                    for ((idx, pt) in pts.withIndex()) {
                        val vertexMarker = Marker(mapView).apply {
                            position = GeoPoint(pt.latitude, pt.longitude)
                            title = "Sommet #$idx"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            val isSelected = idx == selectedStraightenIndices.first || idx == selectedStraightenIndices.second
                            // Colorier en jaune vif si sélectionné, sinon bleu
                            val dotColor = if (isSelected) "#EAB308" else "#2563EB"
                            icon = MapMarkerHelper.createVertexDrawable(context, idx, isSelected)
                            setOnMarkerClickListener { _, _ ->
                                onStraightenVertexClicked(idx)
                                true
                            }
                        }
                        mapView.overlays.add(vertexMarker)
                    }
                }
            }
        }

        // 6. Trace GPS active en cours d'enregistrement
        if (activeTrackPoints.size >= 2) {
            val activePolyline = Polyline(mapView).apply {
                for (pt in activeTrackPoints) {
                    addPoint(GeoPoint(pt.latitude, pt.longitude))
                }
                outlinePaint.strokeWidth = 8f
                outlinePaint.isAntiAlias = true
                outlinePaint.color = android.graphics.Color.parseColor("#DC2626") // Rouge trace active
                title = "Trace GPS en cours"
            }
            mapView.overlays.add(activePolyline)
        }

        // 7. Tracé manuel en cours de dessin point par point
        if (isManualTrackMode && manualTrackPoints.isNotEmpty()) {
            val manualPolyline = Polyline(mapView).apply {
                for (pt in manualTrackPoints) {
                    addPoint(GeoPoint(pt.latitude, pt.longitude))
                }
                outlinePaint.strokeWidth = 7f
                outlinePaint.isAntiAlias = true
                outlinePaint.color = android.graphics.Color.parseColor("#2563EB")
            }
            mapView.overlays.add(manualPolyline)

            for ((idx, pt) in manualTrackPoints.withIndex()) {
                val ptMarker = Marker(mapView).apply {
                    position = GeoPoint(pt.latitude, pt.longitude)
                    title = "Point ${idx + 1}"
                    icon = MapMarkerHelper.createVertexDrawable(context, idx + 1, false)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                mapView.overlays.add(ptMarker)
            }
        }

        // 8. Marqueurs Nœuds FTTH
        for (node in nodes) {
            val marker = Marker(mapView).apply {
                position = GeoPoint(node.latitude, node.longitude)
                title = "${node.id} • ${node.type.label}"
                snippet = "${node.name}\n${node.address}"
                icon = MapMarkerHelper.createNodeMarkerDrawable(context, node.type, node.etat)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ ->
                    onNodeClick(node)
                    true
                }
            }
            mapView.overlays.add(marker)
        }

        // 9. Marqueur de position présélectionnée (Confirmation de piquetage)
        if (pendingStakePosition != null) {
            val stakeMarker = Marker(mapView).apply {
                position = GeoPoint(pendingStakePosition.first, pendingStakePosition.second)
                title = "Position à valider"
                snippet = "Touchez 'Confirmer et Piqueter' ci-dessous"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            mapView.overlays.add(stakeMarker)
        }

        // 10. Balise GPS Technicien
        if (userLocation != null) {
            val userMarker = Marker(mapView).apply {
                position = GeoPoint(userLocation.latitude, userLocation.longitude)
                title = "Ma position GPS"
                snippet = "Précision: ±${userLocation.accuracy.toInt()}m"
                icon = MapMarkerHelper.createTechnicianLocationDrawable(context)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            mapView.overlays.add(userMarker)
        }

        mapView.invalidate()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Filigrane / Source des données
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shape = RoundedCornerShape(topEnd = 8.dp),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Text(
                text = if (currentLayer == OsmLayerType.PLAN) "© OpenStreetMap contributors" else "© Esri World Imagery",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // Boîte de dialogue flottante : Confirmation de position de piquetage
        if (pendingStakePosition != null) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 70.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
                    .testTag("confirm_position_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Confirmer la position du nœud :",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Lat: ${String.format(Locale.FRANCE, "%.6f", pendingStakePosition.first)} • Lon: ${String.format(Locale.FRANCE, "%.6f", pendingStakePosition.second)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onCancelStakingPosition,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Annuler")
                        }
                        Button(
                            onClick = {
                                onConfirmStakingPosition(pendingStakePosition.first, pendingStakePosition.second)
                            },
                            modifier = Modifier.weight(1.4f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Piqueter ici", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Boutons Flottants de Contrôle (Boussole / Flèche du Nord, Calque, Recentrage, Zoom)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Flèche du Nord : Tourne avec la carte et remet l'orientation au Nord quand on clique
            FloatingActionButton(
                onClick = {
                    mapView.setMapOrientation(0f, true)
                    mapOrientation = 0f
                },
                modifier = Modifier
                    .size(44.dp)
                    .testTag("north_arrow_button"),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFFDC2626)
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Réinitialiser au Nord",
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(mapView.mapOrientation)
                )
            }

            // Basculer fond de carte (Plan OSM vs Satellite Esri)
            FloatingActionButton(
                onClick = {
                    currentLayer = if (currentLayer == OsmLayerType.PLAN) OsmLayerType.SATELLITE_ESRI else OsmLayerType.PLAN
                },
                modifier = Modifier
                    .size(44.dp)
                    .testTag("switch_layer_button"),
                containerColor = if (currentLayer == OsmLayerType.SATELLITE_ESRI) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (currentLayer == OsmLayerType.SATELLITE_ESRI) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Fond de carte Plan / Satellite",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Recentrage GPS Technicien
            FloatingActionButton(
                onClick = {
                    if (userLocation != null) {
                        mapView.controller.animateTo(
                            GeoPoint(userLocation.latitude, userLocation.longitude),
                            18.0,
                            800L
                        )
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .testTag("recenter_gps_button"),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Ma position GPS",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Cadrer tous les nœuds
            FloatingActionButton(
                onClick = {
                    if (nodes.isNotEmpty()) {
                        val minLat = nodes.minOf { it.latitude }
                        val maxLat = nodes.maxOf { it.latitude }
                        val minLon = nodes.minOf { it.longitude }
                        val maxLon = nodes.maxOf { it.longitude }
                        val box = BoundingBox(maxLat + 0.001, maxLon + 0.001, minLat - 0.001, minLon - 0.001)
                        mapView.zoomToBoundingBox(box, true, 80)
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .testTag("fit_bounds_button"),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(
                    imageVector = Icons.Default.CropFree,
                    contentDescription = "Cadrer le réseau",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Zoom Avant
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
                modifier = Modifier.size(44.dp)
            ) {
                IconButton(onClick = { mapView.controller.zoomIn() }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Zoom avant")
                }
            }

            // Zoom Arrière
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
                modifier = Modifier.size(44.dp)
            ) {
                IconButton(onClick = { mapView.controller.zoomOut() }) {
                    Icon(imageVector = Icons.Default.Remove, contentDescription = "Zoom arrière")
                }
            }
        }
    }
}
