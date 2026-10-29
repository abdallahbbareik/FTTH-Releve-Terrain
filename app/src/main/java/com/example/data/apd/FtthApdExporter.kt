package com.example.data.apd

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Service d'exportation multi-formats FTTH APD :
 * - Rapport PDF imprimable mis en page (Synoptique, BPE/PBO, Supports, Câbles, Anomalies)
 * - Fichiers CSV / Excel
 * - Schéma HTML5 / SVG interactif
 */
object FtthApdExporter {

    /**
     * Génère un rapport PDF complet
     */
    fun exportPdfReport(context: Context, result: ApdAnalysisResult, documentTitle: String): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Portrait (595x842 pt)
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.parseColor("#1E293B")
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#0284C7")
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.parseColor("#C55A11")
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }

        var y = 40f

        // En-tête du document
        canvas.drawText("RAPPORT APD FTTH - SYNOPTIQUE RÉSEAU", 40f, y, titlePaint)
        y += 20f
        canvas.drawText("Projet : ${documentTitle.ifBlank { "FTTH" }} • BPE/PBO : ${result.bpePboList.size} • Câbles : ${result.cablesList.size} • Anomalies : ${result.anomalies.size}", 40f, y, subtitlePaint)
        y += 30f

        // Tableau Synthèse BPE / PBO
        canvas.drawText("1. Liste des Boîtiers (BPE / PBO / SRO)", 40f, y, headerPaint)
        y += 18f

        val tableHeaderPaint = Paint().apply {
            color = Color.parseColor("#F1F5F9")
            style = Paint.Style.FILL
        }

        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        // En-tête du tableau
        canvas.drawRect(40f, y - 12f, 555f, y + 6f, tableHeaderPaint)
        canvas.drawText("Nom Boîtier", 45f, y, headerPaint)
        canvas.drawText("Type", 180f, y, headerPaint)
        canvas.drawText("Support", 260f, y, headerPaint)
        canvas.drawText("Câble Entré", 380f, y, headerPaint)
        canvas.drawText("Câbles Sortie", 470f, y, headerPaint)
        y += 12f
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 14f

        for (pt in result.bpePboList.take(30)) {
            if (y > 780f) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 40f
            }
            canvas.drawText(pt.name.take(20), 45f, y, textPaint)
            canvas.drawText(pt.objectType.label, 180f, y, textPaint)
            canvas.drawText(pt.supportName.take(16).ifBlank { "-" }, 260f, y, textPaint)
            canvas.drawText(pt.cableEntre.take(15).ifBlank { "-" }, 380f, y, textPaint)
            canvas.drawText(pt.cablesSortie.take(15).ifBlank { "-" }, 470f, y, textPaint)
            y += 16f
        }

        y += 20f

        // Tableau Câbles
        if (y > 720f) {
            pdfDocument.finishPage(page)
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            y = 40f
        }

        canvas.drawText("2. Synthèse des Câbles et Tronçons", 40f, y, headerPaint)
        y += 18f

        canvas.drawRect(40f, y - 12f, 555f, y + 6f, tableHeaderPaint)
        canvas.drawText("Nom Câble", 45f, y, headerPaint)
        canvas.drawText("Type", 220f, y, headerPaint)
        canvas.drawText("Longueur", 320f, y, headerPaint)
        canvas.drawText("LOG Total", 420f, y, headerPaint)
        y += 12f
        canvas.drawLine(40f, y, 555f, y, linePaint)
        y += 14f

        for (cable in result.cablesList.take(25)) {
            if (y > 780f) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 40f
            }
            canvas.drawText(cable.nom.take(25), 45f, y, textPaint)
            canvas.drawText(cable.typeStruc.ifBlank { "N/A" }, 220f, y, textPaint)
            canvas.drawText("${cable.longueurMeters.toInt()} m", 320f, y, textPaint)
            canvas.drawText("${cable.logTotalMeters.toInt()} m", 420f, y, textPaint)
            y += 16f
        }

        // Tableau Anomalies si existantes
        if (result.anomalies.isNotEmpty()) {
            if (y > 680f) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 40f
            }

            y += 20f
            val redHeaderPaint = Paint().apply {
                color = Color.RED
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }

            canvas.drawText("3. Anomalies Détectées (${result.anomalies.size})", 40f, y, redHeaderPaint)
            y += 18f

            for (anomaly in result.anomalies) {
                if (y > 780f) {
                    pdfDocument.finishPage(page)
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = 40f
                }
                canvas.drawText("• [${anomaly.type}] Cible : ${anomaly.targetName} - ${anomaly.detail}", 45f, y, textPaint)
                y += 16f
            }
        }

        pdfDocument.finishPage(page)

        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        val pdfFile = File(dir, "RAPPORT_SYNOPTIQUE_FTTH_${System.currentTimeMillis()}.pdf")

        FileOutputStream(pdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return pdfFile
    }

    /**
     * Génère une archive ZIP contenant les fichiers CSV et le rapport HTML interactif
     */
    fun exportExcelCsvZip(context: Context, result: ApdAnalysisResult, documentTitle: String): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()

        val zipFile = File(dir, "EXPORT_FTTH_APD_${System.currentTimeMillis()}.zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            // 1. BPE_PBO.csv
            zos.putNextEntry(ZipEntry("BPE_PBO.csv"))
            val bpeWriter = StringBuilder()
            bpeWriter.append("NOM;TYPE_OBJET;Support;Type;Coordinates;Cable_Entre;Cables_Sortie;Nb_Cables_Sortie\n")
            for (pt in result.bpePboList) {
                bpeWriter.append("${pt.name};${pt.objectType.label};${pt.supportName};${pt.supportType};${pt.coordinatesStr};${pt.cableEntre};${pt.cablesSortie};${pt.nbCablesSorties}\n")
            }
            zos.write(bpeWriter.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. Cable.csv
            zos.putNextEntry(ZipEntry("Cable.csv"))
            val cableWriter = StringBuilder()
            cableWriter.append("NOM;TYPE_STRUC;ORIGINE;Supports_Traverses;Nbre_CH_Traversee;Nbre_POT_Traversee;LONGUEUR;LOG_Total\n")
            for (c in result.cablesList) {
                cableWriter.append("${c.nom};${c.typeStruc};${c.originName};${c.supportsTraverses};${c.nbChTraversees};${c.nbPotTraversees};${c.longueurMeters};${c.logTotalMeters}\n")
            }
            zos.write(cableWriter.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 3. Support.csv
            zos.putNextEntry(ZipEntry("Support.csv"))
            val supWriter = StringBuilder()
            supWriter.append("NOM;TYPE_OBJET;Coordinates;TYPE_STRUC\n")
            for (s in result.supportsList) {
                supWriter.append("${s.name};${s.objectType.label};${s.coordinatesStr};${s.typeStruc}\n")
            }
            zos.write(supWriter.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 4. Anomalies.csv
            if (result.anomalies.isNotEmpty()) {
                zos.putNextEntry(ZipEntry("Anomalies.csv"))
                val anoWriter = StringBuilder()
                anoWriter.append("Type_Anomalie;Cible;Distance_m;Detail\n")
                for (a in result.anomalies) {
                    anoWriter.append("${a.type};${a.targetName};${a.distanceMeters ?: ""};${a.detail}\n")
                }
                zos.write(anoWriter.toString().toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            // 5. Synoptique_HTML_Interactif.html
            zos.putNextEntry(ZipEntry("Synoptique_Interactif.html"))
            val htmlContent = generateInteractiveHtml(result, documentTitle)
            zos.write(htmlContent.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        return zipFile
    }

    private fun generateInteractiveHtml(result: ApdAnalysisResult, documentTitle: String): String {
        val sb = StringBuilder()
        sb.append("""
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <title>Synoptique FTTH APD - $documentTitle</title>
                <style>
                    body { font-family: Arial, sans-serif; background: #f8fafc; margin: 20px; }
                    h1 { color: #1e293b; }
                    .card { background: white; padding: 15px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-bottom: 20px; }
                    table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                    th, td { border: 1px solid #cbd5e1; padding: 8px; text-align: left; font-size: 13px; }
                    th { background: #f1f5f9; color: #1e293b; }
                    .badge { padding: 4px 8px; border-radius: 4px; color: white; font-weight: bold; font-size: 11px; }
                    .badge-sro { background: #2563eb; }
                    .badge-bpe { background: #0284c7; }
                    .badge-pbo { background: #059669; }
                    .anomaly { color: #dc2626; font-weight: bold; }
                </style>
            </head>
            <body>
                <h1>Synoptique FTTH APD : $documentTitle</h1>
                <div class="card">
                    <h2>1. Boîtiers BPE / PBO (${result.bpePboList.size})</h2>
                    <table>
                        <tr><th>NOM</th><th>TYPE</th><th>Support</th><th>Câble Entré</th><th>Câbles Sortie</th></tr>
        """.trimIndent())

        for (pt in result.bpePboList) {
            val badgeClass = when (pt.objectType) {
                ApdObjectType.SRO -> "badge-sro"
                ApdObjectType.BPE -> "badge-bpe"
                else -> "badge-pbo"
            }
            sb.append("<tr><td><b>${pt.name}</b></td><td><span class=\"badge $badgeClass\">${pt.objectType.label}</span></td><td>${pt.supportName}</td><td>${pt.cableEntre}</td><td>${pt.cablesSortie}</td></tr>\n")
        }

        sb.append("""
                    </table>
                </div>
                <div class="card">
                    <h2>2. Câbles (${result.cablesList.size})</h2>
                    <table>
                        <tr><th>NOM</th><th>Type</th><th>Origine</th><th>Supports Traversés</th><th>Longueur (m)</th><th>LOG Total (m)</th></tr>
        """.trimIndent())

        for (c in result.cablesList) {
            sb.append("<tr><td><b>${c.nom}</b></td><td>${c.typeStruc}</td><td>${c.originName}</td><td>${c.supportsTraverses}</td><td>${c.longueurMeters.toInt()}m</td><td><b>${c.logTotalMeters.toInt()}m</b></td></tr>\n")
        }

        if (result.anomalies.isNotEmpty()) {
            sb.append("""
                </div>
                <div class="card">
                    <h2 class="anomaly">3. Anomalies APD (${result.anomalies.size})</h2>
                    <table>
                        <tr><th>Type</th><th>Cible</th><th>Détail</th></tr>
            """.trimIndent())

            for (a in result.anomalies) {
                sb.append("<tr><td class=\"anomaly\">${a.type}</td><td><b>${a.targetName}</b></td><td>${a.detail}</td></tr>\n")
            }
        }

        sb.append("""
                    </table>
                </div>
            </body>
            </html>
        """.trimIndent())

        return sb.toString()
    }
}
