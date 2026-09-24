/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.karaoke.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

val KaraokeSeparationQualityKey = stringPreferencesKey("karaoke_separation_quality")
val KaraokeRecordingQualityKey = stringPreferencesKey("karaoke_recording_quality")
val KaraokeDefaultVocalMixKey = floatPreferencesKey("karaoke_default_vocal_mix")
val KaraokeLyricsOffsetMsKey = longPreferencesKey("karaoke_lyrics_offset_ms")
val KaraokeCountdownSecondsKey = intPreferencesKey("karaoke_countdown_seconds")
val KaraokeDuetManualOffsetMsKey = longPreferencesKey("karaoke_duet_manual_offset_ms")
val KaraokeSaveIndividualStemsKey = booleanPreferencesKey("karaoke_save_individual_stems")
val KaraokeNoiseSuppressionKey = booleanPreferencesKey("karaoke_noise_suppression")
val KaraokeEchoCancellationKey = booleanPreferencesKey("karaoke_echo_cancellation")
val KaraokeCompressorKey = booleanPreferencesKey("karaoke_compressor")
val KaraokeAutoAlignDuetsKey = booleanPreferencesKey("karaoke_auto_align_duets")
val KaraokeKeepProcessedStemsKey = booleanPreferencesKey("karaoke_keep_processed_stems")
