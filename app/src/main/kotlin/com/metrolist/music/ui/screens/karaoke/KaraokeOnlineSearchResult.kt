/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.karaoke

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.metrolist.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalNavController
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.viewmodels.OnlineSearchViewModel

/** Song-only search result surface for the dedicated karaoke flow. */
@Composable
fun KaraokeOnlineSearchResult(
    viewModel: OnlineSearchViewModel = hiltViewModel(),
) {
    val navController = LocalNavController.current

    LaunchedEffect(Unit) {
        if (viewModel.filter.value != FILTER_SONG) {
            viewModel.filter.value = FILTER_SONG
        }
    }

    val page = viewModel.viewStateMap[FILTER_SONG.value]
    val songs = page?.items.orEmpty().filterIsInstance<SongItem>()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Karaoke search",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (viewModel.query.isBlank()) "Songs" else "Songs for “${viewModel.query}”",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when {
            page == null -> {
                Text(
                    text = "Searching…",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            songs.isEmpty() -> {
                Text(
                    text = "No songs found.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(
                        items = songs,
                        key = { it.id },
                    ) { song ->
                        YouTubeListItem(
                            item = song,
                            isActive = false,
                            isPlaying = false,
                            modifier = Modifier.clickable {
                                navController.navigate("karaoke_prepare/${song.id}")
                            },
                            trailingContent = {
                                Text(
                                    text = "Karaoke",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                    }
                    if (page.continuation != null) {
                        item(key = "load_more") {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = viewModel::loadMore,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Load more songs")
                            }
                            Spacer(Modifier.height(96.dp))
                        }
                    }
                }
            }
        }
    }
}
