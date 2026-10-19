package com.example.data.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

object PublicStorageHelper {

    fun getPathFromTreeUri(context: Context, uri: Uri): String {
        try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            if (docId != null) {
                val parts = docId.split(":")
                val type = parts[0]
                val relativePath = if (parts.size > 1) parts[1] else ""
                if ("primary".equals(type, ignoreCase = true)) {
                    val base = Environment.getExternalStorageDirectory()
                    return if (relativePath.isNotEmpty()) File(base, relativePath).absolutePath else base.absolutePath
                } else {
                    val sdCard = File("/storage/$type")
                    if (sdCard.exists()) {
                        return if (relativePath.isNotEmpty()) File(sdCard, relativePath).absolutePath else sdCard.absolutePath
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return uri.path ?: uri.toString()
    }

    private const val FOLDER_DOWNLOADS = "Download/Releve-Terrain"
    private const val FOLDER_DOWNLOADS_LOWER = "Download/releve-terrain"
    const val DIR_NAME = "Releve-Terrain"
    const val DIR_NAME_LOWER = "releve-terrain"
    fun getDownloadFolderName(context: Context? = null): String = DIR_NAME
    private const val PREFS_NAME = "ReleveTerrainDeletedProjects"

    private fun getDeletedProjects(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet("deleted_projects", emptySet()) ?: emptySet()
    }

    fun markProjectDeleted(context: Context, projectName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet("deleted_projects", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(projectName)
        prefs.edit().putStringSet("deleted_projects", current).apply()
    }

    fun unmarkProjectDeleted(context: Context, projectName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet("deleted_projects", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.remove(projectName)) {
            prefs.edit().putStringSet("deleted_projects", current).apply()
        }
    }

    /**
     * Sauvegarde un fichier texte dans Download/Releve-Terrain/<relativePath>
     * et miroirs, stockage local de l'application, et MediaStore.
     */
    fun savePublicDocument(context: Context, relativePath: String, content: String): Boolean {
        var fileSuccess = false
        val normalized = relativePath.trimStart('/')

        val parts = normalized.split("/")
        if (parts.size > 1) {
            val proj = parts[0]
            unmarkProjectDeleted(context, proj)
        }

        // 1. Sauvegarde File API dans Download/releve-terrain et Download/Releve-Terrain
        val downloadDirs = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME_LOWER)
        )
        for (pubDown in downloadDirs) {
            try {
                val fileDownloads = File(pubDown, normalized)
                fileDownloads.parentFile?.let { if (!it.exists()) it.mkdirs() }
                fileDownloads.writeText(content, Charsets.UTF_8)
                if (fileDownloads.exists() && fileDownloads.length() > 0) fileSuccess = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Sauvegarde dans les répertoires de l'application
        val appDirs = listOf(
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DIR_NAME),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DIR_NAME_LOWER),
            File(context.filesDir, DIR_NAME),
            File(context.filesDir, DIR_NAME_LOWER)
        )
        for (appDir in appDirs) {
            try {
                val fileExt = File(appDir, normalized)
                fileExt.parentFile?.let { if (!it.exists()) it.mkdirs() }
                fileExt.writeText(content, Charsets.UTF_8)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Sauvegarde via MediaStore sous Download/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")

                val targetFileObj = File(normalized)
                val fileName = targetFileObj.name
                val parentSubDir = targetFileObj.parent?.let { "/$it" } ?: ""

                val folderVariants = listOf(FOLDER_DOWNLOADS, FOLDER_DOWNLOADS_LOWER)
                for (folderVariant in folderVariants) {
                    val fullRelativePath = "$folderVariant$parentSubDir/"
                    val normRelativePath = fullRelativePath.trimEnd('/')

                    val projection = arrayOf(MediaStore.Files.FileColumns._ID)
                    val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND (${MediaStore.Files.FileColumns.RELATIVE_PATH} = ? OR ${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?)"
                    val selectionArgs = arrayOf(fileName, "$normRelativePath/", normRelativePath)

                    var existingUri: Uri? = null
                    resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                            existingUri = ContentUris.withAppendedId(collection, id)
                        }
                    }

                    val targetUri = existingUri ?: run {
                        val mimeType = when {
                            fileName.endsWith(".json") || fileName.startsWith(".") -> "application/json"
                            fileName.endsWith(".geojson") -> "application/geo+json"
                            fileName.endsWith(".kml") -> "application/vnd.google-earth.kml+xml"
                            fileName.endsWith(".csv") -> "text/csv"
                            else -> "text/plain"
                        }
                        val values = ContentValues().apply {
                            put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
                            put(MediaStore.Files.FileColumns.RELATIVE_PATH, fullRelativePath)
                            put(MediaStore.Files.FileColumns.IS_PENDING, 1)
                        }
                        resolver.insert(collection, values)
                    }

                    if (targetUri != null) {
                        resolver.openOutputStream(targetUri, "wt")?.use { output ->
                            output.write(content.toByteArray(Charsets.UTF_8))
                            output.flush()
                        }
                        if (existingUri == null) {
                            val updateValues = ContentValues().apply {
                                put(MediaStore.Files.FileColumns.IS_PENDING, 0)
                            }
                            resolver.update(targetUri, updateValues, null, null)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return fileSuccess
    }

    /**
     * Sauvegarde une photo binaire dans Download/Releve-Terrain/<projet>/photos/
     */
    fun savePublicPhoto(context: Context, relativePath: String, sourceFile: File): String? {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) return null
        val normalized = relativePath.trimStart('/')
        val parts = normalized.split("/")
        val proj = if (parts.size > 1) parts[0] else "projet01"
        val fileName = File(normalized).name

        unmarkProjectDeleted(context, proj)

        val scannedPaths = mutableListOf<String>()
        var preferredPath: String? = null

        // 1. Download
        val targetDownloadFolders = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "releve-terrain/$proj/photos"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Releve-Terrain/$proj/photos")
        )
        for (folder in targetDownloadFolders) {
            try {
                if (!folder.exists()) folder.mkdirs()
                val destFile = File(folder, fileName)
                sourceFile.copyTo(destFile, overwrite = true)
                if (destFile.exists() && destFile.length() > 0) {
                    if (preferredPath == null) preferredPath = destFile.absolutePath
                    scannedPaths.add(destFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. App internal/external folders
        val targetAppFolders = listOf(
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "releve-terrain/$proj/photos"),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Releve-Terrain/$proj/photos"),
            File(context.filesDir, "releve-terrain/$proj/photos"),
            File(context.filesDir, "Releve-Terrain/$proj/photos")
        )
        for (folder in targetAppFolders) {
            try {
                if (!folder.exists()) folder.mkdirs()
                val destFile = File(folder, fileName)
                sourceFile.copyTo(destFile, overwrite = true)
                if (destFile.exists() && destFile.length() > 0) {
                    scannedPaths.add(destFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. MediaStore Download
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                val mediaDownloadPaths = listOf(
                    "Download/releve-terrain/$proj/photos/",
                    "Download/Releve-Terrain/$proj/photos/"
                )
                for (targetRelPath in mediaDownloadPaths) {
                    val normRelPath = targetRelPath.trimEnd('/')
                    val projection = arrayOf(MediaStore.Files.FileColumns._ID)
                    val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND (${MediaStore.Files.FileColumns.RELATIVE_PATH} = ? OR ${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?)"
                    val selectionArgs = arrayOf(fileName, "$normRelPath/", normRelPath)

                    var targetUri: Uri? = null
                    resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                            targetUri = ContentUris.withAppendedId(collection, id)
                        }
                    }

                    if (targetUri == null) {
                        val values = ContentValues().apply {
                            put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.Files.FileColumns.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Files.FileColumns.RELATIVE_PATH, targetRelPath)
                            put(MediaStore.Files.FileColumns.IS_PENDING, 1)
                        }
                        targetUri = resolver.insert(collection, values)
                    }

                    if (targetUri != null) {
                        resolver.openOutputStream(targetUri, "wt")?.use { outStream ->
                            sourceFile.inputStream().use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        val updateValues = ContentValues().apply {
                            put(MediaStore.Files.FileColumns.IS_PENDING, 0)
                        }
                        resolver.update(targetUri, updateValues, null, null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            scannedPaths.add(sourceFile.absolutePath)
            MediaScannerConnection.scanFile(
                context,
                scannedPaths.distinct().toTypedArray(),
                arrayOf("image/jpeg"),
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return preferredPath ?: sourceFile.absolutePath
    }

    /**
     * Lit un fichier depuis Download/Releve-Terrain/<relativePath>
     */
    fun loadPublicDocument(context: Context, relativePath: String): String? {
        val normalized = relativePath.trimStart('/')

        val downloadBases = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME_LOWER),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME)
        )
        for (pubDownloads in downloadBases) {
            try {
                val file = File(pubDownloads, normalized)
                if (file.exists() && file.canRead()) {
                    val text = file.readText(Charsets.UTF_8).trim()
                    if (text.isNotEmpty() && text != "[]") return text
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val appBases = listOf(
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DIR_NAME_LOWER),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DIR_NAME),
            File(context.filesDir, DIR_NAME_LOWER),
            File(context.filesDir, DIR_NAME)
        )
        for (appBase in appBases) {
            try {
                val file = File(appBase, normalized)
                if (file.exists() && file.canRead()) {
                    val text = file.readText(Charsets.UTF_8).trim()
                    if (text.isNotEmpty() && text != "[]") return text
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return null
    }

    /**
     * Découvre tous les sous-dossiers de projets dans Download (releve-terrain et Releve-Terrain).
     */
    fun listPublicProjects(context: Context): List<String> {
        val projectNames = mutableSetOf<String>()
        val deletedProjects = getDeletedProjects(context)

        fun checkAndAdd(file: File) {
            val name = file.name
            val nameLower = name.lowercase()
            if (file.isDirectory &&
                nameLower != "photos" && nameLower != "photo" &&
                nameLower != "exports" && nameLower != "export" &&
                !name.startsWith(".")
            ) {
                if (!deletedProjects.contains(name)) {
                    projectNames.add(name)
                }
            }
        }

        val downloadBases = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME_LOWER),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME)
        )
        for (baseDir in downloadBases) {
            try {
                if (baseDir.exists() && baseDir.isDirectory) {
                    baseDir.listFiles()?.forEach { checkAndAdd(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val appBases = listOf(
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DIR_NAME_LOWER),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DIR_NAME),
            File(context.filesDir, DIR_NAME_LOWER),
            File(context.filesDir, DIR_NAME)
        )
        for (appBase in appBases) {
            try {
                if (appBase.exists() && appBase.isDirectory) {
                    appBase.listFiles()?.forEach { checkAndAdd(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return projectNames.toList().sorted()
    }

    fun deletePublicProject(context: Context, projectName: String): Boolean {
        val sanitized = projectName.trim().replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        if (sanitized.isBlank()) return false

        markProjectDeleted(context, sanitized)
        var deletedAny = false

        listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        ).forEach { baseDir ->
            listOf(DIR_NAME_LOWER, DIR_NAME).forEach { subDirName ->
                try {
                    val projDir = File(File(baseDir, subDirName), sanitized)
                    if (projDir.exists()) {
                        projDir.deleteRecursively()
                        deletedAny = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        listOf(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            context.filesDir
        ).forEach { baseDir ->
            if (baseDir != null) {
                listOf(DIR_NAME_LOWER, DIR_NAME).forEach { subDirName ->
                    try {
                        val projDir = File(File(baseDir, subDirName), sanitized)
                        if (projDir.exists()) {
                            projDir.deleteRecursively()
                            deletedAny = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        return deletedAny
    }

    fun renamePublicProject(context: Context, oldProject: String, newProject: String): Boolean {
        val oldSanitized = oldProject.trim().replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        val newSanitized = newProject.trim().replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        if (oldSanitized.isBlank() || newSanitized.isBlank() || oldSanitized == newSanitized) return false

        unmarkProjectDeleted(context, newSanitized)
        var renamedAny = false

        listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            context.filesDir
        ).forEach { baseDir ->
            if (baseDir != null) {
                listOf(DIR_NAME_LOWER, DIR_NAME).forEach { subDirName ->
                    try {
                        val oldDir = File(File(baseDir, subDirName), oldSanitized)
                        val newDir = File(File(baseDir, subDirName), newSanitized)
                        if (oldDir.exists()) {
                            oldDir.copyRecursively(newDir, overwrite = true)
                            oldDir.deleteRecursively()
                            renamedAny = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        return renamedAny
    }
}
