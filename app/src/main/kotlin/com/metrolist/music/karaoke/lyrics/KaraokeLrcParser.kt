/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.karaoke.lyrics

import com.metrolist.music.karaoke.model.KaraokeLine
import com.metrolist.music.karaoke.model.KaraokeWord

/** Parser for regular LRC and the common Enhanced-LRC `<mm:ss.xx>` word timestamp form. */
object KaraokeLrcParser {
    private val lineTimestamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordTimestamp = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")

    fun parse(input: String): List<KaraokeLine> {
        val parsed = buildList {
            input.lineSequence().forEach { rawLine ->
                val timestamps = lineTimestamp.findAll(rawLine).toList()
                if (timestamps.isEmpty()) return@forEach

                val textStart = timestamps.last().range.last + 1
                val timedText = rawLine.substring(textStart).trim()
                timestamps.forEach { match ->
                    val startMs = match.toMillis()
                    val words = parseWords(timedText)
                    val plainText = if (words.isEmpty()) {
                        timedText
                    } else {
                        wordTimestamp.replace(timedText, "").trim()
                    }
                    add(
                        KaraokeLine(
                            text = plainText,
                            startMs = startMs,
                            endMs = startMs,
                            words = words,
                        ),
                    )
                }
            }
        }.sortedBy { it.startMs }

        return parsed.mapIndexed { index, line ->
            val nextStart = parsed.getOrNull(index + 1)?.startMs
            val fallbackEnd = line.startMs + 5_000L
            val lineEnd = nextStart?.coerceAtLeast(line.startMs + 1L) ?: fallbackEnd
            val completedWords = if (line.words.isEmpty()) {
                emptyList()
            } else {
                line.words.mapIndexed { wordIndex, word ->
                    val nextWordStart = line.words.getOrNull(wordIndex + 1)?.startMs
                    word.copy(
                        endMs = nextWordStart?.coerceAtLeast(word.startMs + 1L) ?: lineEnd,
                    )
                }
            }
            line.copy(endMs = lineEnd, words = completedWords)
        }
    }

    private fun parseWords(timedText: String): List<KaraokeWord> {
        val matches = wordTimestamp.findAll(timedText).toList()
        if (matches.isEmpty()) return emptyList()

        return matches.mapIndexedNotNull { index, match ->
            val start = match.range.last + 1
            val endExclusive = matches.getOrNull(index + 1)?.range?.first ?: timedText.length
            val token = timedText.substring(start, endExclusive).trim()
            if (token.isBlank()) {
                null
            } else {
                KaraokeWord(
                    text = token,
                    startMs = match.toMillis(),
                    endMs = match.toMillis(),
                )
            }
        }
    }

    private fun MatchResult.toMillis(): Long {
        val minutes = groupValues[1].toLongOrNull() ?: 0L
        val seconds = groupValues[2].toLongOrNull() ?: 0L
        val fraction = groupValues[3]
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLongOrNull()?.times(100L) ?: 0L
            2 -> fraction.toLongOrNull()?.times(10L) ?: 0L
            else -> fraction.take(3).toLongOrNull() ?: 0L
        }
        return minutes * 60_000L + seconds * 1_000L + fractionMs
    }
}
