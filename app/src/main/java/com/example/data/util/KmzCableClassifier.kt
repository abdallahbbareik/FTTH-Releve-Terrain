package com.example.data.util

import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.storage.StoredTrack
import com.example.data.storage.TrackPoint
import org.json.JSONObject
import java.util.Locale

/**
 * Entité représentant un câble classifié et regroupé par Nom et Géométrie (points communs),
 * avec enrichissement de la hiérarchie FTTH (Parent/Principal vs Enfant/Dérivation).
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
    val subTracks: List<StoredTrack>,
    val isParent: Boolean = true,
    val isChild: Boolean = false,
    val hierarchyLevel: Int = 0,         // 0 = Parent / SRO, 1 = Enfant N1, 2 = Enfant N2...
    val hierarchyRole: String = "Parent (Principal)",
    val parentCableName: String = "-",   // Nom du câble parent dont il dérive
    val originNodeName: String = "-",    // Boîtier ou SRO d'origine
    val destinationNodeName: String = "-" // Boîtier de fin
)

/**
 * Statistiques globales pour la classification des câbles.
 */
data class CableClassificationStats(
    val totalCables: Int,
    val totalSegments: Int,
    val totalLengthMeters: Double,
    val parentCablesCount: Int = 0,
    val childCablesCount: Int = 0,
    val fo144Count: Int,
    val fo72Count: Int,
    val fo48Count: Int,
    val fo24Count: Int,
    val foOtherCount: Int
) {
    fun getPercentage(count: Int): Float = if (totalCables > 0) (count * 100f / totalCables) else 0f
}

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
                // Ordonner et enchaîner tête-à-queue les tronçons du cluster pour former un tracé continu sans saut transversal
                val orderedSubTracks = orderAndChainSubTracks(cluster)
                val combinedPoints = mutableListOf<TrackPoint>()
                var totalDist = 0.0

                for (trk in orderedSubTracks) {
                    val pts = if (trk.points.isNotEmpty()) trk.points else trk.rawPoints
                    if (pts.isNotEmpty()) {
                        if (combinedPoints.isEmpty()) {
                            combinedPoints.addAll(pts)
                        } else {
                            val lastPt = combinedPoints.last()
                            val firstPt = pts.first()
                            val d = TrackGeometryHelper.calculateDistanceMeters(lastPt.latitude, lastPt.longitude, firstPt.latitude, firstPt.longitude)
                            if (d <= 0.05) {
                                combinedPoints.addAll(pts.subList(1, pts.size))
                            } else {
                                combinedPoints.addAll(pts)
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
                val firstTrk = orderedSubTracks.first()
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
                        segmentCount = orderedSubTracks.size,
                        totalLengthMeters = totalDist,
                        capacityFO = capaFO,
                        cableType = cableType,
                        installationType = installType,
                        sourceFile = firstTrk.sourceFile.ifBlank { "Natif" },
                        startCoordinatesLabel = startLabel,
                        endCoordinatesLabel = endLabel,
                        subTracks = orderedSubTracks
                    )
                )
            }
        }

        // Réorientation et classification hiérarchique : SRO (Parents) ➔ Boîtiers Dérivés (Enfants)
        if (allNodes.isNotEmpty()) {
            return orientCablesFromSroTree(resultCables, allNodes)
        } else {
            return resultCables
        }
    }

    /**
     * Réordonner et classifier les câbles pour respecter et enrichir la hiérarchie du réseau :
     * 1. Câbles principaux (parents) : Commencent à partir d'un SRO (Niveau 0).
     * 2. Câbles enfants / dérivés : Commencent à partir d'un boîtier sur un câble parent (Niveau 1+).
     *    Ces enfants peuvent eux-mêmes devenir les parents d'autres câbles enfants (Niveau 2+).
     * Si un câble est tracé à l'envers dans le fichier KMZ, ses tronçons et points sont inversés.
     */
    fun orientCablesFromSroTree(
        cables: List<ClassifiedCable>,
        allNodes: List<FtthNodeEntity>
    ): List<ClassifiedCable> {
        if (cables.isEmpty()) return emptyList()

        // 1. Détecter tous les emplacements de SRO et Boîtiers
        val sroNodes = mutableListOf<FtthNodeEntity>()
        val boitierNodes = mutableListOf<FtthNodeEntity>()

        for (node in allNodes) {
            val classified = KmzNodeClassifier.classifyNode(node)
            if (classified.category == KmzNodeCategory.SRO ||
                node.type == FtthNodeType.SRO ||
                node.name.startsWith("SRO", ignoreCase = true) ||
                node.name.contains("SRO", ignoreCase = true) ||
                node.name.contains("NRO", ignoreCase = true) ||
                node.name.contains("PM", ignoreCase = true) ||
                node.name.contains("POP", ignoreCase = true)
            ) {
                sroNodes.add(node)
            } else if (classified.category == KmzNodeCategory.BOITIER ||
                node.type == FtthNodeType.BOITIER ||
                node.name.contains("boitier", ignoreCase = true) ||
                node.name.contains("bpe", ignoreCase = true) ||
                node.name.contains("pbo", ignoreCase = true) ||
                node.name.contains("split", ignoreCase = true) ||
                node.name.contains("joint", ignoreCase = true) ||
                node.name.contains("pec", ignoreCase = true) ||
                node.name.contains("bti", ignoreCase = true)
            ) {
                boitierNodes.add(node)
            }
        }

        fun findClosestNodeName(pt: TrackPoint, candidates: List<FtthNodeEntity>, maxDist: Double = 30.0): String? {
            var best: String? = null
            var minD = Double.MAX_VALUE
            for (n in candidates) {
                val d = TrackGeometryHelper.calculateDistanceMeters(pt.latitude, pt.longitude, n.latitude, n.longitude)
                if (d < minD && d <= maxDist) {
                    minD = d
                    best = n.name
                }
            }
            return best
        }

        val sroPoints = sroNodes.map { TrackPoint(it.latitude, it.longitude) }.toMutableList()

        // Si aucun SRO explicite n'est dans les nœuds, utiliser le départ du premier câble de Transport ou le plus gros câble
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
        if (sroPoints.isEmpty()) {
            val bestCandidate = cables.maxByOrNull { c ->
                val foNum = c.capacityFO.filter { it.isDigit() }.toIntOrNull() ?: 0
                foNum * 100000.0 + c.totalLengthMeters
            }
            if (bestCandidate != null) {
                val pts = getCableAllPoints(bestCandidate)
                if (pts.isNotEmpty()) sroPoints.add(pts.first())
            }
        }

        val unoriented = cables.toMutableList()
        val oriented = mutableListOf<ClassifiedCable>()

        // PASSE 1 : Câbles Principaux / Parents (Partant du SRO / Racine réseau, Niveau 0)
        val candidatesForSro = unoriented.filter { cable ->
            val pts = getCableAllPoints(cable)
            if (pts.size < 2) false
            else {
                val dStart = minDistanceToPoints(pts.first(), sroPoints)
                val dEnd = minDistanceToPoints(pts.last(), sroPoints)
                minOf(dStart, dEnd) <= 15.0
            }
        }

        val sroParentsToKeep = if (candidatesForSro.isNotEmpty()) {
            val nonChildCandidates = candidatesForSro.filter { cable ->
                val name = cable.name.lowercase(Locale.ROOT)
                !name.contains("_06-") && !name.contains("_07-") && !name.contains("_01_0") && !name.contains("-01-") && !name.contains("-02-")
            }
            if (nonChildCandidates.isNotEmpty()) nonChildCandidates else listOf(candidatesForSro.maxByOrNull { c ->
                val foNum = c.capacityFO.filter { it.isDigit() }.toIntOrNull() ?: 0
                foNum * 10000.0 + c.totalLengthMeters
            }!!)
        } else emptyList()

        for (cable in sroParentsToKeep) {
            unoriented.remove(cable)
            val pts = getCableAllPoints(cable)
            val dStart = minDistanceToPoints(pts.first(), sroPoints)
            val dEnd = minDistanceToPoints(pts.last(), sroPoints)
            val needReverse = dEnd < dStart
            val orientedCable = if (needReverse) reverseClassifiedCable(cable) else cable
            val orientedPts = getCableAllPoints(orientedCable)

            val originSro = findClosestNodeName(orientedPts.first(), sroNodes, 40.0) ?: "SRO"
            val destBoitier = findClosestNodeName(orientedPts.last(), boitierNodes, 30.0) ?: "-"

            val classifiedParent = orientedCable.copy(
                isParent = true,
                isChild = false,
                hierarchyLevel = 0,
                hierarchyRole = "Parent (Principal)",
                parentCableName = "SRO",
                originNodeName = originSro,
                destinationNodeName = destBoitier
            )
            oriented.add(classifiedParent)
        }

        // Si aucun câble n'a touché le SRO (ex: réseau sans SRO géoréférencé), initialiser avec le câble principal
        if (oriented.isEmpty() && unoriented.isNotEmpty()) {
            val best = unoriented.minByOrNull { c ->
                val pts = getCableAllPoints(c)
                if (pts.isEmpty()) Double.MAX_VALUE
                else minDistanceToPoints(pts.first(), sroPoints)
            } ?: unoriented.first()
            unoriented.remove(best)
            val pts = getCableAllPoints(best)
            val originSro = if (pts.isNotEmpty()) findClosestNodeName(pts.first(), sroNodes, 40.0) ?: "Origine Réseau" else "Origine Réseau"
            val destBoitier = if (pts.isNotEmpty()) findClosestNodeName(pts.last(), boitierNodes, 30.0) ?: "-" else "-"
            oriented.add(
                best.copy(
                    isParent = true,
                    isChild = false,
                    hierarchyLevel = 0,
                    hierarchyRole = "Parent (Principal)",
                    parentCableName = "SRO",
                    originNodeName = originSro,
                    destinationNodeName = destBoitier
                )
            )
        }

        // PASSE 2 : Propagation en arbre vers les câbles enfants (Dérivations géométriques & Boîtiers)
        fun propagateChildren() {
            var progressMade = true
            var safetyLoop = 0
            while (progressMade && unoriented.isNotEmpty() && safetyLoop < 100) {
                progressMade = false
                safetyLoop++

                val iter = unoriented.iterator()
                while (iter.hasNext()) {
                    val candidate = iter.next()
                    val pts = getCableAllPoints(candidate)
                    if (pts.size < 2) continue

                    var bestParent: ClassifiedCable? = null
                    var bestDist = Double.MAX_VALUE
                    var bestNeedReverse = false

                    for (parent in oriented) {
                        val (dist, needRev) = checkCableConnection(candidate, parent, boitierNodes)
                        if (dist < bestDist) {
                            bestDist = dist
                            bestParent = parent
                            bestNeedReverse = needRev
                        }
                    }

                    // Seuil de dérivation : distance directe ou via boîtier partagé <= 35m
                    if (bestDist <= 35.0 && bestParent != null) {
                        iter.remove()
                        val orientedChild = if (bestNeedReverse) reverseClassifiedCable(candidate) else candidate
                        val orientedPts = getCableAllPoints(orientedChild)

                        val parentLevel = bestParent.hierarchyLevel
                        val currentLevel = parentLevel + 1

                        val originNode = findClosestNodeName(orientedPts.first(), allNodes, 30.0)
                            ?: "Boîtier dérivation"
                        val destNode = findClosestNodeName(orientedPts.last(), allNodes, 30.0)
                            ?: "-"

                        val roleLabel = when (currentLevel) {
                            1 -> "Enfant (Dérivé N1)"
                            2 -> "Enfant (Dérivé N2)"
                            else -> "Enfant (Dérivé N$currentLevel)"
                        }

                        val classifiedChild = orientedChild.copy(
                            isParent = false,
                            isChild = true,
                            hierarchyLevel = currentLevel,
                            hierarchyRole = roleLabel,
                            parentCableName = bestParent.name,
                            originNodeName = originNode,
                            destinationNodeName = destNode
                        )
                        oriented.add(classifiedChild)
                        progressMade = true
                    }
                }
            }
        }

        propagateChildren()

        // PASSE 3 : Raccordement des branches et sous-réseaux locaux restants
        // Si des câbles se connectent entre eux sans être reliés à la racine principale,
        // on ne doit PAS les marquer isolés ! On promeut le plus important en parent de branche locale.
        while (unoriented.isNotEmpty()) {
            var foundConnection = false
            var bestPair: Pair<ClassifiedCable, ClassifiedCable>? = null
            var minPairDist = Double.MAX_VALUE

            for (i in 0 until unoriented.size) {
                for (j in i + 1 until unoriented.size) {
                    val (d, _) = checkCableConnection(unoriented[i], unoriented[j], boitierNodes)
                    if (d <= 35.0 && d < minPairDist) {
                        minPairDist = d
                        bestPair = Pair(unoriented[i], unoriented[j])
                        foundConnection = true
                    }
                }
            }

            if (foundConnection && bestPair != null) {
                val c1 = bestPair.first
                val c2 = bestPair.second
                val fo1 = c1.capacityFO.filter { it.isDigit() }.toIntOrNull() ?: 0
                val fo2 = c2.capacityFO.filter { it.isDigit() }.toIntOrNull() ?: 0
                val head = if (fo1 > fo2 || (fo1 == fo2 && c1.totalLengthMeters >= c2.totalLengthMeters)) c1 else c2

                unoriented.remove(head)
                val pts = getCableAllPoints(head)
                val originBoitier = if (pts.isNotEmpty()) findClosestNodeName(pts.first(), boitierNodes, 30.0) ?: "Origine Branche" else "Origine Branche"
                val destBoitier = if (pts.isNotEmpty()) findClosestNodeName(pts.last(), boitierNodes, 30.0) ?: "-" else "-"

                oriented.add(
                    head.copy(
                        isParent = true,
                        isChild = false,
                        hierarchyLevel = 1,
                        hierarchyRole = "Parent (Branche locale)",
                        parentCableName = "Branche locale",
                        originNodeName = originBoitier,
                        destinationNodeName = destBoitier
                    )
                )

                // Relancer la propagation pour rattacher tous les enfants de ce sous-réseau
                propagateChildren()
            } else {
                break
            }
        }

        // PASSE 4 : Uniquement les câbles STRICTEMENT isolés (aucune connexion à aucun câble ni boîtier partagé)
        for (cable in unoriented) {
            val pts = getCableAllPoints(cable)
            val startPt = pts.firstOrNull()
            val endPt = pts.lastOrNull()
            val originNode = if (startPt != null) findClosestNodeName(startPt, boitierNodes, 30.0) ?: "-" else "-"
            val destNode = if (endPt != null) findClosestNodeName(endPt, boitierNodes, 30.0) ?: "-" else "-"

            val classifiedIsolated = cable.copy(
                isParent = false,
                isChild = false,
                hierarchyLevel = 99,
                hierarchyRole = "Isolé (Non raccordé)",
                parentCableName = "-",
                originNodeName = originNode,
                destinationNodeName = destNode
            )
            oriented.add(classifiedIsolated)
        }

        return oriented
    }

    /**
     * Vérifie la connexion physique et topologique entre un câble candidat et un câble parent.
     * Prend en compte la distance segmentaire directe et la proximité via un boîtier commun.
     */
    fun checkCableConnection(
        candidate: ClassifiedCable,
        parent: ClassifiedCable,
        boitierNodes: List<FtthNodeEntity>
    ): Pair<Double, Boolean> {
        val candPts = getCableAllPoints(candidate)
        if (candPts.size < 2) return Pair(Double.MAX_VALUE, false)

        val candStart = candPts.first()
        val candEnd = candPts.last()

        val dStartToParent = minDistanceToCable(candStart, parent)
        val dEndToParent = minDistanceToCable(candEnd, parent)

        val parentPts = getCableAllPoints(parent)
        val parentStart = parentPts.firstOrNull()
        val parentEnd = parentPts.lastOrNull()
        val dParentStartToCand = if (parentStart != null) minDistanceToCable(parentStart, candidate) else Double.MAX_VALUE
        val dParentEndToCand = if (parentEnd != null) minDistanceToCable(parentEnd, candidate) else Double.MAX_VALUE

        val minDirectDist = minOf(minOf(dStartToParent, dEndToParent), minOf(dParentStartToCand, dParentEndToCand))

        // Vérifier si candidate et parent partagent un même boîtier
        var sharedBoitierDist = Double.MAX_VALUE
        var sharedBoitierNeedReverse = false
        for (b in boitierNodes) {
            val dCandStart = TrackGeometryHelper.calculateDistanceMeters(candStart.latitude, candStart.longitude, b.latitude, b.longitude)
            val dCandEnd = TrackGeometryHelper.calculateDistanceMeters(candEnd.latitude, candEnd.longitude, b.latitude, b.longitude)
            val minCandB = minOf(dCandStart, dCandEnd)

            if (minCandB <= 25.0) {
                val bPt = TrackPoint(b.latitude, b.longitude)
                val dParentB = minDistanceToCable(bPt, parent)
                if (dParentB <= 25.0) {
                    val combinedD = minCandB + dParentB
                    if (combinedD < sharedBoitierDist) {
                        sharedBoitierDist = minCandB
                        sharedBoitierNeedReverse = (dCandEnd < dCandStart)
                    }
                }
            }
        }

        val effectiveDist = minOf(minDirectDist, sharedBoitierDist)
        val needReverse = if (sharedBoitierDist < minDirectDist) sharedBoitierNeedReverse else (dEndToParent < dStartToParent)
        return Pair(effectiveDist, needReverse)
    }

    /**
     * Calcule la distance minimale entre un point et l'ensemble de la polyligne d'un câble (segments et sommets).
     */
    fun minDistanceToCable(p: TrackPoint, cable: ClassifiedCable): Double {
        val pts = getCableAllPoints(cable)
        if (pts.isEmpty()) return Double.MAX_VALUE
        if (pts.size == 1) return TrackGeometryHelper.calculateDistanceMeters(p.latitude, p.longitude, pts[0].latitude, pts[0].longitude)
        var minD = Double.MAX_VALUE
        for (i in 0 until pts.size - 1) {
            val d = TrackGeometryHelper.distanceToSegmentMeters(p, pts[i], pts[i + 1])
            if (d < minD) minD = d
        }
        return minD
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
        val allPoints = mutableListOf<TrackPoint>()
        for (trk in cable.subTracks) {
            val pts = if (trk.points.isNotEmpty()) trk.points else trk.rawPoints
            if (pts.isNotEmpty()) {
                if (allPoints.isEmpty()) {
                    allPoints.addAll(pts)
                } else {
                    val lastPt = allPoints.last()
                    val firstPt = pts.first()
                    val d = TrackGeometryHelper.calculateDistanceMeters(lastPt.latitude, lastPt.longitude, firstPt.latitude, firstPt.longitude)
                    if (d <= 0.05) {
                        allPoints.addAll(pts.subList(1, pts.size))
                    } else {
                        allPoints.addAll(pts)
                    }
                }
            }
        }
        return allPoints
    }

    /**
     * Ordonne et oriente tête-à-queue les tronçons d'un cluster pour former un chemin géométrique continu.
     * Évite les sauts transversaux à travers la carte entre tronçons non contigus.
     */
    fun orderAndChainSubTracks(tracks: List<StoredTrack>): List<StoredTrack> {
        if (tracks.size <= 1) return tracks

        val remaining = tracks.map { trk ->
            val pts = if (trk.points.isNotEmpty()) trk.points else trk.rawPoints
            trk.copy(points = pts, rawPoints = pts)
        }.toMutableList()

        // 1. Trouver un tronçon d'extrémité (qui a une extrémité libre ne touchant aucun autre tronçon à <= 2.0m)
        var extremityIdx = 0
        var startReversed = false

        for ((i, trk) in remaining.withIndex()) {
            val sPt = trk.points.firstOrNull() ?: continue
            val ePt = trk.points.lastOrNull() ?: continue

            val sTouches = remaining.indices.count { j ->
                if (i == j) false else {
                    val other = remaining[j]
                    val oS = other.points.firstOrNull()
                    val oE = other.points.lastOrNull()
                    (oS != null && TrackGeometryHelper.calculateDistanceMeters(sPt.latitude, sPt.longitude, oS.latitude, oS.longitude) <= 2.0) ||
                    (oE != null && TrackGeometryHelper.calculateDistanceMeters(sPt.latitude, sPt.longitude, oE.latitude, oE.longitude) <= 2.0)
                }
            }

            val eTouches = remaining.indices.count { j ->
                if (i == j) false else {
                    val other = remaining[j]
                    val oS = other.points.firstOrNull()
                    val oE = other.points.lastOrNull()
                    (oS != null && TrackGeometryHelper.calculateDistanceMeters(ePt.latitude, ePt.longitude, oS.latitude, oS.longitude) <= 2.0) ||
                    (oE != null && TrackGeometryHelper.calculateDistanceMeters(ePt.latitude, ePt.longitude, oE.latitude, oE.longitude) <= 2.0)
                }
            }

            if (sTouches == 0 && eTouches > 0) {
                extremityIdx = i
                startReversed = false
                break
            } else if (eTouches == 0 && sTouches > 0) {
                extremityIdx = i
                startReversed = true
                break
            }
        }

        val firstTrk = remaining.removeAt(extremityIdx)
        val orderedTrks = mutableListOf<StoredTrack>()
        if (startReversed) {
            val revPts = firstTrk.points.reversed()
            orderedTrks.add(firstTrk.copy(points = revPts, rawPoints = revPts))
        } else {
            orderedTrks.add(firstTrk)
        }

        // 2. Enchaîner tête-à-queue les tronçons restants
        while (remaining.isNotEmpty()) {
            val lastPt = orderedTrks.last().points.lastOrNull() ?: break
            var bestIdx = 0
            var bestRev = false
            var minD = Double.MAX_VALUE

            for ((i, trk) in remaining.withIndex()) {
                val sPt = trk.points.firstOrNull() ?: continue
                val ePt = trk.points.lastOrNull() ?: continue
                val dStart = TrackGeometryHelper.calculateDistanceMeters(lastPt.latitude, lastPt.longitude, sPt.latitude, sPt.longitude)
                val dEnd = TrackGeometryHelper.calculateDistanceMeters(lastPt.latitude, lastPt.longitude, ePt.latitude, ePt.longitude)

                if (dStart < minD) {
                    minD = dStart
                    bestIdx = i
                    bestRev = false
                }
                if (dEnd < minD) {
                    minD = dEnd
                    bestIdx = i
                    bestRev = true
                }
            }

            val nextTrk = remaining.removeAt(bestIdx)
            if (bestRev) {
                val revPts = nextTrk.points.reversed()
                orderedTrks.add(nextTrk.copy(points = revPts, rawPoints = revPts))
            } else {
                orderedTrks.add(nextTrk)
            }
        }

        return orderedTrks
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
     * Regrouper géométriquement les tronçons qui partagent des points d'extrémité communs (distance <= 3.0m).
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

                    // Seuil d'accrochage/point commun de 1 mètre (jonction de 2 tronçons)
                    if (minDistance <= 1.0) {
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

        val parents = cables.count { it.isParent }
        val children = cables.count { it.isChild }

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
            parentCablesCount = parents,
            childCablesCount = children,
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
