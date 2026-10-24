package com.wexpa.liquidglass

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The padded layer has one origin. The recording, the shader's uPad, the sampled distance field
 * and the draw-back translate all take their pad from [GlassStyle.recordPad], and that pad is a
 * whole number of layer pixels in the space the layer is recorded in. A field rasterised at
 * ceil(padPx) while the layer was recorded at ceil(pad * renderScale) / renderScale sat 23 versus
 * 24 full-resolution pixels apart for a 22.5 px pad at half scale.
 */
class GlassPadContractTest {
    private val density = Density(density = 3f, fontScale = 1f)
    private val size = Size(480f, 132f)
    private val radii = FloatArray(4) { 66f }

    private fun wholeLayerPixels(pad: Float, rs: Float): Boolean {
        val layer = pad * rs
        return kotlin.math.abs(layer - kotlin.math.round(layer)) < 1e-3f
    }

    @Test
    fun recordPadIsWholeLayerPixelsAtEveryRenderScale() {
        for (rs in listOf(1f, 0.75f, 0.5f, 0.25f)) {
            for (style in listOf(GlassStyle.Regular, GlassStyle.Thick, GlassStyle.Regular.copy(profile = GlassProfile.Held))) {
                val pad = style.recordPad(radii, size, fuse = null, density = density, renderScale = rs)
                assertTrue(wholeLayerPixels(pad, rs), "pad $pad at renderScale $rs is not whole layer pixels")
                assertTrue(pad >= style.padPx(density), "pad $pad is below the kernel reach ${style.padPx(density)}")
            }
        }
    }

    @Test
    fun heldProfileRecordsTheOutwardSamplingMargin() {
        val plain = GlassStyle.Regular.recordPad(radii, size, null, density, 1f)
        val held = GlassStyle.Regular.copy(profile = GlassProfile.Held).recordPad(radii, size, null, density, 1f)
        val band = GlassStyle.Regular.copy(profile = GlassProfile.Held).bandPx(radii, size, density)
        assertTrue(held >= plain, "held pad $held must not be below the plain pad $plain")
        assertTrue(held >= band * 0.3f, "held pad $held must cover 0.3 of the band $band")
    }

    @Test
    fun theWorkedExampleRoundsUpInLayerSpace() {
        // 22.5 px at half scale is 11.25 layer px: rounds to 12 layer px = 24 full-resolution px,
        // for the field and the recording alike (never 23 for one and 24 for the other).
        val rs = 0.5f
        val pad = kotlin.math.ceil(22.5f * rs) / rs
        assertEquals(24f, pad)
        assertTrue(wholeLayerPixels(pad, rs))
    }

    /**
     * #71: a held body's band narrows and widens through a press, and its pad followed, so the
     * selector's layers were reallocated mid-gesture (on a Pixel 7 the first new size compiled a
     * 265 ms GPU pipeline). [GlassPadFloor] keeps the largest pad: it never shrinks, always covers
     * what the body needs, and stays whole layer pixels at every render scale.
     */
    @Test
    fun aDeformingBodyKeepsOneLayerSize() {
        val held = GlassStyle.Regular.copy(profile = GlassProfile.Held)
        val bands = listOf(340f, 320f, 300f, 280f, 300f, 340f, 360f, 340f)
        for (rs in listOf(1f, 0.75f, 0.5f, 0.25f)) {
            val floor = GlassPadFloor()
            val needed = bands.map { held.recordPad(radii, size, null, density, rs, bandOverride = it) }
            assertTrue(needed.distinct().size > 1, "the bands do not move the pad at renderScale $rs: the test proves nothing")
            val kept = needed.map { floor.hold(it) }
            for (i in kept.indices) {
                assertTrue(kept[i] >= needed[i], "held pad ${kept[i]} is below the ${needed[i]} the body needs")
                if (i > 0) assertTrue(kept[i] >= kept[i - 1], "held pad shrank from ${kept[i - 1]} to ${kept[i]}")
                assertTrue(wholeLayerPixels(kept[i], rs), "held pad ${kept[i]} at renderScale $rs is not whole layer pixels")
            }
            assertEquals(needed.max(), kept.last())
            // Only a wider band than any before grows it: one reallocation, not one per frame.
            assertEquals(2, kept.distinct().size, "the held pad changed more than once at renderScale $rs")
        }
    }

    /**
     * #86 review: a held lens that fuses with an anchored neighbour needs the neck's margin, which
     * grows with the gap. Only the body's pad is held, so once the fuse lets go the layer returns
     * to the body's size instead of keeping the fused one for the node's lifetime.
     */
    @Test
    fun aFuseMarginIsNeverHeld() {
        val held = GlassStyle.Regular.copy(profile = GlassProfile.Held)
        // A neighbour well clear of the body (dp, relative to the element's top left).
        val fuse = GlassFuse(DpRect(260.dp, 4.dp, 300.dp, 40.dp), cornerRadius = 18.dp, anchored = true)
        for (rs in listOf(1f, 0.75f, 0.5f, 0.25f)) {
            val floor = GlassPadFloor()
            val body = held.recordPad(radii, size, null, density, rs)
            val fused = held.recordPad(radii, size, fuse, density, rs)
            assertTrue(fused > body, "the fuse does not widen the pad at renderScale $rs: the test proves nothing")
            assertEquals(body, floor.hold(body))
            assertEquals(fused, floor.hold(body, fused), "a fused frame must record the neck's margin")
            assertEquals(body, floor.hold(body), "the held pad kept the fuse's margin at renderScale $rs")
            assertTrue(wholeLayerPixels(floor.hold(body, fused), rs))
        }
    }
}
