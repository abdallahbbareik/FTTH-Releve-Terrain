package com.example.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.local.FtthLinkEntity
import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.local.GpsTrackEntity
import com.example.data.local.GpsTrackPointEntity
import com.example.data.storage.StoredTrack
import com.example.data.storage.TrackPoint
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

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE).format(Date(timestamp))

    /**
     * Convertit une entité Room GpsTrack + points en StoredTrack pour compatibilité ascendante.
     */
    private fun toStoredTrack(track: GpsTrackEntity, points: List<GpsTrackPointEntity>): StoredTrack =
        StoredTrack(
            id = track.id,
            name = track.name,
            type = track.type,
            etat = track.etat,
            conduitAudit = track.conduitAudit,
            conduitType = track.conduitType,
            conduitCount = track.conduitCount,
            conduitDiameters = listOf(track.conduitDiameters),
            startTime = track.startTime,
            endTime = track.endTime,
            totalDistanceMeters = track.totalDistanceMeters,
            points = points.map { TrackPoint(it.latitude, it.longitude, it.altitude, it.accuracy, it.speed, it.timestamp) }
        )

    /**
     * Crée un objet JSON Feature GeoJSON pour un nœud avec l'intégralité des champs du piquetage.
     */
    fun createNodeFeature(node: FtthNodeEntity): JSONObject {
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
        // Champs d'identification généraux
        properties.put("id", node.id)
        properties.put("name", node.name)
        properties.put("buildingName", node.buildingName)
        properties.put("type", node.type.name)
        properties.put("typeLabel", node.type.label)
        properties.put("category", node.type.category)
        properties.put("status", FtthNodeVisuals.getStatusDisplayLabel(node.status, node.type))
        properties.put("statusCode", node.status.name)
        properties.put("etat", node.etat.label)
        properties.put("etatCode", node.etat.name)
        properties.put("latitude", node.latitude)
        properties.put("longitude", node.longitude)
        properties.put("address", node.address)
        properties.put("operator", node.operator)
        properties.put("hasBoitierFtth", node.hasBoitierFtth)
        properties.put("notes", node.notes)
        properties.put("technician", node.technicianName)
        properties.put("photoCount", node.photos.size)
        properties.put("photos", JSONArray(node.photos))
        properties.put("syncState", node.syncState.name)
        properties.put("updatedAt", formatDate(node.updatedAt))
        properties.put("timestamp", node.updatedAt)

        // Spécifique Poteau
        properties.put("poleNature", node.poleNature)
        properties.put("poleHeight", node.poleHeight)

        // Spécifique Chambre
        properties.put("chamberType", node.chamberType)

        // Spécifique Boîtier FTTH
        properties.put("boitierType", node.boitierType)
        properties.put("isSaturated", node.isSaturated)
        properties.put("boitierSupport", node.boitierSupport)
        properties.put("hasSplitter", node.hasSplitter)
        properties.put("splitterType", node.splitterType)

        // Spécifique SRO
        properties.put("sroType", node.sroType)
        properties.put("sroCapacity", node.sroCapacity)

        // Spécifique Immeuble
        properties.put("buildingFloors", node.buildingFloors)
        properties.put("buildingDwellings", node.buildingDwellings)
        properties.put("buildingBoitiersEtage", node.buildingBoitiersEtage)
        properties.put("hasLocalTechnique", node.hasLocalTechnique)
        properties.put("hasGaineMontante", node.hasGaineMontante)
        properties.put("syndicAuthorization", node.syndicAuthorization)
        properties.put("syndicContact", node.syndicContact)
        properties.put("buildingConnectionMode", node.buildingConnectionMode)

        // Spécifique Villa
        properties.put("villaConnectionMode", node.villaConnectionMode)

        feature.put("properties", properties)
        return feature
    }

    /**
     * Crée un objet JSON Feature GeoJSON pour une liaison / câble.
     */
    fun createLinkFeature(link: FtthLinkEntity, nodesMap: Map<String, FtthNodeEntity>): JSONObject? {
        val fromNode = nodesMap[link.fromNodeId] ?: return null
        val toNode = nodesMap[link.toNodeId] ?: return null

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
        props.put("fromNodeName", fromNode.name)
        props.put("toNodeName", toNode.name)
        props.put("cableType", link.cableType)
        props.put("installationType", link.installationType)
        props.put("capacityFO", link.capacityFO)
        props.put("lengthMeters", link.lengthMeters)
        props.put("status", link.status.label)
        props.put("statusCode", link.status.name)
        props.put("associatedTrackId", link.associatedTrackId)
        props.put("updatedAt", formatDate(link.updatedAt))
        props.put("entity_type", "cable")

        feature.put("properties", props)
        return feature
    }

    /**
     * Crée un objet JSON Feature GeoJSON pour une trace GPS / Trajet.
     */
    fun createTrackFeature(track: StoredTrack): JSONObject? {
        val pts = if (track.points.isNotEmpty()) track.points else track.rawPoints
        if (pts.size < 2) return null

        val feature = JSONObject()
        feature.put("type", "Feature")

        val geometry = JSONObject()
        geometry.put("type", "LineString")
        val coords = JSONArray()
        for (pt in pts) {
            val p = JSONArray()
            p.put(pt.longitude)
            p.put(pt.latitude)
            coords.put(p)
        }
        geometry.put("coordinates", coords)
        feature.put("geometry", geometry)

        val props = JSONObject()
        props.put("id", track.id)
        props.put("name", track.name)
        props.put("type", track.type)
        props.put("etatTechnique", track.etat)
        props.put("conduitAudit", track.conduitAudit)
        props.put("conduitType", track.conduitType)
        props.put("conduitCount", track.conduitCount)
        val diaArr = JSONArray()
        track.conduitDiameters.forEach { diaArr.put(it) }
        props.put("conduitDiameters", diaArr)
        props.put("conduitDiametersText", track.conduitDiameters.joinToString(", "))
        props.put("totalDistanceMeters", track.totalDistanceMeters)
        props.put("distanceMeters", track.totalDistanceMeters)
        props.put("pointCount", pts.size)
        props.put("startTime", formatDate(track.startTime))
        props.put("endTime", track.endTime?.let { formatDate(it) } ?: "")
        props.put("isSimplified", track.isSimplified)
        props.put("toleranceMeters", track.toleranceMeters ?: 0.0)
        props.put("photoCount", track.photos.size)
        props.put("featureKind", "GPS_TRACK")
        props.put("entity_type", "infra_lineaire")

        feature.put("properties", props)
        return feature
    }

    // --- 1. EXPORT GEOJSON GLOBAL (TOUTES LES COUCHES) ---

    fun exportGeoJson(
        context: Context,
        nodes: List<FtthNodeEntity>,
        links: List<FtthLinkEntity>,
        tracks: List<StoredTrack> = emptyList()
    ): File {
        val root = JSONObject()
        root.put("type", "FeatureCollection")
        root.put("name", "piquetage_ftth_global")
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.FRANCE).format(Date()))
        val features = JSONArray()

        val nodesMap = nodes.associateBy { it.id }

        // 1. Points pour tous les Nœuds
        for (node in nodes) {
            features.put(createNodeFeature(node))
        }

        // 2. LineStrings pour toutes les Liaisons
        for (link in links) {
            val linkFeat = createLinkFeature(link, nodesMap)
            if (linkFeat != null) features.put(linkFeat)
        }

        // 3. LineStrings pour tous les Trajets / Traces GPS
        val dedupedTracks = tracks.distinctBy { it.id }
        for (track in dedupedTracks) {
            val trackFeat = createTrackFeature(track)
            if (trackFeat != null) features.put(trackFeat)
        }

        root.put("features", features)

        val file = File(getExportDir(context), "piquetage_ftth_complet_${getTimestamp()}.geojson")
        file.writeText(root.toString(2), Charsets.UTF_8)
        return file
    }

    // Surcharge pour compatibilité Room
    @JvmName("exportGeoJsonWithRoomEntities")
    fun exportGeoJson(
        context: Context,
        nodes: List<FtthNodeEntity>,
        links: List<FtthLinkEntity>,
        tracksWithPoints: List<Pair<GpsTrackEntity, List<GpsTrackPointEntity>>>
    ): File {
        val converted = tracksWithPoints.map { toStoredTrack(it.first, it.second) }
        return exportGeoJson(context, nodes, links, converted)
    }

    // --- 2. EXPORT EXCLUSIF DES TRAJETS / TRACES GPS EN GEOJSON ---

    fun exportTracksGeoJson(context: Context, tracks: List<StoredTrack>): File {
        val root = JSONObject()
        root.put("type", "FeatureCollection")
        root.put("name", "trajets_traces_gps")
        root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.FRANCE).format(Date()))
        val features = JSONArray()

        val dedupedTracks = tracks.distinctBy { it.id }
        for (track in dedupedTracks) {
            val feat = createTrackFeature(track)
            if (feat != null) features.put(feat)
        }

        root.put("features", features)

        val file = File(getExportDir(context), "trajets_traces_gps_${getTimestamp()}.geojson")
        file.writeText(root.toString(2), Charsets.UTF_8)
        return file
    }

    // --- 3. EXPORT GEOJSON PAR TYPE DE NOEUD ---

    fun exportGeoJsonByNodeType(context: Context, nodes: List<FtthNodeEntity>): Map<FtthNodeType, File> {
        val result = mutableMapOf<FtthNodeType, File>()
        val ts = getTimestamp()

        val grouped = nodes.groupBy { it.type }
        for (type in FtthNodeType.values()) {
            val list = grouped[type] ?: emptyList()
            if (list.isEmpty()) continue

            val root = JSONObject()
            root.put("type", "FeatureCollection")
            root.put("name", "noeuds_${type.name.lowercase(Locale.ROOT)}")
            root.put("nodeType", type.name)
            root.put("nodeLabel", type.label)
            root.put("category", type.category)
            root.put("count", list.size)
            root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.FRANCE).format(Date()))

            val features = JSONArray()
            for (node in list) {
                features.put(createNodeFeature(node))
            }
            root.put("features", features)

            val suffix = when (type) {
                FtthNodeType.POTEAU -> "poteaux"
                FtthNodeType.CHAMBRE -> "chambres"
                FtthNodeType.BOITIER -> "boitiers"
                FtthNodeType.SRO -> "sro"
                FtthNodeType.IMMEUBLE -> "immeubles"
                FtthNodeType.VILLA -> "villas"
            }
            val file = File(getExportDir(context), "noeuds_${suffix}_${ts}.geojson")
            file.writeText(root.toString(2), Charsets.UTF_8)
            result[type] = file
        }

        return result
    }

    /**
     * Archive ZIP contenant uniquement les fichiers GeoJSON séparés par type de nœud.
     */
    fun exportNodesByTypeZip(context: Context, nodes: List<FtthNodeEntity>): File {
        val filesMap = exportGeoJsonByNodeType(context, nodes)
        val zipFile = File(getExportDir(context), "noeuds_par_type_${getTimestamp()}.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            for ((type, file) in filesMap) {
                addFileToZip(zos, file, file.name)
            }
        }
        return zipFile
    }

    // --- 4. EXPORT BORDEREAU CSV (AVEC TOUS LES CHAMPS DU PIQUETAGE) ---

    fun exportCsv(context: Context, nodes: List<FtthNodeEntity>): File {
        val file = File(getExportDir(context), "bordereau_piquetage_complet_${getTimestamp()}.csv")
        val sb = StringBuilder()

        // Entête complète en français avec séparateur point-virgule
        sb.append(
            listOf(
                "ID",
                "NOM",
                "NOM_BATIMENT",
                "TYPE",
                "CATEGORIE",
                "STATUT",
                "CONFORMITE",
                "LATITUDE",
                "LONGITUDE",
                "ADRESSE",
                "OPERATEUR",
                "BOITIER_FTTH",
                "NATURE_APPUI",
                "HAUTEUR_APPUI",
                "TYPE_CHAMBRE",
                "TYPE_BOITIER",
                "SATURE",
                "SUPPORT_BOITIER",
                "PRESENCE_SPLITTER",
                "TYPE_SPLITTER",
                "TYPE_SRO",
                "CAPACITE_SRO",
                "NB_ETAGES",
                "NB_LOGEMENTS",
                "NB_BOITIERS_ETAGE",
                "LOCAL_TECHNIQUE",
                "GAINE_MONTANTE",
                "AUTORISATION_SYNDIC",
                "CONTACT_SYNDIC",
                "RACCORDEMENT_IMMEUBLE",
                "RACCORDEMENT_VILLA",
                "COMMENTAIRE",
                "TECHNICIEN",
                "NB_PHOTOS",
                "DATE_MAJ"
            ).joinToString(";")
        ).append("\n")

        for (node in nodes) {
            val cols = listOf(
                escapeCsv(node.id),
                escapeCsv(node.name),
                escapeCsv(node.buildingName),
                escapeCsv(node.type.label),
                escapeCsv(node.type.category),
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
                if (node.hasSplitter) "Oui" else "Non",
                escapeCsv(node.splitterType),
                escapeCsv(node.sroType),
                escapeCsv(node.sroCapacity),
                node.buildingFloors.toString(),
                node.buildingDwellings.toString(),
                node.buildingBoitiersEtage.toString(),
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

    // --- 5. EXPORT KML & KMZ AVEC TOUTES LES COUCHES (GOOGLE EARTH) ---

    fun exportKml(
        context: Context,
        nodes: List<FtthNodeEntity>,
        links: List<FtthLinkEntity>,
        tracks: List<StoredTrack> = emptyList()
    ): File {
        val file = File(getExportDir(context), "reseau_ftth_toutes_couches_${getTimestamp()}.kml")
        val nodesMap = nodes.associateBy { it.id }

        val poles = nodes.filter { it.type == FtthNodeType.POTEAU }
        val chambers = nodes.filter { it.type == FtthNodeType.CHAMBRE }
        val boitiers = nodes.filter { it.type == FtthNodeType.BOITIER }
        val sros = nodes.filter { it.type == FtthNodeType.SRO }
        val buildings = nodes.filter { it.type == FtthNodeType.IMMEUBLE }
        val villas = nodes.filter { it.type == FtthNodeType.VILLA }
        val dedupedTracks = tracks.distinctBy { it.id }

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("  <Document>\n")
        sb.append("    <name>Relevé Terrain FTTH - Toutes les Couches</name>\n")
        sb.append("    <open>1</open>\n")

        // Styles pour chaque couche
        sb.append(
            """
    <Style id="style_poteau">
      <IconStyle>
        <color>ff0288d1</color>
        <scale>1.1</scale>
        <Icon><href>http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png</href></Icon>
      </IconStyle>
    </Style>
    <Style id="style_chambre">
      <IconStyle>
        <color>ffe65100</color>
        <scale>1.1</scale>
        <Icon><href>http://maps.google.com/mapfiles/kml/shapes/square.png</href></Icon>
      </IconStyle>
    </Style>
    <Style id="style_boitier">
      <IconStyle>
        <color>ff8e24aa</color>
        <scale>1.1</scale>
        <Icon><href>http://maps.google.com/mapfiles/kml/shapes/target.png</href></Icon>
      </IconStyle>
    </Style>
    <Style id="style_sro">
      <IconStyle>
        <color>ff2e7d32</color>
        <scale>1.3</scale>
        <Icon><href>http://maps.google.com/mapfiles/kml/shapes/star.png</href></Icon>
      </IconStyle>
    </Style>
    <Style id="style_immeuble">
      <IconStyle>
        <color>ffc2185b</color>
        <scale>1.1</scale>
        <Icon><href>http://maps.google.com/mapfiles/kml/shapes/caution.png</href></Icon>
      </IconStyle>
    </Style>
    <Style id="style_villa">
      <IconStyle>
        <color>ff00897b</color>
        <scale>1.0</scale>
        <Icon><href>http://maps.google.com/mapfiles/kml/shapes/homegardenbusiness.png</href></Icon>
      </IconStyle>
    </Style>
    <Style id="style_cable">
      <LineStyle>
        <color>ff00bcd4</color>
        <width>3.5</width>
      </LineStyle>
    </Style>
    <Style id="style_track">
      <LineStyle>
        <color>ff0091ea</color>
        <width>4.5</width>
      </LineStyle>
    </Style>
"""
        )

        // Couche 1 : Poteaux
        appendNodesFolder(sb, "Couche 1 : Poteaux & Appuis Aériens (${poles.size})", poles, "style_poteau")
        // Couche 2 : Chambres
        appendNodesFolder(sb, "Couche 2 : Chambres de Tirage (${chambers.size})", chambers, "style_chambre")
        // Couche 3 : Boîtiers FTTH
        appendNodesFolder(sb, "Couche 3 : Boîtiers FTTH - BPE/PBO (${boitiers.size})", boitiers, "style_boitier")
        // Couche 4 : SRO
        appendNodesFolder(sb, "Couche 4 : Sous-Répartiteurs SRO (${sros.size})", sros, "style_sro")
        // Couche 5 : Immeubles Collectifs
        appendNodesFolder(sb, "Couche 5 : Immeubles Collectifs (${buildings.size})", buildings, "style_immeuble")
        // Couche 6 : Villas & Pavillons
        appendNodesFolder(sb, "Couche 6 : Villas & Pavillons (${villas.size})", villas, "style_villa")

        // Couche 7 : Liaisons & Câbles
        sb.append("    <Folder id=\"couche_cables\">\n")
        sb.append("      <name>Couche 7 : Liaisons &amp; Câbles Fibre (${links.size})</name>\n")
        for (link in links) {
            val from = nodesMap[link.fromNodeId]
            val to = nodesMap[link.toNodeId]
            if (from != null && to != null) {
                sb.append("      <Placemark>\n")
                sb.append("        <name>${escapeXml(link.id)} (${link.capacityFO} FO - ${escapeXml(link.cableType)})</name>\n")
                sb.append("        <styleUrl>#style_cable</styleUrl>\n")
                sb.append("        <description><![CDATA[\n")
                sb.append(
                    """
<div style="font-family:sans-serif;font-size:13px;">
  <h3 style="margin:0 0 8px;color:#0B7BC1;">Câble : ${escapeXml(link.id)}</h3>
  <table border="1" cellpadding="4" cellspacing="0" style="border-collapse:collapse;width:100%;">
    <tr><td><b>De</b></td><td>${escapeXml(from.name)} (${from.id})</td></tr>
    <tr><td><b>Vers</b></td><td>${escapeXml(to.name)} (${to.id})</td></tr>
    <tr><td><b>Type de câble</b></td><td>${escapeXml(link.cableType)}</td></tr>
    <tr><td><b>Type de pose</b></td><td>${escapeXml(link.installationType)}</td></tr>
    <tr><td><b>Capacité FO</b></td><td>${link.capacityFO} FO</td></tr>
    <tr><td><b>Longueur mesurée</b></td><td>${link.lengthMeters} m</td></tr>
    <tr><td><b>Statut</b></td><td>${escapeXml(link.status.label)}</td></tr>
    <tr><td><b>Dernière mise à jour</b></td><td>${formatDate(link.updatedAt)}</td></tr>
  </table>
</div>
"""
                )
                sb.append("        ]]></description>\n")
                sb.append("        <LineString>\n")
                sb.append("          <coordinates>${from.longitude},${from.latitude},0 ${to.longitude},${to.latitude},0</coordinates>\n")
                sb.append("        </LineString>\n")
                sb.append("      </Placemark>\n")
            }
        }
        sb.append("    </Folder>\n")

        // Couche 8 : Traces & Trajets GPS
        sb.append("    <Folder id=\"couche_trajets\">\n")
        sb.append("      <name>Couche 8 : Traces &amp; Trajets GPS (${dedupedTracks.size})</name>\n")
        for (track in dedupedTracks) {
            val pts = if (track.points.isNotEmpty()) track.points else track.rawPoints
            if (pts.size >= 2) {
                sb.append("      <Placemark>\n")
                sb.append("        <name>${escapeXml(track.name)} (${String.format(Locale.FRANCE, "%.1f", track.totalDistanceMeters)} m)</name>\n")
                sb.append("        <styleUrl>#style_track</styleUrl>\n")
                sb.append("        <description><![CDATA[\n")
                sb.append(
                    """
<div style="font-family:sans-serif;font-size:13px;">
  <h3 style="margin:0 0 8px;color:#0B7BC1;">Trajet GPS : ${escapeXml(track.name)}</h3>
  <table border="1" cellpadding="4" cellspacing="0" style="border-collapse:collapse;width:100%;">
    <tr><td><b>Identifiant</b></td><td>${escapeXml(track.id)}</td></tr>
    <tr><td><b>Type de tracé</b></td><td>${escapeXml(track.type)}</td></tr>
    <tr><td><b>État / Conformité</b></td><td>${escapeXml(track.etat)}</td></tr>
    <tr><td><b>Audit Fourreaux</b></td><td>${escapeXml(track.conduitAudit)}</td></tr>
    <tr><td><b>Type de Conduit</b></td><td>${escapeXml(track.conduitType)}</td></tr>
    <tr><td><b>Nombre de Conduits</b></td><td>${track.conduitCount}</td></tr>
    <tr><td><b>Diamètres</b></td><td>${escapeXml(track.conduitDiameters.joinToString(", "))}</td></tr>
    <tr><td><b>Distance Totale</b></td><td>${String.format(Locale.FRANCE, "%.2f", track.totalDistanceMeters)} m</td></tr>
    <tr><td><b>Nombre de Points</b></td><td>${pts.size}</td></tr>
    <tr><td><b>Heure de Début</b></td><td>${formatDate(track.startTime)}</td></tr>
    <tr><td><b>Heure de Fin</b></td><td>${track.endTime?.let { formatDate(it) } ?: "-"}</td></tr>
  </table>
</div>
"""
                )
                sb.append("        ]]></description>\n")
                sb.append("        <LineString>\n")
                val coordString = pts.joinToString(" ") { "${it.longitude},${it.latitude},${it.altitude}" }
                sb.append("          <coordinates>$coordString</coordinates>\n")
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

    private fun appendNodesFolder(
        sb: StringBuilder,
        folderName: String,
        nodes: List<FtthNodeEntity>,
        styleId: String
    ) {
        sb.append("    <Folder>\n")
        sb.append("      <name>${escapeXml(folderName)}</name>\n")
        for (node in nodes) {
            val displayName = if (node.buildingName.isNotBlank()) "${node.id} - ${node.buildingName}" else "${node.id} (${node.name})"
            sb.append("      <Placemark>\n")
            sb.append("        <name>${escapeXml(displayName)}</name>\n")
            sb.append("        <styleUrl>#$styleId</styleUrl>\n")
            sb.append("        <description><![CDATA[\n")
            sb.append(buildNodeHtmlDescription(node))
            sb.append("        ]]></description>\n")
            sb.append("        <Point>\n")
            sb.append("          <coordinates>${node.longitude},${node.latitude},0</coordinates>\n")
            sb.append("        </Point>\n")
            sb.append("      </Placemark>\n")
        }
        sb.append("    </Folder>\n")
    }

    private fun buildNodeHtmlDescription(node: FtthNodeEntity): String {
        val rows = mutableListOf<Pair<String, String>>()
        rows.add("Identifiant" to node.id)
        rows.add("Nom / Repère" to node.name)
        if (node.buildingName.isNotBlank()) rows.add("Bâtiment" to node.buildingName)
        rows.add("Type d'entité" to "${node.type.label} (${node.type.category})")
        rows.add("Statut opérationnel" to FtthNodeVisuals.getStatusDisplayLabel(node.status, node.type))
        rows.add("Conformité technique" to node.etat.label)
        rows.add("Adresse" to node.address)
        if (node.operator.isNotBlank()) rows.add("Opérateur" to node.operator)
        rows.add("Boîtier FTTH présent" to if (node.hasBoitierFtth) "Oui" else "Non")

        // Spécificités par type
        when (node.type) {
            FtthNodeType.POTEAU -> {
                rows.add("Nature appui" to node.poleNature)
                rows.add("Hauteur appui" to "${node.poleHeight} m")
            }
            FtthNodeType.CHAMBRE -> {
                rows.add("Type de chambre" to node.chamberType)
            }
            FtthNodeType.BOITIER -> {
                rows.add("Modèle de boîtier" to node.boitierType)
                rows.add("Support" to node.boitierSupport)
                rows.add("Saturé" to if (node.isSaturated) "Oui" else "Non")
                rows.add("Splitter optique" to if (node.hasSplitter) "Oui (${node.splitterType})" else "Non")
            }
            FtthNodeType.SRO -> {
                rows.add("Type SRO" to node.sroType)
                rows.add("Capacité FO" to node.sroCapacity)
            }
            FtthNodeType.IMMEUBLE -> {
                rows.add("Nombre d'étages" to node.buildingFloors.toString())
                rows.add("Nombre de logements" to node.buildingDwellings.toString())
                rows.add("Boîtiers d'étage" to node.buildingBoitiersEtage.toString())
                rows.add("Local technique" to if (node.hasLocalTechnique) "Oui" else "Non")
                rows.add("Gaine montante" to if (node.hasGaineMontante) "Oui" else "Non")
                rows.add("Autorisation syndic" to node.syndicAuthorization)
                if (node.syndicContact.isNotBlank()) rows.add("Contact syndic" to node.syndicContact)
                rows.add("Mode raccordement" to node.buildingConnectionMode)
            }
            FtthNodeType.VILLA -> {
                rows.add("Mode raccordement" to node.villaConnectionMode)
            }
        }

        if (node.notes.isNotBlank()) rows.add("Commentaires" to node.notes)
        rows.add("Technicien" to node.technicianName)
        rows.add("Nombre de photos" to "${node.photos.size} photo(s)")
        rows.add("Horodatage" to formatDate(node.updatedAt))

        val sb = StringBuilder()
        sb.append("<div style=\"font-family:sans-serif;font-size:13px;\">\n")
        sb.append("  <h3 style=\"margin:0 0 8px;color:#0B7BC1;\">${escapeXml(node.id)} - ${escapeXml(node.type.label)}</h3>\n")
        sb.append("  <table border=\"1\" cellpadding=\"4\" cellspacing=\"0\" style=\"border-collapse:collapse;width:100%;font-size:12px;\">\n")
        for ((k, v) in rows) {
            sb.append("    <tr><td style=\"background:#f0f7fb;width:38%;\"><b>${escapeXml(k)}</b></td><td>${escapeXml(v)}</td></tr>\n")
        }
        sb.append("  </table>\n")
        sb.append("</div>\n")
        return sb.toString()
    }

    private fun escapeXml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    // Surcharges KML
    fun exportKml(context: Context, nodes: List<FtthNodeEntity>, links: List<FtthLinkEntity>): File =
        exportKml(context, nodes, links, emptyList<StoredTrack>())

    @JvmName("exportKmlWithRoomEntities")
    fun exportKml(
        context: Context,
        nodes: List<FtthNodeEntity>,
        links: List<FtthLinkEntity>,
        tracksWithPoints: List<Pair<GpsTrackEntity, List<GpsTrackPointEntity>>>
    ): File = exportKml(context, nodes, links, tracksWithPoints.map { toStoredTrack(it.first, it.second) })

    // --- KMZ COMPLET (TOUTES LES COUCHES) ---

    fun exportKmz(
        context: Context,
        nodes: List<FtthNodeEntity>,
        links: List<FtthLinkEntity>,
        tracks: List<StoredTrack> = emptyList()
    ): File {
        val kmzFile = File(getExportDir(context), "releve_terrain_toutes_couches_${getTimestamp()}.kmz")
        val kmlFile = exportKml(context, nodes, links, tracks)

        ZipOutputStream(FileOutputStream(kmzFile)).use { zos ->
            addFileToZip(zos, kmlFile, "doc.kml")
        }
        return kmzFile
    }

    fun exportKmz(context: Context, nodes: List<FtthNodeEntity>, links: List<FtthLinkEntity>): File =
        exportKmz(context, nodes, links, emptyList<StoredTrack>())

    @JvmName("exportKmzWithRoomEntities")
    fun exportKmz(
        context: Context,
        nodes: List<FtthNodeEntity>,
        links: List<FtthLinkEntity>,
        tracksWithPoints: List<Pair<GpsTrackEntity, List<GpsTrackPointEntity>>>
    ): File = exportKmz(context, nodes, links, tracksWithPoints.map { toStoredTrack(it.first, it.second) })

    // --- 6. EXPORT DOSSIER ZIP COMPLET (TOUTES COUCHES + TRACES + PAR TYPE + PHOTOS) ---

    fun exportCompleteZip(
        context: Context,
        nodes: List<FtthNodeEntity>,
        links: List<FtthLinkEntity>,
        tracks: List<StoredTrack> = emptyList()
    ): File {
        val zipFile = File(getExportDir(context), "dossier_releve_terrain_complet_${getTimestamp()}.zip")

        // 1. Fichiers globaux
        val geoJsonGlobal = exportGeoJson(context, nodes, links, tracks)
        val kmzFile = exportKmz(context, nodes, links, tracks)
        val kmlFile = exportKml(context, nodes, links, tracks)
        val csvFile = exportCsv(context, nodes)

        // 2. Fichier dédié Trajets / Traces GPS
        val tracksGeoJson = exportTracksGeoJson(context, tracks)

        // 3. Fichiers GeoJSON séparés par type de nœud
        val nodeTypeFiles = exportGeoJsonByNodeType(context, nodes)

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // Fichiers racine
            addFileToZip(zos, geoJsonGlobal, "releve_terrain_global.geojson")
            addFileToZip(zos, tracksGeoJson, "trajets_traces_gps.geojson")
            addFileToZip(zos, kmzFile, "releve_terrain_toutes_couches.kmz")
            addFileToZip(zos, kmlFile, "reseau_ftth_toutes_couches.kml")
            addFileToZip(zos, csvFile, "bordereau_piquetage_complet.csv")

            // Dossier pour les GeoJSON par type de nœud
            for ((type, file) in nodeTypeFiles) {
                addFileToZip(zos, file, "noeuds_par_type/${file.name}")
            }

            // Dossier des photos réelles
            for (node in nodes) {
                for (photoPath in node.photos) {
                    val pFile = com.example.data.photo.PhotoStorageManager.resolvePhotoFile(context, photoPath)
                    if (pFile.exists() && pFile.length() > 0) {
                        val sanitizedNodeId = node.id.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                        addFileToZip(zos, pFile, "photos/${sanitizedNodeId}/${pFile.name}")
                    }
                }
            }

            // Photos des traces GPS le cas échéant
            for (track in tracks) {
                for (photo in track.photos) {
                    val pFile = com.example.data.photo.PhotoStorageManager.resolvePhotoFile(context, photo.photoPath)
                    if (pFile.exists() && pFile.length() > 0) {
                        val sanitizedTrackId = track.id.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
                        addFileToZip(zos, pFile, "photos_traces/${sanitizedTrackId}/${pFile.name}")
                    }
                }
            }
        }

        return zipFile
    }

    fun exportCompleteZip(context: Context, nodes: List<FtthNodeEntity>, links: List<FtthLinkEntity>): File =
        exportCompleteZip(context, nodes, links, emptyList<StoredTrack>())

    @JvmName("exportCompleteZipWithRoomEntities")
    fun exportCompleteZip(
        context: Context,
        nodes: List<FtthNodeEntity>,
        links: List<FtthLinkEntity>,
        tracksWithPoints: List<Pair<GpsTrackEntity, List<GpsTrackPointEntity>>>
    ): File = exportCompleteZip(context, nodes, links, tracksWithPoints.map { toStoredTrack(it.first, it.second) })

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
