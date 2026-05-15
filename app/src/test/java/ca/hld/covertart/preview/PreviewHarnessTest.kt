package ca.hld.covertart.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class PreviewHarnessTest {

    @Test
    fun rendersOnePreviewPerSampleCover() {
        val outputDir = File(System.getProperty("previews.outputDir") ?: "build/previews")
        val written = PreviewHarness.run(outputDir)
        assertEquals(SampleCovers.all.size, written.size)
        written.forEach { file ->
            assertTrue("preview not written: $file", file.isFile && file.length() > 0)
            val img = ImageIO.read(file)
            assertEquals("wrong width for ${file.name}", PreviewHarness.TARGET_WIDTH, img.width)
            assertEquals("wrong height for ${file.name}", PreviewHarness.TARGET_HEIGHT, img.height)
        }
    }
}
