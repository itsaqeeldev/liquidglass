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
import kotlin.test.assertEquals
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
    /** Pure red fill and pure blue track, so neither can be mistaken for the white thumb or the black ground. */
    private val colors @Composable get() = SliderDefaults.colors(activeTrackColor = Color.Red, inactiveTrackColor = Color.Blue)

    /** [content] 300 dp wide on black at density 1, captured. */
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

    /** The fill (active track). */
    private fun Color.isRed() = red > .8f && green < .2f && blue < .2f
    /** The track beyond the fill (inactive). */
    private fun Color.isBlue() = blue > .8f && red < .2f && green < .2f

    /** The extent of [match] along the row where the track is widest. */
    private fun PixelMap.span(match: (Color) -> Boolean): IntRange {
        val row = (0 until height).maxBy { y -> (0 until width).count { match(this[it, y]) } }
        val xs = (0 until width).filter { match(this[it, row]) }
        return xs.first()..xs.last()
    }

    private fun PixelMap.count(match: (Color) -> Boolean) =
        (0 until height).sumOf { y -> (0 until width).count { match(this[it, y]) } }

    /** Midway, the fill starts at the slider's start and the track ends at its end. */
    @Test fun theSliderTrackRunsUnderTheThumbToBothEnds() {
        val pixels = track { Slider(value = .5f, onValueChange = {}, colors = colors) }
        val active = pixels.span { it.isRed() }
        val inactive = pixels.span { it.isBlue() }
        assertTrue(active.first <= 2, "the filled track starts ${active.first} px in, not at the slider's start")
        assertTrue(inactive.last >= pixels.width - 3, "the track ends ${pixels.width - 1 - inactive.last} px short of the slider's end")
    }

    /**
     * At 0 the thumb sits at the start and covers the fill, which ends at its centre: no fill shows,
     * and the track still runs to the slider's end.
     */
    @Test fun atZeroNoFillShowsAndTheTrackReachesTheEnd() {
        val pixels = track { Slider(value = 0f, onValueChange = {}, colors = colors) }
        assertEquals(0, pixels.count { it.isRed() }, "fill shows outside the thumb at 0")
        val inactive = pixels.span { it.isBlue() }
        assertTrue(inactive.first <= 38 + 3, "the track starts ${inactive.first} px in, not at the thumb's edge")
        assertTrue(inactive.last >= pixels.width - 3, "the track ends ${pixels.width - 1 - inactive.last} px short of the slider's end")
    }

    /**
     * At 1 the thumb sits at the end and the fill runs from the slider's start under it, so no track
     * beyond the fill shows anywhere.
     */
    @Test fun atOneTheFillRunsFromTheStartToTheThumb() {
        val pixels = track { Slider(value = 1f, onValueChange = {}, colors = colors) }
        assertEquals(0, pixels.count { it.isBlue() }, "unfilled track shows outside the thumb at 1")
        val active = pixels.span { it.isRed() }
        assertTrue(active.first <= 2, "the fill starts ${active.first} px in, not at the slider's start")
        assertTrue(active.last >= pixels.width - 38 - 3, "the fill stops ${pixels.width - 38 - 1 - active.last} px short of the thumb")
    }

    /** The range fill reaches both thumbs, and the track runs to both ends. */
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
