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
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

object RealImportService {

    data class ImportResult(
        val nodes: List<FtthNodeEntity>,
        val links: List<FtthLinkEntity>,
        val tracks: List<StoredTrack>,
        val message: String
    )

    fun importFromFile(context: Context, uri: Uri): ImportResult {
        val fileName = getFileName(context, uri).lowercase()
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Impossible d'ouvrir le fichier sélectionné.")

        return try {
            if (fileName.endsWith(".kmz")) {
                importKmz(inputStream)
            } else if (fileName.endsWith(".kml")) {
                importKml(inputStream)
            } else if (fileName.endsWith(".geojson") || fileName.endsWith(".json")) {
                importGeoJson(inputStream)
            } else {
                val content = inputStream.bufferedReader().use { it.readText() }
                if (content.trim().startsWith("{")) {
                    parseGeoJsonString(content)
                } else {
                    parseKmlString(content)
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

    private fun importKmz(inputStream: InputStream): ImportResult {
        val zipStream = ZipInputStream(inputStream)
        var entry = zipStream.nextEntry
        var kmlContent: String? = null
        while (entry != null) {
            if (entry.name.lowercase().endsWith(".kml")) {
                kmlContent = BufferedReader(InputStreamReader(zipStream)).use { it.readText() }
                break
            }
            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }
        zipStream.close()

        if (kmlContent != null) {
            return parseKmlString(kmlContent)
        }
        return ImportResult(emptyList(), emptyList(), emptyList(), "Aucun fichier KML trouvé dans l'archive KMZ.")
    }

    private fun importKml(inputStream: InputStream): ImportResult {
        val content = inputStream.bufferedReader().use { it.readText() }
        return parseKmlString(content)
    }

    private fun parseKmlString(kml: String): ImportResult {
        val nodes = mutableListOf<FtthNodeEntity>()
        val tracks = mutableListOf<StoredTrack>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(java.io.ByteArrayInputStream(kml.toByteArray(Charsets.UTF_8)))
            doc.documentElement.normalize()

            val placemarks = doc.getElementsByTagName("Placemark")
            for (i in 0 until placemarks.length) {
                val placemark = placemarks.item(i) as? Element ?: continue
                val name = getElementValue(placemark, "name") ?: "Objet-${i + 1}"
                val description = getElementValue(placemark, "description") ?: ""
                
                // Propriétés ExtendedData
                val properties = mutableMapOf<String, String>()
                val dataElements = placemark.getElementsByTagName("Data")
                for (j in 0 until dataElements.length) {
                    val dataEl = dataElements.item(j) as? Element ?: continue
                    val key = dataEl.getAttribute("name")
                    val value = getElementValue(dataEl, "value") ?: ""
                    if (key.isNotEmpty()) {
                        properties[key] = value
                    }
                }

                val lineStringEl = placemark.getElementsByTagName("LineString")
                val pointEl = placemark.getElementsByTagName("Point")

                if (lineStringEl.length > 0) {
                    val lineEl = lineStringEl.item(0) as? Element
                    val coordsText = lineEl?.let { getElementValue(it, "coordinates") } ?: getElementValue(placemark, "coordinates")
                    if (coordsText != null) {
                        val points = parseKmlCoordinates(coordsText)
                        if (points.size >= 2) {
                            val trackType = determineTrackType(name, description, properties)
                            val totalDist = TrackGeometryHelper.computeTotalDistanceMeters(points)
                            tracks.add(
                                StoredTrack(
                                    id = "IMP-KML-TRK-${System.currentTimeMillis()}-$i",
                                    name = name,
                                    type = trackType,
                                    etat = "Conforme",
                                    notes = description,
                                    startTime = System.currentTimeMillis(),
                                    endTime = System.currentTimeMillis(),
                                    totalDistanceMeters = totalDist,
                                    rawPoints = points,
                                    points = points
                                )
                            )
                        }
                    }
                } else if (pointEl.length > 0 || getElementValue(placemark, "coordinates") != null) {
                    val coordsText = getElementValue(placemark, "coordinates")
                    if (coordsText != null) {
                        val parts = coordsText.trim().split(Regex("[,\\s]+"))
                        if (parts.size >= 2) {
                            val lon = parts[0].toDoubleOrNull() ?: 0.0
                            val lat = parts[1].toDoubleOrNull() ?: 0.0
                            if (lat != 0.0 || lon != 0.0) {
                                val nodeType = determineNodeType(name, description, properties)
                                nodes.add(createNodeFromAttributes(
                                    id = "IMP-KML-NOD-${System.currentTimeMillis()}-$i",
                                    type = nodeType,
                                    name = name,
                                    description = description,
                                    lat = lat,
                                    lon = lon,
                                    properties = properties
                                ))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return ImportResult(emptyList(), emptyList(), emptyList(), "Erreur d'analyse KML : ${e.localizedMessage}")
        }
        val msg = buildString {
            if (nodes.isNotEmpty()) append("${nodes.size} nœuds ")
            if (tracks.isNotEmpty()) {
                if (isNotEmpty()) append("et ")
                append("${tracks.size} linéaires/trajets ")
            }
            append("importés avec succès (KML).")
            if (isEmpty()) append("Aucun objet valide trouvé.")
        }
        return ImportResult(nodes, emptyList(), tracks, msg)
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

    private fun getElementValue(parent: Element, tagName: String): String? {
        val list = parent.getElementsByTagName(tagName)
        if (list.length > 0) {
            val el = list.item(0) as? Element
            return el?.textContent?.trim()
        }
        return null
    }

    private fun importGeoJson(inputStream: InputStream): ImportResult {
        val jsonStr = inputStream.bufferedReader().use { it.readText() }
        return parseGeoJsonString(jsonStr)
    }

    private fun parseGeoJsonString(jsonStr: String): ImportResult {
        val nodes = mutableListOf<FtthNodeEntity>()
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

                if (geomType.equals("Point", ignoreCase = true) && coords.length() >= 2) {
                    val lon = coords.getDouble(0)
                    val lat = coords.getDouble(1)
                    if (lat != 0.0 || lon != 0.0) {
                        val nodeType = determineNodeType(name, description, propMap)
                        nodes.add(createNodeFromAttributes(
                            id = "IMP-GEO-NOD-${System.currentTimeMillis()}-$i",
                            type = nodeType,
                            name = name,
                            description = description,
                            lat = lat,
                            lon = lon,
                            properties = propMap
                        ))
                    }
                } else if (geomType.equals("LineString", ignoreCase = true) && coords.length() > 0) {
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
                        val trackType = determineTrackType(name, description, propMap)
                        val totalDist = TrackGeometryHelper.computeTotalDistanceMeters(points)
                        tracks.add(
                            StoredTrack(
                                id = "IMP-GEO-TRK-${System.currentTimeMillis()}-$i",
                                name = name,
                                type = trackType,
                                etat = "Conforme",
                                notes = description,
                                startTime = System.currentTimeMillis(),
                                endTime = System.currentTimeMillis(),
                                totalDistanceMeters = totalDist,
                                rawPoints = points,
                                points = points
                            )
                        )
                    }
                } else if (geomType.equals("MultiLineString", ignoreCase = true) && coords.length() > 0) {
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
                            val trackType = determineTrackType(name, description, propMap)
                            val totalDist = TrackGeometryHelper.computeTotalDistanceMeters(points)
                            tracks.add(
                                StoredTrack(
                                    id = "IMP-GEO-TRK-${System.currentTimeMillis()}-$i-$m",
                                    name = "$name (${m + 1})",
                                    type = trackType,
                                    etat = "Conforme",
                                    notes = description,
                                    startTime = System.currentTimeMillis(),
                                    endTime = System.currentTimeMillis(),
                                    totalDistanceMeters = totalDist,
                                    rawPoints = points,
                                    points = points
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
        val msg = buildString {
            if (nodes.isNotEmpty()) append("${nodes.size} nœuds ")
            if (tracks.isNotEmpty()) {
                if (isNotEmpty()) append("et ")
                append("${tracks.size} linéaires/trajets ")
            }
            append("importés avec succès (GeoJSON).")
            if (isEmpty()) append("Aucun objet valide trouvé.")
        }
        return ImportResult(nodes, emptyList(), tracks, msg)
    }

    private fun determineTrackType(name: String, description: String, properties: Map<String, String>): String {
        val tKeys = listOf("track_type", "type", "category", "nature", "installation", "mode")
        for (k in tKeys) {
            val v = properties[k]?.lowercase() ?: continue
            if (v.contains("aerien") || v.contains("aérien") || v.contains("aerial")) return "Aérien"
            if (v.contains("facade") || v.contains("façade")) return "Façade"
            if (v.contains("gc") || v.contains("souterrain") || v.contains("underground")) return "GC"
        }
        val combined = "$name $description".lowercase()
        return when {
            combined.contains("aerien") || combined.contains("aérien") -> "Aérien"
            combined.contains("facade") || combined.contains("façade") -> "Façade"
            else -> "GC"
        }
    }

    private fun determineNodeType(name: String, description: String, properties: Map<String, String>): FtthNodeType {
        val typeKeys = listOf("type", "node_type", "type_noeud", "category", "nature", "kind", "layer", "class", "sym", "equipment", "equipement")
        for (key in typeKeys) {
            for ((k, v) in properties) {
                if (k.equals(key, ignoreCase = true)) {
                    val evaluated = mapKeywordToNodeType(v)
                    if (evaluated != null) return evaluated
                }
            }
        }

        val combined = "$name $description".lowercase()
        val keywordMatch = mapKeywordToNodeType(combined)
        if (keywordMatch != null) return keywordMatch

        return FtthNodeType.BOITIER
    }

    private fun mapKeywordToNodeType(text: String): FtthNodeType? {
        val t = text.lowercase()
        return when {
            t.contains("poteau") || t.contains("pole") || t.contains("appui") || t.contains("support") || t.contains("bois") || t.contains("beton") || t.contains("facade") -> FtthNodeType.POTEAU
            t.contains("chambre") || t.contains("manhole") || t.contains("ch") || t.contains("regard") -> FtthNodeType.CHAMBRE
            t.contains("boitier") || t.contains("pbo") || t.contains("bpe") || t.contains("pb") || t.contains("box") || t.contains("splitter") -> FtthNodeType.BOITIER
            t.contains("sro") || t.contains("nro") || t.contains("armoire") || t.contains("cabinet") -> FtthNodeType.SRO
            t.contains("immeuble") || t.contains("batiment") || t.contains("building") || t.contains("collectif") || t.contains("residence") -> FtthNodeType.IMMEUBLE
            t.contains("villa") || t.contains("pavillon") || t.contains("maison") || t.contains("house") -> FtthNodeType.VILLA
            else -> null
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
        var address = properties["address"] ?: properties["adresse"] ?: description
        var operator = properties["operator"] ?: properties["operateur"] ?: properties["owner"] ?: "Tunisie Telecom"
        var notes = properties["notes"] ?: properties["commentaire"] ?: properties["comment"] ?: description

        val statusStr = (properties["status"] ?: properties["etat_deploiement"] ?: "").lowercase()
        val status = when {
            statusStr.contains("exist") -> NodeStatus.EXISTANT
            statusStr.contains("const") -> NodeStatus.EN_CONSTRUCTION
            statusStr.contains("poser") || statusStr.contains("new") -> NodeStatus.A_POSER
            statusStr.contains("remplac") -> NodeStatus.A_REMPLACER
            statusStr.contains("depos") -> NodeStatus.A_DEPOSER
            statusStr.contains("raccord") -> NodeStatus.RACCORDEE
            else -> NodeStatus.EXISTANT
        }

        val poleNature = properties["pole_nature"] ?: properties["nature"] ?: "bois"
        val poleHeight = properties["pole_height"]?.toIntOrNull() ?: 8
        val chamberType = properties["chamber_type"] ?: properties["type_chambre"] ?: "L2T"
        val boitierType = properties["boitier_type"] ?: properties["type_boitier"] ?: "PBO"
        val sroCapacity = properties["sro_capacity"] ?: properties["capacite"] ?: "360 FO"
        val buildingFloors = properties["floors"]?.toIntOrNull() ?: properties["etages"]?.toIntOrNull() ?: 4
        val buildingDwellings = properties["dwellings"]?.toIntOrNull() ?: properties["logements"]?.toIntOrNull() ?: 16

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
            poleNature = poleNature,
            poleHeight = poleHeight,
            chamberType = chamberType,
            boitierType = boitierType,
            sroCapacity = sroCapacity,
            buildingFloors = buildingFloors,
            buildingDwellings = buildingDwellings
        )
    }
}
