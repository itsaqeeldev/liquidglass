package com.wexpa.liquidglass

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toPixelMap
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

class GlassPathFieldBufferTest {
    private fun path(w: Int, h: Int, notch: Int) = Path().apply {
        moveTo(0f, 0f); lineTo(w / 2f, notch.toFloat()); lineTo(w.toFloat(), 0f)
        lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close()
    }

    private fun pixels(bitmap: ImageBitmap): List<Int> {
        val map = bitmap.toPixelMap()
        return List(map.width * map.height) { i ->
            (map[i % map.width, i / map.width].red * 255f).toInt()
        }
    }

    @Test fun reusedBuffersMatchFreshBuildsAcrossShapesSizesAndOddPadding() {
        val buffers = GlassPathFieldBuffers()
        var previous: GlassPathField? = null
        var previousPixels: List<Int>? = null
        for ((w, h, pad) in listOf(Triple(200, 150, 23), Triple(40, 30, 25),
            Triple(150, 200, 24), Triple(41, 31, 23), Triple(200, 150, 23))) {
            for (notch in listOf(0, 12, h - 2)) {
                val p = path(w, h, notch)
                val fresh = buildPathField(p, w + 2 * pad, h + 2 * pad,
                    pad.toFloat(), 48f, GlassPathFieldBuffers())!!
                val reused = buildPathField(p, w + 2 * pad, h + 2 * pad,
                    pad.toFloat(), 48f, buffers)!!
                assertEquals(pixels(fresh.bitmap), pixels(reused.bitmap), "size=$w,$h pad=$pad notch=$notch")
                // A later build must never mutate a field already published to a panel.
                previous?.let { assertEquals(previousPixels, pixels(it.bitmap)) }
                previous = reused
                previousPixels = pixels(reused.bitmap)
            }
        }
    }

    @Test fun smallerBuildsReuseTransformArrays() {
        val buffers = GlassPathFieldBuffers()
        buffers.ensureCapacity(1000)
        val inside = buffers.inside
        val dx = buffers.dx
        val dy = buffers.dy
        val distance = buffers.distance
        val outer = buffers.outer
        buffers.ensureCapacity(100)
        assertSame(inside, buffers.inside)
        assertSame(dx, buffers.dx)
        assertSame(dy, buffers.dy)
        assertSame(distance, buffers.distance)
        assertSame(outer, buffers.outer)
    }

    @Test fun concurrentBuildsMatchIndependentWorkspaces() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val tasks = (0 until 12).map { i -> Callable {
                val w = 160 + i
                val h = 90 + i
                val p = path(w, h, i * 2)
                val independent = buildPathField(p, w + 46, h + 46, 23f, 48f, GlassPathFieldBuffers())!!
                val pooled = buildPathField(p, w + 46, h + 46, 23f, 48f)!!
                assertEquals(pixels(independent.bitmap), pixels(pooled.bitmap))
            } }
            executor.invokeAll(tasks).forEach { it.get() }
        } finally {
            executor.shutdownNow()
        }
    }
}
