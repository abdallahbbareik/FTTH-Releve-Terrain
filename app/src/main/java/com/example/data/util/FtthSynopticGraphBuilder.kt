package com.example.data.util

import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import java.util.Locale

data class SynopticNode(
    val id: String,
    val name: String,
    val category: KmzNodeCategory,
    val level: Int,                // Indice horizontal (colonne le long de la ligne câble)
    val row: Int,                  // Indice vertical (ligne du câble dans le synoptique)
    val x: Float,                  // Position X calculée
    val y: Float,                  // Position Y calculée
    val incomingCableName: String,
    val outgoingCablesStr: String,
    val rawNode: FtthNodeEntity,
    val isSro: Boolean,
    val isBoitier: Boolean,
    val hostingSupportName: String = "-",
    val nodeTypeBadge: String = "BPE", // "SRO", "BPE", "PBO"
    val cumulativeDistanceMeters: Double = 0.0
)

data class SynopticLink(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val cableName: String,
    val capacityFO: String,
    val cable: ClassifiedCable?,
    val supportsTraversesStr: String = "-",
    val supportsCount: Int = 0,
    val installationType: String = "Conduite",
    val segmentLengthMeters: Double = 0.0
)

data class SynopticGraph(
    val nodes: List<SynopticNode>,
    val links: List<SynopticLink>,
    val maxLevel: Int,
    val totalWidth: Float,
    val totalHeight: Float
)

object FtthSynopticGraphBuilder {

    /**
     * Algorithme Synoptique Excel par Parcours en Profondeur (DFS) :
     * - Lignes : Chaque câble reçoit sa propre ligne attribuée par parcours en profondeur (DFS).
     *   Un parent est toujours au-dessus de ses descendants (aucune collision/chevauchement).
     * - Colonnes : Calculées dynamiquement. Un câble se dessine en chaîne horizontale, un joint après l'autre.
     * - Enfants : Partent du joint d'origine où ils sont rattachés. Le joint de départ n'est pas redessiné.
     */
    fun buildGraph(
        nodes: List<FtthNodeEntity>,
        cables: List<ClassifiedCable>,
        connectivities: Map<String, BoitierConnectivity>
    ): SynopticGraph {
        if (nodes.isEmpty() || cables.isEmpty()) {
            return SynopticGraph(emptyList(), emptyList(), 0, 800f, 600f)
        }

        val classifiedNodes = nodes.map { KmzNodeClassifier.classifyNode(it) }

        // Nœuds éligibles (SRO et Boîtiers)
        val eligibleNodes = classifiedNodes.filter { cn ->
            val conn = connectivities[cn.id]
            val isSro = cn.category == KmzNodeCategory.SRO || (conn?.isSro == true)
            val isBoitier = cn.category == KmzNodeCategory.BOITIER || (conn?.isBoitier == true)
            isSro || isBoitier
        }

        if (eligibleNodes.isEmpty()) {
            return SynopticGraph(emptyList(), emptyList(), 0, 800f, 600f)
        }

        val eligibleNodeMap = eligibleNodes.associateBy { it.id }

        // Grille de placement (col, row) pour chaque ID de nœud
        val nodeGridPos = mutableMapOf<String, Pair<Int, Int>>()
        val nodeCumulDist = mutableMapOf<String, Double>()
        val linkMap = mutableListOf<SynopticLink>()

        // Map des nœuds vers les câbles enfants rattachés
        val cableNodeSequenceMap = mutableMapOf<String, List<Pair<ClassifiedKmzNode, Double>>>()

        fun getNodesAndDistancesAlongCable(cable: ClassifiedCable): List<Pair<ClassifiedKmzNode, Double>> {
            if (cableNodeSequenceMap.containsKey(cable.id)) {
                return cableNodeSequenceMap[cable.id]!!
            }
            val analysis = CableSupportAnalyzer.analyzeClassifiedCableSupports(cable, nodes)
            val result = mutableListOf<Pair<ClassifiedKmzNode, Double>>()

            for (supp in analysis.supports) {
                val dist = supp.cumulativeDistanceMeters
                if (supp.isSro || supp.node.type == FtthNodeType.SRO) {
                    val cn = eligibleNodeMap[supp.node.id]
                    if (cn != null && result.none { it.first.id == cn.id }) {
                        result.add(Pair(cn, dist))
                    }
                }
                for (b in supp.hostedBoitiers) {
                    val cn = eligibleNodeMap[b.id]
                    if (cn != null && result.none { it.first.id == cn.id }) {
                        result.add(Pair(cn, dist))
                    }
                }
                if (supp.isBoitier || supp.node.type == FtthNodeType.BOITIER) {
                    val cn = eligibleNodeMap[supp.node.id]
                    if (cn != null && result.none { it.first.id == cn.id }) {
                        result.add(Pair(cn, dist))
                    }
                }
            }
            cableNodeSequenceMap[cable.id] = result
            return result
        }

        // Construire la hiérarchie d'arbre de câbles : parentCable -> childCables
        val childCablesMap = mutableMapOf<String, MutableList<ClassifiedCable>>()

        // Câble racine principal (Level 0 / Parent starting at SRO)
        val rootCable = cables.firstOrNull { it.isParent || it.hierarchyLevel == 0 }
            ?: cables.maxByOrNull { c -> c.capacityFO.filter { it.isDigit() }.toIntOrNull() ?: 0 }
            ?: cables.first()

        for (c in cables) {
            if (c.id == rootCable.id) continue
            val parentName = c.parentCableName
            val parent = cables.firstOrNull { it.name.contains(parentName, ignoreCase = true) || parentName.contains(it.name, ignoreCase = true) }
                ?: rootCable

            childCablesMap.getOrPut(parent.id) { mutableListOf() }.add(c)
        }

        // Trier les câbles par capacité FO
        val sortedCables = cables.sortedWith(
            compareBy<ClassifiedCable> { if (it.isParent) 0 else it.hierarchyLevel }
                .thenByDescending { c -> c.capacityFO.filter { it.isDigit() }.toIntOrNull() ?: 0 }
        )

        var nextRowTracker = 0
        val visitedCables = mutableSetOf<String>()

        // Algorithme DFS de placement des câbles et nœuds par parcours inverse des boîtiers
        fun traverseCableDfs(
            cable: ClassifiedCable,
            parentCol: Int,
            isRoot: Boolean,
            baseCumulDist: Double
        ) {
            if (visitedCables.contains(cable.id)) return
            visitedCables.add(cable.id)

            val myRow = if (isRoot) 0 else ++nextRowTracker
            val seq = getNodesAndDistancesAlongCable(cable)
            if (seq.isEmpty()) return

            val cleanCableName = BoitierCableConnectivityHelper.cleanCableName(cable.name)

            // Pré-calculer l'analyse des supports de ce câble UNE SEULE FOIS pour tous ses tronçons
            val supportAnalysis = CableSupportAnalyzer.analyzeClassifiedCableSupports(cable, nodes)
            val allSupps = supportAnalysis.supports
            val intermediateSupps = if (allSupps.size > 2) {
                allSupps.subList(1, allSupps.size - 1)
            } else {
                emptyList()
            }
            val suppStr = if (intermediateSupps.isNotEmpty()) {
                intermediateSupps.joinToString(" -> ") { supp ->
                    val label = supp.supportTypeLabel
                    val name = supp.node.name.trim()
                    if (name.startsWith(label, ignoreCase = true)) name else "$label [$name]"
                }
            } else "-"

            var colPointer = if (isRoot) parentCol else (parentCol + 1)
            var prevNode: ClassifiedKmzNode? = null
            var prevDist = 0.0

            // 1. Placer d'abord tous les nœuds de ce câble sur sa ligne horizontale dédiée
            val nodeColsForThisCable = mutableMapOf<String, Int>()

            for ((idx, pair) in seq.withIndex()) {
                val (cn, cableRelDist) = pair
                val absCumulDist = baseCumulDist + cableRelDist

                val nodeCol: Int
                if (!nodeGridPos.containsKey(cn.id)) {
                    nodeCol = colPointer
                    nodeGridPos[cn.id] = Pair(nodeCol, myRow)
                    nodeCumulDist[cn.id] = absCumulDist
                    colPointer++
                } else {
                    nodeCol = nodeGridPos[cn.id]!!.first
                    if (nodeCumulDist[cn.id] == null) {
                        nodeCumulDist[cn.id] = absCumulDist
                    }
                }
                nodeColsForThisCable[cn.id] = nodeCol

                // Lien horizontal entre nœuds successifs du même câble
                if (prevNode != null && prevNode.id != cn.id) {
                    val linkId = "${prevNode.id}_to_${cn.id}_${cable.id}"
                    if (linkMap.none { it.id == linkId }) {
                        val segLen = Math.abs(cableRelDist - prevDist)
                        linkMap.add(
                            SynopticLink(
                                id = linkId,
                                fromNodeId = prevNode.id,
                                toNodeId = cn.id,
                                cableName = cleanCableName,
                                capacityFO = cable.capacityFO,
                                cable = cable,
                                supportsTraversesStr = suppStr,
                                supportsCount = intermediateSupps.size,
                                installationType = cable.installationType.ifBlank { "Conduite" },
                                segmentLengthMeters = if (segLen > 0) segLen else cable.totalLengthMeters
                            )
                        )
                    }
                }

                prevNode = cn
                prevDist = cableRelDist
            }

            // 2. PARCOURS INVERSE DES BOÎTIERS : Le dernier boîtier traité en premier !
            val childrenOfThisCable = childCablesMap[cable.id] ?: emptyList()

            // Pré-associer chaque enfant au bon boîtier de la séquence pour éviter les filtrages répétitifs O(N*M)
            val childrenByNodeId = mutableMapOf<String, MutableList<ClassifiedCable>>()
            for (child in childrenOfThisCable) {
                val childSeq = getNodesAndDistancesAlongCable(child)
                val firstChildNode = childSeq.firstOrNull()?.first
                val originBoitierName = child.originNodeName

                val matchedNode = seq.firstOrNull { (cn, _) ->
                    (firstChildNode != null && firstChildNode.id == cn.id) ||
                    (originBoitierName.isNotBlank() && originBoitierName != "-" && cn.name.contains(originBoitierName, ignoreCase = true))
                }?.first ?: seq.lastOrNull()?.first

                if (matchedNode != null) {
                    childrenByNodeId.getOrPut(matchedNode.id) { mutableListOf() }.add(child)
                }
            }

            for (pair in seq.reversed()) {
                val (cn, _) = pair
                val nodeCol = nodeColsForThisCable[cn.id] ?: parentCol
                val childrenAtNode = childrenByNodeId[cn.id] ?: emptyList()

                for (child in childrenAtNode) {
                    val childSeq = getNodesAndDistancesAlongCable(child)
                    val firstNewChildNode = childSeq.firstOrNull { !nodeGridPos.containsKey(it.first.id) }?.first

                    traverseCableDfs(
                        cable = child,
                        parentCol = nodeCol,
                        isRoot = false,
                        baseCumulDist = nodeCumulDist[cn.id] ?: baseCumulDist
                    )

                    // Lien vertical de dérivation depuis le joint parent vers le premier nouveau nœud de l'enfant
                    if (firstNewChildNode != null) {
                        val vertLinkId = "${cn.id}_to_${firstNewChildNode.id}_deriv_${child.id}"
                        if (linkMap.none { it.id == vertLinkId }) {
                            linkMap.add(
                                SynopticLink(
                                    id = vertLinkId,
                                    fromNodeId = cn.id,
                                    toNodeId = firstNewChildNode.id,
                                    cableName = BoitierCableConnectivityHelper.cleanCableName(child.name),
                                    capacityFO = child.capacityFO,
                                    cable = child,
                                    supportsTraversesStr = "-",
                                    supportsCount = 0,
                                    installationType = child.installationType.ifBlank { "Aérien" },
                                    segmentLengthMeters = 0.0
                                )
                            )
                        }
                    }
                }
            }
        }

        // Lancer le parcours DFS à partir du câble racine SRO
        traverseCableDfs(rootCable, parentCol = 0, isRoot = true, baseCumulDist = 0.0)

        // Traiter les câbles non encore visités
        for (c in sortedCables) {
            if (!visitedCables.contains(c.id)) {
                traverseCableDfs(c, parentCol = 1, isRoot = false, baseCumulDist = 0.0)
            }
        }

        // Compléter les positions des nœuds éligibles restant
        for (cn in eligibleNodes) {
            if (!nodeGridPos.containsKey(cn.id)) {
                nextRowTracker++
                nodeGridPos[cn.id] = Pair(1, nextRowTracker)
                nodeCumulDist[cn.id] = 0.0
            }
        }

        val maxCol = nodeGridPos.values.maxOfOrNull { it.first } ?: 0
        val maxRow = nodeGridPos.values.maxOfOrNull { it.second } ?: 0

        val columnSpacing = 720f   // Écartement horizontal très généreux entre boîtiers (lisibilité parfaite des connecteurs et supports)
        val rowSpacing = 380f      // Écartement vertical très généreux entre lignes de câble (dégagement optimal du L)
        val startX = 100f
        val startY = 80f

        val totalHeight = Math.max(600f, startY * 2 + (maxRow + 1) * rowSpacing)
        val totalWidth = Math.max(900f, startX * 2 + (maxCol + 1) * columnSpacing)

        val finalNodes = mutableListOf<SynopticNode>()

        for (cn in eligibleNodes) {
            val (col, row) = nodeGridPos[cn.id] ?: Pair(0, 0)
            val xPos = startX + col * columnSpacing
            val yPos = startY + row * rowSpacing

            val conn = connectivities[cn.id]
            val isSro = cn.category == KmzNodeCategory.SRO || (conn?.isSro == true)
            val isBoitier = cn.category == KmzNodeCategory.BOITIER || (conn?.isBoitier == true)

            val nameUpper = cn.name.uppercase(Locale.ROOT)
            val refUpper = cn.reference.uppercase(Locale.ROOT)
            val isBpeExplicit = nameUpper.contains("BPE") || nameUpper.contains("JOINT") || nameUpper.contains("SPLIT") || refUpper.contains("BPE") || refUpper.contains("JOINT")
            val isPbo = (nameUpper.contains("PBO") || (refUpper.contains("PBO") && !isBpeExplicit)) && !isBpeExplicit
            val badgeType = when {
                isSro -> "SRO"
                isPbo -> "PBO"
                else -> "BPE"
            }

            val cumulDist = nodeCumulDist[cn.id] ?: 0.0

            finalNodes.add(
                SynopticNode(
                    id = cn.id,
                    name = cn.name,
                    category = cn.category,
                    level = col,
                    row = row,
                    x = xPos,
                    y = yPos,
                    incomingCableName = conn?.incomingCableName ?: "-",
                    outgoingCablesStr = conn?.outgoingCablesStr ?: "-",
                    rawNode = cn.rawNode,
                    isSro = isSro,
                    isBoitier = isBoitier,
                    hostingSupportName = conn?.hostingSupportName ?: "-",
                    nodeTypeBadge = badgeType,
                    cumulativeDistanceMeters = cumulDist
                )
            )
        }

        return SynopticGraph(
            nodes = finalNodes,
            links = linkMap,
            maxLevel = maxCol,
            totalWidth = totalWidth,
            totalHeight = totalHeight
        )
    }
}
