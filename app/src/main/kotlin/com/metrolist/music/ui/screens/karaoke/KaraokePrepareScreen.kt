/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
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
import com.metrolist.music.karaoke.domain.KaraokeLyricsProvider
import com.metrolist.music.karaoke.lyrics.BetterLyricsKaraokeProvider
import com.metrolist.music.karaoke.lyrics.LrcLibKaraokeProvider
import com.metrolist.music.karaoke.model.KaraokeLyrics
import com.metrolist.music.karaoke.model.KaraokeSelectionStore
import com.metrolist.music.karaoke.model.KaraokeSongRef
import com.metrolist.music.karaoke.model.KaraokeSource
import com.metrolist.music.karaoke.source.LocalKaraokeSourceResolver
import com.metrolist.music.karaoke.source.LocalSongMetadataReader
import com.metrolist.music.karaoke.source.MetrolistOnlineKaraokeSourceResolver

private sealed interface SourcePreparationUiState {
    object Resolving : SourcePreparationUiState
    data class Ready(val durationMs: Long?) : SourcePreparationUiState
    data class Failed(val message: String) : SourcePreparationUiState
}

private sealed interface LyricsPreparationUiState {
    object Waiting : LyricsPreparationUiState
    object Loading : LyricsPreparationUiState
    data class Ready(val lyrics: KaraokeLyrics) : LyricsPreparationUiState
    data class Missing(val reason: String) : LyricsPreparationUiState
}

@Composable
fun KaraokePrepareScreen(
    songId: String,
    source: KaraokeSource,
    title: String? = null,
    artist: String? = null,
    durationSeconds: Int? = null,
    artworkUrl: String? = null,
    mediaUri: String? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val selectedSong = remember(songId) { KaraokeSelectionStore.get(songId) }
    val resolver: KaraokeAudioSourceResolver = remember(context, source) {
        when (source) {
            KaraokeSource.ONLINE -> MetrolistOnlineKaraokeSourceResolver(context.applicationContext)
            KaraokeSource.LOCAL -> LocalKaraokeSourceResolver()
        }
    }
    val lyricsProviders: List<KaraokeLyricsProvider> = remember {
        listOf(BetterLyricsKaraokeProvider(), LrcLibKaraokeProvider())
    }

    val fallbackName = remember(songId, source, mediaUri) {
        if (source == KaraokeSource.LOCAL && mediaUri != null) {
            Uri.parse(mediaUri).lastPathSegment ?: "Local song"
        } else {
            songId
        }
    }

    val initialTitle = title?.takeIf { it.isNotBlank() }
        ?: selectedSong?.title?.takeIf { it.isNotBlank() }
        ?: fallbackName
    val initialArtist = artist?.takeIf { it.isNotBlank() }
        ?: selectedSong?.artist.orEmpty()
    val initialDurationMs = durationSeconds
        ?.takeIf { it > 0 }
        ?.times(1_000L)
        ?: selectedSong?.durationMs
    val resolvedArtwork = artworkUrl?.takeIf { it.isNotBlank() } ?: selectedSong?.artworkUrl

    var displayTitle by remember(songId, initialTitle) { mutableStateOf(initialTitle) }
    var displayArtist by remember(songId, initialArtist) { mutableStateOf(initialArtist) }
    var displayDurationMs by remember(songId, initialDurationMs) { mutableStateOf(initialDurationMs) }
    var sourceState: SourcePreparationUiState by remember(songId, source, mediaUri) {
        mutableStateOf(SourcePreparationUiState.Resolving)
    }
    var lyricsState: LyricsPreparationUiState by remember(songId) {
        mutableStateOf(LyricsPreparationUiState.Waiting)
    }

    LaunchedEffect(songId, source, mediaUri, initialTitle, initialArtist, initialDurationMs) {
        sourceState = SourcePreparationUiState.Resolving
        lyricsState = LyricsPreparationUiState.Waiting

        var effectiveTitle = initialTitle
        var effectiveArtist = initialArtist
        var effectiveDurationMs = initialDurationMs

        if (source == KaraokeSource.LOCAL && !mediaUri.isNullOrBlank()) {
            runCatching {
                LocalSongMetadataReader.read(context.applicationContext, mediaUri)
            }.getOrNull()?.let { metadata ->
                effectiveTitle = metadata.title ?: effectiveTitle
                effectiveArtist = metadata.artist ?: effectiveArtist
                effectiveDurationMs = metadata.durationMs ?: effectiveDurationMs
                displayTitle = effectiveTitle
                displayArtist = effectiveArtist
                displayDurationMs = effectiveDurationMs
            }
        }

        val song = KaraokeSongRef(
            id = songId,
            title = effectiveTitle,
            artist = effectiveArtist,
            source = source,
            durationMs = effectiveDurationMs,
            artworkUrl = resolvedArtwork,
            mediaUri = mediaUri,
        )

        runCatching { resolver.resolve(song) }
            .onSuccess { resolvedAudio ->
                val finalDurationMs = resolvedAudio.durationMs ?: effectiveDurationMs
                displayDurationMs = finalDurationMs
                sourceState = SourcePreparationUiState.Ready(finalDurationMs)

                if (effectiveTitle.isBlank() || effectiveArtist.isBlank()) {
                    lyricsState = LyricsPreparationUiState.Missing(
                        "This file does not contain enough title/artist metadata. KaraVox will support local LRC/TTML import and manual matching as fallback.",
                    )
                    return@onSuccess
                }

                lyricsState = LyricsPreparationUiState.Loading
                var found: KaraokeLyrics? = null
                for (provider in lyricsProviders) {
                    found = runCatching {
                        provider.getLyrics(song, finalDurationMs)
                    }.getOrNull()
                    if (found != null) break
                }

                lyricsState = if (found != null) {
                    LyricsPreparationUiState.Ready(found)
                } else {
                    LyricsPreparationUiState.Missing(
                        "No synced lyrics found. KaraVox will allow local LRC/TTML import or manual sync.",
                    )
                }
            }
            .onFailure { error ->
                sourceState = SourcePreparationUiState.Failed(
                    error.message ?: "Unable to resolve this song",
                )
                lyricsState = LyricsPreparationUiState.Waiting
            }
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
            text = displayTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (displayArtist.isNotBlank()) {
            Text(
                text = displayArtist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        displayDurationMs?.let {
            Text(
                text = "${it / 1_000}s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PreparationLine(
                    label = if (source == KaraokeSource.ONLINE) "Online audio source" else "Local audio source",
                    value = when (sourceState) {
                        SourcePreparationUiState.Resolving -> "Resolving…"
                        is SourcePreparationUiState.Ready -> "Ready"
                        is SourcePreparationUiState.Failed -> "Failed"
                    },
                )
                PreparationLine(label = "Vocal separation", value = "Pending engine")
                PreparationLine(
                    label = "Synced lyrics",
                    value = when (val current = lyricsState) {
                        LyricsPreparationUiState.Waiting -> "Waiting"
                        LyricsPreparationUiState.Loading -> "Searching…"
                        is LyricsPreparationUiState.Ready -> current.lyrics.source.name.replace('_', ' ')
                        is LyricsPreparationUiState.Missing -> "Fallback needed"
                    },
                )
                PreparationLine(label = "Karaoke cache", value = "Pending")
            }
        }

        when (val current = sourceState) {
            SourcePreparationUiState.Resolving -> CircularProgressIndicator()
            is SourcePreparationUiState.Ready -> Text(
                text = "Audio source ready for local processing.",
                color = MaterialTheme.colorScheme.primary,
            )
            is SourcePreparationUiState.Failed -> Text(
                text = current.message,
                color = MaterialTheme.colorScheme.error,
            )
        }

        when (val current = lyricsState) {
            LyricsPreparationUiState.Loading -> Text("Finding the best synced lyrics…")
            is LyricsPreparationUiState.Ready -> {
                val wordCount = current.lyrics.lines.sumOf { it.words.size }
                Text(
                    text = if (wordCount > 0) {
                        "Lyrics ready • ${current.lyrics.lines.size} lines • $wordCount timed words"
                    } else {
                        "Lyrics ready • ${current.lyrics.lines.size} synced lines"
                    },
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            is LyricsPreparationUiState.Missing -> Text(
                text = current.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LyricsPreparationUiState.Waiting -> Unit
        }

        Text(
            text = "Start Karaoke stays disabled until KaraVox has a real separated instrumental. The original song is never presented as a fake karaoke track.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
