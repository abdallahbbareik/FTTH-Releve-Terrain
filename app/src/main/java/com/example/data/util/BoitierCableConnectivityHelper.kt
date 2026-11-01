package com.example.data.util

import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.storage.TrackPoint
import java.util.Locale

/**
 * Connectivité optique d'un boîtier (BPE, PBO, Splitter, etc.) ou SRO avec les câbles du réseau :
 * - Concerne EXCLUSIVEMENT les Boîtiers et les SRO (pas de recherche pour Chambres et Poteaux).
 * - La recherche se fait au niveau des EXTRÉMITÉS des tronçons/câbles (départ et arrivée).
 * - Si une extrémité de câble ne s'arrête pas pile sur un boîtier (erreur APD), on cherche le boîtier le plus proche
 *   et on enregistre l'anomalie d'accrochage APD.
 */
data class BoitierConnectivity(
    val nodeId: String,
    val nodeName: String,
    val isBoitier: Boolean,
    val isSro: Boolean = false,
    val incomingCable: ClassifiedCable?,
    val incomingCableName: String,
    val outgoingCables: List<ClassifiedCable>,
    val outgoingCablesStr: String,
    val snapDistanceMeters: Double = 0.0,
    val anomalieApd: String = "-"
)

object BoitierCableConnectivityHelper {

    fun cleanCableName(name: String): String {
        return name.removePrefix("::").removePrefix(":").removePrefix(" :").trim()
    }

    /**
     * Détermine les câbles d'entrée et de sortie pour chaque nœud éligible aux extrémités des tronçons.
     *
     * RÈGLES MÉTIER FTTH / APD :
     * 1. Recherche au niveau des EXTRÉMITÉS des câbles/tronçons (startPt / endPt).
     * 2. Seuil de conformité APD : 10 mètres maximum.
     * 3. Si d > 10m, le boîtier le plus proche est raccordé mais noté comme ANOMALIE APD.
     */
    fun computeConnectivities(
        nodes: List<FtthNodeEntity>,
        cables: List<ClassifiedCable>,
        maxToleranceMeters: Double = 10.0
    ): Map<String, BoitierConnectivity> {
        if (nodes.isEmpty() || cables.isEmpty()) return emptyMap()

        data class CableExtremity(
            val cable: ClassifiedCable,
            val startPt: TrackPoint,
            val endPt: TrackPoint,
            val totalLength: Double
        )

        val cableGeometries = cables.mapNotNull { c ->
            val pts = KmzCableClassifier.getCableAllPoints(c)
            if (pts.size >= 2) {
                val len = if (c.totalLengthMeters > 0) c.totalLengthMeters else TrackGeometryHelper.computeTotalDistanceMeters(pts)
                CableExtremity(
                    cable = c,
                    startPt = pts.first(),
                    endPt = pts.last(),
                    totalLength = len
                )
            } else null
        }

        // Identifier les positions des SRO pour déterminer l'amont (départ) et l'aval (arrivée)
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

            // Seuls les Boîtiers et SRO sont concernés
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
                    outgoingCablesStr = "-",
                    snapDistanceMeters = 0.0,
                    anomalieApd = "-"
                )
                continue
            }

            val nodePt = TrackPoint(latitude = node.latitude, longitude = node.longitude)

            data class ExtremityMatch(
                val cableGeom: CableExtremity,
                val distToExtremity: Double,
                val isDownstreamEnd: Boolean,  // true = Extrémité d'arrivée (Aval -> Entrée du boîtier)
                val isUpstreamStart: Boolean,  // true = Extrémité de départ (Amont -> Sortie du boîtier)
                val isPassThrough: Boolean     // Passage le long du tronçon
            )

            val matches = mutableListOf<ExtremityMatch>()

            for (cg in cableGeometries) {
                val distStartToSro = minDistToSro(cg.startPt)
                val distEndToSro = minDistToSro(cg.endPt)

                // Déterminer l'extrémité amont (départ) et aval (arrivée)
                val (upstreamPt, downstreamPt) = if (distStartToSro <= distEndToSro) {
                    Pair(cg.startPt, cg.endPt)
                } else {
                    Pair(cg.endPt, cg.startPt)
                }

                val dUpstream = TrackGeometryHelper.calculateDistanceMeters(node.latitude, node.longitude, upstreamPt.latitude, upstreamPt.longitude)
                val dDownstream = TrackGeometryHelper.calculateDistanceMeters(node.latitude, node.longitude, downstreamPt.latitude, downstreamPt.longitude)

                // Recherche aux EXTRÉMITÉS des tronçons (Zone restreinte 10m - 40m max)
                val isDownstreamEnd = dDownstream <= 40.0 && dDownstream <= dUpstream
                val isUpstreamStart = dUpstream <= 40.0 && dUpstream <= dDownstream

                // Passage intermédiaire si aucune extrémité directe
                val minEndDist = Math.min(dUpstream, dDownstream)
                var dSegMin = minEndDist

                val pts = KmzCableClassifier.getCableAllPoints(cg.cable)
                for (i in 0 until pts.size - 1) {
                    val dSeg = TrackGeometryHelper.distanceToSegmentMeters(nodePt, pts[i], pts[i + 1])
                    if (dSeg < dSegMin) dSegMin = dSeg
                }
                val isPassThrough = !isDownstreamEnd && !isUpstreamStart && (dSegMin <= 10.0)

                if (isDownstreamEnd || isUpstreamStart || isPassThrough || minEndDist <= 50.0) {
                    val minDist = minOf(dUpstream, minOf(dDownstream, dSegMin))
                    matches.add(
                        ExtremityMatch(
                            cableGeom = cg,
                            distToExtremity = minDist,
                            isDownstreamEnd = isDownstreamEnd,
                            isUpstreamStart = isUpstreamStart,
                            isPassThrough = isPassThrough
                        )
                    )
                }
            }

            // 1. CÂBLE ENTRÉE (Au niveau de l'extrémité d'arrivée aval)
            var bestIncomingCable: ClassifiedCable? = null
            var incomingSnapDist = 0.0
            var anomalyNote = "-"

            if (!isSro) {
                // Priority 1 : Extrémité réelle d'arrivée (dDownstreamEnd)
                val bestEndMatch = matches.filter { it.isDownstreamEnd }.minByOrNull { it.distToExtremity }
                if (bestEndMatch != null) {
                    bestIncomingCable = bestEndMatch.cableGeom.cable
                    incomingSnapDist = bestEndMatch.distToExtremity
                } else {
                    // Priority 2 : Passage par le boîtier
                    val bestPassMatch = matches.filter { it.isPassThrough }.minByOrNull { it.distToExtremity }
                    if (bestPassMatch != null) {
                        bestIncomingCable = bestPassMatch.cableGeom.cable
                        incomingSnapDist = bestPassMatch.distToExtremity
                    } else {
                        // Priority 3 (Fallback APD) : Boîtier le plus proche d'une extrémité de câble (≤ 50m)
                        val closestMatch = matches.minByOrNull { it.distToExtremity }
                        if (closestMatch != null && closestMatch.distToExtremity <= 50.0) {
                            bestIncomingCable = closestMatch.cableGeom.cable
                            incomingSnapDist = closestMatch.distToExtremity
                        }
                    }
                }

                // ANALYSE DES ANOMALIES APD (Zone de tolérance normale ≤ 10m)
                if (bestIncomingCable != null) {
                    val cleanInName = cleanCableName(bestIncomingCable.name)
                    if (incomingSnapDist > 10.0) {
                        anomalyNote = String.format(
                            Locale.ROOT,
                            "Anomalie APD : Écart de %.1fm (Extrémité du câble '%s' non arrêtée à <=10m)",
                            incomingSnapDist,
                            cleanInName
                        )
                    } else {
                        anomalyNote = String.format(Locale.ROOT, "Conforme APD (Écart %.1fm)", incomingSnapDist)
                    }
                } else {
                    anomalyNote = "Anomalie APD : Boîtier non raccordé (aucune extrémité à <=10m)"
                }
            } else {
                // SRO : Racine du réseau
                val bestEndMatch = matches.filter { it.isDownstreamEnd }.minByOrNull { it.distToExtremity }
                if (bestEndMatch != null) {
                    bestIncomingCable = bestEndMatch.cableGeom.cable
                    incomingSnapDist = bestEndMatch.distToExtremity
                }
                anomalyNote = "SRO / Origine Réseau"
            }

            // 2. CÂBLES SORTIE (Au niveau des extrémités de départ amont)
            val outgoingList = mutableListOf<ClassifiedCable>()

            for (match in matches) {
                var isOutgoing = false

                if (isSro) {
                    if (match.isUpstreamStart || match.isPassThrough || match.distToExtremity <= maxToleranceMeters) {
                        isOutgoing = true
                    }
                } else {
                    if (match.isUpstreamStart || match.isPassThrough) {
                        isOutgoing = true
                    }
                }

                if (isOutgoing) {
                    if (outgoingList.none { it.id == match.cableGeom.cable.id }) {
                        outgoingList.add(match.cableGeom.cable)
                    }
                }
            }

            // Si le câble d'entrée traverse ce boîtier, il est aussi une sortie
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
                outgoingCablesStr = outgoingStr,
                snapDistanceMeters = incomingSnapDist,
                anomalieApd = anomalyNote
            )
        }

        return resultMap
    }
}


