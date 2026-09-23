package com.metrolist.music.karaoke.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class StereoPcmAudioTest {
    @Test
    fun resamplingPreservesDurationAndStereoChannels() {
        val audio = StereoPcmAudio(
            sampleRateHz = 48_000,
            left = FloatArray(48_000) { it / 48_000f },
            right = FloatArray(48_000) { -(it / 48_000f) },
        )

        val resampled = audio.resample(44_100)

        assertEquals(44_100, resampled.sampleRateHz)
        assertEquals(44_100, resampled.sampleCount)
        assertEquals(1_000L, resampled.durationMs)
        assertTrue(resampled.left.last() > 0.99f)
        assertTrue(resampled.right.last() < -0.99f)
    }

    @Test
    fun wavWriterProducesValidStereoPcmHeaderAndData() {
        val directory = Files.createTempDirectory("karavox-wav-test").toFile()
        try {
            val file = directory.resolve("stem.wav")
            Pcm16WavWriter.write(
                file,
                StereoPcmAudio(
                    sampleRateHz = 44_100,
                    left = floatArrayOf(-1f, 0f, 1f),
                    right = floatArrayOf(1f, 0f, -1f),
                ),
            )

            val bytes = file.readBytes()
            assertEquals("RIFF", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
            assertEquals("WAVE", bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII))
            assertEquals("data", bytes.copyOfRange(36, 40).toString(Charsets.US_ASCII))
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(2, header.getShort(22).toInt())
            assertEquals(44_100, header.getInt(24))
            assertEquals(12, header.getInt(40))
            assertEquals(56, bytes.size)
        } finally {
            directory.deleteRecursively()
        }
    }
}
