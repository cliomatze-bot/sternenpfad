package com.ipa.analyzer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import com.ipa.analyzer.core.diff.ListQuery
import com.ipa.analyzer.core.diff.SortOrder
import com.ipa.analyzer.core.model.AccountEntry
import com.ipa.analyzer.core.model.ListType
import com.ipa.analyzer.core.model.SnapshotSummary
import com.ipa.analyzer.ui.CompareMode
import com.ipa.analyzer.ui.DataViewModel
import com.ipa.analyzer.ui.components.AccountRow
import com.ipa.analyzer.ui.components.EmptyState
import com.ipa.analyzer.ui.components.InfoBox
import com.ipa.analyzer.ui.components.Picker
import com.ipa.analyzer.ui.components.ScreenHeader
import com.ipa.analyzer.ui.components.SearchField
import com.ipa.analyzer.ui.components.StatTile
import com.ipa.analyzer.ui.components.formatDateTimeMs
import com.ipa.analyzer.ui.components.formatNumber
import com.ipa.analyzer.ui.theme.Negative
import com.ipa.analyzer.ui.theme.Neutral
import com.ipa.analyzer.ui.theme.Positive

private data class Category(val title: String, val entries: List<AccountEntry>, val color: Color)

private fun summaryLabel(s: SnapshotSummary) = "${s.label} · ${formatDateTimeMs(s.createdAt)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(dataVm: DataViewModel, navigate: (String) -> Unit) {
    val summaries by dataVm.summaries.collectAsStateWithLifecycle()
    val c by dataVm.compare.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Listenvergleich", subtitle = "Neue, entfernte und unveränderte Einträge")

        TabRow(selectedTabIndex = c.mode.ordinal, containerColor = MaterialTheme.colorScheme.background) {
            CompareMode.entries.forEach { m ->
                Tab(selected = c.mode == m, onClick = { dataVm.setCompareMode(m); tab = 0 }, text = { Text(m.label) })
            }
        }

        if (summaries.isEmpty()) {
            EmptyState(
                Icons.Filled.SwapHoriz, "Keine Datenstände",
                "Für einen Vergleich werden importierte Datenexporte benötigt.",
                actionLabel = "Datenexport importieren", onAction = { navigate(Routes.IMPORT) },
            )
        } else {
            val categories: List<Category>? = remember(c.mode, c.diff, c.relationship) {
                when (c.mode) {
                    CompareMode.SNAPSHOTS -> c.diff?.let {
                        listOf(
                            Category("Neu", it.added, Positive),
                            Category("Entfernt", it.removed, Negative),
                            Category("Unverändert", it.unchanged, Neutral),
                        )
                    }
                    CompareMode.RELATIONSHIP -> c.relationship?.let {
                        listOf(
                            Category("Folgen nicht zurück", it.notFollowingBack, Negative),
                            Category("Du folgst nicht", it.fans, Positive),
                            Category("Gegenseitig", it.mutual, Neutral),
                        )
                    }
                }
            }
            val selected = categories?.let { it[tab.coerceIn(0, it.lastIndex)] }
            val shown = remember(selected, query) {
                selected?.let { ListQuery.apply(it.entries, query, SortOrder.NAME_ASC) } ?: emptyList()
            }

            // Eine einzige scrollbare Liste – funktioniert auch auf kleinen Displays.
            LazyColumn(Modifier.fillMaxSize()) {
                item(key = "controls") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (c.mode == CompareMode.SNAPSHOTS) {
                            if (summaries.size < 2) {
                                InfoBox(
                                    Icons.Filled.Info, "Zweiter Datenstand fehlt",
                                    "Importiere zu einem späteren Zeitpunkt einen weiteren Export, um Veränderungen zu sehen.",
                                )
                            }
                            Picker("Basis (älterer Stand)", summaries.firstOrNull { it.id == c.baseId }, summaries, ::summaryLabel,
                                { dataVm.setBase(it.id) })
                            Picker("Vergleich (neuerer Stand)", summaries.firstOrNull { it.id == c.targetId }, summaries, ::summaryLabel,
                                { dataVm.setTarget(it.id) })
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ListType.entries.forEach { t ->
                                    FilterChip(selected = c.listType == t, onClick = { dataVm.setListType(t) }, label = { Text(t.label) })
                                }
                            }
                        } else {
                            Picker("Datenstand", summaries.firstOrNull { it.id == c.relationshipId }, summaries, ::summaryLabel,
                                { dataVm.setRelationshipSnapshot(it.id) })
                        }
                        Button(onClick = { dataVm.runCompare(); tab = 0 }, enabled = !c.loading, modifier = Modifier.fillMaxWidth()) {
                            Text("Vergleichen")
                        }
                        if (c.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        c.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
                if (categories != null && selected != null) {
                    item(key = "stats") {
                        Column {
                            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                categories.forEachIndexed { i, cat ->
                                    StatTile(cat.title, formatNumber(cat.entries.size), Modifier.weight(1f), accent = cat.color) { tab = i }
                                }
                            }
                            Text(
                                "Tippe auf eine Kategorie. Angezeigt: ${selected.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                            Column(Modifier.padding(horizontal = 16.dp)) {
                                SearchField(query, { query = it }, "In „${selected.title}“ suchen")
                            }
                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider()
                        }
                    }
                    if (shown.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(Icons.Filled.Info, "Keine Einträge", "In dieser Kategorie gibt es keine Einträge.")
                        }
                    } else {
                        items(shown, key = { "e_" + it.key }) { e ->
                            AccountRow(e, onOpen = { url -> runCatching { uri.openUri(url) } },
                                badgeText = selected.title, badgeColor = selected.color)
                        }
                    }
                }
            }
        }
    }
}
