package com.example.data.util

import android.location.Location
import com.example.data.storage.TrackPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object TrackGeometryHelper {

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
    }

    fun computeTotalDistanceMeters(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            total += calculateDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        }
        return total
    }

    /**
     * Algorithme de simplification de trajectoire Ramer-Douglas-Peucker (RDP)
     * Supprime les petits zigzags et le bruit GPS tout en conservant les vrais virages
     * avec une tolérance en mètres (ex: 3m, 5m, 10m, 20m).
     */
    fun simplifyRamerDouglasPeucker(points: List<TrackPoint>, epsilonMeters: Double): List<TrackPoint> {
        if (points.size <= 2) return points

        var maxDistance = 0.0
        var maxIndex = 0

        val first = points.first()
        val last = points.last()

        for (i in 1 until points.size - 1) {
            val d = perpendicularDistanceMeters(points[i], first, last)
            if (d > maxDistance) {
                maxDistance = d
                maxIndex = i
            }
        }

        return if (maxDistance > epsilonMeters) {
            val left = simplifyRamerDouglasPeucker(points.subList(0, maxIndex + 1), epsilonMeters)
            val right = simplifyRamerDouglasPeucker(points.subList(maxIndex, points.size), epsilonMeters)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    /**
     * Calcule la distance perpendiculaire d'un point P par rapport au segment AB (en mètres)
     */
    private fun perpendicularDistanceMeters(p: TrackPoint, a: TrackPoint, b: TrackPoint): Double {
        val dAB = calculateDistanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        if (dAB < 0.001) {
            return calculateDistanceMeters(p.latitude, p.longitude, a.latitude, a.longitude)
        }

        // Projection plane locale (équirectangulaire) centrée sur a
        val meanLatRad = Math.toRadians((a.latitude + b.latitude) / 2.0)
        val xA = 0.0
        val yA = 0.0
        val xB = Math.toRadians(b.longitude - a.longitude) * 6371000.0 * cos(meanLatRad)
        val yB = Math.toRadians(b.latitude - a.latitude) * 6371000.0

        val xP = Math.toRadians(p.longitude - a.longitude) * 6371000.0 * cos(meanLatRad)
        val yP = Math.toRadians(p.latitude - a.latitude) * 6371000.0

        val numerator = Math.abs((yB - yA) * xP - (xB - xA) * yP + xB * yA - yB * xA)
        val denominator = sqrt((yB - yA) * (yB - yA) + (xB - xA) * (xB - xA))

        return if (denominator > 0.0) numerator / denominator else 0.0
    }

    /**
     * Redresser entre deux sommets :
     * Tout ce qui est entre l'index A et l'index B est supprimé et devient un segment rectiligne.
     */
    fun straightenBetweenVertices(
        points: List<TrackPoint>,
        indexA: Int,
        indexB: Int
    ): List<TrackPoint> {
        if (points.size < 2) return points
        val startIdx = Math.min(indexA, indexB).coerceIn(0, points.size - 1)
        val endIdx = Math.max(indexA, indexB).coerceIn(0, points.size - 1)

        if (startIdx >= endIdx || endIdx - startIdx <= 1) return points

        val result = mutableListOf<TrackPoint>()
        // 1. Tous les points avant le premier sommet inclus
        for (i in 0..startIdx) {
            result.add(points[i])
        }
        // 2. Les points intermédiaires (startIdx+1 until endIdx) sont omis pour faire une ligne droite directe
        // 3. Tous les points à partir du second sommet inclus
        for (i in endIdx until points.size) {
            result.add(points[i])
        }

        return result
    }
}
