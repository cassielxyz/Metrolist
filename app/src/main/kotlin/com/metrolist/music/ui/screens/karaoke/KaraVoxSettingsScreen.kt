package com.metrolist.music.ui.screens.karaoke

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.metrolist.music.karaoke.cache.KaraVoxStorageManager
import com.metrolist.music.karaoke.cache.KaraokeStorageUsage
import com.metrolist.music.karaoke.model.KaraokeSessionStore
import com.metrolist.music.karaoke.model.SeparationQuality
import com.metrolist.music.karaoke.settings.KaraokeCountdownSecondsKey
import com.metrolist.music.karaoke.settings.KaraokeDefaultVocalMixKey
import com.metrolist.music.karaoke.settings.KaraokeDuetManualOffsetMsKey
import com.metrolist.music.karaoke.settings.KaraokeSeparationQualityKey
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun KaraVoxSettingsScreen() {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) { KaraVoxStorageManager(context) }
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<KaraokeStorageUsage?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var quality by rememberEnumPreference(KaraokeSeparationQualityKey, SeparationQuality.BALANCED)
    var defaultVocalMix by rememberPreference(KaraokeDefaultVocalMixKey, 0f)
    var countdownSeconds by rememberPreference(KaraokeCountdownSecondsKey, 3)
    var duetManualOffsetMs by rememberPreference(KaraokeDuetManualOffsetMsKey, 0L)

    fun refresh() {
        scope.launch { usage = manager.usage() }
    }
    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("KaraVox settings", style = MaterialTheme.typography.headlineMedium)

        Text("Separation quality", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(
                selected = quality == SeparationQuality.FAST,
                onClick = { quality = SeparationQuality.FAST },
                label = { Text("Fast") },
            )
            FilterChip(
                selected = quality == SeparationQuality.BALANCED,
                onClick = { quality = SeparationQuality.BALANCED },
                label = { Text("Balanced") },
            )
        }
        Text(
            if (quality == SeparationQuality.FAST) {
                "Smaller model and faster preparation."
            } else {
                "Higher-quality instrumental separation; recommended on modern phones."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Default vocal guide ${(defaultVocalMix * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = defaultVocalMix.coerceIn(0f, 1f),
            onValueChange = { defaultVocalMix = it.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Recording countdown", style = MaterialTheme.typography.titleMedium)
        Text("$countdownSeconds seconds")
        Slider(
            value = countdownSeconds.toFloat().coerceIn(0f, 5f),
            onValueChange = { countdownSeconds = it.roundToInt().coerceIn(0, 5) },
            valueRange = 0f..5f,
            steps = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Duet timing correction", style = MaterialTheme.typography.titleLarge)
        Text(
            "Partner track ${if (duetManualOffsetMs >= 0L) "+" else ""}${duetManualOffsetMs} ms",
            style = MaterialTheme.typography.titleMedium,
        )
        Slider(
            value = duetManualOffsetMs.toFloat().coerceIn(-1_000f, 1_000f),
            onValueChange = { duetManualOffsetMs = it.roundToLong() },
            valueRange = -1_000f..1_000f,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { duetManualOffsetMs = (duetManualOffsetMs - 10L).coerceAtLeast(-1_000L) }) { Text("-10 ms") }
            OutlinedButton(onClick = { duetManualOffsetMs = 0L }) { Text("Reset") }
            OutlinedButton(onClick = { duetManualOffsetMs = (duetManualOffsetMs + 10L).coerceAtMost(1_000L) }) { Text("+10 ms") }
        }
        Text(
            "This correction is applied after KaraVox automatic duet alignment, so you can fine-tune the final timing between both singers.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Storage", style = MaterialTheme.typography.titleLarge)
        usage?.let {
            Text("Prepared songs: ${formatBytes(it.preparedBytes)}")
            Text("Separation models: ${formatBytes(it.modelBytes)}")
            Text("Recordings: ${formatBytes(it.recordingBytes)}")
            Text("Temporary source cache: ${formatBytes(it.transientBytes)}")
            Text("Total KaraVox data: ${formatBytes(it.totalBytes)}")
        } ?: Text("Calculating storage…")

        OutlinedButton(
            onClick = {
                scope.launch {
                    manager.clearTransient()
                    status = "Temporary source cache cleared"
                    refresh()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Clear temporary cache") }
        OutlinedButton(
            onClick = {
                scope.launch {
                    manager.clearPrepared()
                    KaraokeSessionStore.clear()
                    status = "Prepared stems and saved sessions cleared"
                    refresh()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Clear prepared songs") }
        OutlinedButton(
            onClick = {
                scope.launch {
                    manager.clearModels()
                    status = "Downloaded separation models cleared"
                    refresh()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Clear separation models") }
        Button(
            onClick = ::refresh,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Refresh storage") }
        status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Text(
            "Privacy: separation and microphone recording run locally. KaraVox has no social feed, followers, or automatic recording uploads.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
