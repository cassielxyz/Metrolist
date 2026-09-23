package com.metrolist.music.karaoke.lyrics

data class TimedWord(val text: String, val startMs: Long, val endMs: Long)

data class TimedLine(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val words: List<TimedWord> = emptyList(),
    val singer: SingerPart = SingerPart.BOTH,
)

enum class SingerPart { PRIMARY, PARTNER, BOTH }

data class KaraokeLyrics(
    val lines: List<TimedLine>,
    val language: String? = null,
    val translation: List<TimedLine> = emptyList(),
    val source: String,
) {
    fun activeLine(positionMs: Long, offsetMs: Long = 0): TimedLine? {
        val adjusted = positionMs + offsetMs
        return lines.lastOrNull { adjusted >= it.startMs && adjusted < it.endMs }
    }
}

interface KaraokeLyricsProvider {
    val id: String
    suspend fun lyrics(songId: String, title: String, artist: String, durationMs: Long): KaraokeLyrics?
}
