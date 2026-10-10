package com.example.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.local.GpsTrackEntity
import com.example.data.local.GpsTrackPointEntity
import com.example.ui.components.FtthNodeVisuals
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object RealExportService {

    private fun getExportDir(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getTimestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date())

    fun exportGeoJson(
        context: Context,
        nodes: List<FtthNodeEntity>,
        links: List<FtthLinkEntity>,
        tracksWithPoints: List<Pair<GpsTrackEntity, List<GpsTrackPointEntity>>> = emptyList()
    ): File {
        val root = JSONObject()
        root.put("type", "FeatureCollection")
        val features = JSONArray()

        val nodesMap = nodes.associateBy { it.id }

        // 1. Points for Nodes
        for (node in nodes) {
            val feature = JSONObject()
            feature.put("type", "Feature")

            val geometry = JSONObject()
            geometry.put("type", "Point")
            val coords = JSONArray()
            coords.put(node.longitude)
            coords.put(node.latitude)
            geometry.put("coordinates", coords)
            feature.put("geometry", geometry)

            val properties = JSONObject()
            properties.put("id", node.id)
            properties.put("name", node.name)
            properties.put("buildingName", node.buildingName)
            properties.put("type", node.type.name)
            properties.put("typeLabel", node.type.label)
            properties.put("category", node.type.category)
            properties.put("status", FtthNodeVisuals.getStatusDisplayLabel(node.status, node.type))
            properties.put("etat", node.etat.label)
            properties.put("address", node.address)
            properties.put("operator", node.operator)
            properties.put("hasBoitierFtth", node.hasBoitierFtth)
            properties.put("notes", node.notes)
            properties.put("technician", node.technicianName)
            properties.put("photoCount", node.photos.size)
            properties.put("photos", JSONArray(node.photos))
            properties.put("updatedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE).format(Date(node.updatedAt)))

            // Specific attributes
            properties.put("poleNature", node.poleNature)
            properties.put("poleHeight", node.poleHeight)
            properties.put("chamberType", node.chamberType)
            properties.put("boitierType", node.boitierType)
            properties.put("isSaturated", node.isSaturated)
            properties.put("boitierSupport", node.boitierSupport)
            properties.put("sroType", node.sroType)
            properties.put("sroCapacity", node.sroCapacity)
            properties.put("buildingFloors", node.buildingFloors)
            properties.put("buildingDwellings", node.buildingDwellings)
            properties.put("hasLocalTechnique", node.hasLocalTechnique)
            properties.put("hasGaineMontante", node.hasGaineMontante)
            properties.put("syndicAuthorization", node.syndicAuthorization)
            properties.put("syndicContact", node.syndicContact)
            properties.put("buildingConnectionMode", node.buildingConnectionMode)
            properties.put("villaConnectionMode", node.villaConnectionMode)

            feature.put("properties", properties)
            features.put(feature)
        }

        // 2. LineStrings for Links (Câbles)
        for (link in links) {
            val fromNode = nodesMap[link.fromNodeId]
            val toNode = nodesMap[link.toNodeId]
            if (fromNode != null && toNode != null) {
                val feature = JSONObject()
                feature.put("type", "Feature")

                val geometry = JSONObject()
                geometry.put("type", "LineString")
                val coords = JSONArray()

                val p1 = JSONArray()
                p1.put(fromNode.longitude)
                p1.put(fromNode.latitude)
                coords.put(p1)

                val p2 = JSONArray()
                p2.put(toNode.longitude)
                p2.put(toNode.latitude)
                coords.put(p2)

                geometry.put("coordinates", coords)
                feature.put("geometry", geometry)

                val props = JSONObject()
                props.put("id", link.id)
                props.put("fromNodeId", link.fromNodeId)
                props.put("toNodeId", link.toNodeId)
                props.put("cableType", link.cableType)
                props.put("installationType", link.installationType)
                props.put("capacityFO", link.capacityFO)
                props.put("lengthMeters", link.lengthMeters)
                props.put("status", link.status.label)
                feature.put("properties", props)

                features.put(feature)
            }
        }

        // 3. LineStrings for GPS Tracks
        for ((track, points) in tracksWithPoints) {
            if (points.size >= 2) {
                val feature = JSONObject()
                feature.put("type", "Feature")

                val geometry = JSONObject()
                geometry.put("type", "LineString")
                val coords = JSONArray()
                for (pt in points) {
                    val p = JSONArray()
                    p.put(pt.longitude)
                    p.put(pt.latitude)
                    coords.put(p)
                }
                geometry.put("coordinates", coords)
                feature.put("geometry", geometry)

                val props = JSONObject()
                props.put("trackId", track.id)
                props.put("trackName", track.name)
                props.put("type", track.type)
                props.put("etatTechnique", track.etat)
                props.put("conduitAudit", track.conduitAudit)
                props.put("conduitType", track.conduitType)
                props.put("conduitCount", track.conduitCount)
                props.put("conduitDiameters", track.conduitDiameters)
                props.put("distanceMeters", track.totalDistanceMeters)
                props.put("pointCount", points.size)
                props.put("featureKind", "GPS_TRACK")
                feature.put("properties", props)

                features.put(feature)
            }
        }

        root.put("features", features)

        val file = File(getExportDir(context), "piquetage_ftth_${getTimestamp()}.geojson")
        file.writeText(root.toString(2))
        return file
    }

    fun exportCsv(context: Context, nodes: List<FtthNodeEntity>): File {
        val file = File(getExportDir(context), "bordereau_piquetage_${getTimestamp()}.csv")
        val sb = StringBuilder()

        // CSV Header (RFC 4180 with semicolon separator for French Excel)
        sb.append("ID;NOM;NOM_BATIMENT;TYPE;STATUT;CONFORMITE;LATITUDE;LONGITUDE;ADRESSE;OPERATEUR;BOITIER_FTTH;NATURE_APPUI;HAUTEUR;TYPE_CHAMBRE;TYPE_BOITIER;SATURE;SUPPORT_BOITIER;TYPE_SRO;CAPACITE_SRO;ETAGES;LOGEMENTS;LOCAL_TECH;GAINE_MONTANTE;AUTORISATION_SYNDIC;CONTACT_SYNDIC;RACCORDEMENT_IMMEUBLE;RACCORDEMENT_VILLA;COMMENTAIRE;TECHNICIEN;NB_PHOTOS;DATE\n")

        for (node in nodes) {
            val cols = listOf(
                escapeCsv(node.id),
                escapeCsv(node.name),
                escapeCsv(node.buildingName),
                escapeCsv(node.type.label),
                escapeCsv(FtthNodeVisuals.getStatusDisplayLabel(node.status, node.type)),
                escapeCsv(node.etat.label),
                node.latitude.toString(),
                node.longitude.toString(),
                escapeCsv(node.address),
                escapeCsv(node.operator),
                if (node.hasBoitierFtth) "Oui" else "Non",
                escapeCsv(node.poleNature),
                node.poleHeight.toString(),
                escapeCsv(node.chamberType),
                escapeCsv(node.boitierType),
                if (node.isSaturated) "Oui" else "Non",
                escapeCsv(node.boitierSupport),
                escapeCsv(node.sroType),
                escapeCsv(node.sroCapacity),
                node.buildingFloors.toString(),
                node.buildingDwellings.toString(),
                if (node.hasLocalTechnique) "Oui" else "Non",
                if (node.hasGaineMontante) "Oui" else "Non",
                escapeCsv(node.syndicAuthorization),
                escapeCsv(node.syndicContact),
                escapeCsv(node.buildingConnectionMode),
                escapeCsv(node.villaConnectionMode),
                escapeCsv(node.notes),
                escapeCsv(node.technicianName),
                node.photos.size.toString(),
                SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.FRANCE).format(Date(node.updatedAt))
            )
            sb.append(cols.joinToString(";")).append("\n")
        }

        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    private fun escapeCsv(value: String): String {
        val sanitized = value.replace("\"", "\"\"")
        return if (sanitized.contains(";") || sanitized.contains("\n") || sanitized.contains("\"")) {
            "\"$sanitized\""
        } else {
            sanitized
        }
    }

    fun exportKml(context: Context, nodes: List<FtthNodeEntity>, links: List<FtthLinkEntity>): File {
        val file = File(getExportDir(context), "reseau_ftth_${getTimestamp()}.kml")
        val nodesMap = nodes.associateBy { it.id }

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("  <Document>\n")
        sb.append("    <name>Relevé Terrain FTTH</name>\n")
        sb.append("    <Folder>\n")
        sb.append("      <name>Nœuds FTTH</name>\n")

        for (node in nodes) {
            val displayName = if (node.buildingName.isNotBlank()) "${node.id} - ${node.buildingName}" else "${node.id} (${node.type.label})"
            sb.append("      <Placemark>\n")
            sb.append("        <name>${escapeXml(displayName)}</name>\n")
            sb.append("        <description><![CDATA[\n")
            sb.append("          <b>Type:</b> ${node.type.label}<br/>\n")
            if (node.buildingName.isNotBlank()) {
                sb.append("          <b>Bâtiment:</b> ${escapeXml(node.buildingName)}<br/>\n")
            }
            sb.append("          <b>Statut:</b> ${escapeXml(FtthNodeVisuals.getStatusDisplayLabel(node.status, node.type))}<br/>\n")
            sb.append("          <b>État:</b> ${node.etat.label}<br/>\n")
            sb.append("          <b>Adresse:</b> ${node.address}<br/>\n")
            sb.append("          <b>Boîtier FTTH:</b> ${if (node.hasBoitierFtth) "Oui" else "Non"}<br/>\n")
            sb.append("          <b>Commentaire:</b> ${node.notes}<br/>\n")
            sb.append("          <b>Photos:</b> ${node.photos.size}<br/>\n")
            sb.append("        ]]></description>\n")
            sb.append("        <Point>\n")
            sb.append("          <coordinates>${node.longitude},${node.latitude},0</coordinates>\n")
            sb.append("        </Point>\n")
            sb.append("      </Placemark>\n")
        }
        sb.append("    </Folder>\n")

        sb.append("    <Folder>\n")
        sb.append("      <name>Liaisons &amp; Câbles</name>\n")
        for (link in links) {
            val from = nodesMap[link.fromNodeId]
            val to = nodesMap[link.toNodeId]
            if (from != null && to != null) {
                sb.append("      <Placemark>\n")
                sb.append("        <name>${escapeXml(link.id)} (${link.capacityFO} FO)</name>\n")
                sb.append("        <description>${link.cableType} - ${link.installationType}</description>\n")
                sb.append("        <LineString>\n")
                sb.append("          <coordinates>${from.longitude},${from.latitude},0 ${to.longitude},${to.latitude},0</coordinates>\n")
                sb.append("        </LineString>\n")
                sb.append("      </Placemark>\n")
            }
        }
        sb.append("    </Folder>\n")
        sb.append("  </Document>\n")
        sb.append("</kml>\n")

        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    private fun escapeXml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun exportKmz(
        context: Context,
        nodes: List<FtthNodeEntity>,
        links: List<FtthLinkEntity>,
        tracksWithPoints: List<Pair<GpsTrackEntity, List<GpsTrackPointEntity>>> = emptyList()
    ): File {
        val kmzFile = File(getExportDir(context), "releve_terrain_${getTimestamp()}.kmz")
        val kmlFile = exportKml(context, nodes, links)

        ZipOutputStream(FileOutputStream(kmzFile)).use { zos ->
            addFileToZip(zos, kmlFile, "doc.kml")
        }
        return kmzFile
    }

    fun exportCompleteZip(
        context: Context,
        nodes: List<FtthNodeEntity>,
        links: List<FtthLinkEntity>,
        tracksWithPoints: List<Pair<GpsTrackEntity, List<GpsTrackPointEntity>>> = emptyList()
    ): File {
        val zipFile = File(getExportDir(context), "dossier_releve_terrain_${getTimestamp()}.zip")
        val geoJsonFile = exportGeoJson(context, nodes, links, tracksWithPoints)
        val kmzFile = exportKmz(context, nodes, links, tracksWithPoints)
        val csvFile = exportCsv(context, nodes)
        val kmlFile = exportKml(context, nodes, links)

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // Add GeoJSON
            addFileToZip(zos, geoJsonFile, "releve_terrain.geojson")
            // Add KMZ Google Earth
            addFileToZip(zos, kmzFile, "releve_terrain.kmz")
            // Add KML
            addFileToZip(zos, kmlFile, "reseau_ftth.kml")
            // Add CSV
            addFileToZip(zos, csvFile, "bordereau_releve.csv")

            // Add all photos
            for (node in nodes) {
                for (photoPath in node.photos) {
                    val pFile = com.example.data.photo.PhotoStorageManager.resolvePhotoFile(context, photoPath)
                    if (pFile.exists() && pFile.length() > 0) {
                        val sanitizedNodeId = node.id.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                        addFileToZip(zos, pFile, "photos/${sanitizedNodeId}/${pFile.name}")
                    }
                }
            }
        }

        return zipFile
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists()) return
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        FileInputStream(file).use { fis ->
            fis.copyTo(zos)
        }
        zos.closeEntry()
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String = "Exporter les données FTTH") {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
