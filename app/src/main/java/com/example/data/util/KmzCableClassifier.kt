package com.example.data.util

import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.storage.StoredTrack
import com.example.data.storage.TrackPoint
import org.json.JSONObject
import java.util.Locale

/**
 * Entité représentant un câble classifié et regroupé par Nom et Géométrie (points communs).
 */
data class ClassifiedCable(
    val id: String,
    val name: String,
    val baseName: String,
    val segmentCount: Int,
    val totalLengthMeters: Double,
    val capacityFO: String,
    val cableType: String,        // Transport, Distribution, Raccordement
    val installationType: String, // Souterrain, Aérien, Façade
    val sourceFile: String,
    val startCoordinatesLabel: String,
    val endCoordinatesLabel: String,
    val subTracks: List<StoredTrack>
)

/**
 * Statistiques globales pour la classification des câbles.
 */
data class CableClassificationStats(
    val totalCables: Int,
    val totalSegments: Int,
    val totalLengthMeters: Double,
    val fo144Count: Int,
    val fo72Count: Int,
    val fo48Count: Int,
    val fo24Count: Int,
    val foOtherCount: Int
)

object KmzCableClassifier {

    /**
     * Classifier et réagglomérer une liste de tracés/lignes en câbles par Nom et Géométrie (points communs),
     * puis réorienter le sens logique des câbles en partant du SRO vers les boîtiers enfants.
     */
    fun classifyAndGroupCables(
        tracks: List<StoredTrack>,
        allNodes: List<FtthNodeEntity> = emptyList()
    ): List<ClassifiedCable> {
        if (tracks.isEmpty()) return emptyList()

        // 1. Filtrer les tracés qui sont des câbles ou lignes
        val candidateTracks = tracks.filter { trk ->
            trk.rawPoints.size >= 2 || trk.points.size >= 2
        }

        if (candidateTracks.isEmpty()) return emptyList()

        // 2. Grouper d'abord par nom de base nettoyé (ex: "CBL_TRANS_01_TR1" -> "CBL_TRANS_01")
        val groupsByBaseName = candidateTracks.groupBy { trk ->
            val smartName = resolveTrackSmartName(trk)
            extractBaseCableName(smartName)
        }

        val resultCables = mutableListOf<ClassifiedCable>()

        for ((baseName, trackGroup) in groupsByBaseName) {
            // Pour chaque groupe de nom identique ou similaire, regrouper les tronçons connectés géométriquement
            val connectedClusters = clusterTracksByGeometry(trackGroup)

            for ((clusterIdx, cluster) in connectedClusters.withIndex()) {
                val combinedPoints = mutableListOf<TrackPoint>()
                var totalDist = 0.0

                for (trk in cluster) {
                    val pts = if (trk.points.isNotEmpty()) trk.points else trk.rawPoints
                    if (pts.isNotEmpty()) {
                        if (combinedPoints.isEmpty()) {
                            combinedPoints.addAll(pts)
                        } else {
                            // Vérifier si le début de trk se connecte à la fin de combinedPoints
                            val lastPt = combinedPoints.last()
                            val firstPt = pts.first()
                            val endPt = pts.last()

                            val distStart = TrackGeometryHelper.calculateDistanceMeters(lastPt.latitude, lastPt.longitude, firstPt.latitude, firstPt.longitude)
                            val distEnd = TrackGeometryHelper.calculateDistanceMeters(lastPt.latitude, lastPt.longitude, endPt.latitude, endPt.longitude)

                            if (distStart <= distEnd) {
                                combinedPoints.addAll(pts)
                            } else {
                                combinedPoints.addAll(pts.reversed())
                            }
                        }
                    }
                    totalDist += if (trk.totalDistanceMeters > 0) trk.totalDistanceMeters else TrackGeometryHelper.computeTotalDistanceMeters(pts)
                }

                val startPt = combinedPoints.firstOrNull()
                val endPt = combinedPoints.lastOrNull()

                val startLabel = if (startPt != null) String.format(Locale.ROOT, "%.4f, %.4f", startPt.latitude, startPt.longitude) else "-"
                val endLabel = if (endPt != null) String.format(Locale.ROOT, "%.4f, %.4f", endPt.latitude, endPt.longitude) else "-"

                // Déterminer la capacité FO et les types à partir du premier tracé du groupe
                val firstTrk = cluster.first()
                val (capaFO, cableType, installType) = extractCableProperties(firstTrk)

                val cableDisplayName = if (connectedClusters.size > 1) {
                    "$baseName (Branche ${clusterIdx + 1})"
                } else {
                    baseName
                }

                val cableId = "CBL-AGGR-${firstTrk.id}"

                resultCables.add(
                    ClassifiedCable(
                        id = cableId,
                        name = cableDisplayName,
                        baseName = baseName,
                        segmentCount = cluster.size,
                        totalLengthMeters = totalDist,
                        capacityFO = capaFO,
                        cableType = cableType,
                        installationType = installType,
                        sourceFile = firstTrk.sourceFile.ifBlank { "Natif" },
                        startCoordinatesLabel = startLabel,
                        endCoordinatesLabel = endLabel,
                        subTracks = cluster
                    )
                )
            }
        }

        // Réorientation hiérarchique : SRO (Parents) ➔ Boîtiers Dérivés (Enfants)
        if (allNodes.isNotEmpty()) {
            return orientCablesFromSroTree(resultCables, allNodes)
        } else {
            return resultCables
        }
    }

    /**
     * Réordonner les câbles et leurs tronçons pour respecter la hiérarchie du réseau :
     * 1. Câbles principaux (parents) : Commencent à partir d'un SRO.
     * 2. Câbles enfants / dérivés : Commencent à partir d'un boîtier sur un câble parent.
     * Si un câble est tracé à l'envers dans le fichier KMZ, ses tronçons et points sont inversés.
     */
    fun orientCablesFromSroTree(
        cables: List<ClassifiedCable>,
        allNodes: List<FtthNodeEntity>
    ): List<ClassifiedCable> {
        if (cables.isEmpty()) return emptyList()

        // 1. Détecter tous les emplacements de SRO
        val sroPoints = mutableListOf<TrackPoint>()
        for (node in allNodes) {
            val classified = KmzNodeClassifier.classifyNode(node)
            if (classified.category == KmzNodeCategory.SRO ||
                node.type == FtthNodeType.SRO ||
                node.name.startsWith("SRO", ignoreCase = true) ||
                node.name.contains("SRO", ignoreCase = true)
            ) {
                sroPoints.add(TrackPoint(latitude = node.latitude, longitude = node.longitude))
            }
        }

        // Si aucun SRO explicite n'est dans les nœuds, utiliser le départ du premier câble de Transport
        if (sroPoints.isEmpty()) {
            for (c in cables) {
                if (c.cableType.equals("Transport", ignoreCase = true)) {
                    val pts = getCableAllPoints(c)
                    if (pts.isNotEmpty()) {
                        sroPoints.add(pts.first())
                    }
                }
            }
        }

        val knownOrigins = mutableListOf<TrackPoint>()
        knownOrigins.addAll(sroPoints)

        val unoriented = cables.toMutableList()
        val oriented = mutableListOf<ClassifiedCable>()

        // PASSE 1 : Orientations directes depuis les SRO (Câbles parents / Niveau 0)
        val iterPass1 = unoriented.iterator()
        while (iterPass1.hasNext()) {
            val cable = iterPass1.next()
            val pts = getCableAllPoints(cable)
            if (pts.size < 2) continue

            val startPt = pts.first()
            val endPt = pts.last()

            val dStart = minDistanceToPoints(startPt, sroPoints)
            val dEnd = minDistanceToPoints(endPt, sroPoints)

            // Vérifier si un SRO est connecté à une extrémité (<= 25m) ou intersecte le câble (<= 15m)
            val minInternalSroDist = pts.minOfOrNull { pt -> minDistanceToPoints(pt, sroPoints) } ?: Double.MAX_VALUE
            val connectsToSro = minOf(dStart, dEnd) <= 25.0 || minInternalSroDist <= 15.0

            if (connectsToSro) {
                iterPass1.remove()
                // Si la fin est plus proche du SRO que le début, inverser le sens logique
                val orientedCable = if (dEnd < dStart) {
                    reverseClassifiedCable(cable)
                } else {
                    cable
                }
                oriented.add(orientedCable)

                val orientedPts = getCableAllPoints(orientedCable)
                if (orientedPts.isNotEmpty()) {
                    knownOrigins.addAll(orientedPts)
                }
            }
        }

        // PASSE 2 : Propagation en arbre vers les câbles enfants (Dérivations sur boîtiers)
        var progressMade = true
        var safetyLoopCount = 0
        while (progressMade && unoriented.isNotEmpty() && safetyLoopCount < 50) {
            progressMade = false
            safetyLoopCount++

            val iterPass2 = unoriented.iterator()
            while (iterPass2.hasNext()) {
                val cable = iterPass2.next()
                val pts = getCableAllPoints(cable)
                if (pts.size < 2) continue

                val startPt = pts.first()
                val endPt = pts.last()

                val dStart = minDistanceToPoints(startPt, knownOrigins)
                val dEnd = minDistanceToPoints(endPt, knownOrigins)

                // Seuil de proximité avec un boîtier/parent = 15 mètres
                if (minOf(dStart, dEnd) <= 15.0) {
                    iterPass2.remove()
                    val orientedCable = if (dEnd < dStart) {
                        reverseClassifiedCable(cable)
                    } else {
                        cable
                    }
                    oriented.add(orientedCable)

                    val orientedPts = getCableAllPoints(orientedCable)
                    if (orientedPts.isNotEmpty()) {
                        knownOrigins.addAll(orientedPts)
                    }
                    progressMade = true
                }
            }
        }

        // PASSE 3 : Câbles restants non connectés (conserver ou orienter vers l'origine la plus proche)
        for (cable in unoriented) {
            val pts = getCableAllPoints(cable)
            if (pts.size >= 2) {
                val startPt = pts.first()
                val endPt = pts.last()
                val dStart = minDistanceToPoints(startPt, knownOrigins)
                val dEnd = minDistanceToPoints(endPt, knownOrigins)

                if (dEnd < dStart && dEnd <= 50.0) {
                    oriented.add(reverseClassifiedCable(cable))
                } else {
                    oriented.add(cable)
                }
            } else {
                oriented.add(cable)
            }
        }

        return oriented
    }

    private fun reverseClassifiedCable(cable: ClassifiedCable): ClassifiedCable {
        val reversedSubTracks = cable.subTracks.map { trk ->
            val pts = if (trk.points.isNotEmpty()) trk.points else trk.rawPoints
            val revPts = pts.reversed()
            trk.copy(
                points = revPts,
                rawPoints = revPts
            )
        }.reversed()

        return cable.copy(
            startCoordinatesLabel = cable.endCoordinatesLabel,
            endCoordinatesLabel = cable.startCoordinatesLabel,
            subTracks = reversedSubTracks
        )
    }

    fun getCableAllPoints(cable: ClassifiedCable): List<TrackPoint> {
        return cable.subTracks.flatMap { if (it.points.isNotEmpty()) it.points else it.rawPoints }
    }

    private fun minDistanceToPoints(target: TrackPoint, points: List<TrackPoint>): Double {
        if (points.isEmpty()) return Double.MAX_VALUE
        var minD = Double.MAX_VALUE
        for (p in points) {
            val d = TrackGeometryHelper.calculateDistanceMeters(target.latitude, target.longitude, p.latitude, p.longitude)
            if (d < minD) {
                minD = d
            }
        }
        return minD
    }

    /**
     * Regrouper géométriquement les tronçons qui partagent des points d'extrémité communs (distance < 5.0m).
     */
    private fun clusterTracksByGeometry(tracks: List<StoredTrack>): List<List<StoredTrack>> {
        val unvisited = tracks.toMutableList()
        val clusters = mutableListOf<MutableList<StoredTrack>>()

        while (unvisited.isNotEmpty()) {
            val currentCluster = mutableListOf<StoredTrack>()
            val queue = ArrayDeque<StoredTrack>()

            val seed = unvisited.removeAt(0)
            queue.add(seed)
            currentCluster.add(seed)

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val curPts = if (current.points.isNotEmpty()) current.points else current.rawPoints
                if (curPts.isEmpty()) continue

                val curStart = curPts.first()
                val curEnd = curPts.last()

                val iter = unvisited.iterator()
                while (iter.hasNext()) {
                    val candidate = iter.next()
                    val candPts = if (candidate.points.isNotEmpty()) candidate.points else candidate.rawPoints
                    if (candPts.isEmpty()) continue

                    val candStart = candPts.first()
                    val candEnd = candPts.last()

                    // Vérifier si un des sommets du candidat se connecte à un des sommets du tracé actuel
                    val d1 = TrackGeometryHelper.calculateDistanceMeters(curStart.latitude, curStart.longitude, candStart.latitude, candStart.longitude)
                    val d2 = TrackGeometryHelper.calculateDistanceMeters(curStart.latitude, curStart.longitude, candEnd.latitude, candEnd.longitude)
                    val d3 = TrackGeometryHelper.calculateDistanceMeters(curEnd.latitude, curEnd.longitude, candStart.latitude, candStart.longitude)
                    val d4 = TrackGeometryHelper.calculateDistanceMeters(curEnd.latitude, curEnd.longitude, candEnd.latitude, candEnd.longitude)

                    val minDistance = minOf(d1, minOf(d2, minOf(d3, d4)))

                    // Seuil d'accrochage/point commun de 5 mètres
                    if (minDistance <= 5.0) {
                        iter.remove()
                        currentCluster.add(candidate)
                        queue.add(candidate)
                    }
                }
            }

            clusters.add(currentCluster)
        }

        return clusters
    }

    /**
     * Extraire le nom de base d'un câble en retirant les suffixes de tronçons.
     */
    fun extractBaseCableName(rawName: String): String {
        var clean = rawName.trim()
        if (clean.isBlank()) return "Câble Sans Nom"

        // Retirer les motifs comme "_TRONCON_1", "_TR1", "-TR1", "_SEG_2", " (1)", " #1", "_PART_1"
        clean = clean.replace(Regex("""[_\-\s]+(?:TRONCON|TR|SEGMENT|SEG|PARTIE|PART|SECTION)[_\-\s]*\d+""", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("""\s*\(\d+\)$"""), "")
        clean = clean.replace(Regex("""\s*#\d+$"""), "")

        return clean.trim().ifBlank { rawName }
    }

    /**
     * Extraire la capacité FO, le type et le mode de pose à partir du tracé et de ses attributs.
     */
    private fun extractCableProperties(track: StoredTrack): Triple<String, String, String> {
        val combined = "${track.name} ${track.type} ${track.notes} ${track.kmlProperties}".lowercase(Locale.ROOT)

        // Capacité FO
        val matchFo = Regex("""(\d+)\s*(?:fo|f\.o|fibres?|fibers?|brins?)\b""", RegexOption.IGNORE_CASE).find(combined)
        val foCapacity = if (matchFo != null) {
            "${matchFo.groupValues[1]} FO"
        } else {
            val matchStandard = Regex("""\b(864|720|576|432|288|144|96|72|48|36|24|16|12|8|6|4|2)\b""").find(combined)
            if (matchStandard != null) "${matchStandard.groupValues[1]} FO" else "Non spécifiée"
        }

        // Type de câble
        val cableType = when {
            combined.contains("transport") || combined.contains("trsp") || combined.contains("144") -> "Transport"
            combined.contains("raccord") || combined.contains("branchement") || combined.contains("drop") -> "Raccordement"
            else -> "Distribution"
        }

        // Mode d'installation
        val installType = when {
            combined.contains("aerien") || combined.contains("aérien") || combined.contains("poteau") || combined.contains("appui") -> "Aérien"
            combined.contains("facade") || combined.contains("façade") -> "Façade"
            else -> "Souterrain"
        }

        return Triple(foCapacity, cableType, installType)
    }

    /**
     * Calculer les statistiques globales pour la classification des câbles.
     */
    fun computeStats(cables: List<ClassifiedCable>): CableClassificationStats {
        val totalCables = cables.size
        val totalSegments = cables.sumOf { it.segmentCount }
        val totalDist = cables.sumOf { it.totalLengthMeters }

        var fo144 = 0
        var fo72 = 0
        var fo48 = 0
        var fo24 = 0
        var foOther = 0

        for (c in cables) {
            when {
                c.capacityFO.contains("144") -> fo144++
                c.capacityFO.contains("72") -> fo72++
                c.capacityFO.contains("48") -> fo48++
                c.capacityFO.contains("24") -> fo24++
                else -> foOther++
            }
        }

        return CableClassificationStats(
            totalCables = totalCables,
            totalSegments = totalSegments,
            totalLengthMeters = totalDist,
            fo144Count = fo144,
            fo72Count = fo72,
            fo48Count = fo48,
            fo24Count = fo24,
            foOtherCount = foOther
        )
    }

    /**
     * Extraire la carte de propriétés d'un tracé et résoudre son vrai nom SIG (ex: CDI_01-288FO au lieu de 288FO).
     */
    fun resolveTrackSmartName(track: StoredTrack): String {
        val map = mutableMapOf<String, String>()
        if (track.kmlProperties.isNotBlank()) {
            try {
                val json = JSONObject(track.kmlProperties)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = json.optString(k, "")
                    if (k.isNotBlank() && v.isNotBlank()) map[k] = v
                }
            } catch (_: Exception) {}
        }
        if (track.notes.isNotBlank()) {
            val lines = track.notes.split("\n")
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("•") && trimmed.contains(":")) {
                    val withoutBullet = trimmed.removePrefix("•").trim()
                    val colonIdx = withoutBullet.indexOf(":")
                    if (colonIdx > 0) {
                        val k = withoutBullet.substring(0, colonIdx).trim()
                        val v = withoutBullet.substring(colonIdx + 1).trim()
                        if (k.isNotBlank() && v.isNotBlank() && !map.containsKey(k)) {
                            map[k] = v
                        }
                    }
                }
            }
        }
        return com.example.data.import.RealImportService.resolveSmartName(track.name, map)
    }
}
