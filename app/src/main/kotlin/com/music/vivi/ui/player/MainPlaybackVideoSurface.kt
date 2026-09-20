package com.music.vivi.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Surface only for the MusicService's primary player. It deliberately owns no player, audio
 * output, queue or lifecycle beyond attaching/detaching the view's surface.
 */
@Composable
fun MainPlaybackVideoSurface(
    player: ExoPlayer,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(player) {
        onDispose {
            // Leaving Now Playing (including backgrounding) detaches only the surface. The
            // service-owned audio renderer, MediaSession and notification keep running.
            player.clearVideoSurface()
        }
    }

    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
        update = { view -> view.player = player },
        onRelease = { view ->
            // PlayerView owns a Player.Listener as well as the Surface. Drop both references
            // when Compose releases this view; never stop or release the service player.
            view.player = null
            player.clearVideoSurface()
        },
        modifier = modifier.alpha(if (visible) 1f else 0f),
    )
}
