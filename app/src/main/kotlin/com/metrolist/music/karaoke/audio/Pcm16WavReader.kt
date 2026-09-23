/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.audio

import java.io.File
import java.io.RandomAccessFile

/**
 * Small RIFF/WAVE reader for KaraVox-generated PCM16 files.
 * Supports mono and stereo PCM and rejects compressed/unsupported WAV input instead of guessing.
 */
object Pcm16WavReader {
    fun read(file: File): StereoPcmAudio {
        require(file.isFile && file.length() >= 44L) { "WAV file is missing or too small" }

        RandomAccessFile(file, "r").use { input ->
            require(input.readAscii(4) == "RIFF") { "Not a RIFF WAV file" }
            input.readLeUInt32() // RIFF size
            require(input.readAscii(4) == "WAVE") { "RIFF file is not WAVE audio" }

            var formatCode: Int? = null
            var channelCount: Int? = null
            var sampleRate: Int? = null
            var bitsPerSample: Int? = null
            var dataOffset: Long? = null
            var dataBytes: Long? = null

            while (input.filePointer + 8L <= input.length()) {
                val chunkId = input.readAscii(4)
                val chunkSize = input.readLeUInt32()
                val chunkStart = input.filePointer
                val chunkEnd = chunkStart + chunkSize
                require(chunkEnd <= input.length()) { "Truncated WAV chunk: $chunkId" }

                when (chunkId) {
                    "fmt " -> {
                        require(chunkSize >= 16L) { "Invalid WAV fmt chunk" }
                        formatCode = input.readLeUInt16()
                        channelCount = input.readLeUInt16()
                        sampleRate = input.readLeUInt32().toInt()
                        input.readLeUInt32() // byte rate
                        input.readLeUInt16() // block align
                        bitsPerSample = input.readLeUInt16()
                    }
                    "data" -> {
                        dataOffset = chunkStart
                        dataBytes = chunkSize
                    }
                }

                // RIFF chunks are word-aligned.
                input.seek(chunkEnd + (chunkSize and 1L))
            }

            require(formatCode == 1) { "Only uncompressed PCM WAV is supported" }
            val channels = requireNotNull(channelCount) { "WAV channel count is missing" }
            require(channels == 1 || channels == 2) { "Only mono/stereo WAV is supported" }
            val rate = requireNotNull(sampleRate) { "WAV sample rate is missing" }
            require(rate in 8_000..384_000) { "Invalid WAV sample rate: $rate" }
            require(bitsPerSample == 16) { "Only 16-bit PCM WAV is supported" }
            val offset = requireNotNull(dataOffset) { "WAV data chunk is missing" }
            val byteCount = requireNotNull(dataBytes) { "WAV data size is missing" }
            val blockAlign = channels * 2L
            require(byteCount % blockAlign == 0L) { "WAV data is not frame-aligned" }
            val frameCountLong = byteCount / blockAlign
            require(frameCountLong <= Int.MAX_VALUE) { "WAV is too large to process" }
            val frameCount = frameCountLong.toInt()

            val left = FloatArray(frameCount)
            val right = FloatArray(frameCount)
            input.seek(offset)
            repeat(frameCount) { index ->
                val first = input.readLeInt16() / 32_768f
                val second = if (channels == 2) input.readLeInt16() / 32_768f else first
                left[index] = first.coerceIn(-1f, 1f)
                right[index] = second.coerceIn(-1f, 1f)
            }
            return StereoPcmAudio(rate, left, right)
        }
    }

    private fun RandomAccessFile.readAscii(length: Int): String {
        val bytes = ByteArray(length)
        readFully(bytes)
        return bytes.toString(Charsets.US_ASCII)
    }

    private fun RandomAccessFile.readLeUInt16(): Int {
        val b0 = readUnsignedByte()
        val b1 = readUnsignedByte()
        return b0 or (b1 shl 8)
    }

    private fun RandomAccessFile.readLeInt16(): Int {
        val unsigned = readLeUInt16()
        return if (unsigned and 0x8000 != 0) unsigned - 0x1_0000 else unsigned
    }

    private fun RandomAccessFile.readLeUInt32(): Long {
        val b0 = readUnsignedByte().toLong()
        val b1 = readUnsignedByte().toLong()
        val b2 = readUnsignedByte().toLong()
        val b3 = readUnsignedByte().toLong()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }
}
