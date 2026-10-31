package com.example.data.util

import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.storage.StoredTrack
import com.example.data.storage.TrackPoint
import java.util.Locale

/**
 * Support traversé (Chambre, Poteau, SRO, Boîtier, etc.) le long du tracé d'un câble,
 * ordonné du point de départ (0m) jusqu'à la fin du câble.
 */
data class IntersectedSupport(
    val index: Int,                         // Numéro d'ordre (1, 2, 3...)
    val node: FtthNodeEntity,              // Nœud/support concerné
    val supportTypeLabel: String,          // "Chambre", "Poteau", "SRO", "Boîtier", etc.
    val isChambre: Boolean,
    val isPoteau: Boolean,
    val isSro: Boolean = false,
    val isBoitier: Boolean = false,
    val cumulativeDistanceMeters: Double,  // Distance cumulée depuis le départ du câble (m)
    val intervalDistanceMeters: Double,    // Distance par rapport au support précédent (m)
    val perpendicularDistanceMeters: Double, // Distance d'écartement par rapport au câble (m)
    val hostedBoitiers: List<FtthNodeEntity> = emptyList() // Boîtier(s) posé(s) / hébergé(s) dans ou sur ce support
)

/**
 * Synthèse de l'analyse des supports traversés par un câble.
 */
data class CableSupportAnalysisResult(
    val cableId: String,
    val cableName: String,
    val totalLengthMeters: Double,
    val supports: List<IntersectedSupport>,
    val totalChambresCount: Int,
    val totalPoteauxCount: Int,
    val totalSroCount: Int = 0,
    val totalBoitiersCount: Int = 0,      // Total de tous les boîtiers (hébergés + autonomes)
    val totalOtherSupportsCount: Int = 0
)

object CableSupportAnalyzer {

    /**
     * Identifier et ordonner tous les supports (SRO, Chambres, Poteaux, etc.) traversés par un câble,
     * de son point de départ jusqu'à sa fin, en rattachant les boîtiers à leurs supports d'accueil.
     *
     * RÈGLE STRICTE SUR LES BOÎTIERS :
     * Un boîtier appartient à un câble UNIQUEMENT s'il intersecte avec l'une des extrémités d'un tronçon.
     * Les boîtiers situés dans une chambre ou un poteau au milieu d'un tronçon ne sont pas conservés.
     *
     * @param track Le câble (tracé/polyligne)
     * @param allNodes La liste de tous les nœuds de la zone
     * @param maxToleranceMeters Seuil d'écartement maximal par rapport au fil du câble (par défaut 1 mètre)
     * @param subTracks Liste optionnelle des tronçons (sous-tracés) composant le câble
     */
    fun analyzeCableSupports(
        track: StoredTrack,
        allNodes: List<FtthNodeEntity>,
        maxToleranceMeters: Double = 1.0,
        subTracks: List<StoredTrack> = emptyList()
    ): CableSupportAnalysisResult {
        val points = if (track.points.isNotEmpty()) track.points else track.rawPoints
        if (points.size < 2 || allNodes.isEmpty()) {
            return CableSupportAnalysisResult(
                cableId = track.id,
                cableName = track.name,
                totalLengthMeters = track.totalDistanceMeters,
                supports = emptyList(),
                totalChambresCount = 0,
                totalPoteauxCount = 0,
                totalSroCount = 0,
                totalBoitiersCount = 0,
                totalOtherSupportsCount = 0
            )
        }

        val totalLength = if (track.totalDistanceMeters > 0) track.totalDistanceMeters else TrackGeometryHelper.computeTotalDistanceMeters(points)

        // 1. Déterminer la liste des tronçons et leurs points d'extrémités (début/fin de chaque tronçon)
        val activeSubTracks = if (subTracks.isNotEmpty()) subTracks else listOf(track)
        val tronconExtremityPoints = mutableListOf<TrackPoint>()

        for (st in activeSubTracks) {
            val stPts = if (st.points.isNotEmpty()) st.points else st.rawPoints
            if (stPts.isNotEmpty()) {
                tronconExtremityPoints.add(stPts.first())
                tronconExtremityPoints.add(stPts.last())
            }
        }

        // Pré-calculer les distances cumulées le long des sommets P0, P1, P2 ... Pn
        val segmentStartDistances = DoubleArray(points.size)
        var accDist = 0.0
        segmentStartDistances[0] = 0.0
        for (i in 0 until points.size - 1) {
            accDist += TrackGeometryHelper.calculateDistanceMeters(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude
            )
            segmentStartDistances[i + 1] = accDist
        }

        // Déterminer la distance cumulée projetée de chaque point d'extrémité de tronçon le long de la ligne du câble
        val tronconExtremityCumulDistances = mutableListOf<Double>()
        for (extPt in tronconExtremityPoints) {
            val nodePt = TrackPoint(latitude = extPt.latitude, longitude = extPt.longitude)
            var minPerp = Double.MAX_VALUE
            var bestCumul = 0.0

            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val perp = TrackGeometryHelper.distanceToSegmentMeters(nodePt, p1, p2)
                if (perp < minPerp) {
                    minPerp = perp
                    val segLength = TrackGeometryHelper.calculateDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
                    val projDistOnSegment = if (segLength > 0.001) {
                        val dP1 = TrackGeometryHelper.calculateDistanceMeters(extPt.latitude, extPt.longitude, p1.latitude, p1.longitude)
                        val dP2 = TrackGeometryHelper.calculateDistanceMeters(extPt.latitude, extPt.longitude, p2.latitude, p2.longitude)
                        val proj = ((dP1 * dP1) - (dP2 * dP2) + (segLength * segLength)) / (2.0 * segLength)
                        proj.coerceIn(0.0, segLength)
                    } else {
                        0.0
                    }
                    bestCumul = segmentStartDistances[i] + projDistOnSegment
                }
            }
            tronconExtremityCumulDistances.add(bestCumul)
        }

        // Structure temporaire pour chaque nœud candidat le long du câble
        data class CandidateNode(
            val node: FtthNodeEntity,
            val cumulDist: Double,
            val perpDist: Double,
            val isSro: Boolean,
            val isChambre: Boolean,
            val isPoteau: Boolean,
            val isBoitierOnly: Boolean
        )

        val candidateList = mutableListOf<CandidateNode>()

        for (node in allNodes) {
            val nodePt = TrackPoint(latitude = node.latitude, longitude = node.longitude)
            var minPerpDist = Double.MAX_VALUE
            var bestCumulDist = 0.0

            // Tester la proximité orthogonale avec chaque segment [i, i+1] du câble
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                val perpDist = TrackGeometryHelper.distanceToSegmentMeters(nodePt, p1, p2)

                if (perpDist < minPerpDist) {
                    minPerpDist = perpDist

                    val segLength = TrackGeometryHelper.calculateDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
                    val projDistOnSegment = if (segLength > 0.001) {
                        val dP1 = TrackGeometryHelper.calculateDistanceMeters(node.latitude, node.longitude, p1.latitude, p1.longitude)
                        val dP2 = TrackGeometryHelper.calculateDistanceMeters(node.latitude, node.longitude, p2.latitude, p2.longitude)
                        val proj = ((dP1 * dP1) - (dP2 * dP2) + (segLength * segLength)) / (2.0 * segLength)
                        proj.coerceIn(0.0, segLength)
                    } else {
                        0.0
                    }

                    bestCumulDist = segmentStartDistances[i] + projDistOnSegment
                }
            }

            val classified = KmzNodeClassifier.classifyNode(node)

            val isSro = node.type == FtthNodeType.SRO || classified.category == KmzNodeCategory.SRO ||
                    node.name.startsWith("SRO", ignoreCase = true) ||
                    node.name.contains("SRO", ignoreCase = true)
            val isChambre = !isSro && (node.type == FtthNodeType.CHAMBRE || classified.category == KmzNodeCategory.CHAMBRE ||
                    node.name.contains("chambre", ignoreCase = true) || node.name.contains(" regard", ignoreCase = true))
            val isPoteau = !isSro && (node.type == FtthNodeType.POTEAU || classified.category == KmzNodeCategory.POTEAU ||
                    node.name.contains("poteau", ignoreCase = true) || node.name.contains("appui", ignoreCase = true))
            val isBoitier = !isSro && (node.type == FtthNodeType.BOITIER || classified.category == KmzNodeCategory.BOITIER ||
                    node.name.contains("boitier", ignoreCase = true) || node.name.contains("split", ignoreCase = true) ||
                    node.name.contains("joint", ignoreCase = true) || node.name.contains("pbo", ignoreCase = true))

            val isBoitierOnly = isBoitier && !isSro && !isChambre && !isPoteau

            // Distance aux extrémités pour SRO (au cas où le câble commence/termine à quelques mètres du SRO)
            val distToStart = TrackGeometryHelper.calculateDistanceMeters(node.latitude, node.longitude, points.first().latitude, points.first().longitude)
            val distToEnd = TrackGeometryHelper.calculateDistanceMeters(node.latitude, node.longitude, points.last().latitude, points.last().longitude)
            val isNearExtremitySro = isSro && (distToStart <= 25.0 || distToEnd <= 25.0)

            // Conserver les nœuds situés dans la zone d'influence du fil du câble (<= maxToleranceMeters)
            // Pour le SRO, on accorde une tolérance plus large (jusqu'à 15m orthogonal ou 25m d'extrémité)
            if (minPerpDist <= maxToleranceMeters || (isSro && minPerpDist <= 15.0) || isNearExtremitySro) {
                val effectiveCumul = if (isNearExtremitySro && minPerpDist > maxToleranceMeters) {
                    if (distToStart <= distToEnd) 0.0 else totalLength
                } else {
                    bestCumulDist
                }

                candidateList.add(
                    CandidateNode(
                        node = node,
                        cumulDist = effectiveCumul,
                        perpDist = minPerpDist,
                        isSro = isSro,
                        isChambre = isChambre,
                        isPoteau = isPoteau,
                        isBoitierOnly = isBoitierOnly
                    )
                )
            }
        }

        // Séparer les supports d'infrastructures physiques (SRO, Chambre, Poteau, Autre) et les boîtiers
        val primaryCandidates = candidateList.filter { !it.isBoitierOnly }.toMutableList()
        val rawBoitierCandidates = candidateList.filter { it.isBoitierOnly }

        // FILTRAGE DES BOÎTIERS : Un boîtier appartient au câble UNIQUEMENT s'il intersecte avec l'une des extrémités d'un tronçon
        val validBoitierCandidates = rawBoitierCandidates.filter { b ->
            val isNearGPS = tronconExtremityPoints.any { extPt ->
                TrackGeometryHelper.calculateDistanceMeters(
                    b.node.latitude, b.node.longitude,
                    extPt.latitude, extPt.longitude
                ) <= 10.0 // Proximité GPS <= 10m de l'extrémité d'un tronçon
            }
            val isNearCumul = tronconExtremityCumulDistances.any { extCumul ->
                Math.abs(b.cumulDist - extCumul) <= 5.0 // Proximité le long du tracé <= 5m de l'extrémité d'un tronçon
            }
            isNearGPS || isNearCumul
        }

        // Map associant ID du support principal ➔ Liste des boîtiers hébergés
        val hostedBoitiersMap = mutableMapOf<String, MutableList<FtthNodeEntity>>()

        for (b in validBoitierCandidates) {
            // Chercher le support d'infrastructure le plus proche de ce boîtier
            // (Proximité spatiale le long du câble <= 5m OU proximité GPS <= 10m)
            var bestPrimary: CandidateNode? = null
            var minDeltaDist = Double.MAX_VALUE

            for (p in primaryCandidates) {
                val deltaCumul = Math.abs(b.cumulDist - p.cumulDist)
                val gpsDist = TrackGeometryHelper.calculateDistanceMeters(
                    b.node.latitude, b.node.longitude,
                    p.node.latitude, p.node.longitude
                )

                if (deltaCumul <= 5.0 || gpsDist <= 10.0) {
                    if (deltaCumul < minDeltaDist) {
                        minDeltaDist = deltaCumul
                        bestPrimary = p
                    }
                }
            }

            if (bestPrimary != null) {
                // Le boîtier est hébergé dans/sur le support d'infrastructure trouvé
                hostedBoitiersMap.getOrPut(bestPrimary.node.id) { mutableListOf() }.add(b.node)
            } else {
                // Aucun support trouvé à proximité : le boîtier est autonome (ex: boîtier de début/fin ou aérien isolé)
                primaryCandidates.add(b)
            }
        }

        // VÉRIFICATION DE LA PRÉSENCE DU SRO DANS LE PARCOURS :
        // "Si le SRO est compté parmi les nœuds du parcours, le SRO devient l'origine (source des câbles principaux)"
        val sroCandidate = primaryCandidates.firstOrNull { it.isSro }

        val adjustedPrimary = if (sroCandidate != null) {
            val originDist = sroCandidate.cumulDist
            primaryCandidates.map { c ->
                val adjustedDist = Math.abs(c.cumulDist - originDist)
                c.copy(cumulDist = adjustedDist)
            }
        } else {
            primaryCandidates
        }

        // Trier chronologiquement du Départ (0m) vers la Fin du câble
        // Si un SRO est présent, il est obligatoirement positionné en toute première position (index 1 à 0m)
        val sortedPrimary = adjustedPrimary.sortedWith(
            compareBy<CandidateNode> { if (it.isSro) -1.0 else it.cumulDist }
                .thenBy { it.cumulDist }
        )

        val intersectedSupports = mutableListOf<IntersectedSupport>()
        var prevDist = 0.0
        var chambresCount = 0
        var poteauxCount = 0
        var sroCount = 0
        var boitiersCount = 0
        var othersCount = 0

        for ((idx, candidate) in sortedPrimary.withIndex()) {
            val (node, cumulDist, perpDist, isSro, isChambre, isPoteau, isBoitierOnly) = candidate

            val supportLabel = when {
                isSro -> "SRO"
                isChambre -> "Chambre"
                isPoteau -> "Poteau"
                isBoitierOnly -> "Boîtier"
                else -> node.type.label.ifBlank { "Support" }
            }

            if (isSro) sroCount++
            else if (isChambre) chambresCount++
            else if (isPoteau) poteauxCount++
            else if (isBoitierOnly) boitiersCount++
            else othersCount++

            val hosted = hostedBoitiersMap[node.id] ?: emptyList()
            boitiersCount += hosted.size

            val interval = if (idx == 0) cumulDist else (cumulDist - prevDist).coerceAtLeast(0.0)
            prevDist = cumulDist

            intersectedSupports.add(
                IntersectedSupport(
                    index = idx + 1,
                    node = node,
                    supportTypeLabel = supportLabel,
                    isChambre = isChambre,
                    isPoteau = isPoteau,
                    isSro = isSro,
                    isBoitier = isBoitierOnly,
                    cumulativeDistanceMeters = cumulDist,
                    intervalDistanceMeters = interval,
                    perpendicularDistanceMeters = perpDist,
                    hostedBoitiers = hosted
                )
            )
        }

        return CableSupportAnalysisResult(
            cableId = track.id,
            cableName = track.name,
            totalLengthMeters = totalLength,
            supports = intersectedSupports,
            totalChambresCount = chambresCount,
            totalPoteauxCount = poteauxCount,
            totalSroCount = sroCount,
            totalBoitiersCount = boitiersCount,
            totalOtherSupportsCount = othersCount
        )
    }

    /**
     * Analyser les supports pour un câble regroupé ClassifiedCable.
     */
    fun analyzeClassifiedCableSupports(
        cable: ClassifiedCable,
        allNodes: List<FtthNodeEntity>,
        maxToleranceMeters: Double = 1.0
    ): CableSupportAnalysisResult {
        val syntheticTrack = StoredTrack(
            id = cable.id,
            name = cable.name,
            totalDistanceMeters = cable.totalLengthMeters,
            rawPoints = cable.subTracks.flatMap { if (it.points.isNotEmpty()) it.points else it.rawPoints },
            points = cable.subTracks.flatMap { if (it.points.isNotEmpty()) it.points else it.rawPoints }
        )
        return analyzeCableSupports(syntheticTrack, allNodes, maxToleranceMeters, subTracks = cable.subTracks)
    }
}
