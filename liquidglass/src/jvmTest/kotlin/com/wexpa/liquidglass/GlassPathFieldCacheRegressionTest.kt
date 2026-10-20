package com.wexpa.liquidglass

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class GlassPathFieldCacheRegressionTest {
    @Test fun disabledBackendDoesNotCreateAnOutline() = runComposeUiTest {
        val shape = object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
                error("A shader-incapable backend must not build a field")
        }
        var field: GlassPathField? = null
        setContent {
            field = rememberGlassPathField(shape, Size(1080f, 720f), 24f, 48f,
                Density(1f), LayoutDirection.Ltr, enabled = false)
        }
        waitForIdle()
        assertNull(field)
    }

    @Test fun equalPaddedDimensionsReuseDifferentGeometry() = runComposeUiTest {
        val density = Density(1f)
        var outlineCalls = 0
        val shape = object : Shape {
            override fun createOutline(
                size: Size, layoutDirection: LayoutDirection, density: Density
            ): Outline {
                outlineCalls++
                return Outline.Generic(Path().apply {
                    moveTo(0f, 0f); lineTo(size.width / 2, 12f)
                    lineTo(size.width, 0f); lineTo(size.width, size.height)
                    lineTo(0f, size.height); close()
                })
            }
        }
        var second by mutableStateOf(false)
        var tick by mutableIntStateOf(0)
        var result: GlassPathField? = null
        setContent {
            val frame = tick
            val field = rememberGlassPathField(
                shape, if (second) Size(80f, 40f) else Size(100f, 60f),
                if (second) 20f else 10f, 16f, density, LayoutDirection.Ltr, enabled = true
            )
            SideEffect { if (frame >= 0) result = field }
        }
        waitForIdle()
        val first = result
        val calls = outlineCalls
        runOnIdle { second = true }
        waitForIdle()
        // A changed inner geometry must invalidate the field.
        assertNotSame(first, result)
        assertEquals(calls + 1, outlineCalls)
        val path = (shape.createOutline(
            Size(80f, 40f), LayoutDirection.Ltr, density
        ) as Outline.Generic).path
        val fresh = buildPathField(path, 120, 80, 20f, 16f)!!.bitmap.toPixelMap()
        val stale = result!!.bitmap.toPixelMap()
        var differences = 0
        for (y in 0 until stale.height) for (x in 0 until stale.width) {
            if (stale[x, y] != fresh[x, y]) differences++
        }
        assertEquals(0, differences)
        println("different_texels=$differences")
        val correct = result
        runOnIdle { tick++ }; waitForIdle()
        assertSame(correct, result)
    }

    @Test fun subpixelGeometryReusesFieldUntilRoundedInputsChange() = runComposeUiTest {
        val density = Density(1f)
        val outlines = mutableListOf<Size>()
        val shape = object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                outlines += size
                return Outline.Generic(Path().apply {
                    moveTo(0f, 0f); lineTo(size.width / 2, 12f)
                    lineTo(size.width, 0f); lineTo(size.width, size.height)
                    lineTo(0f, size.height); close()
                })
            }
        }
        var size by mutableStateOf(Size(100.1f, 60.1f))
        var pad by mutableFloatStateOf(10.1f)
        var result: GlassPathField? = null
        setContent {
            val field = rememberGlassPathField(shape, size, pad, 16f, density,
                LayoutDirection.Ltr, enabled = true)
            SideEffect { result = field }
        }
        waitForIdle()
        val first = result
        runOnIdle { pad = 10.4f }; waitForIdle()
        assertSame(first, result, "Sub-pixel pad changes within a pixel bucket must reuse the EDT")
        runOnIdle { size = Size(100.4f, 60.4f) }; waitForIdle()
        assertSame(first, result)
        assertEquals(listOf(Size(100f, 60f)), outlines)

        fun assertMatchesRoundedBuild(expectedSize: Size, expectedPad: Float) {
            assertEquals(expectedSize, outlines.last())
            val path = (shape.createOutline(expectedSize, LayoutDirection.Ltr, density) as Outline.Generic).path
            val fresh = buildPathField(path, (expectedSize.width + 2 * expectedPad).toInt(),
                (expectedSize.height + 2 * expectedPad).toInt(), expectedPad, 16f)!!
            assertEquals(fresh.layerWidth, result!!.layerWidth)
            assertEquals(fresh.layerHeight, result!!.layerHeight)
            val actual = result!!.bitmap.toPixelMap()
            val reference = fresh.bitmap.toPixelMap()
            for (y in 0 until reference.height) for (x in 0 until reference.width) {
                assertEquals(reference[x, y], actual[x, y])
            }
        }
        runOnIdle { pad = 10.6f }; waitForIdle()
        assertNotSame(first, result)
        assertMatchesRoundedBuild(Size(100f, 60f), 11f)
        val changedPad = result
        runOnIdle { size = Size(100.6f, 60.6f) }; waitForIdle()
        assertNotSame(changedPad, result)
        assertMatchesRoundedBuild(Size(101f, 61f), 11f)
    }
}
