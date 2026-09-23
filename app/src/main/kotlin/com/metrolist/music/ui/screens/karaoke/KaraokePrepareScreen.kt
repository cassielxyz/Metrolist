/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.karaoke

import android.net.Uri
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
import com.metrolist.music.karaoke.domain.KaraokeAudioSourceResolver
import com.metrolist.music.karaoke.model.KaraokeSongRef
import com.metrolist.music.karaoke.model.KaraokeSource
import com.metrolist.music.karaoke.source.LocalKaraokeSourceResolver
import com.metrolist.music.karaoke.source.MetrolistOnlineKaraokeSourceResolver

private sealed interface SourcePreparationUiState {
    object Resolving : SourcePreparationUiState
    data class Ready(val durationMs: Long?) : SourcePreparationUiState
    data class Failed(val message: String) : SourcePreparationUiState
}

/**
 * First preparation stage shared by online and local karaoke items.
 * It verifies that the source can be resolved before expensive stem separation starts.
 */
@Composable
fun KaraokePrepareScreen(
    songId: String,
    source: KaraokeSource,
    mediaUri: String? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resolver: KaraokeAudioSourceResolver = remember(context, source) {
        when (source) {
            KaraokeSource.ONLINE -> MetrolistOnlineKaraokeSourceResolver(context.applicationContext)
            KaraokeSource.LOCAL -> LocalKaraokeSourceResolver()
        }
    }
    val displayName = remember(songId, source, mediaUri) {
        if (source == KaraokeSource.LOCAL && mediaUri != null) {
            Uri.parse(mediaUri).lastPathSegment ?: "Local song"
        } else {
            songId
        }
    }
    var state: SourcePreparationUiState by remember(songId, source, mediaUri) {
        mutableStateOf(SourcePreparationUiState.Resolving)
    }

    LaunchedEffect(songId, source, mediaUri) {
        state = SourcePreparationUiState.Resolving
        state = runCatching {
            resolver.resolve(
                KaraokeSongRef(
                    id = songId,
                    title = displayName,
                    artist = "",
                    source = source,
                    mediaUri = mediaUri,
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
            text = displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PreparationLine(
                    label = if (source == KaraokeSource.ONLINE) "Online audio source" else "Local audio source",
                    value = when (state) {
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
                    text = "The separation and lyric adapters are intentionally required before Start Karaoke is enabled; original audio is never mislabeled as an instrumental.",
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
