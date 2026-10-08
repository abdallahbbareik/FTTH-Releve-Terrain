package com.example.data.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

object PublicStorageHelper {

    private const val FOLDER_DOCUMENTS = "Documents/Releve-Terrain"
    private const val FOLDER_DOCUMENTS_LOWER = "Documents/releve-terrain"
    private const val FOLDER_DOWNLOADS = "Download/Releve-Terrain"
    private const val FOLDER_DOWNLOADS_LOWER = "Download/releve-terrain"
    private const val DIR_NAME = "Releve-Terrain"
    private const val DIR_NAME_LOWER = "releve-terrain"
    private const val PREFS_NAME = "ReleveTerrainDeletedProjects"

    fun getDeletedProjects(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet("deleted_projects", emptySet()) ?: emptySet()
    }

    fun isProjectDeleted(context: Context, projectName: String): Boolean {
        val deleted = getDeletedProjects(context)
        return deleted.contains(projectName) || deleted.contains(projectName.lowercase())
    }

    fun markProjectDeleted(context: Context, projectName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet("deleted_projects", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(projectName)
        current.add(projectName.lowercase())
        prefs.edit().putStringSet("deleted_projects", HashSet(current)).commit()
    }

    fun unmarkProjectDeleted(context: Context, projectName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet("deleted_projects", emptySet())?.toMutableSet() ?: mutableSetOf()
        var changed = current.remove(projectName)
        changed = current.remove(projectName.lowercase()) || changed
        if (changed) {
            prefs.edit().putStringSet("deleted_projects", HashSet(current)).commit()
        }
    }

    /**
     * Sauvegarde un fichier texte dans Documents/releve-terrain/<relativePath> et Documents/Releve-Terrain/<relativePath>
     * et miroirs Download, stockage local de l'application, et MediaStore pour survie absolue après réinstallation.
     */
    fun savePublicDocument(context: Context, relativePath: String, content: String): Boolean {
        var fileSuccess = false
        val normalized = relativePath.trimStart('/')

        val parts = normalized.split("/")
        if (parts.size > 1) {
            val proj = parts[0]
            if (isProjectDeleted(context, proj)) {
                return false
            }
        }

        // 1. Sauvegarde File API dans Documents/releve-terrain et Documents/Releve-Terrain
        val docDirs = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), DIR_NAME),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), DIR_NAME_LOWER)
        )
        for (pubDir in docDirs) {
            try {
                val fileDocs = File(pubDir, normalized)
                fileDocs.parentFile?.let { if (!it.exists()) it.mkdirs() }
                fileDocs.writeText(content, Charsets.UTF_8)
                if (fileDocs.exists() && fileDocs.length() > 0) fileSuccess = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Sauvegarde miroir File API dans Download/releve-terrain et Download/Releve-Terrain
        val downloadDirs = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DIR_NAME_LOWER)
        )
        for (pubDown in downloadDirs) {
            try {
                val fileDownloads = File(pubDown, normalized)
                fileDownloads.parentFile?.let { if (!it.exists()) it.mkdirs() }
                fileDownloads.writeText(content, Charsets.UTF_8)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Sauvegarde dans les répertoires de l'application (externalFilesDir et filesDir)
        val appDirs = listOf(
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), DIR_NAME),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), DIR_NAME_LOWER),
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

        // 4. Sauvegarde via MediaStore (avec mode "wt" pour écrasement propre sans résidus)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")

                val targetFileObj = File(normalized)
                val fileName = targetFileObj.name
                val parentSubDir = targetFileObj.parent?.let { "/$it" } ?: ""

                // Sauvegarder pour les deux variantes de casse dans MediaStore
                val folderVariants = listOf(FOLDER_DOCUMENTS, FOLDER_DOCUMENTS_LOWER)
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
     * Sauvegarde une photo binaire dans Documents/releve-terrain/<projet>/photos/
     * ainsi que Documents/Releve-Terrain/<projet>/photos/ (et miroirs Download, app, et MediaStore).
     *
     * IMPORTANT : Pour le dossier Documents/ sous MediaStore, le type MIME doit être "application/octet-stream"
     * car Android MediaProvider refuse "image/jpeg" sous le répertoire racine Documents.
     * Pour Pictures/, nous enregistrons également dans MediaStore Images ("image/jpeg") pour la galerie.
     */
    fun savePublicPhoto(context: Context, relativePath: String, sourceFile: File): String? {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) return null
        val normalized = relativePath.trimStart('/')
        val parts = normalized.split("/")
        val proj = if (parts.size > 1) parts[0] else "projet01"
        val fileName = File(normalized).name

        unmarkProjectDeleted(context, proj)

        val scannedPaths = mutableListOf<String>()

        // 1. Écriture directe File API dans :
        // - Documents/releve-terrain/<proj>/photos/<fileName>
        // - Documents/Releve-Terrain/<proj>/photos/<fileName>
        // - Documents/releve-terrain/<proj>/photo/<fileName>
        // - Documents/Releve-Terrain/<proj>/Photos/<fileName>
        var preferredDocPath: String? = null
        val targetPublicDocFolders = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "releve-terrain/$proj/photos"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Releve-Terrain/$proj/photos"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "releve-terrain/$proj/photo"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Releve-Terrain/$proj/Photos")
        )
        for (folder in targetPublicDocFolders) {
            try {
                if (!folder.exists()) folder.mkdirs()
                val destFile = File(folder, fileName)
                sourceFile.copyTo(destFile, overwrite = true)
                if (destFile.exists() && destFile.length() > 0) {
                    if (preferredDocPath == null) preferredDocPath = destFile.absolutePath
                    scannedPaths.add(destFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Écriture miroir dans Download/releve-terrain et Download/Releve-Terrain
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
                    scannedPaths.add(destFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Écriture interne / app external
        var appExtPath: String? = null
        val targetAppFolders = listOf(
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "releve-terrain/$proj/photos"),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Releve-Terrain/$proj/photos"),
            File(context.filesDir, "releve-terrain/$proj/photos"),
            File(context.filesDir, "Releve-Terrain/$proj/photos")
        )
        for (folder in targetAppFolders) {
            try {
                if (!folder.exists()) folder.mkdirs()
                val destFile = File(folder, fileName)
                sourceFile.copyTo(destFile, overwrite = true)
                if (destFile.exists() && destFile.length() > 0) {
                    if (appExtPath == null) appExtPath = destFile.absolutePath
                    scannedPaths.add(destFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 4. Enregistrement garanti via MediaStore dans Documents/releve-terrain/<proj>/photos/
        // avec MIME "application/octet-stream" pour que MediaProvider autorise Documents/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                val mediaDocPaths = listOf(
                    "Documents/releve-terrain/$proj/photos/",
                    "Documents/Releve-Terrain/$proj/photos/"
                )
                for (targetRelPath in mediaDocPaths) {
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
                            // "application/octet-stream" est accepté dans Documents/ par MediaProvider
                            put(MediaStore.Files.FileColumns.MIME_TYPE, "application/octet-stream")
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

            // 4b. Enregistrement miroir dans Pictures/ pour l'application Galerie native
            try {
                val resolver = context.contentResolver
                val picturesRelPaths = listOf(
                    "Pictures/releve-terrain/$proj/photos/",
                    "Pictures/Releve-Terrain/$proj/photos/"
                )
                for (picPath in picturesRelPaths) {
                    val normPicPath = picPath.trimEnd('/')
                    val projCol = arrayOf(MediaStore.Images.Media._ID)
                    val sel = "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND (${MediaStore.Images.Media.RELATIVE_PATH} = ? OR ${MediaStore.Images.Media.RELATIVE_PATH} = ?)"
                    val args = arrayOf(fileName, "$normPicPath/", normPicPath)

                    var picUri: Uri? = null
                    resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projCol, sel, args, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                            picUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        }
                    }

                    if (picUri == null) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, picPath)
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        picUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    }

                    if (picUri != null) {
                        resolver.openOutputStream(picUri, "wt")?.use { out ->
                            sourceFile.inputStream().use { inStream ->
                                inStream.copyTo(out)
                            }
                        }
                        val updateValues = ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 0)
                        }
                        resolver.update(picUri, updateValues, null, null)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 5. Indexation MediaScanner immédiate pour tous les chemins
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

        return preferredDocPath ?: appExtPath ?: sourceFile.absolutePath
    }

    /**
     * Lit un fichier depuis Documents/releve-terrain/<relativePath> ou Documents/Releve-Terrain/<relativePath>
     * ou les miroirs Download / stockage application.
     */
    fun loadPublicDocument(context: Context, relativePath: String): String? {
        val normalized = relativePath.trimStart('/')

        // 1. Tente par Documents (releve-terrain et Releve-Terrain)
        val docBases = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), DIR_NAME_LOWER),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), DIR_NAME)
        )
        for (pubDocs in docBases) {
            try {
                val file = File(pubDocs, normalized)
                if (file.exists() && file.canRead()) {
                    val text = file.readText(Charsets.UTF_8).trim()
                    if (text.isNotEmpty() && text != "[]") return text
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Tente par Download (releve-terrain et Releve-Terrain)
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

        // 3. Tente par externalFilesDir (persistant)
        val appBases = listOf(
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), DIR_NAME_LOWER),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), DIR_NAME),
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

        // 4. Tente par MediaStore avec correspondance stricte du sous-dossier
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val targetFileObj = File(normalized)
                val fileName = targetFileObj.name
                val parentSubDir = targetFileObj.parent?.let { "/$it" } ?: ""

                val folderVariants = listOf(FOLDER_DOCUMENTS_LOWER, FOLDER_DOCUMENTS, FOLDER_DOWNLOADS_LOWER, FOLDER_DOWNLOADS)
                for (folderVariant in folderVariants) {
                    val targetRelPath = "$folderVariant$parentSubDir/"
                    val normRelPath = targetRelPath.trimEnd('/')

                    val resolver = context.contentResolver
                    val collection = MediaStore.Files.getContentUri("external")
                    val projection = arrayOf(MediaStore.Files.FileColumns._ID)
                    val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND (${MediaStore.Files.FileColumns.RELATIVE_PATH} = ? OR ${MediaStore.Files.FileColumns.RELATIVE_PATH} = ?)"
                    val selectionArgs = arrayOf(fileName, "$normRelPath/", normRelPath)

                    resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                            val uri = ContentUris.withAppendedId(collection, id)
                            resolver.openInputStream(uri)?.use { stream ->
                                val text = stream.bufferedReader(Charsets.UTF_8).readText().trim()
                                if (text.isNotEmpty() && text != "[]") return text
                            }
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
     * Découvre tous les sous-dossiers de projets dans Documents et Download (releve-terrain et Releve-Terrain).
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

        // 1. Documents (releve-terrain et Releve-Terrain)
        val docBases = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), DIR_NAME_LOWER),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), DIR_NAME)
        )
        for (baseDir in docBases) {
            try {
                if (baseDir.exists() && baseDir.isDirectory) {
                    baseDir.listFiles()?.forEach { checkAndAdd(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Download (releve-terrain et Releve-Terrain)
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

        // 3. App External & Internal
        val appBases = listOf(
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), DIR_NAME_LOWER),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), DIR_NAME),
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

        // 4. MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                val projection = arrayOf(MediaStore.Files.FileColumns.RELATIVE_PATH)

                listOf(
                    "$FOLDER_DOCUMENTS_LOWER/",
                    "$FOLDER_DOCUMENTS/",
                    "$FOLDER_DOWNLOADS_LOWER/",
                    "$FOLDER_DOWNLOADS/"
                ).forEach { prefix ->
                    val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                    val selectionArgs = arrayOf("$prefix%")

                    resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                        val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                        while (cursor.moveToNext()) {
                            val relPath = cursor.getString(pathCol) ?: continue
                            if (relPath.startsWith(prefix)) {
                                val sub = relPath.removePrefix(prefix).trimStart('/')
                                val firstSegment = sub.split("/").firstOrNull()?.trim()
                                val segmentLower = firstSegment?.lowercase()
                                if (!firstSegment.isNullOrEmpty() &&
                                    segmentLower != "photos" && segmentLower != "photo" &&
                                    segmentLower != "exports" && segmentLower != "export" &&
                                    !firstSegment.startsWith(".")
                                ) {
                                    if (!deletedProjects.contains(firstSegment)) {
                                        projectNames.add(firstSegment)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return projectNames.toList().sorted()
    }

    /**
     * Supprime définitivement un projet de tous les emplacements de stockage public et MediaStore.
     */
    fun deletePublicProject(context: Context, projectName: String): Boolean {
        val sanitized = projectName.trim().replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        if (sanitized.isBlank()) return false

        markProjectDeleted(context, sanitized)

        var deletedAny = false

        // 1. Documents et Download
        listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
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

        // 2. Répertoires de l'application
        listOf(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            context.filesDir
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

        // 3. MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                listOf(
                    "$FOLDER_DOCUMENTS_LOWER/$sanitized/",
                    "$FOLDER_DOCUMENTS/$sanitized/",
                    "$FOLDER_DOWNLOADS_LOWER/$sanitized/",
                    "$FOLDER_DOWNLOADS/$sanitized/"
                ).forEach { pathPrefix ->
                    val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                    val selectionArgs = arrayOf("$pathPrefix%")
                    resolver.delete(collection, selection, selectionArgs)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return deletedAny
    }

    /**
     * Renomme un projet dans tous les emplacements de stockage et met à jour MediaStore.
     */
    fun renamePublicProject(context: Context, oldProject: String, newProject: String): Boolean {
        val oldSanitized = oldProject.trim().replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        val newSanitized = newProject.trim().replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        if (oldSanitized.isBlank() || newSanitized.isBlank() || oldSanitized == newSanitized) return false

        unmarkProjectDeleted(context, newSanitized)
        var renamedAny = false

        // 1. Documents, Download et répertoires application (releve-terrain et Releve-Terrain)
        listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            context.filesDir
        ).forEach { baseDir ->
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

        // 2. MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")
                listOf(
                    Pair("$FOLDER_DOCUMENTS_LOWER/$oldSanitized/", "$FOLDER_DOCUMENTS_LOWER/$newSanitized/"),
                    Pair("$FOLDER_DOCUMENTS/$oldSanitized/", "$FOLDER_DOCUMENTS/$newSanitized/"),
                    Pair("$FOLDER_DOWNLOADS_LOWER/$oldSanitized/", "$FOLDER_DOWNLOADS_LOWER/$newSanitized/"),
                    Pair("$FOLDER_DOWNLOADS/$oldSanitized/", "$FOLDER_DOWNLOADS/$newSanitized/")
                ).forEach { (oldPrefix, newPrefix) ->
                    val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                    val selectionArgs = arrayOf("$oldPrefix%")
                    val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.RELATIVE_PATH)

                    resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                        val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idCol)
                            val currentPath = cursor.getString(pathCol) ?: continue
                            val updatedPath = currentPath.replaceFirst(oldPrefix, newPrefix)
                            val itemUri = ContentUris.withAppendedId(collection, id)
                            val updateValues = ContentValues().apply {
                                put(MediaStore.Files.FileColumns.RELATIVE_PATH, updatedPath)
                            }
                            resolver.update(itemUri, updateValues, null, null)
                            renamedAny = true
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return renamedAny
    }
}
