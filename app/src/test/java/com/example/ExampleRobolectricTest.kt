package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DefaultFtthData
import com.example.data.local.FtthNodeType
import com.example.data.repository.FtthRepository
import org.junit.Assert.assertEquals
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
}

