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

    private const val FOLDER_NAME = "Documents/Releve-Terrain"
    private const val DIRECTORY_NAME = "Releve-Terrain"

    /**
     * Sauvegarde un fichier texte dans Documents/Releve-Terrain
     * en utilisant le système de fichiers standard ET MediaStore pour survie après désinstallation/réinstallation.
     */
    fun savePublicDocument(context: Context, fileName: String, content: String): Boolean {
        var fileSuccess = false
        // 1. Sauvegarde via File API dans le stockage partagé public
        try {
            val pubDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val targetDir = File(pubDocs, DIRECTORY_NAME)
            if (!targetDir.exists()) targetDir.mkdirs()
            val file = File(targetDir, fileName)
            file.writeText(content, Charsets.UTF_8)
            fileSuccess = file.exists() && file.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Sauvegarde via MediaStore (garantit l'accès et la persistance entre désinstallations/réinstallations)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val collection = MediaStore.Files.getContentUri("external")

                val projection = arrayOf(MediaStore.Files.FileColumns._ID)
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(fileName, "%$FOLDER_NAME%")

                var existingUri: Uri? = null
                resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                        existingUri = ContentUris.withAppendedId(collection, id)
                    }
                }

                val targetUri = existingUri ?: run {
                    val values = ContentValues().apply {
                        put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.Files.FileColumns.MIME_TYPE, if (fileName.endsWith(".json")) "application/json" else "application/geo+json")
                        put(MediaStore.Files.FileColumns.RELATIVE_PATH, "$FOLDER_NAME/")
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
     * Lit un fichier depuis Documents/Releve-Terrain.
     * Tente via File API, et si non trouvé ou non lisible, tente via MediaStore.
     * Cette fonction permet de récupérer les données sauvegardées même après réinstallation complète de l'application !
     */
    fun loadPublicDocument(context: Context, fileName: String): String? {
        // 1. Tente par File API direct dans le dossier public
        try {
            val pubDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val file = File(File(pubDocs, DIRECTORY_NAME), fileName)
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
                val projection = arrayOf(MediaStore.Files.FileColumns._ID)
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(fileName, "%$FOLDER_NAME%")

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
}
