package com.metrolist.music.ui.screens.karaoke

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Dedicated karaoke search: song discovery only, never normal-player navigation. */
@Composable
fun KaraokeSearchScreen(
    onSearch: (String) -> Unit,
    onLocalSongSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val localPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onLocalSongSelected(uri.toString())
        }
    }
    fun submit() {
        val clean = query.trim()
        if (clean.isNotEmpty()) onSearch(clean)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Find a song", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Search online songs for karaoke preparation. Selecting a result prepares stems and synced lyrics.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Song or artist") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = ::submit,
            enabled = query.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Search songs") }
        OutlinedButton(
            onClick = { localPicker.launch(arrayOf("audio/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Choose local audio") }
    }
}
