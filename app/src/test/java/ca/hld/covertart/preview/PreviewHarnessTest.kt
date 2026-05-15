package ca.hld.covertart.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PreviewHarnessTest {

    @Test
    fun rendersOnePreviewPerSampleCover() {
        val outputDir = File(System.getProperty("previews.outputDir") ?: "build/previews")
        val written = PreviewHarness.run(outputDir)
        assertEquals(SampleCovers.all.size, written.size)
        written.forEach { assertTrue("preview not written: $it", it.isFile && it.length() > 0) }
    }
}
