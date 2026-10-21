package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DefaultFtthData
import com.example.data.local.FtthNodeType
import com.example.data.repository.FtthRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Releve-Terrain", appName)
  }

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
  fun `verify kmz import parses nodes, cables with FO capacity and GC tracks`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
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

    val kmzFile = java.io.File(context.cacheDir, "test_import.kmz")
    val fos = java.io.FileOutputStream(kmzFile)
    val zos = java.util.zip.ZipOutputStream(fos)
    zos.putNextEntry(java.util.zip.ZipEntry("doc.kml"))
    zos.write(sampleKml.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
    zos.closeEntry()
    zos.close()

    val fileUri = android.net.Uri.fromFile(kmzFile)
    val result = com.example.data.import.RealImportService.importFromFile(context, fileUri)

    // Vérifier les nœuds
    assertTrue(result.nodes.any { it.name == "CH_01" && it.type == FtthNodeType.CHAMBRE })
    assertTrue(result.nodes.any { it.name == "POT_02" && it.type == FtthNodeType.POTEAU })

    // Vérifier le câble 144 FO
    val cable = result.links.firstOrNull { it.capacityFO == 144 }
    assertNotNull("Le câble 144 FO doit être importé dans les liaisons", cable)
    assertEquals("Transport", cable?.cableType)

    // Vérifier les tracés
    assertTrue("Le tracé GC doit être importé", result.tracks.any { it.name == "TRANCHEE_GC" && it.type == "GC" })
  }
}

