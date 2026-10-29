package com.example.data.apd

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Moteur de parsing KMZ / KML pour l'extraction FTTH APD
 * Lit les fichiers KMZ, extrait les Placemarks, résout les tables HTML de description
 * et classifie les entités selon la nomenclature APD.
 */
object FtthApdParser {

    /**
     * Lit un flux KMZ (Zip) et retourne les octets du fichier KML principal (doc.kml ou premier .kml)
     */
    fun loadKmlFromKmz(kmzStream: InputStream): ByteArray {
        ZipInputStream(kmzStream).use { zis ->
            var entry = zis.nextEntry
            var firstKmlBytes: ByteArray? = null
            var docKmlBytes: ByteArray? = null

            while (entry != null) {
                val name = entry.name.lowercase(Locale.ROOT)
                if (name.endsWith(".kml")) {
                    val bytes = zis.readBytes()
                    if (name.endsWith("doc.kml")) {
                        docKmlBytes = bytes
                        break
                    } else if (firstKmlBytes == null) {
                        firstKmlBytes = bytes
                    }
                }
                entry = zis.nextEntry
            }

            return docKmlBytes ?: firstKmlBytes ?: throw IllegalArgumentException("Aucun fichier .kml valide trouvé dans le KMZ")
        }
    }

    /**
     * Parse une description au format tableau HTML 2 colonnes (clé/valeur).
     * Extrait les paires clé/valeur HTML sans dépendance externe.
     */
    fun parseDescriptionTable(html: String?): Map<String, String> {
        if (html.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()

        val trRegex = Regex("(?is)<tr[^>]*>\\s*<t[dh][^>]*>(.*?)</t[dh]>\\s*<t[dh][^>]*>(.*?)</t[dh]>\\s*</tr>")
        val tagStripRegex = Regex("<[^>]*>")

        for (match in trRegex.findAll(html)) {
            val rawKey = match.groupValues[1].replace(tagStripRegex, "").trim().trimEnd(':').trim()
            val rawVal = match.groupValues[2].replace(tagStripRegex, "").trim()
            if (rawKey.isNotEmpty()) {
                result[rawKey] = rawVal
            }
        }
        return result
    }

    /**
     * Parse le KML XML et extrait tous les Placemarks (Point & LineString)
     */
    fun parsePlacemarks(kmlBytes: ByteArray): List<ApdRawPlacemark> {
        val results = mutableListOf<ApdRawPlacemark>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(kmlBytes), "UTF-8")

        var eventType = parser.eventType
        var currentName = ""
        var currentFolder = ""
        var currentDescription = ""
        var folderStack = mutableListOf<String>()
        var geomType: String? = null
        var coordsStr: String? = null
        var currentAttributes = mutableMapOf<String, String>()
        var inPlacemark = false
        var currentTag = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name ?: ""
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = tagName.lowercase(Locale.ROOT)
                    if (currentTag == "folder") {
                        // Dossier KML
                    } else if (currentTag == "placemark") {
                        inPlacemark = true
                        currentName = ""
                        currentDescription = ""
                        geomType = null
                        coordsStr = null
                        currentAttributes = mutableMapOf()
                        if (folderStack.isNotEmpty()) {
                            currentFolder = folderStack.last()
                        }
                    } else if (inPlacemark) {
                        if (currentTag == "point") {
                            geomType = "Point"
                        } else if (currentTag == "linestring") {
                            geomType = "LineString"
                        }
                    }
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (text.isNotBlank()) {
                        if (!inPlacemark && currentTag == "name") {
                            currentFolder = text
                            if (folderStack.isEmpty() || folderStack.last() != text) {
                                folderStack.add(text)
                            }
                        } else if (inPlacemark) {
                            when (currentTag) {
                                "name" -> currentName = text
                                "description" -> currentDescription = text
                                "coordinates" -> coordsStr = text
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    val endTag = tagName.lowercase(Locale.ROOT)
                    if (endTag == "folder" && folderStack.isNotEmpty()) {
                        folderStack.removeAt(folderStack.size - 1)
                    } else if (endTag == "placemark" && inPlacemark) {
                        inPlacemark = false
                        if (geomType != null && !coordsStr.isNullOrBlank()) {
                            val descAttrs = parseDescriptionTable(currentDescription)
                            val mergedAttrs = descAttrs + currentAttributes
                            results.add(
                                ApdRawPlacemark(
                                    name = currentName,
                                    folder = currentFolder,
                                    geometryType = geomType!!,
                                    coordinatesStr = coordsStr!!,
                                    attributes = mergedAttrs
                                )
                            )
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return results
    }

    /**
     * Extrai la première coordonnée longitude, latitude d'une chaîne
     */
    fun parseLonLat(coordStr: String?): Pair<Double, Double>? {
        if (coordStr.isNullOrBlank()) return null
        val firstPt = coordStr.trim().split(Regex("\\s+")).firstOrNull() ?: return null
        val parts = firstPt.split(",")
        if (parts.size >= 2) {
            val lon = parts[0].toDoubleOrNull() ?: return null
            val lat = parts[1].toDoubleOrNull() ?: return null
            return Pair(lon, lat)
        }
        return null
    }

    /**
     * Classifie le type d'objet d'un point selon la nomenclature FTTH APD
     */
    fun classifyPointType(raw: ApdRawPlacemark): ApdObjectType {
        val fonc = (raw.attributes["TYPE_FONC"] ?: raw.attributes["type_fonc"] ?: raw.attributes["Type_Fonc"] ?: raw.attributes["FONCTION"] ?: raw.attributes["Fonction"] ?: "").uppercase(Locale.ROOT)
        val name = (raw.attributes["NOM"] ?: raw.attributes["nom"] ?: raw.name).trim()
        val nameUpper = name.uppercase(Locale.ROOT)
        val typeStruc = (raw.attributes["TYPE_STRUC"] ?: raw.attributes["type_struc"] ?: raw.attributes["Type_Struc"] ?: "").uppercase(Locale.ROOT)
        val folderUpper = raw.folder.uppercase(Locale.ROOT)

        return when {
            nameUpper.startsWith("SRO") || folderUpper.contains("SRO") -> ApdObjectType.SRO
            fonc == "CHAMBRE" || folderUpper.contains("CHAMBRE") || nameUpper.contains("CHAMBRE") -> ApdObjectType.CHAMBRE
            fonc == "APPUI" || fonc == "POTEAU" || folderUpper.contains("POTEAU") || folderUpper.contains("APPUI") || nameUpper.contains("POTEAU") || nameUpper.contains("APPUI") -> ApdObjectType.POTEAU
            nameUpper == "JOINT" || nameUpper == "BPE" || (nameUpper.contains("BPE") && !nameUpper.contains("PBO")) -> ApdObjectType.BPE
            nameUpper == "JOINT-SPLIT 1/8" || nameUpper.contains("BPE-PBO") || (nameUpper.contains("BPE") && nameUpper.contains("PBO")) -> ApdObjectType.BPE_PBO
            nameUpper == "SPLIT 1/8" || nameUpper == "PBO" -> ApdObjectType.PBO
            else -> {
                val fallback = typeStruc.ifBlank { fonc }
                when {
                    fallback.contains("SRO") || folderUpper.contains("SRO") -> ApdObjectType.SRO
                    fallback.contains("CHAMBRE") || folderUpper.contains("CHAMBRE") -> ApdObjectType.CHAMBRE
                    fallback.contains("POTEAU") || fallback.contains("APPUI") || folderUpper.contains("POTEAU") || folderUpper.contains("APPUI") -> ApdObjectType.POTEAU
                    fallback.contains("BPE-PBO") || fallback.contains("JOINT-SPLIT") -> ApdObjectType.BPE_PBO
                    fallback.contains("BPE") || fallback.contains("JOINT") || folderUpper.contains("BPE") -> ApdObjectType.BPE
                    fallback.contains("PBO") || fallback.contains("SPLIT") || folderUpper.contains("PBO") -> ApdObjectType.PBO
                    else -> ApdObjectType.AUTRE
                }
            }
        }
    }
}
