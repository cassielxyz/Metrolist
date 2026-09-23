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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.metrolist.music.karaoke.cache.KaraokeSessionRepository
import com.metrolist.music.karaoke.cache.SavedKaraokeSession
import com.metrolist.music.karaoke.model.KaraokeSessionStore

/**
 * Private duet entry point. Both singers record local takes from the same prepared session; takes
 * can then be exchanged/imported from Recordings and aligned after capture, without live mic upload.
 */
@Composable
fun KaraokeDuetScreen(
    onOpenSession: (String) -> Unit,
    onOpenRecordings: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { KaraokeSessionRepository(context) }
    var sessions by remember { mutableStateOf<List<SavedKaraokeSession>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        sessions = repository.list()
        loading = false
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Private duet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Choose the same prepared song on both phones. Each phone records only its singer. Export/import the takes from Recordings; KaraVox aligns and mixes them after recording.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onOpenRecordings, modifier = Modifier.fillMaxWidth()) {
            Text("Manage duet takes")
        }
        when {
            loading -> Text("Loading prepared songs…")
            sessions.isEmpty() -> Text("Prepare at least one song first, then return here.")
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
                            Text(session.song.title, style = MaterialTheme.typography.titleMedium)
                            if (session.song.artist.isNotBlank()) Text(session.song.artist)
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
                                ) { Text("Record take") }
                                OutlinedButton(
                                    onClick = onOpenRecordings,
                                    modifier = Modifier.weight(1f),
                                ) { Text("Takes") }
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}
