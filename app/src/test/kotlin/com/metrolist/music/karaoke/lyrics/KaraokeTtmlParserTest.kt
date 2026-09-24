package com.metrolist.music.karaoke.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokeTtmlParserTest {
    @Test
    fun parsesLineTimingAndTimedSpans() {
        val lines = KaraokeTtmlParser.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="00:00:10.000" end="00:00:12.000">
                  <span begin="00:00:10.000" end="00:00:10.600">Hello</span>
                  <span begin="00:00:10.600">karaoke</span>
                </p>
                <p begin="12.5s" dur="2s">Next line</p>
              </div></body>
            </tt>
            """.trimIndent(),
        )

        assertEquals(2, lines.size)
        assertEquals("Hello karaoke", lines[0].text)
        assertEquals(10_000L, lines[0].startMs)
        assertEquals(12_000L, lines[0].endMs)
        assertEquals(2, lines[0].words.size)
        assertEquals("Hello", lines[0].words[0].text)
        assertEquals(10_600L, lines[0].words[0].endMs)
        assertEquals("karaoke", lines[0].words[1].text)
        assertEquals(12_000L, lines[0].words[1].endMs)
        assertEquals(12_500L, lines[1].startMs)
        assertEquals(14_500L, lines[1].endMs)
    }

    @Test
    fun parsesSupportedTimeExpressions() {
        assertEquals(1_500L, KaraokeTtmlParser.parseTime("1500ms"))
        assertEquals(1_500L, KaraokeTtmlParser.parseTime("1.5s"))
        assertEquals(90_000L, KaraokeTtmlParser.parseTime("1.5m"))
        assertEquals(3_600_000L, KaraokeTtmlParser.parseTime("1h"))
        assertEquals(62_250L, KaraokeTtmlParser.parseTime("00:01:02.250"))
        assertTrue(KaraokeTtmlParser.parseTime("frames:bad") == null)
    }

    @Test
    fun rejectsDoctypeAndExternalEntityInput() {
        val unsafe = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE tt [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div><p begin="0s" end="1s">&xxe;</p></div></body>
            </tt>
        """.trimIndent()

        assertTrue(runCatching { KaraokeTtmlParser.parse(unsafe) }.isFailure)
    }
}
