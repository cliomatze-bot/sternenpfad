package com.ipa.analyzer.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipa.analyzer.Routes
import com.ipa.analyzer.core.diff.ListComparator
import com.ipa.analyzer.core.diff.ListQuery
import com.ipa.analyzer.core.diff.SortOrder
import com.ipa.analyzer.core.model.ListType
import com.ipa.analyzer.core.model.Snapshot
import com.ipa.analyzer.core.model.list
import com.ipa.analyzer.ui.DataViewModel
import com.ipa.analyzer.ui.components.AccountRow
import com.ipa.analyzer.ui.components.EmptyState
import com.ipa.analyzer.ui.components.ScreenHeader
import com.ipa.analyzer.ui.components.SearchField
import com.ipa.analyzer.ui.components.formatDateTimeMs
import com.ipa.analyzer.ui.components.formatNumber
import com.ipa.analyzer.ui.theme.Negative
import com.ipa.analyzer.ui.theme.Positive

private enum class RelFilter { ALL, NOT_RECIPROCATED, MUTUAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(type: ListType, dataVm: DataViewModel, switchTo: () -> Unit, navigate: (String) -> Unit) {
    val active by dataVm.active.collectAsStateWithLifecycle()
    val loading by dataVm.loading.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current

    var query by rememberSaveable(type) { mutableStateOf("") }
    var sort by rememberSaveable(type) { mutableStateOf(SortOrder.NAME_ASC) }
    var filter by rememberSaveable(type) { mutableStateOf(RelFilter.ALL) }
    var onlyWithDate by rememberSaveable(type) { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = if (type == ListType.FOLLOWERS) "Follower-Liste" else "Following-Liste",
            subtitle = active?.let { "${it.label} · ${formatDateTimeMs(it.createdAt)}" },
            actions = {
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.FilterList, contentDescription = "Sortieren") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortOrder.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text((if (s == sort) "✓ " else "") + s.label) },
                                onClick = { sort = s; sortMenu = false },
                            )
                        }
                    }
                }
            },
        )
        TabRow(
            selectedTabIndex = if (type == ListType.FOLLOWERS) 0 else 1,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Tab(selected = type == ListType.FOLLOWERS, onClick = { if (type != ListType.FOLLOWERS) switchTo() },
                text = { Text("Follower") })
            Tab(selected = type == ListType.FOLLOWING, onClick = { if (type != ListType.FOLLOWING) switchTo() },
                text = { Text("Gefolgt") })
        }

        val snap = active
        if (snap == null) {
            if (!loading) {
                EmptyState(
                    Icons.Filled.UploadFile,
                    "Keine Daten vorhanden",
                    "Instagram stellt Follower-Listen nicht über eine öffentliche Schnittstelle bereit. " +
                        "Importiere deinen eigenen Datenexport, um deine Listen zu sehen.",
                    actionLabel = "Datenexport importieren",
                    onAction = { navigate(Routes.IMPORT) },
                )
            }
        } else if ((type == ListType.FOLLOWERS && !snap.hasFollowers) || (type == ListType.FOLLOWING && !snap.hasFollowing)) {
            EmptyState(
                Icons.Filled.Group,
                "Liste nicht im Import enthalten",
                "Der aktive Datenstand enthält keine ${type.label}-Liste. Importiere einen vollständigen Export " +
                    "oder wähle im Verlauf einen anderen Datenstand.",
                actionLabel = "Importieren",
                onAction = { navigate(Routes.IMPORT) },
            )
        } else {
            AccountListContent(type, snap, query, { query = it }, sort, filter, { filter = it }, onlyWithDate,
                { onlyWithDate = it }, open = { url -> runCatching { uri.openUri(url) } })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountListContent(
    type: ListType,
    s: Snapshot,
    query: String,
    onQuery: (String) -> Unit,
    sort: SortOrder,
    filter: RelFilter,
    onFilter: (RelFilter) -> Unit,
    onlyWithDate: Boolean,
    onOnlyWithDate: (Boolean) -> Unit,
    open: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        val base = s.list(type)
        val bothAvailable = s.hasFollowers && s.hasFollowing
        val rel = remember(s.id) { if (bothAvailable) ListComparator.relationship(s.followers, s.following) else null }
        val filteredByRel = when {
            rel == null || filter == RelFilter.ALL -> base
            filter == RelFilter.MUTUAL -> rel.mutual
            type == ListType.FOLLOWING -> rel.notFollowingBack
            else -> rel.fans
        }
        val shown = remember(filteredByRel, query, sort, onlyWithDate) {
            ListQuery.apply(filteredByRel, query, sort, onlyWithDate)
        }
        val mutualKeys = remember(rel) { rel?.mutual?.mapTo(HashSet()) { it.key } ?: emptySet() }

        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SearchField(query, onQuery, "In ${formatNumber(base.size)} Konten suchen")
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = filter == RelFilter.ALL, onClick = { onFilter(RelFilter.ALL) }, label = { Text("Alle") })
                if (rel != null) {
                    FilterChip(
                        selected = filter == RelFilter.NOT_RECIPROCATED,
                        onClick = { onFilter(RelFilter.NOT_RECIPROCATED) },
                        label = { Text(if (type == ListType.FOLLOWING) "Folgen dir nicht zurück" else "Du folgst nicht zurück") },
                    )
                    FilterChip(selected = filter == RelFilter.MUTUAL, onClick = { onFilter(RelFilter.MUTUAL) }, label = { Text("Gegenseitig") })
                }
                FilterChip(selected = onlyWithDate, onClick = { onOnlyWithDate(!onlyWithDate) }, label = { Text("Nur mit Datum") })
            }
            Text(
                "${formatNumber(shown.size)} Einträge · ${sort.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        HorizontalDivider()
        if (shown.isEmpty()) {
            EmptyState(Icons.Filled.Group, "Keine Treffer", "Passe Suche oder Filter an.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.key }) { entry ->
                    val (label, color) = when {
                        rel == null -> null to Color.Unspecified
                        entry.key in mutualKeys -> "gegenseitig" to Positive
                        type == ListType.FOLLOWING -> "folgt nicht zurück" to Negative
                        else -> "Fan" to Color.Unspecified
                    }
                    AccountRow(entry, onOpen = open, badgeText = label, badgeColor = color)
                }
            }
        }
    }
}
