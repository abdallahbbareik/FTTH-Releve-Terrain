package com.example.data.util

import com.example.data.local.FtthNodeEntity
import org.json.JSONObject
import java.util.Locale

/**
 * Générateur d'exportation Excel multi-feuilles (.xls / XML Spreadsheet 2003) pour la qualification FTTH :
 * - Feuille 1 : SRO
 * - Feuille 2 : Boîtiers
 * - Feuille 3 : Chambres
 * - Feuille 4 : Poteaux
 * - Feuille 5 : Autres / Non classifiés
 * - Feuille 6 : Câbles FO
 * - Feuille 7 : Anomalies APD (Uniquement les anomalies d'accrochage >10m ou câbles non arrêtés)
 */
object ExcelExporterHelper {

    fun generateExcelPayload(
        classifiedNodes: List<ClassifiedKmzNode>,
        connectivityMap: Map<String, BoitierConnectivity>,
        classifiedCables: List<ClassifiedCable>,
        nodes: List<FtthNodeEntity>
    ): Pair<String, String> {

        // Regrouper les nœuds par catégorie
        val sroNodes = classifiedNodes.filter { it.category == KmzNodeCategory.SRO }
        val boitierNodes = classifiedNodes.filter { it.category == KmzNodeCategory.BOITIER }
        val chambreNodes = classifiedNodes.filter { it.category == KmzNodeCategory.CHAMBRE }
        val poteauNodes = classifiedNodes.filter { it.category == KmzNodeCategory.POTEAU }
        val autreNodes = classifiedNodes.filter { it.category == KmzNodeCategory.AUTRE }

        // Filtrer les anomalies APD (Seulement les éléments ayant un écart > 10m ou non raccordés)
        data class ApdAnomalyRow(
            val objectType: String,
            val idAndName: String,
            val categoryLabel: String,
            val gapMeters: Double,
            val anomalyDetail: String,
            val cableName: String,
            val coordinatesStr: String
        )

        val anomaliesList = mutableListOf<ApdAnomalyRow>()

        // 1. Anomalies sur les Boîtiers / SRO
        for (item in classifiedNodes) {
            val conn = connectivityMap[item.id] ?: continue
            val isAnomaly = conn.anomalieApd.contains("Anomalie", ignoreCase = true) || conn.snapDistanceMeters > 10.0
            if (isAnomaly) {
                val coords = String.format(Locale.ROOT, "%.6f, %.6f", item.latitude, item.longitude)
                anomaliesList.add(
                    ApdAnomalyRow(
                        objectType = if (item.category == KmzNodeCategory.SRO) "SRO" else "Boîtier",
                        idAndName = "${item.id} - ${item.name}",
                        categoryLabel = item.category.label,
                        gapMeters = conn.snapDistanceMeters,
                        anomalyDetail = conn.anomalieApd,
                        cableName = conn.incomingCableName,
                        coordinatesStr = coords
                    )
                )
            }
        }

        // 2. Anomalies sur les extrémités des Câbles (Câbles non arrêtés à <= 10m d'un boîtier)
        for (cable in classifiedCables) {
            val cleanCableName = cable.name.removePrefix("::").removePrefix(":").removePrefix(" :").trim()
            val supportAnalysis = CableSupportAnalyzer.analyzeClassifiedCableSupports(cable, nodes)

            val originSupport = supportAnalysis.supports.firstOrNull()
            val destSupport = supportAnalysis.supports.lastOrNull()

            val originBoitier = originSupport?.hostedBoitiers?.firstOrNull()
            val destBoitier = destSupport?.hostedBoitiers?.firstOrNull()

            if (destBoitier == null) {
                anomaliesList.add(
                    ApdAnomalyRow(
                        objectType = "Câble FO (Extrémité Arrivée)",
                        idAndName = "${cable.id} - $cleanCableName",
                        categoryLabel = "Câble FO ${cable.capacityFO}",
                        gapMeters = 0.0,
                        anomalyDetail = "Anomalie APD : L'extrémité finale du câble ne s'arrête sur aucun boîtier à <=10m",
                        cableName = cleanCableName,
                        coordinatesStr = cable.endCoordinatesLabel
                    )
                )
            }
            if (originBoitier == null && !cable.cableType.equals("Transport", ignoreCase = true)) {
                anomaliesList.add(
                    ApdAnomalyRow(
                        objectType = "Câble FO (Extrémité Départ)",
                        idAndName = "${cable.id} - $cleanCableName",
                        categoryLabel = "Câble FO ${cable.capacityFO}",
                        gapMeters = 0.0,
                        anomalyDetail = "Anomalie APD : L'origine du câble ne démarre d'aucun boîtier/SRO à <=10m",
                        cableName = cleanCableName,
                        coordinatesStr = cable.startCoordinatesLabel
                    )
                )
            }
        }

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<?mso-application progid=\"Excel.Sheet\"?>\n")
        sb.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"\n")
        sb.append(" xmlns:o=\"urn:schemas-microsoft-com:office:office\"\n")
        sb.append(" xmlns:x=\"urn:schemas-microsoft-com:office:excel\"\n")
        sb.append(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\">\n")

        // STYLES
        sb.append(" <Styles>\n")
        sb.append("  <Style ss:ID=\"Default\" ss:Name=\"Normal\">\n")
        sb.append("   <Font ss:FontName=\"Calibri\" ss:Size=\"11\" ss:Color=\"#000000\"/>\n")
        sb.append("  </Style>\n")
        sb.append("  <Style ss:ID=\"HeaderMain\">\n")
        sb.append("   <Font ss:FontName=\"Calibri\" ss:Size=\"11\" ss:Bold=\"1\" ss:Color=\"#FFFFFF\"/>\n")
        sb.append("   <Interior ss:Color=\"#0F172A\" ss:Pattern=\"Solid\"/>\n") // Navy
        sb.append("  </Style>\n")
        sb.append("  <Style ss:ID=\"HeaderBoitier\">\n")
        sb.append("   <Font ss:FontName=\"Calibri\" ss:Size=\"11\" ss:Bold=\"1\" ss:Color=\"#FFFFFF\"/>\n")
        sb.append("   <Interior ss:Color=\"#0D9488\" ss:Pattern=\"Solid\"/>\n") // Teal
        sb.append("  </Style>\n")
        sb.append("  <Style ss:ID=\"HeaderSro\">\n")
        sb.append("   <Font ss:FontName=\"Calibri\" ss:Size=\"11\" ss:Bold=\"1\" ss:Color=\"#FFFFFF\"/>\n")
        sb.append("   <Interior ss:Color=\"#9333EA\" ss:Pattern=\"Solid\"/>\n") // Purple
        sb.append("  </Style>\n")
        sb.append("  <Style ss:ID=\"HeaderAnomaly\">\n")
        sb.append("   <Font ss:FontName=\"Calibri\" ss:Size=\"11\" ss:Bold=\"1\" ss:Color=\"#FFFFFF\"/>\n")
        sb.append("   <Interior ss:Color=\"#DC2626\" ss:Pattern=\"Solid\"/>\n") // Red
        sb.append("  </Style>\n")
        sb.append("  <Style ss:ID=\"AnomalyCell\">\n")
        sb.append("   <Font ss:FontName=\"Calibri\" ss:Size=\"11\" ss:Bold=\"1\" ss:Color=\"#B45309\"/>\n")
        sb.append("   <Interior ss:Color=\"#FEF3C7\" ss:Pattern=\"Solid\"/>\n") // Amber light
        sb.append("  </Style>\n")
        sb.append(" </Styles>\n")

        // 1. FEUILLE : SRO
        sb.append(buildNodesWorksheet("SRO", sroNodes, connectivityMap, true, "HeaderSro"))

        // 2. FEUILLE : Boîtiers
        sb.append(buildNodesWorksheet("Boîtiers", boitierNodes, connectivityMap, true, "HeaderBoitier"))

        // 3. FEUILLE : Chambres
        sb.append(buildNodesWorksheet("Chambres", chambreNodes, connectivityMap, false, "HeaderMain"))

        // 4. FEUILLE : Poteaux
        sb.append(buildNodesWorksheet("Poteaux", poteauNodes, connectivityMap, false, "HeaderMain"))

        // 5. FEUILLE : Autres
        sb.append(buildNodesWorksheet("Autres", autreNodes, connectivityMap, false, "HeaderMain"))

        // 6. FEUILLE : Câbles FO
        sb.append(buildCablesWorksheet("Câbles FO", classifiedCables, nodes))

        // 7. FEUILLE : Anomalies APD
        sb.append(" <Worksheet ss:Name=\"Anomalies APD\">\n")
        sb.append("  <Table>\n")
        sb.append("   <Column ss:Width=\"150\"/>\n")
        sb.append("   <Column ss:Width=\"220\"/>\n")
        sb.append("   <Column ss:Width=\"120\"/>\n")
        sb.append("   <Column ss:Width=\"110\"/>\n")
        sb.append("   <Column ss:Width=\"380\"/>\n")
        sb.append("   <Column ss:Width=\"180\"/>\n")
        sb.append("   <Column ss:Width=\"160\"/>\n")

        sb.append("   <Row>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderAnomaly\"><Data ss:Type=\"String\">Type Objet</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderAnomaly\"><Data ss:Type=\"String\">ID / Nom Objet</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderAnomaly\"><Data ss:Type=\"String\">Catégorie</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderAnomaly\"><Data ss:Type=\"String\">Écart Accrochage (m)</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderAnomaly\"><Data ss:Type=\"String\">Détail de l'Anomalie APD</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderAnomaly\"><Data ss:Type=\"String\">Câble Concerné</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderAnomaly\"><Data ss:Type=\"String\">Coordonnées GPS</Data></Cell>\n")
        sb.append("   </Row>\n")

        if (anomaliesList.isEmpty()) {
            sb.append("   <Row>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">Aucune anomalie d'accrochage détectée. Réseau 100% conforme (<=10m).</Data></Cell>\n")
            sb.append("   </Row>\n")
        } else {
            for (ano in anomaliesList) {
                sb.append("   <Row>\n")
                sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(ano.objectType)}</Data></Cell>\n")
                sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(ano.idAndName)}</Data></Cell>\n")
                sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(ano.categoryLabel)}</Data></Cell>\n")
                sb.append("    <Cell><Data ss:Type=\"Number\">${String.format(Locale.ROOT, "%.1f", ano.gapMeters)}</Data></Cell>\n")
                sb.append("    <Cell ss:StyleID=\"AnomalyCell\"><Data ss:Type=\"String\">${xmlEscape(ano.anomalyDetail)}</Data></Cell>\n")
                sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(ano.cableName)}</Data></Cell>\n")
                sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(ano.coordinatesStr)}</Data></Cell>\n")
                sb.append("   </Row>\n")
            }
        }

        sb.append("  </Table>\n")
        sb.append(" </Worksheet>\n")

        sb.append("</Workbook>\n")

        val fileName = "qualification_ftth_${System.currentTimeMillis()}.xls"
        return Pair(fileName, sb.toString())
    }

    private fun buildNodesWorksheet(
        sheetName: String,
        nodeList: List<ClassifiedKmzNode>,
        connectivityMap: Map<String, BoitierConnectivity>,
        includeConnectivities: Boolean,
        headerStyleId: String
    ): String {
        val sb = StringBuilder()
        sb.append(" <Worksheet ss:Name=\"${xmlEscape(sheetName)}\">\n")
        sb.append("  <Table>\n")
        sb.append("   <Column ss:Width=\"120\"/>\n")
        sb.append("   <Column ss:Width=\"180\"/>\n")
        sb.append("   <Column ss:Width=\"140\"/>\n")
        sb.append("   <Column ss:Width=\"140\"/>\n")
        if (includeConnectivities) {
            sb.append("   <Column ss:Width=\"160\"/>\n")
            sb.append("   <Column ss:Width=\"220\"/>\n")
            sb.append("   <Column ss:Width=\"260\"/>\n")
        }
        sb.append("   <Column ss:Width=\"220\"/>\n")
        sb.append("   <Column ss:Width=\"100\"/>\n")
        sb.append("   <Column ss:Width=\"100\"/>\n")
        sb.append("   <Column ss:Width=\"120\"/>\n")

        sb.append("   <Row>\n")
        sb.append("    <Cell ss:StyleID=\"$headerStyleId\"><Data ss:Type=\"String\">ID</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"$headerStyleId\"><Data ss:Type=\"String\">Nom Nœud</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"$headerStyleId\"><Data ss:Type=\"String\">TYPE_FONC</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"$headerStyleId\"><Data ss:Type=\"String\">Référence</Data></Cell>\n")
        if (includeConnectivities) {
            sb.append("    <Cell ss:StyleID=\"$headerStyleId\"><Data ss:Type=\"String\">Câble Entrée</Data></Cell>\n")
            sb.append("    <Cell ss:StyleID=\"$headerStyleId\"><Data ss:Type=\"String\">Câbles Sortie</Data></Cell>\n")
            sb.append("    <Cell ss:StyleID=\"$headerStyleId\"><Data ss:Type=\"String\">Accrochage / Anomalie APD</Data></Cell>\n")
        }
        sb.append("    <Cell ss:StyleID=\"$headerStyleId\"><Data ss:Type=\"String\">Règle Appliquée</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"$headerStyleId\"><Data ss:Type=\"String\">Latitude</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"$headerStyleId\"><Data ss:Type=\"String\">Longitude</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"$headerStyleId\"><Data ss:Type=\"String\">Fichier Source</Data></Cell>\n")
        sb.append("   </Row>\n")

        if (nodeList.isEmpty()) {
            sb.append("   <Row>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">Aucun élément dans cette catégorie.</Data></Cell>\n")
            sb.append("   </Row>\n")
        } else {
            for (item in nodeList) {
                val conn = connectivityMap[item.id]
                val cableIn = conn?.incomingCableName ?: "-"
                val cablesOut = conn?.outgoingCablesStr ?: "-"
                val anomalie = conn?.anomalieApd ?: "-"
                val isAnomaly = anomalie.contains("Anomalie", ignoreCase = true)

                sb.append("   <Row>\n")
                sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(item.id)}</Data></Cell>\n")
                sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(item.name)}</Data></Cell>\n")
                sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(item.typeFonc)}</Data></Cell>\n")
                sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(item.reference)}</Data></Cell>\n")
                if (includeConnectivities) {
                    sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(cableIn)}</Data></Cell>\n")
                    sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(cablesOut)}</Data></Cell>\n")
                    if (isAnomaly) {
                        sb.append("    <Cell ss:StyleID=\"AnomalyCell\"><Data ss:Type=\"String\">${xmlEscape(anomalie)}</Data></Cell>\n")
                    } else {
                        sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(anomalie)}</Data></Cell>\n")
                    }
                }
                sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(item.ruleApplied)}</Data></Cell>\n")
                sb.append("    <Cell><Data ss:Type=\"Number\">${item.latitude}</Data></Cell>\n")
                sb.append("    <Cell><Data ss:Type=\"Number\">${item.longitude}</Data></Cell>\n")
                sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(item.sourceFile)}</Data></Cell>\n")
                sb.append("   </Row>\n")
            }
        }

        sb.append("  </Table>\n")
        sb.append(" </Worksheet>\n")
        return sb.toString()
    }

    private fun buildCablesWorksheet(
        sheetName: String,
        cables: List<ClassifiedCable>,
        nodes: List<FtthNodeEntity>
    ): String {
        val sb = StringBuilder()
        sb.append(" <Worksheet ss:Name=\"${xmlEscape(sheetName)}\">\n")
        sb.append("  <Table>\n")
        sb.append("   <Column ss:Width=\"120\"/>\n")
        sb.append("   <Column ss:Width=\"180\"/>\n")
        sb.append("   <Column ss:Width=\"80\"/>\n")
        sb.append("   <Column ss:Width=\"100\"/>\n")
        sb.append("   <Column ss:Width=\"110\"/>\n")
        sb.append("   <Column ss:Width=\"100\"/>\n")
        sb.append("   <Column ss:Width=\"100\"/>\n")
        sb.append("   <Column ss:Width=\"180\"/>\n")
        sb.append("   <Column ss:Width=\"180\"/>\n")
        sb.append("   <Column ss:Width=\"300\"/>\n")
        sb.append("   <Column ss:Width=\"140\"/>\n")
        sb.append("   <Column ss:Width=\"140\"/>\n")
        sb.append("   <Column ss:Width=\"120\"/>\n")

        sb.append("   <Row>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderMain\"><Data ss:Type=\"String\">ID</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderMain\"><Data ss:Type=\"String\">Nom Câble</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderMain\"><Data ss:Type=\"String\">Tronçons</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderMain\"><Data ss:Type=\"String\">Capacité FO</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderMain\"><Data ss:Type=\"String\">Type Câble</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderMain\"><Data ss:Type=\"String\">Pose</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderMain\"><Data ss:Type=\"String\">Longueur (m)</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderMain\"><Data ss:Type=\"String\">Origine Départ</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderMain\"><Data ss:Type=\"String\">Extrémité Arrivée</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderMain\"><Data ss:Type=\"String\">Supports Traversés</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderMain\"><Data ss:Type=\"String\">Départ Coords</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderMain\"><Data ss:Type=\"String\">Arrivée Coords</Data></Cell>\n")
        sb.append("    <Cell ss:StyleID=\"HeaderMain\"><Data ss:Type=\"String\">Fichier Source</Data></Cell>\n")
        sb.append("   </Row>\n")

        for (c in cables) {
            val cleanName = c.name.removePrefix("::").removePrefix(":").removePrefix(" :").trim()
            val supportAnalysis = CableSupportAnalyzer.analyzeClassifiedCableSupports(c, nodes)

            val originStr = supportAnalysis.supports.firstOrNull()?.let { "${it.supportTypeLabel} [${it.node.name}]" } ?: "-"
            val destStr = supportAnalysis.supports.lastOrNull()?.let { "${it.supportTypeLabel} [${it.node.name}]" } ?: "-"

            val supportsStr = supportAnalysis.supports.joinToString(" -> ") { supp ->
                var str = "${supp.index}. ${supp.supportTypeLabel} [${supp.node.name}]"
                if (supp.hostedBoitiers.isNotEmpty()) {
                    val bNames = supp.hostedBoitiers.joinToString(", ") { it.name }
                    str += " (Boîtier: $bNames)"
                }
                str
            }

            sb.append("   <Row>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(c.id)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(cleanName)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"Number\">${c.segmentCount}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(c.capacityFO)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(c.cableType)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(c.installationType)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"Number\">${String.format(Locale.ROOT, "%.1f", c.totalLengthMeters)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(originStr)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(destStr)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(supportsStr)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(c.startCoordinatesLabel)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(c.endCoordinatesLabel)}</Data></Cell>\n")
            sb.append("    <Cell><Data ss:Type=\"String\">${xmlEscape(c.sourceFile)}</Data></Cell>\n")
            sb.append("   </Row>\n")
        }

        sb.append("  </Table>\n")
        sb.append(" </Worksheet>\n")
        return sb.toString()
    }

    private fun xmlEscape(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
