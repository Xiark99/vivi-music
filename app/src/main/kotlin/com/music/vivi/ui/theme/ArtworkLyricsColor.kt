package com.music.vivi.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalProcessedLyricsBackground = staticCompositionLocalOf<Boolean?> { null }

@Composable
fun rememberPlayerArtwork(url: String?): Bitmap? {
    val context = LocalContext.current
    val artworkState = produceState<Bitmap?>(null, url) {
        value = if (url == null) null else withContext(Dispatchers.IO) {
            try {
                context.imageLoader.execute(
                    ImageRequest.Builder(context).data(url).size(128, 128).allowHardware(false).build()
                ).image?.toBitmap()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }
    return artworkState.value
}

fun lyricsContrastColor(accent: Color, background: Color): Color {
    fun contrast(color: Color): Float {
        val a = color.luminance() + 0.05f
        val b = background.luminance() + 0.05f
        return maxOf(a, b) / minOf(a, b)
    }
    if (contrast(accent) >= 4.5f) return accent
    val end = if (contrast(Color.White) > contrast(Color.Black)) Color.White else Color.Black
    var low = 0f
    var high = 1f
    repeat(12) {
        val mid = (low + high) / 2
        if (contrast(lerp(accent, end, mid)) >= 4.5f) high = mid else low = mid
    }
    return lerp(accent, end, high)
}

@Composable
fun rememberArtworkLyricsColor(url: String?, processedBackground: Boolean): Color {
    val artwork = rememberPlayerArtwork(url)
    val fallback = MaterialTheme.colorScheme.primary
    val seed by produceState(fallback, artwork) {
        value = if (artwork == null) fallback else withContext(Dispatchers.Default) {
            runCatching { artwork.extractThemeColor() }.getOrDefault(fallback)
        }
    }
    val scheme = rememberDynamicColorScheme(
        seedColor = seed,
        isDark = processedBackground || MaterialTheme.colorScheme.surface.luminance() < 0.5f,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )
    // A 68% black scrim bounds even a white moving backdrop to this luminance.
    val backing = if (processedBackground) Color(0xFF525252) else MaterialTheme.colorScheme.surface
    val animated by animateColorAsState(scheme.primary, tween(700), label = "artworkLyrics")
    return lyricsContrastColor(animated, backing)
}
