/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.karaoke.settings

import com.metrolist.music.karaoke.model.RecordingQuality
import com.metrolist.music.karaoke.model.SeparationQuality

/**
 * Karaoke-specific settings model. Persistence adapters can map this to the existing
 * Metrolist preference system without coupling the karaoke domain to Android APIs.
 */
data class KaraokePreferences(
    val separationQuality: SeparationQuality = SeparationQuality.BALANCED,
    val recordingQuality: RecordingQuality = RecordingQuality.HIGH,
    /** Original-vocal guide mixed back into the instrumental, 0f = karaoke, 1f = original. */
    val defaultVocalMix: Float = 0f,
    val lyricsOffsetMs: Long = 0L,
    val countdownSeconds: Int = 3,
    val defaultManualDuetOffsetMs: Long = 0L,
    val saveIndividualStems: Boolean = true,
    val noiseSuppression: Boolean = true,
    val echoCancellation: Boolean = true,
    val compressor: Boolean = true,
    val autoAlignDuets: Boolean = true,
    val keepProcessedStems: Boolean = true,
)
