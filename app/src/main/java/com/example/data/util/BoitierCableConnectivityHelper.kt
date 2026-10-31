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
     * @param maxToleranceMeters Seuil de tolérance GPS pour l'accrochage aux extrémités (15m par défaut)
     */
    fun computeConnectivities(
        nodes: List<FtthNodeEntity>,
        cables: List<ClassifiedCable>,
        maxToleranceMeters: Double = 15.0
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

        val resultMap = mutableMapOf<String, BoitierConnectivity>()

        for (node in nodes) {
            val classified = KmzNodeClassifier.classifyNode(node)
            val isChambre = classified.category == KmzNodeCategory.CHAMBRE
            val isPoteau = classified.category == KmzNodeCategory.POTEAU
            val isSro = classified.category == KmzNodeCategory.SRO ||
                    node.type == FtthNodeType.SRO ||
                    node.name.startsWith("SRO", ignoreCase = true)
            val isBoitier = classified.category == KmzNodeCategory.BOITIER ||
                    node.type == FtthNodeType.BOITIER ||
                    node.name.contains("boitier", ignoreCase = true) ||
                    node.name.contains("split", ignoreCase = true) ||
                    node.name.contains("joint", ignoreCase = true) ||
                    node.name.contains("bpe", ignoreCase = true) ||
                    (node.name.contains("pbo", ignoreCase = true) && !isChambre && !isPoteau)

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

            // Analyse de la proximité de chaque câble par rapport à ce nœud (boîtier ou SRO)
            data class CableProximity(
                val cableGeom: CableGeometry,
                val dStart: Double,
                val dEnd: Double,
                val passesThrough: Boolean,
                val minDistanceToPath: Double
            )

            val proximityList = mutableListOf<CableProximity>()

            for (cg in cableGeometries) {
                val dStart = TrackGeometryHelper.calculateDistanceMeters(
                    node.latitude, node.longitude,
                    cg.startPt.latitude, cg.startPt.longitude
                )
                val dEnd = TrackGeometryHelper.calculateDistanceMeters(
                    node.latitude, node.longitude,
                    cg.endPt.latitude, cg.endPt.longitude
                )

                // Vérifier si le câble passe par ce boîtier (traverse le boîtier)
                var minDistanceToPath = Math.min(dStart, dEnd)
                var closestCumulDist = if (dStart < dEnd) 0.0 else cg.totalLength
                var curCumul = 0.0

                for (i in 0 until cg.points.size - 1) {
                    val p1 = cg.points[i]
                    val p2 = cg.points[i + 1]
                    val segLen = TrackGeometryHelper.calculateDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
                    val dSeg = TrackGeometryHelper.distanceToSegmentMeters(nodePt, p1, p2)

                    if (dSeg < minDistanceToPath) {
                        minDistanceToPath = dSeg
                        val dP1 = TrackGeometryHelper.calculateDistanceMeters(node.latitude, node.longitude, p1.latitude, p1.longitude)
                        val dP2 = TrackGeometryHelper.calculateDistanceMeters(node.latitude, node.longitude, p2.latitude, p2.longitude)
                        val proj = if (segLen > 0.001) {
                            (((dP1 * dP1) - (dP2 * dP2) + (segLen * segLen)) / (2.0 * segLen)).coerceIn(0.0, segLen)
                        } else 0.0
                        closestCumulDist = curCumul + proj
                    }
                    curCumul += segLen
                }

                // Un câble passe par le boîtier s'il s'approche à moins de maxToleranceMeters
                // ET qu'il a des sections significatives avant et après ce point
                val passesThrough = if (minDistanceToPath <= maxToleranceMeters) {
                    val bufferDist = Math.min(maxToleranceMeters, 10.0)
                    (closestCumulDist >= bufferDist) && ((cg.totalLength - closestCumulDist) >= bufferDist)
                } else false

                if (dStart <= maxToleranceMeters || dEnd <= maxToleranceMeters || passesThrough) {
                    proximityList.add(
                        CableProximity(
                            cableGeom = cg,
                            dStart = dStart,
                            dEnd = dEnd,
                            passesThrough = passesThrough,
                            minDistanceToPath = minDistanceToPath
                        )
                    )
                }
            }

            // 1. CÂBLE ENTRÉE :
            // Pour un SRO : pas de câble d'entrée (racine du réseau), sauf si un câble se termine là.
            // Pour un Boîtier : un seul câble par boîtier.
            // Priorité 1 : Le câble dont l'extrémité réelle s'arrête là (dEnd <= maxToleranceMeters).
            // Priorité 2 : Si aucun ne s'arrête là, le câble qui passe par le boîtier (passesThrough == true).
            var bestIncomingCable: ClassifiedCable? = null

            if (!isSro) {
                // Chercher d'abord un câble dont la fin s'arrête à ce boîtier
                var minEndDist = Double.MAX_VALUE
                for (prox in proximityList) {
                    if (prox.dEnd <= maxToleranceMeters && prox.dEnd < minEndDist) {
                        minEndDist = prox.dEnd
                        bestIncomingCable = prox.cableGeom.cable
                    }
                }

                // Si aucun ne s'arrête là, prendre le câble en passage le plus proche
                if (bestIncomingCable == null) {
                    var minPassDist = Double.MAX_VALUE
                    for (prox in proximityList) {
                        if (prox.passesThrough && prox.minDistanceToPath < minPassDist) {
                            minPassDist = prox.minDistanceToPath
                            bestIncomingCable = prox.cableGeom.cable
                        }
                    }
                }
            } else {
                // Pour SRO : uniquement si un câble externe se termine au SRO
                var minEndDist = Double.MAX_VALUE
                for (prox in proximityList) {
                    if (prox.dEnd <= maxToleranceMeters && prox.dEnd < minEndDist) {
                        minEndDist = prox.dEnd
                        bestIncomingCable = prox.cableGeom.cable
                    }
                }
            }

            // 2. CÂBLES SORTIE :
            // 0, 1 ou plusieurs câbles (ceux qui démarrent à ce point : dStart <= maxToleranceMeters)
            // + le câble d'entrée peut être listé dans les câbles de sortie si le même câble passe par le boîtier !
            val outgoingList = mutableListOf<ClassifiedCable>()

            for (prox in proximityList) {
                var isOutgoing = false

                // Condition A : Le câble démarre à ce point (boîtier ou SRO)
                if (prox.dStart <= maxToleranceMeters) {
                    isOutgoing = true
                }

                // Condition B : Le câble passe par le boîtier (il entre et ressort donc continue après)
                if (prox.passesThrough) {
                    isOutgoing = true
                }

                if (isOutgoing) {
                    if (outgoingList.none { it.id == prox.cableGeom.cable.id }) {
                        outgoingList.add(prox.cableGeom.cable)
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
