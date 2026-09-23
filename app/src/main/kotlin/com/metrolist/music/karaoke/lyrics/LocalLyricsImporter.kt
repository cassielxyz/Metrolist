/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.lyrics

import android.content.Context
import android.net.Uri
import com.metrolist.music.karaoke.model.KaraokeLyrics
import com.metrolist.music.karaoke.model.LyricsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocalLyricsImporter {
    private const val MAX_LYRICS_BYTES = 2 * 1024 * 1024

    suspend fun import(
        context: Context,
        uri: Uri,
    ): KaraokeLyrics = withContext(Dispatchers.IO) {
        val resolver = context.applicationContext.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_LYRICS_BYTES) { "Lyrics file is larger than 2 MiB" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: error("Unable to open lyrics file")

        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        require(text.isNotBlank()) { "Lyrics file is empty" }
        val looksXml = uri.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase() in setOf("ttml", "xml") || text.trimStart().startsWith('<')

        val source: LyricsSource
        val lines = if (looksXml) {
            source = LyricsSource.LOCAL_TTML
            KaraokeTtmlParser.parse(text)
        } else {
            source = LyricsSource.LOCAL_LRC
            KaraokeLrcParser.parse(text)
        }
        require(lines.isNotEmpty()) { "No timed lyric lines were found" }
        KaraokeLyrics(source = source, lines = lines)
    }
}
