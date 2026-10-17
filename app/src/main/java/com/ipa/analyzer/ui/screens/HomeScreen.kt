package com.ipa.analyzer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipa.analyzer.Routes
import com.ipa.analyzer.core.diff.ListComparator
import com.ipa.analyzer.ui.DataViewModel
import com.ipa.analyzer.ui.ProfileViewModel
import com.ipa.analyzer.ui.components.InfoBox
import com.ipa.analyzer.ui.components.SectionCard
import com.ipa.analyzer.ui.components.SectionTitle
import com.ipa.analyzer.ui.components.StatTile
import com.ipa.analyzer.ui.components.formatDateTimeMs
import com.ipa.analyzer.ui.components.formatNumber
import com.ipa.analyzer.ui.theme.Negative

@Composable
fun HomeScreen(
    dataVm: DataViewModel,
    profileVm: ProfileViewModel,
    onSearch: () -> Unit,
    navigate: (String) -> Unit,
    navigateTab: (String) -> Unit,
) {
    val state by profileVm.state.collectAsStateWithLifecycle()
    val creds by profileVm.credentials.collectAsStateWithLifecycle()
    val active by dataVm.active.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current

    val relationship = remember(active) {
        active?.takeIf { it.hasFollowers && it.hasFollowing }?.let { ListComparator.relationship(it.followers, it.following) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text("Instagram Profile Analyzer", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Öffentliche Profildaten & eigene Datenexporte analysieren",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            SectionTitle("Profil suchen")
            OutlinedTextField(
                value = state.query,
                onValueChange = profileVm::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Benutzername, z. B. natgeo") },
                prefix = { Text("@") },
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus()
                    if (state.query.isNotBlank()) onSearch()
                }),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { focus.clearFocus(); onSearch() },
                enabled = state.query.isNotBlank() && !state.loading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Profil analysieren")
            }
            if (!creds.isComplete) {
                Spacer(Modifier.height(10.dp))
                InfoBox(
                    icon = Icons.Filled.Info,
                    title = "Offizielle Schnittstelle nicht eingerichtet",
                    text = "Instagram erlaubt den Abruf fremder Profile nur über die Graph API (Business Discovery) " +
                        "mit eigenem Zugriffstoken – und nur für Business- und Creator-Konten. " +
                        "Ohne Token kannst du deinen eigenen Datenexport importieren.",
                    actions = {
                        TextButton(onClick = { navigateTab(Routes.SETTINGS) }) { Text("Einrichten") }
                        TextButton(onClick = { navigate(Routes.IMPORT) }) { Text("Export importieren") }
                    },
                )
            }
        }

        SectionCard {
            SectionTitle("Aktiver Datenstand")
            val a = active
            if (a == null) {
                Text(
                    "Noch keine Daten importiert. Importiere deinen Instagram-Datenexport (ZIP oder JSON), " +
                        "um Follower- und Following-Listen zu analysieren.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = { navigate(Routes.IMPORT) }, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Datenexport importieren")
                }
            } else {
                Text(a.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Importiert am ${formatDateTimeMs(a.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Follower", if (a.hasFollowers) formatNumber(a.followers.size) else "–", Modifier.weight(1f)) {
                        navigateTab(Routes.FOLLOWERS)
                    }
                    StatTile("Gefolgt", if (a.hasFollowing) formatNumber(a.following.size) else "–", Modifier.weight(1f)) {
                        navigateTab(Routes.FOLLOWING)
                    }
                }
                if (relationship != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("folgen dir nicht zurück", formatNumber(relationship.notFollowingBack.size), Modifier.weight(1f), accent = Negative) {
                            navigateTab(Routes.FOLLOWING)
                        }
                        StatTile("gegenseitig", formatNumber(relationship.mutual.size), Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { navigate(Routes.IMPORT) }, modifier = Modifier.weight(1f)) { Text("Neuer Import") }
                    OutlinedButton(onClick = { navigateTab(Routes.HISTORY) }, modifier = Modifier.weight(1f)) { Text("Verlauf") }
                }
            }
        }

        SectionCard(onClick = { navigate(Routes.POSTS) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Beitragsübersicht", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Beiträge & Reels aus API-Abruf oder Datenexport",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
