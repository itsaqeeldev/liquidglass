package com.wexpa.liquidglass.material3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The slider tracks span the thumb's whole travel, as iOS's do (#79): Material lays the track out
 * between the thumb's centre positions, so a track drawn only in its own bounds stopped half a thumb
 * (19 dp) short of each end and the thumb hung past it. Pure red/blue on black, scanned along the
 * track's centre row.
 */
@OptIn(ExperimentalTestApi::class)
class GlassSliderTrackTest {
    private val colors @Composable get() = SliderDefaults.colors(activeTrackColor = Color.Red, inactiveTrackColor = Color.Blue)

    private fun track(content: @Composable () -> Unit): PixelMap {
        lateinit var pixels: PixelMap
        runDesktopComposeUiTest(400, 120, testTimeout = 2.minutes) {
            setContent {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    MaterialTheme(colorScheme = lightColorScheme()) {
                        Box(Modifier.background(Color.Black).padding(40.dp)) {
                            Box(Modifier.width(300.dp).testTag("slider")) { content() }
                        }
                    }
                }
            }
            waitForIdle()
            pixels = onNodeWithTag("slider").captureToImage().toPixelMap()
        }
        return pixels
    }

    private fun Color.isRed() = red > .8f && green < .2f && blue < .2f
    private fun Color.isBlue() = blue > .8f && red < .2f && green < .2f

    /** The extent of [match] along the row where the track is widest. */
    private fun PixelMap.span(match: (Color) -> Boolean): IntRange {
        val row = (0 until height).maxBy { y -> (0 until width).count { match(this[it, y]) } }
        val xs = (0 until width).filter { match(this[it, row]) }
        return xs.first()..xs.last()
    }

    @Test fun theSliderTrackRunsUnderTheThumbToBothEnds() {
        val pixels = track { Slider(value = .5f, onValueChange = {}, colors = colors) }
        val active = pixels.span { it.isRed() }
        val inactive = pixels.span { it.isBlue() }
        assertTrue(active.first <= 2, "the filled track starts ${active.first} px in, not at the slider's start")
        assertTrue(inactive.last >= pixels.width - 3, "the track ends ${pixels.width - 1 - inactive.last} px short of the slider's end")
    }

    @Test fun theRangeSliderTrackRunsUnderBothThumbs() {
        val pixels = track { RangeSlider(value = .25f..(.75f), onValueChange = {}, colors = colors) }
        val inactive = pixels.span { it.isBlue() }
        val active = pixels.span { it.isRed() }
        assertTrue(inactive.first <= 2, "the track starts ${inactive.first} px in, not at the slider's start")
        assertTrue(inactive.last >= pixels.width - 3, "the track ends ${pixels.width - 1 - inactive.last} px short of the slider's end")
        // Filled between the thumbs' centres, so the visible fill runs from one thumb's edge to the other's.
        val travel = pixels.width - 38
        val (left, right) = travel / 4 + 38 to travel * 3 / 4
        assertTrue(active.first <= left + 3 && active.last >= right - 3,
            "the fill $active does not reach both thumbs (expected about $left..$right)")
    }
}
