package com.wexpa.liquidglass.sample

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.wexpa.liquidglass.material3.*
import kotlin.math.PI
import kotlin.math.sin

/** Album titles and artists: invented, as is every cover. */
private val Albums = listOf(
    "Midnight Drive" to "Neon Coast", "Glasshouse" to "Lumen", "Evergreen" to "Fern & Field",
    "Golden Hour" to "Solace", "Neon Bloom" to "Prism", "Open Water" to "Tidal",
    "Paper Moons" to "Atlas", "Signal" to "Static Youth",
)

/**
 * A Material 3 app built only from `liquidglass-material3`, shared by the Android sample, the desktop
 * app and the web demo: a music library over a colourful wallpaper and detailed album art, which is
 * what glass is for. Everything is interactive: the tab bar and its search button, the segmented
 * control, the chips, the mini player's controls, and from the menu a dialog and a sheet. The star
 * button switches between the light and dark schemes.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MaterialShowcase(
    onSwitchScreen: () -> Unit,
    startDark: Boolean = false,
    startSheet: Boolean = false,
    /** Start scrolled this far, so the album art already sits under the top bar's glass. */
    startScroll: Dp = 0.dp,
) {
    var dark by remember { mutableStateOf(startDark) }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        var tab by remember { mutableIntStateOf(0) }
        var segment by remember { mutableIntStateOf(0) }
        var playing by remember { mutableStateOf(true) }
        var progress by remember { mutableFloatStateOf(.38f) }
        var crossfade by remember { mutableStateOf(true) }
        var dialog by remember { mutableStateOf(false) }
        var sheet by remember { mutableStateOf(startSheet) }
        var menu by remember { mutableStateOf(false) }
        var downloaded by remember { mutableStateOf(true) }
        Scaffold(
            containerColor = if (dark) Color(0xFF0B0B10) else Color(0xFFF6F3F8),
            topBar = {
                TopAppBar(
                    title = { Text("Library", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onSwitchScreen) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atlas Studio") } },
                    actions = {
                        // Hold (or hover) for the glass tooltip.
                        TooltipBox(TooltipDefaults.rememberPlainTooltipPositionProvider(),
                            { PlainTooltip { Text(if (dark) "Switch to light" else "Switch to dark") } }, rememberTooltipState()) {
                            IconButton(onClick = { dark = !dark }) { Icon(Icons.Default.Star, "Switch light and dark") }
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Play next") }, onClick = { menu = false; dialog = true })
                                DropdownMenuItem(text = { Text("Queue") }, onClick = { menu = false; sheet = true })
                                DropdownMenuItem(text = { Text("Atlas Studio") }, onClick = { menu = false; onSwitchScreen() })
                            }
                        }
                    },
                )
            },
            bottomBar = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                NavigationBar(accessory = { Icon(Icons.Default.Search, "Search") }, onAccessoryClick = { sheet = true },
                    modifier = Modifier.widthIn(max = 520.dp)) {
                    listOf(Icons.Default.Home to "Listen", Icons.Default.Favorite to "Loved", Icons.Default.Person to "You")
                        .forEachIndexed { i, (icon, label) ->
                            NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, null) }, label = { Text(label) })
                        }
                }
            } },
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                Wallpaper(dark, Modifier.fillMaxSize())
                val scrollPx = with(LocalDensity.current) { startScroll.roundToPx() }
                LazyVerticalGrid(
                    GridCells.Adaptive(150.dp), Modifier.fillMaxSize(),
                    state = rememberLazyGridState(initialFirstVisibleItemScrollOffset = scrollPx),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 172.dp, bottom = 240.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text("Recently played", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.height(10.dp))
                            // 1.6:1 on a phone; capped on a wide window, where the full-width cover was
                            // taller than the screen and showed only the top of its sky.
                            BoxWithConstraints {
                                CoverArt((tab + 0) % CoverCount, Modifier.fillMaxWidth().height(min(maxWidth / 1.6f, 280.dp))
                                    .clip(RoundedCornerShape(22.dp)))
                            }
                        }
                    }
                    items(Albums.size * 3) { i ->
                        val (title, artist) = Albums[(i + tab) % Albums.size]
                        Column {
                            CoverArt((i + tab + 1) % CoverCount, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
                            Text(title, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(artist, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .6f),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Column(
                    Modifier.padding(top = padding.calculateTopPadding()).widthIn(max = 520.dp).fillMaxWidth().align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Albums", "Songs", "Artists").forEachIndexed { i, l ->
                            SegmentedButton(selected = segment == i, onClick = { segment = i },
                                shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(l) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { playing = true }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Play") }
                        OutlinedButton(onClick = { sheet = true }) { Text("Shuffle") }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { dialog = true }) { Icon(Icons.Default.Add, "Add") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = downloaded, onClick = { downloaded = !downloaded }, label = { Text("Downloaded") },
                            leadingIcon = if (downloaded) { { Icon(Icons.Default.Check, null, Modifier.size(18.dp)) } } else null)
                        AssistChip(onClick = {}, label = { Text("Recently added") })
                    }
                }
                // The mini player, floating over the library as iOS's accessory does.
                Card(Modifier.align(Alignment.BottomCenter).padding(bottom = padding.calculateBottomPadding()).padding(horizontal = 16.dp, vertical = 10.dp)
                    .widthIn(max = 488.dp).fillMaxWidth()) {
                    // Inset like iOS's accessories: the controls keep clear of the card's rim.
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoverArt(0, Modifier.size(46.dp).clip(RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Midnight Drive", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("Neon Coast", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                            }
                            IconButton(onClick = { playing = !playing }) {
                                Icon(if (playing) Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play")
                            }
                            Spacer(Modifier.width(10.dp))
                            Switch(checked = crossfade, onCheckedChange = { crossfade = it })
                        }
                        Slider(value = progress, onValueChange = { progress = it }, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (dialog) AlertDialog(
                    onDismissRequest = { dialog = false },
                    confirmButton = { TextButton(onClick = { dialog = false }) { Text("Play next") } },
                    dismissButton = { TextButton(onClick = { dialog = false }) { Text("Cancel") } },
                    title = { Text("Play Glasshouse next?") },
                    text = { Text("The glass behind this dialog refracts the app, drawn in the same window.") },
                )
                if (sheet) ModalBottomSheet(onDismissRequest = { sheet = false }) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Up next", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Albums.take(5).forEachIndexed { i, (title, artist) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CoverArt(i + 1, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                                Spacer(Modifier.width(12.dp))
                                Column { Text(title, fontWeight = FontWeight.SemiBold); Text(artist, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        Button(onClick = { sheet = false }) { Text("Done") }
                    }
                }
            }
        }
    }
}

// ---- Art: everything is drawn, so the demo ships no images ------------------------------------------

/** A soft mesh of colour, as iOS's wallpapers are: what glass is seen over most. */
@Composable
private fun Wallpaper(dark: Boolean, modifier: Modifier) = Canvas(modifier) {
    drawRect(if (dark) Color(0xFF0B0B10) else Color(0xFFF6F3F8))
    val blobs = if (dark) listOf(
        Triple(Offset(.15f, .10f), .55f, Color(0xFF6A3DE8)), Triple(Offset(.95f, .25f), .50f, Color(0xFFE8466A)),
        Triple(Offset(.30f, .60f), .55f, Color(0xFF1E8BC3)), Triple(Offset(.90f, .85f), .55f, Color(0xFFF2994A)),
    ) else listOf(
        Triple(Offset(.10f, .08f), .60f, Color(0xFFB8A4FF)), Triple(Offset(.95f, .22f), .55f, Color(0xFFFFA8B8)),
        Triple(Offset(.25f, .62f), .60f, Color(0xFF9ED8FF)), Triple(Offset(.92f, .88f), .60f, Color(0xFFFFD39A)),
    )
    for ((c, r, color) in blobs) {
        val centre = Offset(c.x * size.width, c.y * size.height)
        val radius = r * size.maxDimension
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = if (dark) .55f else .8f), color.copy(alpha = 0f)), centre, radius), radius, centre)
    }
}

/** Material's pause glyph; the core icon set has none. */
private val Pause = materialIcon("Filled.Pause") {
    materialPath {
        moveTo(6f, 19f); horizontalLineToRelative(4f); verticalLineTo(5f); horizontalLineTo(6f); close()
        moveTo(14f, 5f); verticalLineToRelative(14f); horizontalLineToRelative(4f); verticalLineTo(5f); close()
    }
}

private const val CoverCount = 8

/** One of [CoverCount] invented album covers, each with the kind of detail glass shows off. */
@Composable
private fun CoverArt(index: Int, modifier: Modifier) = Canvas(modifier) {
    when (index % CoverCount) {
        0 -> sunset()
        1 -> rings()
        2 -> waves()
        3 -> checker()
        4 -> bars()
        5 -> bloom()
        6 -> moons()
        else -> signal()
    }
}

private fun DrawScope.sunset() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2B1055), Color(0xFFD53369), Color(0xFFFFB25B))))
    val sun = Offset(size.width / 2, size.height * .58f); val r = size.minDimension * .3f
    drawCircle(Brush.verticalGradient(listOf(Color(0xFFFFF1A8), Color(0xFFFF7E5F)), sun.y - r, sun.y + r), r, sun)
    var y = sun.y; var h = 2f
    while (y < sun.y + r) { drawRect(Color(0xFF7A1F5C), Offset(0f, y), Size(size.width, h)); y += h * 3.2f; h *= 1.25f }
    drawRect(Brush.verticalGradient(listOf(Color(0xFF3A0E4F), Color(0xFF0E0628)), size.height * .78f, size.height),
        Offset(0f, size.height * .78f), Size(size.width, size.height * .22f))
}

private fun DrawScope.rings() {
    drawRect(Color(0xFF14163D))
    val c = Offset(size.width * .38f, size.height * .42f)
    val colours = listOf(Color(0xFFF7B733), Color(0xFFC33764), Color(0xFF4AC29A), Color(0xFF2E86DE))
    var r = size.maxDimension * 1.1f; var i = 0
    while (r > 4f) { drawCircle(colours[i % colours.size], r, c); r -= size.minDimension * .07f; i++ }
}

private fun DrawScope.waves() {
    drawRect(Color(0xFF06283D))
    val colours = listOf(Color(0xFF1363DF), Color(0xFF47B5FF), Color(0xFF7FE7DC), Color(0xFFDFF6FF), Color(0xFF256D85), Color(0xFF00A8CC))
    for ((k, colour) in colours.withIndex()) {
        val base = size.height * (.18f + k * .14f)
        val path = Path().apply {
            moveTo(0f, size.height)
            var x = 0f
            while (x <= size.width) { lineTo(x, base + sin(x / size.width * 2 * PI * 1.6 + k).toFloat() * size.height * .06f); x += 4f }
            lineTo(size.width, size.height); close()
        }
        drawPath(path, colour)
    }
}

private fun DrawScope.checker() {
    drawRect(Color(0xFFFFF4D6))
    val n = 8; val s = size.width / n
    rotate(12f) {
        for (i in -2 until n + 3) for (j in -2 until n + 3) if ((i + j) % 2 == 0)
            drawRect(Color(0xFFFF4E50), Offset(i * s, j * s), Size(s, s))
    }
    drawCircle(Color(0xFFF9D423), size.minDimension * .22f, Offset(size.width * .64f, size.height * .4f))
    drawCircle(Color(0xFFFFF4D6), size.minDimension * .12f, Offset(size.width * .64f, size.height * .4f))
}

private fun DrawScope.bars() {
    val colours = listOf(Color(0xFFEDEDED), Color(0xFFF2D21B), Color(0xFF19C3D9), Color(0xFF29C94B), Color(0xFFD936C2),
        Color(0xFFE23C2F), Color(0xFF2E3BD8))
    val w = size.width / colours.size
    for ((k, c) in colours.withIndex()) drawRect(c, Offset(k * w, 0f), Size(w + 1f, size.height * .72f))
    drawRect(Color(0xFF111111), Offset(0f, size.height * .72f), Size(size.width, size.height * .28f))
    for (k in 0 until 14) drawRect(if (k % 2 == 0) Color.White else Color(0xFF222222),
        Offset(k * size.width / 14, size.height * .8f), Size(size.width / 14 + 1, size.height * .1f))
}

private fun DrawScope.bloom() {
    drawRect(Brush.radialGradient(listOf(Color(0xFFFFE3F1), Color(0xFFFF6FB5)), Offset(size.width / 2, size.height / 2), size.maxDimension))
    val c = Offset(size.width / 2, size.height / 2)
    for (k in 0 until 12) rotate(k * 30f, c) {
        drawOval(Color(0xFFB0127A).copy(alpha = .55f), Offset(c.x - size.width * .08f, c.y - size.height * .42f),
            Size(size.width * .16f, size.height * .42f))
    }
    drawCircle(Color(0xFFFFD84D), size.minDimension * .1f, c)
}

private fun DrawScope.moons() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF2C5364))))
    for (k in 0 until 5) {
        val x = size.width * (.1f + k * .2f); val r = size.minDimension * .085f
        drawCircle(Color(0xFFF5F0E1), r, Offset(x, size.height * .5f))
        drawCircle(Color(0xFF203A43), r, Offset(x + r * (k - 2) * .45f, size.height * .5f))
    }
    drawLine(Color(0xFFF5F0E1).copy(alpha = .5f), Offset(0f, size.height * .72f), Offset(size.width, size.height * .72f), 2f)
}

private fun DrawScope.signal() {
    drawRect(Color(0xFF0D0D0D))
    val c = Offset(size.width * .5f, size.height * 1.05f)
    for (k in 1..9) drawCircle(if (k % 2 == 0) Color(0xFF00F5A0) else Color(0xFF00D9F5), size.minDimension * .11f * k, c,
        style = Stroke(size.minDimension * .03f))
}
