package io.hkmario.monologue

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.hkmario.monologue.domain.IntroTimeline
import io.hkmario.monologue.ui.IntroTexture
import io.hkmario.monologue.ui.renderIntro
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Renders intro frames on a phone-sized canvas and saves them to Pictures/monologue-qa for checking by eye. */
@RunWith(AndroidJUnit4::class)
class IntroFramesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val paper = Color(0xFFF5EFE4)

    private fun frame(t: Float): Bitmap {
        val width = 1080; val height = 2340
        val image = ImageBitmap(width, height)
        val density = Density(2.75f)
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(image), Size(width.toFloat(), height.toFloat())) {
            drawRect(paper) // the app behind the intro
            renderIntro(t, IntroTexture(), measurer)
        }
        return image.asAndroidBitmap()
    }

    private fun save(name: String, bitmap: Bitmap) {
        val media = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.delete(media, "${android.provider.MediaStore.Images.Media.RELATIVE_PATH}=? AND ${android.provider.MediaStore.Images.Media.DISPLAY_NAME} LIKE ?", arrayOf("Pictures/monologue-qa/", "$name%"))
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/monologue-qa")
        }
        val uri = context.contentResolver.insert(media, values)!!
        context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun framesAtEachQuarterAndKeyMoments() {
        val record = 0xFF171717.toInt(); val label = 0xFFA74932.toInt()
        val quarters = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        quarters.forEach { t -> save("intro-%03d".format((t * 100).toInt()), frame(t)) }
        // Extra looks at the landing and the stamp, the moments most likely to look off.
        save("intro-land", frame(IntroTimeline.LAND / IntroTimeline.DURATION))
        save("intro-stamp", frame((IntroTimeline.STAMP + 0.02f) / IntroTimeline.DURATION))
        save("intro-hold", frame((IntroTimeline.LIFT_END + 0.1f) / IntroTimeline.DURATION))

        // 0 %: paper only. 50 %: record with the label on it. 100 %: faded out to the app behind.
        val start = frame(0f); val mid = frame(0.5f); val end = frame(1f)
        assertNotEquals(record, start.getPixel(540, 1170))
        val cx = 540; val cy = 1170
        assertTrue("the label is terracotta at mid-point", (-40..40).any { dx -> mid.getPixel(cx + dx * 3, cy) == label })
        assertEquals(paper.toArgb(), end.getPixel(cx, cy))
    }
}
