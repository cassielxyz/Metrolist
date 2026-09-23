/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.karaoke

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metrolist.music.karaoke.model.KaraokeLine
import com.metrolist.music.karaoke.model.KaraokeLyrics

/**
 * Karaoke-first fullscreen lyric surface.
 * Word timing is rendered when available; line timing remains the fallback.
 */
@Composable
fun KaraokePlayerScreen(
    lyrics: KaraokeLyrics?,
    positionMs: Long,
    isPlaying: Boolean,
    vocalMix: Float,
    onTogglePlayback: () -> Unit,
    onRestart: () -> Unit,
    onRecord: () -> Unit,
) {
    val adjustedPosition = positionMs + (lyrics?.globalOffsetMs ?: 0L)
    val lines = lyrics?.lines.orEmpty()
    val currentIndex = lines.indexOfLast { adjustedPosition >= it.startMs }
        .coerceAtLeast(0)
        .coerceAtMost((lines.size - 1).coerceAtLeast(0))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        if (lines.isEmpty()) {
            Text(
                text = "Synced lyrics are not available yet",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                lines.getOrNull(currentIndex - 1)?.let { line ->
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.45f),
                    )
                }

                KaraokeCurrentLine(
                    line = lines[currentIndex],
                    positionMs = adjustedPosition,
                )

                lines.getOrNull(currentIndex + 1)?.let { line ->
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Practice vocal mix ${(vocalMix.coerceIn(0f, 1f) * 100).toInt()}%",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Restart")
                }
                Button(
                    onClick = onTogglePlayback,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isPlaying) "Pause" else "Play")
                }
                OutlinedButton(
                    onClick = onRecord,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Record")
                }
            }
        }
    }
}

@Composable
private fun KaraokeCurrentLine(
    line: KaraokeLine,
    positionMs: Long,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val futureColor = Color.White

    if (line.words.isEmpty()) {
        val lineActive = positionMs >= line.startMs
        Text(
            text = line.text,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (lineActive) activeColor else futureColor,
        )
        return
    }

    val highlighted = buildAnnotatedString {
        line.words.forEachIndexed { index, word ->
            val color = if (positionMs >= word.startMs) activeColor else futureColor
            pushStyle(
                SpanStyle(
                    color = color,
                    fontWeight = FontWeight.Bold,
                ),
            )
            append(word.text)
            pop()
            if (index != line.words.lastIndex) append(" ")
        }
    }

    Text(
        text = highlighted,
        style = MaterialTheme.typography.headlineMedium,
    )
}
