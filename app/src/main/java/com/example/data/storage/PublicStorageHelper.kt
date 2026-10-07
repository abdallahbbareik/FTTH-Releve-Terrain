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

    private const val BASE_DOCUMENTS_FOLDER = "Documents/Releve-Terrain"
    private const val BASE_DIRECTORY_NAME = "Releve-Terrain"

    /**
     * Sauvegarde un fichier texte dans Documents/Releve-Terrain/<relativePath>
     * en utilisant le système de fichiers standard ET MediaStore pour survie après désinstallation/réinstallation.
     */
    fun savePublicDocument(context: Context, relativePath: String, content: String): Boolean {
        var fileSuccess = false
        val normalizedRelativePath = relativePath.trimStart('/')
        
        // 1. Sauvegarde via File API dans le stockage partagé public
        try {
            val pubDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val baseDir = File(pubDocs, BASE_DIRECTORY_NAME)
            val targetFile = File(baseDir, normalizedRelativePath)
            targetFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
            targetFile.writeText(content, Charsets.UTF_8)
            fileSuccess = targetFile.exists() && targetFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Sauvegarde via MediaStore (garantit l'accès et la persistance entre désinstallations/réinstallations)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")

                val targetFileObj = File(normalizedRelativePath)
                val fileName = targetFileObj.name
                val parentSubDir = targetFileObj.parent?.let { "/$it" } ?: ""
                val fullRelativePath = "$BASE_DOCUMENTS_FOLDER$parentSubDir/"

                val projection = arrayOf(MediaStore.Files.FileColumns._ID)
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(fileName, "%$BASE_DOCUMENTS_FOLDER$parentSubDir%")

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
     * Lit un fichier depuis Documents/Releve-Terrain/<relativePath>.
     * Tente via File API, et si non trouvé ou non lisible, tente via MediaStore.
     * Cette fonction permet de récupérer les données sauvegardées même après réinstallation complète de l'application !
     */
    fun loadPublicDocument(context: Context, relativePath: String): String? {
        val normalizedRelativePath = relativePath.trimStart('/')

        // 1. Tente par File API direct dans le dossier public
        try {
            val pubDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val file = File(File(pubDocs, BASE_DIRECTORY_NAME), normalizedRelativePath)
            if (file.exists() && file.canRead()) {
                val text = file.readText(Charsets.UTF_8).trim()
                if (text.isNotEmpty() && text != "[]") return text
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Tente par MediaStore (crucial après réinstallation)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                val targetFileObj = File(normalizedRelativePath)
                val fileName = targetFileObj.name
                val parentSubDir = targetFileObj.parent?.let { "/$it" } ?: ""

                val projection = arrayOf(MediaStore.Files.FileColumns._ID)
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(fileName, "%$BASE_DOCUMENTS_FOLDER$parentSubDir%")

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
     * Découvre la liste de tous les sous-dossiers de projets dans Documents/Releve-Terrain
     */
    fun listPublicProjects(context: Context): List<String> {
        val projectNames = mutableSetOf<String>()

        // 1. Via File API sur dossier public
        try {
            val pubDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val baseDir = File(pubDocs, BASE_DIRECTORY_NAME)
            if (baseDir.exists() && baseDir.isDirectory) {
                baseDir.listFiles()?.forEach { file ->
                    if (file.isDirectory && file.name != "Photos" && file.name != "Exports" && !file.name.startsWith(".")) {
                        projectNames.add(file.name)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Via MediaStore (détection des dossiers persistés après réinstallation)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                val projection = arrayOf(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("%$BASE_DOCUMENTS_FOLDER/%")

                resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                    while (cursor.moveToNext()) {
                        val relPath = cursor.getString(pathIdx) ?: continue
                        // Ex: "Documents/Releve-Terrain/projet01/" ou "Documents/Releve-Terrain/projet02/Photos/"
                        val prefix = "$BASE_DOCUMENTS_FOLDER/"
                        if (relPath.startsWith(prefix)) {
                            val sub = relPath.removePrefix(prefix).trim('/')
                            val firstSegment = sub.split("/").firstOrNull()
                            if (!firstSegment.isNullOrBlank() && firstSegment != "Photos" && firstSegment != "Exports" && !firstSegment.startsWith(".")) {
                                projectNames.add(firstSegment)
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
     * Supprime un sous-dossier de projet public et ses entrées MediaStore
     */
    fun deletePublicProject(context: Context, projectName: String): Boolean {
        try {
            val pubDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val projectDir = File(File(pubDocs, BASE_DIRECTORY_NAME), projectName)
            if (projectDir.exists()) {
                projectDir.deleteRecursively()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("%$BASE_DOCUMENTS_FOLDER/$projectName%")
                resolver.delete(collection, selection, selectionArgs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return true
    }

    /**
     * Renomme un projet dans le dossier public
     */
    fun renamePublicProject(context: Context, oldName: String, newName: String): Boolean {
        try {
            val pubDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val base = File(pubDocs, BASE_DIRECTORY_NAME)
            val oldDir = File(base, oldName)
            val newDir = File(base, newName)
            if (oldDir.exists()) {
                return oldDir.renameTo(newDir)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
