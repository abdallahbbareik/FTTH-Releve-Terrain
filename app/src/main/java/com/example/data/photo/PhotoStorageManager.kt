package com.example.data.photo

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.storage.PublicStorageHelper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modes de configuration de résolution et taille des photos enregistrées.
 * Options : Résolution native (~7+ Mo), 2 Mo, 1 Mo
 */
enum class PhotoResolutionMode(
    val id: String,
    val title: String,
    val subtitle: String,
    val targetMaxBytes: Long,
    val maxDimension: Int,
    val defaultQuality: Int
) {
    NATIVE(
        id = "native",
        title = "Résolution native",
        subtitle = "Plein capteur (~7+ Mo)",
        targetMaxBytes = -1L,
        maxDimension = -1,
        defaultQuality = 100
    ),
    LIMIT_2MB(
        id = "2mb",
        title = "2 Mo",
        subtitle = "Qualité optimale (max ~2 Mo)",
        targetMaxBytes = 2 * 1024 * 1024L, // 2 097 152 octets
        maxDimension = 2800,
        defaultQuality = 88
    ),
    LIMIT_1MB(
        id = "1mb",
        title = "1 Mo",
        subtitle = "Standard allégé (max ~1 Mo)",
        targetMaxBytes = 1 * 1024 * 1024L, // 1 048 576 octets
        maxDimension = 2048,
        defaultQuality = 82
    );

    companion object {
        fun fromId(id: String?): PhotoResolutionMode {
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NATIVE
        }
    }
}

data class PhotoDetails(
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val resolutionLabel: String,
    val megaPixelsLabel: String = "",
    val qualityLabel: String = "",
    val storageLocationLabel: String = "",
    val modeLabel: String = ""
)

object PhotoStorageManager {

    private const val PREFS_NAME = "ReleveTerrainStoragePrefs"
    private const val KEY_ACTIVE_PROJECT = "active_project_name"
    private const val KEY_PHOTO_RESOLUTION_MODE = "photo_resolution_mode"

    fun getProjectName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val proj = prefs.getString(KEY_ACTIVE_PROJECT, "projet01") ?: "projet01"
        val cleaned = proj.trim().replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        return cleaned.ifBlank { "projet01" }
    }

    /**
     * Récupère le mode de résolution configuré par l'utilisateur.
     * Par défaut : NATIVE (Résolution native)
     */
    fun getPhotoResolutionMode(context: Context): PhotoResolutionMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeId = prefs.getString(KEY_PHOTO_RESOLUTION_MODE, PhotoResolutionMode.NATIVE.id)
        return PhotoResolutionMode.fromId(modeId)
    }

    /**
     * Enregistre le mode de résolution choisi (Résolution native, 2Mo, 1Mo).
     */
    fun setPhotoResolutionMode(context: Context, mode: PhotoResolutionMode) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PHOTO_RESOLUTION_MODE, mode.id).apply()
    }

    /**
     * Répertoire public Documents/releve-terrain/<projet>/photos/
     */
    fun getPhotosDirectory(context: Context, projectName: String? = null): File {
        val proj = projectName ?: getProjectName(context)

        // 1. Dossier public Documents/releve-terrain/<projet>/photos (et Releve-Terrain)
        val docDirs = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "releve-terrain/$proj/photos"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Releve-Terrain/$proj/photos")
        )
        for (pubDocs in docDirs) {
            try {
                if (!pubDocs.exists()) pubDocs.mkdirs()
                if (pubDocs.exists() && pubDocs.canWrite()) {
                    return pubDocs
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Dossier public Download/releve-terrain/<projet>/photos
        val downloadDirs = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "releve-terrain/$proj/photos"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Releve-Terrain/$proj/photos")
        )
        for (pubDown in downloadDirs) {
            try {
                if (!pubDown.exists()) pubDown.mkdirs()
                if (pubDown.exists() && pubDown.canWrite()) {
                    return pubDown
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Dossier externe sécurisé de l'application
        val appExt = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "releve-terrain/$proj/photos")
        if (!appExt.exists()) appExt.mkdirs()
        return appExt
    }

    /**
     * Crée un fichier de capture temporaire pour l'appareil photo dans un emplacement garanti accessible
     * pour FileProvider et l'application caméra externe. Le fichier sera ensuite finalisé dans Documents/releve-terrain/<projet>/photos/.
     */
    fun createNewPhotoFile(context: Context, objectId: String = "PHOTO", projectName: String? = null): File {
        val proj = projectName ?: getProjectName(context)
        val sanitizedId = objectId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date())
        val fileName = "PHOTO_${sanitizedId}_${timestamp}_${System.currentTimeMillis() % 1000}.jpg"

        val captureDir = File(context.getExternalFilesDir("camera_captures"), proj).apply {
            if (!exists()) mkdirs()
        }
        val file = File(captureDir, fileName)
        try {
            if (!file.exists()) {
                file.createNewFile()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return file
    }

    fun getUriForPhotoFile(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Traite et compresse le fichier photo selon la résolution/taille configurée (Native, 2Mo, 1Mo).
     * - Conserve l'orientation EXIF de prise de vue.
     * - Si mode == NATIVE : aucun traitement, qualité 100% capteur intacte.
     * - Si mode == 2Mo ou 1Mo : redimensionnement doux et compression JPEG progressive pour respecter le plafond de taille.
     */
    fun processPhotoFileToConfiguredResolution(context: Context, photoFile: File): File {
        val mode = getPhotoResolutionMode(context)
        if (mode == PhotoResolutionMode.NATIVE) {
            return photoFile
        }
        if (!photoFile.exists() || photoFile.length() <= 0L) {
            return photoFile
        }
        if (photoFile.length() <= mode.targetMaxBytes) {
            return photoFile
        }

        try {
            // 1. Lire l'orientation EXIF pour éviter toute rotation intempestive
            var rotationDegrees = 0f
            try {
                val exif = ExifInterface(photoFile.absolutePath)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                rotationDegrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Décoder les dimensions sans charger l'image complète en mémoire
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(photoFile.absolutePath, boundsOptions)
            val srcW = boundsOptions.outWidth
            val srcH = boundsOptions.outHeight
            if (srcW <= 0 || srcH <= 0) return photoFile

            // 3. Calcul du inSampleSize pour économiser la mémoire sur capteurs 48MP/108MP
            var inSampleSize = 1
            val maxDim = mode.maxDimension
            val maxSrcDim = maxOf(srcW, srcH)
            while (maxSrcDim / (inSampleSize * 2) >= maxDim) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = BitmapFactory.decodeFile(photoFile.absolutePath, decodeOptions) ?: return photoFile

            // 4. Appliquer la rotation EXIF si nécessaire
            if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (rotated != bitmap) {
                    bitmap.recycle()
                    bitmap = rotated
                }
            }

            // 5. Redimensionner si les dimensions dépassent encore maxDimension
            val currentMax = maxOf(bitmap.width, bitmap.height)
            if (currentMax > maxDim) {
                val scale = maxDim.toFloat() / currentMax.toFloat()
                val targetW = (bitmap.width * scale).toInt()
                val targetH = (bitmap.height * scale).toInt()
                val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                    bitmap = scaled
                }
            }

            // 6. Compression JPEG progressive jusqu'à atteindre le seuil cible (2 Mo ou 1 Mo)
            var quality = mode.defaultQuality
            val tempProcessed = File(photoFile.parentFile, "proc_${photoFile.name}")
            var finalBytes: ByteArray? = null

            while (quality >= 35) {
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                val bytes = baos.toByteArray()
                if (bytes.size <= mode.targetMaxBytes || quality <= 45) {
                    finalBytes = bytes
                    break
                }
                quality -= 10
            }

            bitmap.recycle()

            if (finalBytes != null && finalBytes.isNotEmpty()) {
                FileOutputStream(tempProcessed).use { it.write(finalBytes) }
                if (tempProcessed.exists() && tempProcessed.length() > 0) {
                    tempProcessed.copyTo(photoFile, overwrite = true)
                    tempProcessed.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return photoFile
    }

    /**
     * Enregistre et finalise la photo capturée dans les dossiers publics :
     * - Documents/releve-terrain/<projet>/photos/
     * - Documents/Releve-Terrain/<projet>/photos/
     * - et dans MediaStore avec la résolution/taille configurée.
     */
    fun syncAndFinalizePhoto(context: Context, photoFile: File, projectName: String? = null): String {
        if (!photoFile.exists() || photoFile.length() <= 0L) {
            return photoFile.absolutePath
        }

        // 1. Appliquer le réglage de résolution configuré (Native, 2Mo ou 1Mo)
        processPhotoFileToConfiguredResolution(context, photoFile)

        val proj = projectName ?: getProjectName(context)
        val fileName = photoFile.name
        val relativePath = "$proj/photos/$fileName"

        // 2. Sauvegarde dans Documents/releve-terrain/<proj>/photos/<fileName> via PublicStorageHelper
        val finalizedPath = PublicStorageHelper.savePublicPhoto(context, relativePath, photoFile)

        // 3. Sauvegarde dans MediaStore Images pour affichage immédiat dans la galerie
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val targetDirs = listOf(
                "Pictures/releve-terrain/$proj/photos/",
                "Pictures/Releve-Terrain/$proj/photos/"
            )
            for (targetDir in targetDirs) {
                try {
                    val resolver = context.contentResolver
                    val normDir = targetDir.trimEnd('/')

                    val projection = arrayOf(MediaStore.Images.Media._ID)
                    val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ? AND (${MediaStore.Images.Media.RELATIVE_PATH} = ? OR ${MediaStore.Images.Media.RELATIVE_PATH} = ?)"
                    val selectionArgs = arrayOf(fileName, "$normDir/", normDir)

                    var imgUri: Uri? = null
                    resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                            imgUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        }
                    }

                    if (imgUri == null) {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(MediaStore.Images.Media.RELATIVE_PATH, targetDir)
                            put(MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        imgUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    }

                    if (imgUri != null) {
                        resolver.openOutputStream(imgUri, "wt")?.use { out ->
                            photoFile.inputStream().use { input ->
                                input.copyTo(out)
                            }
                        }
                        val updateValues = ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 0)
                        }
                        resolver.update(imgUri, updateValues, null, null)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return finalizedPath ?: photoFile.absolutePath
    }

    /**
     * Enregistre une photo importée depuis la galerie en appliquant la résolution/taille configurée
     * dans Documents/releve-terrain/<projet>/photos/
     */
    fun saveImportedPhoto(context: Context, objectId: String, sourceUri: Uri, projectName: String? = null): String? {
        return try {
            val proj = projectName ?: getProjectName(context)
            val destinationFile = createNewPhotoFile(context, objectId, proj)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (destinationFile.exists() && destinationFile.length() > 0L) {
                syncAndFinalizePhoto(context, destinationFile, proj)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Résout un fichier photo existant en cherchant dans l'ordre :
     * 1. Chemin direct
     * 2. Documents/releve-terrain/<projet>/photos/
     * 3. Documents/Releve-Terrain/<projet>/photos/
     * 4. Documents/releve-terrain/<projet>/photo/
     * 5. Documents/Releve-Terrain/<projet>/Photos/
     * 6. Download/releve-terrain/<projet>/photos/
     * 7. Download/Releve-Terrain/<projet>/photos/
     * 8. App External Files Dir
     */
    fun resolvePhotoFile(context: Context, filePath: String, projectName: String? = null): File {
        val directFile = File(filePath)
        if (directFile.exists() && directFile.length() > 0) return directFile

        val fileName = directFile.name
        val proj = projectName ?: getProjectName(context)

        val candidates = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "releve-terrain/$proj/photos/$fileName"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Releve-Terrain/$proj/photos/$fileName"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "releve-terrain/$proj/photo/$fileName"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Releve-Terrain/$proj/Photos/$fileName"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "releve-terrain/$proj/photos/$fileName"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Releve-Terrain/$proj/photos/$fileName"),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "releve-terrain/$proj/photos/$fileName"),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Releve-Terrain/$proj/photos/$fileName"),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Releve-Terrain/$proj/Photos/$fileName"),
            File(context.getExternalFilesDir("camera_captures"), "$proj/$fileName"),
            File(context.filesDir, "releve-terrain/$proj/photos/$fileName"),
            File(context.filesDir, "Releve-Terrain/$proj/photos/$fileName")
        )

        for (candidate in candidates) {
            if (candidate.exists() && candidate.length() > 0) return candidate
        }

        return directFile
    }

    /**
     * Récupère la résolution réelle (largeur x hauteur), le nombre de mégapixels et le poids d'un fichier photo.
     */
    fun getPhotoDetails(filePath: String, context: Context? = null): PhotoDetails {
        val file = File(filePath)
        if (!file.exists()) return PhotoDetails(0, 0, 0L, "Fichier non trouvé", "", "Inconnu", filePath, "")
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val w = options.outWidth
            val h = options.outHeight
            val sizeKb = file.length() / 1024
            val sizeMbStr = if (sizeKb >= 1024) String.format(Locale.FRANCE, "%.1f Mo", sizeKb / 1024.0) else "$sizeKb Ko"

            val mp = (w.toDouble() * h.toDouble()) / 1_000_000.0
            val mpStr = if (mp >= 0.1) String.format(Locale.FRANCE, "%.1f MP", mp) else ""
            val label = if (w > 0 && h > 0) {
                if (mpStr.isNotEmpty()) "${w}x${h} px ($mpStr) • $sizeMbStr" else "${w}x${h} px • $sizeMbStr"
            } else {
                sizeMbStr
            }

            val quality = when {
                w >= 3840 || h >= 3840 -> "Très haute résolution (4K+)"
                w >= 1920 || h >= 1920 -> "Haute résolution (Full HD)"
                w >= 1280 || h >= 1280 -> "Résolution standard (HD)"
                w > 0 -> "Basse résolution"
                else -> "Inconnue"
            }

            val modeStr = if (context != null) {
                val mode = getPhotoResolutionMode(context)
                "Mode: ${mode.title}"
            } else ""

            val location = "Documents/releve-terrain/.../photos/${file.name}"
            PhotoDetails(w, h, file.length(), label, mpStr, quality, location, modeStr)
        } catch (e: Exception) {
            val sizeKb = file.length() / 1024
            PhotoDetails(0, 0, file.length(), "$sizeKb Ko", "", "Standard", filePath, "")
        }
    }

    fun deletePhoto(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            val fileName = file.name

            // Supprimer également les copies miroir
            listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ).forEach { baseDir ->
                try {
                    listOf("releve-terrain", "Releve-Terrain").forEach { subName ->
                        val candidate = File(baseDir, subName)
                        if (candidate.exists()) {
                            candidate.walkTopDown().filter { it.name == fileName }.forEach { it.delete() }
                        }
                    }
                } catch (ignored: Exception) {}
            }

            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            false
        }
    }
}
