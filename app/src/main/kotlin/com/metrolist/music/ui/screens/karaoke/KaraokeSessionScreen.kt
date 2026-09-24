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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.metrolist.music.karaoke.audio.SynchronizedStemPlayer
import com.metrolist.music.karaoke.cache.KaraokeSessionRepository
import com.metrolist.music.karaoke.model.DuetSyncMetadata
import com.metrolist.music.karaoke.model.KaraokeSessionStore
import com.metrolist.music.karaoke.model.RecordingQuality
import com.metrolist.music.karaoke.recording.AndroidWavKaraokeRecorder
import com.metrolist.music.karaoke.recording.KaraokeRecordingRepository
import com.metrolist.music.karaoke.settings.KaraokeCountdownSecondsKey
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Runtime host for a prepared karaoke session with persistent crash/process recovery. */
@Composable
fun KaraokeSessionScreen(
    sessionId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val coroutineScope = rememberCoroutineScope()
    val sessionRepository = remember(appContext) { KaraokeSessionRepository(appContext) }
    var session by remember(sessionId) { mutableStateOf(KaraokeSessionStore.get(sessionId)) }
    var loadingSession by remember(sessionId) { mutableStateOf(session == null) }
    var countdownSeconds by rememberPreference(KaraokeCountdownSecondsKey, 3)

    LaunchedEffect(sessionId) {
        if (session == null) {
            session = sessionRepository.load(sessionId)?.also(KaraokeSessionStore::put)
        }
        loadingSession = false
    }

    val activeSession = session
    if (activeSession == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (loadingSession) "Loading karaoke…" else "Karaoke session unavailable",
                style = MaterialTheme.typography.headlineSmall,
            )
            if (!loadingSession) {
                Text("Prepare the song again. KaraVox only restores sessions whose cached stems still exist.")
                Button(onClick = onBack) { Text("Back") }
            }
        }
        return
    }

    LaunchedEffect(activeSession.id, activeSession.lyrics) {
        sessionRepository.save(activeSession)
    }

    val player = remember(
        activeSession.id,
        activeSession.stems.instrumentalUri,
        activeSession.stems.vocalsUri,
    ) {
        SynchronizedStemPlayer(
            appContext,
            activeSession.stems.instrumentalUri,
            activeSession.stems.vocalsUri,
        )
    }
    val recorder = remember(appContext) { AndroidWavKaraokeRecorder(appContext) }
    val recordingRepository = remember(appContext) { KaraokeRecordingRepository(appContext) }

    var positionMs by remember(activeSession.id) { mutableLongStateOf(0L) }
    var isPlaying by remember(activeSession.id) { mutableStateOf(false) }
    var vocalMix by remember(activeSession.id) { mutableFloatStateOf(0f) }
    var mixBeforeRecording by remember(activeSession.id) { mutableFloatStateOf(0f) }
    var activeRecordingId by remember(activeSession.id) { mutableStateOf<String?>(null) }
    var recordingStartNs by remember(activeSession.id) { mutableLongStateOf(0L) }
    var playbackStartOffsetMs by remember(activeSession.id) { mutableLongStateOf(0L) }
    var recordingStarting by remember(activeSession.id) { mutableStateOf(false) }
    var recordingStopping by remember(activeSession.id) { mutableStateOf(false) }
    var recordingStatus by remember(activeSession.id) { mutableStateOf<String?>(null) }

    fun beginRecording() {
        if (activeRecordingId != null || recordingStarting || recordingStopping) return
        recordingStarting = true
        coroutineScope.launch {
            runCatching {
                player.pause()
                player.seekTo(0L)
                mixBeforeRecording = vocalMix
                vocalMix = 0f
                player.setVocalMix(0f)

                val safeCountdown = countdownSeconds.coerceIn(0, 5)
                if (safeCountdown > 0) {
                    for (remaining in safeCountdown downTo 1) {
                        recordingStatus = "Recording starts in $remaining…"
                        delay(1_000L)
                    }
                }
                recordingStatus = "Starting private recording…"

                val captureStart = System.nanoTime()
                val recordingId = recorder.start(activeSession, RecordingQuality.STUDIO_WAV)
                val playRequest = System.nanoTime()
                recordingStartNs = captureStart
                playbackStartOffsetMs = ((playRequest - captureStart) / 1_000_000L).coerceAtLeast(0L)
                activeRecordingId = recordingId
                player.play()
            }.onSuccess {
                recordingStatus = "Recording locally • vocal guide muted"
            }.onFailure { error ->
                vocalMix = mixBeforeRecording
                player.setVocalMix(vocalMix)
                recordingStatus = error.message ?: "Unable to start recording"
            }
            recordingStarting = false
        }
    }

    fun stopRecording() {
        val recordingId = activeRecordingId ?: return
        if (recordingStopping) return
        recordingStopping = true
        recordingStatus = "Saving private recording…"
        player.pause()
        coroutineScope.launch {
            runCatching {
                val captured = recorder.stop(recordingId)
                val take = captured.copy(
                    syncMetadata = DuetSyncMetadata(
                        localStartTimestampNs = recordingStartNs,
                        syncMarkerPositionMs = playbackStartOffsetMs,
                    ),
                )
                recordingRepository.save(activeSession, take)
            }.onSuccess {
                recordingStatus = "Saved privately on this device"
            }.onFailure { error ->
                recordingStatus = error.message ?: "Unable to save recording"
            }
            activeRecordingId = null
            vocalMix = mixBeforeRecording
            player.setVocalMix(vocalMix)
            recordingStopping = false
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) beginRecording()
        else recordingStatus = "Microphone permission is required to record"
    }

    DisposableEffect(player) {
        onDispose { player.close() }
    }

    DisposableEffect(activeRecordingId) {
        val recordingToCancel = activeRecordingId
        onDispose {
            if (recordingToCancel != null) {
                CoroutineScope(Dispatchers.IO).launch { recorder.cancel(recordingToCancel) }
            }
        }
    }

    LaunchedEffect(player) {
        while (true) {
            player.maintainSync()
            positionMs = player.currentPosition
            isPlaying = player.isPlaying
            delay(50L)
        }
    }

    KaraokePlayerScreen(
        lyrics = activeSession.lyrics,
        positionMs = positionMs,
        isPlaying = isPlaying,
        vocalMix = vocalMix,
        onVocalMixChange = { value ->
            if (activeRecordingId == null && !recordingStarting && !recordingStopping) {
                vocalMix = value.coerceIn(0f, 1f)
                player.setVocalMix(vocalMix)
            }
        },
        onTogglePlayback = {
            if (player.isPlaying) player.pause() else player.play()
        },
        onRestart = { player.restart() },
        onRecord = {
            if (activeRecordingId != null) {
                stopRecording()
            } else if (!recordingStarting && !recordingStopping &&
                ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
            ) {
                beginRecording()
            } else if (!recordingStarting && !recordingStopping) {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onLyricsOffsetChange = { offsetMs ->
            activeSession.lyrics?.let { lyrics ->
                val updatedSession = activeSession.copy(
                    lyrics = lyrics.copy(globalOffsetMs = offsetMs.coerceIn(-10_000L, 10_000L)),
                )
                KaraokeSessionStore.put(updatedSession)
                session = updatedSession
                coroutineScope.launch { sessionRepository.save(updatedSession) }
            }
        },
        isRecording = activeRecordingId != null || recordingStarting || recordingStopping,
        recordingStatus = recordingStatus,
    )
}
