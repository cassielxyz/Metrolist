package com.metrolist.music.karaoke.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import java.io.Closeable
import kotlin.math.abs

/**
 * Keeps instrumental and vocal stems on one practical playback clock so the vocal guide can be
 * mixed from 0-100% without rendering a new file. The instrumental player is authoritative.
 */
class SynchronizedStemPlayer(
    context: Context,
    instrumentalUri: String,
    vocalsUri: String,
) : Closeable {
    private val instrumental = ExoPlayer.Builder(context.applicationContext).build().apply {
        setMediaItem(MediaItem.fromUri(instrumentalUri))
        volume = 1f
        prepare()
    }
    private val vocals = ExoPlayer.Builder(context.applicationContext).build().apply {
        setMediaItem(MediaItem.fromUri(vocalsUri))
        volume = 0f
        prepare()
    }

    val currentPosition: Long
        get() = instrumental.currentPosition.coerceAtLeast(0L)

    val isPlaying: Boolean
        get() = instrumental.isPlaying

    fun setVocalMix(value: Float) {
        vocals.volume = value.coerceIn(0f, 1f)
    }

    fun play() {
        synchronize(force = true)
        instrumental.play()
        vocals.play()
    }

    fun pause() {
        instrumental.pause()
        vocals.pause()
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        instrumental.seekTo(target)
        vocals.seekTo(target)
    }

    fun restart() {
        seekTo(0L)
        play()
    }

    /** Call periodically while the UI is active. */
    fun maintainSync(maxDriftMs: Long = 35L) {
        val drift = vocals.currentPosition - instrumental.currentPosition
        if (abs(drift) > maxDriftMs) vocals.seekTo(instrumental.currentPosition.coerceAtLeast(0L))
        if (instrumental.isPlaying && !vocals.isPlaying) vocals.play()
        if (!instrumental.isPlaying && vocals.isPlaying) vocals.pause()
    }

    private fun synchronize(force: Boolean) {
        val drift = vocals.currentPosition - instrumental.currentPosition
        if (force || abs(drift) > 20L) vocals.seekTo(instrumental.currentPosition.coerceAtLeast(0L))
    }

    override fun close() {
        instrumental.stop()
        vocals.stop()
        instrumental.release()
        vocals.release()
    }
}
