package com.example.data.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object PublicStorageHelper {

    private const val FOLDER_DOCUMENTS = "Documents/Releve-Terrain"
    private const val FOLDER_DOWNLOADS = "Download/Releve-Terrain"
    private const val DIR_NAME = "Releve-Terrain"
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
     * Sauvegarde un fichier texte dans Documents/Releve-Terrain/<relativePath> et Download/Releve-Terrain/<relativePath>
     * et dans le stockage local de l'application, avec MediaStore pour survie absolue après réinstallation.
     */
    fun savePublicDocument(context: Context, relativePath: String, content: String): Boolean {
        var fileSuccess = false
        val normalized = relativePath.trimStart('/')

        val parts = normalized.split("/")
        if (parts.size > 1) {
            val proj = parts[0]
            unmarkProjectDeleted(context, proj)
        }

        // 1. Sauvegarde File API dans Documents/Releve-Terrain
        try {
            val pubDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val fileDocs = File(File(pubDocs, DIR_NAME), normalized)
            fileDocs.parentFile?.let { if (!it.exists()) it.mkdirs() }
            fileDocs.writeText(content, Charsets.UTF_8)
            fileSuccess = fileDocs.exists() && fileDocs.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Sauvegarde miroir File API dans Download/Releve-Terrain
        try {
            val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val fileDownloads = File(File(pubDownloads, DIR_NAME), normalized)
            fileDownloads.parentFile?.let { if (!it.exists()) it.mkdirs() }
            fileDownloads.writeText(content, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Sauvegarde dans les répertoires de l'application (externalFilesDir et filesDir)
        try {
            val extDocs = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), DIR_NAME)
            val fileExt = File(extDocs, normalized)
            fileExt.parentFile?.let { if (!it.exists()) it.mkdirs() }
            fileExt.writeText(content, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val intDocs = File(context.filesDir, DIR_NAME)
            val fileInt = File(intDocs, normalized)
            fileInt.parentFile?.let { if (!it.exists()) it.mkdirs() }
            fileInt.writeText(content, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Sauvegarde via MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")

                val targetFileObj = File(normalized)
                val fileName = targetFileObj.name
                val parentSubDir = targetFileObj.parent?.let { "/$it" } ?: ""
                val fullRelativePath = "$FOLDER_DOCUMENTS$parentSubDir/"

                val projection = arrayOf(MediaStore.Files.FileColumns._ID)
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"
                val selectionArgs = arrayOf(fileName, fullRelativePath)

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
                    }
                    resolver.insert(collection, values)
                }

                if (targetUri != null) {
                    resolver.openOutputStream(targetUri, "rwt")?.use { output ->
                        output.write(content.toByteArray(Charsets.UTF_8))
                        output.flush()
                    }
                    return true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return fileSuccess
    }

    /**
     * Lit un fichier depuis Documents/Releve-Terrain/<relativePath> ou Download/Releve-Terrain/<relativePath>
     * ou les répertoires internes de l'application.
     */
    fun loadPublicDocument(context: Context, relativePath: String): String? {
        val normalized = relativePath.trimStart('/')

        // 1. Tente par Documents/Releve-Terrain
        try {
            val pubDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val file = File(File(pubDocs, DIR_NAME), normalized)
            if (file.exists() && file.canRead()) {
                val text = file.readText(Charsets.UTF_8).trim()
                if (text.isNotEmpty() && text != "[]") return text
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Tente par Download/Releve-Terrain (secours persistant après réinstallation)
        try {
            val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(File(pubDownloads, DIR_NAME), normalized)
            if (file.exists() && file.canRead()) {
                val text = file.readText(Charsets.UTF_8).trim()
                if (text.isNotEmpty() && text != "[]") return text
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Tente par externalFilesDir (persistant)
        try {
            val extDocs = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), DIR_NAME)
            val file = File(extDocs, normalized)
            if (file.exists() && file.canRead()) {
                val text = file.readText(Charsets.UTF_8).trim()
                if (text.isNotEmpty() && text != "[]") return text
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Tente par filesDir interne
        try {
            val intDocs = File(context.filesDir, DIR_NAME)
            val file = File(intDocs, normalized)
            if (file.exists() && file.canRead()) {
                val text = file.readText(Charsets.UTF_8).trim()
                if (text.isNotEmpty() && text != "[]") return text
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 5. Tente par MediaStore avec correspondance stricte du sous-dossier
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                val targetFileObj = File(normalized)
                val fileName = targetFileObj.name
                val parentSubDir = targetFileObj.parent?.let { "/$it" } ?: ""
                val targetPath = "$FOLDER_DOCUMENTS$parentSubDir/"

                val projection = arrayOf(MediaStore.Files.FileColumns._ID)
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?"
                val selectionArgs = arrayOf(fileName, targetPath)

                resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                        val uri = ContentUris.withAppendedId(collection, id)
                        resolver.openInputStream(uri)?.use { input ->
                            val text = input.bufferedReader(Charsets.UTF_8).readText().trim()
                            if (text.isNotEmpty() && text != "[]") return text
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return null
    }

    /**
     * Découvre tous les sous-dossiers de projets dans Documents/Releve-Terrain et Download/Releve-Terrain
     */
    fun listPublicProjects(context: Context): List<String> {
        val projectNames = mutableSetOf<String>()
        val deletedProjects = getDeletedProjects(context)

        fun checkAndAdd(file: File) {
            if (file.isDirectory && file.name != "Photos" && file.name != "Exports" && !file.name.startsWith(".")) {
                if (!deletedProjects.contains(file.name)) {
                    projectNames.add(file.name)
                }
            }
        }

        // 1. Documents/Releve-Terrain
        try {
            val pubDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val baseDir = File(pubDocs, DIR_NAME)
            if (baseDir.exists() && baseDir.isDirectory) {
                baseDir.listFiles()?.forEach { checkAndAdd(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Download/Releve-Terrain
        try {
            val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val baseDir = File(pubDownloads, DIR_NAME)
            if (baseDir.exists() && baseDir.isDirectory) {
                baseDir.listFiles()?.forEach { checkAndAdd(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. App External & Internal
        try {
            val extDocs = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), DIR_NAME)
            if (extDocs.exists() && extDocs.isDirectory) {
                extDocs.listFiles()?.forEach { checkAndAdd(it) }
            }
            val intDocs = File(context.filesDir, DIR_NAME)
            if (intDocs.exists() && intDocs.isDirectory) {
                intDocs.listFiles()?.forEach { checkAndAdd(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                val projection = arrayOf(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("%$DIR_NAME/%")

                resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                    while (cursor.moveToNext()) {
                        val relPath = cursor.getString(pathIdx) ?: continue
                        val parts = relPath.split("/").filter { it.isNotBlank() }
                        val dirIdx = parts.indexOf(DIR_NAME)
                        if (dirIdx >= 0 && dirIdx + 1 < parts.size) {
                            val subProject = parts[dirIdx + 1]
                            if (subProject != "Photos" && subProject != "Exports" && !subProject.startsWith(".")) {
                                if (!deletedProjects.contains(subProject)) {
                                    projectNames.add(subProject)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return projectNames.sorted()
    }

    /**
     * Supprime définitivement un projet dans tous les emplacements publics et MediaStore
     */
    fun deletePublicProject(context: Context, projectName: String): Boolean {
        var deleted = false
        markProjectDeleted(context, projectName)

        // 1. Documents/Releve-Terrain/<projectName>
        try {
            val pubDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val projectDir = File(File(pubDocs, DIR_NAME), projectName)
            if (projectDir.exists()) {
                projectDir.deleteRecursively()
                deleted = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Download/Releve-Terrain/<projectName>
        try {
            val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val projectDir = File(File(pubDownloads, DIR_NAME), projectName)
            if (projectDir.exists()) {
                projectDir.deleteRecursively()
                deleted = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. App Storage
        try {
            val extDocs = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), DIR_NAME)
            val projectDirExt = File(extDocs, projectName)
            if (projectDirExt.exists()) {
                projectDirExt.deleteRecursively()
                deleted = true
            }

            val intDocs = File(context.filesDir, DIR_NAME)
            val projectDirInt = File(intDocs, projectName)
            if (projectDirInt.exists()) {
                projectDirInt.deleteRecursively()
                deleted = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("%$DIR_NAME/$projectName/%")
                resolver.delete(collection, selection, selectionArgs)
                deleted = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return deleted
    }

    /**
     * Renomme un projet dans Documents et Download
     */
    fun renamePublicProject(context: Context, oldName: String, newName: String): Boolean {
        var success = false
        unmarkProjectDeleted(context, newName)

        val bases = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            context.filesDir
        )

        for (baseRoot in bases) {
            if (baseRoot == null) continue
            try {
                val base = File(baseRoot, DIR_NAME)
                val oldDir = File(base, oldName)
                val newDir = File(base, newName)
                if (oldDir.exists()) {
                    oldDir.copyRecursively(newDir, overwrite = true)
                    oldDir.deleteRecursively()
                    success = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        markProjectDeleted(context, oldName)
        return success
    }
}
