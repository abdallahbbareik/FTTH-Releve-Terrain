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
                id = json.optString("id", "INF-${System.currentTimeMillis()}"),
                name = json.optString("name", "Infra_lineaire"),
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

    // Dossiers cibles sous Documents/Releve-Terrain et Download/Releve-Terrain
    val publicBaseDir: File
        get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Releve-Terrain")

    val publicDownloadsDir: File
        get() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Releve-Terrain")

    val appExtBaseDir: File
        get() = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Releve-Terrain")

    val internalBaseDir: File
        get() = File(context.filesDir, "Releve-Terrain")

    // Répertoire principal préféré pour Releve-Terrain
    val baseDir: File
        get() {
            ensureDirectoriesExist()
            if (isWritable(publicBaseDir)) return publicBaseDir
            if (isWritable(publicDownloadsDir)) return publicDownloadsDir
            if (isWritable(appExtBaseDir)) return appExtBaseDir
            return internalBaseDir
        }

    fun getProjectDir(projectName: String = currentProject): File {
        val sanitized = sanitizeProjectName(projectName)
        val dir = File(baseDir, sanitized)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getProjectPhotosDir(projectName: String = currentProject): File {
        val dir = File(getProjectDir(projectName), "photos")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    init {
        ensureDirectoriesExist()
        migrateLegacyRootFilesIfNeeded()
        ensureDefaultProjectExists()
    }

    fun sanitizeProjectName(name: String): String {
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
     * Migration automatique à l'installation / démarrage des fichiers situés directement dans la racine Releve-Terrain/ vers projet01
     */
    private fun migrateLegacyRootFilesIfNeeded() {
        try {
            val rootNodesFile = File(publicBaseDir, "noeuds.json")
            val rootLegacyPoints = File(publicBaseDir, "points.json")
            val rootTracksFile = File(publicBaseDir, "infra_lineaire.json")
            val rootLegacyTracks = File(publicBaseDir, "trajets.json")
            val rootLinksFile = File(publicBaseDir, "cables.json")
            val rootLegacyLinks = File(publicBaseDir, "liaisons.json")

            val project01Dir = File(publicBaseDir, "projet01")
            val projNodesFile = File(project01Dir, "noeuds.json")

            val sourceNodes = when {
                rootNodesFile.exists() && rootNodesFile.length() > 2 -> rootNodesFile
                rootLegacyPoints.exists() && rootLegacyPoints.length() > 2 -> rootLegacyPoints
                else -> null
            }

            if (sourceNodes != null && (!projNodesFile.exists() || projNodesFile.length() <= 2)) {
                project01Dir.mkdirs()
                sourceNodes.copyTo(projNodesFile, overwrite = true)
                sourceNodes.copyTo(File(project01Dir, ".noeuds"), overwrite = true)

                val sourceTracks = when {
                    rootTracksFile.exists() -> rootTracksFile
                    rootLegacyTracks.exists() -> rootLegacyTracks
                    else -> null
                }
                sourceTracks?.copyTo(File(project01Dir, "infra_lineaire.json"), overwrite = true)
                sourceTracks?.copyTo(File(project01Dir, ".infra_lineaire"), overwrite = true)

                val sourceLinks = when {
                    rootLinksFile.exists() -> rootLinksFile
                    rootLegacyLinks.exists() -> rootLegacyLinks
                    else -> null
                }
                sourceLinks?.copyTo(File(project01Dir, "cables.json"), overwrite = true)
                sourceLinks?.copyTo(File(project01Dir, ".cables"), overwrite = true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Vérifie et restaure le projet actif sauvegardé lors du démarrage
     */
    private fun ensureDefaultProjectExists() {
        val saved = prefs.getString("active_project_name", null)
        if (!saved.isNullOrBlank()) {
            _currentProject = sanitizeProjectName(saved)
        } else {
            _currentProject = "projet01"
            prefs.edit().putString("active_project_name", "projet01").apply()
        }
    }

    fun ensureDirectoriesExist() {
        listOf(publicBaseDir, publicDownloadsDir, appExtBaseDir, internalBaseDir).forEach { base ->
            try {
                if (!base.exists()) base.mkdirs()
                File(base, "photos").let { if (!it.exists()) it.mkdirs() }
                File(base, "Photos").let { if (!it.exists()) it.mkdirs() }
                File(base, "Exports").let { if (!it.exists()) it.mkdirs() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun ensureProjectDirectoriesExist(projectName: String) {
        val sanitized = sanitizeProjectName(projectName)
        listOf(publicBaseDir, publicDownloadsDir, appExtBaseDir, internalBaseDir).forEach { base ->
            try {
                val projDir = File(base, sanitized)
                if (!projDir.exists()) projDir.mkdirs()
                File(projDir, "photos").let { if (!it.exists()) it.mkdirs() }
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
        if (isWritable(publicDownloadsDir)) list.add(publicDownloadsDir)
        if (isWritable(appExtBaseDir)) list.add(appExtBaseDir)
        if (isWritable(internalBaseDir)) list.add(internalBaseDir)
        return if (list.isNotEmpty()) list else listOf(context.filesDir)
    }

    // --- GESTION DES PROJETS ---

    fun listAllProjects(): List<ProjectInfo> {
        val projectMap = mutableMapOf<String, Long>()
        val deletedProjects = PublicStorageHelper.getDeletedProjects(context)

        // 1. Scanner Documents/Releve-Terrain
        try {
            if (publicBaseDir.exists() && publicBaseDir.isDirectory) {
                publicBaseDir.listFiles()?.forEach { file ->
                    val lower = file.name.lowercase()
                    if (file.isDirectory && lower != "photos" && lower != "exports" && !file.name.startsWith(".") && !deletedProjects.contains(file.name)) {
                        projectMap[file.name] = file.lastModified()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Scanner Download/Releve-Terrain
        try {
            if (publicDownloadsDir.exists() && publicDownloadsDir.isDirectory) {
                publicDownloadsDir.listFiles()?.forEach { file ->
                    val lower = file.name.lowercase()
                    if (file.isDirectory && lower != "photos" && lower != "exports" && !file.name.startsWith(".") && !deletedProjects.contains(file.name)) {
                        projectMap.putIfAbsent(file.name, file.lastModified())
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Scanner le stockage externe privé de l'app
        try {
            if (appExtBaseDir.exists() && appExtBaseDir.isDirectory) {
                appExtBaseDir.listFiles()?.forEach { file ->
                    val lower = file.name.lowercase()
                    if (file.isDirectory && lower != "photos" && lower != "exports" && !file.name.startsWith(".") && !deletedProjects.contains(file.name)) {
                        projectMap.putIfAbsent(file.name, file.lastModified())
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Scanner via MediaStore (secours persistant après réinstallation)
        val mediaStoreProjects = PublicStorageHelper.listPublicProjects(context)
        for (p in mediaStoreProjects) {
            if (!deletedProjects.contains(p)) {
                projectMap.putIfAbsent(p, System.currentTimeMillis())
            }
        }

        projectMap.keys.removeAll(deletedProjects)

        if (projectMap.isEmpty()) {
            PublicStorageHelper.unmarkProjectDeleted(context, "projet01")
            projectMap["projet01"] = System.currentTimeMillis()
        }

        val result = mutableListOf<ProjectInfo>()
        for ((name, lastMod) in projectMap) {
            var nCount = 0
            var tCount = 0
            var lCount = 0

            val projDir = File(baseDir, name)
            val nf = File(projDir, "noeuds.json")
            if (nf.exists() && nf.length() > 2) {
                try { nCount = JSONArray(nf.readText(Charsets.UTF_8)).length() } catch (ignored: Exception) {}
            }
            val tf = File(projDir, "infra_lineaire.json")
            if (tf.exists() && tf.length() > 2) {
                try { tCount = JSONArray(tf.readText(Charsets.UTF_8)).length() } catch (ignored: Exception) {}
            }
            val lf = File(projDir, "cables.json")
            if (lf.exists() && lf.length() > 2) {
                try { lCount = JSONArray(lf.readText(Charsets.UTF_8)).length() } catch (ignored: Exception) {}
            }

            if (name == _currentProject && nCount == 0 && tCount == 0 && lCount == 0) {
                nCount = loadNodesForProject(name).size
                tCount = loadTracksForProject(name).size
                lCount = loadLinksForProject(name).size
            }

            result.add(
                ProjectInfo(
                    name = name,
                    nodesCount = nCount,
                    tracksCount = tCount,
                    linksCount = lCount,
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
        PublicStorageHelper.unmarkProjectDeleted(context, sanitized)

        val currentNodes = if (copyCurrent) loadNodes() else emptyList()
        val currentLinks = if (copyCurrent) loadLinks() else emptyList()
        val currentTracks = if (copyCurrent) loadTracks() else emptyList()

        if (copyCurrent) {
            saveNodes(currentNodes, project = sanitized, updateGeoJson = false)
            saveLinks(currentLinks, project = sanitized, updateGeoJson = false)
            saveTracks(currentTracks, project = sanitized, updateGeoJson = false)
        } else {
            // Créer fichiers vides initiaux pour ce nouveau sous-projet
            saveNodes(emptyList(), project = sanitized, updateGeoJson = false)
            saveLinks(emptyList(), project = sanitized, updateGeoJson = false)
            saveTracks(emptyList(), project = sanitized, updateGeoJson = false)
        }
        generateAndSaveGeoJson(project = sanitized)

        setCurrentProject(sanitized)
        return true
    }

    fun deleteProject(projectName: String): Boolean {
        val sanitized = sanitizeProjectName(projectName)

        // Marquer comme supprimé pour filtrage immédiat
        PublicStorageHelper.markProjectDeleted(context, sanitized)
        PublicStorageHelper.markProjectDeleted(context, projectName)

        // 1. Supprimer sur tous les dossiers physiques (Releve-Terrain et releve-terrain)
        listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            context.filesDir
        ).forEach { baseDir ->
            listOf("Releve-Terrain", "releve-terrain").forEach { subDirName ->
                try {
                    val targetBase = File(baseDir, subDirName)
                    File(targetBase, sanitized).let { if (it.exists()) it.deleteRecursively() }
                    File(targetBase, projectName).let { if (it.exists()) it.deleteRecursively() }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 2. Supprimer via MediaStore
        PublicStorageHelper.deletePublicProject(context, sanitized)
        PublicStorageHelper.deletePublicProject(context, projectName)

        // 3. Nettoyer les clés SharedPreferences pour ce projet
        prefs.edit()
            .remove("backup_nodes_json_$sanitized")
            .remove("backup_tracks_json_$sanitized")
            .remove("backup_links_json_$sanitized")
            .remove("backup_nodes_json_$projectName")
            .remove("backup_tracks_json_$projectName")
            .remove("backup_links_json_$projectName")
            .commit()

        // 4. Si c'était le projet courant, basculer sur un autre projet existant
        if (_currentProject.equals(sanitized, ignoreCase = true) || _currentProject.equals(projectName, ignoreCase = true)) {
            val remaining = listAllProjects().filter {
                !it.name.equals(sanitized, ignoreCase = true) && !it.name.equals(projectName, ignoreCase = true)
            }
            if (remaining.isNotEmpty()) {
                setCurrentProject(remaining.first().name)
            } else {
                _currentProject = "projet01"
                PublicStorageHelper.unmarkProjectDeleted(context, "projet01")
                prefs.edit().putString("active_project_name", "projet01").commit()
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

        listOf(publicBaseDir, publicDownloadsDir, appExtBaseDir, internalBaseDir).forEach { base ->
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

    // --- CHARGEMENT & SAUVEGARDE DES NOEUDS (.noeuds / noeuds.json) ---

    fun loadNodes(project: String = currentProject): List<FtthNodeEntity> {
        return loadNodesForProject(project)
    }

    private fun loadNodesForProject(project: String): List<FtthNodeEntity> {
        val sanitized = sanitizeProjectName(project)
        val targetFiles = mutableListOf(
            "$sanitized/noeuds.json",
            "$sanitized/.noeuds",
            "$sanitized/points.json",
            "$sanitized/.points"
        )
        if (sanitized == "projet01") {
            targetFiles.add("noeuds.json")
            targetFiles.add("points.json")
            targetFiles.add(".noeuds")
            targetFiles.add(".points")
        }

        for (relPath in targetFiles) {
            val publicContent = PublicStorageHelper.loadPublicDocument(context, relPath)
            if (!publicContent.isNullOrBlank() && publicContent.trim() != "[]") {
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

            val searchFiles = listOf(
                File(publicBaseDir, relPath),
                File(publicDownloadsDir, relPath),
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

        val prefsJson = prefs.getString("backup_nodes_json_$sanitized", null)
        if (!prefsJson.isNullOrBlank() && prefsJson.trim() != "[]") {
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

            PublicStorageHelper.savePublicDocument(context, "$sanitized/noeuds.json", jsonString)
            PublicStorageHelper.savePublicDocument(context, "$sanitized/.noeuds", jsonString)

            for (dir in getWritableDirectories()) {
                try {
                    val projDir = File(dir, sanitized)
                    if (!projDir.exists()) projDir.mkdirs()
                    File(projDir, "noeuds.json").writeText(jsonString, Charsets.UTF_8)
                    File(projDir, ".noeuds").writeText(jsonString, Charsets.UTF_8)
                    File(projDir, "points.json").writeText(jsonString, Charsets.UTF_8)
                    File(projDir, ".points").writeText(jsonString, Charsets.UTF_8)
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

            // Génération immédiate du GeoJSON dédié aux nœuds du projet
            generateAndSaveNodesGeoJson(nodes, sanitized)
            if (updateGeoJson) {
                generateAndSaveLinksGeoJson(loadLinks(sanitized), sanitized, nodes)
                generateAndSaveGeoJson(sanitized)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- CHARGEMENT & SAUVEGARDE DES CÂBLES (.cables / cables.json) ---

    fun loadLinks(project: String = currentProject): List<FtthLinkEntity> {
        return loadLinksForProject(project)
    }

    private fun loadLinksForProject(project: String): List<FtthLinkEntity> {
        val sanitized = sanitizeProjectName(project)
        val targetFiles = mutableListOf(
            "$sanitized/cables.json",
            "$sanitized/.cables",
            "$sanitized/liaisons.json",
            "$sanitized/.liaisons"
        )
        if (sanitized == "projet01") {
            targetFiles.add("cables.json")
            targetFiles.add("liaisons.json")
            targetFiles.add(".cables")
            targetFiles.add(".liaisons")
        }

        for (relPath in targetFiles) {
            val publicContent = PublicStorageHelper.loadPublicDocument(context, relPath)
            if (!publicContent.isNullOrBlank() && publicContent.trim() != "[]") {
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
                File(publicDownloadsDir, relPath),
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
        if (!prefsJson.isNullOrBlank() && prefsJson.trim() != "[]") {
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

            PublicStorageHelper.savePublicDocument(context, "$sanitized/cables.json", jsonString)
            PublicStorageHelper.savePublicDocument(context, "$sanitized/.cables", jsonString)

            for (dir in getWritableDirectories()) {
                try {
                    val projDir = File(dir, sanitized)
                    if (!projDir.exists()) projDir.mkdirs()
                    File(projDir, "cables.json").writeText(jsonString, Charsets.UTF_8)
                    File(projDir, ".cables").writeText(jsonString, Charsets.UTF_8)
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

            // Génération immédiate du GeoJSON dédié aux câbles du projet
            generateAndSaveLinksGeoJson(links, sanitized)
            if (updateGeoJson) generateAndSaveGeoJson(sanitized)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- CHARGEMENT & SAUVEGARDE DES INFRA_LINEAIRE (.infra_lineaire / infra_lineaire.json) ---

    fun loadTracks(project: String = currentProject): List<StoredTrack> {
        return loadTracksForProject(project)
    }

    private fun loadTracksForProject(project: String): List<StoredTrack> {
        val sanitized = sanitizeProjectName(project)
        val targetFiles = mutableListOf(
            "$sanitized/infra_lineaire.json",
            "$sanitized/.infra_lineaire",
            "$sanitized/trajets.json",
            "$sanitized/.trajets"
        )
        if (sanitized == "projet01") {
            targetFiles.add("infra_lineaire.json")
            targetFiles.add("trajets.json")
            targetFiles.add(".infra_lineaire")
            targetFiles.add(".trajets")
        }

        for (relPath in targetFiles) {
            val publicContent = PublicStorageHelper.loadPublicDocument(context, relPath)
            if (!publicContent.isNullOrBlank() && publicContent.trim() != "[]") {
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
                File(publicDownloadsDir, relPath),
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
        if (!prefsJson.isNullOrBlank() && prefsJson.trim() != "[]") {
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

            PublicStorageHelper.savePublicDocument(context, "$sanitized/infra_lineaire.json", jsonString)
            PublicStorageHelper.savePublicDocument(context, "$sanitized/.infra_lineaire", jsonString)

            for (dir in getWritableDirectories()) {
                try {
                    val projDir = File(dir, sanitized)
                    if (!projDir.exists()) projDir.mkdirs()
                    File(projDir, "infra_lineaire.json").writeText(jsonString, Charsets.UTF_8)
                    File(projDir, ".infra_lineaire").writeText(jsonString, Charsets.UTF_8)
                    File(projDir, "trajets.json").writeText(jsonString, Charsets.UTF_8)
                    File(projDir, ".trajets").writeText(jsonString, Charsets.UTF_8)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            prefs.edit().putString("backup_tracks_json_$sanitized", jsonString).apply()

            // Génération immédiate du GeoJSON dédié aux infras linéaires du projet
            generateAndSaveTracksGeoJson(tracks, sanitized)
            if (updateGeoJson) generateAndSaveGeoJson(sanitized)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- GÉNÉRATION AUTOMATIQUE DES FICHIERS GEOJSON DÉDIÉS PAR TYPE D'ENTITÉS ---

    /**
     * Supprime tout fichier GeoJSON individuel résiduel pour garantir strictement un fichier par type d'entités
     */
    private fun cleanupIndividualGeoJsonFiles(sanitized: String) {
        val allowedGeoJsonNames = setOf("noeuds.geojson", "infra_lineaire.geojson", "cables.geojson", "releve_$sanitized.geojson")
        val allBases = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "releve-terrain"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Releve-Terrain"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "releve-terrain"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Releve-Terrain"),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "releve-terrain"),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Releve-Terrain"),
            File(context.filesDir, "releve-terrain"),
            File(context.filesDir, "Releve-Terrain")
        )
        for (dir in allBases) {
            try {
                val projDir = File(dir, sanitized)
                if (projDir.exists() && projDir.isDirectory) {
                    projDir.listFiles()?.forEach { file ->
                        if (file.name.endsWith(".geojson") && !allowedGeoJsonNames.contains(file.name)) {
                            file.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Génère et sauvegarde le fichier GeoJSON unique pour tous les NŒUDS du projet : noeuds.geojson
     */
    fun generateAndSaveNodesGeoJson(nodes: List<FtthNodeEntity>, project: String = currentProject) {
        val sanitized = sanitizeProjectName(project)
        try {
            cleanupIndividualGeoJsonFiles(sanitized)
            val root = JSONObject()
            root.put("type", "FeatureCollection")
            root.put("name", "noeuds")
            root.put("project", sanitized)
            root.put("updatedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.FRANCE).format(Date()))

            val features = JSONArray()
            for (node in nodes) {
                val feat = JSONObject()
                feat.put("type", "Feature")
                val geom = JSONObject().apply {
                    put("type", "Point")
                    val coords = JSONArray()
                    coords.put(node.longitude)
                    coords.put(node.latitude)
                    put("coordinates", coords)
                }
                feat.put("geometry", geom)
                feat.put("properties", nodeToJson(node).apply { put("entity_type", "noeud") })
                features.put(feat)
            }
            root.put("features", features)
            val geoJsonString = root.toString(2)

            PublicStorageHelper.savePublicDocument(context, "$sanitized/noeuds.geojson", geoJsonString)
            for (dir in getWritableDirectories()) {
                try {
                    val projDir = File(dir, sanitized)
                    if (!projDir.exists()) projDir.mkdirs()
                    File(projDir, "noeuds.geojson").writeText(geoJsonString, Charsets.UTF_8)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Génère et sauvegarde le fichier GeoJSON unique pour toutes les INFRASTRUCTURES LINÉAIRES du projet : infra_lineaire.geojson
     */
    fun generateAndSaveTracksGeoJson(tracks: List<StoredTrack>, project: String = currentProject) {
        val sanitized = sanitizeProjectName(project)
        try {
            cleanupIndividualGeoJsonFiles(sanitized)
            val root = JSONObject()
            root.put("type", "FeatureCollection")
            root.put("name", "infra_lineaire")
            root.put("project", sanitized)
            root.put("updatedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.FRANCE).format(Date()))

            val features = JSONArray()
            for (track in tracks) {
                val pts = if (track.points.isNotEmpty()) track.points else track.rawPoints
                if (pts.size >= 2) {
                    val feat = JSONObject()
                    feat.put("type", "Feature")
                    val geom = JSONObject().apply {
                        put("type", "LineString")
                        val coords = JSONArray()
                        for (p in pts) {
                            coords.put(JSONArray().put(p.longitude).put(p.latitude))
                        }
                        put("coordinates", coords)
                    }
                    feat.put("geometry", geom)
                    feat.put("properties", track.toJson().apply { put("entity_type", "infra_lineaire") })
                    features.put(feat)
                }
            }
            root.put("features", features)
            val geoJsonString = root.toString(2)

            PublicStorageHelper.savePublicDocument(context, "$sanitized/infra_lineaire.geojson", geoJsonString)
            for (dir in getWritableDirectories()) {
                try {
                    val projDir = File(dir, sanitized)
                    if (!projDir.exists()) projDir.mkdirs()
                    File(projDir, "infra_lineaire.geojson").writeText(geoJsonString, Charsets.UTF_8)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Génère et sauvegarde le fichier GeoJSON unique pour tous les CÂBLES du projet : cables.geojson
     */
    fun generateAndSaveLinksGeoJson(links: List<FtthLinkEntity>, project: String = currentProject, nodes: List<FtthNodeEntity>? = null) {
        val sanitized = sanitizeProjectName(project)
        try {
            cleanupIndividualGeoJsonFiles(sanitized)
            val currentNodes = nodes ?: loadNodes(sanitized)
            val nodesMap = currentNodes.associateBy { it.id }

            val root = JSONObject()
            root.put("type", "FeatureCollection")
            root.put("name", "cables")
            root.put("project", sanitized)
            root.put("updatedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.FRANCE).format(Date()))

            val features = JSONArray()
            for (link in links) {
                val n1 = nodesMap[link.fromNodeId]
                val n2 = nodesMap[link.toNodeId]
                if (n1 != null && n2 != null) {
                    val feat = JSONObject()
                    feat.put("type", "Feature")
                    val geom = JSONObject().apply {
                        put("type", "LineString")
                        val coords = JSONArray()
                        coords.put(JSONArray().put(n1.longitude).put(n1.latitude))
                        coords.put(JSONArray().put(n2.longitude).put(n2.latitude))
                        put("coordinates", coords)
                    }
                    feat.put("geometry", geom)
                    feat.put("properties", linkToJson(link).apply { put("entity_type", "cable") })
                    features.put(feat)
                }
            }
            root.put("features", features)
            val geoJsonString = root.toString(2)

            PublicStorageHelper.savePublicDocument(context, "$sanitized/cables.geojson", geoJsonString)
            for (dir in getWritableDirectories()) {
                try {
                    val projDir = File(dir, sanitized)
                    if (!projDir.exists()) projDir.mkdirs()
                    File(projDir, "cables.geojson").writeText(geoJsonString, Charsets.UTF_8)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun generateAndSaveGeoJson(project: String = currentProject) {
        val sanitized = sanitizeProjectName(project)
        try {
            val nodes = loadNodes(sanitized)
            val links = loadLinks(sanitized)
            val tracks = loadTracks(sanitized)

            // 1. Mettre à jour chaque fichier GeoJSON spécifique
            generateAndSaveNodesGeoJson(nodes, sanitized)
            generateAndSaveTracksGeoJson(tracks, sanitized)
            generateAndSaveLinksGeoJson(links, sanitized, nodes)

            // 2. Générer également le GeoJSON combiné complet du projet
            val nodesMap = nodes.associateBy { it.id }

            val root = JSONObject()
            root.put("type", "FeatureCollection")
            root.put("project", sanitized)
            root.put("updatedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.FRANCE).format(Date()))
            val features = JSONArray()

            // Nœuds
            for (node in nodes) {
                val feat = JSONObject()
                feat.put("type", "Feature")
                val geom = JSONObject().apply {
                    put("type", "Point")
                    val coords = JSONArray()
                    coords.put(node.longitude)
                    coords.put(node.latitude)
                    put("coordinates", coords)
                }
                feat.put("geometry", geom)
                feat.put("properties", nodeToJson(node).apply { put("entity_type", "noeud") })
                features.put(feat)
            }

            // Câbles
            for (link in links) {
                val n1 = nodesMap[link.fromNodeId]
                val n2 = nodesMap[link.toNodeId]
                if (n1 != null && n2 != null) {
                    val feat = JSONObject()
                    feat.put("type", "Feature")
                    val geom = JSONObject().apply {
                        put("type", "LineString")
                        val coords = JSONArray()
                        coords.put(JSONArray().put(n1.longitude).put(n1.latitude))
                        coords.put(JSONArray().put(n2.longitude).put(n2.latitude))
                        put("coordinates", coords)
                    }
                    feat.put("geometry", geom)
                    feat.put("properties", linkToJson(link).apply { put("entity_type", "cable") })
                    features.put(feat)
                }
            }

            // Infra_lineaire
            for (track in tracks) {
                val pts = if (track.points.isNotEmpty()) track.points else track.rawPoints
                if (pts.size >= 2) {
                    val feat = JSONObject()
                    feat.put("type", "Feature")
                    val geom = JSONObject().apply {
                        put("type", "LineString")
                        val coords = JSONArray()
                        for (p in pts) {
                            coords.put(JSONArray().put(p.longitude).put(p.latitude))
                        }
                        put("coordinates", coords)
                    }
                    feat.put("geometry", geom)
                    feat.put("properties", track.toJson().apply { put("entity_type", "infra_lineaire") })
                    features.put(feat)
                }
            }

            root.put("features", features)
            val geoJsonString = root.toString(2)

            PublicStorageHelper.savePublicDocument(context, "$sanitized/releve_$sanitized.geojson", geoJsonString)

            for (dir in getWritableDirectories()) {
                try {
                    val projDir = File(dir, sanitized)
                    if (!projDir.exists()) projDir.mkdirs()
                    File(projDir, "releve_$sanitized.geojson").writeText(geoJsonString, Charsets.UTF_8)
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
        put("hasBoitierFtth", node.hasBoitierFtth)
        put("notes", node.notes)
        put("technicianName", node.technicianName)
        put("updatedAt", node.updatedAt)

        val phoArr = JSONArray()
        node.photos.forEach { phoArr.put(it) }
        put("photos", phoArr)

        put("poleNature", node.poleNature)
        put("poleHeight", node.poleHeight)
        put("chamberType", node.chamberType)
        put("boitierType", node.boitierType)
        put("isSaturated", node.isSaturated)
        put("boitierSupport", node.boitierSupport)
        put("splitterType", node.splitterType)
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

    private fun nodeFromJson(json: JSONObject): FtthNodeEntity {
        val photosList = mutableListOf<String>()
        val phoArr = json.optJSONArray("photos")
        if (phoArr != null) {
            for (i in 0 until phoArr.length()) {
                photosList.add(phoArr.getString(i))
            }
        }

        val typeStr = json.optString("type", FtthNodeType.POTEAU.name)
        val type = try { FtthNodeType.valueOf(typeStr) } catch (e: Exception) { FtthNodeType.POTEAU }

        val statusStr = json.optString("status", NodeStatus.EXISTANT.name)
        val status = try { NodeStatus.valueOf(statusStr) } catch (e: Exception) { NodeStatus.EXISTANT }

        val etatStr = json.optString("etat", NodeConformity.CONFORME.name)
        val etat = try { NodeConformity.valueOf(etatStr) } catch (e: Exception) { NodeConformity.CONFORME }

        return FtthNodeEntity(
            id = json.optString("id", "NODE-${System.currentTimeMillis() % 10000}"),
            name = json.optString("name", ""),
            type = type,
            latitude = json.optDouble("latitude", 0.0),
            longitude = json.optDouble("longitude", 0.0),
            status = status,
            etat = etat,
            address = json.optString("address", ""),
            hasBoitierFtth = json.optBoolean("hasBoitierFtth", false),
            notes = json.optString("notes", ""),
            photos = photosList,
            technicianName = json.optString("technicianName", ""),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            poleNature = json.optString("poleNature", "Bois"),
            poleHeight = json.optInt("poleHeight", 8),
            chamberType = json.optString("chamberType", "L1T"),
            boitierType = json.optString("boitierType", "PBO"),
            isSaturated = json.optBoolean("isSaturated", false),
            boitierSupport = json.optString("boitierSupport", "Façade"),
            splitterType = json.optString("splitterType", "1:8"),
            sroType = json.optString("sroType", "Armoire"),
            sroCapacity = json.optString("sroCapacity", "160 FO"),
            buildingFloors = json.optInt("buildingFloors", 1),
            buildingDwellings = json.optInt("buildingDwellings", 1),
            hasLocalTechnique = json.optBoolean("hasLocalTechnique", false),
            hasGaineMontante = json.optBoolean("hasGaineMontante", false),
            syndicAuthorization = json.optString("syndicAuthorization", "Non"),
            syndicContact = json.optString("syndicContact", ""),
            buildingConnectionMode = json.optString("buildingConnectionMode", "Souterrain"),
            villaConnectionMode = json.optString("villaConnectionMode", "Aérien")
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
        val status = try { NodeStatus.valueOf(statusStr) } catch (e: Exception) { NodeStatus.EXISTANT }
        return FtthLinkEntity(
            id = json.optString("id", "LNK-${System.currentTimeMillis() % 10000}"),
            fromNodeId = json.optString("fromNodeId", ""),
            toNodeId = json.optString("toNodeId", ""),
            cableType = json.optString("cableType", "Distribution"),
            installationType = json.optString("installationType", "Aérien"),
            capacityFO = json.optInt("capacityFO", 12),
            lengthMeters = json.optDouble("lengthMeters", 0.0),
            status = status,
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
        )
    }
}
