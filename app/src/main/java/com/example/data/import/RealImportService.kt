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
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

object RealImportService {

    data class ImportResult(
        val nodes: List<FtthNodeEntity>,
        val links: List<FtthLinkEntity>,
        val tracks: List<StoredTrack>,
        val message: String
    )

    fun importFromFile(
        context: Context,
        uri: Uri,
        existingNodes: List<FtthNodeEntity> = emptyList()
    ): ImportResult {
        val fileName = getFileName(context, uri).lowercase(Locale.ROOT)
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Impossible d'ouvrir le fichier sélectionné.")

        return try {
            when {
                fileName.endsWith(".kmz") -> importKmz(inputStream, existingNodes)
                fileName.endsWith(".kml") -> importKml(inputStream, existingNodes)
                fileName.endsWith(".geojson") || fileName.endsWith(".json") -> importGeoJson(inputStream, existingNodes)
                else -> {
                    val content = inputStream.bufferedReader().use { it.readText() }
                    if (content.trim().startsWith("{")) {
                        parseGeoJsonString(content, existingNodes)
                    } else {
                        parseKmlString(content, existingNodes)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ImportResult(emptyList(), emptyList(), emptyList(), "Erreur d'import : ${e.localizedMessage}")
        } finally {
            try { inputStream.close() } catch (_: Exception) {}
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result = "import.geojson"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex("_display_name")
                if (idx != -1) {
                    result = it.getString(idx) ?: result
                }
            }
        }
        return result
    }

    private fun importKmz(inputStream: InputStream, existingNodes: List<FtthNodeEntity>): ImportResult {
        val zipStream = ZipInputStream(inputStream)
        var entry = zipStream.nextEntry
        val kmlContents = mutableListOf<String>()

        while (entry != null) {
            val nameLower = entry.name.lowercase(Locale.ROOT)
            if (nameLower.endsWith(".kml")) {
                val content = BufferedReader(InputStreamReader(zipStream, StandardCharsets.UTF_8)).use { it.readText() }
                if (content.isNotBlank()) {
                    kmlContents.add(content)
                }
            }
            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }
        zipStream.close()

        if (kmlContents.isNotEmpty()) {
            val allNodes = mutableListOf<FtthNodeEntity>()
            val allLinks = mutableListOf<FtthLinkEntity>()
            val allTracks = mutableListOf<StoredTrack>()

            for (kml in kmlContents) {
                val combinedExisting = existingNodes + allNodes
                val res = parseKmlString(kml, combinedExisting)
                allNodes.addAll(res.nodes)
                allLinks.addAll(res.links)
                allTracks.addAll(res.tracks)
            }
            val msg = buildSummaryMessage(allNodes.size, allLinks.size, allTracks.size, "KMZ")
            return ImportResult(allNodes, allLinks, allTracks, msg)
        }
        return ImportResult(emptyList(), emptyList(), emptyList(), "Aucun fichier KML valide trouvé dans l'archive KMZ.")
    }

    private fun importKml(inputStream: InputStream, existingNodes: List<FtthNodeEntity>): ImportResult {
        val content = inputStream.bufferedReader().use { it.readText() }
        return parseKmlString(content, existingNodes)
    }

    private fun parseKmlString(kml: String, existingNodes: List<FtthNodeEntity>): ImportResult {
        val nodes = mutableListOf<FtthNodeEntity>()
        val links = mutableListOf<FtthLinkEntity>()
        val tracks = mutableListOf<StoredTrack>()

        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(kml.toByteArray(StandardCharsets.UTF_8)))
            doc.documentElement.normalize()

            parseKmlElement(doc.documentElement, "", existingNodes, nodes, links, tracks)

        } catch (e: Exception) {
            e.printStackTrace()
            return ImportResult(emptyList(), emptyList(), emptyList(), "Erreur d'analyse KML : ${e.localizedMessage}")
        }

        val msg = buildSummaryMessage(nodes.size, links.size, tracks.size, "KML")
        return ImportResult(nodes, links, tracks, msg)
    }

    private fun parseKmlElement(
        element: Element,
        parentFolderName: String,
        existingNodes: List<FtthNodeEntity>,
        nodes: MutableList<FtthNodeEntity>,
        links: MutableList<FtthLinkEntity>,
        tracks: MutableList<StoredTrack>
    ) {
        val placemarks = element.getElementsByTagNameNS("*", "Placemark")
        for (i in 0 until placemarks.length) {
            val pm = placemarks.item(i) as? Element ?: continue
            val name = getChildTagValue(pm, "name") ?: "Objet"
            val desc = getChildTagValue(pm, "description") ?: ""
            val styleUrl = getChildTagValue(pm, "styleUrl") ?: ""
            val folderContext = if (parentFolderName.isNotEmpty()) "$parentFolderName / $name" else name

            // Extraire ExtendedData / Data
            val properties = mutableMapOf<String, String>()
            val dataNodes = pm.getElementsByTagNameNS("*", "Data")
            for (j in 0 until dataNodes.length) {
                val dataEl = dataNodes.item(j) as? Element ?: continue
                val key = dataEl.getAttribute("name")
                val value = getChildTagValue(dataEl, "value") ?: ""
                if (key.isNotEmpty()) properties[key] = value
            }

            // Vérifier géométries : LineString, Point, Polygon
            val lineStringEls = pm.getElementsByTagNameNS("*", "LineString")
            val pointEls = pm.getElementsByTagNameNS("*", "Point")
            val polygonEls = pm.getElementsByTagNameNS("*", "Polygon")

            if (lineStringEls.length > 0) {
                val lineEl = lineStringEls.item(0) as? Element
                val coordsText = lineEl?.let { getChildTagValue(it, "coordinates") } ?: getChildTagValue(pm, "coordinates")
                if (coordsText != null) {
                    val pts = parseKmlCoordinates(coordsText)
                    if (pts.size >= 2) {
                        processLineString(
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
                            index = tracks.size + links.size
                        )
                    }
                }
            } else if (polygonEls.length > 0) {
                val polyEl = polygonEls.item(0) as? Element
                val coordsText = polyEl?.let { getChildTagValue(it, "coordinates") } ?: getChildTagValue(pm, "coordinates")
                if (coordsText != null) {
                    val pts = parseKmlCoordinates(coordsText)
                    if (pts.isNotEmpty()) {
                        val lat = pts.map { it.latitude }.average()
                        val lon = pts.map { it.longitude }.average()
                        val nodeType = determineNodeType(folderContext, desc, styleUrl, properties)
                        nodes.add(
                            createNodeFromAttributes(
                                id = "KML-NOD-${System.currentTimeMillis()}-${nodes.size}",
                                type = nodeType,
                                name = name,
                                description = desc,
                                lat = lat,
                                lon = lon,
                                properties = properties
                            )
                        )
                    }
                }
            } else if (pointEls.length > 0 || getChildTagValue(pm, "coordinates") != null) {
                val coordsText = getChildTagValue(pm, "coordinates")
                if (coordsText != null) {
                    val parts = coordsText.trim().split(Regex("[,\\s]+"))
                    if (parts.size >= 2) {
                        val lon = parts[0].toDoubleOrNull() ?: 0.0
                        val lat = parts[1].toDoubleOrNull() ?: 0.0
                        if (lat != 0.0 || lon != 0.0) {
                            val nodeType = determineNodeType(folderContext, desc, styleUrl, properties)
                            nodes.add(
                                createNodeFromAttributes(
                                    id = "KML-NOD-${System.currentTimeMillis()}-${nodes.size}",
                                    type = nodeType,
                                    name = name,
                                    description = desc,
                                    lat = lat,
                                    lon = lon,
                                    properties = properties
                                )
                            )
                        }
                    }
                }
            }
        }

        // Parcourir récursivement les sous-dossiers
        val subFolders = element.getElementsByTagNameNS("*", "Folder")
        for (k in 0 until subFolders.length) {
            val subFolder = subFolders.item(k) as? Element ?: continue
            val folderName = getChildTagValue(subFolder, "name") ?: ""
            val nextParent = if (parentFolderName.isNotEmpty() && folderName.isNotEmpty()) "$parentFolderName / $folderName" else (folderName.ifEmpty { parentFolderName })
            parseKmlElement(subFolder, nextParent, existingNodes, nodes, links, tracks)
        }
    }

    private fun getChildTagValue(parent: Element, tagName: String): String? {
        val list = parent.getElementsByTagNameNS("*", tagName)
        if (list.length > 0) {
            val el = list.item(0) as? Element
            return el?.textContent?.trim()
        }
        return null
    }

    private fun parseKmlCoordinates(coordsText: String): List<TrackPoint> {
        val points = mutableListOf<TrackPoint>()
        val tokens = coordsText.trim().split(Regex("\\s+"))
        for (token in tokens) {
            val parts = token.split(",")
            if (parts.size >= 2) {
                val lon = parts[0].toDoubleOrNull() ?: continue
                val lat = parts[1].toDoubleOrNull() ?: continue
                val alt = if (parts.size >= 3) parts[2].toDoubleOrNull() ?: 0.0 else 0.0
                points.add(TrackPoint(latitude = lat, longitude = lon, altitude = alt))
            }
        }
        return points
    }

    private fun importGeoJson(inputStream: InputStream, existingNodes: List<FtthNodeEntity>): ImportResult {
        val jsonStr = inputStream.bufferedReader().use { it.readText() }
        return parseGeoJsonString(jsonStr, existingNodes)
    }

    private fun parseGeoJsonString(jsonStr: String, existingNodes: List<FtthNodeEntity>): ImportResult {
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

                val name = propMap["name"] ?: propMap["nom"] ?: propMap["ID"] ?: "Objet-${i + 1}"
                val description = propMap["description"] ?: propMap["desc"] ?: ""

                when {
                    geomType.equals("Point", ignoreCase = true) && coords.length() >= 2 -> {
                        val lon = coords.getDouble(0)
                        val lat = coords.getDouble(1)
                        if (lat != 0.0 || lon != 0.0) {
                            val nodeType = determineNodeType(name, description, "", propMap)
                            nodes.add(
                                createNodeFromAttributes(
                                    id = "GEO-NOD-${System.currentTimeMillis()}-$i",
                                    type = nodeType,
                                    name = name,
                                    description = description,
                                    lat = lat,
                                    lon = lon,
                                    properties = propMap
                                )
                            )
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
                            processLineString(
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
                                index = i
                            )
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
                                processLineString(
                                    name = "$name (${m + 1})",
                                    description = description,
                                    styleUrl = "",
                                    folderContext = name,
                                    properties = propMap,
                                    pts = points,
                                    existingNodes = existingNodes,
                                    nodes = nodes,
                                    links = links,
                                    tracks = tracks,
                                    index = i * 100 + m
                                )
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
                            val lat = points.map { it.latitude }.average()
                            val lon = points.map { it.longitude }.average()
                            val nodeType = determineNodeType(name, description, "", propMap)
                            nodes.add(
                                createNodeFromAttributes(
                                    id = "GEO-NOD-${System.currentTimeMillis()}-$i",
                                    type = nodeType,
                                    name = name,
                                    description = description,
                                    lat = lat,
                                    lon = lon,
                                    properties = propMap
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return ImportResult(emptyList(), emptyList(), emptyList(), "Erreur d'analyse GeoJSON : ${e.localizedMessage}")
        }

        val msg = buildSummaryMessage(nodes.size, links.size, tracks.size, "GeoJSON")
        return ImportResult(nodes, links, tracks, msg)
    }

    /**
     * Traitement intelligent des polylignes / LineString :
     * Détermine s'il s'agit d'un CÂBLE OPTIQUE avec sa capacité en FO ou d'une INFRASTRUCTURE GC.
     */
    private fun processLineString(
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
        index: Int
    ) {
        val dist = TrackGeometryHelper.computeTotalDistanceMeters(pts)
        val lineInfo = classifyLineString(name, description, styleUrl, folderContext, properties)

        if (lineInfo.isCable) {
            // C'est un CÂBLE OPTIQUE avec capacité en FO
            val trackId = "TRK-CBL-${System.currentTimeMillis()}-$index"
            val cableId = "CBL-${System.currentTimeMillis() % 100000}-$index"

            // Trouver ou générer les extrémités (fromNodeId et toNodeId)
            val allNodesPool = existingNodes + nodes
            val startPt = pts.first()
            val endPt = pts.last()

            var fromNode = findClosestNode(startPt.latitude, startPt.longitude, allNodesPool, maxDistanceMeters = 75.0)
            var toNode = findClosestNode(endPt.latitude, endPt.longitude, allNodesPool, maxDistanceMeters = 75.0)

            // Si aucune extrémité trouvée, générer des nœuds de raccordement automatiques
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
                    notes = "Généré automatiquement lors de l'import du câble ${lineInfo.capacityFO} FO"
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
                    notes = "Généré automatiquement lors de l'import du câble ${lineInfo.capacityFO} FO"
                )
                nodes.add(toNode)
            }

            // Créer la liaison Câble (FtthLinkEntity)
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

            // Créer également le tracé physique (StoredTrack) pour que la ligne exacte s'affiche sur la carte
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
                    points = pts
                )
            )

        } else {
            // C'est une INFRASTRUCTURE GÉNIE CIVIL (GC / Conduite / Tranchée)
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
                    points = pts
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

    private data class LineClassification(
        val isCable: Boolean,
        val capacityFO: Int,
        val cableType: String,
        val installationType: String,
        val infrastructureType: String,
        val conduitType: String = "PEHD",
        val conduitCount: Int = 1
    )

    /**
     * Analyse intelligente pour classifier un LineString : Câble (avec capacité FO) ou Génie Civil (GC).
     */
    private fun classifyLineString(
        name: String,
        description: String,
        styleUrl: String,
        folderContext: String,
        properties: Map<String, String>
    ): LineClassification {
        val combined = "$name $description $styleUrl $folderContext ${properties.values.joinToString(" ")}".lowercase(Locale.ROOT)

        // 1. Extraire la capacité en FO
        val detectedFO = extractCapacityFO(combined, properties)

        // 2. Détecter si c'est explicitement un câble ou une infra
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

        // Priorité : si une capacité en FO est détectée ou mot-clé câble prédominant
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

        // Type de câble : Transport, Distribution, Raccordement
        val cableType = when {
            combined.contains("transport") || combined.contains("trsp") || combined.contains("nro") || capacityFO >= 144 -> "Transport"
            combined.contains("raccord") || combined.contains("branchement") || combined.contains("client") || combined.contains("drop") || capacityFO <= 12 -> "Raccordement"
            else -> "Distribution"
        }

        // Type de pose / installation : Souterrain, Aérien, Façade
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

    /**
     * Extraction avancée de la capacité en FO (xx FO, xx fibres, attributs SIG...)
     */
    private fun extractCapacityFO(combined: String, properties: Map<String, String>): Int? {
        // 1. Chercher dans les attributs de propriétés (capacité, capa_fo, nb_fo, fo, etc.)
        val propKeys = listOf("capacity", "capacite", "capacite_fo", "capa_fo", "capa", "nb_fo", "nbr_fo", "fo", "f_o", "fibres", "fiber", "fibers", "brins", "taille_fo")
        for ((k, v) in properties) {
            val kLower = k.lowercase(Locale.ROOT)
            if (propKeys.any { kLower.contains(it) }) {
                val num = Regex("""\d+""").find(v)?.value?.toIntOrNull()
                if (num != null && isValidFoCapacity(num)) return num
            }
        }

        // 2. Chercher avec Regex : ex: 144FO, 144 FO, 72 f.o, 48 fibres, 24fo, 12 fo
        val matchFo = Regex("""(\d+)\s*(?:fo|f\.o|fibres?|fibers?|brins?)\b""", RegexOption.IGNORE_CASE).find(combined)
        if (matchFo != null) {
            val num = matchFo.groupValues[1].toIntOrNull()
            if (num != null && num > 0) return num
        }

        // 3. Chercher préfixe cable/cbl/fo suivi de nombre : CABLE_144, CBL-72, FO_48
        val matchPrefix = Regex("""(?:cable|câble|cbl|fo)[_\-\s]+(\d+)\b""", RegexOption.IGNORE_CASE).find(combined)
        if (matchPrefix != null) {
            val num = matchPrefix.groupValues[1].toIntOrNull()
            if (num != null && isValidFoCapacity(num)) return num
        }

        // 4. Détecter un nombre standard de capacité FO (720, 432, 288, 144, 96, 72, 48, 36, 24, 16, 12, 8, 6, 4, 2)
        // seulement si le contexte mentionne un câble ou fibre
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

    /**
     * Détermination intelligente du type de nœud FTTH (Poteau, Chambre, Boîtier, SRO, Immeuble, Villa).
     */
    private fun determineNodeType(
        name: String,
        description: String,
        styleUrl: String,
        properties: Map<String, String>
    ): FtthNodeType {
        val combined = "$name $description $styleUrl ${properties.values.joinToString(" ")}".lowercase(Locale.ROOT)

        return when {
            // Poteau / Appui aérien
            combined.contains("poteau") || combined.contains("pole") || combined.contains("appui") ||
                    combined.contains("support") || combined.contains("bois") || combined.contains("beton") ||
                    combined.contains("béton") || combined.contains("pt_") || combined.contains("pot_") ||
                    combined.contains("pt-") || combined.contains("pot-") || Regex("""\b(pt|pot|p)\d+\b""").containsMatchIn(combined) -> FtthNodeType.POTEAU

            // SRO / NRO (Armoire de rue / PM)
            combined.contains("sro") || combined.contains("nro") || combined.contains("armoire") ||
                    combined.contains("cabinet") || combined.contains("pm360") || combined.contains("pm144") ||
                    combined.contains("sous-repartiteur") || combined.contains("sous_repartiteur") ||
                    Regex("""\bpm\d*\b""").containsMatchIn(combined) -> FtthNodeType.SRO

            // Immeuble / Bâtiment collectif
            combined.contains("immeuble") || combined.contains("batiment") || combined.contains("bâtiment") ||
                    combined.contains("building") || combined.contains("collectif") || combined.contains("residence") ||
                    combined.contains("résidence") || combined.contains("imm_") || combined.contains("imm-") ||
                    combined.contains("bat_") || combined.contains("bat-") || combined.contains("bloc") -> FtthNodeType.IMMEUBLE

            // Villa / Pavillon
            combined.contains("villa") || combined.contains("pavillon") || combined.contains("maison") ||
                    combined.contains("individuel") || combined.contains("hab_") -> FtthNodeType.VILLA

            // Chambre souterraine / Regard
            combined.contains("chambre") || combined.contains("manhole") || combined.contains("regard") ||
                    combined.contains("l0t") || combined.contains("l1t") || combined.contains("l2t") ||
                    combined.contains("l3t") || combined.contains("l4t") || combined.contains("k1c") ||
                    combined.contains("k2c") || combined.contains("ch_") || combined.contains("ch-") ||
                    Regex("""\bch\d+\b""").containsMatchIn(combined) -> FtthNodeType.CHAMBRE

            // Boîtier / PBO / BPE
            combined.contains("boitier") || combined.contains("boîtier") || combined.contains("pbo") ||
                    combined.contains("bpe") || combined.contains("pb_") || combined.contains("pb-") ||
                    combined.contains("pri") || combined.contains("splitter") || combined.contains("coupleur") ||
                    combined.contains("bpo") || combined.contains("bpi") || combined.contains("box") -> FtthNodeType.BOITIER

            else -> FtthNodeType.BOITIER
        }
    }

    private fun createNodeFromAttributes(
        id: String,
        type: FtthNodeType,
        name: String,
        description: String,
        lat: Double,
        lon: Double,
        properties: Map<String, String>
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
            notes = notes
        )
    }

    private fun buildSummaryMessage(nodesCount: Int, linksCount: Int, tracksCount: Int, format: String): String {
        return buildString {
            val parts = mutableListOf<String>()
            if (nodesCount > 0) parts.add("$nodesCount nœuds")
            if (linksCount > 0) parts.add("$linksCount câbles optiques (avec capacité FO)")
            if (tracksCount > 0) parts.add("$tracksCount tracés/infrastructures")

            if (parts.isNotEmpty()) {
                append(parts.joinToString(", "))
                append(" importés avec succès ($format).")
            } else {
                append("Aucun objet géospatial valide trouvé.")
            }
        }
    }
}
