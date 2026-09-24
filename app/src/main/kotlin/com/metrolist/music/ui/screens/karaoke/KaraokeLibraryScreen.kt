package com.metrolist.music.ui.screens.karaoke

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metrolist.music.karaoke.cache.KaraokeSessionRepository
import com.metrolist.music.karaoke.cache.SavedKaraokeSession
import com.metrolist.music.karaoke.model.KaraokeSessionStore
import kotlinx.coroutines.launch

/** Prepared songs that can reopen without another separation pass. */
@Composable
fun KaraokeLibraryScreen(
    onOpenSession: (String) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { KaraokeSessionRepository(context) }
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<SavedKaraokeSession>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            runCatching { repository.list() }
                .onSuccess {
                    sessions = it
                    loading = false
                    error = null
                }
                .onFailure {
                    loading = false
                    error = it.message ?: "Unable to load prepared songs"
                }
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Prepared songs", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Separated stems and synced lyrics saved on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when {
            loading -> Text("Loading…")
            sessions.isEmpty() -> Text("No prepared songs yet. Search online or choose a local audio file from Home.")
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(sessions, key = { it.session.id }) { saved ->
                    val session = saved.session
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(session.song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            if (session.song.artist.isNotBlank()) {
                                Text(session.song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "${session.song.source.name.lowercase().replaceFirstChar { it.uppercase() }} • " +
                                    if (session.lyrics != null) "lyrics ready" else "no synced lyrics",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    onClick = {
                                        KaraokeSessionStore.put(session)
                                        onOpenSession(session.id)
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Sing") }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            repository.delete(session.id, deleteStemFiles = true)
                                            KaraokeSessionStore.remove(session.id)
                                            reload()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
}
