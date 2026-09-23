package com.metrolist.music.karaoke.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

data class DecodedPcmFile(
    val file: File,
    val sampleRateHz: Int,
    val frameCount: Long,
)

/** Streams Android MediaCodec output to PCM16 WAV so long songs never need full-song float arrays. */
class AndroidPcmFileDecoder(
    private val maxDurationMs: Long = 15L * 60L * 1_000L,
) {
    suspend fun decode(
        sourceFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit = {},
    ): DecodedPcmFile = withContext(Dispatchers.IO) {
        require(sourceFile.isFile && sourceFile.length() > 0L) { "Audio source file is missing or empty" }
        outputFile.parentFile?.mkdirs()
        outputFile.delete()

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var writer: Pcm16WavStreamWriter? = null
        var writerClosed = false
        try {
            extractor.setDataSource(sourceFile.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track found in selected source")
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val durationUs = inputFormat.getLongOrNull(MediaFormat.KEY_DURATION)
            durationUs?.let {
                require(it / 1_000L <= maxDurationMs) {
                    "Audio is longer than KaraVox's ${maxDurationMs / 60_000L}-minute processing limit"
                }
            }
            val mime = requireNotNull(inputFormat.getString(MediaFormat.KEY_MIME))
            extractor.selectTrack(trackIndex)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var sampleRate = inputFormat.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: 44_100
            var channelCount = inputFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 2
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var decodedFrames = 0L
            var inputEnded = false
            var outputEnded = false
            val info = MediaCodec.BufferInfo()

            while (!outputEnded) {
                coroutineContext.ensureActive()
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = requireNotNull(codec.getInputBuffer(inputIndex))
                        input.clear()
                        val size = extractor.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime.coerceAtLeast(0L), 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = codec.outputFormat
                        val newRate = format.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: sampleRate
                        if (writer != null) require(newRate == sampleRate) { "Decoder changed sample rate mid-stream" }
                        sampleRate = newRate
                        channelCount = format.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: channelCount
                        pcmEncoding = format.getIntegerOrNull(MediaFormat.KEY_PCM_ENCODING)
                            ?: AudioFormat.ENCODING_PCM_16BIT
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER,
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val buffer = requireNotNull(codec.getOutputBuffer(outputIndex)).duplicate()
                            val start = info.offset.coerceAtLeast(0)
                            val end = (start + info.size).coerceAtMost(buffer.capacity())
                            buffer.position(start)
                            buffer.limit(end)
                            val payload = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
                            val activeWriter = writer ?: Pcm16WavStreamWriter(outputFile, sampleRate).also { writer = it }
                            decodedFrames += appendPcmFrames(payload, pcmEncoding, channelCount, activeWriter)
                            val maxFrames = sampleRate.toLong() * maxDurationMs / 1_000L
                            require(decodedFrames <= maxFrames + sampleRate) {
                                "Decoded audio exceeds KaraVox processing duration limit"
                            }
                            durationUs?.takeIf { it > 0L }?.let { duration ->
                                val expectedFrames = sampleRate.toDouble() * duration / 1_000_000.0
                                if (expectedFrames > 0.0) {
                                    onProgress((decodedFrames / expectedFrames).toFloat().coerceIn(0f, 0.99f))
                                }
                            }
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            val activeWriter = writer ?: error("Audio decoder produced no usable PCM samples")
            activeWriter.close()
            writerClosed = true
            require(outputFile.isFile && outputFile.length() > 44L) { "Decoded PCM file is empty" }
            onProgress(1f)
            DecodedPcmFile(outputFile, sampleRate, decodedFrames)
        } catch (error: Throwable) {
            outputFile.delete()
            throw error
        } finally {
            if (!writerClosed) runCatching { writer?.close() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun appendPcmFrames(
        buffer: ByteBuffer,
        encoding: Int,
        channelCount: Int,
        writer: Pcm16WavStreamWriter,
    ): Int {
        require(channelCount > 0) { "Decoder returned invalid channel count: $channelCount" }
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            AudioFormat.ENCODING_PCM_8BIT -> 1
            else -> error("Unsupported decoder PCM encoding: $encoding")
        }
        val frameBytes = bytesPerSample * channelCount
        val frameCount = buffer.remaining() / frameBytes
        repeat(frameCount) {
            var first = 0f
            var second = 0f
            repeat(channelCount) { channel ->
                val sample = when (encoding) {
                    AudioFormat.ENCODING_PCM_16BIT -> buffer.short / 32_768f
                    AudioFormat.ENCODING_PCM_FLOAT -> buffer.float.coerceIn(-1f, 1f)
                    AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 0xff) - 128) / 128f
                    else -> 0f
                }
                if (channel == 0) first = sample
                if (channel == 1) second = sample
            }
            if (channelCount == 1) second = first
            writer.writeFrame(first, second)
        }
        return frameCount
    }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? = if (containsKey(key)) getInteger(key) else null
    private fun MediaFormat.getLongOrNull(key: String): Long? = if (containsKey(key)) getLong(key) else null

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}
