package com.example

import com.example.data.apd.ApdObjectType
import com.example.data.apd.ApdPoint
import com.example.data.apd.ApdRawPlacemark
import com.example.data.apd.FtthApdParser
import com.example.data.apd.FtthApdTopologyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FtthApdTopologyEngineTest {

    @Test
    fun testHaversineDistance() {
        // Distance entre 2 points connus à Tunis (~557m)
        val lat1 = 36.8065
        val lon1 = 10.1815
        val lat2 = 36.8115
        val lon2 = 10.1815
        val dist = FtthApdTopologyEngine.haversineM(lon1, lat1, lon2, lat2)
        assertTrue(dist > 500.0 && dist < 600.0)
    }

    @Test
    fun testSpatialIndexQuery() {
        val lons = doubleArrayOf(10.1815, 10.1820, 10.2500)
        val lats = doubleArrayOf(36.8065, 36.8066, 36.9000)
        val index = FtthApdTopologyEngine.SpatialIndex(lons, lats)

        // Chercher dans un rayon de 50m autour du premier point
        val matches = index.queryBallPoint(10.1815, 36.8065, 50.0)
        assertTrue(matches.contains(0))
        assertTrue(!matches.contains(2))
    }

    @Test
    fun testDeduplicationAndCableMatching() {
        val rawPoints = listOf(
            ApdRawPlacemark("SRO-01", "SRO", "Point", "10.1815,36.8065,0", mapOf("TYPE_FONC" to "SRO")),
            ApdRawPlacemark("JOINT-01", "BPE", "Point", "10.1820,36.8070,0", mapOf("TYPE_FONC" to "JOINT")),
            ApdRawPlacemark("SPLIT-01", "PBO", "Point", "10.1830,36.8080,0", mapOf("TYPE_FONC" to "SPLIT"))
        )

        val rawLines = listOf(
            ApdRawPlacemark("CDI_SRO-01_BPE-01_24FO", "CABLE", "LineString", "10.1815,36.8065,0 10.1820,36.8070,0", mapOf("NOM" to "CDI_SRO-01_BPE-01_24FO")),
            ApdRawPlacemark("CDI_BPE-01_PBO-01_8FO", "CABLE", "LineString", "10.1820,36.8070,0 10.1830,36.8080,0", mapOf("NOM" to "CDI_BPE-01_PBO-01_8FO"))
        )

        val result = FtthApdTopologyEngine.processApdKmz(rawPoints, rawLines, toleranceM = 2.0)

        assertNotNull(result)
        assertEquals(3, result.allPoints.size)
        assertEquals(2, result.cablesList.size)
        assertTrue(result.roots.contains("CDI_SRO-01_BPE-01_24FO"))
    }

    @Test
    fun testAdductionCablesFiltered() {
        val rawPoints = listOf(
            ApdRawPlacemark("SRO-01", "SRO", "Point", "10.1815,36.8065,0", mapOf("TYPE_FONC" to "SRO")),
            ApdRawPlacemark("JOINT-01", "BPE", "Point", "10.1820,36.8070,0", mapOf("TYPE_FONC" to "JOINT"))
        )

        val rawLines = listOf(
            ApdRawPlacemark("CDI_SRO-01_BPE-01_24FO", "CABLE", "LineString", "10.1815,36.8065,0 10.1820,36.8070,0", mapOf("NOM" to "CDI_SRO-01_BPE-01_24FO")),
            ApdRawPlacemark("CABLE_ADD_01", "adduction", "LineString", "10.1820,36.8070,0 10.1825,36.8075,0", mapOf("NOM" to "CABLE_ADD_01"))
        )

        val result = FtthApdTopologyEngine.processApdKmz(rawPoints, rawLines, toleranceM = 2.0)

        assertNotNull(result)
        assertEquals(1, result.cablesList.size)
        assertEquals("CDI_SRO-01_BPE-01_24FO", result.cablesList[0].nom)
    }
}
