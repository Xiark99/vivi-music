package com.music.vivi.ui.player

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.core.graphics.get
import androidx.core.graphics.scale
import androidx.annotation.RequiresApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.music.vivi.ui.theme.rememberPlayerArtwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BetterLyricsCanvas(artworkUrl: String?, isPlaying: Boolean, visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    val artwork = rememberPlayerArtwork(artworkUrl)
    val prepared by produceState<Bitmap?>(null, artwork) {
        value = artwork?.let { cover ->
            withContext(Dispatchers.Default) {
                val small = cover.scale(32, 32, filter = true)
                val pixels = IntArray(32 * 32)
                small.getPixels(pixels, 0, 32, 0, 0, 32, 32)
                Bitmap.createBitmap(blurArtworkPixels(pixels, 32, 32), 32, 32, Bitmap.Config.ARGB_8888)
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val canAnimate = Build.VERSION.SDK_INT >= 33 && LocalView.current.isHardwareAccelerated
    val elapsed = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lifecycle, isPlaying, prepared, canAnimate) {
        if (isPlaying && prepared != null && canAnimate) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var previous = 0L
                while (true) {
                    withFrameNanos { now ->
                        if (previous == 0L) previous = now
                        if (now - previous >= 32_000_000L) {
                            elapsed.floatValue += ((now - previous) / 1_000_000_000f).coerceAtMost(0.1f)
                            previous = now
                        }
                    }
                }
            }
        }
    }
    val fallback = MaterialTheme.colorScheme.primary
    Crossfade(prepared, modifier = modifier, animationSpec = tween(1000), label = "betterLyricsCover") { cover ->
        val colors = remember(cover, fallback) {
            if (cover == null) listOf(fallback, Color.Black) else listOf(
                Color(cover[6, 6]), Color(cover[25, 8]), Color(cover[16, 25])
            ).map { lerp(Color.Black, it, 0.45f) }
        }
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors))) {
            if (Build.VERSION.SDK_INT >= 33 && cover != null) {
                FluidArtworkShader(cover, elapsed)
            }
        }
    }
}

@RequiresApi(33)
@Composable
private fun FluidArtworkShader(cover: Bitmap, elapsed: State<Float>) {
    val shader = remember(cover) {
        runCatching {
            RuntimeShader(FLUID_ARTWORK_SHADER).apply {
                setInputShader("art", BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
                })
            }
        }.getOrNull()
    } ?: return
    val paint = remember(shader) { Paint().apply { this.shader = shader } }
    Canvas(Modifier.fillMaxSize()) {
        val canvas = drawContext.canvas.nativeCanvas
        if (canvas.isHardwareAccelerated && size.width > 0 && size.height > 0) {
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("time", elapsed.value)
            canvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}

// Original static-art shader; it has no media decoder, video source or player dependency.
private const val FLUID_ARTWORK_SHADER = """
    uniform shader art;
    uniform float2 resolution;
    uniform float time;
    half4 main(float2 position) {
        float2 uv = position / resolution;
        float2 drift = float2(
            sin(uv.y * 5.7 + time * 0.15) + cos(uv.x * 4.2 - time * 0.11),
            sin(uv.x * 5.1 + time * 0.12) + cos(uv.y * 3.9 + time * 0.09));
        float2 p = clamp(0.5 + (uv - 0.5) * 0.75 + drift * 0.16, 0.06, 0.94) * 31.0;
        half3 color = art.eval(p).rgb * 0.4;
        color += (art.eval(p + float2(2.0, 0.0)).rgb + art.eval(p - float2(2.0, 0.0)).rgb
                + art.eval(p + float2(0.0, 2.0)).rgb + art.eval(p - float2(0.0, 2.0)).rgb) * 0.15;
        half gray = dot(color, half3(0.2126, 0.7152, 0.0722));
        color = clamp(mix(half3(gray), color, 1.35), 0.0, 1.0);
        float vignette = 1.0 - 0.25 * length(uv - 0.5);
        return half4(color * 0.45 * vignette, 1.0);
    }
"""

internal fun blurArtworkPixels(input: IntArray, width: Int, height: Int): IntArray {
    require(width > 0 && height > 0 && input.size == width * height)
    var pixels = input.copyOf()
    repeat(6) {
        val next = IntArray(pixels.size)
        for (y in 0 until height) for (x in 0 until width) {
            var red = 0
            var green = 0
            var blue = 0
            for (dy in -2..2) for (dx in -2..2) {
                val color = pixels[(y + dy).coerceIn(0, height - 1) * width + (x + dx).coerceIn(0, width - 1)]
                val alpha = color ushr 24
                red += ((color ushr 16) and 255) * alpha / 255
                green += ((color ushr 8) and 255) * alpha / 255
                blue += (color and 255) * alpha / 255
            }
            next[y * width + x] = (255 shl 24) or ((red / 25) shl 16) or ((green / 25) shl 8) or (blue / 25)
        }
        pixels = next
    }
    return pixels
}
