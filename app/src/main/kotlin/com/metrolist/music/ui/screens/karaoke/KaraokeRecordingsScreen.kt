/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.karaoke

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import com.metrolist.music.karaoke.audio.KaraokeFileMixTrack
import com.metrolist.music.karaoke.audio.StreamingKaraokeMixer
import com.metrolist.music.karaoke.cache.KaraokeSessionRepository
import com.metrolist.music.karaoke.duet.KaraokeDuetPackage
import com.metrolist.music.karaoke.duet.WaveformDuetAlignmentEngine
import com.metrolist.music.karaoke.model.DuetSyncMetadata
import com.metrolist.music.karaoke.recording.KaraokeRecordingRepository
import com.metrolist.music.karaoke.recording.SavedKaraokeRecording
import com.metrolist.music.karaoke.settings.KaraokeDuetManualOffsetMsKey
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.text.DateFormat
import java.util.Date

@Composable
fun KaraokeRecordingsScreen() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(appContext) { KaraokeRecordingRepository(appContext) }
    val sessionRepository = remember(appContext) { KaraokeSessionRepository(appContext) }
    val duetPackage = remember(appContext) { KaraokeDuetPackage(appContext) }
    val alignmentEngine = remember { WaveformDuetAlignmentEngine() }
    val coroutineScope = rememberCoroutineScope()
    val player = remember { ExoPlayer.Builder(appContext).build() }
    var duetManualOffsetMs by rememberPreference(KaraokeDuetManualOffsetMsKey, 0L)

    var recordings by remember { mutableStateOf<List<SavedKaraokeRecording>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var activeRecordingId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pendingPackageExport by remember { mutableStateOf<SavedKaraokeRecording?>(null) }
    var pendingMixExport by remember { mutableStateOf<File?>(null) }
    var selectedDuetTake by remember { mutableStateOf<SavedKaraokeRecording?>(null) }

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

    val exportPackageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val recording = pendingPackageExport
        pendingPackageExport = null
        if (uri != null && recording != null) {
            coroutineScope.launch {
                runCatching {
                    appContext.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        duetPackage.export(recording, output)
                    } ?: error("Unable to open export destination")
                }.onSuccess { statusMessage = "Private duet take exported" }
                    .onFailure { errorMessage = it.message ?: "Unable to export duet take" }
            }
        }
    }

    val exportMixLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("audio/wav"),
    ) { uri ->
        val file = pendingMixExport
        pendingMixExport = null
        if (uri != null && file != null) {
            coroutineScope.launch(Dispatchers.IO) {
                runCatching {
                    appContext.contentResolver.openOutputStream(uri, "w")?.use { output ->
                        file.inputStream().buffered().use { input -> input.copyTo(output, 128 * 1024) }
                    } ?: error("Unable to open mix destination")
                }.onSuccess {
                    file.delete()
                    withContext(Dispatchers.Main) { statusMessage = "Karaoke mix exported" }
                }.onFailure { error ->
                    file.delete()
                    withContext(Dispatchers.Main) { errorMessage = error.message ?: "Unable to export mix" }
                }
            }
        } else {
            file?.delete()
        }
    }

    val importDuetLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                runCatching {
                    appContext.contentResolver.openInputStream(uri)?.use { input -> duetPackage.import(input) }
                        ?: error("Unable to open duet package")
                }.onSuccess {
                    statusMessage = "Duet take imported privately"
                    reload()
                }.onFailure { error -> errorMessage = error.message ?: "Unable to import duet take" }
            }
        }
    }

    fun exportPackage(recording: SavedKaraokeRecording) {
        pendingPackageExport = recording
        exportPackageLauncher.launch("${safeName(recording.title)}-${recording.take.id.take(8)}.karavox.zip")
    }

    fun renderSoloMix(recording: SavedKaraokeRecording) {
        val instrumentalUri = recording.instrumentalUri
        if (instrumentalUri.isNullOrBlank()) {
            errorMessage = "Instrumental is not stored with this imported take"
            return
        }
        coroutineScope.launch {
            statusMessage = "Rendering private mix…"
            runCatching {
                val instrumental = localFile(instrumentalUri) ?: error("Instrumental file is unavailable")
                val vocal = localFile(recording.take.vocalUri) ?: error("Vocal file is unavailable")
                val output = File(appContext.cacheDir, "karaoke/export/${recording.take.id}.mix.wav")
                withContext(Dispatchers.Default) {
                    StreamingKaraokeMixer.mix(
                        instrumentalFile = instrumental,
                        vocalTracks = listOf(KaraokeFileMixTrack(vocal, gain = 1f)),
                        outputFile = output,
                    )
                }
                output
            }.onSuccess { output ->
                pendingMixExport = output
                statusMessage = null
                exportMixLauncher.launch("${safeName(recording.title)}-karaoke.wav")
            }.onFailure { error ->
                statusMessage = null
                errorMessage = error.message ?: "Unable to render mix"
            }
        }
    }

    fun renderDuet(host: SavedKaraokeRecording, partner: SavedKaraokeRecording) {
        if (host.take.sessionId != partner.take.sessionId) {
            errorMessage = "Duet takes must come from the same prepared song"
            return
        }
        coroutineScope.launch {
            statusMessage = "Aligning and rendering duet…"
            runCatching {
                val session = sessionRepository.load(host.take.sessionId)
                    ?: error("Prepared song is required on this device to mix the duet")
                val hostFile = localFile(host.take.vocalUri) ?: error("Host vocal file is unavailable")
                val partnerFile = localFile(partner.take.vocalUri) ?: error("Partner vocal file is unavailable")
                val instrumental = localFile(session.stems.instrumentalUri)
                    ?: error("Prepared instrumental file is unavailable")
                val hostSync = host.take.syncMetadata ?: DuetSyncMetadata(0L)
                val partnerSync = partner.take.syncMetadata ?: DuetSyncMetadata(0L)
                val alignment = alignmentEngine.align(host.take, partner.take, hostSync, partnerSync)
                val finalPartnerOffsetMs = (alignment.partnerOffsetMs + duetManualOffsetMs)
                    .coerceIn(-10_000L, 10_000L)
                val output = File(appContext.cacheDir, "karaoke/export/${host.take.id}-${partner.take.id}.duet.wav")
                withContext(Dispatchers.Default) {
                    StreamingKaraokeMixer.mix(
                        instrumentalFile = instrumental,
                        vocalTracks = listOf(
                            KaraokeFileMixTrack(hostFile, gain = 0.92f),
                            KaraokeFileMixTrack(partnerFile, gain = 0.92f, offsetMs = finalPartnerOffsetMs),
                        ),
                        outputFile = output,
                    )
                }
                Triple(output, alignment, finalPartnerOffsetMs)
            }.onSuccess { (output, alignment, finalPartnerOffsetMs) ->
                selectedDuetTake = null
                pendingMixExport = output
                statusMessage = buildString {
                    append("Aligned with ${alignment.method}: ${alignment.partnerOffsetMs} ms")
                    if (duetManualOffsetMs != 0L) {
                        append(" • manual ${if (duetManualOffsetMs > 0L) "+" else ""}${duetManualOffsetMs} ms")
                    }
                    append(" • final ${finalPartnerOffsetMs} ms")
                }
                exportMixLauncher.launch("${safeName(host.title)}-duet.wav")
            }.onFailure { error ->
                statusMessage = null
                errorMessage = error.message ?: "Unable to render duet"
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Recordings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Private vocal takes stay on this device until you explicitly export them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { importDuetLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Import private duet take") }

        selectedDuetTake?.let { selected ->
            Text(
                "Duet host selected: ${selected.title}. Choose Duet on a second take from the same song.",
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Partner timing correction: ${if (duetManualOffsetMs >= 0L) "+" else ""}${duetManualOffsetMs} ms (change in Settings).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        when {
            loading -> Text("Loading recordings…")
            recordings.isEmpty() -> Text(
                "No recordings yet. Prepare a song, start karaoke, then tap Record.",
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
                                recording.title.ifBlank { "Karaoke recording" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (recording.artist.isNotBlank()) {
                                Text(recording.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "${recording.take.durationMs / 1_000}s • " +
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
                                ) { Text(if (activeRecordingId == recording.take.id && player.isPlaying) "Pause" else "Play") }
                                OutlinedButton(
                                    onClick = { exportPackage(recording) },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Export take") }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { renderSoloMix(recording) },
                                    modifier = Modifier.weight(1f),
                                    enabled = recording.instrumentalUri != null,
                                ) { Text("Mix solo") }
                                OutlinedButton(
                                    onClick = {
                                        val selected = selectedDuetTake
                                        if (selected == null || selected.take.id == recording.take.id) {
                                            selectedDuetTake = if (selected?.take?.id == recording.take.id) null else recording
                                        } else {
                                            renderDuet(selected, recording)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text(if (selectedDuetTake?.take?.id == recording.take.id) "Cancel duet" else "Duet") }
                            }
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        if (activeRecordingId == recording.take.id) {
                                            player.stop()
                                            activeRecordingId = null
                                        }
                                        if (selectedDuetTake?.take?.id == recording.take.id) selectedDuetTake = null
                                        runCatching { repository.delete(recording) }
                                            .onSuccess { reload() }
                                            .onFailure { errorMessage = it.message ?: "Unable to delete recording" }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
}

private fun localFile(uri: String): File? = runCatching {
    when {
        uri.startsWith("file:", ignoreCase = true) -> File(URI(uri))
        uri.startsWith("/") -> File(uri)
        else -> null
    }
}.getOrNull()

private fun safeName(value: String): String = value
    .ifBlank { "karavox" }
    .replace(Regex("[^A-Za-z0-9._-]+"), "-")
    .trim('-')
    .take(64)
    .ifBlank { "karavox" }
