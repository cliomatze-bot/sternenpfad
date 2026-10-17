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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ipa.analyzer.Routes
import com.ipa.analyzer.core.model.SnapshotSummary
import com.ipa.analyzer.ui.DataViewModel
import com.ipa.analyzer.ui.components.Badge
import com.ipa.analyzer.ui.components.EmptyState
import com.ipa.analyzer.ui.components.ScreenHeader
import com.ipa.analyzer.ui.components.SectionCard
import com.ipa.analyzer.ui.components.formatDateTimeMs
import com.ipa.analyzer.ui.components.formatNumber
import com.ipa.analyzer.ui.theme.Negative

@Composable
fun HistoryScreen(dataVm: DataViewModel, navigate: (String) -> Unit, navigateTab: (String) -> Unit) {
    val summaries by dataVm.summaries.collectAsStateWithLifecycle()
    val active by dataVm.active.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<SnapshotSummary?>(null) }
    var confirmAll by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "Verlauf",
            subtitle = "${summaries.size} gespeicherte Datenstände (nur lokal)",
            actions = {
                IconButton(onClick = { navigate(Routes.IMPORT) }) { Icon(Icons.Filled.Add, contentDescription = "Neuer Import") }
                if (summaries.isNotEmpty()) {
                    IconButton(onClick = { confirmAll = true }) { Icon(Icons.Filled.DeleteSweep, contentDescription = "Alle löschen") }
                }
            },
        )
        if (summaries.isEmpty()) {
            EmptyState(
                Icons.Filled.History, "Noch kein Verlauf",
                "Jeder Import wird hier mit Datum und Uhrzeit gespeichert. So kannst du später Veränderungen vergleichen.",
                actionLabel = "Datenexport importieren", onAction = { navigate(Routes.IMPORT) },
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (summaries.size >= 2) {
                    item(key = "compare") {
                        Button(
                            onClick = { dataVm.preselectLatestTwo(); dataVm.runCompare(); navigateTab(Routes.COMPARE) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                            Spacer(Modifier.padding(start = 8.dp))
                            Text("Die zwei neuesten Stände vergleichen")
                        }
                    }
                }
                itemsIndexed(summaries, key = { _, s -> s.id }) { index, s ->
                    // Verlauf ist absteigend sortiert: der nächstältere Stand folgt direkt danach.
                    val previous = summaries.getOrNull(index + 1)
                    HistoryItem(
                        s = s,
                        previous = previous,
                        isActive = active?.id == s.id,
                        onActivate = { dataVm.setActive(s.id) },
                        onDelete = { confirmDelete = s },
                    )
                }
            }
        }
    }

    confirmDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Datenstand löschen?") },
            text = { Text("„${s.label}“ vom ${formatDateTimeMs(s.createdAt)} wird dauerhaft von diesem Gerät entfernt.") },
            confirmButton = { TextButton(onClick = { dataVm.delete(s.id); confirmDelete = null }) { Text("Löschen", color = Negative) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Abbrechen") } },
        )
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Gesamten Verlauf löschen?") },
            text = { Text("Alle ${summaries.size} gespeicherten Datenstände werden dauerhaft gelöscht.") },
            confirmButton = { TextButton(onClick = { dataVm.deleteAllSnapshots(); confirmAll = false }) { Text("Alles löschen", color = Negative) } },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun HistoryItem(
    s: SnapshotSummary,
    previous: SnapshotSummary?,
    isActive: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard(onClick = onActivate) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.label, style = MaterialTheme.typography.titleMedium)
                Text(formatDateTimeMs(s.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isActive) Badge("Aktiv", MaterialTheme.colorScheme.primary)
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Löschen") }
        }
        Spacer(Modifier.height(6.dp))
        val parts = buildList {
            add("Follower: " + if (s.hasFollowers) formatNumber(s.followerCount) + delta(s.followerCount, previous?.takeIf { it.hasFollowers }?.followerCount) else "–")
            add("Gefolgt: " + if (s.hasFollowing) formatNumber(s.followingCount) + delta(s.followingCount, previous?.takeIf { it.hasFollowing }?.followingCount) else "–")
            if (s.postCount > 0) add("Beiträge: ${formatNumber(s.postCount)}")
        }
        Text(parts.joinToString("  ·  "), style = MaterialTheme.typography.bodyMedium)
        if (previous != null && (s.hasFollowers || s.hasFollowing)) {
            Text(
                "Δ gegenüber Stand vom ${formatDateTimeMs(previous.createdAt)} (Anzahl)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Quelle: ${s.sourceName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (!isActive) {
            OutlinedButton(onClick = onActivate, modifier = Modifier.padding(top = 6.dp)) { Text("Als aktiv verwenden") }
        }
    }
}

private fun delta(now: Int, before: Int?): String {
    if (before == null) return ""
    val d = now - before
    return when {
        d > 0 -> " (+$d)"
        d < 0 -> " ($d)"
        else -> " (±0)"
    }
}
