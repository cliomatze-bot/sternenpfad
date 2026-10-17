package com.ipa.analyzer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ipa.analyzer.Routes
import com.ipa.analyzer.core.diff.PostAnalyzer
import com.ipa.analyzer.core.diff.PostStats
import com.ipa.analyzer.core.model.PostEntry
import com.ipa.analyzer.core.model.PostKind
import com.ipa.analyzer.ui.DataViewModel
import com.ipa.analyzer.ui.ProfileViewModel
import com.ipa.analyzer.ui.components.EmptyState
import com.ipa.analyzer.ui.components.InfoBox
import com.ipa.analyzer.ui.components.ScreenHeader
import com.ipa.analyzer.ui.components.SectionCard
import com.ipa.analyzer.ui.components.SectionTitle
import com.ipa.analyzer.ui.components.StatTile
import com.ipa.analyzer.ui.components.formatDateSec
import com.ipa.analyzer.ui.components.formatNumber
import java.util.Locale

private enum class PostSource(val label: String) { API("API-Abruf"), EXPORT("Datenexport") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostsScreen(profileVm: ProfileViewModel, dataVm: DataViewModel, onBack: () -> Unit, navigate: (String) -> Unit) {
    val pState by profileVm.state.collectAsStateWithLifecycle()
    val active by dataVm.active.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current

    val api = pState.lastSuccess
    val exportPosts = active?.posts ?: emptyList()
    var source by rememberSaveable {
        mutableStateOf(if (api != null) PostSource.API else PostSource.EXPORT)
    }
    var kindFilter by rememberSaveable { mutableStateOf<PostKind?>(null) }

    val posts: List<PostEntry> = when (source) {
        PostSource.API -> api?.posts ?: emptyList()
        PostSource.EXPORT -> exportPosts
    }
    val filtered = remember(posts, kindFilter) { if (kindFilter == null) posts else posts.filter { it.kind == kindFilter } }
    val stats = remember(posts) { PostAnalyzer.stats(posts) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "Beitragsübersicht",
            subtitle = when (source) {
                PostSource.API -> api?.let { "@${it.profile.username} · offizielle Graph API" }
                PostSource.EXPORT -> active?.let { "${it.label} · Datenexport" }
            },
            onBack = onBack,
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "controls") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PostSource.entries.forEach { s ->
                            FilterChip(selected = source == s, onClick = { source = s; kindFilter = null }, label = { Text(s.label) })
                        }
                    }
                    when {
                        source == PostSource.API && api == null -> InfoBox(
                            Icons.Filled.Info, "Kein API-Abruf vorhanden",
                            "Beiträge fremder Konten sind nur über die offizielle Graph API abrufbar (öffentliche Business-/Creator-Konten). " +
                                "Suche zuerst ein Profil auf der Startseite oder nutze deinen Datenexport.",
                        )
                        source == PostSource.EXPORT && active == null -> InfoBox(
                            Icons.Filled.Info, "Kein Datenexport importiert",
                            "Importiere deinen Instagram-Datenexport, um deine eigenen Beiträge und Reels auszuwerten.",
                            actions = {
                                androidx.compose.material3.TextButton(onClick = { navigate(Routes.IMPORT) }) { Text("Importieren") }
                            },
                        )
                        source == PostSource.EXPORT -> Text(
                            "Hinweis: Instagram-Datenexporte enthalten Datum, Typ und Bildunterschrift – aber keine Like- oder Kommentarzahlen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> {}
                    }
                }
            }
            if (posts.isNotEmpty()) {
                item(key = "stats") { StatsCard(stats) }
                item(key = "kinds") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(selected = kindFilter == null, onClick = { kindFilter = null }, label = { Text("Alle (${posts.size})") })
                        stats.byKind.entries.sortedByDescending { it.value }.forEach { (k, n) ->
                            FilterChip(selected = kindFilter == k, onClick = { kindFilter = k }, label = { Text("${k.label} ($n)") })
                        }
                    }
                }
                items(filtered, key = { it.id }) { p ->
                    PostRow(p, onOpen = { url -> runCatching { uri.openUri(url) } })
                }
            } else if ((source == PostSource.API && api != null) || (source == PostSource.EXPORT && active != null)) {
                item(key = "empty") {
                    EmptyState(Icons.Filled.PhotoLibrary, "Keine Beiträge", "In dieser Quelle sind keine Beiträge vorhanden.")
                }
            }
            item(key = "bottom") { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun StatsCard(s: PostStats) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionCard {
            SectionTitle("Kennzahlen")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Beiträge", formatNumber(s.total), Modifier.weight(1f))
                StatTile("pro Woche", s.postsPerWeek?.let { String.format(Locale.GERMANY, "%.1f", it) } ?: "–", Modifier.weight(1f))
            }
            if (s.avgLikes != null || s.avgComments != null) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Ø Likes", s.avgLikes?.let { formatNumber(Math.round(it)) } ?: "–", Modifier.weight(1f))
                    StatTile("Ø Kommentare", s.avgComments?.let { formatNumber(Math.round(it)) } ?: "–", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Zeitraum: ${formatDateSec(s.firstTimestamp)} – ${formatDateSec(s.lastTimestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val months = s.perMonth.takeLast(12)
            if (months.size >= 2) {
                Spacer(Modifier.height(12.dp))
                Text("Veröffentlichungen pro Monat", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                val max = months.maxOf { it.count }.coerceAtLeast(1)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    months.forEach { m ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom) {
                            Text("${m.count}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height((70f * m.count / max).coerceAtLeast(3f).dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            )
                            Text(String.format(Locale.GERMANY, "%02d/%02d", m.month, m.year % 100),
                                style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PostRow(p: PostEntry, onOpen: (String) -> Unit) {
    val link = p.permalink
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (link != null) it.clickable { onOpen(link) } else it }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (p.mediaUrl != null) {
                AsyncImage(model = p.mediaUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    when (p.kind) {
                        PostKind.REEL -> Icons.Filled.Movie
                        PostKind.VIDEO -> Icons.Filled.Videocam
                        PostKind.CAROUSEL -> Icons.Filled.ViewCarousel
                        else -> Icons.Filled.Image
                    },
                    contentDescription = p.kind.label,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${p.kind.label} · ${formatDateSec(p.timestamp)}" + if (p.mediaCountInPost > 1) " · ${p.mediaCountInPost} Medien" else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                p.caption?.takeIf { it.isNotBlank() } ?: "Ohne Bildunterschrift",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (p.likeCount != null || p.commentCount != null) {
                Text(
                    listOfNotNull(
                        p.likeCount?.let { "♥ ${formatNumber(it)}" },
                        p.commentCount?.let { "💬 ${formatNumber(it)}" },
                    ).joinToString("   "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
