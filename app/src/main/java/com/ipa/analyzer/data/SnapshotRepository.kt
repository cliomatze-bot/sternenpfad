package com.ipa.analyzer.data

import com.ipa.analyzer.core.model.Snapshot
import com.ipa.analyzer.core.model.SnapshotCodec
import com.ipa.analyzer.core.model.SnapshotSummary
import com.ipa.analyzer.core.model.summary
import java.io.File

/**
 * Speichert importierte Datenstände ausschließlich lokal im privaten App-Speicher.
 * Pro Datenstand: <id>.json (vollständig) und <id>.meta.json (Kurzinfo für den Verlauf).
 */
class SnapshotRepository(baseDir: File) {
    private val dir = File(baseDir, "snapshots").apply { mkdirs() }

    @Synchronized
    fun save(snapshot: Snapshot) {
        writeAtomic(File(dir, "${snapshot.id}.json"), SnapshotCodec.encode(snapshot))
        writeAtomic(File(dir, "${snapshot.id}.meta.json"), SnapshotCodec.encodeSummary(snapshot.summary()))
    }

    @Synchronized
    fun list(): List<SnapshotSummary> =
        (dir.listFiles { f -> f.name.endsWith(".meta.json") } ?: emptyArray())
            .mapNotNull { f -> runCatching { SnapshotCodec.decodeSummary(f.readText()) }.getOrNull() }
            .sortedByDescending { it.createdAt }

    @Synchronized
    fun load(id: String): Snapshot? {
        if (!isSafeId(id)) return null
        val f = File(dir, "$id.json")
        if (!f.exists()) return null
        return runCatching { SnapshotCodec.decode(f.readText()) }.getOrNull()
    }

    @Synchronized
    fun delete(id: String): Boolean {
        if (!isSafeId(id)) return false
        val a = File(dir, "$id.json").delete()
        val b = File(dir, "$id.meta.json").delete()
        return a || b
    }

    @Synchronized
    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    fun totalBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun isSafeId(id: String) = id.matches(Regex("^[A-Za-z0-9_-]{1,64}$"))

    private fun writeAtomic(target: File, content: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw java.io.IOException("Datei konnte nicht gespeichert werden: ${target.name}")
        }
    }
}
