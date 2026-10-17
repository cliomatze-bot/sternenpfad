package com.ipa.analyzer.data

import com.ipa.analyzer.core.parser.ExportFile
import com.ipa.analyzer.core.parser.InstagramExportParser
import com.ipa.analyzer.core.parser.ParsedExport
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class ImportException(message: String) : Exception(message)

/** Liest ZIP- oder JSON-Dateien eines Instagram-Datenexports. */
object ExportReader {
    private const val MAX_ENTRY_BYTES = 80L * 1024 * 1024
    private const val MAX_FILES = 200

    data class Source(val name: String, val open: () -> InputStream)

    fun read(sources: List<Source>): ParsedExport {
        if (sources.isEmpty()) throw ImportException("Keine Datei ausgewählt.")
        val files = mutableListOf<ExportFile>()
        var htmlSeen = false
        for (src in sources) {
            val lower = src.name.lowercase()
            val head = src.open().use { s -> ByteArray(4).also { readFully(s, it) } }
            val isZip = head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
            when {
                isZip -> src.open().use { input ->
                    val r = readZip(input)
                    files += r.first
                    htmlSeen = htmlSeen || r.second
                }
                lower.endsWith(".json") || head[0] == '{'.code.toByte() || head[0] == '['.code.toByte() ||
                    (head[0] == 0xEF.toByte() && head[1] == 0xBB.toByte()) ->
                    src.open().use { files += ExportFile(src.name, readLimited(it, src.name)) }
                lower.endsWith(".html") -> htmlSeen = true
                else -> throw ImportException("„${src.name}“ ist weder eine ZIP- noch eine JSON-Datei.")
            }
        }
        if (files.isEmpty() && !htmlSeen) {
            throw ImportException("Im Import wurden keine passenden Instagram-Exportdateien gefunden " +
                "(erwartet z. B. followers_1.json, following.json, posts_1.json).")
        }
        return InstagramExportParser.parse(files, htmlSeen)
    }

    fun readZip(input: InputStream): Pair<List<ExportFile>, Boolean> {
        val out = mutableListOf<ExportFile>()
        var htmlSeen = false
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name
                val fname = InstagramExportParser.fileName(name).lowercase()
                if (fname.endsWith(".html") && (fname.startsWith("follower") || fname.startsWith("following"))) htmlSeen = true
                if (fname.endsWith(".json") && InstagramExportParser.isRelevantPath(name)) {
                    if (out.size >= MAX_FILES) throw ImportException("Zu viele Dateien im Archiv.")
                    out += ExportFile(name, readLimited(zip, name))
                }
            }
        }
        return out to htmlSeen
    }

    private fun readLimited(input: InputStream, name: String): String {
        val buf = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            total += n
            if (total > MAX_ENTRY_BYTES) throw ImportException("„$name“ ist zu groß (> 80 MB).")
            buf.write(chunk, 0, n)
        }
        return buf.toString("UTF-8")
    }

    private fun readFully(s: InputStream, target: ByteArray) {
        var off = 0
        while (off < target.size) {
            val n = s.read(target, off, target.size - off)
            if (n < 0) break
            off += n
        }
    }
}
