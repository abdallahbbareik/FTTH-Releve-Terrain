package com.example.data.util

import android.content.Context
import com.example.data.local.FtthNodeEntity
import com.example.data.storage.StoredTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Données d'analyse complète KMZ mises en cache persistant.
 */
data class CachedKmzAnalysis(
    val signature: String,
    val timestamp: Long,
    val cables: List<ClassifiedCable>,
    val connectivities: Map<String, BoitierConnectivity>,
    val synopticGraph: SynopticGraph
)

/**
 * Gestionnaire de persistance pour mémoriser l'analyse complète du KMZ (câbles, connectivités boîtiers, synoptique).
 * Évite de recalculer l'analyse à chaque ouverture de l'application ou accès au dialogue.
 */
object KmzAnalysisPersistenceManager {
    private const val CACHE_FILE_NAME = "kmz_analysis_cache.json"

    @Volatile
    private var memoryCache: CachedKmzAnalysis? = null

    /**
     * Calcule une signature unique basée sur les nœuds et tracés.
     */
    fun computeSignature(nodes: List<FtthNodeEntity>, tracks: List<StoredTrack>): String {
        val nodesHash = nodes.fold(0L) { acc, n -> acc * 31 + n.id.hashCode() + n.name.hashCode() + n.updatedAt }
        val tracksHash = tracks.fold(0L) { acc, t -> acc * 31 + t.id.hashCode() + t.name.hashCode() + t.points.size + t.totalDistanceMeters.toBits() }
        return "${nodes.size}_${tracks.size}_${nodesHash}_${tracksHash}"
    }

    /**
     * Récupère l'analyse mémorisée si la signature correspond aux données actuelles.
     * 1. Vérifie le cache mémoire (instantané 0ms)
     * 2. Vérifie le fichier cache sur stockage interne (5-15ms)
     */
    suspend fun getAnalysis(
        context: Context,
        nodes: List<FtthNodeEntity>,
        tracks: List<StoredTrack>
    ): CachedKmzAnalysis? = withContext(Dispatchers.IO) {
        if (nodes.isEmpty() && tracks.isEmpty()) return@withContext null
        val currentSig = computeSignature(nodes, tracks)

        // 1. Vérification en mémoire vive
        memoryCache?.let { cached ->
            if (cached.signature == currentSig) {
                return@withContext cached
            }
        }

        // 2. Vérification sur le stockage interne
        val file = File(context.filesDir, CACHE_FILE_NAME)
        if (!file.exists() || file.length() == 0L) return@withContext null

        try {
            val jsonStr = file.readText(Charsets.UTF_8)
            val root = JSONObject(jsonStr)
            val savedSig = root.optString("signature", "")
            if (savedSig != currentSig) {
                return@withContext null
            }
            val timestamp = root.optLong("timestamp", 0L)

            // Reconstituer les ClassifiedCable
            val cablesArray = root.optJSONArray("cables") ?: JSONArray()
            val cables = mutableListOf<ClassifiedCable>()
            for (i in 0 until cablesArray.length()) {
                val cObj = cablesArray.getJSONObject(i)
                val subTracksArr = cObj.optJSONArray("subTracks") ?: JSONArray()
                val subTracks = mutableListOf<StoredTrack>()
                for (j in 0 until subTracksArr.length()) {
                    subTracks.add(StoredTrack.fromJson(subTracksArr.getJSONObject(j)))
                }

                cables.add(
                    ClassifiedCable(
                        id = cObj.optString("id"),
                        name = cObj.optString("name"),
                        baseName = cObj.optString("baseName"),
                        segmentCount = cObj.optInt("segmentCount"),
                        totalLengthMeters = cObj.optDouble("totalLengthMeters"),
                        capacityFO = cObj.optString("capacityFO"),
                        cableType = cObj.optString("cableType"),
                        installationType = cObj.optString("installationType"),
                        sourceFile = cObj.optString("sourceFile"),
                        startCoordinatesLabel = cObj.optString("startCoordinatesLabel"),
                        endCoordinatesLabel = cObj.optString("endCoordinatesLabel"),
                        subTracks = subTracks,
                        isParent = cObj.optBoolean("isParent", true),
                        isChild = cObj.optBoolean("isChild", false),
                        hierarchyLevel = cObj.optInt("hierarchyLevel", 0),
                        hierarchyRole = cObj.optString("hierarchyRole", "Parent (Principal)"),
                        parentCableName = cObj.optString("parentCableName", "-"),
                        originNodeName = cObj.optString("originNodeName", "-"),
                        destinationNodeName = cObj.optString("destinationNodeName", "-")
                    )
                )
            }

            val cablesMap = cables.associateBy { it.id }
            val nodesMap = nodes.associateBy { it.id }

            // Reconstituer BoitierConnectivity
            val connObj = root.optJSONObject("connectivities") ?: JSONObject()
            val connMap = mutableMapOf<String, BoitierConnectivity>()
            val keys = connObj.keys()
            while (keys.hasNext()) {
                val nodeId = keys.next()
                val bObj = connObj.getJSONObject(nodeId)
                val incomingId = bObj.optString("incomingCableId", "")
                val outgoingArr = bObj.optJSONArray("outgoingCableIds") ?: JSONArray()
                val outgoingCables = mutableListOf<ClassifiedCable>()
                for (k in 0 until outgoingArr.length()) {
                    val cid = outgoingArr.getString(k)
                    cablesMap[cid]?.let { outgoingCables.add(it) }
                }

                connMap[nodeId] = BoitierConnectivity(
                    nodeId = bObj.optString("nodeId"),
                    nodeName = bObj.optString("nodeName"),
                    isBoitier = bObj.optBoolean("isBoitier"),
                    isSro = bObj.optBoolean("isSro"),
                    incomingCable = cablesMap[incomingId],
                    incomingCableName = bObj.optString("incomingCableName"),
                    outgoingCables = outgoingCables,
                    outgoingCablesStr = bObj.optString("outgoingCablesStr"),
                    snapDistanceMeters = bObj.optDouble("snapDistanceMeters", 0.0),
                    anomalieApd = bObj.optString("anomalieApd", "-"),
                    hostingSupportName = bObj.optString("hostingSupportName", "-"),
                    hostingSupportDistance = bObj.optDouble("hostingSupportDistance", 0.0)
                )
            }

            // Reconstituer SynopticGraph
            val graphObj = root.optJSONObject("synopticGraph")
            val synopticGraph: SynopticGraph = if (graphObj != null) {
                val sNodesArr = graphObj.optJSONArray("nodes") ?: JSONArray()
                val sNodes = mutableListOf<SynopticNode>()
                for (m in 0 until sNodesArr.length()) {
                    val snObj = sNodesArr.getJSONObject(m)
                    val sId = snObj.optString("id")
                    val rawNode = nodesMap[sId] ?: continue
                    val catStr = snObj.optString("category", "AUTRE")
                    val cat = try { KmzNodeCategory.valueOf(catStr) } catch (_: Exception) { KmzNodeCategory.AUTRE }

                    sNodes.add(
                        SynopticNode(
                            id = sId,
                            name = snObj.optString("name"),
                            category = cat,
                            level = snObj.optInt("level"),
                            row = snObj.optInt("row"),
                            x = snObj.optDouble("x").toFloat(),
                            y = snObj.optDouble("y").toFloat(),
                            incomingCableName = snObj.optString("incomingCableName"),
                            outgoingCablesStr = snObj.optString("outgoingCablesStr"),
                            rawNode = rawNode,
                            isSro = snObj.optBoolean("isSro"),
                            isBoitier = snObj.optBoolean("isBoitier"),
                            hostingSupportName = snObj.optString("hostingSupportName", "-"),
                            nodeTypeBadge = snObj.optString("nodeTypeBadge", "BPE"),
                            cumulativeDistanceMeters = snObj.optDouble("cumulativeDistanceMeters", 0.0)
                        )
                    )
                }

                val sLinksArr = graphObj.optJSONArray("links") ?: JSONArray()
                val sLinks = mutableListOf<SynopticLink>()
                for (p in 0 until sLinksArr.length()) {
                    val slObj = sLinksArr.getJSONObject(p)
                    val cableRef = cablesMap[slObj.optString("cableId")]

                    sLinks.add(
                        SynopticLink(
                            id = slObj.optString("id"),
                            fromNodeId = slObj.optString("fromNodeId"),
                            toNodeId = slObj.optString("toNodeId"),
                            cableName = slObj.optString("cableName"),
                            capacityFO = slObj.optString("capacityFO"),
                            cable = cableRef,
                            supportsTraversesStr = slObj.optString("supportsTraversesStr", "-"),
                            supportsCount = slObj.optInt("supportsCount", 0),
                            installationType = slObj.optString("installationType", "Conduite"),
                            segmentLengthMeters = slObj.optDouble("segmentLengthMeters", 0.0)
                        )
                    )
                }

                SynopticGraph(
                    nodes = sNodes,
                    links = sLinks,
                    maxLevel = graphObj.optInt("maxLevel"),
                    totalWidth = graphObj.optDouble("totalWidth", 800.0).toFloat(),
                    totalHeight = graphObj.optDouble("totalHeight", 600.0).toFloat()
                )
            } else {
                FtthSynopticGraphBuilder.buildGraph(nodes, cables, connMap)
            }

            val result = CachedKmzAnalysis(
                signature = currentSig,
                timestamp = timestamp,
                cables = cables,
                connectivities = connMap,
                synopticGraph = synopticGraph
            )
            memoryCache = result
            return@withContext result
        } catch (e: Exception) {
            android.util.Log.e("KmzPersistence", "Erreur lecture cache KMZ persistant", e)
            return@withContext null
        }
    }

    /**
     * Sauvegarde l'analyse complète en mémoire et sur disque interne.
     */
    suspend fun saveAnalysis(
        context: Context,
        signature: String,
        cables: List<ClassifiedCable>,
        connectivities: Map<String, BoitierConnectivity>,
        synopticGraph: SynopticGraph
    ) = withContext(Dispatchers.IO) {
        val data = CachedKmzAnalysis(
            signature = signature,
            timestamp = System.currentTimeMillis(),
            cables = cables,
            connectivities = connectivities,
            synopticGraph = synopticGraph
        )
        memoryCache = data

        try {
            val root = JSONObject()
            root.put("signature", signature)
            root.put("timestamp", data.timestamp)

            val cablesArray = JSONArray()
            for (c in cables) {
                val cObj = JSONObject()
                cObj.put("id", c.id)
                cObj.put("name", c.name)
                cObj.put("baseName", c.baseName)
                cObj.put("segmentCount", c.segmentCount)
                cObj.put("totalLengthMeters", c.totalLengthMeters)
                cObj.put("capacityFO", c.capacityFO)
                cObj.put("cableType", c.cableType)
                cObj.put("installationType", c.installationType)
                cObj.put("sourceFile", c.sourceFile)
                cObj.put("startCoordinatesLabel", c.startCoordinatesLabel)
                cObj.put("endCoordinatesLabel", c.endCoordinatesLabel)
                cObj.put("isParent", c.isParent)
                cObj.put("isChild", c.isChild)
                cObj.put("hierarchyLevel", c.hierarchyLevel)
                cObj.put("hierarchyRole", c.hierarchyRole)
                cObj.put("parentCableName", c.parentCableName)
                cObj.put("originNodeName", c.originNodeName)
                cObj.put("destinationNodeName", c.destinationNodeName)

                val subTracksArr = JSONArray()
                for (st in c.subTracks) {
                    subTracksArr.put(st.toJson())
                }
                cObj.put("subTracks", subTracksArr)
                cablesArray.put(cObj)
            }
            root.put("cables", cablesArray)

            val connObj = JSONObject()
            for ((nodeId, conn) in connectivities) {
                val bObj = JSONObject()
                bObj.put("nodeId", conn.nodeId)
                bObj.put("nodeName", conn.nodeName)
                bObj.put("isBoitier", conn.isBoitier)
                bObj.put("isSro", conn.isSro)
                bObj.put("incomingCableId", conn.incomingCable?.id ?: "")
                bObj.put("incomingCableName", conn.incomingCableName)
                val outArr = JSONArray()
                conn.outgoingCables.forEach { outArr.put(it.id) }
                bObj.put("outgoingCableIds", outArr)
                bObj.put("outgoingCablesStr", conn.outgoingCablesStr)
                bObj.put("snapDistanceMeters", conn.snapDistanceMeters)
                bObj.put("anomalieApd", conn.anomalieApd)
                bObj.put("hostingSupportName", conn.hostingSupportName)
                bObj.put("hostingSupportDistance", conn.hostingSupportDistance)
                connObj.put(nodeId, bObj)
            }
            root.put("connectivities", connObj)

            val graphObj = JSONObject()
            graphObj.put("maxLevel", synopticGraph.maxLevel)
            graphObj.put("totalWidth", synopticGraph.totalWidth.toDouble())
            graphObj.put("totalHeight", synopticGraph.totalHeight.toDouble())

            val sNodesArr = JSONArray()
            for (sn in synopticGraph.nodes) {
                val snObj = JSONObject()
                snObj.put("id", sn.id)
                snObj.put("name", sn.name)
                snObj.put("category", sn.category.name)
                snObj.put("level", sn.level)
                snObj.put("row", sn.row)
                snObj.put("x", sn.x.toDouble())
                snObj.put("y", sn.y.toDouble())
                snObj.put("incomingCableName", sn.incomingCableName)
                snObj.put("outgoingCablesStr", sn.outgoingCablesStr)
                snObj.put("isSro", sn.isSro)
                snObj.put("isBoitier", sn.isBoitier)
                snObj.put("hostingSupportName", sn.hostingSupportName)
                snObj.put("nodeTypeBadge", sn.nodeTypeBadge)
                snObj.put("cumulativeDistanceMeters", sn.cumulativeDistanceMeters)
                sNodesArr.put(snObj)
            }
            graphObj.put("nodes", sNodesArr)

            val sLinksArr = JSONArray()
            for (sl in synopticGraph.links) {
                val slObj = JSONObject()
                slObj.put("id", sl.id)
                slObj.put("fromNodeId", sl.fromNodeId)
                slObj.put("toNodeId", sl.toNodeId)
                slObj.put("cableName", sl.cableName)
                slObj.put("capacityFO", sl.capacityFO)
                slObj.put("cableId", sl.cable?.id ?: "")
                slObj.put("supportsTraversesStr", sl.supportsTraversesStr)
                slObj.put("supportsCount", sl.supportsCount)
                slObj.put("installationType", sl.installationType)
                slObj.put("segmentLengthMeters", sl.segmentLengthMeters)
                sLinksArr.put(slObj)
            }
            graphObj.put("links", sLinksArr)

            root.put("synopticGraph", graphObj)

            val file = File(context.filesDir, CACHE_FILE_NAME)
            file.writeText(root.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("KmzPersistence", "Erreur écriture cache KMZ persistant", e)
        }
    }

    /**
     * Invalide le cache en mémoire et supprime le fichier de cache sur disque.
     */
    fun clearCache(context: Context) {
        memoryCache = null
        try {
            val file = File(context.filesDir, CACHE_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
    }
}
