package com.example.data.storage

import android.content.Context
import android.os.Environment
import com.example.data.local.AppDatabase
import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.local.NodeConformity
import com.example.data.local.NodeStatus
import com.example.data.local.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    val type: String = "GC", // GC, Aérien, Façade
    val etat: String = "Conforme", // Conforme, Non conforme
    val conduitAudit: String = "Libres", // Libres, Occupés, Bouchés
    val conduitType: String = "PEHD", // PEHD, PVC, Autre
    val conduitCount: Int = 1,
    val conduitDiameters: List<String> = listOf("Ø 40"), // Ø 30, Ø 40, Ø 50, Ø 80, Ø 100, etc.
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val totalDistanceMeters: Double = 0.0,
    val rawPoints: List<TrackPoint> = emptyList(),
    val points: List<TrackPoint> = emptyList(),
    val photos: List<TrackPhoto> = emptyList(),
    val isSimplified: Boolean = false,
    val toleranceMeters: Double? = null,
    val isActive: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type)
        put("etat", etat)
        put("conduitAudit", conduitAudit)
        put("conduitType", conduitType)
        put("conduitCount", conduitCount)
        val diaArr = JSONArray()
        conduitDiameters.forEach { diaArr.put(it) }
        put("conduitDiameters", diaArr)
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

            val diaList = mutableListOf<String>()
            val diaArr = json.optJSONArray("conduitDiameters")
            if (diaArr != null) {
                for (i in 0 until diaArr.length()) {
                    diaList.add(diaArr.getString(i))
                }
            } else if (json.has("conduitDiameters")) {
                val str = json.optString("conduitDiameters", "Ø 40")
                if (str.isNotBlank()) diaList.add(str)
            }
            if (diaList.isEmpty()) {
                diaList.add("Ø 40")
            }

            return StoredTrack(
                id = json.optString("id", "TRK-${System.currentTimeMillis()}"),
                name = json.optString("name", "Trajet"),
                type = json.optString("type", "GC"),
                etat = json.optString("etat", "Conforme"),
                conduitAudit = json.optString("conduitAudit", "Libres"),
                conduitType = json.optString("conduitType", "PEHD"),
                conduitCount = json.optInt("conduitCount", 1),
                conduitDiameters = diaList,
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

/**
 * Informations sur un projet FTTH
 */
data class ProjectInfo(
    val name: String,
    val nodesCount: Int,
    val tracksCount: Int,
    val linksCount: Int,
    val lastModified: Long,
    val isCurrent: Boolean
)

class DocumentStorageManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("ReleveTerrainStoragePrefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val database = AppDatabase.getDatabase(context)

    // Nom du projet actif (par défaut "projet01")
    private var _currentProject: String = prefs.getString("active_project_name", "projet01") ?: "projet01"
    val currentProject: String get() = _currentProject

    // Dossiers cibles sous Documents/Releve-Terrain
    val publicBaseDir: File
        get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Releve-Terrain")

    val appExtBaseDir: File
        get() = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Releve-Terrain")

    val internalBaseDir: File
        get() = File(context.filesDir, "Releve-Terrain")

    // Répertoire principal préféré pour Releve-Terrain
    val baseDir: File
        get() {
            ensureDirectoriesExist()
            if (isWritable(publicBaseDir)) return publicBaseDir
            if (isWritable(appExtBaseDir)) return appExtBaseDir
            return internalBaseDir
        }

    // Répertoire du projet actuel
    fun getProjectDir(projectName: String = currentProject): File {
        val sanitized = sanitizeProjectName(projectName)
        val dir = File(baseDir, sanitized)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getProjectPhotosDir(projectName: String = currentProject): File {
        val dir = File(getProjectDir(projectName), "Photos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    init {
        ensureDirectoriesExist()
        migrateLegacyRootFilesIfNeeded()
        ensureDefaultProjectExists()
    }

    private fun sanitizeProjectName(name: String): String {
        val cleaned = name.trim().replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        return cleaned.ifBlank { "projet01" }
    }

    fun setCurrentProject(projectName: String) {
        val sanitized = sanitizeProjectName(projectName)
        _currentProject = sanitized
        prefs.edit().putString("active_project_name", sanitized).apply()
        ensureProjectDirectoriesExist(sanitized)
    }

    /**
     * Migration transparente des fichiers racines créés par les anciennes versions
     * vers le dossier du premier projet "projet01"
     */
    private fun migrateLegacyRootFilesIfNeeded() {
        try {
            val rootNodesFile = File(publicBaseDir, "noeuds.json")
            val rootTracksFile = File(publicBaseDir, "trajets.json")
            val rootLinksFile = File(publicBaseDir, "liaisons.json")

            val project01Dir = File(publicBaseDir, "projet01")
            val projNodesFile = File(project01Dir, "noeuds.json")

            if (rootNodesFile.exists() && rootNodesFile.length() > 2 && !projNodesFile.exists()) {
                project01Dir.mkdirs()
                rootNodesFile.copyTo(File(project01Dir, "noeuds.json"), overwrite = true)
                rootNodesFile.copyTo(File(project01Dir, ".points"), overwrite = true)
                if (rootTracksFile.exists()) {
                    rootTracksFile.copyTo(File(project01Dir, "trajets.json"), overwrite = true)
                    rootTracksFile.copyTo(File(project01Dir, ".trajets"), overwrite = true)
                }
                if (rootLinksFile.exists()) {
                    rootLinksFile.copyTo(File(project01Dir, "liaisons.json"), overwrite = true)
                    rootLinksFile.copyTo(File(project01Dir, ".liaisons"), overwrite = true)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun ensureDefaultProjectExists() {
        val projects = listAllProjects()
        if (projects.isEmpty()) {
            createProject("projet01", copyCurrent = false)
        } else {
            // S'assurer que le projet actif est valide
            if (projects.none { it.name == _currentProject }) {
                _currentProject = projects.first().name
                prefs.edit().putString("active_project_name", _currentProject).apply()
            }
        }
    }

    /**
     * Crée l'arborescence Documents/Releve-Terrain
     */
    fun ensureDirectoriesExist() {
        try {
            if (!publicBaseDir.exists()) publicBaseDir.mkdirs()
            File(publicBaseDir, "Photos").let { if (!it.exists()) it.mkdirs() }
            File(publicBaseDir, "Exports").let { if (!it.exists()) it.mkdirs() }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (!appExtBaseDir.exists()) appExtBaseDir.mkdirs()
            File(appExtBaseDir, "Photos").let { if (!it.exists()) it.mkdirs() }
            File(appExtBaseDir, "Exports").let { if (!it.exists()) it.mkdirs() }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (!internalBaseDir.exists()) internalBaseDir.mkdirs()
            File(internalBaseDir, "Photos").let { if (!it.exists()) it.mkdirs() }
            File(internalBaseDir, "Exports").let { if (!it.exists()) it.mkdirs() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun ensureProjectDirectoriesExist(projectName: String) {
        val sanitized = sanitizeProjectName(projectName)
        listOf(publicBaseDir, appExtBaseDir, internalBaseDir).forEach { base ->
            try {
                val projDir = File(base, sanitized)
                if (!projDir.exists()) projDir.mkdirs()
                File(projDir, "Photos").let { if (!it.exists()) it.mkdirs() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isWritable(dir: File): Boolean {
        return try {
            if (!dir.exists()) dir.mkdirs()
            val testFile = File(dir, ".test_write")
            testFile.writeText("ok")
            val canRead = testFile.readText() == "ok"
            testFile.delete()
            canRead
        } catch (e: Exception) {
            false
        }
    }

    private fun getWritableDirectories(): List<File> {
        val list = mutableListOf<File>()
        if (isWritable(publicBaseDir)) list.add(publicBaseDir)
        if (isWritable(appExtBaseDir)) list.add(appExtBaseDir)
        if (isWritable(internalBaseDir)) list.add(internalBaseDir)
        return if (list.isNotEmpty()) list else listOf(context.filesDir)
    }

    // --- GESTION DES PROJETS ---

    fun listAllProjects(): List<ProjectInfo> {
        val projectMap = mutableMapOf<String, Long>()

        // 1. Scanner les dossiers physiques sur le stockage public
        try {
            if (publicBaseDir.exists() && publicBaseDir.isDirectory) {
                publicBaseDir.listFiles()?.forEach { file ->
                    if (file.isDirectory && file.name != "Photos" && file.name != "Exports" && !file.name.startsWith(".")) {
                        projectMap[file.name] = file.lastModified()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Scanner le stockage externe privé de l'app
        try {
            if (appExtBaseDir.exists() && appExtBaseDir.isDirectory) {
                appExtBaseDir.listFiles()?.forEach { file ->
                    if (file.isDirectory && file.name != "Photos" && file.name != "Exports" && !file.name.startsWith(".")) {
                        projectMap.putIfAbsent(file.name, file.lastModified())
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Scanner via MediaStore (recherche après réinstallation)
        val mediaStoreProjects = PublicStorageHelper.listPublicProjects(context)
        for (p in mediaStoreProjects) {
            projectMap.putIfAbsent(p, System.currentTimeMillis())
        }

        // Si aucun projet trouvé mais des fichiers racines existent
        if (projectMap.isEmpty()) {
            val rootNodes = File(publicBaseDir, "noeuds.json")
            if (rootNodes.exists()) {
                projectMap["projet01"] = rootNodes.lastModified()
            }
        }

        val result = mutableListOf<ProjectInfo>()
        for ((name, lastMod) in projectMap) {
            val nodes = loadNodes(name)
            val tracks = loadTracks(name)
            val links = loadLinks(name)
            result.add(
                ProjectInfo(
                    name = name,
                    nodesCount = nodes.size,
                    tracksCount = tracks.size,
                    linksCount = links.size,
                    lastModified = lastMod,
                    isCurrent = name == _currentProject
                )
            )
        }

        return result.sortedWith(compareByDescending<ProjectInfo> { it.isCurrent }.thenBy { it.name })
    }

    fun createProject(projectName: String, copyCurrent: Boolean = false): Boolean {
        val sanitized = sanitizeProjectName(projectName)
        ensureProjectDirectoriesExist(sanitized)

        val currentNodes = if (copyCurrent) loadNodes() else emptyList()
        val currentLinks = if (copyCurrent) loadLinks() else emptyList()
        val currentTracks = if (copyCurrent) loadTracks() else emptyList()

        saveNodes(currentNodes, project = sanitized, updateGeoJson = false)
        saveLinks(currentLinks, project = sanitized, updateGeoJson = false)
        saveTracks(currentTracks, project = sanitized, updateGeoJson = false)
        generateAndSaveGeoJson(project = sanitized)

        setCurrentProject(sanitized)
        return true
    }

    fun deleteProject(projectName: String): Boolean {
        val sanitized = sanitizeProjectName(projectName)
        listOf(publicBaseDir, appExtBaseDir, internalBaseDir).forEach { base ->
            try {
                val dir = File(base, sanitized)
                if (dir.exists()) dir.deleteRecursively()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        PublicStorageHelper.deletePublicProject(context, sanitized)

        if (_currentProject == sanitized) {
            val remaining = listAllProjects()
            if (remaining.isNotEmpty()) {
                setCurrentProject(remaining.first().name)
            } else {
                createProject("projet01", copyCurrent = false)
            }
        }
        return true
    }

    fun renameProject(oldName: String, newName: String): Boolean {
        val oldSanitized = sanitizeProjectName(oldName)
        val newSanitized = sanitizeProjectName(newName)
        if (oldSanitized == newSanitized) return true

        ensureProjectDirectoriesExist(newSanitized)

        listOf(publicBaseDir, appExtBaseDir, internalBaseDir).forEach { base ->
            try {
                val oldDir = File(base, oldSanitized)
                val newDir = File(base, newSanitized)
                if (oldDir.exists()) {
                    oldDir.copyRecursively(newDir, overwrite = true)
                    oldDir.deleteRecursively()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        PublicStorageHelper.renamePublicProject(context, oldSanitized, newSanitized)

        if (_currentProject == oldSanitized) {
            setCurrentProject(newSanitized)
        }
        return true
    }

    // --- CHARGEMENT & SAUVEGARDE DES NOEUDS (.points / noeuds.json) ---

    fun loadNodes(project: String = currentProject): List<FtthNodeEntity> {
        val sanitized = sanitizeProjectName(project)
        val targetFiles = listOf("$sanitized/noeuds.json", "$sanitized/.points", "$sanitized/points.json", "noeuds.json")

        for (relPath in targetFiles) {
            // 1. Tente de charger depuis le stockage public partagé (persiste après réinstallation)
            val publicContent = PublicStorageHelper.loadPublicDocument(context, relPath)
            if (!publicContent.isNullOrBlank() && publicContent != "[]") {
                try {
                    val jsonArray = JSONArray(publicContent)
                    val list = mutableListOf<FtthNodeEntity>()
                    for (i in 0 until jsonArray.length()) {
                        list.add(nodeFromJson(jsonArray.getJSONObject(i)))
                    }
                    if (list.isNotEmpty()) return list
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. Tente depuis les dossiers physiques
            val searchFiles = listOf(
                File(publicBaseDir, relPath),
                File(appExtBaseDir, relPath),
                File(internalBaseDir, relPath)
            )

            for (file in searchFiles) {
                if (file.exists()) {
                    try {
                        val content = file.readText(Charsets.UTF_8).trim()
                        if (content.isNotEmpty() && content != "[]") {
                            val jsonArray = JSONArray(content)
                            val list = mutableListOf<FtthNodeEntity>()
                            for (i in 0 until jsonArray.length()) {
                                list.add(nodeFromJson(jsonArray.getJSONObject(i)))
                            }
                            if (list.isNotEmpty()) return list
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // Vérifier SharedPreferences de secours
        val prefsJson = prefs.getString("backup_nodes_json_$sanitized", null)
        if (!prefsJson.isNullOrBlank() && prefsJson != "[]") {
            try {
                val jsonArray = JSONArray(prefsJson)
                val list = mutableListOf<FtthNodeEntity>()
                for (i in 0 until jsonArray.length()) {
                    list.add(nodeFromJson(jsonArray.getJSONObject(i)))
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return emptyList()
    }

    fun saveNodes(nodes: List<FtthNodeEntity>, project: String = currentProject, updateGeoJson: Boolean = true) {
        val sanitized = sanitizeProjectName(project)
        ensureProjectDirectoriesExist(sanitized)
        try {
            val jsonArray = JSONArray()
            nodes.forEach { jsonArray.put(nodeToJson(it)) }
            val jsonString = jsonArray.toString(2)

            // Sauvegarde publique persistante sous Documents/Releve-Terrain/<projet>/noeuds.json et .points
            PublicStorageHelper.savePublicDocument(context, "$sanitized/noeuds.json", jsonString)
            PublicStorageHelper.savePublicDocument(context, "$sanitized/.points", jsonString)

            // Écrire dans tous les répertoires disponibles
            for (dir in getWritableDirectories()) {
                try {
                    val projDir = File(dir, sanitized)
                    if (!projDir.exists()) projDir.mkdirs()
                    File(projDir, "noeuds.json").writeText(jsonString, Charsets.UTF_8)
                    File(projDir, ".points").writeText(jsonString, Charsets.UTF_8)
                    File(projDir, "points.json").writeText(jsonString, Charsets.UTF_8)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            prefs.edit().putString("backup_nodes_json_$sanitized", jsonString).apply()

            if (project == currentProject) {
                scope.launch {
                    try {
                        database.ftthDao().clearNodes()
                        if (nodes.isNotEmpty()) {
                            database.ftthDao().insertNodes(nodes)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (updateGeoJson) generateAndSaveGeoJson(sanitized)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- CHARGEMENT & SAUVEGARDE DES LIAISONS (.liaisons / liaisons.json) ---

    fun loadLinks(project: String = currentProject): List<FtthLinkEntity> {
        val sanitized = sanitizeProjectName(project)
        val targetFiles = listOf("$sanitized/liaisons.json", "$sanitized/.liaisons", "liaisons.json")

        for (relPath in targetFiles) {
            val publicContent = PublicStorageHelper.loadPublicDocument(context, relPath)
            if (!publicContent.isNullOrBlank() && publicContent != "[]") {
                try {
                    val jsonArray = JSONArray(publicContent)
                    val list = mutableListOf<FtthLinkEntity>()
                    for (i in 0 until jsonArray.length()) {
                        list.add(linkFromJson(jsonArray.getJSONObject(i)))
                    }
                    if (list.isNotEmpty()) return list
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val searchFiles = listOf(
                File(publicBaseDir, relPath),
                File(appExtBaseDir, relPath),
                File(internalBaseDir, relPath)
            )

            for (file in searchFiles) {
                if (file.exists()) {
                    try {
                        val content = file.readText(Charsets.UTF_8).trim()
                        if (content.isNotEmpty() && content != "[]") {
                            val jsonArray = JSONArray(content)
                            val list = mutableListOf<FtthLinkEntity>()
                            for (i in 0 until jsonArray.length()) {
                                list.add(linkFromJson(jsonArray.getJSONObject(i)))
                            }
                            if (list.isNotEmpty()) return list
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        val prefsJson = prefs.getString("backup_links_json_$sanitized", null)
        if (!prefsJson.isNullOrBlank() && prefsJson != "[]") {
            try {
                val jsonArray = JSONArray(prefsJson)
                val list = mutableListOf<FtthLinkEntity>()
                for (i in 0 until jsonArray.length()) {
                    list.add(linkFromJson(jsonArray.getJSONObject(i)))
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return emptyList()
    }

    fun saveLinks(links: List<FtthLinkEntity>, project: String = currentProject, updateGeoJson: Boolean = true) {
        val sanitized = sanitizeProjectName(project)
        ensureProjectDirectoriesExist(sanitized)
        try {
            val jsonArray = JSONArray()
            links.forEach { jsonArray.put(linkToJson(it)) }
            val jsonString = jsonArray.toString(2)

            PublicStorageHelper.savePublicDocument(context, "$sanitized/liaisons.json", jsonString)
            PublicStorageHelper.savePublicDocument(context, "$sanitized/.liaisons", jsonString)

            for (dir in getWritableDirectories()) {
                try {
                    val projDir = File(dir, sanitized)
                    if (!projDir.exists()) projDir.mkdirs()
                    File(projDir, "liaisons.json").writeText(jsonString, Charsets.UTF_8)
                    File(projDir, ".liaisons").writeText(jsonString, Charsets.UTF_8)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            prefs.edit().putString("backup_links_json_$sanitized", jsonString).apply()

            if (project == currentProject) {
                scope.launch {
                    try {
                        database.ftthDao().clearLinks()
                        if (links.isNotEmpty()) {
                            database.ftthDao().insertLinks(links)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (updateGeoJson) generateAndSaveGeoJson(sanitized)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- CHARGEMENT & SAUVEGARDE DES TRAJETS (.trajets / trajets.json) ---

    fun loadTracks(project: String = currentProject): List<StoredTrack> {
        val sanitized = sanitizeProjectName(project)
        val targetFiles = listOf("$sanitized/trajets.json", "$sanitized/.trajets", "trajets.json")

        for (relPath in targetFiles) {
            val publicContent = PublicStorageHelper.loadPublicDocument(context, relPath)
            if (!publicContent.isNullOrBlank() && publicContent != "[]") {
                try {
                    val jsonArray = JSONArray(publicContent)
                    val list = mutableListOf<StoredTrack>()
                    for (i in 0 until jsonArray.length()) {
                        list.add(StoredTrack.fromJson(jsonArray.getJSONObject(i)))
                    }
                    if (list.isNotEmpty()) return list
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val searchFiles = listOf(
                File(publicBaseDir, relPath),
                File(appExtBaseDir, relPath),
                File(internalBaseDir, relPath)
            )

            for (file in searchFiles) {
                if (file.exists()) {
                    try {
                        val content = file.readText(Charsets.UTF_8).trim()
                        if (content.isNotEmpty() && content != "[]") {
                            val jsonArray = JSONArray(content)
                            val list = mutableListOf<StoredTrack>()
                            for (i in 0 until jsonArray.length()) {
                                list.add(StoredTrack.fromJson(jsonArray.getJSONObject(i)))
                            }
                            if (list.isNotEmpty()) return list
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        val prefsJson = prefs.getString("backup_tracks_json_$sanitized", null)
        if (!prefsJson.isNullOrBlank() && prefsJson != "[]") {
            try {
                val jsonArray = JSONArray(prefsJson)
                val list = mutableListOf<StoredTrack>()
                for (i in 0 until jsonArray.length()) {
                    list.add(StoredTrack.fromJson(jsonArray.getJSONObject(i)))
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return emptyList()
    }

    fun saveTracks(tracks: List<StoredTrack>, project: String = currentProject, updateGeoJson: Boolean = true) {
        val sanitized = sanitizeProjectName(project)
        ensureProjectDirectoriesExist(sanitized)
        try {
            val jsonArray = JSONArray()
            tracks.forEach { jsonArray.put(it.toJson()) }
            val jsonString = jsonArray.toString(2)

            PublicStorageHelper.savePublicDocument(context, "$sanitized/trajets.json", jsonString)
            PublicStorageHelper.savePublicDocument(context, "$sanitized/.trajets", jsonString)

            for (dir in getWritableDirectories()) {
                try {
                    val projDir = File(dir, sanitized)
                    if (!projDir.exists()) projDir.mkdirs()
                    File(projDir, "trajets.json").writeText(jsonString, Charsets.UTF_8)
                    File(projDir, ".trajets").writeText(jsonString, Charsets.UTF_8)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            prefs.edit().putString("backup_tracks_json_$sanitized", jsonString).apply()

            if (updateGeoJson) generateAndSaveGeoJson(sanitized)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- GÉNÉRATION AUTOMATIQUE DU FICHIER GEOJSON DU PROJET ---

    fun generateAndSaveGeoJson(project: String = currentProject) {
        val sanitized = sanitizeProjectName(project)
        try {
            val nodes = loadNodes(sanitized)
            val links = loadLinks(sanitized)
            val tracks = loadTracks(sanitized)

            val root = JSONObject()
            root.put("type", "FeatureCollection")
            root.put("project", sanitized)
            val features = JSONArray()

            val nodesMap = nodes.associateBy { it.id }

            // 1. Nœuds
            for (node in nodes) {
                val feat = JSONObject()
                feat.put("type", "Feature")
                val geom = JSONObject()
                geom.put("type", "Point")
                val coords = JSONArray()
                coords.put(node.longitude)
                coords.put(node.latitude)
                geom.put("coordinates", coords)
                feat.put("geometry", geom)

                val props = JSONObject()
                props.put("id", node.id)
                props.put("name", node.name)
                props.put("type", node.type.name)
                props.put("typeLabel", node.type.label)
                props.put("status", node.status.label)
                props.put("etat", node.etat.label)
                props.put("address", node.address)
                props.put("hasBoitierFtth", node.hasBoitierFtth)
                props.put("notes", node.notes)
                props.put("technician", node.technicianName)
                props.put("updatedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE).format(Date(node.updatedAt)))
                feat.put("properties", props)
                features.put(feat)
            }

            // 2. Liaisons
            for (link in links) {
                val n1 = nodesMap[link.fromNodeId]
                val n2 = nodesMap[link.toNodeId]
                if (n1 != null && n2 != null) {
                    val feat = JSONObject()
                    feat.put("type", "Feature")
                    val geom = JSONObject()
                    geom.put("type", "LineString")
                    val lineCoords = JSONArray()
                    lineCoords.put(JSONArray().apply { put(n1.longitude); put(n1.latitude) })
                    lineCoords.put(JSONArray().apply { put(n2.longitude); put(n2.latitude) })
                    geom.put("coordinates", lineCoords)
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

            // 3. Trajets
            for (track in tracks) {
                val pts = if (track.points.isNotEmpty()) track.points else track.rawPoints
                if (pts.size >= 2) {
                    val feat = JSONObject()
                    feat.put("type", "Feature")
                    val geom = JSONObject()
                    geom.put("type", "LineString")
                    val lineCoords = JSONArray()
                    for (p in pts) {
                        lineCoords.put(JSONArray().apply { put(p.longitude); put(p.latitude) })
                    }
                    geom.put("coordinates", lineCoords)
                    feat.put("geometry", geom)

                    val props = JSONObject()
                    props.put("id", track.id)
                    props.put("name", track.name)
                    props.put("trajetType", track.type)
                    props.put("etatTechnique", track.etat)
                    props.put("conduitAudit", track.conduitAudit)
                    props.put("conduitType", track.conduitType)
                    props.put("conduitCount", track.conduitCount)
                    props.put("conduitDiameters", JSONArray(track.conduitDiameters))
                    props.put("distanceMeters", track.totalDistanceMeters)
                    props.put("pointCount", pts.size)
                    props.put("photoCount", track.photos.size)
                    feat.put("properties", props)
                    features.put(feat)
                }
            }

            root.put("features", features)
            val geoJsonString = root.toString(2)

            PublicStorageHelper.savePublicDocument(context, "$sanitized/releve_terrain.geojson", geoJsonString)

            for (dir in getWritableDirectories()) {
                try {
                    val projDir = File(dir, sanitized)
                    if (!projDir.exists()) projDir.mkdirs()
                    File(projDir, "releve_terrain.geojson").writeText(geoJsonString, Charsets.UTF_8)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- SÉRIALISATION JSON ---

    private fun nodeToJson(node: FtthNodeEntity): JSONObject = JSONObject().apply {
        put("id", node.id)
        put("name", node.name)
        put("type", node.type.name)
        put("latitude", node.latitude)
        put("longitude", node.longitude)
        put("status", node.status.name)
        put("etat", node.etat.name)
        put("address", node.address)
        put("notes", node.notes)
        put("technicianName", node.technicianName)
        put("photoCount", node.photos.size)
        put("updatedAt", node.updatedAt)
        put("hasBoitierFtth", node.hasBoitierFtth)
        val photosArr = JSONArray()
        node.photos.forEach { photosArr.put(it) }
        put("photos", photosArr)

        // Spécifiques
        put("poleNature", node.poleNature)
        put("poleHeight", node.poleHeight)
        put("chamberType", node.chamberType)
        put("boitierType", node.boitierType)
        put("isSaturated", node.isSaturated)
        put("boitierSupport", node.boitierSupport)
        put("hasSplitter", node.hasSplitter)
        put("splitterType", node.splitterType)
        put("sroType", node.sroType)
        put("sroCapacity", node.sroCapacity)
        put("buildingFloors", node.buildingFloors)
        put("buildingDwellings", node.buildingDwellings)
        put("buildingBoitiersEtage", node.buildingBoitiersEtage)
        put("hasLocalTechnique", node.hasLocalTechnique)
        put("hasGaineMontante", node.hasGaineMontante)
        put("syndicAuthorization", node.syndicAuthorization)
        put("syndicContact", node.syndicContact)
        put("buildingConnectionMode", node.buildingConnectionMode)
        put("villaConnectionMode", node.villaConnectionMode)
    }

    private fun nodeFromJson(json: JSONObject): FtthNodeEntity {
        val photosList = mutableListOf<String>()
        val photosArr = json.optJSONArray("photos")
        if (photosArr != null) {
            for (i in 0 until photosArr.length()) {
                photosList.add(photosArr.getString(i))
            }
        }

        val typeStr = json.optString("type", FtthNodeType.POTEAU.name)
        val type = try {
            FtthNodeType.valueOf(typeStr)
        } catch (e: Exception) {
            FtthNodeType.POTEAU
        }

        val statusStr = json.optString("status", NodeStatus.EXISTANT.name)
        val status = try {
            NodeStatus.valueOf(statusStr)
        } catch (e: Exception) {
            NodeStatus.EXISTANT
        }

        val etatStr = json.optString("etat", NodeConformity.CONFORME.name)
        val etat = try {
            NodeConformity.valueOf(etatStr)
        } catch (e: Exception) {
            NodeConformity.CONFORME
        }

        return FtthNodeEntity(
            id = json.optString("id", "NODE-${System.currentTimeMillis()}"),
            name = json.optString("name", "Nœud"),
            type = type,
            latitude = json.optDouble("latitude", 0.0),
            longitude = json.optDouble("longitude", 0.0),
            status = status,
            etat = etat,
            address = json.optString("address", ""),
            photos = photosList,
            notes = json.optString("notes", ""),
            technicianName = json.optString("technicianName", "Technicien"),
            photoCount = photosList.size,
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            syncState = SyncState.SYNCED,
            hasBoitierFtth = json.optBoolean("hasBoitierFtth", false),
            poleNature = json.optString("poleNature", "bois"),
            poleHeight = json.optInt("poleHeight", 8),
            chamberType = json.optString("chamberType", "L2T"),
            boitierType = json.optString("boitierType", "PBO"),
            isSaturated = json.optBoolean("isSaturated", false),
            boitierSupport = json.optString("boitierSupport", "Poteau"),
            hasSplitter = json.optBoolean("hasSplitter", false),
            splitterType = json.optString("splitterType", "1:8"),
            sroType = json.optString("sroType", "armoire de rue"),
            sroCapacity = json.optString("sroCapacity", "360 FO"),
            buildingFloors = json.optInt("buildingFloors", 4),
            buildingDwellings = json.optInt("buildingDwellings", 16),
            buildingBoitiersEtage = json.optInt("buildingBoitiersEtage", 4),
            hasLocalTechnique = json.optBoolean("hasLocalTechnique", true),
            hasGaineMontante = json.optBoolean("hasGaineMontante", true),
            syndicAuthorization = json.optString("syndicAuthorization", "Accord obtenu"),
            syndicContact = json.optString("syndicContact", ""),
            buildingConnectionMode = json.optString("buildingConnectionMode", "souterrain"),
            villaConnectionMode = json.optString("villaConnectionMode", "aérien")
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

    private fun linkFromJson(json: JSONObject): FtthLinkEntity {
        val statusStr = json.optString("status", NodeStatus.EXISTANT.name)
        val status = try {
            NodeStatus.valueOf(statusStr)
        } catch (e: Exception) {
            NodeStatus.EXISTANT
        }

        return FtthLinkEntity(
            id = json.optString("id", "LNK-${System.currentTimeMillis()}"),
            fromNodeId = json.optString("fromNodeId", ""),
            toNodeId = json.optString("toNodeId", ""),
            cableType = json.optString("cableType", "Distribution"),
            installationType = json.optString("installationType", "Aérien"),
            capacityFO = json.optInt("capacityFO", 24),
            lengthMeters = json.optDouble("lengthMeters", 45.0),
            status = status,
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
        )
    }
}
