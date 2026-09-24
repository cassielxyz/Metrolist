/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.metrolist.music.karaoke.domain.KaraokeRecorder
import com.metrolist.music.karaoke.model.KaraokeSession
import com.metrolist.music.karaoke.model.RecordingCodec
import com.metrolist.music.karaoke.model.RecordingQuality
import com.metrolist.music.karaoke.model.RecordingTake
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-quality local microphone baseline for KaraVox.
 *
 * This implementation records mono PCM at the session quality's sample rate and exports a WAV
 * file. It intentionally supports the STUDIO_WAV profile only; AAC and FLAC are separate encoder
 * backends so a file is never mislabeled as another codec.
 */
class AndroidWavKaraokeRecorder(
    private val context: Context,
) : KaraokeRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = ConcurrentHashMap<String, ActiveRecording>()

    override suspend fun start(
        session: KaraokeSession,
        quality: RecordingQuality,
    ): String = withContext(Dispatchers.IO) {
        require(quality.codec == RecordingCodec.WAV) {
            "AndroidWavKaraokeRecorder only supports WAV recording"
        }
        check(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        ) {
            "Microphone permission is required"
        }

        val sampleRate = quality.sampleRateHz
        val channelMask = AudioFormat.CHANNEL_IN_MONO
        // PCM float/24-bit support varies heavily between Android devices. Capture reliable
        // 16-bit PCM now; a device-capability-aware 24-bit backend can be added independently.
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelMask, encoding)
        check(minBuffer > 0) { "Unable to determine microphone buffer size" }
        val bufferSize = (minBuffer * 2).coerceAtLeast(sampleRate / 2)

        val recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        check(recorder.state == AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            "Microphone could not be initialized"
        }

        val directory = File(context.filesDir, "karaoke/recordings/${session.id.safePathSegment()}")
            .apply { mkdirs() }
        val recordingId = UUID.randomUUID().toString()
        val rawFile = File(directory, "$recordingId.pcm")
        val outputFile = File(directory, "$recordingId.wav")
        val running = AtomicBoolean(true)
        val finished = CompletableDeferred<CaptureStats>()

        recorder.startRecording()
        val job = scope.launch {
            var bytesWritten = 0L
            try {
                BufferedOutputStream(FileOutputStream(rawFile)).use { output ->
                    val buffer = ByteArray(bufferSize)
                    while (running.get()) {
                        val read = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                        if (read > 0) {
                            output.write(buffer, 0, read)
                            bytesWritten += read
                        } else if (read < 0) {
                            throw IllegalStateException("Microphone read failed: $read")
                        }
                    }
                    output.flush()
                }
                finished.complete(CaptureStats(bytesWritten = bytesWritten))
            } catch (error: Throwable) {
                finished.completeExceptionally(error)
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
        }

        active[recordingId] = ActiveRecording(
            id = recordingId,
            sessionId = session.id,
            quality = quality,
            sampleRate = sampleRate,
            rawFile = rawFile,
            outputFile = outputFile,
            running = running,
            job = job,
            finished = finished,
        )

        recordingId
    }

    override suspend fun stop(recordingId: String): RecordingTake = withContext(Dispatchers.IO) {
        val recording = active.remove(recordingId)
            ?: error("Recording is not active: $recordingId")

        recording.running.set(false)
        val stats = recording.finished.await()
        recording.job.join()

        writeMono16BitWav(
            pcmFile = recording.rawFile,
            wavFile = recording.outputFile,
            sampleRate = recording.sampleRate,
        )
        recording.rawFile.delete()

        val bytesPerSample = 2L
        val durationMs = if (stats.bytesWritten > 0L) {
            (stats.bytesWritten * 1_000L) / (recording.sampleRate * bytesPerSample)
        } else {
            0L
        }

        RecordingTake(
            id = recording.id,
            sessionId = recording.sessionId,
            vocalUri = recording.outputFile.toURI().toString(),
            quality = recording.quality,
            durationMs = durationMs,
        )
    }

    override suspend fun cancel(recordingId: String) {
        val recording = active.remove(recordingId) ?: return
        recording.running.set(false)
        runCatching { recording.job.cancelAndJoin() }
        recording.rawFile.delete()
        recording.outputFile.delete()
    }

    private fun writeMono16BitWav(
        pcmFile: File,
        wavFile: File,
        sampleRate: Int,
    ) {
        val dataSize = pcmFile.length()
        val byteRate = sampleRate * 2
        val riffSize = 36L + dataSize

        BufferedOutputStream(FileOutputStream(wavFile)).use { output ->
            output.write("RIFF".toByteArray(Charsets.US_ASCII))
            output.write(littleEndianInt(riffSize.toInt()))
            output.write("WAVE".toByteArray(Charsets.US_ASCII))
            output.write("fmt ".toByteArray(Charsets.US_ASCII))
            output.write(littleEndianInt(16))
            output.write(littleEndianShort(1)) // PCM
            output.write(littleEndianShort(1)) // mono
            output.write(littleEndianInt(sampleRate))
            output.write(littleEndianInt(byteRate))
            output.write(littleEndianShort(2)) // block align
            output.write(littleEndianShort(16)) // bits per sample
            output.write("data".toByteArray(Charsets.US_ASCII))
            output.write(littleEndianInt(dataSize.toInt()))

            BufferedInputStream(FileInputStream(pcmFile)).use { input ->
                input.copyTo(output)
            }
        }
    }

    private fun littleEndianInt(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun littleEndianShort(value: Int): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array()

    private fun String.safePathSegment(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_").take(96)

    private data class CaptureStats(
        val bytesWritten: Long,
    )

    private data class ActiveRecording(
        val id: String,
        val sessionId: String,
        val quality: RecordingQuality,
        val sampleRate: Int,
        val rawFile: File,
        val outputFile: File,
        val running: AtomicBoolean,
        val job: Job,
        val finished: CompletableDeferred<CaptureStats>,
    )
}
