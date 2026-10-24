@file:OptIn(ExperimentalMaterial3Api::class)

package com.wexpa.liquidglass.material3

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSliderState
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.wexpa.liquidglass.CapsuleShape
import com.wexpa.liquidglass.Glass
import com.wexpa.liquidglass.glassEffect
import androidx.compose.material3.RangeSlider as M3RangeSlider
import androidx.compose.material3.Slider as M3Slider

/** The iOS 26 control thumb: a white pill at rest that turns into a glass lens while held. Authored
 * proportions from iOS 26 (38 x 24 pt, about 1.4x larger while held). */
private val GlassThumbWidth = 38.dp

@Composable
private fun GlassThumb(held: Boolean, enabled: Boolean) {
    val grow by animateFloatAsState(if (held) 1.4f else 1f, spring(dampingRatio = .7f, stiffness = 500f), label = "thumb")
    val modifier = Modifier.size(GlassThumbWidth, 24.dp).scale(grow)
    if (held && enabled) Box(modifier.glassEffect(Glass.Lens(1.25f), CapsuleShape))
    else Box(modifier.background(if (enabled) Color.White else Color(0xFFE5E5EA), CapsuleShape))
}

/**
 * Material 3's `Switch`, iOS 26 style: a capsule track in [colors]' track colours and a thumb that
 * becomes a glass lens while pressed. The same parameters as Material's.
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(
        checkedTrackColor = MaterialTheme.colorScheme.primary,
        uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .16f),
    ),
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val offset by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = .75f, stiffness = 400f), label = "switch")
    val track = when {
        !enabled -> if (checked) colors.disabledCheckedTrackColor else colors.disabledUncheckedTrackColor
        checked -> colors.checkedTrackColor
        else -> colors.uncheckedTrackColor
    }
    val toggle = if (onCheckedChange != null) Modifier.toggleable(checked, source, null, enabled, Role.Switch, onCheckedChange) else Modifier
    Box(modifier.then(toggle).size(62.dp, 28.dp).background(track, CapsuleShape), contentAlignment = Alignment.CenterStart) {
        Box(Modifier.offset(x = offset), contentAlignment = Alignment.Center) {
            GlassThumb(pressed, enabled)
            thumbContent?.invoke()
        }
    }
}

/**
 * Material 3's `Slider`, iOS 26 style: Material's track and a thumb that becomes a glass lens while
 * dragged, magnifying the track under it. The same parameters as Material's.
 */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .16f),
    ),
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val dragged by source.collectIsDraggedAsState()
    // The value overload with custom thumb and track: the same call on Material 3 1.9 and later.
    M3Slider(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
        onValueChangeFinished = onValueChangeFinished, colors = colors, interactionSource = source, steps = steps,
        thumb = { GlassThumb(pressed || dragged, enabled) },
        track = { GlassSliderTrack(it, valueRange, colors, enabled) },
        valueRange = valueRange,
    )
}

/**
 * Material 3's `RangeSlider`, iOS 26 style: the same thin track, filled between the two thumbs, and
 * two thumbs that each become a glass lens while dragged. The same parameters as Material's.
 */
@Composable
fun RangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .16f),
    ),
) {
    val start = remember { MutableInteractionSource() }
    val end = remember { MutableInteractionSource() }
    val startHeld by start.collectIsPressedAsState()
    val startDragged by start.collectIsDraggedAsState()
    val endHeld by end.collectIsPressedAsState()
    val endDragged by end.collectIsDraggedAsState()
    M3RangeSlider(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled,
        valueRange = valueRange, onValueChangeFinished = onValueChangeFinished, colors = colors,
        startInteractionSource = start, endInteractionSource = end,
        startThumb = { GlassThumb(startHeld || startDragged, enabled) },
        endThumb = { GlassThumb(endHeld || endDragged, enabled) },
        track = { GlassRangeTrack(it, colors, enabled) },
        steps = steps,
    )
}

/** [GlassSliderTrack] for a range: filled between the two thumbs' centres. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassRangeTrack(state: RangeSliderState, colors: SliderColors, enabled: Boolean) {
    val active = if (enabled) colors.activeTrackColor else colors.disabledActiveTrackColor
    val inactive = if (enabled) colors.inactiveTrackColor else colors.disabledInactiveTrackColor
    val range = state.valueRange
    fun fraction(v: Float) = if (range.endInclusive > range.start)
        ((v - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f) else 0f
    val from = fraction(state.activeRangeStart)
    val to = fraction(state.activeRangeEnd)
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val r = CornerRadius(size.height / 2f)
        val overhang = GlassThumbWidth.toPx() / 2f
        drawRoundRect(inactive, Offset(-overhang, 0f), Size(size.width + 2f * overhang, size.height), r)
        drawRoundRect(active, Offset(from * size.width, 0f), Size((to - from) * size.width, size.height), r)
    }
}

/**
 * iOS's slider track: one thin capsule, filled from the start to the thumb's centre, with no gap
 * around the thumb (Material's track breaks there). 6 dp tall; authored to iOS 26's look, not measured.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlassSliderTrack(state: SliderState, range: ClosedFloatingPointRange<Float>, colors: SliderColors, enabled: Boolean) {
    val active = if (enabled) colors.activeTrackColor else colors.disabledActiveTrackColor
    val inactive = if (enabled) colors.inactiveTrackColor else colors.disabledInactiveTrackColor
    val fraction = if (range.endInclusive > range.start)
        ((state.value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f) else 0f
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val r = CornerRadius(size.height / 2f)
        // Material lays the track out between the thumb's centre positions (half a thumb in from each
        // end). iOS's capsule runs under the whole thumb, so draw half a thumb past both ends.
        val overhang = GlassThumbWidth.toPx() / 2f
        drawRoundRect(inactive, Offset(-overhang, 0f), Size(size.width + 2f * overhang, size.height), r)
        drawRoundRect(active, Offset(-overhang, 0f), Size(overhang + fraction * size.width, size.height), r)
    }
}
