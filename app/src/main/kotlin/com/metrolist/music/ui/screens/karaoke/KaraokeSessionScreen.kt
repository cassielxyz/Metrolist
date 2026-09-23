/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.ui.screens.karaoke

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.metrolist.music.karaoke.model.KaraokeSessionStore
import kotlinx.coroutines.delay

/** Runtime host for a prepared karaoke session. Only the instrumental stem is played. */
@Composable
fun KaraokeSessionScreen(
    sessionId: String,
    onBack: () -> Unit,
    onRecord: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val session = remember(sessionId) { KaraokeSessionStore.get(sessionId) }

    if (session == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Karaoke session expired",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text("Prepare the song again. Cached instrumental stems are still kept on this device.")
            Button(onClick = onBack) { Text("Back") }
        }
        return
    }

    val player = remember(session.id, session.stems.instrumentalUri) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(MediaItem.fromUri(session.stems.instrumentalUri))
            prepare()
        }
    }
    var positionMs by remember(session.id) { mutableLongStateOf(0L) }
    var isPlaying by remember(session.id) { mutableStateOf(false) }

    DisposableEffect(player) {
        onDispose {
            player.stop()
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            delay(50L)
        }
    }

    KaraokePlayerScreen(
        lyrics = session.lyrics,
        positionMs = positionMs,
        isPlaying = isPlaying,
        vocalMix = 0f,
        onTogglePlayback = {
            if (player.isPlaying) player.pause() else player.play()
        },
        onRestart = {
            player.seekTo(0L)
            player.play()
        },
        onRecord = { onRecord(session.id) },
    )
}
