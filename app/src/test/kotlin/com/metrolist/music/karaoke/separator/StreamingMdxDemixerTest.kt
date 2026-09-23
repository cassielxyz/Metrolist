package com.metrolist.music.karaoke.separator

import com.metrolist.music.karaoke.audio.Pcm16WavReader
import com.metrolist.music.karaoke.audio.Pcm16WavStreamWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class StreamingMdxDemixerTest {
    @Test
    fun identityModelStreamsVocalPredictionWithoutGrowingWithSongLength() {
        val root = createTempDir(prefix = "karavox-streaming-mdx-")
        try {
            val sourceFile = File(root, "source.wav")
            val length = 2_137
            val left = FloatArray(length) { index ->
                (0.32 * sin(2.0 * PI * 29.0 * index / 512.0)).toFloat()
            }
            val right = FloatArray(length) { index ->
                (0.25 * sin(2.0 * PI * 41.0 * index / 512.0)).toFloat()
            }
            Pcm16WavStreamWriter(sourceFile, 44_100).use { it.write(left, right) }
            val quantizedSource = Pcm16WavReader.read(sourceFile)

            val spec = MdxDspSpec(
                nFft = 64,
                hopLength = 16,
                dimF = 33,
                dimT = 17,
                compensation = 1f,
                predictedStem = PredictedStem.VOCALS,
                overlap = 0.25f,
            )
            val result = StreamingMdxDemixer(spec) { tensor -> tensor.copyOf() }
                .separate(sourceFile, File(root, "out"), 44_100)
            val vocals = Pcm16WavReader.read(result.vocals)
            val instrumental = Pcm16WavReader.read(result.instrumental)

            assertEquals(length, vocals.sampleCount)
            assertEquals(length, instrumental.sampleCount)
            val vocalError = left.indices.maxOf {
                abs(quantizedSource.left[it] - vocals.left[it]).toDouble()
            }
            val instrumentalPeak = instrumental.left.maxOf { abs(it).toDouble() }
            assertTrue("vocal max error=$vocalError", vocalError < 2e-3)
            assertTrue("instrumental peak=$instrumentalPeak", instrumentalPeak < 2e-3)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun sampleSourceResamplesOnlyRequestedWindow() {
        val root = createTempDir(prefix = "karavox-sample-source-")
        try {
            val sourceFile = File(root, "source.wav")
            val values = FloatArray(1_000) { it / 1_000f }
            Pcm16WavStreamWriter(sourceFile, 1_000.coerceAtLeast(8_000)).use {
                val repeated = FloatArray(8_000) { index -> values[index % values.size] }
                it.write(repeated, repeated)
            }
            com.metrolist.music.karaoke.audio.Pcm16WavSampleSource(sourceFile).use { source ->
                val block = source.readResampled(100, 200, 8_000)
                assertEquals(200, block[0].size)
                assertTrue(block[0].any { sample -> abs(sample) > 1e-4f })
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
