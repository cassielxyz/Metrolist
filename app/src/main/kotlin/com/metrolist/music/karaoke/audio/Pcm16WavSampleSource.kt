package com.metrolist.music.karaoke.audio

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Random-access PCM16 WAV reader that resamples bounded windows instead of loading a song into RAM. */
class Pcm16WavSampleSource(file: File) : Closeable {
    private val input = RandomAccessFile(file, "r")
    private val channels: Int
    val sampleRateHz: Int
    private val dataOffset: Long
    private val sourceFrameCount: Int

    init {
        require(file.isFile && file.length() >= 44L) { "WAV file is missing or too small" }
        require(input.readAscii(4) == "RIFF") { "Not a RIFF WAV file" }
        input.readLeUInt32()
        require(input.readAscii(4) == "WAVE") { "RIFF file is not WAVE audio" }

        var formatCode: Int? = null
        var channelCount: Int? = null
        var rate: Int? = null
        var bitsPerSample: Int? = null
        var foundDataOffset: Long? = null
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
                    rate = input.readLeUInt32().toInt()
                    input.readLeUInt32()
                    input.readLeUInt16()
                    bitsPerSample = input.readLeUInt16()
                }
                "data" -> {
                    foundDataOffset = chunkStart
                    dataBytes = chunkSize
                }
            }
            input.seek(chunkEnd + (chunkSize and 1L))
        }
        require(formatCode == 1) { "Only uncompressed PCM WAV is supported" }
        channels = requireNotNull(channelCount)
        require(channels == 1 || channels == 2) { "Only mono/stereo WAV is supported" }
        sampleRateHz = requireNotNull(rate)
        require(sampleRateHz in 8_000..384_000) { "Invalid WAV sample rate: $sampleRateHz" }
        require(bitsPerSample == 16) { "Only 16-bit PCM WAV is supported" }
        dataOffset = requireNotNull(foundDataOffset)
        val bytes = requireNotNull(dataBytes)
        val blockAlign = channels * 2L
        require(bytes % blockAlign == 0L) { "WAV data is not frame-aligned" }
        val frames = bytes / blockAlign
        require(frames <= Int.MAX_VALUE) { "WAV is too large to process" }
        sourceFrameCount = frames.toInt()
    }

    fun frameCountAt(targetSampleRateHz: Int): Int {
        require(targetSampleRateHz > 0)
        if (sourceFrameCount == 0) return 0
        return (sourceFrameCount.toDouble() * targetSampleRateHz / sampleRateHz)
            .roundToInt()
            .coerceAtLeast(1)
    }

    fun readResampled(
        targetStartFrame: Int,
        frameCount: Int,
        targetSampleRateHz: Int,
    ): Array<FloatArray> {
        require(targetStartFrame >= 0) { "Target start must be non-negative" }
        require(frameCount >= 0) { "Frame count must be non-negative" }
        require(targetSampleRateHz > 0) { "Target sample rate must be positive" }
        val output = Array(2) { FloatArray(frameCount) }
        if (frameCount == 0 || sourceFrameCount == 0) return output

        val totalTargetFrames = frameCountAt(targetSampleRateHz)
        if (targetStartFrame >= totalTargetFrames) return output
        val validCount = minOf(frameCount, totalTargetFrames - targetStartFrame)
        val ratio = sampleRateHz.toDouble() / targetSampleRateHz.toDouble()
        val firstSourcePosition = targetStartFrame * ratio
        val lastSourcePosition = (targetStartFrame + validCount - 1) * ratio
        val sourceStart = floor(firstSourcePosition).toInt().coerceIn(0, sourceFrameCount - 1)
        val sourceEnd = (ceil(lastSourcePosition).toInt() + 1).coerceIn(sourceStart, sourceFrameCount - 1)
        val source = readSourceFrames(sourceStart, sourceEnd - sourceStart + 1)

        repeat(validCount) { index ->
            val position = (targetStartFrame + index) * ratio
            val lower = floor(position).toInt().coerceIn(0, sourceFrameCount - 1)
            val upper = (lower + 1).coerceAtMost(sourceFrameCount - 1)
            val fraction = (position - lower).toFloat()
            val localLower = lower - sourceStart
            val localUpper = upper - sourceStart
            for (channel in 0..1) {
                val a = source[channel][localLower]
                val b = source[channel][localUpper]
                output[channel][index] = a * (1f - fraction) + b * fraction
            }
        }
        return output
    }

    override fun close() = input.close()

    private fun readSourceFrames(startFrame: Int, count: Int): Array<FloatArray> {
        val output = Array(2) { FloatArray(count) }
        if (count == 0) return output
        val bytesPerFrame = channels * 2L
        input.seek(dataOffset + startFrame * bytesPerFrame)
        repeat(count) { index ->
            val first = input.readLeInt16() / 32_768f
            val second = if (channels == 2) input.readLeInt16() / 32_768f else first
            output[0][index] = first
            output[1][index] = second
        }
        return output
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
        val value = readLeUInt16()
        return if (value and 0x8000 != 0) value - 0x1_0000 else value
    }

    private fun RandomAccessFile.readLeUInt32(): Long {
        val b0 = readUnsignedByte().toLong()
        val b1 = readUnsignedByte().toLong()
        val b2 = readUnsignedByte().toLong()
        val b3 = readUnsignedByte().toLong()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }
}
