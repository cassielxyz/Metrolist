/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.duet

import com.metrolist.music.karaoke.domain.DuetAlignmentEngine
import com.metrolist.music.karaoke.model.AlignmentResult
import com.metrolist.music.karaoke.model.DuetSyncMetadata
import com.metrolist.music.karaoke.model.RecordingTake
import kotlin.math.absoluteValue

/**
 * Deterministic first-pass duet alignment.
 *
 * Priority:
 * 1. detected marker positions inside both recordings
 * 2. room scheduled-start error from both clients
 * 3. latency calibration + manual trim only
 *
 * Waveform correlation can refine this result in a later stage without changing the contract.
 */
class MetadataDuetAlignmentEngine : DuetAlignmentEngine {
    override suspend fun align(
        host: RecordingTake,
        partner: RecordingTake,
        hostSync: DuetSyncMetadata,
        partnerSync: DuetSyncMetadata,
    ): AlignmentResult {
        require(host.sessionId == partner.sessionId) {
            "Cannot align takes from different karaoke sessions"
        }

        val markerOffset = if (
            hostSync.syncMarkerPositionMs != null &&
            partnerSync.syncMarkerPositionMs != null
        ) {
            hostSync.syncMarkerPositionMs - partnerSync.syncMarkerPositionMs
        } else {
            null
        }

        val scheduleOffset = if (
            hostSync.roomStartErrorMs != null &&
            partnerSync.roomStartErrorMs != null
        ) {
            hostSync.roomStartErrorMs - partnerSync.roomStartErrorMs
        } else {
            null
        }

        // If the partner audio path adds more delay, move that take earlier by the difference.
        val latencyOffset = hostSync.deviceLatencyMs - partnerSync.deviceLatencyMs
        val manualOffset = partnerSync.manualOffsetMs - hostSync.manualOffsetMs

        val base = markerOffset ?: scheduleOffset ?: 0L
        val finalOffset = base + latencyOffset + manualOffset

        val confidence = when {
            markerOffset != null -> 0.95f
            scheduleOffset != null && scheduleOffset.absoluteValue <= 100L -> 0.80f
            scheduleOffset != null -> 0.68f
            hostSync.deviceLatencyMs != 0L || partnerSync.deviceLatencyMs != 0L -> 0.45f
            else -> 0.25f
        }

        val method = buildString {
            append(
                when {
                    markerOffset != null -> "sync-marker"
                    scheduleOffset != null -> "room-start"
                    else -> "manual"
                },
            )
            if (hostSync.deviceLatencyMs != 0L || partnerSync.deviceLatencyMs != 0L) {
                append("+latency")
            }
            if (manualOffset != 0L) append("+manual")
        }

        return AlignmentResult(
            partnerOffsetMs = finalOffset,
            confidence = confidence,
            method = method,
        )
    }
}
