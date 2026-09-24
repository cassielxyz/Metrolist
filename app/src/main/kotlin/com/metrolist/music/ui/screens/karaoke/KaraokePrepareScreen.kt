/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.ui.screens.karaoke

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metrolist.music.karaoke.domain.KaraokeAudioSourceResolver
import com.metrolist.music.karaoke.domain.KaraokeLyricsProvider
import com.metrolist.music.karaoke.lyrics.BetterLyricsKaraokeProvider
import com.metrolist.music.karaoke.lyrics.LocalLyricsImporter
import com.metrolist.music.karaoke.lyrics.LrcLibKaraokeProvider
import com.metrolist.music.karaoke.model.KaraokeLyrics
import com.metrolist.music.karaoke.model.KaraokeMode
import com.metrolist.music.karaoke.model.KaraokeSelectionStore
import com.metrolist.music.karaoke.model.KaraokeSession
import com.metrolist.music.karaoke.model.KaraokeSessionStore
import com.metrolist.music.karaoke.model.KaraokeSongRef
import com.metrolist.music.karaoke.model.KaraokeSource
import com.metrolist.music.karaoke.model.ResolvedKaraokeAudio
import com.metrolist.music.karaoke.model.SeparationQuality
import com.metrolist.music.karaoke.separator.AndroidMdxVocalSeparator
import com.metrolist.music.karaoke.separator.FileKaraokeStemCache
import com.metrolist.music.karaoke.separator.KaraVoxModelCatalog
import com.metrolist.music.karaoke.separator.KaraVoxModelManager
import com.metrolist.music.karaoke.separator.ModelInstallState
import com.metrolist.music.karaoke.source.LocalKaraokeSourceResolver
import com.metrolist.music.karaoke.source.LocalSongMetadataReader
import com.metrolist.music.karaoke.source.MetrolistOnlineKaraokeSourceResolver
import kotlinx.coroutines.launch
import java.io.File

private sealed interface SourcePreparationUiState {
    data object Resolving : SourcePreparationUiState
    data class Ready(val durationMs: Long?) : SourcePreparationUiState
    data class Failed(val message: String) : SourcePreparationUiState
}

private sealed interface LyricsPreparationUiState {
    data object Waiting : LyricsPreparationUiState
    data object Loading : LyricsPreparationUiState
    data class Ready(val lyrics: KaraokeLyrics) : LyricsPreparationUiState
    data class Missing(val reason: String) : LyricsPreparationUiState
}

private sealed interface SeparationUiState {
    data object Idle : SeparationUiState
    data class Running(val progress: Float) : SeparationUiState
    data class Ready(val session: KaraokeSession) : SeparationUiState
    data class Failed(val message: String) : SeparationUiState
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
    onStartKaraoke: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val coroutineScope = rememberCoroutineScope()
    val selectedSong = remember(songId) { KaraokeSelectionStore.get(songId) }
    val resolver: KaraokeAudioSourceResolver = remember(appContext, source) {
        when (source) {
            KaraokeSource.ONLINE -> MetrolistOnlineKaraokeSourceResolver(appContext)
            KaraokeSource.LOCAL -> LocalKaraokeSourceResolver()
        }
    }
    val lyricsProviders: List<KaraokeLyricsProvider> = remember {
        listOf(BetterLyricsKaraokeProvider(), LrcLibKaraokeProvider())
    }
    val modelManager = remember(appContext) { KaraVoxModelManager(appContext) }
    val separator = remember(appContext) { AndroidMdxVocalSeparator(appContext, modelManager) }

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
    var resolvedAudio by remember(songId, source, mediaUri) { mutableStateOf<ResolvedKaraokeAudio?>(null) }
    var currentSong by remember(songId, source, mediaUri) { mutableStateOf<KaraokeSongRef?>(null) }
    var sourceState: SourcePreparationUiState by remember(songId, source, mediaUri) {
        mutableStateOf(SourcePreparationUiState.Resolving)
    }
    var lyricsState: LyricsPreparationUiState by remember(songId) {
        mutableStateOf(LyricsPreparationUiState.Waiting)
    }
    var lyricsImportError by remember(songId) { mutableStateOf<String?>(null) }
    var separationQuality by remember { mutableStateOf(SeparationQuality.BALANCED) }
    var modelState: ModelInstallState by remember { mutableStateOf(ModelInstallState.Missing) }
    var modelDownloadProgress by remember { mutableFloatStateOf(0f) }
    var separationState: SeparationUiState by remember(songId) { mutableStateOf(SeparationUiState.Idle) }

    fun applyLyrics(lyrics: KaraokeLyrics) {
        lyricsImportError = null
        lyricsState = LyricsPreparationUiState.Ready(lyrics)
        val ready = separationState as? SeparationUiState.Ready
        if (ready != null) {
            val updated = ready.session.copy(lyrics = lyrics)
            KaraokeSessionStore.put(updated)
            separationState = SeparationUiState.Ready(updated)
        }
    }

    val lyricsPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { selectedUri ->
        if (selectedUri != null) {
            coroutineScope.launch {
                runCatching {
                    LocalLyricsImporter.import(appContext, selectedUri)
                }.onSuccess(::applyLyrics)
                    .onFailure { error ->
                        lyricsImportError = error.message ?: "Unable to import lyrics"
                    }
            }
        }
    }

    val modelSpec = remember(separationQuality) { KaraVoxModelCatalog.forQuality(separationQuality) }
    val stemCache = remember(appContext, modelSpec.id) {
        FileKaraokeStemCache(
            rootDir = File(appContext.filesDir, "karaoke/prepared-stems"),
            engineVersion = "mdx-${modelSpec.id}-v1",
        )
    }

    LaunchedEffect(modelSpec.id) {
        modelState = modelManager.inspect(modelSpec)
        modelDownloadProgress = 0f
        separationState = SeparationUiState.Idle
    }

    LaunchedEffect(songId, source, mediaUri, initialTitle, initialArtist, initialDurationMs) {
        sourceState = SourcePreparationUiState.Resolving
        lyricsState = LyricsPreparationUiState.Waiting
        lyricsImportError = null
        resolvedAudio = null
        currentSong = null
        separationState = SeparationUiState.Idle

        var effectiveTitle = initialTitle
        var effectiveArtist = initialArtist
        var effectiveDurationMs = initialDurationMs

        if (source == KaraokeSource.LOCAL && !mediaUri.isNullOrBlank()) {
            runCatching {
                LocalSongMetadataReader.read(appContext, mediaUri)
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
        currentSong = song

        runCatching { resolver.resolve(song) }
            .onSuccess { audio ->
                val finalDurationMs = audio.durationMs ?: effectiveDurationMs
                displayDurationMs = finalDurationMs
                resolvedAudio = audio
                sourceState = SourcePreparationUiState.Ready(finalDurationMs)

                if (effectiveTitle.isBlank() || effectiveArtist.isBlank()) {
                    lyricsState = LyricsPreparationUiState.Missing(
                        "This file does not contain enough title/artist metadata. You can still prepare its instrumental and import local synced lyrics.",
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
                        "No online synced lyrics found. Import an LRC/Enhanced-LRC/TTML file or continue without lyrics.",
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

    fun downloadSelectedModel() {
        if (modelState is ModelInstallState.Downloading) return
        coroutineScope.launch {
            modelDownloadProgress = 0f
            modelState = ModelInstallState.Downloading(0L, modelSpec.expectedBytes)
            runCatching {
                modelManager.install(modelSpec) { progress ->
                    modelState = progress
                    val total = progress.totalBytes
                    modelDownloadProgress = if (total != null && total > 0L) {
                        (progress.bytesRead.toFloat() / total).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
            }.onSuccess { file ->
                modelDownloadProgress = 1f
                modelState = ModelInstallState.Ready(file)
            }.onFailure { error ->
                modelState = ModelInstallState.Invalid(error.message ?: "Model download failed")
            }
        }
    }

    fun prepareInstrumental() {
        val audio = resolvedAudio ?: return
        val song = currentSong ?: return
        if (modelState !is ModelInstallState.Ready || separationState is SeparationUiState.Running) return

        coroutineScope.launch {
            separationState = SeparationUiState.Running(0f)
            runCatching {
                val cached = stemCache.get(audio, separationQuality)
                val stems = if (cached != null) {
                    separationState = SeparationUiState.Running(1f)
                    cached
                } else {
                    val separated = separator.separate(audio, separationQuality) { progress ->
                        separationState = SeparationUiState.Running(progress.coerceIn(0f, 1f))
                    }
                    stemCache.put(audio, separationQuality, separated)
                }
                KaraokeSession(
                    id = "${song.source.name.lowercase()}:${song.id}:${separationQuality.name.lowercase()}",
                    song = song,
                    mode = KaraokeMode.SOLO,
                    sourceAudio = audio,
                    stems = stems,
                    lyrics = (lyricsState as? LyricsPreparationUiState.Ready)?.lyrics,
                )
            }.onSuccess { session ->
                KaraokeSessionStore.put(session)
                separationState = SeparationUiState.Ready(session)
            }.onFailure { error ->
                separationState = SeparationUiState.Failed(
                    error.message ?: "Unable to separate vocals",
                )
            }
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

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = separationQuality == SeparationQuality.FAST,
                onClick = { separationQuality = SeparationQuality.FAST },
                label = { Text("Fast") },
                enabled = separationState !is SeparationUiState.Running,
            )
            FilterChip(
                selected = separationQuality == SeparationQuality.BALANCED,
                onClick = { separationQuality = SeparationQuality.BALANCED },
                label = { Text("Balanced") },
                enabled = separationState !is SeparationUiState.Running,
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
                PreparationLine(
                    label = "Separation model",
                    value = when (modelState) {
                        ModelInstallState.Missing -> "Download required"
                        is ModelInstallState.Downloading -> "Downloading…"
                        is ModelInstallState.Ready -> "Ready"
                        is ModelInstallState.Invalid -> "Needs repair"
                    },
                )
                PreparationLine(
                    label = "Vocal separation",
                    value = when (separationState) {
                        SeparationUiState.Idle -> "Ready to prepare"
                        is SeparationUiState.Running -> "Processing…"
                        is SeparationUiState.Ready -> "Ready"
                        is SeparationUiState.Failed -> "Failed"
                    },
                )
                PreparationLine(
                    label = "Synced lyrics",
                    value = when (val current = lyricsState) {
                        LyricsPreparationUiState.Waiting -> "Waiting"
                        LyricsPreparationUiState.Loading -> "Searching…"
                        is LyricsPreparationUiState.Ready -> current.lyrics.source.name.replace('_', ' ')
                        is LyricsPreparationUiState.Missing -> "Fallback available"
                    },
                )
                PreparationLine(
                    label = "Karaoke cache",
                    value = if (separationState is SeparationUiState.Ready) "Saved" else "Automatic",
                )
            }
        }

        when (val current = sourceState) {
            SourcePreparationUiState.Resolving -> CircularProgressIndicator()
            is SourcePreparationUiState.Ready -> Text(
                text = "Audio source ready for private on-device processing.",
                color = MaterialTheme.colorScheme.primary,
            )
            is SourcePreparationUiState.Failed -> Text(
                text = current.message,
                color = MaterialTheme.colorScheme.error,
            )
        }

        when (val current = modelState) {
            ModelInstallState.Missing -> Button(
                onClick = ::downloadSelectedModel,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Download ${modelSpec.displayName}")
            }
            is ModelInstallState.Invalid -> {
                Text(current.reason, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = ::downloadSelectedModel,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Repair model")
                }
            }
            is ModelInstallState.Downloading -> {
                LinearProgressIndicator(
                    progress = { modelDownloadProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = if (current.totalBytes != null && current.totalBytes > 0L) {
                        "Model ${(modelDownloadProgress * 100).toInt()}%"
                    } else {
                        "Downloading separation model…"
                    },
                )
            }
            is ModelInstallState.Ready -> Unit
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

        OutlinedButton(
            onClick = {
                lyricsPicker.launch(
                    arrayOf("text/*", "application/xml", "text/xml", "application/octet-stream"),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (lyricsState is LyricsPreparationUiState.Ready) "Replace lyrics file" else "Import LRC / TTML")
        }
        lyricsImportError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        when (val current = separationState) {
            SeparationUiState.Idle -> Unit
            is SeparationUiState.Running -> {
                LinearProgressIndicator(
                    progress = { current.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Preparing instrumental ${(current.progress * 100).toInt()}%")
            }
            is SeparationUiState.Ready -> Text(
                text = "Instrumental and vocal stems are ready and cached locally.",
                color = MaterialTheme.colorScheme.primary,
            )
            is SeparationUiState.Failed -> Text(current.message, color = MaterialTheme.colorScheme.error)
        }

        if (separationState !is SeparationUiState.Ready) {
            Button(
                onClick = ::prepareInstrumental,
                enabled = sourceState is SourcePreparationUiState.Ready &&
                    modelState is ModelInstallState.Ready &&
                    separationState !is SeparationUiState.Running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Prepare instrumental")
            }
        }

        val readySession = (separationState as? SeparationUiState.Ready)?.session
        Button(
            onClick = { readySession?.let { onStartKaraoke(it.id) } },
            enabled = readySession != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start karaoke")
        }
        OutlinedButton(
            onClick = onBack,
            enabled = separationState !is SeparationUiState.Running,
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
