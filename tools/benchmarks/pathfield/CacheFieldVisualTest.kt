package com.wexpa.liquidglass

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt
import kotlin.test.*

/** Opt-in visualization of the actual field texels, not an app screenshot or timing capture. */
@OptIn(ExperimentalTestApi::class)
class CacheFieldVisualTest {
    /** Exports the fractional recording origin beside the old double-rounded field and reference. */
    @Test fun renderFractionalPaddingOrigin() = runComposeUiTest {
        val density = Density(1f)
        val size = Size(100f, 60f)
        val shape = androidx.compose.foundation.shape.GenericShape { s, _ ->
            moveTo(0f, 0f); lineTo(s.width / 2, 12f)
            lineTo(s.width, 0f); lineTo(s.width, s.height); lineTo(0f, s.height); close()
        }
        val pad = GlassStyle(blurRadius = 22.1.dp).recordPad(
            FloatArray(4), size, null, density, renderScale = 0.75f)
        var field: GlassPathField? = null
        setContent {
            field = rememberGlassPathField(shape, size, pad, 16f, density,
                LayoutDirection.Ltr, enabled = true)
        }
        waitForIdle()
        val path = (shape.createOutline(size, LayoutDirection.Ltr, density) as Outline.Generic).path
        val oldPad = pad.roundToInt().toFloat()
        val old = buildPathField(path, (size.width + 2 * oldPad).roundToInt(),
            (size.height + 2 * oldPad).roundToInt(), oldPad, 16f)!!
        val fresh = buildPathField(path, (size.width + 2 * pad).roundToInt(),
            (size.height + 2 * pad).roundToInt(), pad, 16f)!!
        val actual = field!!.bitmap.toPixelMap()
        val reference = fresh.bitmap.toPixelMap()
        assertEquals(reference.width, actual.width)
        assertEquals(reference.height, actual.height)
        for (y in 0 until reference.height) for (x in 0 until reference.width) {
            assertEquals(reference[x, y], actual[x, y])
        }
        val image = BufferedImage(990, 350, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.color = Color(244, 246, 249); g.fillRect(0, 0, image.width, image.height)
            g.color = Color(25, 35, 50); g.font = Font(Font.SANS_SERIF, Font.BOLD, 16)
            g.drawString("Fractional pad: renderScale 0.75, recording-grid origin 22.666667 px", 24, 28)
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 14)
            val fields = listOf(old, field!!, fresh)
            val titles = listOf("Previous: pad rounded to 23", "Fixed: exact recorded pad", "Fresh reference: recorded pad")
            for (i in fields.indices) {
                val x = 24 + i * 324
                val item = fields[i]
                g.drawString(titles[i], x, 60)
                val pixels = item.bitmap.toAwtImage()
                g.drawImage(pixels, x, 76, pixels.width * 4, pixels.height * 4, null)
                g.drawString("Layer ${item.layerWidth} x ${item.layerHeight}", x, 308)
            }
            g.drawString("Actual SDF texels at 4x. Fixed and fresh match exactly; this is not a device screenshot.", 24, 334)
        } finally {
            g.dispose()
        }
        val out = File("build/reports/atlas/compose-cache-field").apply { mkdirs() }
        ImageIO.write(image, "png", File(out, "fractional-pad.png"))
    }

    /** Exports real field texels showing invalidation when inner geometry changes at equal padded size. */
    @Test fun renderSizeAndPaddingCollision() = runComposeUiTest {
        val density = Density(1f)
        val shape = object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density) =
                Outline.Generic(Path().apply {
                    moveTo(0f, 0f); lineTo(size.width / 2, 12f)
                    lineTo(size.width, 0f); lineTo(size.width, size.height)
                    lineTo(0f, size.height); close()
                })
        }
        var changed by mutableStateOf(false)
        var field: GlassPathField? = null
        setContent {
            val next = rememberGlassPathField(shape,
                if (changed) Size(80f, 40f) else Size(100f, 60f),
                if (changed) 20f else 10f, 16f, density, LayoutDirection.Ltr, enabled = true)
            SideEffect { field = next }
        }
        waitForIdle()
        val stale = field!!
        runOnIdle { changed = true }
        waitForIdle()
        val updated = field!!
        assertNotSame(stale, updated)
        val path = shape.createOutline(Size(80f, 40f), LayoutDirection.Ltr, density).path
        val fresh = buildPathField(path, 120, 80, 20f, 16f)!!
        val updatedPixels = updated.bitmap.toPixelMap()
        val freshPixels = fresh.bitmap.toPixelMap()
        for (y in 0 until freshPixels.height) for (x in 0 until freshPixels.width) {
            assertEquals(freshPixels[x, y], updatedPixels[x, y])
        }
        val image = BufferedImage(960, 300, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color(244, 246, 249)
            graphics.fillRect(0, 0, image.width, image.height)
            graphics.color = Color(25, 35, 50)
            graphics.font = Font(Font.SANS_SERIF, Font.BOLD, 16)
            graphics.drawString("Distance-field cache: same padded dimensions, changed inner geometry", 24, 28)
            graphics.font = Font(Font.SANS_SERIF, Font.PLAIN, 14)
            val fields = listOf(stale, updated, fresh)
            val titles = listOf("First field (stale if reused)", "Corrected remembered field", "Fresh reference")
            for (i in fields.indices) {
                val x = 24 + i * 312
                graphics.drawString(titles[i], x, 60)
                graphics.drawImage(fields[i].bitmap.toAwtImage(), x, 76, 240, 160, null)
            }
            graphics.drawString("100 x 60, pad 10", 24, 258)
            graphics.drawString("80 x 40, pad 20", 336, 258)
            graphics.drawString("80 x 40, pad 20", 648, 258)
            graphics.drawString("Actual SDF texels, enlarged 4x. Corrected and fresh fields match pixel-for-pixel.", 24, 284)
        } finally {
            graphics.dispose()
        }
        val out = File("build/reports/atlas/compose-cache-field").apply { mkdirs() }
        ImageIO.write(image, "png", File(out, "field-cache.png"))
    }
}
