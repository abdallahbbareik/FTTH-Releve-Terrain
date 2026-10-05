package com.example.data.storage

import android.content.Context
import android.os.Environment
import com.example.data.local.DefaultFtthData
import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.local.NodeConformity
import com.example.data.local.NodeStatus
import com.example.data.local.SyncState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val accuracy: Float = 0.0f,
    val speed: Float = 0.0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("lat", latitude)
        put("lon", longitude)
        put("alt", altitude)
        put("acc", accuracy)
        put("spd", speed)
        put("time", timestamp)
    }

    companion object {
        fun fromJson(json: JSONObject): TrackPoint = TrackPoint(
            latitude = json.optDouble("lat", 0.0),
            longitude = json.optDouble("lon", 0.0),
            altitude = json.optDouble("alt", 0.0),
            accuracy = json.optDouble("acc", 0.0).toFloat(),
            speed = json.optDouble("spd", 0.0).toFloat(),
            timestamp = json.optLong("time", System.currentTimeMillis())
        )
    }
}

data class TrackPhoto(
    val id: String = "PHO-${System.currentTimeMillis() % 10000}",
    val photoPath: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("photoPath", photoPath)
        put("latitude", latitude)
        put("longitude", longitude)
        put("timestamp", timestamp)
    }

    companion object {
        fun fromJson(json: JSONObject): TrackPhoto = TrackPhoto(
            id = json.optString("id", "PHO-${System.currentTimeMillis() % 10000}"),
            photoPath = json.optString("photoPath", ""),
            latitude = json.optDouble("latitude", 0.0),
            longitude = json.optDouble("longitude", 0.0),
            timestamp = json.optLong("timestamp", System.currentTimeMillis())
        )
    }
}

data class StoredTrack(
    val id: String,
    val name: String,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val totalDistanceMeters: Double = 0.0,
    val rawPoints: List<TrackPoint> = emptyList(), // Points bruts d'origine
    val points: List<TrackPoint> = emptyList(),    // Points actuels (simplifiés ou redressés)
    val photos: List<TrackPhoto> = emptyList(),    // Photos géoréférencées le long du trajet
    val isSimplified: Boolean = false,
    val toleranceMeters: Double? = null,
    val isActive: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("startTime", startTime)
        put("endTime", endTime ?: JSONObject.NULL)
        put("totalDistanceMeters", totalDistanceMeters)
        put("isSimplified", isSimplified)
        put("toleranceMeters", toleranceMeters ?: JSONObject.NULL)
        put("isActive", isActive)

        val rawArr = JSONArray()
        rawPoints.forEach { rawArr.put(it.toJson()) }
        put("rawPoints", rawArr)

        val ptsArr = JSONArray()
        points.forEach { ptsArr.put(it.toJson()) }
        put("points", ptsArr)

        val phoArr = JSONArray()
        photos.forEach { phoArr.put(it.toJson()) }
        put("photos", phoArr)
    }

    companion object {
        fun fromJson(json: JSONObject): StoredTrack {
            val rawList = mutableListOf<TrackPoint>()
            val rawArr = json.optJSONArray("rawPoints")
            if (rawArr != null) {
                for (i in 0 until rawArr.length()) {
                    rawList.add(TrackPoint.fromJson(rawArr.getJSONObject(i)))
                }
            }

            val ptsList = mutableListOf<TrackPoint>()
            val ptsArr = json.optJSONArray("points")
            if (ptsArr != null) {
                for (i in 0 until ptsArr.length()) {
                    ptsList.add(TrackPoint.fromJson(ptsArr.getJSONObject(i)))
                }
            }

            val phoList = mutableListOf<TrackPhoto>()
            val phoArr = json.optJSONArray("photos")
            if (phoArr != null) {
                for (i in 0 until phoArr.length()) {
                    phoList.add(TrackPhoto.fromJson(phoArr.getJSONObject(i)))
                }
            }

            return StoredTrack(
                id = json.optString("id", "TRK-${System.currentTimeMillis()}"),
                name = json.optString("name", "Trajet"),
                startTime = json.optLong("startTime", System.currentTimeMillis()),
                endTime = if (json.has("endTime") && !json.isNull("endTime")) json.optLong("endTime") else null,
                totalDistanceMeters = json.optDouble("totalDistanceMeters", 0.0),
                rawPoints = rawList,
                points = if (ptsList.isNotEmpty()) ptsList else rawList,
                photos = phoList,
                isSimplified = json.optBoolean("isSimplified", false),
                toleranceMeters = if (json.has("toleranceMeters") && !json.isNull("toleranceMeters")) json.optDouble("toleranceMeters") else null,
                isActive = json.optBoolean("isActive", false)
            )
        }
    }
}

class DocumentStorageManager(private val context: Context) {

    // Dossier public ou application "Documents/Releve-Terrain"
    val baseDir: File by lazy {
        val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val targetDir = File(publicDocs, "Releve-Terrain")
        if (!targetDir.exists()) {
            val created = targetDir.mkdirs()
            if (!created) {
                val fallback = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Releve-Terrain")
                if (!fallback.exists()) fallback.mkdirs()
                return@lazy fallback
            }
        }
        targetDir
    }

    val photosDir: File by lazy {
        val dir = File(baseDir, "Photos")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    val nodesFile: File get() = File(baseDir, "noeuds.json")
    val linksFile: File get() = File(baseDir, "liaisons.json")
    val tracksFile: File get() = File(baseDir, "trajets.json")
    val geoJsonFile: File get() = File(baseDir, "releve_terrain.geojson")

    init {
        // Initialiser avec les données par défaut si vide
        if (!nodesFile.exists()) {
            saveNodes(DefaultFtthData.getDefaultNodes(), updateGeoJson = false)
        }
        if (!linksFile.exists()) {
            saveLinks(DefaultFtthData.getDefaultLinks(), updateGeoJson = false)
        }
        if (!tracksFile.exists()) {
            saveTracks(emptyList(), updateGeoJson = false)
        }
        if (!geoJsonFile.exists()) {
            generateAndSaveGeoJson()
        }
    }

    // --- NOEUDS ---

    fun loadNodes(): List<FtthNodeEntity> {
        return try {
            if (!nodesFile.exists()) return emptyList()
            val content = nodesFile.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(content)
            val list = mutableListOf<FtthNodeEntity>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(nodeFromJson(obj))
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveNodes(nodes: List<FtthNodeEntity>, updateGeoJson: Boolean = true) {
        try {
            val jsonArray = JSONArray()
            nodes.forEach { jsonArray.put(nodeToJson(it)) }
            nodesFile.writeText(jsonArray.toString(2), Charsets.UTF_8)
            if (updateGeoJson) generateAndSaveGeoJson()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- LIAISONS ---

    fun loadLinks(): List<FtthLinkEntity> {
        return try {
            if (!linksFile.exists()) return emptyList()
            val content = linksFile.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(content)
            val list = mutableListOf<FtthLinkEntity>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(linkFromJson(obj))
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveLinks(links: List<FtthLinkEntity>, updateGeoJson: Boolean = true) {
        try {
            val jsonArray = JSONArray()
            links.forEach { jsonArray.put(linkToJson(it)) }
            linksFile.writeText(jsonArray.toString(2), Charsets.UTF_8)
            if (updateGeoJson) generateAndSaveGeoJson()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- TRAJETS ---

    fun loadTracks(): List<StoredTrack> {
        return try {
            if (!tracksFile.exists()) return emptyList()
            val content = tracksFile.readText(Charsets.UTF_8)
            val jsonArray = JSONArray(content)
            val list = mutableListOf<StoredTrack>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(StoredTrack.fromJson(obj))
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveTracks(tracks: List<StoredTrack>, updateGeoJson: Boolean = true) {
        try {
            val jsonArray = JSONArray()
            tracks.forEach { jsonArray.put(it.toJson()) }
            tracksFile.writeText(jsonArray.toString(2), Charsets.UTF_8)
            if (updateGeoJson) generateAndSaveGeoJson()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- SAUVEGARDE REELLE GEOJSON DANS DOCUMENTS/RELEVE-TERRAIN/RELEVE_TERRAIN.GEOJSON ---

    fun generateAndSaveGeoJson() {
        try {
            val nodes = loadNodes()
            val links = loadLinks()
            val tracks = loadTracks()

            val root = JSONObject()
            root.put("type", "FeatureCollection")
            val features = JSONArray()

            val nodesMap = nodes.associateBy { it.id }

            // 1. Points pour les nœuds (avec photos dans Photos/<id_noeud>/)
            for (node in nodes) {
                val feat = JSONObject()
                feat.put("type", "Feature")

                val geom = JSONObject()
                geom.put("type", "Point")
                val coords = JSONArray().apply {
                    put(node.longitude)
                    put(node.latitude)
                }
                geom.put("coordinates", coords)
                feat.put("geometry", geom)

                val props = JSONObject()
                props.put("id", node.id)
                props.put("type", node.type.name)
                props.put("typeLabel", node.type.label)
                props.put("name", node.name)
                props.put("status", node.status.label)
                props.put("etat", node.etat.label)
                props.put("address", node.address)
                props.put("hasBoitierFtth", node.hasBoitierFtth)
                props.put("notes", node.notes)
                props.put("technician", node.technicianName)
                props.put("photosFolder", "Photos/${node.id.replace("[^a-zA-Z0-9_-]".toRegex(), "_")}")
                props.put("photosCount", node.photos.size)
                props.put("photos", JSONArray(node.photos))
                props.put("poleNature", node.poleNature)
                props.put("poleHeight", node.poleHeight)
                props.put("chamberType", node.chamberType)
                props.put("boitierType", node.boitierType)
                props.put("isSaturated", node.isSaturated)
                props.put("boitierSupport", node.boitierSupport)
                props.put("sroType", node.sroType)
                props.put("sroCapacity", node.sroCapacity)
                props.put("buildingFloors", node.buildingFloors)
                props.put("buildingDwellings", node.buildingDwellings)
                props.put("hasLocalTechnique", node.hasLocalTechnique)
                props.put("hasGaineMontante", node.hasGaineMontante)
                props.put("syndicAuthorization", node.syndicAuthorization)
                props.put("syndicContact", node.syndicContact)
                props.put("buildingConnectionMode", node.buildingConnectionMode)
                props.put("villaConnectionMode", node.villaConnectionMode)
                props.put("updatedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE).format(Date(node.updatedAt)))

                feat.put("properties", props)
                features.put(feat)
            }

            // 2. Lignes pour les câbles
            for (link in links) {
                val from = nodesMap[link.fromNodeId]
                val to = nodesMap[link.toNodeId]
                if (from != null && to != null) {
                    val feat = JSONObject()
                    feat.put("type", "Feature")

                    val geom = JSONObject()
                    geom.put("type", "LineString")
                    val coords = JSONArray().apply {
                        put(JSONArray().put(from.longitude).put(from.latitude))
                        put(JSONArray().put(to.longitude).put(to.latitude))
                    }
                    geom.put("coordinates", coords)
                    feat.put("geometry", geom)

                    val props = JSONObject()
                    props.put("id", link.id)
                    props.put("fromNodeId", link.fromNodeId)
                    props.put("toNodeId", link.toNodeId)
                    props.put("cableType", link.cableType)
                    props.put("installationType", link.installationType)
                    props.put("capacityFO", link.capacityFO)
                    props.put("lengthMeters", link.lengthMeters)
                    props.put("status", link.status.label)
                    feat.put("properties", props)

                    features.put(feat)
                }
            }

            // 3. Lignes pour les trajets et Points pour les photos le long des trajets
            for (track in tracks) {
                val pts = if (track.points.isNotEmpty()) track.points else track.rawPoints
                if (pts.size >= 2) {
                    val feat = JSONObject()
                    feat.put("type", "Feature")

                    val geom = JSONObject()
                    geom.put("type", "LineString")
                    val coords = JSONArray()
                    for (p in pts) {
                        coords.put(JSONArray().put(p.longitude).put(p.latitude))
                    }
                    geom.put("coordinates", coords)
                    feat.put("geometry", geom)

                    val props = JSONObject()
                    props.put("id", track.id)
                    props.put("name", track.name)
                    props.put("distanceMeters", track.totalDistanceMeters)
                    props.put("pointCount", pts.size)
                    props.put("isSimplified", track.isSimplified)
                    props.put("toleranceMeters", track.toleranceMeters ?: 0.0)
                    props.put("type", "TRAJET_GPS")
                    feat.put("properties", props)

                    features.put(feat)
                }

                // Photos le long du trajet
                for (pho in track.photos) {
                    val pFeat = JSONObject()
                    pFeat.put("type", "Feature")
                    val pGeom = JSONObject()
                    pGeom.put("type", "Point")
                    pGeom.put("coordinates", JSONArray().put(pho.longitude).put(pho.latitude))
                    pFeat.put("geometry", pGeom)

                    val pProps = JSONObject()
                    pProps.put("id", pho.id)
                    pProps.put("trackId", track.id)
                    pProps.put("photoPath", pho.photoPath)
                    pProps.put("timestamp", pho.timestamp)
                    pProps.put("type", "PHOTO_TRAJET")
                    pFeat.put("properties", pProps)

                    features.put(pFeat)
                }
            }

            root.put("features", features)
            geoJsonFile.writeText(root.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- Helpers de sérialisation JSON ---

    private fun nodeToJson(node: FtthNodeEntity): JSONObject = JSONObject().apply {
        put("id", node.id)
        put("type", node.type.name)
        put("name", node.name)
        put("latitude", node.latitude)
        put("longitude", node.longitude)
        put("status", node.status.name)
        put("etat", node.etat.name)
        put("address", node.address)
        put("hasBoitierFtth", node.hasBoitierFtth)
        put("notes", node.notes)
        put("technicianName", node.technicianName)
        put("photoCount", node.photos.size)
        put("photos", JSONArray(node.photos))
        put("updatedAt", node.updatedAt)

        put("poleNature", node.poleNature)
        put("poleHeight", node.poleHeight)
        put("chamberType", node.chamberType)
        put("boitierType", node.boitierType)
        put("isSaturated", node.isSaturated)
        put("boitierSupport", node.boitierSupport)
        put("sroType", node.sroType)
        put("sroCapacity", node.sroCapacity)
        put("buildingFloors", node.buildingFloors)
        put("buildingDwellings", node.buildingDwellings)
        put("hasLocalTechnique", node.hasLocalTechnique)
        put("hasGaineMontante", node.hasGaineMontante)
        put("syndicAuthorization", node.syndicAuthorization)
        put("syndicContact", node.syndicContact)
        put("buildingConnectionMode", node.buildingConnectionMode)
        put("villaConnectionMode", node.villaConnectionMode)
    }

    private fun nodeFromJson(obj: JSONObject): FtthNodeEntity {
        val photosList = mutableListOf<String>()
        val photosArr = obj.optJSONArray("photos")
        if (photosArr != null) {
            for (i in 0 until photosArr.length()) {
                photosList.add(photosArr.getString(i))
            }
        }

        val typeStr = obj.optString("type", FtthNodeType.POTEAU.name)
        val type = try { FtthNodeType.valueOf(typeStr) } catch (e: Exception) { FtthNodeType.POTEAU }

        val statusStr = obj.optString("status", NodeStatus.EXISTANT.name)
        val status = try { NodeStatus.valueOf(statusStr) } catch (e: Exception) { NodeStatus.EXISTANT }

        val etatStr = obj.optString("etat", NodeConformity.CONFORME.name)
        val etat = try { NodeConformity.valueOf(etatStr) } catch (e: Exception) { NodeConformity.CONFORME }

        return FtthNodeEntity(
            id = obj.optString("id", "N-${System.currentTimeMillis() % 10000}"),
            type = type,
            name = obj.optString("name", "Nœud"),
            latitude = obj.optDouble("latitude", 48.8566),
            longitude = obj.optDouble("longitude", 2.3522),
            status = status,
            etat = etat,
            address = obj.optString("address", ""),
            hasBoitierFtth = obj.optBoolean("hasBoitierFtth", false),
            notes = obj.optString("notes", ""),
            technicianName = obj.optString("technicianName", "Tech-01"),
            photoCount = photosList.size,
            photos = photosList,
            syncState = SyncState.SYNCED,
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
            poleNature = obj.optString("poleNature", "béton"),
            poleHeight = obj.optInt("poleHeight", 8),
            chamberType = obj.optString("chamberType", "L2T"),
            boitierType = obj.optString("boitierType", "PBO"),
            isSaturated = obj.optBoolean("isSaturated", false),
            boitierSupport = obj.optString("boitierSupport", "Poteau"),
            sroType = obj.optString("sroType", "armoire de rue"),
            sroCapacity = obj.optString("sroCapacity", "360 FO"),
            buildingFloors = obj.optInt("buildingFloors", 4),
            buildingDwellings = obj.optInt("buildingDwellings", 16),
            hasLocalTechnique = obj.optBoolean("hasLocalTechnique", true),
            hasGaineMontante = obj.optBoolean("hasGaineMontante", true),
            syndicAuthorization = obj.optString("syndicAuthorization", "Accord obtenu"),
            syndicContact = obj.optString("syndicContact", ""),
            buildingConnectionMode = obj.optString("buildingConnectionMode", "souterrain"),
            villaConnectionMode = obj.optString("villaConnectionMode", "aérien")
        )
    }

    private fun linkToJson(link: FtthLinkEntity): JSONObject = JSONObject().apply {
        put("id", link.id)
        put("fromNodeId", link.fromNodeId)
        put("toNodeId", link.toNodeId)
        put("cableType", link.cableType)
        put("installationType", link.installationType)
        put("capacityFO", link.capacityFO)
        put("lengthMeters", link.lengthMeters)
        put("status", link.status.name)
        put("updatedAt", link.updatedAt)
    }

    private fun linkFromJson(obj: JSONObject): FtthLinkEntity {
        val statusStr = obj.optString("status", NodeStatus.EXISTANT.name)
        val status = try { NodeStatus.valueOf(statusStr) } catch (e: Exception) { NodeStatus.EXISTANT }
        return FtthLinkEntity(
            id = obj.optString("id", "LNK-${System.currentTimeMillis() % 10000}"),
            fromNodeId = obj.optString("fromNodeId", ""),
            toNodeId = obj.optString("toNodeId", ""),
            cableType = obj.optString("cableType", "Distribution"),
            installationType = obj.optString("installationType", "Aérien"),
            capacityFO = obj.optInt("capacityFO", 24),
            lengthMeters = obj.optDouble("lengthMeters", 45.0),
            status = status,
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
        )
    }
}
