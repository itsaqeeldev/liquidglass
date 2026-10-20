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
                if (second) 20f else 10f, 16f, density, LayoutDirection.Ltr
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
}
