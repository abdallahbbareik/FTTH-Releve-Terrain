package com.example.data.util

import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.storage.TrackPoint

/**
 * Connectivité optique d'un boîtier (BPE, PBO, Splitter, etc.) ou SRO avec les câbles du réseau :
 * - Concerne EXCLUSIVEMENT les Boîtiers et les SRO (pas de recherche pour Chambres et Poteaux).
 * - Câble Entrée : Un seul câble par boîtier (extrémité réelle qui s'arrête là, ou câble qui traverse/passe par le boîtier).
 * - Câbles Sortie : 0, 1 ou plusieurs câbles (ceux qui démarrent à ce point, ou le câble d'entrée s'il passe par le boîtier).
 */
data class BoitierConnectivity(
    val nodeId: String,
    val nodeName: String,
    val isBoitier: Boolean,
    val isSro: Boolean = false,
    val incomingCable: ClassifiedCable?,
    val incomingCableName: String,
    val outgoingCables: List<ClassifiedCable>,
    val outgoingCablesStr: String
)

object BoitierCableConnectivityHelper {

    fun cleanCableName(name: String): String {
        return name.removePrefix("::").removePrefix(":").removePrefix(" :").trim()
    }

    /**
     * Détermine les câbles d'entrée et de sortie pour chaque nœud éligible.
     *
     * RÈGLES MÉTIER FTTH :
     * 1. La détection des câbles d'entrée et de sortie ne concerne QUE les boîtiers et les SRO.
     *    Pas de recherche pour les chambres et poteaux (valeurs "-" directes).
     * 2. Câble Entrée : Un seul câble par boîtier (celui dont l'extrémité réelle s'arrête là, ou câble en passage).
     * 3. Câbles Sortie : 0, 1 ou plusieurs câbles (ceux qui démarrent à ce point).
     * 4. Le câble d'entrée peut être listé dans les câbles de sortie si le même câble passe par le boîtier.
     *
     * @param nodes La liste de tous les nœuds de la zone
     * @param cables La liste des câbles classifiés et réorientés (depuis le SRO)
     * @param maxToleranceMeters Seuil de tolérance GPS pour l'accrochage (35m par défaut)
     */
    fun computeConnectivities(
        nodes: List<FtthNodeEntity>,
        cables: List<ClassifiedCable>,
        maxToleranceMeters: Double = 35.0
    ): Map<String, BoitierConnectivity> {
        if (nodes.isEmpty() || cables.isEmpty()) return emptyMap()

        data class CableGeometry(
            val cable: ClassifiedCable,
            val points: List<TrackPoint>,
            val startPt: TrackPoint,
            val endPt: TrackPoint,
            val totalLength: Double
        )

        val cableGeometries = cables.mapNotNull { c ->
            val pts = KmzCableClassifier.getCableAllPoints(c)
            if (pts.size >= 2) {
                val len = if (c.totalLengthMeters > 0) c.totalLengthMeters else TrackGeometryHelper.computeTotalDistanceMeters(pts)
                CableGeometry(
                    cable = c,
                    points = pts,
                    startPt = pts.first(),
                    endPt = pts.last(),
                    totalLength = len
                )
            } else null
        }

        // Identifier les positions de tous les SRO pour orienter le sens amont/aval
        val sroPoints = mutableListOf<TrackPoint>()
        for (n in nodes) {
            val classified = KmzNodeClassifier.classifyNode(n)
            if (classified.category == KmzNodeCategory.SRO ||
                n.type == FtthNodeType.SRO ||
                n.name.startsWith("SRO", ignoreCase = true) ||
                n.name.contains("SRO", ignoreCase = true)
            ) {
                sroPoints.add(TrackPoint(latitude = n.latitude, longitude = n.longitude))
            }
        }

        fun minDistToSro(pt: TrackPoint): Double {
            if (sroPoints.isEmpty()) return 0.0
            var minD = Double.MAX_VALUE
            for (sroPt in sroPoints) {
                val d = TrackGeometryHelper.calculateDistanceMeters(pt.latitude, pt.longitude, sroPt.latitude, sroPt.longitude)
                if (d < minD) minD = d
            }
            return minD
        }

        val resultMap = mutableMapOf<String, BoitierConnectivity>()

        for (node in nodes) {
            val classified = KmzNodeClassifier.classifyNode(node)
            val isChambre = classified.category == KmzNodeCategory.CHAMBRE
            val isPoteau = classified.category == KmzNodeCategory.POTEAU
            val isSro = classified.category == KmzNodeCategory.SRO ||
                    node.type == FtthNodeType.SRO ||
                    node.name.startsWith("SRO", ignoreCase = true) ||
                    node.name.contains("SRO", ignoreCase = true)

            val isNameBoitier = node.name.contains("boitier", ignoreCase = true) ||
                    node.name.contains("split", ignoreCase = true) ||
                    node.name.contains("joint", ignoreCase = true) ||
                    node.name.contains("bpe", ignoreCase = true) ||
                    node.name.contains("pbo", ignoreCase = true) ||
                    node.name.contains("pec", ignoreCase = true) ||
                    node.name.contains("bti", ignoreCase = true)

            val isBoitier = (classified.category == KmzNodeCategory.BOITIER ||
                    node.type == FtthNodeType.BOITIER ||
                    isNameBoitier) && !isChambre && !isPoteau

            // Règle 1 : La détection ne concerne QUE les boîtiers et SRO. Ignorer chambres et poteaux.
            val isTarget = (isBoitier || isSro) && !isChambre && !isPoteau
            if (!isTarget) {
                resultMap[node.id] = BoitierConnectivity(
                    nodeId = node.id,
                    nodeName = node.name,
                    isBoitier = false,
                    isSro = false,
                    incomingCable = null,
                    incomingCableName = "-",
                    outgoingCables = emptyList(),
                    outgoingCablesStr = "-"
                )
                continue
            }

            val nodePt = TrackPoint(latitude = node.latitude, longitude = node.longitude)

            data class CableMatch(
                val cableGeom: CableGeometry,
                val dUpstream: Double,       // Distance à l'extrémité la plus proche du SRO (départ)
                val dDownstream: Double,     // Distance à l'extrémité la plus éloignée du SRO (arrivée)
                val minDistToPath: Double,   // Distance minimale au tracé du câble
                val fraction: Double,        // Position relative le long du câble [0.0 - 1.0]
                val isAtDownstreamEnd: Boolean,
                val isAtUpstreamEnd: Boolean,
                val isPassThrough: Boolean
            )

            val matches = mutableListOf<CableMatch>()

            for (cg in cableGeometries) {
                val distStartToSro = minDistToSro(cg.startPt)
                val distEndToSro = minDistToSro(cg.endPt)

                val (dStartRaw, dEndRaw) = if (distStartToSro <= distEndToSro) {
                    Pair(
                        TrackGeometryHelper.calculateDistanceMeters(node.latitude, node.longitude, cg.startPt.latitude, cg.startPt.longitude),
                        TrackGeometryHelper.calculateDistanceMeters(node.latitude, node.longitude, cg.endPt.latitude, cg.endPt.longitude)
                    )
                } else {
                    Pair(
                        TrackGeometryHelper.calculateDistanceMeters(node.latitude, node.longitude, cg.endPt.latitude, cg.endPt.longitude),
                        TrackGeometryHelper.calculateDistanceMeters(node.latitude, node.longitude, cg.startPt.latitude, cg.startPt.longitude)
                    )
                }

                val dUpstream = dStartRaw
                val dDownstream = dEndRaw

                // Proximité et projection sur le câble
                var minDistToPath = Math.min(dUpstream, dDownstream)
                var closestCumulDist = if (dUpstream < dDownstream) 0.0 else cg.totalLength
                var curCumul = 0.0

                for (i in 0 until cg.points.size - 1) {
                    val p1 = cg.points[i]
                    val p2 = cg.points[i + 1]
                    val segLen = TrackGeometryHelper.calculateDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
                    val dSeg = TrackGeometryHelper.distanceToSegmentMeters(nodePt, p1, p2)

                    if (dSeg < minDistToPath) {
                        minDistToPath = dSeg
                        val dP1 = TrackGeometryHelper.calculateDistanceMeters(node.latitude, node.longitude, p1.latitude, p1.longitude)
                        val dP2 = TrackGeometryHelper.calculateDistanceMeters(node.latitude, node.longitude, p2.latitude, p2.longitude)
                        val proj = if (segLen > 0.001) {
                            (((dP1 * dP1) - (dP2 * dP2) + (segLen * segLen)) / (2.0 * segLen)).coerceIn(0.0, segLen)
                        } else 0.0
                        closestCumulDist = curCumul + proj
                    }
                    curCumul += segLen
                }

                val fraction = if (cg.totalLength > 0.001) (closestCumulDist / cg.totalLength).coerceIn(0.0, 1.0) else 0.5

                // Critères de rôle du câble par rapport au nœud
                val tolerance = maxToleranceMeters // 35m
                val isAtDownstreamEnd = (dDownstream <= tolerance) || (dDownstream < dUpstream && fraction >= 0.70 && minDistToPath <= tolerance)
                val isAtUpstreamEnd = (dUpstream <= tolerance) || (dUpstream < dDownstream && fraction <= 0.30 && minDistToPath <= tolerance)
                val isPassThrough = (minDistToPath <= tolerance) && !isAtDownstreamEnd && !isAtUpstreamEnd

                if (isAtDownstreamEnd || isAtUpstreamEnd || isPassThrough || minDistToPath <= tolerance) {
                    matches.add(
                        CableMatch(
                            cableGeom = cg,
                            dUpstream = dUpstream,
                            dDownstream = dDownstream,
                            minDistToPath = minDistToPath,
                            fraction = fraction,
                            isAtDownstreamEnd = isAtDownstreamEnd,
                            isAtUpstreamEnd = isAtUpstreamEnd,
                            isPassThrough = isPassThrough
                        )
                    )
                }
            }

            // 1. CÂBLE ENTRÉE
            var bestIncomingCable: ClassifiedCable? = null

            if (!isSro) {
                // Priorité 1 : Le câble dont l'extrémité aval s'arrête au boîtier (isAtDownstreamEnd)
                val bestEndMatch = matches.filter { it.isAtDownstreamEnd }.minByOrNull { it.dDownstream }
                if (bestEndMatch != null) {
                    bestIncomingCable = bestEndMatch.cableGeom.cable
                } else {
                    // Priorité 2 : Le câble en passage (isPassThrough)
                    val bestPassMatch = matches.filter { it.isPassThrough }.minByOrNull { it.minDistToPath }
                    if (bestPassMatch != null) {
                        bestIncomingCable = bestPassMatch.cableGeom.cable
                    } else {
                        // Priorité 3 : Tout câble s'approchant le plus du boîtier
                        val closestMatch = matches.minByOrNull { it.minDistToPath }
                        if (closestMatch != null && closestMatch.minDistToPath <= 60.0) {
                            bestIncomingCable = closestMatch.cableGeom.cable
                        } else {
                            // Fallback ultime : chercher parmi TOUS les câbles le câble le plus proche
                            val globalClosest = cableGeometries.minByOrNull { cg ->
                                val d1 = TrackGeometryHelper.calculateDistanceMeters(node.latitude, node.longitude, cg.startPt.latitude, cg.startPt.longitude)
                                val d2 = TrackGeometryHelper.calculateDistanceMeters(node.latitude, node.longitude, cg.endPt.latitude, cg.endPt.longitude)
                                Math.min(d1, d2)
                            }
                            if (globalClosest != null) {
                                val dStartGlobal = TrackGeometryHelper.calculateDistanceMeters(node.latitude, node.longitude, globalClosest.startPt.latitude, globalClosest.startPt.longitude)
                                val dEndGlobal = TrackGeometryHelper.calculateDistanceMeters(node.latitude, node.longitude, globalClosest.endPt.latitude, globalClosest.endPt.longitude)
                                if (minOf(dStartGlobal, dEndGlobal) <= 150.0) {
                                    bestIncomingCable = globalClosest.cable
                                }
                            }
                        }
                    }
                }
            } else {
                // Pour SRO : uniquement si un câble externe de transport s'y termine
                val bestEndMatch = matches.filter { it.isAtDownstreamEnd }.minByOrNull { it.dDownstream }
                if (bestEndMatch != null) {
                    bestIncomingCable = bestEndMatch.cableGeom.cable
                }
            }

            // 2. CÂBLES SORTIE
            val outgoingList = mutableListOf<ClassifiedCable>()

            for (match in matches) {
                var isOutgoing = false

                if (isSro) {
                    // Pour le SRO, tous les câbles qui démarrent du SRO ou y passent sont des sorties
                    if (match.dUpstream <= maxToleranceMeters || match.dDownstream <= maxToleranceMeters || match.minDistToPath <= maxToleranceMeters) {
                        isOutgoing = true
                    }
                } else {
                    // Condition A : Le câble démarre à ce point vers l'aval (isAtUpstreamEnd)
                    if (match.isAtUpstreamEnd) {
                        isOutgoing = true
                    }
                    // Condition B : Le câble passe par le boîtier (il continue donc vers l'aval)
                    if (match.isPassThrough) {
                        isOutgoing = true
                    }
                }

                if (isOutgoing) {
                    if (outgoingList.none { it.id == match.cableGeom.cable.id }) {
                        outgoingList.add(match.cableGeom.cable)
                    }
                }
            }

            // Règle 4/6 : Si un câble d'entrée est en passage par ce boîtier, il doit AUSSI figurer dans les câbles de sortie
            if (bestIncomingCable != null && !isSro) {
                val incomingMatch = matches.firstOrNull { it.cableGeom.cable.id == bestIncomingCable.id }
                if (incomingMatch != null && incomingMatch.isPassThrough) {
                    if (outgoingList.none { it.id == bestIncomingCable.id }) {
                        outgoingList.add(bestIncomingCable)
                    }
                }
            }

            val incomingName = if (bestIncomingCable != null) cleanCableName(bestIncomingCable.name) else "-"
            val outgoingStr = if (outgoingList.isNotEmpty()) {
                outgoingList.joinToString(" | ") { cleanCableName(it.name) }
            } else "-"

            resultMap[node.id] = BoitierConnectivity(
                nodeId = node.id,
                nodeName = node.name,
                isBoitier = isBoitier,
                isSro = isSro,
                incomingCable = bestIncomingCable,
                incomingCableName = incomingName,
                outgoingCables = outgoingList,
                outgoingCablesStr = outgoingStr
            )
        }

        return resultMap
    }
}

