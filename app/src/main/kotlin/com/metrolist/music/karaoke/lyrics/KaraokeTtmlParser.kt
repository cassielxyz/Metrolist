/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.lyrics

import com.metrolist.music.karaoke.model.KaraokeLine
import com.metrolist.music.karaoke.model.KaraokeWord
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong

/**
 * Safe TTML parser for karaoke line timing and optional timed word/span timing.
 * External entities and DTD loading are disabled because imported lyric files are untrusted input.
 */
object KaraokeTtmlParser {
    fun parse(input: String): List<KaraokeLine> {
        if (input.isBlank()) return emptyList()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            setExpandEntityReferences(false)
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
        }
        val document = factory.newDocumentBuilder().parse(
            ByteArrayInputStream(input.toByteArray(Charsets.UTF_8)),
        )
        val paragraphs = document.getElementsByTagNameNS("*", "p")
        val result = mutableListOf<KaraokeLine>()

        for (index in 0 until paragraphs.length) {
            val paragraph = paragraphs.item(index) as? Element ?: continue
            val startMs = parseTime(paragraph.getAttribute("begin")) ?: continue
            val explicitEnd = parseTime(paragraph.getAttribute("end"))
            val duration = parseTime(paragraph.getAttribute("dur"))
            val words = parseTimedSpans(paragraph)
            val text = normalizeText(paragraph.textContent)
            result += KaraokeLine(
                text = text,
                startMs = startMs,
                endMs = explicitEnd ?: duration?.let { startMs + it } ?: startMs,
                words = words,
            )
        }

        val sorted = result.sortedBy { it.startMs }
        return sorted.mapIndexed { index, line ->
            val nextStart = sorted.getOrNull(index + 1)?.startMs
            val lineEnd = when {
                line.endMs > line.startMs -> line.endMs
                nextStart != null -> nextStart.coerceAtLeast(line.startMs + 1L)
                else -> line.startMs + 5_000L
            }
            val completedWords = line.words.mapIndexed { wordIndex, word ->
                val nextWordStart = line.words.getOrNull(wordIndex + 1)?.startMs
                word.copy(
                    endMs = when {
                        word.endMs > word.startMs -> word.endMs
                        nextWordStart != null -> nextWordStart.coerceAtLeast(word.startMs + 1L)
                        else -> lineEnd
                    },
                )
            }
            line.copy(endMs = lineEnd, words = completedWords)
        }
    }

    private fun parseTimedSpans(paragraph: Element): List<KaraokeWord> {
        val words = mutableListOf<KaraokeWord>()
        fun visit(node: Node) {
            if (node is Element && node.localName == "span") {
                val begin = parseTime(node.getAttribute("begin"))
                val end = parseTime(node.getAttribute("end"))
                val duration = parseTime(node.getAttribute("dur"))
                val token = normalizeText(node.textContent)
                if (begin != null && token.isNotBlank()) {
                    words += KaraokeWord(
                        text = token,
                        startMs = begin,
                        endMs = end ?: duration?.let { begin + it } ?: begin,
                    )
                    return
                }
            }
            val children = node.childNodes
            for (index in 0 until children.length) visit(children.item(index))
        }
        visit(paragraph)
        return words.sortedBy { it.startMs }
    }

    internal fun parseTime(value: String?): Long? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null

        if (raw.endsWith("ms", ignoreCase = true)) {
            return raw.dropLast(2).toDoubleOrNull()?.roundToLong()
        }
        if (raw.endsWith("s", ignoreCase = true)) {
            return raw.dropLast(1).toDoubleOrNull()?.times(1_000.0)?.roundToLong()
        }
        if (raw.endsWith("m", ignoreCase = true)) {
            return raw.dropLast(1).toDoubleOrNull()?.times(60_000.0)?.roundToLong()
        }
        if (raw.endsWith("h", ignoreCase = true)) {
            return raw.dropLast(1).toDoubleOrNull()?.times(3_600_000.0)?.roundToLong()
        }

        val parts = raw.split(':')
        if (parts.size == 3) {
            val hours = parts[0].toLongOrNull() ?: return null
            val minutes = parts[1].toLongOrNull() ?: return null
            val seconds = parts[2].toDoubleOrNull() ?: return null
            return (hours * 3_600_000L + minutes * 60_000L + seconds * 1_000.0).roundToLong()
        }
        if (parts.size == 2) {
            val minutes = parts[0].toLongOrNull() ?: return null
            val seconds = parts[1].toDoubleOrNull() ?: return null
            return (minutes * 60_000L + seconds * 1_000.0).roundToLong()
        }
        return raw.toDoubleOrNull()?.times(1_000.0)?.roundToLong()
    }

    private fun normalizeText(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .trim()
}
