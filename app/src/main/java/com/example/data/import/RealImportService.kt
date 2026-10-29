package com.example.data.import

import android.content.Context
import android.net.Uri
import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.local.NodeConformity
import com.example.data.local.NodeStatus
import com.example.data.storage.StoredTrack
import com.example.data.storage.TrackPoint
import com.example.data.util.TrackGeometryHelper
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Service d'importation de données géospatiales (KMZ, KML, GeoJSON).
 *
 * Prend en charge deux modes :
 * 1. NATIVE (Par défaut actuel) : Importation et affichage brut des données sans classification automatique.
 *    Les polylignes restent des tracés natifs fidèles à l'original (Google Earth / SIG), les points restent des
 *    points natifs préservant tous les attributs, sans créer de faux câbles ni forcer de types FTTH.
 * 2. INTELLIGENT_CLASSIFICATION (Méthode enregistrée et conservée) : Analyse heuristique déterminant automatiquement
 *    la distinction entre Génie Civil (GC) et Câbles optiques avec capacité en FO (xx FO).
 */
object RealImportService {

    enum class ImportMode(val label: String, val shortDescription: String) {
        NATIVE(
            label = "Mode Natif (Données brutes sans classification)",
            shortDescription = "Affiche fidèlement les tracés et points tels quels sans conversion automatique."
        ),
        INTELLIGENT_CLASSIFICATION(
            label = "Classification FTTH intelligente",
            shortDescription = "Détecte automatiquement les câbles optiques (capacité FO) et le Génie Civil (GC)."
        )
    }

    data class KmlFolderInfo(
        val name: String,
        val sourceFile: String = "",
        val tracksCount: Int = 0,
        val nodesCount: Int = 0,
        val totalDistanceMeters: Double = 0.0,
        val colorHex: String? = null,
        val isVisible: Boolean = true
    )

    data class KmlFileInfo(
        val fileName: String,
        val title: String = "",
        val folders: List<KmlFolderInfo> = emptyList(),
        val tracksCount: Int = 0,
        val nodesCount: Int = 0,
        val totalDistanceMeters: Double = 0.0,
        val isVisible: Boolean = true
    )

    private data class KmlParsedStyle(
        val colorHex: String? = null,
        val width: Float? = null
    )

    data class ImportResult(
        val nodes: List<FtthNodeEntity>,
        val links: List<FtthLinkEntity>,
        val tracks: List<StoredTrack>,
        val message: String,
        val documentTitle: String = "",
        val folders: List<KmlFolderInfo> = emptyList(),
        val fileName: String = ""
    )

    /**
     * Méthode principale d'importation.
     * Par défaut, utilise le mode NATIVE (sans classification automatique) comme demandé.
     */
    fun importFromFile(
        context: Context,
        uri: Uri,
        existingNodes: List<FtthNodeEntity> = emptyList(),
        mode: ImportMode = ImportMode.NATIVE
    ): ImportResult {
        val originalFileName = getFileName(context, uri)
        val fileNameLower = originalFileName.lowercase(Locale.ROOT)
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return ImportResult(emptyList(), emptyList(), emptyList(), "Impossible d'ouvrir le fichier sélectionné.")
        } catch (e: Exception) {
            return ImportResult(emptyList(), emptyList(), emptyList(), "Erreur de lecture du fichier : ${e.localizedMessage}")
        }

        if (bytes.isEmpty()) {
            return ImportResult(emptyList(), emptyList(), emptyList(), "Le fichier sélectionné est vide.")
        }

        return try {
            // Détection magique des fichiers ZIP (KMZ) : signature 0x50 0x4B (PK..)
            val isZip = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

            when {
                isZip || fileNameLower.endsWith(".kmz") -> {
                    importKmzFromBytes(bytes, existingNodes, mode, originalFileName, originalFileName)
                }
                fileNameLower.endsWith(".kml") -> {
                    val kmlText = String(bytes, StandardCharsets.UTF_8)
                    parseKmlString(kmlText, existingNodes, mode, originalFileName, originalFileName)
                }
                fileNameLower.endsWith(".geojson") || fileNameLower.endsWith(".json") -> {
                    val jsonText = String(bytes, StandardCharsets.UTF_8)
                    parseGeoJsonString(jsonText, existingNodes, mode, originalFileName)
                }
                else -> {
                    val text = String(bytes, StandardCharsets.UTF_8).trim()
                    if (text.startsWith("{") || text.startsWith("[")) {
                        parseGeoJsonString(text, existingNodes, mode, originalFileName)
                    } else if (text.contains("<kml", ignoreCase = true) || text.contains("<?xml", ignoreCase = true) || text.contains("<Document", ignoreCase = true)) {
                        parseKmlString(text, existingNodes, mode, originalFileName, originalFileName)
                    } else {
                        // Essai KML puis GeoJSON
                        val kmlRes = parseKmlString(text, existingNodes, mode, originalFileName, originalFileName)
                        if (kmlRes.nodes.isNotEmpty() || kmlRes.links.isNotEmpty() || kmlRes.tracks.isNotEmpty()) {
                            kmlRes
                        } else {
                            parseGeoJsonString(text, existingNodes, mode, originalFileName)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ImportResult(emptyList(), emptyList(), emptyList(), "Erreur d'import : ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Méthode explicite pour l'import avec classification automatique intelligente (conservée et enregistrée).
     */
    fun importFromFileWithClassification(
        context: Context,
        uri: Uri,
        existingNodes: List<FtthNodeEntity> = emptyList()
    ): ImportResult = importFromFile(context, uri, existingNodes, ImportMode.INTELLIGENT_CLASSIFICATION)

    /**
     * Méthode explicite pour l'import natif sans classification.
     */
    fun importFromFileNative(
        context: Context,
        uri: Uri,
        existingNodes: List<FtthNodeEntity> = emptyList()
    ): ImportResult = importFromFile(context, uri, existingNodes, ImportMode.NATIVE)

    private fun getFileName(context: Context, uri: Uri): String {
        var result = "import_file"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex("_display_name")
                if (idx != -1) {
                    val name = it.getString(idx)
                    if (!name.isNullOrBlank()) {
                        result = name
                    }
                }
            }
        }
        return result
    }

    private fun importKmzFromBytes(
        bytes: ByteArray,
        existingNodes: List<FtthNodeEntity>,
        mode: ImportMode,
        fallbackDocTitle: String = "",
        fileName: String = ""
    ): ImportResult {
        val fileSourceName = fileName.ifBlank { fallbackDocTitle.ifBlank { "Fichier KMZ" } }
        val kmlContents = mutableListOf<String>()
        try {
            val zipStream = ZipInputStream(ByteArrayInputStream(bytes))
            var entry = zipStream.nextEntry
            while (entry != null) {
                val nameLower = entry.name.lowercase(Locale.ROOT)
                if (!entry.isDirectory && nameLower.endsWith(".kml")) {
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var readLen: Int
                    while (zipStream.read(buffer).also { readLen = it } != -1) {
                        out.write(buffer, 0, readLen)
                    }
                    val kmlText = out.toString(StandardCharsets.UTF_8.name())
                    if (kmlText.isNotBlank()) {
                        kmlContents.add(kmlText)
                    }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            zipStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
            return ImportResult(emptyList(), emptyList(), emptyList(), "Erreur d'extraction de l'archive KMZ : ${e.localizedMessage}")
        }

        if (kmlContents.isNotEmpty()) {
            val allNodes = mutableListOf<FtthNodeEntity>()
            val allLinks = mutableListOf<FtthLinkEntity>()
            val allTracks = mutableListOf<StoredTrack>()
            val allFolders = mutableListOf<KmlFolderInfo>()
            var docTitle = fallbackDocTitle

            for (kml in kmlContents) {
                val combinedExisting = existingNodes + allNodes
                val res = parseKmlString(kml, combinedExisting, mode, fallbackDocTitle, fileSourceName)
                allNodes.addAll(res.nodes)
                allLinks.addAll(res.links)
                allTracks.addAll(res.tracks)
                allFolders.addAll(res.folders)
                if (res.documentTitle.isNotBlank() && (docTitle.isBlank() || docTitle == fallbackDocTitle)) {
                    docTitle = res.documentTitle
                }
            }

            // Dédupliquer les dossiers KML par nom en conservant le sourceFile
            val mergedFolders = allFolders
                .groupBy { it.name }
                .map { (name, list) ->
                    KmlFolderInfo(
                        name = name,
                        sourceFile = fileSourceName,
                        tracksCount = list.sumOf { it.tracksCount },
                        nodesCount = list.sumOf { it.nodesCount },
                        totalDistanceMeters = list.sumOf { it.totalDistanceMeters },
                        colorHex = list.firstOrNull { !it.colorHex.isNullOrBlank() }?.colorHex ?: getFolderDefaultColor(name),
                        isVisible = true
                    )
                }

            val msg = buildSummaryMessage(allNodes.size, allLinks.size, allTracks.size, "KMZ", mode)
            return ImportResult(
                nodes = allNodes,
                links = allLinks,
                tracks = allTracks,
                message = msg,
                documentTitle = docTitle.ifBlank { fallbackDocTitle },
                fileName = fileSourceName,
                folders = mergedFolders
            )
        }
        return ImportResult(emptyList(), emptyList(), emptyList(), "Aucun fichier KML valide trouvé dans l'archive KMZ.")
    }

    fun importKml(inputStream: InputStream, existingNodes: List<FtthNodeEntity>, mode: ImportMode = ImportMode.NATIVE): ImportResult {
        val content = inputStream.bufferedReader().use { it.readText() }
        return parseKmlString(content, existingNodes, mode)
    }

    private fun sanitizeXml(rawXml: String): String {
        var s = rawXml.removePrefix("\uFEFF").trim()
        // Remplacer les esperluettes '&' non échappées par '&amp;'
        s = s.replace(Regex("""&(?!(amp|lt|gt|quot|apos|#\d+|#x[0-9a-fA-F]+);)"""), "&amp;")
        return s
    }

    private val FOLDER_PALETTE = listOf(
        "#2563EB", // Bleu Royal
        "#9333EA", // Violet
        "#059669", // Émeraude
        "#EA580C", // Orange
        "#0284C7", // Cyan / Ciel
        "#D97706", // Ambre
        "#DC2626", // Rouge
        "#0D9488", // Sarcelle
        "#7C3AED", // Mauve
        "#4F46E5"  // Indigo
    )

    private fun getFolderDefaultColor(folderName: String): String {
        if (folderName.isBlank() || folderName == "Sans dossier") return "#2563EB"
        val hash = Math.abs(folderName.hashCode())
        return FOLDER_PALETTE[hash % FOLDER_PALETTE.size]
    }

    private fun kmlColorToAndroidHex(kmlColor: String?): String? {
        if (kmlColor.isNullOrBlank()) return null
        val clean = kmlColor.trim().removePrefix("#")
        return when (clean.length) {
            8 -> {
                // Format KML : AABBGGRR -> Convertir en Android #AARRGGBB
                val a = clean.substring(0, 2)
                val b = clean.substring(2, 4)
                val g = clean.substring(4, 6)
                val r = clean.substring(6, 8)
                "#$a$r$g$b"
            }
            6 -> {
                // BBGGRR -> #ffRRGGBB
                val b = clean.substring(0, 2)
                val g = clean.substring(2, 4)
                val r = clean.substring(4, 6)
                "#ff$r$g$b"
            }
            else -> null
        }
    }

    private fun getImmediateFolderName(element: Element): String {
        var parent = element.parentNode
        while (parent != null) {
            if (parent is Element) {
                if (parent.tagName.equals("Folder", ignoreCase = true)) {
                    val name = getDirectChildValue(parent, "name")
                    if (!name.isNullOrBlank()) {
                        return name
                    }
                }
            }
            parent = parent.parentNode
        }
        return ""
    }

    private fun getDirectChildValue(parent: Element, tagName: String): String? {
        var child = parent.firstChild
        while (child != null) {
            if (child is Element && child.tagName.equals(tagName, ignoreCase = true)) {
                return child.textContent?.trim()
            }
            child = child.nextSibling
        }
        return null
    }

    private fun resolvePlacemarkStyle(
        pm: Element,
        styleUrl: String,
        stylesMap: Map<String, KmlParsedStyle>,
        styleMaps: Map<String, String>
    ): KmlParsedStyle? {
        // 1. Style inline sous Placemark (<Style><LineStyle>...)
        val inlineStyles = pm.getElementsByTagName("Style")
        if (inlineStyles.length > 0) {
            val sEl = inlineStyles.item(0) as? Element
            if (sEl != null) {
                val ls = sEl.getElementsByTagName("LineStyle")
                var c: String? = null
                var w: Float? = null
                if (ls.length > 0) {
                    val lsEl = ls.item(0) as? Element
                    if (lsEl != null) {
                        c = kmlColorToAndroidHex(getDirectOrFirstChildValue(lsEl, "color"))
                        w = getDirectOrFirstChildValue(lsEl, "width")?.toFloatOrNull()
                    }
                }
                val ps = sEl.getElementsByTagName("PolyStyle")
                if (c == null && ps.length > 0) {
                    val psEl = ps.item(0) as? Element
                    if (psEl != null) {
                        c = kmlColorToAndroidHex(getDirectOrFirstChildValue(psEl, "color"))
                    }
                }
                if (c != null || w != null) {
                    return KmlParsedStyle(c, w)
                }
            }
        }
        // 2. Référence styleUrl (#style_id)
        if (styleUrl.isNotBlank()) {
            val cleanUrl = styleUrl.removePrefix("#")
            val targetId = styleMaps[cleanUrl] ?: cleanUrl
            return stylesMap[targetId]
        }
        return null
    }

    fun parseKmlString(
        kml: String,
        existingNodes: List<FtthNodeEntity>,
        mode: ImportMode = ImportMode.NATIVE,
        fallbackDocTitle: String = "",
        fileName: String = ""
    ): ImportResult {
        val fileSourceName = fileName.ifBlank { fallbackDocTitle.ifBlank { "Fichier KML" } }
        val nodes = mutableListOf<FtthNodeEntity>()
        val links = mutableListOf<FtthLinkEntity>()
        val tracks = mutableListOf<StoredTrack>()
        var extractedDocTitle = fallbackDocTitle

        try {
            val sanitized = sanitizeXml(kml)
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            factory.isValidating = false
            try { factory.isCoalescing = true } catch (_: Exception) {}
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(sanitized.toByteArray(StandardCharsets.UTF_8)))
            doc.documentElement.normalize()

            // 1. Extraire le titre du Document KML
            val docEls = doc.getElementsByTagName("Document")
            if (docEls.length > 0) {
                val docEl = docEls.item(0) as? Element
                val docName = docEl?.let { getDirectChildValue(it, "name") }
                if (!docName.isNullOrBlank()) {
                    extractedDocTitle = docName
                }
            }
            if (extractedDocTitle.isBlank()) {
                val kmlName = getDirectChildValue(doc.documentElement, "name")
                if (!kmlName.isNullOrBlank()) {
                    extractedDocTitle = kmlName
                }
            }

            // 2. Extraire la table des Styles KML (<Style id="...">)
            val stylesMap = mutableMapOf<String, KmlParsedStyle>()
            val styleNodes = doc.getElementsByTagName("Style")
            for (s in 0 until styleNodes.length) {
                val styleEl = styleNodes.item(s) as? Element ?: continue
                val id = styleEl.getAttribute("id")
                if (id.isNotBlank()) {
                    var color: String? = null
                    var width: Float? = null
                    val lineStyles = styleEl.getElementsByTagName("LineStyle")
                    if (lineStyles.length > 0) {
                        val lsEl = lineStyles.item(0) as? Element
                        if (lsEl != null) {
                            color = kmlColorToAndroidHex(getDirectOrFirstChildValue(lsEl, "color"))
                            width = getDirectOrFirstChildValue(lsEl, "width")?.toFloatOrNull()
                        }
                    }
                    val polyStyles = styleEl.getElementsByTagName("PolyStyle")
                    if (color == null && polyStyles.length > 0) {
                        val psEl = polyStyles.item(0) as? Element
                        if (psEl != null) {
                            color = kmlColorToAndroidHex(getDirectOrFirstChildValue(psEl, "color"))
                        }
                    }
                    stylesMap[id] = KmlParsedStyle(colorHex = color, width = width)
                }
            }

            // 3. Extraire les StyleMap (<StyleMap id="...">)
            val styleMaps = mutableMapOf<String, String>()
            val styleMapNodes = doc.getElementsByTagName("StyleMap")
            for (sm in 0 until styleMapNodes.length) {
                val smEl = styleMapNodes.item(sm) as? Element ?: continue
                val id = smEl.getAttribute("id")
                if (id.isNotBlank()) {
                    val pairs = smEl.getElementsByTagName("Pair")
                    var targetStyleUrl: String? = null
                    for (p in 0 until pairs.length) {
                        val pairEl = pairs.item(p) as? Element ?: continue
                        val key = getDirectOrFirstChildValue(pairEl, "key")
                        val sUrl = getDirectOrFirstChildValue(pairEl, "styleUrl")
                        if (key == "normal" && !sUrl.isNullOrBlank()) {
                            targetStyleUrl = sUrl.removePrefix("#")
                            break
                        } else if (targetStyleUrl == null && !sUrl.isNullOrBlank()) {
                            targetStyleUrl = sUrl.removePrefix("#")
                        }
                    }
                    if (targetStyleUrl != null) {
                        styleMaps[id] = targetStyleUrl
                    }
                }
            }

            // 4. Parcourir tous les Placemarks
            val placemarks = doc.getElementsByTagName("Placemark")
            for (i in 0 until placemarks.length) {
                val pm = placemarks.item(i) as? Element ?: continue
                val rawKmlName = getDirectOrFirstChildValue(pm, "name") ?: "Objet-${i + 1}"
                val desc = getDirectOrFirstChildValue(pm, "description") ?: ""
                val styleUrl = getDirectOrFirstChildValue(pm, "styleUrl") ?: ""
                val immediateFolder = getImmediateFolderName(pm)
                val folderHierarchy = getParentFolderHierarchy(pm)

                // Extraire ExtendedData : <Data name="..."> et <SimpleData name="..."> (GIS / QGIS)
                val properties = linkedMapOf<String, String>()
                val dataNodes = pm.getElementsByTagName("Data")
                for (j in 0 until dataNodes.length) {
                    val dataEl = dataNodes.item(j) as? Element ?: continue
                    val key = dataEl.getAttribute("name").ifBlank { getDirectOrFirstChildValue(dataEl, "displayName") ?: "" }
                    val value = getDirectOrFirstChildValue(dataEl, "value") ?: ""
                    if (key.isNotBlank()) properties[key] = value
                }
                val simpleDataNodes = pm.getElementsByTagName("SimpleData")
                for (j in 0 until simpleDataNodes.length) {
                    val sEl = simpleDataNodes.item(j) as? Element ?: continue
                    val key = sEl.getAttribute("name")
                    val value = sEl.textContent.trim()
                    if (key.isNotBlank()) properties[key] = value
                }
                // Extraire également les attributs des tables HTML dans <description> (format QGIS / ArcGIS / Google Earth)
                if (desc.isNotBlank()) {
                    extractHtmlTableProperties(desc, properties)
                }

                // Résoudre le vrai nom de l'entité si <name> KML est juste une capacité (ex: "288FO")
                val name = resolveSmartName(rawKmlName, properties)
                val folderContext = if (folderHierarchy.isNotEmpty()) "$folderHierarchy / $name" else name

                // Résolution de couleur et style
                val parsedStyle = resolvePlacemarkStyle(pm, styleUrl, stylesMap, styleMaps)
                val itemColor = parsedStyle?.colorHex ?: getFolderDefaultColor(immediateFolder)
                val itemWidth = parsedStyle?.width ?: 7f

                // Extraire géométries
                val lineStringEls = pm.getElementsByTagName("LineString")
                val pointEls = pm.getElementsByTagName("Point")
                val polygonEls = pm.getElementsByTagName("Polygon")
                var parsedAnyGeometry = false

                // A. Traiter tous les LineStrings sans exclusion
                if (lineStringEls.length > 0) {
                    for (l in 0 until lineStringEls.length) {
                        val lineEl = lineStringEls.item(l) as? Element ?: continue
                        val coordsText = getDirectOrFirstChildValue(lineEl, "coordinates") ?: continue
                        val pts = parseKmlCoordinates(coordsText)
                        if (pts.size >= 2) {
                            parsedAnyGeometry = true
                            val lineName = if (lineStringEls.length > 1) "$name ($l)" else name
                            if (mode == ImportMode.NATIVE) {
                                processLineStringNative(
                                    name = lineName,
                                    description = desc,
                                    styleUrl = styleUrl,
                                    folderName = immediateFolder,
                                    folderContext = folderContext,
                                    colorHex = itemColor,
                                    strokeWidth = itemWidth,
                                    properties = properties,
                                    pts = pts,
                                    tracks = tracks,
                                    index = tracks.size,
                                    sourceFile = fileSourceName
                                )
                            } else {
                                processLineStringClassification(
                                    name = lineName,
                                    description = desc,
                                    styleUrl = styleUrl,
                                    folderContext = folderContext,
                                    properties = properties,
                                    pts = pts,
                                    existingNodes = existingNodes,
                                    nodes = nodes,
                                    links = links,
                                    tracks = tracks,
                                    index = tracks.size + links.size,
                                    sourceFile = fileSourceName
                                )
                            }
                        }
                    }
                }

                // B. Traiter tous les Points (indépendamment des LineStrings)
                if (pointEls.length > 0) {
                    for (p in 0 until pointEls.length) {
                        val ptEl = pointEls.item(p) as? Element ?: continue
                        val coordsText = getDirectOrFirstChildValue(ptEl, "coordinates") ?: continue
                        val pts = parseKmlCoordinates(coordsText)
                        if (pts.isNotEmpty()) {
                            parsedAnyGeometry = true
                            val pt = pts.first()
                            val ptName = if (pointEls.length > 1) "$name ($p)" else name
                            if (mode == ImportMode.NATIVE) {
                                nodes.add(
                                    createNativeNode(
                                        id = "KML-NOD-${System.currentTimeMillis()}-${nodes.size}",
                                        name = ptName,
                                        description = desc,
                                        folderName = immediateFolder,
                                        folderContext = folderContext,
                                        colorHex = itemColor,
                                        lat = pt.latitude,
                                        lon = pt.longitude,
                                        properties = properties,
                                        sourceFile = fileSourceName
                                    )
                                )
                            } else {
                                val nodeType = determineNodeType(folderContext, desc, styleUrl, properties)
                                nodes.add(
                                    createNodeFromAttributesWithClassification(
                                        id = "KML-NOD-${System.currentTimeMillis()}-${nodes.size}",
                                        type = nodeType,
                                        name = ptName,
                                        description = desc,
                                        lat = pt.latitude,
                                        lon = pt.longitude,
                                        properties = properties,
                                        sourceFile = fileSourceName
                                    )
                                )
                            }
                        }
                    }
                }

                // C. Traiter tous les Polygons
                if (polygonEls.length > 0) {
                    for (pg in 0 until polygonEls.length) {
                        val polyEl = polygonEls.item(pg) as? Element ?: continue
                        val coordsText = getDirectOrFirstChildValue(polyEl, "coordinates") ?: continue
                        val pts = parseKmlCoordinates(coordsText)
                        if (pts.isNotEmpty()) {
                            parsedAnyGeometry = true
                            val polyName = if (polygonEls.length > 1) "$name ($pg)" else name
                            if (mode == ImportMode.NATIVE) {
                                processPolygonNative(
                                    name = polyName,
                                    description = desc,
                                    folderName = immediateFolder,
                                    folderContext = folderContext,
                                    colorHex = itemColor,
                                    strokeWidth = itemWidth,
                                    properties = properties,
                                    pts = pts,
                                    tracks = tracks,
                                    nodes = nodes,
                                    index = tracks.size,
                                    sourceFile = fileSourceName
                                )
                            } else {
                                val lat = pts.map { it.latitude }.average()
                                val lon = pts.map { it.longitude }.average()
                                val nodeType = determineNodeType(folderContext, desc, styleUrl, properties)
                                nodes.add(
                                    createNodeFromAttributesWithClassification(
                                        id = "KML-NOD-${System.currentTimeMillis()}-${nodes.size}",
                                        type = nodeType,
                                        name = polyName,
                                        description = desc,
                                        lat = lat,
                                        lon = lon,
                                        properties = properties,
                                        sourceFile = fileSourceName
                                    )
                                )
                            }
                        }
                    }
                }

                // D. Coordonnées directes sous Placemark si aucune géométrie enfant trouvée
                if (!parsedAnyGeometry) {
                    val coordsText = getDirectOrFirstChildValue(pm, "coordinates")
                    if (coordsText != null) {
                        val pts = parseKmlCoordinates(coordsText)
                        if (pts.size >= 2) {
                            if (mode == ImportMode.NATIVE) {
                                processLineStringNative(
                                    name = name,
                                    description = desc,
                                    styleUrl = styleUrl,
                                    folderName = immediateFolder,
                                    folderContext = folderContext,
                                    colorHex = itemColor,
                                    strokeWidth = itemWidth,
                                    properties = properties,
                                    pts = pts,
                                    tracks = tracks,
                                    index = tracks.size,
                                    sourceFile = fileSourceName
                                )
                            } else {
                                processLineStringClassification(
                                    name = name,
                                    description = desc,
                                    styleUrl = styleUrl,
                                    folderContext = folderContext,
                                    properties = properties,
                                    pts = pts,
                                    existingNodes = existingNodes,
                                    nodes = nodes,
                                    links = links,
                                    tracks = tracks,
                                    index = tracks.size + links.size,
                                    sourceFile = fileSourceName
                                )
                            }
                        } else if (pts.isNotEmpty()) {
                            val pt = pts.first()
                            if (mode == ImportMode.NATIVE) {
                                nodes.add(
                                    createNativeNode(
                                        id = "KML-NOD-${System.currentTimeMillis()}-${nodes.size}",
                                        name = name,
                                        description = desc,
                                        folderName = immediateFolder,
                                        folderContext = folderContext,
                                        colorHex = itemColor,
                                        lat = pt.latitude,
                                        lon = pt.longitude,
                                        properties = properties,
                                        sourceFile = fileSourceName
                                    )
                                )
                            } else {
                                val nodeType = determineNodeType(folderContext, desc, styleUrl, properties)
                                nodes.add(
                                    createNodeFromAttributesWithClassification(
                                        id = "KML-NOD-${System.currentTimeMillis()}-${nodes.size}",
                                        type = nodeType,
                                        name = name,
                                        description = desc,
                                        lat = pt.latitude,
                                        lon = pt.longitude,
                                        properties = properties,
                                        sourceFile = fileSourceName
                                    )
                                )
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return ImportResult(emptyList(), emptyList(), emptyList(), "Erreur d'analyse KML : ${e.localizedMessage}")
        }

        // Construire la liste des dossiers KML (reproduisant l'arborescence Google Earth)
        val folderTracksMap = tracks.groupBy { it.folderName.ifBlank { "Sans dossier" } }
        val folderNodesMap = nodes.groupBy { it.folderName.ifBlank { "Sans dossier" } }
        val allFolderNames = (folderTracksMap.keys + folderNodesMap.keys).distinct()

        val kmlFolders = allFolderNames.map { fName ->
            val fTracks = folderTracksMap[fName] ?: emptyList()
            val fNodes = folderNodesMap[fName] ?: emptyList()
            val totalDist = fTracks.sumOf { it.totalDistanceMeters }
            val color = fTracks.firstOrNull { !it.colorHex.isNullOrBlank() }?.colorHex
                ?: fNodes.firstOrNull { !it.colorHex.isNullOrBlank() }?.colorHex
                ?: getFolderDefaultColor(fName)

            KmlFolderInfo(
                name = fName,
                sourceFile = fileSourceName,
                tracksCount = fTracks.size,
                nodesCount = fNodes.size,
                totalDistanceMeters = totalDist,
                colorHex = color,
                isVisible = true
            )
        }

        val msg = buildSummaryMessage(nodes.size, links.size, tracks.size, "KML", mode)
        return ImportResult(
            nodes = nodes,
            links = links,
            tracks = tracks,
            message = msg,
            documentTitle = extractedDocTitle.ifBlank { fallbackDocTitle },
            fileName = fileSourceName,
            folders = kmlFolders
        )
    }

    private fun getParentFolderHierarchy(element: Element): String {
        val folders = mutableListOf<String>()
        var parent = element.parentNode
        while (parent != null) {
            if (parent is Element) {
                val tag = parent.tagName
                if (tag.equals("Folder", ignoreCase = true) || tag.equals("Document", ignoreCase = true)) {
                    val name = getDirectOrFirstChildValue(parent, "name")
                    if (!name.isNullOrBlank() && !name.equals("Document", ignoreCase = true)) {
                        folders.add(0, name)
                    }
                }
            }
            parent = parent.parentNode
        }
        return folders.joinToString(" / ")
    }

    private fun getDirectOrFirstChildValue(parent: Element, tagName: String): String? {
        var child = parent.firstChild
        while (child != null) {
            if (child is Element && child.tagName.equals(tagName, ignoreCase = true)) {
                return child.textContent?.trim()
            }
            child = child.nextSibling
        }
        val list = parent.getElementsByTagName(tagName)
        if (list.length > 0) {
            val el = list.item(0) as? Element
            return el?.textContent?.trim()
        }
        return null
    }

    private fun parseKmlCoordinates(coordsText: String): List<TrackPoint> {
        val points = mutableListOf<TrackPoint>()
        if (coordsText.isBlank()) return points
        // Normaliser les espaces autour des virgules pour que "lon, lat, alt" devienne "lon,lat,alt"
        val normalized = coordsText.replace(Regex("""\s*,\s*"""), ",")
        val tokens = normalized.trim().split(Regex("""[\s\r\n]+"""))
        for (token in tokens) {
            if (token.isBlank()) continue
            val parts = token.split(",")
            if (parts.size >= 2) {
                val lon = parts[0].trim().toDoubleOrNull() ?: continue
                val lat = parts[1].trim().toDoubleOrNull() ?: continue
                val alt = if (parts.size >= 3) parts[2].trim().toDoubleOrNull() ?: 0.0 else 0.0
                if (lat in -90.0..90.0 && lon in -180.0..180.0 && (lat != 0.0 || lon != 0.0)) {
                    points.add(TrackPoint(latitude = lat, longitude = lon, altitude = alt))
                }
            }
        }
        return points
    }

    fun importGeoJson(inputStream: InputStream, existingNodes: List<FtthNodeEntity>, mode: ImportMode = ImportMode.NATIVE): ImportResult {
        val jsonStr = inputStream.bufferedReader().use { it.readText() }
        return parseGeoJsonString(jsonStr, existingNodes, mode)
    }

    fun parseGeoJsonString(
        jsonStr: String,
        existingNodes: List<FtthNodeEntity>,
        mode: ImportMode = ImportMode.NATIVE,
        fileName: String = ""
    ): ImportResult {
        val fileSourceName = fileName.ifBlank { "Fichier GeoJSON" }
        val nodes = mutableListOf<FtthNodeEntity>()
        val links = mutableListOf<FtthLinkEntity>()
        val tracks = mutableListOf<StoredTrack>()

        try {
            val root = JSONObject(jsonStr)
            val features = if (root.has("features")) {
                root.getJSONArray("features")
            } else if (root.has("geometry")) {
                JSONArray().put(root)
            } else {
                JSONArray()
            }

            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val geometry = feature.optJSONObject("geometry") ?: continue
                val geomType = geometry.optString("type")
                val coords = geometry.optJSONArray("coordinates") ?: continue

                val properties = feature.optJSONObject("properties") ?: JSONObject()
                val propMap = mutableMapOf<String, String>()
                val keys = properties.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    propMap[k] = properties.optString(k, "")
                }

                val rawGeoName = propMap["name"] ?: propMap["NOM"] ?: propMap["Nom"] ?: propMap["nom"] ?: propMap["CODE"] ?: propMap["ID"] ?: "Objet-${i + 1}"
                val name = resolveSmartName(rawGeoName, propMap)
                val description = propMap["description"] ?: propMap["desc"] ?: ""

                when {
                    geomType.equals("Point", ignoreCase = true) && coords.length() >= 2 -> {
                        val lon = coords.getDouble(0)
                        val lat = coords.getDouble(1)
                        if (lat != 0.0 || lon != 0.0) {
                            if (mode == ImportMode.NATIVE) {
                                nodes.add(
                                    createNativeNode(
                                        id = "GEO-NOD-${System.currentTimeMillis()}-$i",
                                        name = name,
                                        description = description,
                                        folderContext = name,
                                        lat = lat,
                                        lon = lon,
                                        properties = propMap,
                                        sourceFile = fileSourceName
                                    )
                                )
                            } else {
                                val nodeType = determineNodeType(name, description, "", propMap)
                                nodes.add(
                                    createNodeFromAttributesWithClassification(
                                        id = "GEO-NOD-${System.currentTimeMillis()}-$i",
                                        type = nodeType,
                                        name = name,
                                        description = description,
                                        lat = lat,
                                        lon = lon,
                                        properties = propMap,
                                        sourceFile = fileSourceName
                                    )
                                )
                            }
                        }
                    }
                    geomType.equals("LineString", ignoreCase = true) && coords.length() > 0 -> {
                        val points = mutableListOf<TrackPoint>()
                        for (j in 0 until coords.length()) {
                            val ptArr = coords.getJSONArray(j)
                            if (ptArr.length() >= 2) {
                                val lon = ptArr.getDouble(0)
                                val lat = ptArr.getDouble(1)
                                val alt = if (ptArr.length() >= 3) ptArr.getDouble(2) else 0.0
                                points.add(TrackPoint(latitude = lat, longitude = lon, altitude = alt))
                            }
                        }
                        if (points.size >= 2) {
                            if (mode == ImportMode.NATIVE) {
                                processLineStringNative(
                                    name = name,
                                    description = description,
                                    styleUrl = "",
                                    folderContext = name,
                                    properties = propMap,
                                    pts = points,
                                    tracks = tracks,
                                    index = i,
                                    sourceFile = fileSourceName
                                )
                            } else {
                                processLineStringClassification(
                                    name = name,
                                    description = description,
                                    styleUrl = "",
                                    folderContext = name,
                                    properties = propMap,
                                    pts = points,
                                    existingNodes = existingNodes,
                                    nodes = nodes,
                                    links = links,
                                    tracks = tracks,
                                    index = i,
                                    sourceFile = fileSourceName
                                )
                            }
                        }
                    }
                    geomType.equals("MultiLineString", ignoreCase = true) && coords.length() > 0 -> {
                        for (m in 0 until coords.length()) {
                            val lineArr = coords.getJSONArray(m)
                            val points = mutableListOf<TrackPoint>()
                            for (j in 0 until lineArr.length()) {
                                val ptArr = lineArr.getJSONArray(j)
                                if (ptArr.length() >= 2) {
                                    val lon = ptArr.getDouble(0)
                                    val lat = ptArr.getDouble(1)
                                    val alt = if (ptArr.length() >= 3) ptArr.getDouble(2) else 0.0
                                    points.add(TrackPoint(latitude = lat, longitude = lon, altitude = alt))
                                }
                            }
                            if (points.size >= 2) {
                                val lineName = "$name (${m + 1})"
                                if (mode == ImportMode.NATIVE) {
                                    processLineStringNative(
                                        name = lineName,
                                        description = description,
                                        styleUrl = "",
                                        folderContext = name,
                                        properties = propMap,
                                        pts = points,
                                        tracks = tracks,
                                        index = i * 100 + m,
                                        sourceFile = fileSourceName
                                    )
                                } else {
                                    processLineStringClassification(
                                        name = lineName,
                                        description = description,
                                        styleUrl = "",
                                        folderContext = name,
                                        properties = propMap,
                                        pts = points,
                                        existingNodes = existingNodes,
                                        nodes = nodes,
                                        links = links,
                                        tracks = tracks,
                                        index = i * 100 + m,
                                        sourceFile = fileSourceName
                                    )
                                }
                            }
                        }
                    }
                    geomType.equals("Polygon", ignoreCase = true) && coords.length() > 0 -> {
                        val ring = coords.getJSONArray(0)
                        val points = mutableListOf<TrackPoint>()
                        for (j in 0 until ring.length()) {
                            val ptArr = ring.getJSONArray(j)
                            if (ptArr.length() >= 2) {
                                points.add(TrackPoint(latitude = ptArr.getDouble(1), longitude = ptArr.getDouble(0)))
                            }
                        }
                        if (points.isNotEmpty()) {
                            if (mode == ImportMode.NATIVE) {
                                processPolygonNative(
                                    name = name,
                                    description = description,
                                    folderContext = name,
                                    properties = propMap,
                                    pts = points,
                                    tracks = tracks,
                                    nodes = nodes,
                                    index = i,
                                    sourceFile = fileSourceName
                                )
                            } else {
                                val lat = points.map { it.latitude }.average()
                                val lon = points.map { it.longitude }.average()
                                val nodeType = determineNodeType(name, description, "", propMap)
                                nodes.add(
                                    createNodeFromAttributesWithClassification(
                                        id = "GEO-NOD-${System.currentTimeMillis()}-$i",
                                        type = nodeType,
                                        name = name,
                                        description = description,
                                        lat = lat,
                                        lon = lon,
                                        properties = propMap,
                                        sourceFile = fileSourceName
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return ImportResult(emptyList(), emptyList(), emptyList(), "Erreur d'analyse GeoJSON : ${e.localizedMessage}")
        }

        val folderTracksMap = tracks.groupBy { it.folderName.ifBlank { "Sans dossier" } }
        val folderNodesMap = nodes.groupBy { it.folderName.ifBlank { "Sans dossier" } }
        val allFolderNames = (folderTracksMap.keys + folderNodesMap.keys).distinct()
        val geoFolders = allFolderNames.map { fName ->
            val fTracks = folderTracksMap[fName] ?: emptyList()
            val fNodes = folderNodesMap[fName] ?: emptyList()
            KmlFolderInfo(
                name = fName,
                sourceFile = fileSourceName,
                tracksCount = fTracks.size,
                nodesCount = fNodes.size,
                totalDistanceMeters = fTracks.sumOf { it.totalDistanceMeters },
                colorHex = getFolderDefaultColor(fName),
                isVisible = true
            )
        }

        val msg = buildSummaryMessage(nodes.size, links.size, tracks.size, "GeoJSON", mode)
        return ImportResult(
            nodes = nodes,
            links = links,
            tracks = tracks,
            message = msg,
            documentTitle = fileSourceName,
            fileName = fileSourceName,
            folders = geoFolders
        )
    }

    // =========================================================================================
    //  TRAITEMENT NATIF DES DONNÉES (Affichage direct sans classification automatique)
    // =========================================================================================

    private fun processLineStringNative(
        name: String,
        description: String,
        styleUrl: String,
        folderName: String = "",
        folderContext: String,
        colorHex: String? = null,
        strokeWidth: Float? = null,
        properties: Map<String, String>,
        pts: List<TrackPoint>,
        tracks: MutableList<StoredTrack>,
        index: Int,
        sourceFile: String = ""
    ) {
        val dist = TrackGeometryHelper.computeTotalDistanceMeters(pts)
        val trackId = "TRK-NAT-${System.currentTimeMillis() % 100000}-$index"
        val notes = buildNativeNotes(description, folderContext, properties)
        val propsJson = try { org.json.JSONObject(properties as Map<*, *>).toString() } catch (_: Exception) { "" }

        tracks.add(
            StoredTrack(
                id = trackId,
                name = name.ifBlank { "Tracé #${index + 1}" },
                type = "Tracé", // Type natif
                etat = "Conforme",
                conduitAudit = "Natif",
                conduitType = "Natif",
                notes = notes,
                totalDistanceMeters = dist,
                rawPoints = pts,
                points = pts,
                folderName = folderName,
                colorHex = colorHex,
                strokeWidth = strokeWidth,
                isVisible = true,
                isNativeKml = true,
                kmlProperties = propsJson,
                sourceFile = sourceFile
            )
        )
    }

    private fun createNativeNode(
        id: String,
        name: String,
        description: String,
        folderName: String = "",
        folderContext: String,
        colorHex: String? = null,
        lat: Double,
        lon: Double,
        properties: Map<String, String>,
        sourceFile: String = ""
    ): FtthNodeEntity {
        val notes = buildNativeNotes(description, folderContext, properties)
        val address = properties["address"] ?: properties["adresse"] ?: ""
        val operator = properties["operator"] ?: properties["operateur"] ?: properties["layer"] ?: ""
        val propsJson = try { org.json.JSONObject(properties as Map<*, *>).toString() } catch (_: Exception) { "" }

        return FtthNodeEntity(
            id = id,
            type = FtthNodeType.BOITIER, // Marker neutre pour affichage natif
            name = name.ifBlank { "Point" },
            buildingName = name,
            latitude = lat,
            longitude = lon,
            status = NodeStatus.EXISTANT,
            etat = NodeConformity.CONFORME,
            address = address,
            operator = operator,
            notes = notes,
            folderName = folderName,
            colorHex = colorHex,
            isVisible = true,
            isNativeKml = true,
            kmlProperties = propsJson,
            sourceFile = sourceFile
        )
    }

    private fun processPolygonNative(
        name: String,
        description: String,
        folderName: String = "",
        folderContext: String,
        colorHex: String? = null,
        strokeWidth: Float? = null,
        properties: Map<String, String>,
        pts: List<TrackPoint>,
        tracks: MutableList<StoredTrack>,
        nodes: MutableList<FtthNodeEntity>,
        index: Int,
        sourceFile: String = ""
    ) {
        if (pts.isEmpty()) return
        val closedPts = if (pts.first().latitude != pts.last().latitude || pts.first().longitude != pts.last().longitude) {
            pts + pts.first()
        } else {
            pts
        }
        val dist = TrackGeometryHelper.computeTotalDistanceMeters(closedPts)
        val notes = buildNativeNotes(description, folderContext, properties)
        val propsJson = try { org.json.JSONObject(properties as Map<*, *>).toString() } catch (_: Exception) { "" }

        tracks.add(
            StoredTrack(
                id = "POLY-NAT-${System.currentTimeMillis() % 100000}-$index",
                name = name.ifBlank { "Polygone #${index + 1}" },
                type = "Polygone",
                etat = "Conforme",
                conduitAudit = "Natif",
                conduitType = "Natif",
                notes = notes,
                totalDistanceMeters = dist,
                rawPoints = closedPts,
                points = closedPts,
                folderName = folderName,
                colorHex = colorHex,
                strokeWidth = strokeWidth,
                isVisible = true,
                isNativeKml = true,
                kmlProperties = propsJson,
                sourceFile = sourceFile
            )
        )
    }

    private fun extractHtmlTableProperties(html: String, map: MutableMap<String, String>) {
        try {
            val rowRegex = Regex("""<tr[^>]*>\s*<(?:td|th)[^>]*>(.*?)</(?:td|th)>\s*<td[^>]*>(.*?)</td>\s*</tr>""", RegexOption.IGNORE_CASE)
            for (match in rowRegex.findAll(html)) {
                val rawKey = match.groupValues[1].replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").trim()
                val rawVal = match.groupValues[2].replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").trim()
                if (rawKey.isNotBlank() && rawVal.isNotBlank() && !map.containsKey(rawKey)) {
                    map[rawKey] = rawVal
                }
            }
        } catch (_: Exception) {}
    }

    private fun buildNativeNotes(description: String, folderContext: String, properties: Map<String, String>): String {
        val sb = StringBuilder()
        if (description.isNotBlank()) {
            sb.append(description.trim())
        }
        if (folderContext.isNotBlank() && !folderContext.equals(description, ignoreCase = true)) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Dossier KML : ").append(folderContext)
        }
        val filtered = properties.filter { (k, v) ->
            !k.equals("name", ignoreCase = true) &&
            !k.equals("description", ignoreCase = true) &&
            v.isNotBlank()
        }
        if (filtered.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n\nAttributs SIG :\n") else sb.append("Attributs SIG :\n")
            for ((k, v) in filtered) {
                sb.append("• ").append(k).append(" : ").append(v).append("\n")
            }
        }
        return sb.toString().trim()
    }

    // =========================================================================================
    //  MÉTHODE DE CLASSIFICATION INTELLIGENTE (ENREGISTRÉE ET CONSERVÉE)
    // =========================================================================================

    /**
     * Traitement avec classification intelligente des polylignes / LineString :
     * Détermine s'il s'agit d'un CÂBLE OPTIQUE avec sa capacité en FO ou d'une INFRASTRUCTURE GC.
     */
    private fun processLineStringClassification(
        name: String,
        description: String,
        styleUrl: String,
        folderContext: String,
        properties: Map<String, String>,
        pts: List<TrackPoint>,
        existingNodes: List<FtthNodeEntity>,
        nodes: MutableList<FtthNodeEntity>,
        links: MutableList<FtthLinkEntity>,
        tracks: MutableList<StoredTrack>,
        index: Int,
        sourceFile: String = ""
    ) {
        val dist = TrackGeometryHelper.computeTotalDistanceMeters(pts)
        val lineInfo = classifyLineString(name, description, styleUrl, folderContext, properties)

        if (lineInfo.isCable) {
            // C'est un CÂBLE OPTIQUE avec capacité en FO
            val trackId = "TRK-CBL-${System.currentTimeMillis()}-$index"
            val cableId = "CBL-${System.currentTimeMillis() % 100000}-$index"

            val allNodesPool = existingNodes + nodes
            val startPt = pts.first()
            val endPt = pts.last()

            var fromNode = findClosestNode(startPt.latitude, startPt.longitude, allNodesPool, maxDistanceMeters = 75.0)
            var toNode = findClosestNode(endPt.latitude, endPt.longitude, allNodesPool, maxDistanceMeters = 75.0)

            if (fromNode == null) {
                val startNodeType = when (lineInfo.installationType.lowercase(Locale.ROOT)) {
                    "aérien" -> FtthNodeType.POTEAU
                    "façade" -> FtthNodeType.BOITIER
                    else -> FtthNodeType.CHAMBRE
                }
                fromNode = FtthNodeEntity(
                    id = "EXT-A-${System.currentTimeMillis() % 10000}-$index",
                    type = startNodeType,
                    name = "Extrémité $name",
                    latitude = startPt.latitude,
                    longitude = startPt.longitude,
                    notes = "Généré automatiquement lors de l'import du câble ${lineInfo.capacityFO} FO",
                    sourceFile = sourceFile
                )
                nodes.add(fromNode)
            }

            if (toNode == null || toNode.id == fromNode.id) {
                val endNodeType = when (lineInfo.installationType.lowercase(Locale.ROOT)) {
                    "aérien" -> FtthNodeType.POTEAU
                    "façade" -> FtthNodeType.BOITIER
                    else -> FtthNodeType.CHAMBRE
                }
                toNode = FtthNodeEntity(
                    id = "EXT-B-${System.currentTimeMillis() % 10000}-$index",
                    type = endNodeType,
                    name = "Extrémité $name (B)",
                    latitude = endPt.latitude,
                    longitude = endPt.longitude,
                    notes = "Généré automatiquement lors de l'import du câble ${lineInfo.capacityFO} FO",
                    sourceFile = sourceFile
                )
                nodes.add(toNode)
            }

            val link = FtthLinkEntity(
                id = cableId,
                fromNodeId = fromNode.id,
                toNodeId = toNode.id,
                cableType = lineInfo.cableType,
                installationType = lineInfo.installationType,
                capacityFO = lineInfo.capacityFO,
                lengthMeters = dist,
                associatedTrackId = trackId
            )
            links.add(link)

            val propsJson = try { org.json.JSONObject(properties as Map<*, *>).toString() } catch (_: Exception) { "" }
            val cableTrackName = if (name.contains("FO", ignoreCase = true)) name else "$name (${lineInfo.capacityFO} FO)"
            tracks.add(
                StoredTrack(
                    id = trackId,
                    name = cableTrackName,
                    type = if (lineInfo.installationType == "Souterrain") "GC" else lineInfo.installationType,
                    etat = "Conforme",
                    notes = "Câble ${lineInfo.capacityFO} FO [${lineInfo.cableType} - ${lineInfo.installationType}]",
                    totalDistanceMeters = dist,
                    rawPoints = pts,
                    points = pts,
                    kmlProperties = propsJson,
                    sourceFile = sourceFile
                )
            )

        } else {
            // C'est une INFRASTRUCTURE GÉNIE CIVIL (GC / Conduite / Tranchée)
            val propsJson = try { org.json.JSONObject(properties as Map<*, *>).toString() } catch (_: Exception) { "" }
            val trackId = "TRK-GC-${System.currentTimeMillis()}-$index"
            tracks.add(
                StoredTrack(
                    id = trackId,
                    name = name,
                    type = lineInfo.infrastructureType,
                    etat = "Conforme",
                    conduitType = lineInfo.conduitType,
                    conduitCount = lineInfo.conduitCount,
                    conduitDiameters = listOf("Ø 40"),
                    conduitAudit = "Libres",
                    notes = description.ifBlank { "Infrastructure ${lineInfo.infrastructureType}" },
                    totalDistanceMeters = dist,
                    rawPoints = pts,
                    points = pts,
                    kmlProperties = propsJson,
                    sourceFile = sourceFile
                )
            )
        }
    }

    private fun findClosestNode(
        lat: Double,
        lon: Double,
        candidates: List<FtthNodeEntity>,
        maxDistanceMeters: Double
    ): FtthNodeEntity? {
        var closest: FtthNodeEntity? = null
        var minDistance = maxDistanceMeters

        for (node in candidates) {
            val d = TrackGeometryHelper.calculateDistanceMeters(lat, lon, node.latitude, node.longitude)
            if (d < minDistance) {
                minDistance = d
                closest = node
            }
        }
        return closest
    }

    data class LineClassification(
        val isCable: Boolean,
        val capacityFO: Int,
        val cableType: String,
        val installationType: String,
        val infrastructureType: String,
        val conduitType: String = "PEHD",
        val conduitCount: Int = 1
    )

    fun classifyLineString(
        name: String,
        description: String,
        styleUrl: String,
        folderContext: String,
        properties: Map<String, String>
    ): LineClassification {
        val combined = "$name $description $styleUrl $folderContext ${properties.values.joinToString(" ")}".lowercase(Locale.ROOT)

        val detectedFO = extractCapacityFO(combined, properties)

        val isCableKeyword = combined.contains("cable") || combined.contains("câble") ||
                combined.contains("cbl") || combined.contains("fibre") || combined.contains("fiber") ||
                combined.contains("optique") || combined.contains("transport") ||
                combined.contains("distrib") || combined.contains("distribution") ||
                combined.contains("raccord") || combined.contains("branchement") ||
                combined.contains("tirage") || detectedFO != null

        val isGcKeyword = combined.contains("gc") || combined.contains("tranchee") ||
                combined.contains("tranchée") || combined.contains("fourreau") ||
                combined.contains("conduite") || combined.contains("pehd") ||
                combined.contains("pvc") || combined.contains("alveole") ||
                combined.contains("alvéole") || combined.contains("masque") ||
                combined.contains("infrastructure") || combined.contains("genie civil") ||
                combined.contains("génie civil")

        val isCable = (detectedFO != null || isCableKeyword) && !(isGcKeyword && detectedFO == null && !isCableKeyword)

        val capacityFO = detectedFO ?: if (isCable) {
            when {
                combined.contains("transport") -> 144
                combined.contains("raccord") -> 4
                combined.contains("72") -> 72
                combined.contains("48") -> 48
                combined.contains("24") -> 24
                combined.contains("12") -> 12
                else -> 24
            }
        } else 0

        val cableType = when {
            combined.contains("transport") || combined.contains("trsp") || combined.contains("nro") || capacityFO >= 144 -> "Transport"
            combined.contains("raccord") || combined.contains("branchement") || combined.contains("client") || combined.contains("drop") || capacityFO <= 12 -> "Raccordement"
            else -> "Distribution"
        }

        val installationType = when {
            combined.contains("aerien") || combined.contains("aérien") || combined.contains("pole") || combined.contains("poteau") || combined.contains("appui") -> "Aérien"
            combined.contains("facade") || combined.contains("façade") || combined.contains("mural") -> "Façade"
            else -> "Souterrain"
        }

        val infraType = when {
            combined.contains("aerien") || combined.contains("aérien") -> "Aérien"
            combined.contains("facade") || combined.contains("façade") -> "Façade"
            else -> "GC"
        }

        val conduitType = if (combined.contains("pvc")) "PVC" else "PEHD"
        val conduitCount = if (combined.contains("bi-tub") || combined.contains("2x") || combined.contains("2 tub")) 2 else 1

        return LineClassification(
            isCable = isCable,
            capacityFO = capacityFO,
            cableType = cableType,
            installationType = installationType,
            infrastructureType = infraType,
            conduitType = conduitType,
            conduitCount = conduitCount
        )
    }

    fun extractCapacityFO(combined: String, properties: Map<String, String>): Int? {
        val propKeys = listOf("capacity", "capacite", "capacite_fo", "capa_fo", "capa", "nb_fo", "nbr_fo", "fo", "f_o", "fibres", "fiber", "fibers", "brins", "taille_fo")
        for ((k, v) in properties) {
            val kLower = k.lowercase(Locale.ROOT)
            if (propKeys.any { kLower.contains(it) }) {
                val num = Regex("""\d+""").find(v)?.value?.toIntOrNull()
                if (num != null && isValidFoCapacity(num)) return num
            }
        }

        val matchFo = Regex("""(\d+)\s*(?:fo|f\.o|fibres?|fibers?|brins?)\b""", RegexOption.IGNORE_CASE).find(combined)
        if (matchFo != null) {
            val num = matchFo.groupValues[1].toIntOrNull()
            if (num != null && num > 0) return num
        }

        val matchPrefix = Regex("""(?:cable|câble|cbl|fo)[_\-\s]+(\d+)\b""", RegexOption.IGNORE_CASE).find(combined)
        if (matchPrefix != null) {
            val num = matchPrefix.groupValues[1].toIntOrNull()
            if (num != null && isValidFoCapacity(num)) return num
        }

        if (combined.contains("cable") || combined.contains("câble") || combined.contains("fibre") || combined.contains("optique")) {
            val matchStandard = Regex("""\b(864|720|576|432|288|144|96|72|48|36|24|16|12|8|6|4|2)\b""").find(combined)
            if (matchStandard != null) {
                val num = matchStandard.groupValues[1].toIntOrNull()
                if (num != null) return num
            }
        }

        return null
    }

    private fun isValidFoCapacity(num: Int): Boolean {
        return num in 1..1500
    }

    fun determineNodeType(
        name: String,
        description: String,
        styleUrl: String,
        properties: Map<String, String>
    ): FtthNodeType {
        val combined = "$name $description $styleUrl ${properties.values.joinToString(" ")}".lowercase(Locale.ROOT)

        return when {
            combined.contains("poteau") || combined.contains("pole") || combined.contains("appui") ||
                    combined.contains("support") || combined.contains("bois") || combined.contains("beton") ||
                    combined.contains("béton") || combined.contains("pt_") || combined.contains("pot_") ||
                    combined.contains("pt-") || combined.contains("pot-") || Regex("""\b(pt|pot|p)\d+\b""").containsMatchIn(combined) -> FtthNodeType.POTEAU

            combined.contains("sro") || combined.contains("nro") || combined.contains("armoire") ||
                    combined.contains("cabinet") || combined.contains("pm360") || combined.contains("pm144") ||
                    combined.contains("sous-repartiteur") || combined.contains("sous_repartiteur") ||
                    Regex("""\bpm\d*\b""").containsMatchIn(combined) -> FtthNodeType.SRO

            combined.contains("immeuble") || combined.contains("batiment") || combined.contains("bâtiment") ||
                    combined.contains("building") || combined.contains("collectif") || combined.contains("residence") ||
                    combined.contains("résidence") || combined.contains("imm_") || combined.contains("imm-") ||
                    combined.contains("bat_") || combined.contains("bat-") || combined.contains("bloc") -> FtthNodeType.IMMEUBLE

            combined.contains("villa") || combined.contains("pavillon") || combined.contains("maison") ||
                    combined.contains("individuel") || combined.contains("hab_") -> FtthNodeType.VILLA

            combined.contains("chambre") || combined.contains("manhole") || combined.contains("regard") ||
                    combined.contains("l0t") || combined.contains("l1t") || combined.contains("l2t") ||
                    combined.contains("l3t") || combined.contains("l4t") || combined.contains("k1c") ||
                    combined.contains("k2c") || combined.contains("ch_") || combined.contains("ch-") ||
                    Regex("""\bch\d+\b""").containsMatchIn(combined) -> FtthNodeType.CHAMBRE

            combined.contains("boitier") || combined.contains("boîtier") || combined.contains("pbo") ||
                    combined.contains("bpe") || combined.contains("pb_") || combined.contains("pb-") ||
                    combined.contains("pri") || combined.contains("splitter") || combined.contains("coupleur") ||
                    combined.contains("bpo") || combined.contains("bpi") || combined.contains("box") -> FtthNodeType.BOITIER

            else -> FtthNodeType.BOITIER
        }
    }

    private fun createNodeFromAttributesWithClassification(
        id: String,
        type: FtthNodeType,
        name: String,
        description: String,
        lat: Double,
        lon: Double,
        properties: Map<String, String>,
        sourceFile: String = ""
    ): FtthNodeEntity {
        val address = properties["address"] ?: properties["adresse"] ?: description
        val operator = properties["operator"] ?: properties["operateur"] ?: properties["owner"] ?: "Tunisie Telecom"
        val notes = properties["notes"] ?: properties["commentaire"] ?: description

        val statusStr = (properties["status"] ?: properties["etat"] ?: "").lowercase(Locale.ROOT)
        val status = when {
            statusStr.contains("exist") -> NodeStatus.EXISTANT
            statusStr.contains("const") -> NodeStatus.EN_CONSTRUCTION
            statusStr.contains("poser") -> NodeStatus.A_POSER
            statusStr.contains("remplac") -> NodeStatus.A_REMPLACER
            statusStr.contains("depos") -> NodeStatus.A_DEPOSER
            statusStr.contains("raccord") -> NodeStatus.RACCORDEE
            else -> NodeStatus.EXISTANT
        }

        val propsJson = try { org.json.JSONObject(properties as Map<*, *>).toString() } catch (_: Exception) { "" }

        return FtthNodeEntity(
            id = id,
            type = type,
            name = name,
            buildingName = name,
            latitude = lat,
            longitude = lon,
            status = status,
            etat = NodeConformity.CONFORME,
            address = address,
            operator = operator,
            notes = notes,
            kmlProperties = propsJson,
            sourceFile = sourceFile
        )
    }

    private fun buildSummaryMessage(
        nodesCount: Int,
        linksCount: Int,
        tracksCount: Int,
        format: String,
        mode: ImportMode
    ): String {
        return buildString {
            val parts = mutableListOf<String>()
            if (nodesCount > 0) parts.add("$nodesCount point${if (nodesCount > 1) "s" else ""}")
            if (linksCount > 0) parts.add("$linksCount câble${if (linksCount > 1) "s" else ""} optique${if (linksCount > 1) "s" else ""}")
            if (tracksCount > 0) parts.add("$tracksCount tracé${if (tracksCount > 1) "s" else ""}/ligne${if (tracksCount > 1) "s" else ""}")

            if (parts.isNotEmpty()) {
                append(parts.joinToString(", "))
                if (mode == ImportMode.NATIVE) {
                    append(" affichés avec succès en mode natif ($format, non classés).")
                } else {
                    append(" importés et classifiés avec succès ($format).")
                }
            } else {
                append("Aucun objet géospatial valide trouvé.")
            }
        }
    }

    /**
     * Résoudre le vrai nom de l'entité en donnant la priorité aux attributs SIG (NOM, CODE, ID)
     * lorsque le nom KML brut est simplement une capacité (ex: "288FO", "144 FO").
     */
    fun resolveSmartName(rawName: String, properties: Map<String, String>): String {
        val cleanRaw = rawName.trim()

        // 1. Chercher les clés de Nom/Code privilégiées dans les attributs SIG
        val sigNomKeys = listOf("NOM", "Nom", "nom", "NOM_CABLE", "CODE", "CODE_CABLE", "ID_CABLE", "REF", "REFERENCE", "IDENTIFIANT", "NAME", "Name")
        var sigNom = ""
        for (k in sigNomKeys) {
            val found = properties[k]
            if (!found.isNullOrBlank()) {
                sigNom = found.trim()
                break
            }
        }

        if (sigNom.isBlank()) return cleanRaw.ifBlank { "Objet KML" }

        // 2. Vérifier si cleanRaw est une pure capacité en FO (ex: "288FO", "288 FO", "144FO", "72 FO", "24FO", "12FO", "4FO", "360FO")
        val isRawJustCapacity = Regex("""^\d+\s*(?:FO|F\.O|fibres?|fibers?|brins?)$""", RegexOption.IGNORE_CASE).matches(cleanRaw) ||
                cleanRaw.matches(Regex("""^\d+$"""))

        // Si cleanRaw est juste une capacité brute ou un nom générique ("Point", "Objet-1"), adopter le Nom SIG
        return if (isRawJustCapacity || cleanRaw.equals("Point", ignoreCase = true) || cleanRaw.startsWith("Objet-")) {
            sigNom
        } else if (cleanRaw.length < sigNom.length && sigNom.contains(cleanRaw, ignoreCase = true)) {
            sigNom
        } else {
            cleanRaw
        }
    }
}
