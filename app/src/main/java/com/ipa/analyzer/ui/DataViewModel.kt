package com.ipa.analyzer.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ipa.analyzer.AnalyzerApp
import com.ipa.analyzer.core.diff.ListComparator
import com.ipa.analyzer.core.diff.ListDiff
import com.ipa.analyzer.core.model.ListType
import com.ipa.analyzer.core.model.Snapshot
import com.ipa.analyzer.core.model.SnapshotSummary
import com.ipa.analyzer.core.model.list
import com.ipa.analyzer.core.parser.ParsedExport
import com.ipa.analyzer.data.ExportReader
import com.ipa.analyzer.data.ImportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.util.UUID

sealed class ImportState {
    data object Idle : ImportState()
    data object Reading : ImportState()
    data class Preview(val parsed: ParsedExport, val sourceName: String) : ImportState()
    data class Error(val message: String) : ImportState()
    data class Saved(val label: String) : ImportState()
}

enum class CompareMode(val label: String) { SNAPSHOTS("Zwei Datenstände"), RELATIONSHIP("Follower ↔ Gefolgt") }

data class CompareState(
    val mode: CompareMode = CompareMode.SNAPSHOTS,
    val baseId: String? = null,
    val targetId: String? = null,
    val listType: ListType = ListType.FOLLOWERS,
    val relationshipId: String? = null,
    val loading: Boolean = false,
    val diff: ListDiff? = null,
    val relationship: ListComparator.Relationship? = null,
    val message: String? = null,
)

/** Verwaltet importierte Datenstände, Import, Verlauf und Vergleiche. */
class DataViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AnalyzerApp
    private val repo = app.repository
    private val settings = app.settings

    private val _summaries = MutableStateFlow<List<SnapshotSummary>>(emptyList())
    val summaries: StateFlow<List<SnapshotSummary>> = _summaries.asStateFlow()

    private val _active = MutableStateFlow<Snapshot?>(null)
    val active: StateFlow<Snapshot?> = _active.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _import = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _import.asStateFlow()

    private val _compare = MutableStateFlow(CompareState())
    val compare: StateFlow<CompareState> = _compare.asStateFlow()

    private val _storageBytes = MutableStateFlow(0L)
    val storageBytes: StateFlow<Long> = _storageBytes.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            val (list, active, bytes) = withContext(Dispatchers.IO) {
                val l = repo.list()
                val activeId = settings.activeSnapshotId?.takeIf { id -> l.any { it.id == id } } ?: l.firstOrNull()?.id
                Triple(l, activeId?.let { repo.load(it) }, repo.totalBytes())
            }
            _summaries.value = list
            _active.value = active
            _storageBytes.value = bytes
            _loading.value = false
            ensureCompareDefaults(list)
        }
    }

    private fun ensureCompareDefaults(list: List<SnapshotSummary>) {
        _compare.update { c ->
            val ids = list.map { it.id }
            c.copy(
                targetId = c.targetId?.takeIf { it in ids } ?: list.getOrNull(0)?.id,
                baseId = c.baseId?.takeIf { it in ids } ?: list.getOrNull(1)?.id,
                relationshipId = c.relationshipId?.takeIf { it in ids } ?: _active.value?.id ?: list.firstOrNull()?.id,
                diff = if (c.baseId in ids && c.targetId in ids) c.diff else null,
                relationship = if (c.relationshipId in ids) c.relationship else null,
            )
        }
    }

    fun setActive(id: String) {
        settings.activeSnapshotId = id
        viewModelScope.launch {
            _active.value = withContext(Dispatchers.IO) { repo.load(id) }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(id) }
            if (settings.activeSnapshotId == id) settings.activeSnapshotId = null
            refresh()
        }
    }

    fun deleteAllSnapshots() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteAll() }
            settings.activeSnapshotId = null
            _compare.value = CompareState()
            refresh()
        }
    }

    /** Löscht sämtliche lokal gespeicherten Daten der App (Datenstände und Einstellungen). */
    fun deleteEverything() {
        settings.clearAll() // synchron im Speicher, damit nachfolgende Lesezugriffe bereits leer sind
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteAll() }
            _compare.value = CompareState()
            _import.value = ImportState.Idle
            refresh()
        }
    }

    // ---------- Import ----------

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _import.value = ImportState.Reading
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val result = withContext(Dispatchers.IO) {
                try {
                    val sources = uris.map { uri ->
                        val name = displayName(uri)
                        ExportReader.Source(name) {
                            resolver.openInputStream(uri) ?: throw ImportException("„$name“ konnte nicht geöffnet werden.")
                        }
                    }
                    val parsed = ExportReader.read(sources)
                    if (parsed.isEmpty) {
                        ImportState.Error(
                            (listOf("Keine verwertbaren Follower-, Following- oder Beitragsdaten gefunden.") + parsed.warnings)
                                .joinToString("\n")
                        )
                    } else {
                        ImportState.Preview(parsed, sources.joinToString(", ") { it.name })
                    }
                } catch (e: ImportException) {
                    ImportState.Error(e.message ?: "Import fehlgeschlagen.")
                } catch (e: FileNotFoundException) {
                    ImportState.Error("Datei nicht gefunden oder kein Zugriff.")
                } catch (e: java.util.zip.ZipException) {
                    ImportState.Error("Die ZIP-Datei ist beschädigt oder unvollständig.")
                } catch (e: java.io.IOException) {
                    ImportState.Error("Lesefehler: ${e.message}")
                } catch (e: OutOfMemoryError) {
                    ImportState.Error("Die Datei ist zu groß für den verfügbaren Arbeitsspeicher. " +
                        "Tipp: Nur die JSON-Dateien aus dem Ordner „followers_and_following“ auswählen.")
                } catch (e: SecurityException) {
                    ImportState.Error("Kein Zugriff auf die ausgewählte Datei.")
                }
            }
            _import.value = result
        }
    }

    fun saveImport(label: String) {
        val preview = _import.value as? ImportState.Preview ?: return
        val p = preview.parsed
        val snapshot = Snapshot(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            label = label.trim().ifEmpty { p.profile?.username?.let { "@$it" } ?: "Import" },
            sourceName = preview.sourceName,
            profile = p.profile,
            followers = p.followers ?: emptyList(),
            following = p.following ?: emptyList(),
            posts = p.posts,
            hasFollowers = p.followers != null,
            hasFollowing = p.following != null,
        )
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.save(snapshot) }
                settings.activeSnapshotId = snapshot.id
                _import.value = ImportState.Saved(snapshot.label)
                refresh()
            } catch (e: java.io.IOException) {
                _import.value = ImportState.Error("Speichern fehlgeschlagen: ${e.message}")
            }
        }
    }

    fun resetImport() { _import.value = ImportState.Idle }

    private fun displayName(uri: Uri): String {
        val resolver = getApplication<Application>().contentResolver
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "Datei"
    }

    // ---------- Vergleich ----------

    fun setCompareMode(mode: CompareMode) = _compare.update { it.copy(mode = mode, message = null) }
    fun setBase(id: String) = _compare.update { it.copy(baseId = id, diff = null, message = null) }
    fun setTarget(id: String) = _compare.update { it.copy(targetId = id, diff = null, message = null) }
    fun setListType(t: ListType) = _compare.update { it.copy(listType = t, diff = null, message = null) }
    fun setRelationshipSnapshot(id: String) = _compare.update { it.copy(relationshipId = id, relationship = null, message = null) }

    fun preselectLatestTwo() {
        val l = _summaries.value
        if (l.size >= 2) _compare.update {
            it.copy(mode = CompareMode.SNAPSHOTS, targetId = l[0].id, baseId = l[1].id, diff = null, message = null)
        }
    }

    fun runCompare() {
        val c = _compare.value
        viewModelScope.launch {
            _compare.update { it.copy(loading = true, message = null) }
            val updated = withContext(Dispatchers.IO) {
                when (c.mode) {
                    CompareMode.SNAPSHOTS -> {
                        val a = c.baseId?.let { repo.load(it) }
                        val b = c.targetId?.let { repo.load(it) }
                        when {
                            a == null || b == null -> c.copy(message = "Bitte zwei gespeicherte Datenstände auswählen.")
                            a.id == b.id -> c.copy(message = "Bitte zwei unterschiedliche Datenstände auswählen.")
                            !hasList(a, c.listType) || !hasList(b, c.listType) -> c.copy(
                                message = "Mindestens einer der Datenstände enthält keine ${c.listType.label}-Liste.")
                            else -> {
                                // Älteren Stand immer als Basis verwenden, damit „Neu“/„Entfernt“ korrekt sind.
                                val (old, new) = if (a.createdAt <= b.createdAt) a to b else b to a
                                c.copy(diff = ListComparator.compare(old.list(c.listType), new.list(c.listType)),
                                    message = if (old.id != a.id) "Hinweis: Reihenfolge nach Datum getauscht (älterer Stand = Basis)." else null)
                            }
                        }
                    }
                    CompareMode.RELATIONSHIP -> {
                        val s = c.relationshipId?.let { repo.load(it) }
                        when {
                            s == null -> c.copy(message = "Bitte einen Datenstand auswählen.")
                            !s.hasFollowers || !s.hasFollowing -> c.copy(
                                message = "Dieser Datenstand enthält nicht beide Listen (Follower und Gefolgt).")
                            else -> c.copy(relationship = ListComparator.relationship(s.followers, s.following))
                        }
                    }
                }
            }
            _compare.value = updated.copy(loading = false)
        }
    }

    private fun hasList(s: Snapshot, t: ListType) = when (t) {
        ListType.FOLLOWERS -> s.hasFollowers
        ListType.FOLLOWING -> s.hasFollowing
    }
}
