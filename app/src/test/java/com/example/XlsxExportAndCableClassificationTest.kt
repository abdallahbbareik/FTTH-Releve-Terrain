package com.example

import com.example.data.local.FtthNodeEntity
import com.example.data.local.FtthNodeType
import com.example.data.storage.StoredTrack
import com.example.data.storage.TrackPoint
import com.example.data.util.BoitierCableConnectivityHelper
import com.example.data.util.ExcelExporterHelper
import com.example.data.util.KmzCableClassifier
import com.example.data.util.KmzNodeClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class XlsxExportAndCableClassificationTest {

    @Test
    fun testXlsxArchiveStructureAnd8Sheets() {
        // Crée des nœuds de test pour chaque catégorie
        val sro = FtthNodeEntity(
            id = "node_sro",
            name = "SRO_CENTRAL",
            type = FtthNodeType.SRO,
            latitude = 36.8000,
            longitude = 10.1800
        )
        val bpe = FtthNodeEntity(
            id = "node_bpe",
            name = "BPE_01",
            type = FtthNodeType.BOITIER,
            latitude = 36.8005,
            longitude = 10.1805
        )

        // Câble reliant SRO à BPE
        val track1 = StoredTrack(
            id = "trk_cbl_1",
            name = "CABLE_DISTRIB_72FO",
            type = "Câble",
            points = listOf(
                TrackPoint(latitude = 36.8000, longitude = 10.1800),
                TrackPoint(latitude = 36.8005, longitude = 10.1805)
            ),
            totalDistanceMeters = 70.0
        )

        // Câble dérivé partant de BPE vers une chambre
        val track2 = StoredTrack(
            id = "trk_cbl_2",
            name = "CABLE_DERIV_12FO",
            type = "Câble",
            points = listOf(
                TrackPoint(latitude = 36.8005, longitude = 10.1805),
                TrackPoint(latitude = 36.8010, longitude = 10.1810)
            ),
            totalDistanceMeters = 75.0
        )

        val allNodes = listOf(sro, bpe)
        val classifiedNodes = allNodes.map { KmzNodeClassifier.classifyNode(it) }
        val classifiedCables = KmzCableClassifier.classifyAndGroupCables(listOf(track1, track2), allNodes)
        val connectivityMap = BoitierCableConnectivityHelper.computeConnectivities(allNodes, classifiedCables)

        // Génération du payload Excel .xlsx
        val (fileName, xlsxBytes) = ExcelExporterHelper.generateExcelPayload(
            classifiedNodes = classifiedNodes,
            connectivityMap = connectivityMap,
            classifiedCables = classifiedCables,
            nodes = allNodes
        )

        assertTrue("Le nom du fichier doit se terminer par .xlsx", fileName.endsWith(".xlsx"))
        assertTrue("Les octets du fichier ne doivent pas être vides", xlsxBytes.isNotEmpty())

        // Vérification que le fichier est une archive ZIP OpenXML valide
        val zipEntries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                zipEntries.add(entry.name)
                entry = zis.nextEntry
            }
        }

        assertTrue("Doit contenir [Content_Types].xml", zipEntries.contains("[Content_Types].xml"))
        assertTrue("Doit contenir _rels/.rels", zipEntries.contains("_rels/.rels"))
        assertTrue("Doit contenir xl/workbook.xml", zipEntries.contains("xl/workbook.xml"))
        assertTrue("Doit contenir xl/_rels/workbook.xml.rels", zipEntries.contains("xl/_rels/workbook.xml.rels"))
        assertTrue("Doit contenir xl/styles.xml", zipEntries.contains("xl/styles.xml"))

        // Vérifier les 8 feuilles
        for (i in 1..8) {
            assertTrue("Doit contenir sheet$i.xml", zipEntries.contains("xl/worksheets/sheet$i.xml"))
        }
    }

    @Test
    fun testConnectedCablesAreNotClassifiedAsIsolated() {
        val sro = FtthNodeEntity(
            id = "sro_1",
            name = "SRO_TEST",
            type = FtthNodeType.SRO,
            latitude = 36.8000,
            longitude = 10.1800
        )

        val bpe = FtthNodeEntity(
            id = "bpe_1",
            name = "BPE_TEST",
            type = FtthNodeType.BOITIER,
            latitude = 36.8005,
            longitude = 10.1805
        )

        val cablePrincipal = StoredTrack(
            id = "trk_1",
            name = "CBL_DIST_48FO",
            type = "Câble",
            points = listOf(
                TrackPoint(36.8000, 10.1800),
                TrackPoint(36.8005, 10.1805)
            )
        )

        val cableEnfant = StoredTrack(
            id = "trk_2",
            name = "CBL_DIST_12FO",
            type = "Câble",
            points = listOf(
                TrackPoint(36.8005, 10.1805),
                TrackPoint(36.8010, 10.1810)
            )
        )

        val cables = KmzCableClassifier.classifyAndGroupCables(listOf(cablePrincipal, cableEnfant), listOf(sro, bpe))

        val c1 = cables.firstOrNull { it.name.contains("48FO") }
        val c2 = cables.firstOrNull { it.name.contains("12FO") }

        assertNotNull(c1)
        assertNotNull(c2)

        // Aucun des deux câbles ne doit être isolé !
        assertFalse("Le câble principal ne doit pas être isolé", c1!!.hierarchyRole.contains("Isolé"))
        assertFalse("Le câble enfant ne doit pas être isolé", c2!!.hierarchyRole.contains("Isolé"))
        assertTrue("Le câble enfant doit être identifié comme child", c2.isChild)
        assertEquals("Parent (Principal)", c1.hierarchyRole)
    }

    @Test
    fun testCableCdi010724FoParcoursAndNoDistantPole() {
        val chambre006 = FtthNodeEntity(
            id = "ch_006",
            name = "L2T-P1-006",
            type = FtthNodeType.CHAMBRE,
            latitude = 36.835963,
            longitude = 10.125702
        )
        val boitier006 = FtthNodeEntity(
            id = "bpe_006",
            name = "JOINT-SPLIT 1/8",
            type = FtthNodeType.BOITIER,
            latitude = 36.835963,
            longitude = 10.125702
        )

        val chambre007 = FtthNodeEntity(
            id = "ch_007",
            name = "L2T-P1-007",
            type = FtthNodeType.CHAMBRE,
            latitude = 36.835917,
            longitude = 10.124745
        )
        val boitier007 = FtthNodeEntity(
            id = "bpe_007",
            name = "JOINT-SPLIT 1/8",
            type = FtthNodeType.BOITIER,
            latitude = 36.835917,
            longitude = 10.124745
        )

        val chambre008 = FtthNodeEntity(
            id = "ch_008",
            name = "L2T-P1-008",
            type = FtthNodeType.CHAMBRE,
            latitude = 36.836655,
            longitude = 10.124160
        )
        val boitier008 = FtthNodeEntity(
            id = "bpe_008",
            name = "JOINT",
            type = FtthNodeType.BOITIER,
            latitude = 36.836655,
            longitude = 10.124160
        )

        // Poteau situé à 50m mais qui était capturé lors d'un saut de coordonnées
        val poteau011 = FtthNodeEntity(
            id = "pot_011",
            name = "POT-P1-008-11",
            type = FtthNodeType.POTEAU,
            latitude = 36.836400,
            longitude = 10.124707
        )

        val sro = FtthNodeEntity(
            id = "sro_tun",
            name = "SRO-TUN_F603",
            type = FtthNodeType.SRO,
            latitude = 36.8374,
            longitude = 10.1274
        )

        // Câble parent 288FO arrivant à L2T-P1-006
        val cableParent288 = StoredTrack(
            id = "trk_parent_288",
            name = "CDI_01-288FO",
            type = "Câble",
            points = listOf(
                TrackPoint(36.8374, 10.1274),
                TrackPoint(36.835963, 10.125702) // Touche L2T-P1-006
            )
        )

        // Les deux tronçons de CDI_01_07-24FO enregistrés dans l'ordre inverse dans le KMZ :
        // Tronçon 1 (007 -> 008)
        val troncon1 = StoredTrack(
            id = "trk_24_seg1",
            name = "CDI_01_07-24FO",
            type = "Câble",
            points = listOf(
                TrackPoint(36.835917, 10.124745), // 007
                TrackPoint(36.836655, 10.124160)  // 008
            )
        )
        // Tronçon 2 (006 -> 007)
        val troncon2 = StoredTrack(
            id = "trk_24_seg2",
            name = "CDI_01_07-24FO",
            type = "Câble",
            points = listOf(
                TrackPoint(36.835963, 10.125702), // 006
                TrackPoint(36.835917, 10.124745)  // 007
            )
        )

        val allNodes = listOf(sro, chambre006, boitier006, chambre007, boitier007, chambre008, boitier008, poteau011)
        val classifiedCables = KmzCableClassifier.classifyAndGroupCables(
            listOf(cableParent288, troncon1, troncon2),
            allNodes
        )

        val cable24 = classifiedCables.firstOrNull { it.name.contains("CDI_01_07-24FO") }
        assertNotNull("Le câble CDI_01_07-24FO doit être regroupé", cable24)

        // Doit être orienté de L2T-P1-006 vers L2T-P1-008
        assertTrue("CDI_01_07-24FO doit être un enfant dérivé du parent 288FO", cable24!!.isChild)
        assertEquals("CDI_01-288FO", cable24.parentCableName)
        assertEquals("Enfant (Dérivé N1)", cable24.hierarchyRole)

        // Analyse des supports traversés le long du câble 24FO
        val supportAnalysis = com.example.data.util.CableSupportAnalyzer.analyzeClassifiedCableSupports(cable24, allNodes)
        val supports = supportAnalysis.supports

        assertEquals("Le câble 24FO doit traverser exactement 3 chambres", 3, supports.size)
        assertEquals("L2T-P1-006", supports[0].node.name)
        assertEquals("L2T-P1-007", supports[1].node.name)
        assertEquals("L2T-P1-008", supports[2].node.name)

        // Le poteau POT-P1-008-11 ne doit JAMAIS apparaître !
        assertFalse(
            "Le poteau POT-P1-008-11 ne doit pas être inclus dans le parcours",
            supports.any { it.node.name == "POT-P1-008-11" }
        )
    }

    @Test
    fun testBoitierHostingSupportAssociationAndExcelExport() {
        // Chambre à des coordonnées données
        val chambre006 = FtthNodeEntity(
            id = "ch_006",
            name = "L2T-P1-006",
            type = FtthNodeType.CHAMBRE,
            latitude = 36.835963,
            longitude = 10.125702
        )

        // Boîtier installé dans cette chambre (mêmes coordonnées)
        val boitier006 = FtthNodeEntity(
            id = "bpe_006",
            name = "JOINT-SPLIT 1/8",
            type = FtthNodeType.BOITIER,
            latitude = 36.835963,
            longitude = 10.125702
        )

        // Poteau aérien
        val poteau01 = FtthNodeEntity(
            id = "pot_01",
            name = "POT-P1-007-01",
            type = FtthNodeType.POTEAU,
            latitude = 36.836000,
            longitude = 10.126000
        )

        // Boîtier installé sur ce poteau (décalage de 0.5m, donc <= 1.0m)
        val boitierAerien = FtthNodeEntity(
            id = "bpe_aerien",
            name = "PBO-AER-01",
            type = FtthNodeType.BOITIER,
            latitude = 36.836003, // ~0.33m d'écart
            longitude = 10.126000
        )

        // Boîtier isolé sans chambre ni poteau à proximité (> 1m)
        val boitierIsole = FtthNodeEntity(
            id = "bpe_isole",
            name = "BPE-ISOLE",
            type = FtthNodeType.BOITIER,
            latitude = 36.840000,
            longitude = 10.130000
        )

        val track = StoredTrack(
            id = "trk_test",
            name = "CABLE_TEST",
            type = "Câble",
            points = listOf(
                TrackPoint(36.835963, 10.125702),
                TrackPoint(36.836000, 10.126000)
            )
        )

        val allNodes = listOf(chambre006, boitier006, poteau01, boitierAerien, boitierIsole)
        val classifiedCables = KmzCableClassifier.classifyAndGroupCables(listOf(track), allNodes)
        val connectivityMap = BoitierCableConnectivityHelper.computeConnectivities(allNodes, classifiedCables)

        // 1. Vérification de la connectivité et détection du support physique (<= 1.0m)
        val conn006 = connectivityMap["bpe_006"]
        assertNotNull("La connectivité pour bpe_006 doit exister", conn006)
        assertEquals("Le boîtier 006 doit être associé à la Chambre L2T-P1-006", "Chambre [L2T-P1-006]", conn006!!.hostingSupportName)

        val connAerien = connectivityMap["bpe_aerien"]
        assertNotNull("La connectivité pour bpe_aerien doit exister", connAerien)
        assertEquals("Le boîtier aérien doit être associé au Poteau POT-P1-007-01", "Poteau [POT-P1-007-01]", connAerien!!.hostingSupportName)

        val connIsole = connectivityMap["bpe_isole"]
        assertNotNull("La connectivité pour bpe_isole doit exister", connIsole)
        assertEquals("Le boîtier isolé ne doit avoir aucun support associé", "-", connIsole!!.hostingSupportName)

        // 2. Vérification de l'export Excel (Feuille 2 Boîtiers contient la colonne Support)
        val classifiedNodes = allNodes.map { KmzNodeClassifier.classifyNode(it) }
        val (_, xlsxBytes) = ExcelExporterHelper.generateExcelPayload(
            classifiedNodes = classifiedNodes,
            connectivityMap = connectivityMap,
            classifiedCables = classifiedCables,
            nodes = allNodes
        )

        var sheet2XmlContent = ""
        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "xl/worksheets/sheet2.xml") {
                    sheet2XmlContent = zis.readBytes().toString(Charsets.UTF_8)
                    break
                }
                entry = zis.nextEntry
            }
        }

        assertTrue("La feuille 2 (Boîtiers) doit contenir l'en-tête de colonne 'Support'", sheet2XmlContent.contains("Support"))
        assertTrue("La feuille 2 doit mentionner 'Chambre [L2T-P1-006]'", sheet2XmlContent.contains("Chambre [L2T-P1-006]"))
        assertTrue("La feuille 2 doit mentionner 'Poteau [POT-P1-007-01]'", sheet2XmlContent.contains("Poteau [POT-P1-007-01]"))
    }

    @Test
    fun testSynopticGraphHierarchyAndSupportLabels() {
        val sro = FtthNodeEntity(id = "sro_1", name = "SRO-CENTER", type = FtthNodeType.SRO, latitude = 36.8000, longitude = 10.1800)
        val bpe = FtthNodeEntity(id = "bpe_1", name = "JOINT-SPLIT 1/8", type = FtthNodeType.BOITIER, latitude = 36.8005, longitude = 10.1805)
        val pbo = FtthNodeEntity(id = "pbo_1", name = "PBO-AER-01", type = FtthNodeType.BOITIER, latitude = 36.8010, longitude = 10.1810)
        val chambre = FtthNodeEntity(id = "ch_1", name = "L2T-P1-001", type = FtthNodeType.CHAMBRE, latitude = 36.8002, longitude = 10.1802)

        val track1 = StoredTrack(
            id = "t1", name = "CDI_288FO", type = "Câble",
            points = listOf(TrackPoint(36.8000, 10.1800), TrackPoint(36.8002, 10.1802), TrackPoint(36.8005, 10.1805))
        )
        val track2 = StoredTrack(
            id = "t2", name = "CDI_12FO", type = "Câble",
            points = listOf(TrackPoint(36.8005, 10.1805), TrackPoint(36.8010, 10.1810))
        )

        val allNodes = listOf(sro, bpe, pbo, chambre)
        val cables = KmzCableClassifier.classifyAndGroupCables(listOf(track1, track2), allNodes)
        val connectivities = BoitierCableConnectivityHelper.computeConnectivities(allNodes, cables)

        val graph = com.example.data.util.FtthSynopticGraphBuilder.buildGraph(allNodes, cables, connectivities)

        val sroNode = graph.nodes.find { it.id == "sro_1" }
        assertNotNull(sroNode)
        assertEquals("SRO", sroNode!!.nodeTypeBadge)

        val bpeNode = graph.nodes.find { it.id == "bpe_1" }
        assertNotNull(bpeNode)
        assertEquals("BPE", bpeNode!!.nodeTypeBadge)

        val pboNode = graph.nodes.find { it.id == "pbo_1" }
        assertNotNull(pboNode)
        assertEquals("PBO", pboNode!!.nodeTypeBadge)

        val link1 = graph.links.find { it.fromNodeId == "sro_1" && it.toNodeId == "bpe_1" }
        assertNotNull("Lien SRO -> BPE doit exister", link1)
        assertTrue("Le lien doit contenir la chambre L2T-P1-001", link1!!.supportsTraversesStr.contains("L2T-P1-001"))
    }
}
