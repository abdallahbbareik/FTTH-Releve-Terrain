package com.example.data.util

import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType

data class SynopticNode(
    val id: String,
    val name: String,
    val category: KmzNodeCategory,
    val level: Int,                // 0 = SRO (racine), 1 = Niveau 1 (boîtier transport), 2 = Niveau 2 (boîtier distribution/PBO), etc.
    val row: Int,                  // Indice vertical dans le niveau
    val x: Float,                  // Position calculée X
    val y: Float,                  // Position calculée Y
    val incomingCableName: String,
    val outgoingCablesStr: String,
    val rawNode: FtthNodeEntity,
    val isSro: Boolean,
    val isBoitier: Boolean
)

data class SynopticLink(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val cableName: String,
    val capacityFO: String,
    val cable: ClassifiedCable?
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
     * Tisse et calcule le schéma synoptique du réseau FTTH à partir des nœuds et des câbles.
     */
    fun buildGraph(
        nodes: List<FtthNodeEntity>,
        cables: List<ClassifiedCable>,
        connectivities: Map<String, BoitierConnectivity>
    ): SynopticGraph {
        if (nodes.isEmpty()) {
            return SynopticGraph(emptyList(), emptyList(), 0, 800f, 600f)
        }

        val classifiedNodes = nodes.map { KmzNodeClassifier.classifyNode(it) }
        val nodeById = classifiedNodes.associateBy { it.id }

        // Conserver uniquement les SRO et les Boîtiers (pas de chambres/poteaux seuls dans le synoptique)
        val eligibleNodes = classifiedNodes.filter { cn ->
            val conn = connectivities[cn.id]
            val isSro = cn.category == KmzNodeCategory.SRO || (conn?.isSro == true)
            val isBoitier = cn.category == KmzNodeCategory.BOITIER || (conn?.isBoitier == true)
            isSro || isBoitier
        }

        if (eligibleNodes.isEmpty()) {
            return SynopticGraph(emptyList(), emptyList(), 0, 800f, 600f)
        }

        // Map des niveaux calculés pour chaque ID de nœud
        val nodeLevels = mutableMapOf<String, Int>()
        val parentMap = mutableMapOf<String, String>() // targetNodeId -> parentNodeId
        val linkMap = mutableListOf<SynopticLink>()

        // 1. Identifier les SRO (Racines, Level 0)
        val sroNodes = eligibleNodes.filter { cn ->
            val conn = connectivities[cn.id]
            cn.category == KmzNodeCategory.SRO || (conn?.isSro == true)
        }

        val rootNodeIds = if (sroNodes.isNotEmpty()) {
            sroNodes.map { it.id }
        } else {
            // Si pas de SRO explicite, prendre le premier boîtier comme racine
            listOf(eligibleNodes.first().id)
        }

        rootNodeIds.forEach { id -> nodeLevels[id] = 0 }

        // 2. Traverser le réseau via les liaisons câbles pour assigner les niveaux hiérarchiques (Breadth-First Search)
        val queue = ArrayDeque<String>()
        queue.addAll(rootNodeIds)
        val visited = mutableSetOf<String>()
        visited.addAll(rootNodeIds)

        while (queue.isNotEmpty()) {
            val currId = queue.removeFirst()
            val currLevel = nodeLevels[currId] ?: 0
            val conn = connectivities[currId] ?: continue

            // Parcourir tous les câbles de sortie de ce nœud
            for (outCable in conn.outgoingCables) {
                // Trouver quel boîtier de destination ce câble alimente à son autre extrémité
                val cleanOutName = BoitierCableConnectivityHelper.cleanCableName(outCable.name)

                // Chercher le nœud éligible dont le câble d'entrée correspond à outCable
                for (otherNode in eligibleNodes) {
                    if (otherNode.id == currId) continue
                    val otherConn = connectivities[otherNode.id] ?: continue
                    val cleanInName = BoitierCableConnectivityHelper.cleanCableName(otherConn.incomingCableName)

                    val matchesIncoming = (outCable.id == otherConn.incomingCable?.id) ||
                            (cleanInName.isNotBlank() && cleanInName != "-" && (cleanInName == cleanOutName || cleanOutName.contains(cleanInName, ignoreCase = true) || cleanInName.contains(cleanOutName, ignoreCase = true)))

                    if (matchesIncoming) {
                        if (!visited.contains(otherNode.id)) {
                            nodeLevels[otherNode.id] = currLevel + 1
                            parentMap[otherNode.id] = currId
                            visited.add(otherNode.id)
                            queue.addLast(otherNode.id)
                        }

                        // Ajouter le lien
                        val linkId = "${currId}_to_${otherNode.id}_${outCable.id}"
                        if (linkMap.none { it.id == linkId }) {
                            linkMap.add(
                                SynopticLink(
                                    id = linkId,
                                    fromNodeId = currId,
                                    toNodeId = otherNode.id,
                                    cableName = cleanOutName,
                                    capacityFO = outCable.capacityFO,
                                    cable = outCable
                                )
                            )
                        }
                    }
                }
            }
        }

        // Attribuer un niveau 1 aux nœuds non visités
        for (cn in eligibleNodes) {
            if (!nodeLevels.containsKey(cn.id)) {
                nodeLevels[cn.id] = 1
            }
        }

        val maxLevel = nodeLevels.values.maxOrNull() ?: 0

        // 3. Regrouper les nœuds par niveau et calculer les coordonnées (X, Y)
        val nodesByLevel = mutableMapOf<Int, MutableList<ClassifiedKmzNode>>()
        for (lvl in 0..maxLevel) {
            nodesByLevel[lvl] = mutableListOf()
        }
        for (cn in eligibleNodes) {
            val lvl = nodeLevels[cn.id] ?: 0
            nodesByLevel.getOrPut(lvl) { mutableListOf() }.add(cn)
        }

        val columnSpacing = 280f   // Écartement horizontal entre niveaux
        val rowSpacing = 110f      // Écartement vertical entre boîtiers d'un même niveau
        val startX = 100f
        val startY = 80f

        var maxNodesInLevel = 1
        nodesByLevel.values.forEach { list ->
            if (list.size > maxNodesInLevel) maxNodesInLevel = list.size
        }

        val totalHeight = Math.max(600f, startY * 2 + maxNodesInLevel * rowSpacing)
        val totalWidth = Math.max(800f, startX * 2 + (maxLevel + 1) * columnSpacing)

        val finalNodes = mutableListOf<SynopticNode>()

        for ((lvl, levelNodes) in nodesByLevel) {
            val xPos = startX + lvl * columnSpacing
            val levelHeight = levelNodes.size * rowSpacing
            val levelStartY = (totalHeight - levelHeight) / 2f + (rowSpacing / 2f)

            for ((idx, cn) in levelNodes.withIndex()) {
                val yPos = levelStartY + idx * rowSpacing
                val conn = connectivities[cn.id]
                val isSro = cn.category == KmzNodeCategory.SRO || (conn?.isSro == true)
                val isBoitier = cn.category == KmzNodeCategory.BOITIER || (conn?.isBoitier == true)

                finalNodes.add(
                    SynopticNode(
                        id = cn.id,
                        name = cn.name,
                        category = cn.category,
                        level = lvl,
                        row = idx,
                        x = xPos,
                        y = yPos,
                        incomingCableName = conn?.incomingCableName ?: "-",
                        outgoingCablesStr = conn?.outgoingCablesStr ?: "-",
                        rawNode = cn.rawNode,
                        isSro = isSro,
                        isBoitier = isBoitier
                    )
                )
            }
        }

        return SynopticGraph(
            nodes = finalNodes,
            links = linkMap,
            maxLevel = maxLevel,
            totalWidth = totalWidth,
            totalHeight = totalHeight
        )
    }
}
