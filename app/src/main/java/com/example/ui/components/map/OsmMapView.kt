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
import androidx.compose.material.icons.filled.AddLocationAlt
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
import androidx.compose.runtime.rememberUpdatedState
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
import com.example.data.util.TrackGeometryHelper
import com.example.data.local.FtthNodeEntity
import com.example.data.storage.StoredTrack
import com.example.data.storage.TrackPhoto
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    activeTrack: StoredTrack? = null,
    selectedNode: FtthNodeEntity?,
    isManualTrackMode: Boolean,
    manualTrackPoints: List<TrackPoint>,
    manualTrackPhotos: List<TrackPhoto> = emptyList(),
    pendingStakePosition: Pair<Double, Double>?,
    isStraightenMode: Boolean,
    selectedStraightenIndices: Pair<Int?, Int?>,
    movingNode: FtthNodeEntity? = null,
    tempMoveNodePosition: Pair<Double, Double>? = null,
    isMoveVertexMode: Boolean = false,
    selectedTrackToMoveVertex: StoredTrack? = null,
    movingVertexIndex: Int? = null,
    tempVertexPosition: Pair<Double, Double>? = null,
    isPickOnMapMode: Boolean = false,
    onCancelPickOnMapMode: (() -> Unit)? = null,
    onUpdateStakingPosition: ((latitude: Double, longitude: Double) -> Unit)? = null,
    onLinkClick: ((FtthLinkEntity) -> Unit)? = null,
    onTrackClick: ((StoredTrack) -> Unit)? = null,
    onNodeClick: (FtthNodeEntity) -> Unit,
    onMapLongClick: (latitude: Double, longitude: Double) -> Unit,
    onManualTrackAddPoint: (latitude: Double, longitude: Double) -> Unit,
    onConfirmStakingPosition: (latitude: Double, longitude: Double) -> Unit,
    onCancelStakingPosition: () -> Unit,
    onStraightenVertexClicked: (index: Int) -> Unit,
    onMapClickForMove: ((latitude: Double, longitude: Double) -> Unit)? = null,
    onSelectVertexToMove: ((index: Int) -> Unit)? = null,
    onTrackPhotoClick: ((TrackPhoto) -> Unit)? = null,
    mapFocusTarget: Pair<Double, Double>? = null,
    onMapFocusTargetConsumed: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentLayer by remember { mutableStateOf(OsmLayerType.PLAN) }
    var mapOrientation by remember { mutableFloatStateOf(0f) }

    remember {
        Configuration.getInstance().userAgentValue = context.packageName
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
    }

    val TUNISIA_CENTER_LAT = 34.0
    val TUNISIA_CENTER_LON = 9.5375
    val TUNISIA_DEFAULT_ZOOM = 7.2

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

            if (userLocation != null) {
                controller.setZoom(16.5)
                controller.setCenter(GeoPoint(userLocation.latitude, userLocation.longitude))
            } else if (nodes.isNotEmpty()) {
                controller.setZoom(16.5)
                controller.setCenter(GeoPoint(nodes.first().latitude, nodes.first().longitude))
            } else {
                controller.setZoom(TUNISIA_DEFAULT_ZOOM)
                controller.setCenter(GeoPoint(TUNISIA_CENTER_LAT, TUNISIA_CENTER_LON))
            }

            addMapListener(object : org.osmdroid.events.MapListener {
                override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                    mapOrientation = this@apply.mapOrientation
                    return false
                }
                override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                    return false
                }
            })
        }
    }

    var hasAutoCenteredOnGps by remember { mutableStateOf(userLocation != null) }

    LaunchedEffect(userLocation) {
        if (userLocation != null && !hasAutoCenteredOnGps) {
            hasAutoCenteredOnGps = true
            mapView.controller.setZoom(16.5)
            mapView.controller.animateTo(GeoPoint(userLocation.latitude, userLocation.longitude))
        }
    }

    LaunchedEffect(mapFocusTarget) {
        if (mapFocusTarget != null) {
            mapView.controller.setZoom(18.5)
            mapView.controller.animateTo(GeoPoint(mapFocusTarget.first, mapFocusTarget.second))
            onMapFocusTargetConsumed?.invoke()
        }
    }

    LaunchedEffect(selectedNode) {
        if (selectedNode != null) {
            mapView.controller.setZoom(18.5)
            mapView.controller.animateTo(GeoPoint(selectedNode.latitude, selectedNode.longitude))
        }
    }

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

    LaunchedEffect(currentLayer) {
        when (currentLayer) {
            OsmLayerType.PLAN -> mapView.setTileSource(TileSourceFactory.MAPNIK)
            OsmLayerType.SATELLITE_ESRI -> mapView.setTileSource(ESRI_WORLD_IMAGERY)
        }
        mapView.invalidate()
    }

    val currentIsPickOnMapMode by rememberUpdatedState(isPickOnMapMode)
    val currentPendingStakePosition by rememberUpdatedState(pendingStakePosition)
    val currentMovingNode by rememberUpdatedState(movingNode)
    val currentIsMoveVertexMode by rememberUpdatedState(isMoveVertexMode)
    val currentMovingVertexIndex by rememberUpdatedState(movingVertexIndex)
    val currentIsManualTrackMode by rememberUpdatedState(isManualTrackMode)
    val currentOnMapClickForMove by rememberUpdatedState(onMapClickForMove)
    val currentOnManualTrackAddPoint by rememberUpdatedState(onManualTrackAddPoint)
    val currentOnUpdateStakingPosition by rememberUpdatedState(onUpdateStakingPosition)
    val currentOnMapLongClick by rememberUpdatedState(onMapLongClick)

    LaunchedEffect(
        nodes, links, userLocation, allTracks, activeTrackPoints, selectedNode,
        isManualTrackMode, manualTrackPoints, pendingStakePosition, isStraightenMode, selectedStraightenIndices,
        movingNode, tempMoveNodePosition, isMoveVertexMode, movingVertexIndex, tempVertexPosition,
        isPickOnMapMode
    ) {
        mapView.overlays.clear()

        val rotationGesture = RotationGestureOverlay(mapView)
        rotationGesture.isEnabled = true
        mapView.overlays.add(rotationGesture)

        val compassOverlay = CompassOverlay(context, InternalCompassOrientationProvider(context), mapView)
        compassOverlay.enableCompass()
        mapView.overlays.add(compassOverlay)

        val eventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (p != null) {
                    if (currentMovingNode != null || (currentIsMoveVertexMode && currentMovingVertexIndex != null)) {
                        currentOnMapClickForMove?.invoke(p.latitude, p.longitude)
                        return true
                    }
                    if (currentIsManualTrackMode) {
                        currentOnManualTrackAddPoint(p.latitude, p.longitude)
                        return true
                    }
                    if (currentIsPickOnMapMode || currentPendingStakePosition != null) {
                        if (currentOnUpdateStakingPosition != null) {
                            currentOnUpdateStakingPosition!!.invoke(p.latitude, p.longitude)
                        } else {
                            currentOnMapLongClick(p.latitude, p.longitude)
                        }
                        return true
                    }
                }
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                if (p != null && !currentIsManualTrackMode && currentMovingNode == null && !currentIsMoveVertexMode) {
                    currentOnMapLongClick(p.latitude, p.longitude)
                    return true
                }
                return false
            }
        }
        mapView.overlays.add(MapEventsOverlay(eventsReceiver))

        val nodesMap = nodes.associateBy { it.id }
        val tracksMap = allTracks.associateBy { it.id }

        // Liaisons Câbles
        for (link in links) {
            val from = nodesMap[link.fromNodeId]
            val to = nodesMap[link.toNodeId]
            if (from != null && to != null) {
                val polyline = Polyline(mapView).apply {
                    addPoint(GeoPoint(from.latitude, from.longitude))

                    val assocTrack = if (link.associatedTrackId.isNotBlank()) tracksMap[link.associatedTrackId] else null
                    if (assocTrack != null) {
                        val pts = if (assocTrack.points.isNotEmpty()) assocTrack.points else assocTrack.rawPoints
                        if (pts.isNotEmpty()) {
                            val distStart = TrackGeometryHelper.calculateDistanceMeters(from.latitude, from.longitude, pts.first().latitude, pts.first().longitude)
                            val distEnd = TrackGeometryHelper.calculateDistanceMeters(from.latitude, from.longitude, pts.last().latitude, pts.last().longitude)
                            val orderedPts = if (distStart > distEnd) pts.reversed() else pts
                            for (pt in orderedPts) {
                                addPoint(GeoPoint(pt.latitude, pt.longitude))
                            }
                        }
                    }

                    addPoint(GeoPoint(to.latitude, to.longitude))
                    outlinePaint.strokeWidth = 7f
                    outlinePaint.isAntiAlias = true
                    outlinePaint.color = when (link.installationType.lowercase(Locale.ROOT)) {
                        "aérien" -> android.graphics.Color.parseColor("#EA580C")
                        "façade" -> android.graphics.Color.parseColor("#0284C7")
                        else -> android.graphics.Color.parseColor("#16A34A")
                    }
                    title = "${link.id} (${link.capacityFO} FO - ${link.cableType})"
                    setOnClickListener { _, _, _ ->
                        onLinkClick?.invoke(link)
                        true
                    }
                }
                mapView.overlays.add(polyline)
            }
        }

        // Tracés GPS enregistrés
        for (track in allTracks) {
            val pts = if (track.points.isNotEmpty()) track.points else track.rawPoints
            if (pts.size >= 2) {
                val polyline = Polyline(mapView).apply {
                    for (pt in pts) {
                        addPoint(GeoPoint(pt.latitude, pt.longitude))
                    }
                    outlinePaint.strokeWidth = 8f
                    outlinePaint.isAntiAlias = true
                    outlinePaint.color = when {
                        track.etat == "Non conforme" -> android.graphics.Color.parseColor("#DC2626")
                        track.type == "GC" -> android.graphics.Color.parseColor("#EA580C")
                        track.type == "Aérien" -> android.graphics.Color.parseColor("#0284C7")
                        track.type == "Façade" -> android.graphics.Color.parseColor("#8B5CF6")
                        track.isSimplified -> android.graphics.Color.parseColor("#059669")
                        else -> android.graphics.Color.parseColor("#7C3AED")
                    }
                    title = "${track.name} [${track.type}] (${String.format(Locale.FRANCE, "%.2f km", track.totalDistanceMeters / 1000.0)})"
                    setOnClickListener { _, _, _ ->
                        if (!isStraightenMode && !isMoveVertexMode) {
                            onTrackClick?.invoke(track)
                            true
                        } else false
                    }
                }
                mapView.overlays.add(polyline)

                // Sommets en mode Redressement
                if (isStraightenMode) {
                    for ((idx, pt) in pts.withIndex()) {
                        val vertexMarker = Marker(mapView).apply {
                            position = GeoPoint(pt.latitude, pt.longitude)
                            title = "Sommet #$idx"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            val isSelected = idx == selectedStraightenIndices.first || idx == selectedStraightenIndices.second
                            icon = MapMarkerHelper.createVertexDrawable(context, idx, isSelected)
                            setOnMarkerClickListener { _, _ ->
                                onStraightenVertexClicked(idx)
                                true
                            }
                        }
                        mapView.overlays.add(vertexMarker)
                    }
                }

                // Sommets en mode Déplacement de sommet
                if (isMoveVertexMode && selectedTrackToMoveVertex?.id == track.id) {
                    for ((idx, pt) in pts.withIndex()) {
                        val isBeingMoved = idx == movingVertexIndex
                        val ptPos = if (isBeingMoved && tempVertexPosition != null) {
                            GeoPoint(tempVertexPosition.first, tempVertexPosition.second)
                        } else {
                            GeoPoint(pt.latitude, pt.longitude)
                        }

                        val vertexMarker = Marker(mapView).apply {
                            position = ptPos
                            title = "Sommet #$idx à déplacer"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            icon = MapMarkerHelper.createVertexDrawable(context, idx, isBeingMoved)
                            setOnMarkerClickListener { _, _ ->
                                onSelectVertexToMove?.invoke(idx)
                                true
                            }
                        }
                        mapView.overlays.add(vertexMarker)
                    }
                }

                // Photos le long du trajet (marquées par un point bleu avec icône photo sur la carte)
                for (pho in track.photos) {
                    val photoMarker = Marker(mapView).apply {
                        position = GeoPoint(pho.latitude, pho.longitude)
                        title = "Photo sur trajet : ${track.name}"
                        snippet = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date(pho.timestamp))
                        icon = MapMarkerHelper.createTrackPhotoDrawable(context)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ ->
                            onTrackPhotoClick?.invoke(pho)
                            true
                        }
                    }
                    mapView.overlays.add(photoMarker)
                }
            }
        }

        // Photos de la trace active en cours d'enregistrement
        if (activeTrack != null) {
            for (pho in activeTrack.photos) {
                val photoMarker = Marker(mapView).apply {
                    position = GeoPoint(pho.latitude, pho.longitude)
                    title = "Photo enregistrée sur trace active"
                    snippet = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(Date(pho.timestamp))
                    icon = MapMarkerHelper.createTrackPhotoDrawable(context)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ ->
                        onTrackPhotoClick?.invoke(pho)
                        true
                    }
                }
                mapView.overlays.add(photoMarker)
            }
        }

        // Trace active en cours
        if (activeTrackPoints.size >= 2) {
            val activePolyline = Polyline(mapView).apply {
                for (pt in activeTrackPoints) {
                    addPoint(GeoPoint(pt.latitude, pt.longitude))
                }
                outlinePaint.strokeWidth = 8f
                outlinePaint.isAntiAlias = true
                outlinePaint.color = android.graphics.Color.parseColor("#DC2626")
                title = "Trace GPS en cours"
            }
            mapView.overlays.add(activePolyline)
        }

        // Tracé manuel en cours
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

            // Photos sur tracé manuel
            for (pho in manualTrackPhotos) {
                val photoMarker = Marker(mapView).apply {
                    position = GeoPoint(pho.latitude, pho.longitude)
                    title = "Photo sur tracé manuel"
                    snippet = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(Date(pho.timestamp))
                    icon = MapMarkerHelper.createTrackPhotoDrawable(context)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ ->
                        onTrackPhotoClick?.invoke(pho)
                        true
                    }
                }
                mapView.overlays.add(photoMarker)
            }
        }

        // Marqueurs Nœuds FTTH
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

        // Repositionnement de Nœud sur la Carte
        if (movingNode != null) {
            if (tempMoveNodePosition != null) {
                val tempMarker = Marker(mapView).apply {
                    position = GeoPoint(tempMoveNodePosition.first, tempMoveNodePosition.second)
                    title = "Nouvelle position pour ${movingNode.id}"
                    icon = MapMarkerHelper.createNodeMarkerDrawable(context, movingNode.type, movingNode.etat)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                mapView.overlays.add(tempMarker)

                val linkLine = Polyline(mapView).apply {
                    addPoint(GeoPoint(movingNode.latitude, movingNode.longitude))
                    addPoint(GeoPoint(tempMoveNodePosition.first, tempMoveNodePosition.second))
                    outlinePaint.color = android.graphics.Color.parseColor("#EAB308")
                    outlinePaint.strokeWidth = 5f
                }
                mapView.overlays.add(linkLine)
            }
        }

        // Position présélectionnée
        if (pendingStakePosition != null) {
            val stakeMarker = Marker(mapView).apply {
                position = GeoPoint(pendingStakePosition.first, pendingStakePosition.second)
                title = "Position à valider"
                snippet = "Glissez ou touchez la carte pour ajuster"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                isDraggable = true
                setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                    override fun onMarkerDrag(marker: Marker?) {}
                    override fun onMarkerDragStart(marker: Marker?) {}
                    override fun onMarkerDragEnd(marker: Marker?) {
                        marker?.position?.let { gp ->
                            if (onUpdateStakingPosition != null) {
                                onUpdateStakingPosition.invoke(gp.latitude, gp.longitude)
                            } else {
                                onMapLongClick(gp.latitude, gp.longitude)
                            }
                        }
                    }
                })
            }
            mapView.overlays.add(stakeMarker)
        }

        // Balise GPS Technicien
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

        // Bannière flottante : Mode Sélection de point sur la carte
        if (isPickOnMapMode) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AddLocationAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Touchez la carte à l'emplacement souhaité",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { onCancelPickOnMapMode?.invoke() },
                        modifier = Modifier.height(30.dp),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text("Annuler", fontSize = 11.sp)
                    }
                }
            }
        }

        // Boîte de dialogue : Confirmation de position de piquetage
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
                        text = "Position de piquetage sélectionnée :",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Touchez un autre endroit sur la carte pour corriger la position, ou validez :",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

        // Boutons Flottants (Boussole / Flèche du Nord, Calque, Recentrage, Zoom)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Réinitialiser au Nord",
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(-mapOrientation)
                    )
                }
            }

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
