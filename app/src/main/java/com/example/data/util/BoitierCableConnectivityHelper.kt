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
    val anomalieApd: String = "-",
    val hostingSupportName: String = "-",
    val hostingSupportDistance: Double = 0.0
)

object BoitierCableConnectivityHelper {

    fun cleanCableName(name: String): String {
        return name.removePrefix("::").removePrefix(":").removePrefix(" :").trim()
    }

    /**
     * Détermine les câbles d'entrée et de sortie pour chaque nœud éligible aux extrémités des tronçons,
     * ainsi que le support physique d'accueil (Chambre ou Poteau à proximité immédiate <= 1m).
     *
     * RÈGLES MÉTIER FTTH / APD :
     * 1. Recherche au niveau des EXTRÉMITÉS des câbles/tronçons (startPt / endPt) : 1 m.
     * 2. Conformité APD (Accrochage nominal) : <= 1 m.
     * 3. Alerte Anomalie APD : > 2 m.
     * 4. Câble en passage dans un boîtier : <= 1 m.
     * 5. Support d'accueil (Chambre ou Poteau) : même coordonnées / décalage max <= 1.0 m.
     */
    fun computeConnectivities(
        nodes: List<FtthNodeEntity>,
        cables: List<ClassifiedCable>,
        maxToleranceMeters: Double = 1.0
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

        // Pré-identifier l'ensemble des supports physiques d'infrastructures (Chambres, Poteaux, SRO)
        data class PhysicalSupportCandidate(
            val node: FtthNodeEntity,
            val category: KmzNodeCategory,
            val typeLabel: String
        )

        val physicalSupports = mutableListOf<PhysicalSupportCandidate>()
        for (n in nodes) {
            val classified = KmzNodeClassifier.classifyNode(n)
            val isChambre = classified.category == KmzNodeCategory.CHAMBRE ||
                    n.type == FtthNodeType.CHAMBRE ||
                    n.name.startsWith("CHAMBRE", ignoreCase = true) ||
                    n.name.contains("CHAMBRE", ignoreCase = true)
            val isPoteau = classified.category == KmzNodeCategory.POTEAU ||
                    n.type == FtthNodeType.POTEAU ||
                    n.name.startsWith("POTEAU", ignoreCase = true) ||
                    n.name.contains("POTEAU", ignoreCase = true) ||
                    n.name.startsWith("POT", ignoreCase = true) ||
                    n.name.startsWith("APPUI", ignoreCase = true)
            val isSro = classified.category == KmzNodeCategory.SRO ||
                    n.type == FtthNodeType.SRO ||
                    n.name.startsWith("SRO", ignoreCase = true)

            val isNameBoitier = n.name.contains("boitier", ignoreCase = true) ||
                    n.name.contains("split", ignoreCase = true) ||
                    n.name.contains("joint", ignoreCase = true) ||
                    n.name.contains("bpe", ignoreCase = true) ||
                    n.name.contains("pbo", ignoreCase = true) ||
                    n.name.contains("pec", ignoreCase = true) ||
                    n.name.contains("bti", ignoreCase = true)
            val isBoitierNode = (classified.category == KmzNodeCategory.BOITIER || n.type == FtthNodeType.BOITIER || isNameBoitier) && !isChambre && !isPoteau

            if (!isBoitierNode) {
                if (isChambre) {
                    physicalSupports.add(PhysicalSupportCandidate(n, KmzNodeCategory.CHAMBRE, "Chambre"))
                } else if (isPoteau) {
                    physicalSupports.add(PhysicalSupportCandidate(n, KmzNodeCategory.POTEAU, "Poteau"))
                } else if (isSro) {
                    physicalSupports.add(PhysicalSupportCandidate(n, KmzNodeCategory.SRO, "SRO"))
                }
            }
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
                    anomalieApd = "-",
                    hostingSupportName = "-",
                    hostingSupportDistance = 0.0
                )
                continue
            }

            // Pour un boîtier, recherche du support d'infrastructure associé (Chambre ou Poteau à <= 1.0 m)
            var hostingSupportName = "-"
            var hostingSupportDist = 0.0

            if (isBoitier) {
                var bestSupport: PhysicalSupportCandidate? = null
                var minSupportDist = Double.MAX_VALUE

                for (supp in physicalSupports) {
                    if (supp.node.id == node.id) continue
                    val d = TrackGeometryHelper.calculateDistanceMeters(
                        node.latitude, node.longitude,
                        supp.node.latitude, supp.node.longitude
                    )
                    if (d <= 1.0 && d < minSupportDist) {
                        minSupportDist = d
                        bestSupport = supp
                    }
                }

                if (bestSupport != null) {
                    hostingSupportDist = minSupportDist
                    val cleanSuppName = bestSupport.node.name.trim()
                    hostingSupportName = if (cleanSuppName.startsWith(bestSupport.typeLabel, ignoreCase = true)) {
                        cleanSuppName
                    } else {
                        "${bestSupport.typeLabel} [$cleanSuppName]"
                    }
                }
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

                // Recherche aux EXTRÉMITÉS des tronçons (Zone nominale 1 m)
                val isDownstreamEnd = dDownstream <= 1.0 && dDownstream <= dUpstream
                val isUpstreamStart = dUpstream <= 1.0 && dUpstream <= dDownstream

                // Passage intermédiaire si aucune extrémité directe (<= 1 m)
                val minEndDist = Math.min(dUpstream, dDownstream)
                var dSegMin = minEndDist

                val pts = KmzCableClassifier.getCableAllPoints(cg.cable)
                for (i in 0 until pts.size - 1) {
                    val dSeg = TrackGeometryHelper.distanceToSegmentMeters(nodePt, pts[i], pts[i + 1])
                    if (dSeg < dSegMin) dSegMin = dSeg
                }
                val isPassThrough = !isDownstreamEnd && !isUpstreamStart && (dSegMin <= 1.0)

                if (isDownstreamEnd || isUpstreamStart || isPassThrough || minEndDist <= 10.0) {
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
                // Priority 1 : Extrémité réelle d'arrivée (dDownstreamEnd <= 1m)
                val bestEndMatch = matches.filter { it.isDownstreamEnd }.minByOrNull { it.distToExtremity }
                if (bestEndMatch != null) {
                    bestIncomingCable = bestEndMatch.cableGeom.cable
                    incomingSnapDist = bestEndMatch.distToExtremity
                } else {
                    // Priority 2 : Passage par le boîtier (<= 1m)
                    val bestPassMatch = matches.filter { it.isPassThrough }.minByOrNull { it.distToExtremity }
                    if (bestPassMatch != null) {
                        bestIncomingCable = bestPassMatch.cableGeom.cable
                        incomingSnapDist = bestPassMatch.distToExtremity
                    } else {
                        // Priority 3 (Fallback APD) : Boîtier le plus proche d'une extrémité de câble
                        val closestMatch = matches.minByOrNull { it.distToExtremity }
                        if (closestMatch != null && closestMatch.distToExtremity <= 10.0) {
                            bestIncomingCable = closestMatch.cableGeom.cable
                            incomingSnapDist = closestMatch.distToExtremity
                        }
                    }
                }

                // ANALYSE DES ANOMALIES APD :
                // - Conformité APD : <= 1 m
                // - Alerte Anomalie APD : > 2 m
                if (bestIncomingCable != null) {
                    val cleanInName = cleanCableName(bestIncomingCable.name)
                    if (incomingSnapDist > 2.0) {
                        anomalyNote = String.format(
                            Locale.ROOT,
                            "Alerte Anomalie APD : Écart de %.1fm (Extrémité du câble '%s' non arrêtée à <=1m)",
                            incomingSnapDist,
                            cleanInName
                        )
                    } else if (incomingSnapDist > 1.0) {
                        anomalyNote = String.format(
                            Locale.ROOT,
                            "Anomalie APD : Écart de %.1fm (Extrémité câble '%s' > 1m)",
                            incomingSnapDist,
                            cleanInName
                        )
                    } else {
                        anomalyNote = String.format(Locale.ROOT, "Conforme APD (Écart %.1fm)", incomingSnapDist)
                    }
                } else {
                    anomalyNote = "Alerte Anomalie APD : Boîtier non raccordé (aucune extrémité à <=1m)"
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
                    if ((match.isUpstreamStart || match.distToExtremity <= maxToleranceMeters) && (match.cableGeom.cable.isParent || match.cableGeom.cable.hierarchyLevel == 0)) {
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
                anomalieApd = anomalyNote,
                hostingSupportName = hostingSupportName,
                hostingSupportDistance = hostingSupportDist
            )
        }

        return resultMap
    }
}


