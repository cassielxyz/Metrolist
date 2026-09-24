/**
 * KaraVox Project (C) 2026
 * Licensed under GPL-3.0.
 *
 * This file is part of the KaraVox-specific product layer. The repository also contains
 * inherited GPL-3.0 code from Metrolist and other upstream projects; see NOTICE.md.
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.metrolist.music.BuildConfig
import com.metrolist.music.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About KaraVox") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = navController::navigateUp) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo_oval),
                contentDescription = "KaraVox",
                modifier = Modifier.height(92.dp),
            )

            Text(
                text = "KaraVox",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Private karaoke for Android",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            InfoCard(
                title = "Built for karaoke",
                body = "KaraVox is focused on online/local song selection, vocal separation, synced lyrics, private recording, and recording-first private duets. It is not designed as a normal social music player.",
            )

            InfoCard(
                title = "Privacy",
                body = "Recordings stay on your device unless you explicitly choose to share a duet take or export. KaraVox does not require a social profile or public recording feed.",
            )

            InfoCard(
                title = "Open-source foundation",
                body = "KaraVox is GPL-3.0 software. It is an independent karaoke-focused project derived from Metrolist and other open-source components. Upstream authors keep full credit for inherited work.",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { uriHandler.openUri("https://github.com/cassielxyz/KaraVox") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Project")
                }
                OutlinedButton(
                    onClick = { uriHandler.openUri("https://github.com/MetrolistGroup/Metrolist") },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Upstream")
                }
            }

            OutlinedButton(
                onClick = { uriHandler.openUri("https://www.gnu.org/licenses/gpl-3.0.html") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("GPL-3.0 license")
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    body: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
