package com.example.data.apd

import com.example.data.util.TrackGeometryHelper
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Moteur d'Analyse Topologique et de Calcul Spécialisé FTTH APD
 * Porté fidèlement du script Python Processing QGIS APD.
 */
object FtthApdTopologyEngine {

    // --- 1. Géométrie et Index Spatial ---

    fun haversineM(lon1: Double, lat1: Double, lon2: Double, lat2: Double): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dphi = Math.toRadians(lat2 - lat1)
        val dlambda = Math.toRadians(lon2 - lon1)
        val a = sin(dphi / 2.0) * sin(dphi / 2.0) + cos(p1) * cos(p2) * sin(dlambda / 2.0) * sin(dlambda / 2.0)
        return 2.0 * r * asin(sqrt(a))
    }

    fun coordsClose(c1: Pair<Double, Double>, c2: Pair<Double, Double>, toleranceM: Double): Boolean {
        return haversineM(c1.first, c1.second, c2.first, c2.second) <= toleranceM
    }

    class SpatialIndex(
        val lons: DoubleArray,
        val lats: DoubleArray,
        refLat: Double? = null
    ) {
        val mPerDegLat = 111320.0
        val mPerDegLon: Double
        val x: DoubleArray
        val y: DoubleArray

        init {
            val meanLat = refLat ?: if (lats.isNotEmpty()) lats.average() else 0.0
            mPerDegLon = 111320.0 * cos(Math.toRadians(meanLat))
            x = DoubleArray(lons.size) { lons[it] * mPerDegLon }
            y = DoubleArray(lats.size) { lats[it] * mPerDegLat }
        }

        fun queryBallPoint(lon: Double, lat: Double, radiusM: Double): List<Int> {
            if (x.isEmpty()) return emptyList()
            val qx = lon * mPerDegLon
            val qy = lat * mPerDegLat
            val result = mutableListOf<Int>()
            for (i in x.indices) {
                val dist = hypot(x[i] - qx, y[i] - qy)
                if (dist <= radiusM) {
                    result.add(i)
                }
            }
            return result
        }
    }

    // --- 2. Classification et Déduplication ---

    fun classifyPoints(rawPoints: List<ApdRawPlacemark>): List<ApdPoint> {
        return rawPoints.mapIndexed { idx, raw ->
            val lonLat = FtthApdParser.parseLonLat(raw.coordinatesStr) ?: Pair(0.0, 0.0)
            val objType = FtthApdParser.classifyPointType(raw)
            val name = raw.attributes["NOM"]?.ifBlank { raw.name } ?: raw.name.ifBlank { "PT-$idx" }

            ApdPoint(
                id = idx,
                name = name,
                folder = raw.folder,
                longitude = lonLat.first,
                latitude = lonLat.second,
                objectType = objType,
                typeFonc = raw.attributes["TYPE_FONC"] ?: "",
                typeStruc = raw.attributes["TYPE_STRUC"] ?: "",
                attributes = raw.attributes
            )
        }
    }

    fun dedupeExactChambrePoteau(points: List<ApdPoint>, toleranceM: Double = 0.5): List<ApdPoint> {
        val keep = BooleanArray(points.size) { true }
        val targetTypes = setOf(ApdObjectType.CHAMBRE, ApdObjectType.POTEAU, ApdObjectType.SRO)

        for (t in targetTypes) {
            val subIndices = points.indices.filter { points[it].objectType == t }
            val visited = mutableSetOf<Int>()

            for (i in subIndices.indices) {
                val idxI = subIndices[i]
                if (visited.contains(idxI)) continue

                for (j in (i + 1) until subIndices.size) {
                    val idxJ = subIndices[j]
                    if (visited.contains(idxJ)) continue

                    val d = haversineM(
                        points[idxI].longitude, points[idxI].latitude,
                        points[idxJ].longitude, points[idxJ].latitude
                    )
                    if (d <= toleranceM) {
                        keep[idxJ] = false
                        visited.add(idxJ)
                    }
                }
            }
        }

        return points.filterIndexed { idx, _ -> keep[idx] }
    }

    fun dedupeBpePboTolerance(bpePboList: List<ApdPoint>, toleranceM: Double = 0.5): List<ApdPoint> {
        val keep = BooleanArray(bpePboList.size) { true }
        val groupedByName = bpePboList.indices.groupBy { bpePboList[it].name }

        for ((_, indices) in groupedByName) {
            val visited = mutableSetOf<Int>()
            for (i in indices.indices) {
                val idxI = indices[i]
                if (visited.contains(idxI)) continue

                for (j in (i + 1) until indices.size) {
                    val idxJ = indices[j]
                    if (visited.contains(idxJ)) continue

                    val d = haversineM(
                        bpePboList[idxI].longitude, bpePboList[idxI].latitude,
                        bpePboList[idxJ].longitude, bpePboList[idxJ].latitude
                    )
                    if (d <= toleranceM) {
                        keep[idxJ] = false
                        visited.add(idxJ)
                    }
                }
            }
        }

        return bpePboList.filterIndexed { idx, _ -> keep[idx] }
    }

    // --- 3. Rattachement Câbles <-> BPE/PBO ---

    fun parseLineCoords(coordsStr: String): List<Pair<Double, Double>> {
        val pts = coordsStr.trim().split(Regex("\\s+"))
        return pts.mapNotNull { ptStr ->
            val parts = ptStr.split(",")
            if (parts.size >= 2) {
                val lon = parts[0].toDoubleOrNull()
                val lat = parts[1].toDoubleOrNull()
                if (lon != null && lat != null) Pair(lon, lat) else null
            } else null
        }
    }

    fun addCableEntreeSortie(
        points: List<ApdPoint>,
        rawLines: List<ApdRawPlacemark>,
        toleranceM: Double = 1.0
    ): List<ApdPoint> {
        val cables = rawLines.mapNotNull { raw ->
            val nom = raw.attributes["NOM"]?.ifBlank { raw.name } ?: raw.name
            val pts = parseLineCoords(raw.coordinatesStr)
            if (nom.isNotBlank() && pts.size >= 2) {
                Triple(nom, pts.first(), pts.last())
            } else null
        }

        if (points.isEmpty()) return points

        val refLat = points.map { it.latitude }.average()
        val originLons = DoubleArray(cables.size) { cables[it].second.first }
        val originLats = DoubleArray(cables.size) { cables[it].second.second }
        val endLons = DoubleArray(cables.size) { cables[it].third.first }
        val endLats = DoubleArray(cables.size) { cables[it].third.second }

        val originIdx = SpatialIndex(originLons, originLats, refLat)
        val endIdx = SpatialIndex(endLons, endLats, refLat)

        val updatedPoints = points.map { pt ->
            val ptCopy = pt.copy()
            val isJointType = pt.objectType in setOf(ApdObjectType.BPE, ApdObjectType.PBO, ApdObjectType.BPE_PBO, ApdObjectType.SRO)

            if (isJointType) {
                val sortieMatches = originIdx.queryBallPoint(pt.longitude, pt.latitude, toleranceM)
                    .map { cables[it].first }
                    .toSortedSet()

                ptCopy.cablesSortie = sortieMatches.joinToString("; ")
                ptCopy.nbCablesSorties = sortieMatches.size

                if (pt.objectType != ApdObjectType.SRO) {
                    val entreMatches = endIdx.queryBallPoint(pt.longitude, pt.latitude, toleranceM)
                        .map { cables[it].first }
                        .toSortedSet()
                    ptCopy.cableEntre = entreMatches.joinToString("; ")
                } else {
                    ptCopy.cableEntre = ""
                }
            } else {
                ptCopy.cableEntre = ""
                ptCopy.cablesSortie = ""
                ptCopy.nbCablesSorties = 0
            }
            ptCopy
        }

        return updatedPoints
    }

    fun addSupportColumn(
        bpePboList: List<ApdPoint>,
        supportList: List<ApdPoint>,
        toleranceM: Double = 1.0
    ): List<ApdPoint> {
        if (supportList.isEmpty()) return bpePboList

        val refLat = supportList.map { it.latitude }.average()
        val supLons = DoubleArray(supportList.size) { supportList[it].longitude }
        val supLats = DoubleArray(supportList.size) { supportList[it].latitude }
        val supIndex = SpatialIndex(supLons, supLats, refLat)

        return bpePboList.map { bpe ->
            val matches = supIndex.queryBallPoint(bpe.longitude, bpe.latitude, toleranceM)
            val namesFound = mutableListOf<String>()
            val typesFound = mutableListOf<String>()

            for (m in matches) {
                val sup = supportList[m]
                namesFound.add(sup.name)
                typesFound.add(
                    when (sup.objectType) {
                        ApdObjectType.POTEAU -> "Aerien"
                        ApdObjectType.SRO -> "SRO"
                        else -> "GC"
                    }
                )
            }

            bpe.copy(
                supportName = namesFound.joinToString("; "),
                supportType = typesFound.joinToString("; ")
            )
        }
    }

    // --- 4. Fusion des Câbles ---

    fun cableTrueOrigin(segments: List<List<Pair<Double, Double>>>, toleranceM: Double): Pair<Double, Double> {
        if (segments.isEmpty()) return Pair(0.0, 0.0)
        val lastCoords = segments.map { it.last() }
        val candidates = mutableListOf<Pair<Double, Double>>()

        for (seg in segments) {
            val first = seg.first()
            val isTargetOfAnother = lastCoords.any { lc -> lc != first && coordsClose(first, lc, toleranceM) }
            if (!isTargetOfAnother) {
                candidates.add(first)
            }
        }
        return candidates.firstOrNull() ?: segments.first().first()
    }

    fun mergeCables(
        rawLines: List<ApdRawPlacemark>,
        supportList: List<ApdPoint>,
        toleranceM: Double = 1.0
    ): List<ApdCable> {
        val refLat = if (supportList.isNotEmpty()) supportList.map { it.latitude }.average() else 0.0
        val supLons = DoubleArray(supportList.size) { supportList[it].longitude }
        val supLats = DoubleArray(supportList.size) { supportList[it].latitude }
        val supIndex = SpatialIndex(supLons, supLats, refLat)

        val cablesByNom = mutableMapOf<String, MutableList<ApdRawPlacemark>>()
        for (raw in rawLines) {
            val nom = raw.attributes["NOM"]?.ifBlank { raw.name } ?: raw.name
            if (nom.isNotBlank()) {
                cablesByNom.getOrPut(nom) { mutableListOf() }.add(raw)
            }
        }

        val resultCables = mutableListOf<ApdCable>()

        for ((nom, group) in cablesByNom) {
            val segments = group.map { parseLineCoords(it.coordinatesStr) }.filter { it.size >= 2 }
            if (segments.isEmpty()) continue

            val trueOrigin = cableTrueOrigin(segments, toleranceM)

            // Trouver l'origine support
            val originMatches = supIndex.queryBallPoint(trueOrigin.first, trueOrigin.second, toleranceM)
            val originSupportNames = originMatches.map { supportList[it].name }.toSortedSet().joinToString("; ")

            // Trouver les supports traversés et calculer la longueur
            val allVertices = segments.flatten()
            val traversedChambres = mutableSetOf<String>()
            val traversedPoteaux = mutableSetOf<String>()

            for (v in allVertices) {
                if (coordsClose(v, trueOrigin, toleranceM)) continue
                val matches = supIndex.queryBallPoint(v.first, v.second, toleranceM)
                for (m in matches) {
                    val sup = supportList[m]
                    if (sup.objectType == ApdObjectType.CHAMBRE) {
                        traversedChambres.add(sup.name)
                    } else if (sup.objectType == ApdObjectType.POTEAU) {
                        traversedPoteaux.add(sup.name)
                    }
                }
            }

            var totalLen = 0.0
            for (seg in segments) {
                for (i in 0 until (seg.size - 1)) {
                    totalLen += haversineM(seg[i].first, seg[i].second, seg[i + 1].first, seg[i + 1].second)
                }
            }

            val nbCh = traversedChambres.size
            val nbPot = traversedPoteaux.size
            val logTotal = totalLen + nbCh * 30.0 + nbPot * 10.0
            val typeStruc = group.first().attributes["TYPE_STRUC"] ?: ""

            val allTraversed = (traversedChambres + traversedPoteaux).toSortedSet().joinToString("; ")

            resultCables.add(
                ApdCable(
                    nom = nom,
                    typeStruc = typeStruc,
                    originName = originSupportNames,
                    supportsTraverses = allTraversed,
                    nbChTraversees = nbCh,
                    nbPotTraversees = nbPot,
                    longueurMeters = (totalLen * 100.0).toInt() / 100.0,
                    logTotalMeters = (logTotal * 100.0).toInt() / 100.0,
                    segments = segments
                )
            )
        }

        return resultCables
    }

    // --- 5. Construction de la Hiérarchie et Analyse Topologique ---

    fun findNearestPoint(lon: Double, lat: Double, points: List<ApdPoint>): Pair<Int?, Double> {
        var bestIdx: Int? = null
        var bestDist = Double.MAX_VALUE
        for (i in points.indices) {
            val d = haversineM(lon, lat, points[i].longitude, points[i].latitude)
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        return Pair(bestIdx, bestDist)
    }

    fun computeJointPassingCables(
        rawLines: List<ApdRawPlacemark>,
        bpePboList: List<ApdPoint>,
        toleranceM: Double = 1.0
    ): Map<Int, Set<String>> {
        if (bpePboList.isEmpty()) return emptyMap()

        val refLat = bpePboList.map { it.latitude }.average()
        val bpeLons = DoubleArray(bpePboList.size) { bpePboList[it].longitude }
        val bpeLats = DoubleArray(bpePboList.size) { bpePboList[it].latitude }
        val bpeIndex = SpatialIndex(bpeLons, bpeLats, refLat)

        val result = mutableMapOf<Int, MutableSet<String>>()
        val cablesByNom = rawLines.groupBy { it.attributes["NOM"]?.ifBlank { it.name } ?: it.name }

        for ((nom, group) in cablesByNom) {
            if (nom.isBlank()) continue
            val segments = group.map { parseLineCoords(it.coordinatesStr) }.filter { it.size >= 2 }
            val chain = orderSegments(segments, toleranceM)

            for (pt in chain) {
                val matches = bpeIndex.queryBallPoint(pt.first, pt.second, toleranceM)
                for (m in matches) {
                    val bpeIdx = bpePboList[m].id
                    result.getOrPut(bpeIdx) { mutableSetOf() }.add(nom)
                }
            }
        }

        return result
    }

    fun orderSegments(segments: List<List<Pair<Double, Double>>>, tolM: Double): List<Pair<Double, Double>> {
        if (segments.isEmpty()) return emptyList()
        val lastCoords = segments.map { it.last() }
        var startIdx = 0

        for (i in segments.indices) {
            val seg = segments[i]
            val isEnd = lastCoords.indices.any { j -> j != i && coordsClose(seg.first(), lastCoords[j], tolM) }
            if (!isEnd) {
                startIdx = i
                break
            }
        }

        val used = mutableSetOf(startIdx)
        val chain = segments[startIdx].toMutableList()
        var changed = true

        while (changed) {
            changed = false
            val currentEnd = chain.last()
            for (i in segments.indices) {
                if (used.contains(i)) continue
                val seg = segments[i]
                if (coordsClose(seg.first(), currentEnd, tolM)) {
                    chain.addAll(seg.subList(1, seg.size))
                    used.add(i)
                    changed = true
                    break
                } else if (coordsClose(seg.last(), currentEnd, tolM)) {
                    chain.addAll(seg.reversed().subList(1, seg.size))
                    used.add(i)
                    changed = true
                    break
                }
            }
        }

        for (i in segments.indices) {
            if (!used.contains(i)) {
                chain.addAll(segments[i])
            }
        }

        return chain
    }

    fun buildCableHierarchyGeometric(
        rawLines: List<ApdRawPlacemark>,
        bpePboList: List<ApdPoint>,
        toleranceM: Double = 1.0,
        warnThresholdM: Double = 2.0,
        jointPassingCables: Map<Int, Set<String>>
    ): ApdHierarchyResult {
        val cablesByNom = rawLines.groupBy { it.attributes["NOM"]?.ifBlank { it.name } ?: it.name }

        val childrenOf = mutableMapOf<String, MutableList<String>>()
        val roots = mutableListOf<String>()
        val attachOf = mutableMapOf<String, Int?>()
        val suspects = mutableListOf<ApdAnomaly>()

        val bpePboMap = bpePboList.associateBy { it.id }

        for ((nom, group) in cablesByNom) {
            if (nom.isBlank()) continue
            val segments = group.map { parseLineCoords(it.coordinatesStr) }.filter { it.size >= 2 }
            if (segments.isEmpty()) continue

            val trueOrigin = cableTrueOrigin(segments, toleranceM)
            var (nearestIdx, dist) = findNearestPoint(trueOrigin.first, trueOrigin.second, bpePboList)

            if (dist > warnThresholdM) {
                val bpeOnlyList = bpePboList.filter { it.objectType in setOf(ApdObjectType.SRO, ApdObjectType.BPE, ApdObjectType.BPE_PBO) }
                val (bpeIdx, bpeDist) = findNearestPoint(trueOrigin.first, trueOrigin.second, bpeOnlyList)
                if (bpeIdx != null) {
                    nearestIdx = bpeIdx
                    dist = bpeDist
                }
            }

            attachOf[nom] = nearestIdx

            if (nearestIdx == null) {
                roots.add(nom)
                continue
            }

            val nearestPoint = bpePboList[nearestIdx]

            if (dist > warnThresholdM) {
                val jointLabel = nearestPoint.supportName.ifBlank { nearestPoint.name }
                suspects.add(ApdAnomaly("Distance excessive", nom, (dist * 100.0).toInt() / 100.0, "Joint le plus proche : $jointLabel"))
            }

            if (nearestPoint.objectType == ApdObjectType.SRO) {
                roots.add(nom)
                continue
            }

            var parentNom = nearestPoint.cableEntre.split("; ").firstOrNull { it.isNotBlank() } ?: ""

            if (parentNom.isBlank()) {
                val candidates = (jointPassingCables[nearestPoint.id] ?: emptySet()) - setOf(nom)
                parentNom = candidates.sorted().firstOrNull() ?: ""
            }

            if (parentNom.isNotBlank()) {
                childrenOf.getOrPut(parentNom) { mutableListOf() }.add(nom)
            } else {
                roots.add(nom)
                val jointLabel = nearestPoint.supportName.ifBlank { nearestPoint.name }
                suspects.add(ApdAnomaly("Aucun câble parent trouvé", nom, (dist * 100.0).toInt() / 100.0, "Joint le plus proche : $jointLabel"))
            }
        }

        return ApdHierarchyResult(childrenOf, roots, attachOf, suspects)
    }

    fun findPboTypeAnomalies(bpePboList: List<ApdPoint>): List<ApdAnomaly> {
        val anomalies = mutableListOf<ApdAnomaly>()
        for (pt in bpePboList) {
            if (pt.objectType == ApdObjectType.PBO && pt.nbCablesSorties >= 2) {
                val jointLabel = pt.supportName.ifBlank { pt.name }
                anomalies.add(
                    ApdAnomaly(
                        type = "Incohérence de type (PBO)",
                        targetName = jointLabel,
                        detail = "PBO avec ${pt.nbCablesSorties} câbles sortie (devrait être BPE/BPE-PBO)"
                    )
                )
            }
        }
        return anomalies
    }

    fun computeCableChains(
        rawLines: List<ApdRawPlacemark>,
        bpePboList: List<ApdPoint>,
        supportList: List<ApdPoint>,
        toleranceM: Double = 1.0,
        extraJointsByCable: Map<String, Set<Int>> = emptyMap()
    ): Map<String, ApdCableChain> {
        val bpeNonSro = bpePboList.filter { it.objectType != ApdObjectType.SRO }
        if (bpeNonSro.isEmpty()) return emptyMap()

        val refLat = bpeNonSro.map { it.latitude }.average()
        val bLons = DoubleArray(bpeNonSro.size) { bpeNonSro[it].longitude }
        val bLats = DoubleArray(bpeNonSro.size) { bpeNonSro[it].latitude }
        val bIndex = SpatialIndex(bLons, bLats, refLat)

        val supRefLat = if (supportList.isNotEmpty()) supportList.map { it.latitude }.average() else 0.0
        val supLons = DoubleArray(supportList.size) { supportList[it].longitude }
        val supLats = DoubleArray(supportList.size) { supportList[it].latitude }
        val supIndex = SpatialIndex(supLons, supLats, supRefLat)

        val result = mutableMapOf<String, ApdCableChain>()
        val cablesByNom = rawLines.groupBy { it.attributes["NOM"]?.ifBlank { it.name } ?: it.name }

        for ((nom, group) in cablesByNom) {
            if (nom.isBlank()) continue
            val segments = group.map { parseLineCoords(it.coordinatesStr) }.filter { it.size >= 2 }
            val chain = orderSegments(segments, toleranceM)

            val myExtraJoints = extraJointsByCable[nom] ?: emptySet()
            val joints = mutableListOf<Int>()
            val segSupports = mutableListOf<Set<String>>()
            val segLengths = mutableListOf<Double>()

            var currentSup = mutableSetOf<String>()
            var currentLen = 0.0
            var prevVertex: Pair<Double, Double>? = null

            for (pt in chain) {
                if (prevVertex != null) {
                    currentLen += haversineM(prevVertex.first, prevVertex.second, pt.first, pt.second)
                }
                prevVertex = pt

                val supMatches = supIndex.queryBallPoint(pt.first, pt.second, toleranceM)
                for (m in supMatches) {
                    val sup = supportList[m]
                    if (sup.objectType == ApdObjectType.CHAMBRE || sup.objectType == ApdObjectType.POTEAU) {
                        currentSup.add(sup.name)
                    }
                }

                val bpeMatches = bIndex.queryBallPoint(pt.first, pt.second, toleranceM)
                for (m in bpeMatches) {
                    val bpePt = bpeNonSro[m]
                    val idx = bpePt.id
                    val sortieNames = bpePt.cablesSortie.split("; ").filter { it.isNotBlank() }

                    if (bpePt.cableEntre != nom && !sortieNames.contains(nom) && !myExtraJoints.contains(idx)) {
                        continue
                    }

                    if (joints.isEmpty() || joints.last() != idx) {
                        joints.add(idx)
                        segSupports.add(currentSup.toSortedSet())
                        segLengths.add((currentLen * 100.0).toInt() / 100.0)
                        currentSup = mutableSetOf()
                        currentLen = 0.0
                    }
                }
            }

            result[nom] = ApdCableChain(joints, segSupports, segLengths)
        }

        return result
    }

    fun resolveAttachPositions(
        childrenOf: Map<String, List<String>>,
        chains: Map<String, ApdCableChain>,
        attachOf: Map<String, Int?>
    ): Map<String, Map<Int, List<String>>> {
        val resolved = mutableMapOf<String, Map<Int, List<String>>>()

        for ((parent, kids) in childrenOf) {
            val joints = chains[parent]?.joints ?: emptyList()
            val groups = mutableMapOf<Int, MutableList<String>>()

            for (k in kids) {
                val jIdx = attachOf[k]
                val pos = if (joints.isNotEmpty() && jIdx in joints) {
                    joints.indexOf(jIdx)
                } else {
                    if (joints.isNotEmpty()) joints.size - 1 else 0
                }
                groups.getOrPut(pos) { mutableListOf() }.add(k)
            }
            resolved[parent] = groups
        }

        return resolved
    }

    fun reorderChildrenByPosition(
        childrenOf: MutableMap<String, MutableList<String>>,
        resolvedGroups: Map<String, Map<Int, List<String>>>
    ) {
        for ((nom, kids) in childrenOf) {
            val groups = resolvedGroups[nom] ?: emptyMap()
            val posOf = mutableMapOf<String, Int>()
            for ((pos, klist) in groups) {
                for (k in klist) {
                    posOf[k] = pos
                }
            }
            kids.sortByDescending { posOf[it] ?: 0 }
        }
    }

    fun layoutRows(childrenOf: Map<String, List<String>>, roots: List<String>): Map<String, Int> {
        val rowPos = mutableMapOf<String, Int>()
        val visited = mutableSetOf<String>()
        var nextRow = 0

        fun assign(nom: String) {
            if (!visited.add(nom)) return
            rowPos[nom] = nextRow
            nextRow++
            val kids = childrenOf[nom] ?: emptyList()
            for (k in kids) {
                assign(k)
            }
        }

        for (r in roots) {
            assign(r)
        }

        return rowPos
    }

    fun isAdductionCable(raw: ApdRawPlacemark): Boolean {
        val folderLower = raw.folder.lowercase(Locale.ROOT)
        val nameLower = raw.name.lowercase(Locale.ROOT)
        val strucLower = raw.attributes["TYPE_STRUC"]?.lowercase(Locale.ROOT) ?: ""
        val foncLower = raw.attributes["TYPE_FONC"]?.lowercase(Locale.ROOT) ?: ""
        val dossierLower = raw.attributes["DOSSIER"]?.lowercase(Locale.ROOT) ?: ""

        return folderLower.contains("adduction") ||
               nameLower.contains("adduction") ||
               strucLower.contains("adduction") ||
               foncLower.contains("adduction") ||
               dossierLower.contains("adduction")
    }

    /**
     * Exécute le pipeline topologique complet FTTH APD
     */
    fun processApdKmz(
        rawPoints: List<ApdRawPlacemark>,
        rawLines: List<ApdRawPlacemark>,
        toleranceM: Double = 1.0,
        warnThresholdM: Double = 2.0
    ): ApdAnalysisResult {
        return try {
            // 0. Filtrage des câbles d'adduction (dossier/type adduction à éliminer du synoptique)
            val cleanLines = rawLines.filterNot { isAdductionCable(it) }

            // 1. Classification & Déduplication des points
            val classifiedPoints = classifyPoints(rawPoints)
            val dedupedPoints = dedupeExactChambrePoteau(classifiedPoints, toleranceM = 0.5)

            val supportsList = dedupedPoints.filter { it.objectType in setOf(ApdObjectType.CHAMBRE, ApdObjectType.POTEAU) }
            var bpePboList = dedupedPoints.filter { it.objectType in setOf(ApdObjectType.BPE, ApdObjectType.PBO, ApdObjectType.BPE_PBO, ApdObjectType.SRO) }

            // 2. Rattachement Câbles <-> BPE/PBO
            bpePboList = addCableEntreeSortie(bpePboList, cleanLines, toleranceM = toleranceM)
            bpePboList = dedupeBpePboTolerance(bpePboList, toleranceM = 0.5)
            bpePboList = addSupportColumn(bpePboList, supportsList, toleranceM = toleranceM)

            // 3. Fusion des câbles
            val supportsForCableOrigin = dedupedPoints.filter { it.objectType in setOf(ApdObjectType.CHAMBRE, ApdObjectType.POTEAU, ApdObjectType.SRO) }
            val cablesList = mergeCables(cleanLines, supportsForCableOrigin, toleranceM = toleranceM)

            // 4. Passage géométrique & Hiérarchie
            val jointPassingCables = computeJointPassingCables(cleanLines, bpePboList, toleranceM = toleranceM)
            val hierarchy = buildCableHierarchyGeometric(cleanLines, bpePboList, toleranceM = toleranceM, warnThresholdM = warnThresholdM, jointPassingCables = jointPassingCables)

            val mutableChildrenOf = hierarchy.childrenOf.mapValues { it.value.toMutableList() }.toMutableMap()
            val extraJointsByCable = mutableMapOf<String, Set<Int>>()
            for ((parent, kids) in mutableChildrenOf) {
                val pts = kids.mapNotNull { hierarchy.attachOf[it] }.toSet()
                if (pts.isNotEmpty()) {
                    extraJointsByCable[parent] = pts
                }
            }

            // 5. Chaînes de câbles et rangement
            val chains = computeCableChains(cleanLines, bpePboList, supportsList, toleranceM = toleranceM, extraJointsByCable = extraJointsByCable)
            val resolvedGroups = resolveAttachPositions(mutableChildrenOf, chains, hierarchy.attachOf)
            reorderChildrenByPosition(mutableChildrenOf, resolvedGroups)

            // 6. Anomalies
            val pboAnomalies = findPboTypeAnomalies(bpePboList)
            val allAnomalies = hierarchy.suspects + pboAnomalies

            ApdAnalysisResult(
                allPoints = dedupedPoints,
                bpePboList = bpePboList,
                supportsList = supportsList,
                cablesList = cablesList,
                anomalies = allAnomalies,
                roots = hierarchy.roots,
                childrenOf = mutableChildrenOf,
                attachOf = hierarchy.attachOf,
                chains = chains,
                resolvedGroups = resolvedGroups
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ApdAnalysisResult(
                allPoints = emptyList(),
                bpePboList = emptyList(),
                supportsList = emptyList(),
                cablesList = emptyList(),
                anomalies = listOf(ApdAnomaly("Erreur Pipeline", "Génération", 0.0, e.localizedMessage ?: e.javaClass.simpleName)),
                roots = emptyList(),
                childrenOf = emptyMap(),
                attachOf = emptyMap(),
                chains = emptyMap(),
                resolvedGroups = emptyMap()
            )
        }
    }

    data class ApdHierarchyResult(
        val childrenOf: Map<String, List<String>>,
        val roots: List<String>,
        val attachOf: Map<String, Int?>,
        val suspects: List<ApdAnomaly>
    )
}
