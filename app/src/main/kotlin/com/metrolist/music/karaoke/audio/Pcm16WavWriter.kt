/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.audio

import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/** Writes interleaved stereo 16-bit PCM WAV files without external codecs. */
object Pcm16WavWriter {
    fun write(file: File, audio: StereoPcmAudio): File {
        file.parentFile?.mkdirs()
        val channels = 2
        val bitsPerSample = 16
        val bytesPerSample = bitsPerSample / 8
        val dataSize = audio.sampleCount.toLong() * channels * bytesPerSample
        require(dataSize <= UInt.MAX_VALUE.toLong()) { "WAV output is too large for RIFF32" }

        DataOutputStream(BufferedOutputStream(FileOutputStream(file))).use { output ->
            output.writeAscii("RIFF")
            output.writeLeInt((36L + dataSize).toInt())
            output.writeAscii("WAVE")
            output.writeAscii("fmt ")
            output.writeLeInt(16)
            output.writeLeShort(1) // PCM
            output.writeLeShort(channels)
            output.writeLeInt(audio.sampleRateHz)
            output.writeLeInt(audio.sampleRateHz * channels * bytesPerSample)
            output.writeLeShort(channels * bytesPerSample)
            output.writeLeShort(bitsPerSample)
            output.writeAscii("data")
            output.writeLeInt(dataSize.toInt())

            for (index in 0 until audio.sampleCount) {
                output.writeLeShort(audio.left[index].toPcm16())
                output.writeLeShort(audio.right[index].toPcm16())
            }
        }
        check(file.isFile && file.length() == 44L + dataSize) {
            "KaraVox WAV writer produced an invalid file size"
        }
        return file
    }

    private fun Float.toPcm16(): Int =
        (coerceIn(-1f, 1f) * if (this >= 0f) 32_767f else 32_768f)
            .roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

    private fun DataOutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun DataOutputStream.writeLeShort(value: Int) {
        writeByte(value and 0xff)
        writeByte((value ushr 8) and 0xff)
    }

    private fun DataOutputStream.writeLeInt(value: Int) {
        writeByte(value and 0xff)
        writeByte((value ushr 8) and 0xff)
        writeByte((value ushr 16) and 0xff)
        writeByte((value ushr 24) and 0xff)
    }
}
