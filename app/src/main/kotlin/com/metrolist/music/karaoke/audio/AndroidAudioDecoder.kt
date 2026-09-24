/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

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

/** Decodes any Android-supported audio container/codec into normalized stereo floating-point PCM. */
class AndroidAudioDecoder(
    private val maxDurationMs: Long = 15L * 60L * 1_000L,
) {
    suspend fun decode(file: File): StereoPcmAudio = withContext(Dispatchers.IO) {
        require(file.isFile && file.length() > 0L) { "Audio source file is missing or empty" }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: error("No audio track found in selected source")

            val inputFormat = extractor.getTrackFormat(trackIndex)
            inputFormat.getLongOrNull(MediaFormat.KEY_DURATION)?.let { durationUs ->
                require(durationUs / 1_000L <= maxDurationMs) {
                    "Audio is longer than KaraVox's ${maxDurationMs / 60_000L}-minute processing limit"
                }
            }
            val mime = requireNotNull(inputFormat.getString(MediaFormat.KEY_MIME))
            extractor.selectTrack(trackIndex)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val left = FloatPcmChannelBuilder()
            val right = FloatPcmChannelBuilder()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var sampleRate = inputFormat.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: 44_100
            var channelCount = inputFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 2
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var decodedFrames = 0L

            while (!outputEnded) {
                coroutineContext.ensureActive()

                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = requireNotNull(codec.getInputBuffer(inputIndex))
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime.coerceAtLeast(0L),
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = codec.outputFormat
                        sampleRate = format.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: sampleRate
                        channelCount = format.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: channelCount
                        pcmEncoding = format.getIntegerOrNull(MediaFormat.KEY_PCM_ENCODING)
                            ?: AudioFormat.ENCODING_PCM_16BIT
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER,
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> if (outputIndex >= 0) {
                        if (bufferInfo.size > 0 && bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val outputBuffer = requireNotNull(codec.getOutputBuffer(outputIndex)).duplicate()
                            val start = bufferInfo.offset.coerceAtLeast(0)
                            val end = (start + bufferInfo.size).coerceAtMost(outputBuffer.capacity())
                            outputBuffer.position(start)
                            outputBuffer.limit(end)
                            outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                            val frameCount = appendPcmFrames(
                                buffer = outputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN),
                                encoding = pcmEncoding,
                                channelCount = channelCount,
                                left = left,
                                right = right,
                            )
                            decodedFrames += frameCount
                            val maxFrames = sampleRate.toLong() * maxDurationMs / 1_000L
                            require(decodedFrames <= maxFrames + sampleRate) {
                                "Decoded audio exceeds KaraVox processing duration limit"
                            }
                        }
                        outputEnded = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            val leftSamples = left.toFloatArray()
            val rightSamples = right.toFloatArray()
            require(leftSamples.isNotEmpty() && leftSamples.size == rightSamples.size) {
                "Audio decoder produced no usable PCM samples"
            }
            StereoPcmAudio(sampleRate, leftSamples, rightSamples)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun appendPcmFrames(
        buffer: ByteBuffer,
        encoding: Int,
        channelCount: Int,
        left: FloatPcmChannelBuilder,
        right: FloatPcmChannelBuilder,
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
            left.add(first)
            right.add(second)
        }
        return frameCount
    }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private fun MediaFormat.getLongOrNull(key: String): Long? =
        if (containsKey(key)) getLong(key) else null

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}
