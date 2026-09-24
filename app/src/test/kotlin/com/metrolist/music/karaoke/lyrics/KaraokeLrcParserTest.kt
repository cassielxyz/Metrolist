package com.metrolist.music.karaoke.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeLrcParserTest {
    @Test
    fun parsesRegularLrcAndUsesNextLineAsEndTime() {
        val lines = KaraokeLrcParser.parse(
            """
            [00:01.50]First line
            [00:04.00]Second line
            """.trimIndent(),
        )

        assertEquals(2, lines.size)
        assertEquals("First line", lines[0].text)
        assertEquals(1_500L, lines[0].startMs)
        assertEquals(4_000L, lines[0].endMs)
        assertEquals("Second line", lines[1].text)
        assertEquals(4_000L, lines[1].startMs)
        assertEquals(9_000L, lines[1].endMs)
    }

    @Test
    fun parsesEnhancedLrcWordTiming() {
        val lines = KaraokeLrcParser.parse(
            """
            [00:10.00]<00:10.00>Hello <00:10.60>karaoke
            [00:12.00]Next
            """.trimIndent(),
        )

        assertEquals(2, lines.size)
        val first = lines.first()
        assertEquals("Hello karaoke", first.text)
        assertEquals(2, first.words.size)
        assertEquals("Hello", first.words[0].text)
        assertEquals(10_000L, first.words[0].startMs)
        assertEquals(10_600L, first.words[0].endMs)
        assertEquals("karaoke", first.words[1].text)
        assertEquals(10_600L, first.words[1].startMs)
        assertEquals(12_000L, first.words[1].endMs)
    }

    @Test
    fun acceptsMultipleLineTimestampsAndSortsOutput() {
        val lines = KaraokeLrcParser.parse(
            """
            [00:08.0]Later
            [00:02.00][00:04.000]Repeat
            """.trimIndent(),
        )

        assertEquals(listOf(2_000L, 4_000L, 8_000L), lines.map { it.startMs })
        assertEquals(listOf("Repeat", "Repeat", "Later"), lines.map { it.text })
        assertTrue(lines.zipWithNext().all { (a, b) -> a.endMs == b.startMs })
    }
}
