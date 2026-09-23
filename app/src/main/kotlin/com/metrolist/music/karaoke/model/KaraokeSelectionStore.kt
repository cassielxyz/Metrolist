/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 */

package com.metrolist.music.karaoke.model

import java.util.concurrent.ConcurrentHashMap

/**
 * Short-lived handoff for rich song metadata between search results and preparation.
 *
 * Navigation only needs the stable song id. If Android later recreates the process and this
 * cache is empty, the preparation flow can still resolve the source by id and recover metadata
 * through a persistent prepared-song record in a later phase.
 */
object KaraokeSelectionStore {
    private val selections = ConcurrentHashMap<String, KaraokeSongRef>()

    fun put(song: KaraokeSongRef) {
        selections[song.id] = song
    }

    fun get(songId: String): KaraokeSongRef? = selections[songId]

    fun remove(songId: String): KaraokeSongRef? = selections.remove(songId)

    fun clear() {
        selections.clear()
    }
}
