package com.example

import com.example.data.apd.ApdObjectType
import com.example.data.apd.ApdRawPlacemark
import com.example.data.apd.FtthApdParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FtthApdParserTest {

    @Test
    fun testParseDescriptionTable() {
        val html = """
            <table border="1">
                <tr><td>TYPE_FONC</td><td>APPUI</td></tr>
                <tr><td>NOM</td><td>POT-102</td></tr>
                <tr><td>TYPE_STRUC</td><td>AERIEN</td></tr>
            </table>
        """.trimIndent()

        val map = FtthApdParser.parseDescriptionTable(html)
        assertEquals(3, map.size)
        assertEquals("APPUI", map["TYPE_FONC"])
        assertEquals("POT-102", map["NOM"])
        assertEquals("AERIEN", map["TYPE_STRUC"])
    }

    @Test
    fun testClassifyPointType() {
        val p1 = ApdRawPlacemark(
            name = "SRO-101",
            folder = "SRO",
            geometryType = "Point",
            coordinatesStr = "10.1815,36.8065,0",
            attributes = mapOf("TYPE_FONC" to "SRO")
        )
        assertEquals(ApdObjectType.SRO, FtthApdParser.classifyPointType(p1))

        val p2 = ApdRawPlacemark(
            name = "JOINT",
            folder = "BPE",
            geometryType = "Point",
            coordinatesStr = "10.1820,36.8070,0",
            attributes = mapOf("TYPE_FONC" to "JOINT")
        )
        assertEquals(ApdObjectType.BPE, FtthApdParser.classifyPointType(p2))

        val p3 = ApdRawPlacemark(
            name = "SPLIT 1/8",
            folder = "PBO",
            geometryType = "Point",
            coordinatesStr = "10.1825,36.8075,0",
            attributes = mapOf("TYPE_FONC" to "PBO")
        )
        assertEquals(ApdObjectType.PBO, FtthApdParser.classifyPointType(p3))
    }

    @Test
    fun testParseLonLat() {
        val coords = "10.1815,36.8065,0.0"
        val pt = FtthApdParser.parseLonLat(coords)
        assertNotNull(pt)
        assertEquals(10.1815, pt!!.first, 0.0001)
        assertEquals(36.8065, pt.second, 0.0001)
    }
}
