/**
 * vivimusic Project (C) 2026. Licensed under GPL-3.0; see LICENSE and git history.
 * Modified for Nami on 2026-10-17: optional display, lifecycle and volume write handling.
 * Restores the Apple Music style volume row removed in cb275ae7.
 */
package com.music.vivi.ui.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.music.vivi.R
import com.music.vivi.ui.component.PlayerSliderTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppleMusicVolumeSlider(
    isCasting: Boolean,
    castVolume: Float,
    onCastVolumeChange: (Float) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember(audioManager) { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    val systemVolume by produceState(
        initialValue = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume,
        key1 = audioManager,
    ) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                value = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitDispose { context.unregisterReceiver(receiver) }
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val active = isDragged || isPressed
    var dragVolume by remember { mutableFloatStateOf(systemVolume) }
    LaunchedEffect(systemVolume, active) {
        if (!active) dragVolume = systemVolume
    }
    // Keep Binder calls off the UI thread and coalesce rapid gestures without reordering writes.
    val volumeChanges = remember { Channel<Int>(Channel.CONFLATED) }
    LaunchedEffect(audioManager) {
        for (step in volumeChanges) {
            withContext(Dispatchers.IO) { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0) }
        }
    }
    DisposableEffect(volumeChanges) { onDispose { volumeChanges.close() } }
    val animatedVolume by animateFloatAsState(systemVolume, tween(150, easing = LinearOutSlowInEasing), label = "appleVolume")
    val trackHeight by animateDpAsState(if (active) 16.dp else 10.dp, spring(0.7f, 600f), label = "appleVolumeHeight")
    val iconScale by animateFloatAsState(if (active) 1.15f else 1f, spring(0.7f, 600f), label = "appleVolumeIcon")
    val volumeLabel = stringResource(R.string.volume)

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.volume_mute), contentDescription = null, tint = accentColor,
            modifier = Modifier.size(20.dp).graphicsLayer(scaleX = iconScale, scaleY = iconScale),
        )
        Spacer(Modifier.width(12.dp))
        Slider(
            value = (if (isCasting) castVolume else if (active) dragVolume else animatedVolume).coerceIn(0f, 1f),
            onValueChange = { value ->
                dragVolume = value
                if (isCasting) onCastVolumeChange(value)
                else volumeChanges.trySend((value * maxVolume).roundToInt().coerceIn(0, maxVolume))
            },
            modifier = Modifier.weight(1f).semantics { contentDescription = volumeLabel },
            interactionSource = interactionSource,
            thumb = {},
            track = { sliderState ->
                PlayerSliderTrack(
                    sliderState = sliderState,
                    colors = SliderDefaults.colors(
                        activeTrackColor = accentColor.copy(alpha = 0.7f),
                        inactiveTrackColor = accentColor.copy(alpha = 0.15f),
                    ),
                    trackHeight = trackHeight,
                )
            },
        )
        Spacer(Modifier.width(12.dp))
        Icon(
            painterResource(R.drawable.volume_up), contentDescription = null, tint = accentColor,
            modifier = Modifier.size(20.dp).graphicsLayer(scaleX = iconScale, scaleY = iconScale),
        )
    }
}
