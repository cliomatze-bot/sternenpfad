package com.ipa.analyzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ipa.analyzer.Routes
import com.ipa.analyzer.core.api.LookupResult
import com.ipa.analyzer.core.api.PublicProfile
import com.ipa.analyzer.core.model.ExportProfileInfo
import com.ipa.analyzer.ui.DataViewModel
import com.ipa.analyzer.ui.ProfileViewModel
import com.ipa.analyzer.ui.components.Avatar
import com.ipa.analyzer.ui.components.Badge
import com.ipa.analyzer.ui.components.InfoBox
import com.ipa.analyzer.ui.components.ScreenHeader
import com.ipa.analyzer.ui.components.SectionCard
import com.ipa.analyzer.ui.components.SectionTitle
import com.ipa.analyzer.ui.components.StatTile
import com.ipa.analyzer.ui.components.formatDateTimeMs
import com.ipa.analyzer.ui.components.formatNumber
import com.ipa.analyzer.ui.theme.Negative
import com.ipa.analyzer.ui.theme.Positive

@Composable
fun ProfileScreen(profileVm: ProfileViewModel, dataVm: DataViewModel, onBack: () -> Unit, navigate: (String) -> Unit) {
    val state by profileVm.state.collectAsStateWithLifecycle()
    val active by dataVm.active.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val open: (String) -> Unit = { url -> runCatching { uri.openUri(url) } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Profilübersicht", subtitle = state.query.takeIf { it.isNotBlank() }?.let { "@" + it.trim().removePrefix("@") }, onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (val r = state.result) {
                null -> if (state.loading) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    InfoBox(Icons.Filled.Info, "Kein Profil abgerufen", "Gib auf der Startseite einen Benutzernamen ein.")
                }
                is LookupResult.Success -> ProfileContent(r, open, onPosts = { navigate(Routes.POSTS) })
                is LookupResult.NotAvailable -> InfoBox(
                    Icons.Filled.Lock,
                    "Profil nicht abrufbar: @${r.username}",
                    "Über die offizielle Schnittstelle sind nur öffentliche Business- und Creator-Konten abrufbar. " +
                        "Dieses Konto existiert nicht, ist privat oder ein persönliches Konto. Private Inhalte werden " +
                        "von dieser App grundsätzlich nicht umgangen oder abgerufen." +
                        (r.apiMessage?.let { "\n\nAntwort der API: $it" } ?: ""),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    actions = {
                        TextButton(onClick = { open("https://www.instagram.com/${r.username}/") }) { Text("In Instagram öffnen") }
                    },
                )
                is LookupResult.NotConfigured -> InfoBox(
                    Icons.Filled.Key,
                    "Offizielle Schnittstelle nicht eingerichtet",
                    "Instagram bietet keine offizielle, frei zugängliche Schnittstelle zum Abruf beliebiger Profile. " +
                        "Möglich ist der Abruf öffentlicher Business-/Creator-Konten über die Graph API mit deinem eigenen " +
                        "Zugriffstoken (Einstellungen). Alternativ: eigenen Datenexport importieren.",
                    actions = {
                        TextButton(onClick = { navigate(Routes.SETTINGS) }) { Text("API einrichten") }
                        TextButton(onClick = { navigate(Routes.IMPORT) }) { Text("Export importieren") }
                    },
                )
                is LookupResult.InvalidUsername -> InfoBox(
                    Icons.Filled.Warning, "Ungültiger Benutzername",
                    "„${r.input}“ ist kein gültiger Instagram-Benutzername (1–30 Zeichen: Buchstaben, Ziffern, Punkt, Unterstrich).",
                    tint = Negative,
                )
                is LookupResult.AuthError -> InfoBox(Icons.Filled.Key, "Zugriff verweigert", r.message, tint = Negative,
                    actions = { TextButton(onClick = { navigate(Routes.SETTINGS) }) { Text("Einstellungen") } })
                is LookupResult.RateLimited -> InfoBox(Icons.Filled.Warning, "Limit erreicht", r.message, tint = Negative)
                is LookupResult.NetworkError -> InfoBox(Icons.Filled.WifiOff, "Netzwerkfehler", r.message, tint = Negative,
                    actions = { TextButton(onClick = { profileVm.lookup() }) { Text("Erneut versuchen") } })
                is LookupResult.ApiError -> InfoBox(
                    Icons.Filled.Warning, "Fehler der Schnittstelle" + (r.code?.let { " (Code $it)" } ?: ""), r.message, tint = Negative,
                )
            }

            val exportProfile = active?.profile
            if (exportProfile != null) {
                ExportProfileCard(exportProfile, active?.createdAt)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ProfileContent(r: LookupResult.Success, open: (String) -> Unit, onPosts: () -> Unit) {
    val p: PublicProfile = r.profile
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (p.profilePictureUrl != null) {
                AsyncImage(
                    model = p.profilePictureUrl,
                    contentDescription = "Profilbild",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            } else {
                Avatar(p.username, size = 72)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("@${p.username}", style = MaterialTheme.typography.titleMedium)
                if (!p.name.isNullOrBlank()) Text(p.name, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Badge("Öffentlich · Business/Creator", Positive)
            }
        }
        if (!p.biography.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(p.biography, style = MaterialTheme.typography.bodyMedium)
        }
        if (!p.website.isNullOrBlank()) {
            TextButton(onClick = { open(p.website) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(p.website, maxLines = 1)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Beiträge", formatNumber(p.mediaCount), Modifier.weight(1f))
            StatTile("Follower", formatNumber(p.followersCount), Modifier.weight(1f))
            StatTile("Gefolgt", formatNumber(p.followsCount), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Abgerufen am ${formatDateTimeMs(r.fetchedAt)} über die offizielle Instagram Graph API.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    SectionCard {
        SectionTitle("Profilstatus")
        StatusLine(Icons.Filled.CheckCircle, "Konto existiert und ist öffentlich", Positive)
        StatusLine(Icons.Filled.CheckCircle, "Professionelles Konto (Business oder Creator)", Positive)
        StatusLine(Icons.Filled.Info, "Follower-Listen fremder Konten stellt Instagram nicht bereit – nur Anzahlen.",
            MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onPosts, modifier = Modifier.weight(1f)) { Text("Beiträge (${r.posts.size})") }
        OutlinedButton(onClick = { open("https://www.instagram.com/${p.username}/") }, modifier = Modifier.weight(1f)) {
            Text("Instagram")
        }
    }
}

@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: androidx.compose.ui.graphics.Color) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ExportProfileCard(p: ExportProfileInfo, importedAt: Long?) {
    SectionCard {
        SectionTitle("Eigenes Profil (aus Datenexport)")
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.username?.let { "@$it" } ?: "Benutzername unbekannt", style = MaterialTheme.typography.titleMedium)
                if (!p.name.isNullOrBlank()) Text(p.name, style = MaterialTheme.typography.bodyMedium)
            }
            when (p.isPrivate) {
                true -> Badge("Privat", MaterialTheme.colorScheme.primary)
                false -> Badge("Öffentlich", Positive)
                null -> {}
            }
        }
        if (!p.bio.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(p.bio, style = MaterialTheme.typography.bodyMedium)
        }
        if (importedAt != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Stand: Import vom ${formatDateTimeMs(importedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
