/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.ui.screens.karaoke

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.metrolist.music.karaoke.model.DuetSyncMetadata
import com.metrolist.music.karaoke.model.KaraokeSessionStore
import com.metrolist.music.karaoke.model.RecordingQuality
import com.metrolist.music.karaoke.recording.AndroidWavKaraokeRecorder
import com.metrolist.music.karaoke.recording.KaraokeRecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Runtime host for a prepared karaoke session. Only the instrumental stem is played. */
@Composable
fun KaraokeSessionScreen(
    sessionId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val coroutineScope = rememberCoroutineScope()
    var session by remember(sessionId) { mutableStateOf(KaraokeSessionStore.get(sessionId)) }

    val activeSession = session
    if (activeSession == null) {
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

    val player = remember(activeSession.id, activeSession.stems.instrumentalUri) {
        ExoPlayer.Builder(appContext).build().apply {
            setMediaItem(MediaItem.fromUri(activeSession.stems.instrumentalUri))
            prepare()
        }
    }
    val recorder = remember(appContext) { AndroidWavKaraokeRecorder(appContext) }
    val recordingRepository = remember(appContext) { KaraokeRecordingRepository(appContext) }

    var positionMs by remember(activeSession.id) { mutableLongStateOf(0L) }
    var isPlaying by remember(activeSession.id) { mutableStateOf(false) }
    var activeRecordingId by remember(activeSession.id) { mutableStateOf<String?>(null) }
    var recordingStartNs by remember(activeSession.id) { mutableLongStateOf(0L) }
    var playbackStartOffsetMs by remember(activeSession.id) { mutableLongStateOf(0L) }
    var recordingStarting by remember(activeSession.id) { mutableStateOf(false) }
    var recordingStatus by remember(activeSession.id) { mutableStateOf<String?>(null) }

    fun beginRecording() {
        if (activeRecordingId != null || recordingStarting) return
        recordingStarting = true
        recordingStatus = "Starting private recording…"
        coroutineScope.launch {
            runCatching {
                player.pause()
                player.seekTo(0L)

                val captureStart = System.nanoTime()
                val recordingId = recorder.start(activeSession, RecordingQuality.STUDIO_WAV)
                val playRequest = System.nanoTime()
                recordingStartNs = captureStart
                playbackStartOffsetMs = ((playRequest - captureStart) / 1_000_000L).coerceAtLeast(0L)
                activeRecordingId = recordingId
                player.play()
            }.onSuccess {
                recordingStatus = "Recording locally • tap Stop to save"
            }.onFailure { error ->
                recordingStatus = error.message ?: "Unable to start recording"
            }
            recordingStarting = false
        }
    }

    fun stopRecording() {
        val recordingId = activeRecordingId ?: return
        activeRecordingId = null
        recordingStatus = "Saving private recording…"
        player.pause()
        coroutineScope.launch {
            runCatching {
                val captured = recorder.stop(recordingId)
                val take = captured.copy(
                    syncMetadata = DuetSyncMetadata(
                        startTimestampNs = recordingStartNs,
                        syncMarkerPositionMs = playbackStartOffsetMs,
                    ),
                )
                recordingRepository.save(activeSession, take)
            }.onSuccess {
                recordingStatus = "Saved privately on this device"
            }.onFailure { error ->
                recordingStatus = error.message ?: "Unable to save recording"
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            beginRecording()
        } else {
            recordingStatus = "Microphone permission is required to record"
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.stop()
            player.release()
        }
    }

    // Each active recording owns its cleanup effect. Leaving the screen never intentionally keeps
    // microphone capture running in the background.
    DisposableEffect(activeRecordingId) {
        val recordingToCancel = activeRecordingId
        onDispose {
            if (recordingToCancel != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    recorder.cancel(recordingToCancel)
                }
            }
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
        lyrics = activeSession.lyrics,
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
        onRecord = {
            if (activeRecordingId != null) {
                stopRecording()
            } else if (
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            ) {
                beginRecording()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onLyricsOffsetChange = { offsetMs ->
            activeSession.lyrics?.let { lyrics ->
                val updatedSession = activeSession.copy(
                    lyrics = lyrics.copy(
                        globalOffsetMs = offsetMs.coerceIn(-10_000L, 10_000L),
                    ),
                )
                KaraokeSessionStore.put(updatedSession)
                session = updatedSession
            }
        },
        isRecording = activeRecordingId != null || recordingStarting,
        recordingStatus = recordingStatus,
    )
}
