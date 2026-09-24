/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Recording-first duet room shell.
 * Live microphone audio is intentionally not streamed between devices: each device records
 * locally and the two takes are aligned after the performance.
 */
@Composable
fun DuetRecordingRoomScreen(
    onBack: () -> Unit,
    onStartTogether: () -> Unit = {},
    onRecordLater: () -> Unit = {},
) {
    var manualOffsetMs by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Private duet room",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Both phones prepare the same instrumental, key, tempo and lyric timing. Each microphone is recorded locally for full quality.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Session status", fontWeight = FontWeight.SemiBold)
                StatusLine("You", "Not ready")
                StatusLine("Partner", "Waiting")
                StatusLine("Instrumental", "Not prepared")
                StatusLine("Lyrics", "Not prepared")
                StatusLine("Recording", "48 kHz local mic")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Post-record timing correction",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Automatic alignment will use the shared start marker and waveform correlation. Fine-tune the partner take if needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = manualOffsetMs,
                    onValueChange = { manualOffsetMs = it },
                    valueRange = -500f..500f,
                )
                Text(
                    text = "Manual partner offset: ${manualOffsetMs.roundToInt()} ms",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Button(
            onClick = onStartTogether,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Record together")
        }
        OutlinedButton(
            onClick = onRecordLater,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create record-later duet")
        }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Back")
        }

        Text(
            text = "No live voice stream. Network delay cannot damage the final duet because the vocal tracks are aligned after recording.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
