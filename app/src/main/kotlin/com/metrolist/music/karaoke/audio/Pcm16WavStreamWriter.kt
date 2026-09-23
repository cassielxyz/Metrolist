package com.metrolist.music.karaoke.audio

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/** Bounded-memory stereo PCM16 WAV writer. RIFF sizes are finalized on close. */
class Pcm16WavStreamWriter(
    private val file: File,
    val sampleRateHz: Int,
) : Closeable {
    private val output: BufferedOutputStream
    private var frameCount = 0L
    private var closed = false

    init {
        require(sampleRateHz in 8_000..384_000) { "Invalid WAV sample rate: $sampleRateHz" }
        file.parentFile?.mkdirs()
        output = BufferedOutputStream(FileOutputStream(file, false), 128 * 1024)
        output.write(ByteArray(44))
    }

    fun write(left: FloatArray, right: FloatArray, start: Int = 0, count: Int = left.size - start) {
        check(!closed) { "WAV writer is closed" }
        require(left.size == right.size) { "Stereo channels must have equal lengths" }
        require(start >= 0 && count >= 0 && start + count <= left.size) { "Invalid WAV write range" }
        repeat(count) { index ->
            writePcm16(left[start + index])
            writePcm16(right[start + index])
        }
        frameCount += count
    }

    fun writeFrame(left: Float, right: Float) {
        check(!closed) { "WAV writer is closed" }
        writePcm16(left)
        writePcm16(right)
        frameCount += 1
    }

    override fun close() {
        if (closed) return
        closed = true
        output.flush()
        output.close()

        val dataSize = frameCount * 4L
        require(dataSize <= UInt.MAX_VALUE.toLong()) { "WAV output is too large for RIFF32" }
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0L)
            raf.writeAscii("RIFF")
            raf.writeLeInt((36L + dataSize).toInt())
            raf.writeAscii("WAVE")
            raf.writeAscii("fmt ")
            raf.writeLeInt(16)
            raf.writeLeShort(1)
            raf.writeLeShort(2)
            raf.writeLeInt(sampleRateHz)
            raf.writeLeInt(sampleRateHz * 4)
            raf.writeLeShort(4)
            raf.writeLeShort(16)
            raf.writeAscii("data")
            raf.writeLeInt(dataSize.toInt())
        }
        check(file.isFile && file.length() == 44L + dataSize) { "Invalid finalized WAV length" }
    }

    private fun writePcm16(sample: Float) {
        val value = (sample.coerceIn(-1f, 1f) * if (sample >= 0f) 32_767f else 32_768f)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        output.write(value and 0xff)
        output.write((value ushr 8) and 0xff)
    }

    private fun RandomAccessFile.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun RandomAccessFile.writeLeShort(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
    }

    private fun RandomAccessFile.writeLeInt(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
        write((value ushr 16) and 0xff)
        write((value ushr 24) and 0xff)
    }
}
