/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.karaoke.model.RecordingQuality
import com.metrolist.music.karaoke.model.SeparationQuality
import com.metrolist.music.karaoke.settings.KaraokeAutoAlignDuetsKey
import com.metrolist.music.karaoke.settings.KaraokeCompressorKey
import com.metrolist.music.karaoke.settings.KaraokeCountdownSecondsKey
import com.metrolist.music.karaoke.settings.KaraokeDefaultVocalMixKey
import com.metrolist.music.karaoke.settings.KaraokeEchoCancellationKey
import com.metrolist.music.karaoke.settings.KaraokeKeepProcessedStemsKey
import com.metrolist.music.karaoke.settings.KaraokeLyricsOffsetMsKey
import com.metrolist.music.karaoke.settings.KaraokeNoiseSuppressionKey
import com.metrolist.music.karaoke.settings.KaraokeRecordingQualityKey
import com.metrolist.music.karaoke.settings.KaraokeSaveIndividualStemsKey
import com.metrolist.music.karaoke.settings.KaraokeSeparationQualityKey
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaraokeSettingsScreen(navController: NavController) {
    val (separationQuality, setSeparationQuality) = rememberEnumPreference(
        KaraokeSeparationQualityKey,
        SeparationQuality.BALANCED,
    )
    val (recordingQuality, setRecordingQuality) = rememberEnumPreference(
        KaraokeRecordingQualityKey,
        RecordingQuality.HIGH,
    )
    val (vocalMix, setVocalMix) = rememberPreference(KaraokeDefaultVocalMixKey, 0f)
    val (lyricsOffsetMs, setLyricsOffsetMs) = rememberPreference(KaraokeLyricsOffsetMsKey, 0L)
    val (countdownSeconds, setCountdownSeconds) = rememberPreference(KaraokeCountdownSecondsKey, 3)
    val (saveStems, setSaveStems) = rememberPreference(KaraokeSaveIndividualStemsKey, true)
    val (noiseSuppression, setNoiseSuppression) = rememberPreference(KaraokeNoiseSuppressionKey, true)
    val (echoCancellation, setEchoCancellation) = rememberPreference(KaraokeEchoCancellationKey, true)
    val (compressor, setCompressor) = rememberPreference(KaraokeCompressorKey, true)
    val (autoAlignDuets, setAutoAlignDuets) = rememberPreference(KaraokeAutoAlignDuetsKey, true)
    val (keepProcessedStems, setKeepProcessedStems) = rememberPreference(KaraokeKeepProcessedStemsKey, true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Karaoke & recording") },
                navigationIcon = {
                    TextButton(onClick = navController::navigateUp) {
                        Text("Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsHeading("Karaoke processing")
            Button(
                onClick = {
                    val values = SeparationQuality.entries
                    setSeparationQuality(values[(separationQuality.ordinal + 1) % values.size])
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Vocal separation: ${separationQuality.name.lowercase().replaceFirstChar { it.uppercase() }}")
            }

            Text("Practice vocal mix: ${(vocalMix * 100).roundToInt()}%")
            Slider(
                value = vocalMix,
                onValueChange = setVocalMix,
                valueRange = 0f..1f,
            )

            Text("Lyrics timing offset: ${lyricsOffsetMs} ms")
            Slider(
                value = lyricsOffsetMs.toFloat(),
                onValueChange = { setLyricsOffsetMs(it.roundToInt().toLong()) },
                valueRange = -1_000f..1_000f,
            )

            Text("Countdown: $countdownSeconds seconds")
            Slider(
                value = countdownSeconds.toFloat(),
                onValueChange = { setCountdownSeconds(it.roundToInt()) },
                valueRange = 0f..5f,
                steps = 4,
            )

            SettingsHeading("Recording")
            Button(
                onClick = {
                    val values = RecordingQuality.entries
                    setRecordingQuality(values[(recordingQuality.ordinal + 1) % values.size])
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Recording quality: ${recordingQuality.name.replace('_', ' ')}")
            }
            SettingsSwitch("Save individual vocal/instrumental stems", saveStems, setSaveStems)
            SettingsSwitch("Noise suppression", noiseSuppression, setNoiseSuppression)
            SettingsSwitch("Echo cancellation", echoCancellation, setEchoCancellation)
            SettingsSwitch("Compressor", compressor, setCompressor)

            SettingsHeading("Private duets")
            SettingsSwitch("Automatic post-record alignment", autoAlignDuets, setAutoAlignDuets)
            Text(
                text = "Automatic alignment uses recording timestamps, a shared sync marker and waveform matching. A manual millisecond trim remains available after recording.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsHeading("Storage")
            SettingsSwitch("Keep processed karaoke stems", keepProcessedStems, setKeepProcessedStems)
            Text(
                text = "Prepared stems are reusable, so vocal separation only needs to run again when the model/version or source changes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingsSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .padding(end = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
