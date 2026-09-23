/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.karaoke

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metrolist.music.karaoke.model.KaraokeSongRef
import com.metrolist.music.karaoke.model.KaraokeSource
import com.metrolist.music.karaoke.source.MetrolistOnlineKaraokeSourceResolver

private sealed interface SourcePreparationUiState {
    object Resolving : SourcePreparationUiState
    data class Ready(val durationMs: Long?) : SourcePreparationUiState
    data class Failed(val message: String) : SourcePreparationUiState
}

/**
 * First preparation stage for an online karaoke item.
 * It verifies the existing Metrolist playback resolver before expensive separation starts.
 */
@Composable
fun KaraokePrepareScreen(
    videoId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resolver = remember(context) {
        MetrolistOnlineKaraokeSourceResolver(context.applicationContext)
    }
    var state: SourcePreparationUiState by remember(videoId) {
        mutableStateOf(SourcePreparationUiState.Resolving)
    }

    LaunchedEffect(videoId) {
        state = SourcePreparationUiState.Resolving
        state = runCatching {
            resolver.resolve(
                KaraokeSongRef(
                    id = videoId,
                    title = videoId,
                    artist = "",
                    source = KaraokeSource.ONLINE,
                ),
            )
        }.fold(
            onSuccess = { SourcePreparationUiState.Ready(it.durationMs) },
            onFailure = {
                SourcePreparationUiState.Failed(
                    it.message ?: "Unable to resolve this song",
                )
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Prepare karaoke",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Song ID: $videoId",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PreparationLine(
                    label = "Online audio source",
                    value = when (val current = state) {
                        SourcePreparationUiState.Resolving -> "Resolving…"
                        is SourcePreparationUiState.Ready -> "Ready"
                        is SourcePreparationUiState.Failed -> "Failed"
                    },
                )
                PreparationLine(label = "Vocal separation", value = "Pending engine")
                PreparationLine(label = "Synced lyrics", value = "Pending provider adapter")
                PreparationLine(label = "Karaoke cache", value = "Pending")
            }
        }

        when (val current = state) {
            SourcePreparationUiState.Resolving -> CircularProgressIndicator()
            is SourcePreparationUiState.Ready -> {
                Text(
                    text = current.durationMs?.let { "Source resolved • ${it / 1_000}s" }
                        ?: "Source resolved and ready for local processing.",
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "The next implementation slice connects the vocal-separation and lyric adapters. The app does not pretend the original audio is an instrumental.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is SourcePreparationUiState.Failed -> {
                Text(
                    text = current.message,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Button(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start karaoke")
        }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Back")
        }
    }
}

@Composable
private fun PreparationLine(label: String, value: String) {
    Text(
        text = "$label  •  $value",
        style = MaterialTheme.typography.bodyLarge,
    )
}
