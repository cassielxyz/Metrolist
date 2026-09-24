package com.metrolist.music.karaoke.lyrics

import com.metrolist.music.karaoke.model.KaraokeLine
import com.metrolist.music.karaoke.model.KaraokeLyrics
import com.metrolist.music.karaoke.model.KaraokeWord
import com.metrolist.music.karaoke.model.LyricsSource
import java.io.File
import java.util.Base64

/** Small versioned codec for cached lyrics; text is Base64 encoded so arbitrary Unicode is safe. */
object KaraokeLyricsDiskCodec {
    private const val HEADER = "KARAVOX_LYRICS_V1"

    fun write(file: File, lyrics: KaraokeLyrics) {
        file.parentFile?.mkdirs()
        val encoder = Base64.getEncoder()
        file.bufferedWriter(Charsets.UTF_8).use { out ->
            out.appendLine(HEADER)
            out.appendLine("source=${lyrics.source.name}")
            out.appendLine("offset=${lyrics.globalOffsetMs}")
            lyrics.lines.forEachIndexed { lineIndex, line ->
                out.appendLine(
                    listOf("L", line.startMs, line.endMs, encode(encoder, line.text)).joinToString("\t"),
                )
                line.words.forEach { word ->
                    out.appendLine(
                        listOf("W", lineIndex, word.startMs, word.endMs, encode(encoder, word.text))
                            .joinToString("\t"),
                    )
                }
            }
        }
    }

    fun read(file: File): KaraokeLyrics? = runCatching {
        if (!file.isFile) return@runCatching null
        val lines = file.readLines(Charsets.UTF_8)
        if (lines.firstOrNull() != HEADER) return@runCatching null
        val source = lines.getOrNull(1)?.substringAfter("source=", "")
            ?.takeIf { it.isNotBlank() }
            ?.let(LyricsSource::valueOf)
            ?: return@runCatching null
        val offset = lines.getOrNull(2)?.substringAfter("offset=", "0")?.toLongOrNull() ?: 0L
        val decoder = Base64.getDecoder()
        val parsedLines = mutableListOf<KaraokeLine>()
        val wordsByLine = mutableMapOf<Int, MutableList<KaraokeWord>>()

        lines.drop(3).forEach { raw ->
            val parts = raw.split('\t')
            when (parts.firstOrNull()) {
                "L" -> if (parts.size == 4) {
                    val start = parts[1].toLongOrNull() ?: return@forEach
                    val end = parts[2].toLongOrNull() ?: return@forEach
                    parsedLines += KaraokeLine(
                        text = decode(decoder, parts[3]),
                        startMs = start,
                        endMs = end,
                    )
                }
                "W" -> if (parts.size == 5) {
                    val lineIndex = parts[1].toIntOrNull() ?: return@forEach
                    val start = parts[2].toLongOrNull() ?: return@forEach
                    val end = parts[3].toLongOrNull() ?: return@forEach
                    wordsByLine.getOrPut(lineIndex) { mutableListOf() } += KaraokeWord(
                        text = decode(decoder, parts[4]),
                        startMs = start,
                        endMs = end,
                    )
                }
            }
        }
        KaraokeLyrics(
            source = source,
            lines = parsedLines.mapIndexed { index, line ->
                line.copy(words = wordsByLine[index].orEmpty().sortedBy { it.startMs })
            },
            globalOffsetMs = offset,
        )
    }.getOrNull()

    private fun encode(encoder: Base64.Encoder, value: String): String =
        encoder.encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decode(decoder: Base64.Decoder, value: String): String =
        decoder.decode(value).toString(Charsets.UTF_8)
}
