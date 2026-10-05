package com.example.data.photo

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoStorageManager {

    /**
     * Répertoire dédié : Documents/Releve-Terrain/Photos/<id_objet>/
     */
    fun getPhotosDirectory(context: Context, objectId: String): File {
        val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val baseDir = File(publicDocs, "Releve-Terrain/Photos")
        val fallbackBase = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Releve-Terrain/Photos")

        val targetBase = if (baseDir.exists() || baseDir.mkdirs()) baseDir else {
            if (!fallbackBase.exists()) fallbackBase.mkdirs()
            fallbackBase
        }

        val sanitizedId = objectId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val objectDir = File(targetBase, sanitizedId)
        if (!objectDir.exists()) {
            objectDir.mkdirs()
        }
        return objectDir
    }

    fun createNewPhotoFile(context: Context, objectId: String): File {
        val dir = getPhotosDirectory(context, objectId)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date())
        return File(dir, "PHOTO_${timestamp}_${System.currentTimeMillis() % 1000}.jpg")
    }

    fun getUriForPhotoFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun saveImportedPhoto(context: Context, objectId: String, sourceUri: Uri): String? {
        return try {
            val destinationFile = createNewPhotoFile(context, objectId)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            destinationFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deletePhoto(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            false
        }
    }
}
