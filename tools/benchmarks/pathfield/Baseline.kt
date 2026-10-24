package com.wexpa.liquidglass
// Unmodified full-resolution algorithm from upstream 0d88a23, renamed for paired measurement.
// Apache-2.0, original implementation by the LiquidGlass contributors.
import androidx.compose.ui.graphics.*
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
private const val FIELD_SCALE_BASELINE = 2
/** Builds the upstream allocation baseline; added documentation does not change its algorithm. */
internal fun buildPathFieldBaseline(
    path: Path,
    layerWidth: Int,
    layerHeight: Int,
    pad: Float,
    band: Float,
): GlassPathField? {
    val fw = (layerWidth / FIELD_SCALE_BASELINE).coerceAtLeast(1)
    val fh = (layerHeight / FIELD_SCALE_BASELINE).coerceAtLeast(1)
    if (fw < 2 || fh < 2) return null

    // Rasterise the outline at the layer's own resolution, at the panel's offset inside it, and
    // take the distance down to the field's resolution afterwards. Rasterising at field
    // resolution put the outline on a half texel whenever the pad was odd (the pad is a whole
    // number of layer pixels, not of texels), and the boolean mask then rounded it: the sampled
    // silhouette sat a full pixel off the closed form for pads 23 and 25 and exactly on it for 22
    // and 24. A full-resolution mask keeps the outline where the layer has it.
    val mask = ImageBitmap(layerWidth, layerHeight)
    val canvas = Canvas(mask)
    val paint = Paint().apply { color = Color.White }
    canvas.save()
    canvas.translate(pad, pad)
    canvas.drawPath(path, paint)
    canvas.restore()

    val pixels = mask.toPixelMap()
    val inside = BooleanArray(layerWidth * layerHeight)
    for (y in 0 until layerHeight) {
        for (x in 0 until layerWidth) {
            inside[y * layerWidth + x] = pixels[x, y].alpha > 0.5f
        }
    }

    val full = signedDistanceBaseline(inside, layerWidth, layerHeight)
    // A texel covers FIELD_SCALE_BASELINE x FIELD_SCALE_BASELINE layer pixels and is sampled at its centre, which
    // is the mean of the pixel centres it covers; distance is smooth, so the mean is the value.
    val distance = FloatArray(fw * fh)
    for (y in 0 until fh) {
        for (x in 0 until fw) {
            var acc = 0f; var n = 0
            for (dy in 0 until FIELD_SCALE_BASELINE) for (dx in 0 until FIELD_SCALE_BASELINE) {
                val xx = x * FIELD_SCALE_BASELINE + dx; val yy = y * FIELD_SCALE_BASELINE + dy
                if (xx < layerWidth && yy < layerHeight) { acc += full[yy * layerWidth + xx]; n++ }
            }
            distance[y * fw + x] = if (n > 0) acc / n / FIELD_SCALE_BASELINE else 0f
        }
    }
    // `full` is in layer pixels; the field's encoding below is in texels (range / FIELD_SCALE_BASELINE), so
    // the distance is divided by FIELD_SCALE_BASELINE above to keep the stored units unchanged.

    // The shader only reads within the band, so spending the eight bits there rather than on
    // the far interior is what keeps the quantisation under a pixel.
    val range = (band * 1.5f).coerceAtLeast(8f) / FIELD_SCALE_BASELINE
    val out = IntArray(fw * fh)
    for (i in out.indices) {
        val norm = ((distance[i] / range) * 0.5f + 0.5f).coerceIn(0f, 1f)
        val v = (norm * 255f).roundToInt().coerceIn(0, 255)
        out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }

    return GlassPathField(
        bitmap = createFieldBitmap(fw, fh, out),
        range = range * FIELD_SCALE_BASELINE,
        scale = 1f / FIELD_SCALE_BASELINE,
        layerWidth = layerWidth,
        layerHeight = layerHeight,
    )
}

/**
 * Exact Euclidean distance, negative inside, by the 8-point sequential transform.
 *
 * Two passes over the grid propagating the nearest-boundary offset vector, run once for the
 * inside and once for the outside, then subtracted. Chamfer weights would be a line shorter and
 * are wrong by up to 8% on diagonals, which shows up as a normal that leans the wrong way on
 * exactly the sloped edges a hand-drawn shape is made of.
 */
private fun signedDistanceBaseline(inside: BooleanArray, w: Int, h: Int): FloatArray {
    val outer = edtBaseline(inside, w, h, seedInside = false)
    val inner = edtBaseline(inside, w, h, seedInside = true)
    return FloatArray(w * h) { outer[it] - inner[it] }
}

private const val FAR_BASELINE = 1e9f

/** Computes upstream distances with fresh arrays on every pass for the allocation comparison. */
private fun edtBaseline(inside: BooleanArray, w: Int, h: Int, seedInside: Boolean): FloatArray {
    val dx = FloatArray(w * h)
    val dy = FloatArray(w * h)
    val d = FloatArray(w * h)

    for (i in 0 until w * h) {
        val seed = if (seedInside) !inside[i] else inside[i]
        if (seed) {
            dx[i] = 0f; dy[i] = 0f; d[i] = 0f
        } else {
            dx[i] = FAR_BASELINE; dy[i] = FAR_BASELINE; d[i] = FAR_BASELINE
        }
    }

    /** Propagates a nearer seed using the unchanged upstream distance comparison. */
    fun compare(i: Int, ox: Int, oy: Int, sx: Int, sy: Int) {
        val nx = i + ox + oy * w
        if (nx < 0 || nx >= w * h) return
        val cx = dx[nx] + sx
        val cy = dy[nx] + sy
        if (cx >= FAR_BASELINE || cy >= FAR_BASELINE) return
        val cd = cx * cx + cy * cy
        if (cd < d[i] * d[i]) {
            dx[i] = cx; dy[i] = cy; d[i] = sqrt(cd)
        }
    }

    for (y in 0 until h) {
        for (x in 0 until w) {
            val i = y * w + x
            if (x > 0) compare(i, -1, 0, 1, 0)
            if (y > 0) compare(i, 0, -1, 0, 1)
            if (x > 0 && y > 0) compare(i, -1, -1, 1, 1)
            if (x < w - 1 && y > 0) compare(i, 1, -1, 1, 1)
        }
        for (x in w - 1 downTo 0) {
            val i = y * w + x
            if (x < w - 1) compare(i, 1, 0, 1, 0)
        }
    }
    for (y in h - 1 downTo 0) {
        for (x in w - 1 downTo 0) {
            val i = y * w + x
            if (x < w - 1) compare(i, 1, 0, 1, 0)
            if (y < h - 1) compare(i, 0, 1, 0, 1)
            if (x < w - 1 && y < h - 1) compare(i, 1, 1, 1, 1)
            if (x > 0 && y < h - 1) compare(i, -1, 1, 1, 1)
        }
        for (x in 0 until w) {
            val i = y * w + x
            if (x > 0) compare(i, -1, 0, 1, 0)
        }
    }

    for (i in d.indices) if (d[i] >= FAR_BASELINE) d[i] = min(w, h).toFloat()
    return d
}
