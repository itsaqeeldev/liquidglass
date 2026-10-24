package com.wexpa.liquidglass.sample.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.wexpa.liquidglass.sample.MaterialShowcase
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

/**
 * The shared Material 3 showcase (the screen the Android sample, the desktop app and the web demo run),
 * rendered at a phone's size for the README and the docs: 390 x 844 dp at density 3.
 */
@OptIn(ExperimentalTestApi::class)
class MaterialShowcaseRenderTest {
    private val out = File("build/reports/atlas/showcase").apply { mkdirs() }

    private fun shoot(
        name: String, dark: Boolean, scroll: androidx.compose.ui.unit.Dp = 0.dp,
        width: Int = 390, height: Int = 844, density: Float = 3f,
    ) = runDesktopComposeUiTest((width * density).toInt(), (height * density).toInt(), testTimeout = 6.minutes) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density)) {
                Box(Modifier.size(width.dp, height.dp).testTag("app")) { MaterialShowcase(onSwitchScreen = {}, startDark = dark, startScroll = scroll) }
            }
        }
        waitForIdle()
        ImageIO.write(onNodeWithTag("app").captureToImage().toAwtImage(), "png", File(out, "$name.png"))
    }

    @Test fun light() = shoot("showcase-light", dark = false)
    @Test fun dark() = shoot("showcase-dark", dark = true)
    /** Scrolled so the hero art passes under the top bar, segmented control and buttons. */
    @Test fun scrolled() = shoot("showcase-scrolled-light", dark = false, scroll = 170.dp)
    @Test fun scrolledDark() = shoot("showcase-scrolled-dark", dark = true, scroll = 170.dp)
    /** A desktop browser window, as the web demo is usually seen: 1280 x 800 dp at density 1. */
    @Test fun wide() = shoot("showcase-wide-light", dark = false, width = 1280, height = 800, density = 1f)
    @Test fun wideDark() = shoot("showcase-wide-dark", dark = true, width = 1280, height = 800, density = 1f)
}
