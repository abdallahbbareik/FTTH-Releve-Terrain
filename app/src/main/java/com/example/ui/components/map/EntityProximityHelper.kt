package com.example.ui.components.map

import android.graphics.Point
import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.storage.StoredTrack
import com.example.data.storage.TrackPoint
import com.example.data.util.TrackGeometryHelper
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

sealed class MapSelectableEntity {
    data class NodeEntity(val node: FtthNodeEntity) : MapSelectableEntity()
    data class TrackEntity(val track: StoredTrack) : MapSelectableEntity()
    data class LinkEntity(val link: FtthLinkEntity) : MapSelectableEntity()
}

data class NearbyEntityItem(
    val entity: MapSelectableEntity,
    val distanceMeters: Double
)

object EntityProximityHelper {

    /**
     * Recherche toutes les entités (points, tracés, câbles) situées à proximité immédiate
     * des coordonnées indiquées, en combinant distance géographique (mètres) et distance écran (pixels).
     */
    fun findNearbyEntities(
        lat: Double,
        lon: Double,
        nodes: List<FtthNodeEntity>,
        tracks: List<StoredTrack>,
        links: List<FtthLinkEntity>,
        mapView: MapView? = null,
        radiusMeters: Double = 15.0,
        pixelThreshold: Float = 48f
    ): List<NearbyEntityItem> {
        val results = mutableListOf<NearbyEntityItem>()
        val projection = mapView?.projection
        val tapPoint = Point()
        if (projection != null) {
            projection.toPixels(GeoPoint(lat, lon), tapPoint)
        }

        // 1. Nœuds / Points
        for (node in nodes) {
            if (!node.isVisible) continue
            val dist = TrackGeometryHelper.calculateDistanceMeters(lat, lon, node.latitude, node.longitude)
            var matches = dist <= radiusMeters
            if (!matches && projection != null) {
                val nodePoint = Point()
                projection.toPixels(GeoPoint(node.latitude, node.longitude), nodePoint)
                val pixelDist = Math.hypot((nodePoint.x - tapPoint.x).toDouble(), (nodePoint.y - tapPoint.y).toDouble())
                if (pixelDist <= pixelThreshold) {
                    matches = true
                }
            }
            if (matches) {
                results.add(NearbyEntityItem(MapSelectableEntity.NodeEntity(node), dist))
            }
        }

        // 2. Tracés / Polylignes / Polygones
        val tapTrackPt = TrackPoint(latitude = lat, longitude = lon)
        for (track in tracks) {
            if (!track.isVisible) continue
            val pts = if (track.points.isNotEmpty()) track.points else track.rawPoints
            if (pts.size < 2) continue

            var minDist = Double.MAX_VALUE
            for (i in 0 until pts.size - 1) {
                val d = TrackGeometryHelper.distanceToSegmentMeters(tapTrackPt, pts[i], pts[i + 1])
                if (d < minDist) {
                    minDist = d
                }
            }

            var matches = minDist <= radiusMeters
            if (!matches && projection != null) {
                for (pt in pts) {
                    val ptPixels = Point()
                    projection.toPixels(GeoPoint(pt.latitude, pt.longitude), ptPixels)
                    val pixelDist = Math.hypot((ptPixels.x - tapPoint.x).toDouble(), (ptPixels.y - tapPoint.y).toDouble())
                    if (pixelDist <= pixelThreshold) {
                        matches = true
                        break
                    }
                }
            }

            if (matches) {
                results.add(NearbyEntityItem(MapSelectableEntity.TrackEntity(track), minDist))
            }
        }

        // 3. Câbles / Liaisons
        val nodesMap = nodes.associateBy { it.id }
        for (link in links) {
            val from = nodesMap[link.fromNodeId]
            val to = nodesMap[link.toNodeId]
            if (from != null && to != null) {
                val p1 = TrackPoint(latitude = from.latitude, longitude = from.longitude)
                val p2 = TrackPoint(latitude = to.latitude, longitude = to.longitude)
                val d = TrackGeometryHelper.distanceToSegmentMeters(tapTrackPt, p1, p2)
                if (d <= radiusMeters) {
                    results.add(NearbyEntityItem(MapSelectableEntity.LinkEntity(link), d))
                }
            }
        }

        return results.distinctBy {
            when (val e = it.entity) {
                is MapSelectableEntity.NodeEntity -> "node_${e.node.id}"
                is MapSelectableEntity.TrackEntity -> "track_${e.track.id}"
                is MapSelectableEntity.LinkEntity -> "link_${e.link.id}"
            }
        }.sortedBy { it.distanceMeters }
    }
}
