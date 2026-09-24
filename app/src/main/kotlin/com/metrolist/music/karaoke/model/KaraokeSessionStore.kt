/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.model

import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local handoff for fully prepared karaoke sessions.
 *
 * Persistent prepared-song metadata is stored separately from this runtime handoff. Keeping the
 * active session object here prevents large lyric/stem payloads from being serialized into a
 * navigation route.
 */
object KaraokeSessionStore {
    private val sessions = ConcurrentHashMap<String, KaraokeSession>()

    fun put(session: KaraokeSession) {
        sessions[session.id] = session
    }

    fun get(sessionId: String): KaraokeSession? = sessions[sessionId]

    fun remove(sessionId: String): KaraokeSession? = sessions.remove(sessionId)

    fun clear() {
        sessions.clear()
    }
}
