package com.metrolist.music.karaoke.cache

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class KaraokeStorageUsage(
    val preparedBytes: Long,
    val modelBytes: Long,
    val recordingBytes: Long,
    val transientBytes: Long,
) {
    val totalBytes: Long get() = preparedBytes + modelBytes + recordingBytes + transientBytes
}

class KaraVoxStorageManager(context: Context) {
    private val appContext = context.applicationContext
    private val preparedRoot = File(appContext.filesDir, "karaoke/prepared-stems")
    private val sessionRoot = File(appContext.filesDir, "karaoke/sessions")
    private val modelRoot = File(appContext.filesDir, "karaoke/models")
    private val recordingRoot = File(appContext.filesDir, "karaoke/recordings")
    private val sourceRoot = File(appContext.cacheDir, "karaoke/source")
    private val tempRoot = File(appContext.cacheDir, "karaoke/separation-temp")
    private val decodeRoot = File(appContext.cacheDir, "karaoke/decode-temp")

    suspend fun usage(): KaraokeStorageUsage = withContext(Dispatchers.IO) {
        KaraokeStorageUsage(
            preparedBytes = sizeOf(preparedRoot) + sizeOf(sessionRoot),
            modelBytes = sizeOf(modelRoot),
            recordingBytes = sizeOf(recordingRoot),
            transientBytes = sizeOf(sourceRoot) + sizeOf(tempRoot) + sizeOf(decodeRoot),
        )
    }

    suspend fun clearTransient(): Boolean = withContext(Dispatchers.IO) {
        listOf(sourceRoot, tempRoot, decodeRoot).all(::deleteAndRecreate)
    }

    suspend fun clearPrepared(): Boolean = withContext(Dispatchers.IO) {
        listOf(preparedRoot, sessionRoot).all(::deleteAndRecreate)
    }

    suspend fun clearModels(): Boolean = withContext(Dispatchers.IO) { deleteAndRecreate(modelRoot) }

    suspend fun clearRecordings(): Boolean = withContext(Dispatchers.IO) { deleteAndRecreate(recordingRoot) }

    private fun deleteAndRecreate(directory: File): Boolean {
        val deleted = !directory.exists() || directory.deleteRecursively()
        return deleted && (directory.mkdirs() || directory.isDirectory)
    }

    private fun sizeOf(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles().orEmpty().sumOf(::sizeOf)
    }
}
