package com.example.data.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.local.FtthNodeEntity
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Gestionnaire d'exportation pour le Synoptique FTTH :
 * - Export Excel (.xlsx) : Tableaux arborescents des boîtiers, tronçons de câbles et supports traversés.
 * - Export PDF (.pdf) : Plan synoptique vectoriel haute résolution + Bordereaux des tronçons et boîtiers.
 */
object FtthSynopticExporter {

    // =========================================================================
    // 1. EXPORT EXCEL DU SYNOPTIQUE (.XLSX)
    // =========================================================================

    fun generateSynopticExcelPayload(
        graph: SynopticGraph,
        connectivities: Map<String, BoitierConnectivity>,
        cables: List<ClassifiedCable>,
        nodes: List<FtthNodeEntity>
    ): Pair<String, ByteArray> {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            fun addEntry(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            val sheet1Xml = buildSynopticNodesSheet(graph, connectivities)
            val sheet2Xml = buildSynopticLinksSheet(graph)
            val sheet3Xml = buildSynopticCablesSheet(cables, nodes)

            addEntry("[Content_Types].xml", buildContentTypesXml())
            addEntry("_rels/.rels", buildRootRelsXml())
            addEntry("xl/workbook.xml", buildWorkbookXml())
            addEntry("xl/_rels/workbook.xml.rels", buildWorkbookRelsXml())
            addEntry("xl/styles.xml", buildStylesXml())

            addEntry("xl/worksheets/sheet1.xml", sheet1Xml)
            addEntry("xl/worksheets/sheet2.xml", sheet2Xml)
            addEntry("xl/worksheets/sheet3.xml", sheet3Xml)
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date())
        val fileName = "synoptique_ftth_$timestamp.xlsx"
        return Pair(fileName, baos.toByteArray())
    }

    private fun buildSynopticNodesSheet(
        graph: SynopticGraph,
        connectivities: Map<String, BoitierConnectivity>
    ): String {
        val b = XlsxSheetBuilder()
        val headers = listOf(
            "Ligne (Row)",
            "Niveau (Col)",
            "ID Nœud",
            "Nom du Nœud",
            "Type / Badge",
            "Référence",
            "Mode de Pose",
            "Support d'Accueil",
            "Câble Amont (Entrée)",
            "Câbles Aval (Sorties)",
            "Distance Cumulée (m)",
            "Latitude",
            "Longitude",
            "Anomalie APD"
        )
        b.addRow(headers, List(headers.size) { 1 }) // Style header Navy

        // Trier par ligne puis par niveau de colonne
        val sortedNodes = graph.nodes.sortedWith(compareBy({ it.row }, { it.level }))
        for (node in sortedNodes) {
            val conn = connectivities[node.id]
            val classified = KmzNodeClassifier.classifyNode(node.rawNode)
            val refVal = classified.reference.ifBlank { "-" }
            val poseVal = node.rawNode.operator.ifBlank { classified.typeFonc.ifBlank { "-" } }
            val anoVal = conn?.anomalieApd ?: "Conforme"
            val anoStyle = if (anoVal.contains("Anomalie", ignoreCase = true)) 3 else 0

            b.addRow(
                listOf(
                    node.row,
                    node.level,
                    node.id,
                    node.name,
                    node.nodeTypeBadge,
                    refVal,
                    poseVal,
                    node.hostingSupportName,
                    node.incomingCableName,
                    node.outgoingCablesStr,
                    node.cumulativeDistanceMeters,
                    node.rawNode.latitude,
                    node.rawNode.longitude,
                    anoVal
                ),
                listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, anoStyle)
            )
        }
        return b.build()
    }

    private fun buildSynopticLinksSheet(graph: SynopticGraph): String {
        val b = XlsxSheetBuilder()
        val headers = listOf(
            "ID Tronçon",
            "Câble",
            "Capacité FO",
            "Mode de Pose",
            "Nœud Départ (Amont)",
            "Nœud Arrivée (Aval)",
            "Longueur Tronçon (m)",
            "Nombre Supports",
            "Supports Traversés (Cheminement)"
        )
        b.addRow(headers, List(headers.size) { 1 })

        val nodeMap = graph.nodes.associateBy { it.id }
        for (link in graph.links) {
            val fromName = nodeMap[link.fromNodeId]?.name ?: link.fromNodeId
            val toName = nodeMap[link.toNodeId]?.name ?: link.toNodeId

            b.addRow(
                listOf(
                    link.id,
                    link.cableName,
                    link.capacityFO,
                    link.installationType,
                    fromName,
                    toName,
                    link.segmentLengthMeters,
                    link.supportsCount,
                    link.supportsTraversesStr
                )
            )
        }
        return b.build()
    }

    private fun buildSynopticCablesSheet(
        cables: List<ClassifiedCable>,
        nodes: List<FtthNodeEntity>
    ): String {
        val b = XlsxSheetBuilder()
        val headers = listOf(
            "ID Câble",
            "Nom Câble",
            "Capacité FO",
            "Type Câble",
            "Mode de Pose",
            "Rôle Hiérarchie",
            "Câble Parent",
            "Nœud Origine",
            "Nœud Destination",
            "Longueur Totale (m)",
            "Nombre Tronçons",
            "Coordonnées Départ",
            "Coordonnées Fin"
        )
        b.addRow(headers, List(headers.size) { 1 })

        for (c in cables) {
            b.addRow(
                listOf(
                    c.id,
                    c.name,
                    c.capacityFO,
                    c.cableType,
                    c.installationType,
                    c.hierarchyRole,
                    c.parentCableName,
                    c.originNodeName,
                    c.destinationNodeName,
                    c.totalLengthMeters,
                    c.segmentCount,
                    c.startCoordinatesLabel,
                    c.endCoordinatesLabel
                )
            )
        }
        return b.build()
    }

    // =========================================================================
    // 2. EXPORT PDF DU SYNOPTIQUE (.PDF)
    // =========================================================================

    fun generateSynopticPdfPayload(
        graph: SynopticGraph,
        connectivities: Map<String, BoitierConnectivity>,
        cables: List<ClassifiedCable>
    ): Pair<String, ByteArray> {
        val document = PdfDocument()

        // Page 1 : Plan Synoptique Vectoriel Grand Format (Paysage)
        val synopticWidth = Math.max(1600f, graph.totalWidth + 300f).toInt()
        val synopticHeight = Math.max(1100f, graph.totalHeight + 400f).toInt()
        val pageInfo1 = PdfDocument.PageInfo.Builder(synopticWidth, synopticHeight, 1).create()
        val page1 = document.startPage(pageInfo1)
        renderSynopticVectorPlan(page1.canvas, graph, connectivities, cables, synopticWidth, synopticHeight)
        document.finishPage(page1)

        // Page 2 : Bordereau Récapitulatif des Tronçons & Câbles (A4 Paysage standard 842x595)
        val pageInfo2 = PdfDocument.PageInfo.Builder(1191, 842, 2).create()
        val page2 = document.startPage(pageInfo2)
        renderSummaryTablePage(page2.canvas, graph, connectivities, cables, 1191, 842)
        document.finishPage(page2)

        val baos = ByteArrayOutputStream()
        document.writeTo(baos)
        document.close()

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.FRANCE).format(Date())
        val fileName = "synoptique_ftth_$timestamp.pdf"
        return Pair(fileName, baos.toByteArray())
    }

    private fun renderSynopticVectorPlan(
        canvas: Canvas,
        graph: SynopticGraph,
        connectivities: Map<String, BoitierConnectivity>,
        cables: List<ClassifiedCable>,
        pageWidth: Int,
        pageHeight: Int
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Fond blanc technique
        p.color = Color.WHITE
        p.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), p)

        // 2. Cartouche En-tête officiel
        p.color = Color.parseColor("#1E3A8A") // TelecomNavy
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 90f, p)

        p.color = Color.WHITE
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 24f
        canvas.drawText("PLAN SYNOPTIQUE DU RÉSEAU FTTH - CHEMINEMENT CÂBLES & BOÎTIERS", 30f, 40f, p)

        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        p.textSize = 13f
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())
        val totalLen = cables.sumOf { it.totalLengthMeters }
        val subStr = "Date: $dateStr  |  Nœuds: ${graph.nodes.size}  |  Câbles: ${cables.size}  |  Tronçons: ${graph.links.size}  |  Linéaire total: ${String.format(Locale.ROOT, "%.0f m", totalLen)}"
        canvas.drawText(subStr, 30f, 68f, p)

        // 3. Légende technique en haut à droite
        val legPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; typeface = Typeface.DEFAULT_BOLD }
        var legX = pageWidth - 480f
        fun drawBadge(label: String, colorHex: String) {
            val bg = Paint().apply { color = Color.parseColor(colorHex); style = Paint.Style.FILL }
            canvas.drawRoundRect(RectF(legX, 35f, legX + 70f, 65f), 6f, 6f, bg)
            legPaint.color = Color.WHITE
            canvas.drawText(label, legX + 14f, 53f, legPaint)
            legX += 80f
        }
        drawBadge("SRO", "#9333EA")
        drawBadge("BPE", "#1E3A8A")
        drawBadge("PBO", "#0D9488")

        // 4. Décalage pour le diagramme synoptique
        val startDiagramY = 120f
        val nodeMap = graph.nodes.associateBy { it.id }

        // Largeurs et hauteurs des boîtiers
        val cardWidth = 320f
        val cardHeight = 150f

        // A. Tracer les liaisons de câbles
        val linkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            color = Color.parseColor("#1E3A8A")
        }
        val derivPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.0f
            color = Color.parseColor("#06B6D4")
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val suppPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }

        for (link in graph.links) {
            val fromNode = nodeMap[link.fromNodeId] ?: continue
            val toNode = nodeMap[link.toNodeId] ?: continue

            val fromCenterX = fromNode.x + cardWidth / 2f
            val fromCenterY = fromNode.y + startDiagramY + cardHeight / 2f
            val toCenterX = toNode.x + cardWidth / 2f
            val toCenterY = toNode.y + startDiagramY + cardHeight / 2f

            if (fromNode.row == toNode.row) {
                // Liaison horizontale (même ligne de câble)
                val lineY = fromCenterY
                val x1 = fromNode.x + cardWidth
                val x2 = toNode.x

                canvas.drawLine(x1, lineY, x2, lineY, linkPaint)

                // Texte au-dessus de la ligne : Nom Câble - Longueur
                val midX = (x1 + x2) / 2f
                val labelTop = "${link.cableName} (${link.capacityFO}) - ${String.format(Locale.ROOT, "%.0f m", link.segmentLengthMeters)}"
                val textW = textPaint.measureText(labelTop)
                canvas.drawText(labelTop, midX - textW / 2f, lineY - 8f, textPaint)

                // Texte en-dessous : Supports traversés
                if (link.supportsTraversesStr != "-") {
                    val labelBot = "Supports : ${link.supportsTraversesStr}"
                    val botW = suppPaint.measureText(labelBot)
                    canvas.drawText(labelBot, midX - botW / 2f, lineY + 16f, suppPaint)
                }
            } else {
                // Liaison verticale / dérivation en L (câble enfant partant d'un joint parent)
                val pBottomX = fromCenterX
                val pBottomY = fromNode.y + startDiagramY + cardHeight
                val cLeftX = toNode.x
                val cLeftY = toCenterY

                val path = android.graphics.Path()
                path.moveTo(pBottomX, pBottomY)
                path.lineTo(pBottomX, cLeftY)
                path.lineTo(cLeftX, cLeftY)
                canvas.drawPath(path, derivPaint)

                // Label de la dérivation
                val derivLabel = "Dérivation : ${link.cableName} (${link.capacityFO})"
                canvas.drawText(derivLabel, pBottomX + 6f, (pBottomY + cLeftY) / 2f, textPaint)
            }
        }

        // B. Dessiner les cartes boîtiers
        val cardBg = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        val cardBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
        }
        val headerBg = Paint().apply { style = Paint.Style.FILL }
        val nodeNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyNormal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#334155")
            textSize = 8.5f
        }

        for (node in graph.nodes) {
            val left = node.x
            val top = node.y + startDiagramY
            val right = left + cardWidth
            val bottom = top + cardHeight
            val rect = RectF(left, top, right, bottom)

            // Couleur par type
            val mainColorHex = when (node.nodeTypeBadge) {
                "SRO" -> "#9333EA"
                "PBO" -> "#0D9488"
                else -> "#1E3A8A"
            }
            val mainColor = Color.parseColor(mainColorHex)

            // Fond et bordure
            canvas.drawRoundRect(rect, 8f, 8f, cardBg)
            cardBorder.color = mainColor
            canvas.drawRoundRect(rect, 8f, 8f, cardBorder)

            // Bandeau supérieur (Header)
            val headerRect = RectF(left, top, right, top + 26f)
            headerBg.color = mainColor
            canvas.drawRoundRect(headerRect, 8f, 8f, headerBg)
            // Remplir le bas du bandeau pour coins droits
            canvas.drawRect(left, top + 16f, right, top + 26f, headerBg)

            // Badge & Nom du nœud
            canvas.drawText("[${node.nodeTypeBadge}] ${node.name}", left + 8f, top + 17f, nodeNamePaint)

            // Ligne 1 : Référence & Mode de pose
            var curY = top + 42f
            val classified = KmzNodeClassifier.classifyNode(node.rawNode)
            val refVal = classified.reference.ifBlank { "Réf: -" }
            val poseVal = node.rawNode.operator.ifBlank { classified.typeFonc.ifBlank { "Pose: -" } }
            canvas.drawText("$refVal - $poseVal", left + 8f, curY, bodyBold)

            // Ligne 2 : Support d'accueil
            curY += 16f
            val suppLabel = if (node.hostingSupportName != "-") "Support : ${node.hostingSupportName}" else "Support : Non rattaché"
            canvas.drawText(suppLabel, left + 8f, curY, bodyNormal)

            // Ligne 3 : Coordonnées GPS
            curY += 15f
            val coordsStr = "${node.rawNode.longitude}, ${node.rawNode.latitude}, 0"
            canvas.drawText(coordsStr, left + 8f, curY, bodyNormal)

            // Séparateur
            curY += 10f
            val sepPaint = Paint().apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 1f }
            canvas.drawLine(left + 6f, curY, right - 6f, curY, sepPaint)

            // Ligne 4 : In / Out Cables
            curY += 14f
            val inStr = "Entrée: ${node.incomingCableName}"
            val outStr = "Sorties: ${node.outgoingCablesStr}"
            canvas.drawText(inStr, left + 8f, curY, bodyNormal)
            curY += 13f
            canvas.drawText(outStr, left + 8f, curY, bodyNormal)

            // Distance cumulée en bas à droite
            val distStr = String.format(Locale.ROOT, "%.0f m", node.cumulativeDistanceMeters)
            val distW = bodyBold.measureText(distStr)
            canvas.drawText(distStr, right - distW - 10f, bottom - 8f, bodyBold)
        }
    }

    private fun renderSummaryTablePage(
        canvas: Canvas,
        graph: SynopticGraph,
        connectivities: Map<String, BoitierConnectivity>,
        cables: List<ClassifiedCable>,
        pageWidth: Int,
        pageHeight: Int
    ) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Fond blanc
        p.color = Color.WHITE
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), p)

        // En-tête de la page
        p.color = Color.parseColor("#1E3A8A")
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 60f, p)

        p.color = Color.WHITE
        p.textSize = 18f
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("BORDEREAU RÉCAPITULATIF DES TRONÇONS & CHEMINEMENTS DU SYNOPTIQUE", 30f, 38f, p)

        // Tableau des tronçons de câbles
        val startY = 90f
        var curY = startY

        val thBg = Paint().apply { color = Color.parseColor("#0F172A") }
        val thText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 10f; typeface = Typeface.DEFAULT_BOLD }
        val tbText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E293B"); textSize = 9.5f }
        val tbZebra = Paint().apply { color = Color.parseColor("#F8FAFC") }
        val borderPaint = Paint().apply { color = Color.parseColor("#E2E8F0"); strokeWidth = 1f; style = Paint.Style.STROKE }

        val cols = listOf(
            Pair("N°", 40f),
            Pair("Câble", 160f),
            Pair("Capacité", 70f),
            Pair("De (Amont)", 120f),
            Pair("Vers (Aval)", 120f),
            Pair("Mode Pose", 80f),
            Pair("Longueur", 70f),
            Pair("Supports Traversés (Cheminement)", 470f)
        )

        // Ligne d'en-tête du tableau
        val rowHeight = 24f
        canvas.drawRect(30f, curY, pageWidth - 30f, curY + rowHeight, thBg)
        var colX = 30f
        for ((title, width) in cols) {
            canvas.drawText(title, colX + 6f, curY + 16f, thText)
            colX += width
        }
        curY += rowHeight

        // Lignes de données
        val nodeMap = graph.nodes.associateBy { it.id }
        for ((idx, link) in graph.links.withIndex()) {
            if (curY > pageHeight - 40f) break // Limite de page

            if (idx % 2 == 1) {
                canvas.drawRect(30f, curY, pageWidth - 30f, curY + rowHeight, tbZebra)
            }
            canvas.drawRect(30f, curY, pageWidth - 30f, curY + rowHeight, borderPaint)

            val fromName = nodeMap[link.fromNodeId]?.name ?: link.fromNodeId
            val toName = nodeMap[link.toNodeId]?.name ?: link.toNodeId
            val lenStr = String.format(Locale.ROOT, "%.0f m", link.segmentLengthMeters)

            colX = 30f
            val vals = listOf(
                "${idx + 1}",
                link.cableName,
                link.capacityFO,
                fromName,
                toName,
                link.installationType,
                lenStr,
                link.supportsTraversesStr
            )

            for ((i, v) in vals.withIndex()) {
                val w = cols[i].second
                // Tronquer si trop long
                val maxChars = (w / 6.0f).toInt()
                val displayStr = if (v.length > maxChars) v.take(maxChars - 3) + "..." else v
                canvas.drawText(displayStr, colX + 6f, curY + 16f, tbText)
                colX += w
            }

            curY += rowHeight
        }
    }

    // =========================================================================
    // XML OPENXML UTILITIES
    // =========================================================================

    private fun buildContentTypesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
    }

    private fun buildRootRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
    }

    private fun buildWorkbookXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Arborescence Synoptique" sheetId="1" r:id="rId1"/>
    <sheet name="Tronçons &amp; Liaisons" sheetId="2" r:id="rId2"/>
    <sheet name="Hiérarchie Câbles" sheetId="3" r:id="rId3"/>
  </sheets>
</workbook>"""
    }

    private fun buildWorkbookRelsXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
    }

    private fun buildStylesXml(): String {
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="4">
    <font><name val="Calibri"/><sz val="11"/></font>
    <font><b/><color rgb="FFFFFFFF"/><name val="Calibri"/><sz val="11"/></font>
    <font><color rgb="FFDC2626"/><name val="Calibri"/><sz val="11"/></font>
    <font><b/><color rgb="FF1E3A8A"/><name val="Calibri"/><sz val="11"/></font>
  </fonts>
  <fills count="5">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1E3A8A"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFEE2E2"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFF8FAFC"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/></border>
    <border>
      <left style="thin"><color rgb="FFE2E8F0"/></left>
      <right style="thin"><color rgb="FFE2E8F0"/></right>
      <top style="thin"><color rgb="FFE2E8F0"/></top>
      <bottom style="thin"><color rgb="FFE2E8F0"/></bottom>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="5">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
    <xf numFmtId="0" fontId="3" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
    <xf numFmtId="0" fontId="0" fillId="4" borderId="1" xfId="0" applyBorder="1"/>
  </cellXfs>
</styleSheet>"""
    }

    private class XlsxSheetBuilder {
        private val sb = StringBuilder()
        private var currentRow = 0

        init {
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
            sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n")
            sb.append("<sheetData>\n")
        }

        fun addRow(values: List<Any?>, styleIds: List<Int> = emptyList()) {
            currentRow++
            sb.append("<row r=\"$currentRow\">\n")
            for ((colIdx, value) in values.withIndex()) {
                val cellRef = getCellRef(colIdx + 1, currentRow)
                val styleId = if (colIdx < styleIds.size) styleIds[colIdx] else 0
                if (value == null) {
                    // omission
                } else if (value is Number) {
                    val numStr = if (value is Double || value is Float) String.format(Locale.ROOT, "%.2f", value.toDouble()) else value.toString()
                    sb.append("<c r=\"$cellRef\" s=\"$styleId\"><v>$numStr</v></c>\n")
                } else {
                    val str = value.toString()
                    sb.append("<c r=\"$cellRef\" t=\"inlineStr\" s=\"$styleId\"><is><t>${xmlEscape(str)}</t></is></c>\n")
                }
            }
            sb.append("</row>\n")
        }

        fun build(): String {
            sb.append("</sheetData>\n")
            sb.append("</worksheet>\n")
            return sb.toString()
        }

        private fun getCellRef(colIdx: Int, rowIdx: Int): String {
            var c = colIdx
            var colStr = ""
            while (c > 0) {
                val rem = (c - 1) % 26
                colStr = ('A'.code + rem).toChar() + colStr
                c = (c - 1) / 26
            }
            return "$colStr$rowIdx"
        }

        private fun xmlEscape(str: String): String {
            return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }
    }
}
