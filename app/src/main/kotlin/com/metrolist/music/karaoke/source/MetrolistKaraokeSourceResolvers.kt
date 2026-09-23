/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.karaoke.source

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.karaoke.domain.KaraokeAudioSourceResolver
import com.metrolist.music.karaoke.model.KaraokeSongRef
import com.metrolist.music.karaoke.model.KaraokeSource
import com.metrolist.music.karaoke.model.ResolvedKaraokeAudio
import com.metrolist.music.utils.YTPlayerUtils

/**
 * Uses the same playback resolution path as Metrolist for an online song.
 * The returned stream URI is intentionally treated as transient; the preparation layer must
 * copy/cache the source before a signed URL expires.
 */
class MetrolistOnlineKaraokeSourceResolver(
    context: Context,
    private val audioQuality: AudioQuality = AudioQuality.AUTO,
) : KaraokeAudioSourceResolver {
    private val connectivityManager = context.getSystemService<ConnectivityManager>()
        ?: error("ConnectivityManager unavailable")

    override suspend fun resolve(song: KaraokeSongRef): ResolvedKaraokeAudio {
        require(song.source == KaraokeSource.ONLINE) {
            "MetrolistOnlineKaraokeSourceResolver only accepts ONLINE songs"
        }

        val playbackData = YTPlayerUtils.playerResponseForPlayback(
            song.id,
            audioQuality = audioQuality,
            connectivityManager = connectivityManager,
        ).getOrThrow()

        return ResolvedKaraokeAudio(
            uri = playbackData.streamUrl,
            cacheKey = "yt:${song.id}",
            durationMs = playbackData.videoDetails
                ?.lengthSeconds
                ?.toLongOrNull()
                ?.times(1_000L)
                ?: song.durationMs,
        )
    }
}

class LocalKaraokeSourceResolver : KaraokeAudioSourceResolver {
    override suspend fun resolve(song: KaraokeSongRef): ResolvedKaraokeAudio {
        require(song.source == KaraokeSource.LOCAL) {
            "LocalKaraokeSourceResolver only accepts LOCAL songs"
        }
        val uri = requireNotNull(song.mediaUri) {
            "Local karaoke song is missing its media URI"
        }
        return ResolvedKaraokeAudio(
            uri = uri,
            cacheKey = "local:${song.id}",
            durationMs = song.durationMs,
        )
    }
}

class CompositeKaraokeSourceResolver(
    private val online: KaraokeAudioSourceResolver,
    private val local: KaraokeAudioSourceResolver,
) : KaraokeAudioSourceResolver {
    override suspend fun resolve(song: KaraokeSongRef): ResolvedKaraokeAudio =
        when (song.source) {
            KaraokeSource.ONLINE -> online.resolve(song)
            KaraokeSource.LOCAL -> local.resolve(song)
        }
}
