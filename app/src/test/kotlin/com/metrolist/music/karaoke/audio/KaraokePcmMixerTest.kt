package com.metrolist.music.karaoke.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class KaraokePcmMixerTest {
    @Test
    fun positiveOffsetPlacesVocalLater() {
        val instrumental = StereoPcmAudio(
            sampleRateHz = 1_000,
            left = FloatArray(8),
            right = FloatArray(8),
        )
        val vocal = StereoPcmAudio(
            sampleRateHz = 1_000,
            left = floatArrayOf(0.5f, 0.25f),
            right = floatArrayOf(0.4f, 0.2f),
        )

        val mixed = KaraokePcmMixer.mixSolo(
            instrumental = instrumental,
            vocal = vocal,
            vocalOffsetMs = 3L,
        )

        assertEquals(0f, mixed.left[2], 0f)
        assertEquals(0.5f, mixed.left[3], 1e-6f)
        assertEquals(0.25f, mixed.left[4], 1e-6f)
    }

    @Test
    fun negativeOffsetTrimsEarlyVocalSamples() {
        val instrumental = StereoPcmAudio(
            sampleRateHz = 1_000,
            left = FloatArray(4),
            right = FloatArray(4),
        )
        val vocal = StereoPcmAudio(
            sampleRateHz = 1_000,
            left = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f),
            right = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f),
        )

        val mixed = KaraokePcmMixer.mixSolo(
            instrumental = instrumental,
            vocal = vocal,
            vocalOffsetMs = -2L,
        )

        assertEquals(0.3f, mixed.left[0], 1e-6f)
        assertEquals(0.4f, mixed.left[1], 1e-6f)
        assertEquals(0f, mixed.left[2], 1e-6f)
    }

    @Test
    fun resamplesVocalAndLimitsExportRange() {
        val instrumental = StereoPcmAudio(
            sampleRateHz = 4,
            left = floatArrayOf(0.8f, 0.8f, 0.8f, 0.8f),
            right = floatArrayOf(0.8f, 0.8f, 0.8f, 0.8f),
        )
        val vocal = StereoPcmAudio(
            sampleRateHz = 2,
            left = floatArrayOf(0.8f, 0.8f),
            right = floatArrayOf(0.8f, 0.8f),
        )

        val mixed = KaraokePcmMixer.mixSolo(instrumental, vocal)

        assertEquals(4, mixed.sampleCount)
        assertTrue(mixed.left.all { it in -1f..1f })
        assertEquals(1f, mixed.left[0], 0f)
    }

    @Test
    fun wavReaderLoadsMonoKaraVoxPcmAsDualMono() {
        val directory = Files.createTempDirectory("karavox-wav-reader").toFile()
        try {
            val stereoFile = directory.resolve("stereo.wav")
            Pcm16WavWriter.write(
                stereoFile,
                StereoPcmAudio(
                    sampleRateHz = 44_100,
                    left = floatArrayOf(-0.5f, 0f, 0.5f),
                    right = floatArrayOf(0.25f, 0f, -0.25f),
                ),
            )

            val audio = Pcm16WavReader.read(stereoFile)
            assertEquals(44_100, audio.sampleRateHz)
            assertEquals(3, audio.sampleCount)
            assertEquals(-0.5f, audio.left[0], 1e-3f)
            assertEquals(0.25f, audio.right[0], 1e-3f)
        } finally {
            directory.deleteRecursively()
        }
    }
}
