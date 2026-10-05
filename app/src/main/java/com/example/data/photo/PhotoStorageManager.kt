package com.example.data.photo

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoStorageManager {

    fun getPhotosDirectory(context: Context, nodeId: String): File {
        val baseDir = context.getExternalFilesDir("ftth_photos") ?: File(context.filesDir, "ftth_photos")
        val sanitizedId = nodeId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val nodeDir = File(baseDir, sanitizedId)
        if (!nodeDir.exists()) {
            nodeDir.mkdirs()
        }
        return nodeDir
    }

    fun createNewPhotoFile(context: Context, nodeId: String): File {
        val dir = getPhotosDirectory(context, nodeId)
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

    fun saveImportedPhoto(context: Context, nodeId: String, sourceUri: Uri): String? {
        return try {
            val destinationFile = createNewPhotoFile(context, nodeId)
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
