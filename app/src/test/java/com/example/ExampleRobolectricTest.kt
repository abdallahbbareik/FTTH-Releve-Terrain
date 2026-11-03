package com.example

import com.example.data.import.RealImportService
import com.example.data.local.DefaultFtthData
import com.example.data.local.FtthNodeType
import com.example.data.repository.FtthRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

  @Test
  fun `verify default ftth node dataset contains all required architectures`() {
    val nodes = DefaultFtthData.getDefaultNodes()
    val types = nodes.map { it.type }.toSet()

    assertTrue(types.contains(FtthNodeType.POTEAU))
    assertTrue(types.contains(FtthNodeType.CHAMBRE))
    assertTrue(types.contains(FtthNodeType.BOITIER))
    assertTrue(types.contains(FtthNodeType.SRO))
    assertTrue(types.contains(FtthNodeType.IMMEUBLE))
    assertTrue(types.contains(FtthNodeType.VILLA))
  }

  @Test
  fun `verify haversine distance calculation`() {
    val dist = FtthRepository.calculateDistanceMeters(
      48.8566, 2.3522,
      48.8572, 2.3514
    )
    assertTrue("Distance should be around 80-90 meters", dist in 80.0..100.0)
  }

  @Test
  fun `verify kmz native import parses points and line tracks without auto-classification`() {
    val sampleKml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <Folder>
              <name>Reseau FTTH</name>
              <Placemark>
                <name>Point_A</name>
                <description>Description Point A</description>
                <Point><coordinates>9.5375,34.0001,0</coordinates></Point>
              </Placemark>
              <Placemark>
                <name>Ligne_Principale</name>
                <description>Tracé de test</description>
                <LineString>
                  <coordinates>
                    9.5375,34.0001,0
                    9.5380,34.0005,0
                    9.5385,34.0009,0
                  </coordinates>
                </LineString>
              </Placemark>
            </Folder>
          </Document>
        </kml>
    """.trimIndent()

    val result = RealImportService.parseKmlString(kml = sampleKml, existingNodes = emptyList(), mode = RealImportService.ImportMode.NATIVE)

    assertTrue("Le point brut doit être importé", result.nodes.any { it.name == "Point_A" })
    assertEquals("Aucun câble ne doit être créé en mode natif", 0, result.links.size)
    assertTrue("La polyligne doit être importée en tracé natif", result.tracks.any { it.name == "Ligne_Principale" && it.type == "Tracé" })
  }

  @Test
  fun `verify preserved intelligent classification mode classifies cables with FO and GC`() {
    val sampleKml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2">
          <Document>
            <Folder>
              <name>Reseau FTTH</name>
              <Placemark>
                <name>CH_01</name>
                <description>Chambre de tirage L2T</description>
                <Point><coordinates>9.5375,34.0001,0</coordinates></Point>
              </Placemark>
              <Placemark>
                <name>POT_02</name>
                <description>Poteau bois</description>
                <Point><coordinates>9.5385,34.0009,0</coordinates></Point>
              </Placemark>
              <Placemark>
                <name>CABLE_TRANSPORT_144FO</name>
                <description>Câble 144 fibres</description>
                <LineString>
                  <coordinates>
                    9.5375,34.0001,0
                    9.5380,34.0005,0
                    9.5385,34.0009,0
                  </coordinates>
                </LineString>
              </Placemark>
              <Placemark>
                <name>TRANCHEE_GC</name>
                <description>Fourreau PEHD</description>
                <LineString>
                  <coordinates>
                    9.5370,34.0000,0
                    9.5375,34.0001,0
                  </coordinates>
                </LineString>
              </Placemark>
            </Folder>
          </Document>
        </kml>
    """.trimIndent()

    // Mode classification explicite
    val result = RealImportService.parseKmlString(kml = sampleKml, existingNodes = emptyList(), mode = RealImportService.ImportMode.INTELLIGENT_CLASSIFICATION)

    // Vérifier les nœuds classifiés
    assertTrue(result.nodes.any { it.name == "CH_01" && it.type == FtthNodeType.CHAMBRE })
    assertTrue(result.nodes.any { it.name == "POT_02" && it.type == FtthNodeType.POTEAU })

    // Vérifier le câble 144 FO classifié
    val cable = result.links.firstOrNull { it.capacityFO == 144 }
    assertNotNull("Le câble 144 FO doit être classifié", cable)
    assertEquals("Transport", cable?.cableType)

    // Vérifier le tracé GC classifié
    assertTrue("Le tracé GC doit être classifié", result.tracks.any { it.name == "TRANCHEE_GC" && it.type == "GC" })
  }
}
