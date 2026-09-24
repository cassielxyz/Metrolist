package com.metrolist.music.karaoke.duet

import com.metrolist.music.karaoke.model.DuetSyncMetadata
import com.metrolist.music.karaoke.model.RecordingQuality
import com.metrolist.music.karaoke.model.RecordingTake
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataDuetAlignmentEngineTest {
    private val engine = MetadataDuetAlignmentEngine()

    @Test
    fun markerPositionsAlignPartnerToSameSongTimeline() = runBlocking {
        val host = take("host")
        val partner = take("partner")
        val result = engine.align(
            host = host,
            partner = partner,
            hostSync = DuetSyncMetadata(
                localStartTimestampNs = 100L,
                syncMarkerPositionMs = 80L,
            ),
            partnerSync = DuetSyncMetadata(
                localStartTimestampNs = 900L,
                syncMarkerPositionMs = 230L,
            ),
        )

        // Partner's song timeline begins 150 ms later inside its recording, so move it earlier.
        assertEquals(-150L, result.partnerOffsetMs)
        assertEquals("sync-marker", result.method)
        assertTrue(result.confidence >= 0.9f)
    }

    @Test
    fun deviceLatencyAndManualTrimAreAppliedAfterMarkerAlignment() = runBlocking {
        val result = engine.align(
            host = take("host"),
            partner = take("partner"),
            hostSync = DuetSyncMetadata(
                localStartTimestampNs = 0L,
                syncMarkerPositionMs = 100L,
                deviceLatencyMs = 30L,
                manualOffsetMs = 0L,
            ),
            partnerSync = DuetSyncMetadata(
                localStartTimestampNs = 0L,
                syncMarkerPositionMs = 160L,
                deviceLatencyMs = 80L,
                manualOffsetMs = 20L,
            ),
        )

        // marker -60, latency -50, manual +20 = -90 ms.
        assertEquals(-90L, result.partnerOffsetMs)
        assertTrue(result.method.contains("sync-marker"))
        assertTrue(result.method.contains("latency"))
        assertTrue(result.method.contains("manual"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesTakesFromDifferentPreparedSongs() = runBlocking {
        engine.align(
            host = take("host", sessionId = "song-a"),
            partner = take("partner", sessionId = "song-b"),
            hostSync = DuetSyncMetadata(0L),
            partnerSync = DuetSyncMetadata(0L),
        )
    }

    private fun take(id: String, sessionId: String = "session") = RecordingTake(
        id = id,
        sessionId = sessionId,
        vocalUri = "/tmp/$id.wav",
        quality = RecordingQuality.STUDIO_WAV,
        durationMs = 30_000L,
    )
}
