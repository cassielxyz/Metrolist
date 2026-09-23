/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.karaoke

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.metrolist.music.karaoke.recording.KaraokeRecordingRepository
import com.metrolist.music.karaoke.recording.SavedKaraokeRecording
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun KaraokeRecordingsScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(appContext) { KaraokeRecordingRepository(appContext) }
    val coroutineScope = rememberCoroutineScope()
    val player = remember { ExoPlayer.Builder(appContext).build() }
    var recordings by remember { mutableStateOf<List<SavedKaraokeRecording>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var activeRecordingId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun reload() {
        coroutineScope.launch {
            runCatching { repository.list() }
                .onSuccess {
                    recordings = it
                    loading = false
                    errorMessage = null
                }
                .onFailure { error ->
                    loading = false
                    errorMessage = error.message ?: "Unable to load recordings"
                }
        }
    }

    LaunchedEffect(Unit) { reload() }

    DisposableEffect(player) {
        onDispose {
            player.stop()
            player.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Recordings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Private vocal takes stored only on this device until you explicitly export or share them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        errorMessage?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        when {
            loading -> Text("Loading recordings…")
            recordings.isEmpty() -> Text(
                text = "No recordings yet. Prepare a song, start karaoke, then tap Record.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(recordings, key = { it.take.id }) { recording ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = recording.title.ifBlank { "Karaoke recording" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (recording.artist.isNotBlank()) {
                                Text(
                                    text = recording.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "${recording.take.durationMs / 1_000}s • " +
                                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                        .format(Date(recording.createdAtEpochMs)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (activeRecordingId == recording.take.id && player.isPlaying) {
                                            player.pause()
                                            activeRecordingId = null
                                        } else {
                                            player.setMediaItem(MediaItem.fromUri(recording.take.vocalUri))
                                            player.prepare()
                                            player.play()
                                            activeRecordingId = recording.take.id
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(if (activeRecordingId == recording.take.id && player.isPlaying) "Pause" else "Play vocal")
                                }
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            if (activeRecordingId == recording.take.id) {
                                                player.stop()
                                                activeRecordingId = null
                                            }
                                            runCatching { repository.delete(recording) }
                                                .onSuccess { reload() }
                                                .onFailure { error ->
                                                    errorMessage = error.message ?: "Unable to delete recording"
                                                }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
