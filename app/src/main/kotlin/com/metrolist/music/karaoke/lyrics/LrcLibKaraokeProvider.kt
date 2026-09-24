/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.karaoke.lyrics

import com.metrolist.lrclib.LrcLib
import com.metrolist.music.karaoke.domain.KaraokeLyricsProvider
import com.metrolist.music.karaoke.model.KaraokeLyrics
import com.metrolist.music.karaoke.model.KaraokeSongRef
import com.metrolist.music.karaoke.model.LyricsSource

/** Line-synced LRCLIB fallback used after a word-capable provider. */
class LrcLibKaraokeProvider : KaraokeLyricsProvider {
    override suspend fun getLyrics(
        song: KaraokeSongRef,
        durationMs: Long?,
    ): KaraokeLyrics? {
        if (song.title.isBlank() || song.artist.isBlank()) return null

        val lrc = LrcLib.getLyrics(
            title = song.title,
            artist = song.artist,
            duration = durationMs?.div(1_000L)?.toInt() ?: -1,
        ).getOrNull() ?: return null

        val lines = KaraokeLrcParser.parse(lrc)
        if (lines.isEmpty()) return null

        return KaraokeLyrics(
            source = LyricsSource.LRCLIB,
            lines = lines,
        )
    }
}
