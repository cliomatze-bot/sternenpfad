package com.ipa.analyzer.core.parser

import com.ipa.analyzer.core.json.Json
import com.ipa.analyzer.core.json.JsonParseException
import com.ipa.analyzer.core.json.JsonValue
import com.ipa.analyzer.core.model.AccountEntry
import com.ipa.analyzer.core.model.ExportProfileInfo
import com.ipa.analyzer.core.model.PostEntry
import com.ipa.analyzer.core.model.PostKind
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Eine Datei aus dem Export (Pfad innerhalb der ZIP bzw. Dateiname) mit Inhalt. */
data class ExportFile(val path: String, val content: String)

data class ParsedExport(
    val profile: ExportProfileInfo?,
    /** null = im Import nicht enthalten (nicht: leere Liste). */
    val followers: List<AccountEntry>?,
    val following: List<AccountEntry>?,
    val posts: List<PostEntry>,
    val recognizedFiles: List<String>,
    val warnings: List<String>,
) {
    val isEmpty: Boolean get() = followers == null && following == null && posts.isEmpty() && profile == null
}

/**
 * Liest offizielle Instagram-Datenexporte („Deine Informationen herunterladen", Format JSON).
 * Unterstützt das aktuelle Layout (connections/followers_and_following/followers_1.json …)
 * sowie ältere Varianten (relationships_followers / relationships_following).
 */
object InstagramExportParser {

    private val followersName = Regex("^followers(_\\d+)?\\.json$")
    private val followingName = Regex("^following(_\\d+)?\\.json$")
    private val postsName = Regex("^posts(_\\d+)?\\.json$")
    private val reelsName = Regex("^reels\\.json$")
    private val personalName = Regex("^personal_information\\.json$")

    /** Prüft anhand des Pfads, ob eine Datei aus einer ZIP überhaupt eingelesen werden muss. */
    fun isRelevantPath(path: String): Boolean {
        val name = fileName(path).lowercase()
        return followersName.matches(name) || followingName.matches(name) || postsName.matches(name) ||
            reelsName.matches(name) || personalName.matches(name)
    }

    fun parse(files: List<ExportFile>, htmlFilesSeen: Boolean = false): ParsedExport {
        val warnings = mutableListOf<String>()
        val recognized = mutableListOf<String>()
        var followers: MutableList<AccountEntry>? = null
        var following: MutableList<AccountEntry>? = null
        val posts = mutableListOf<PostEntry>()
        var profile: ExportProfileInfo? = null

        for (file in files.sortedBy { it.path }) {
            val name = fileName(file.path).lowercase()
            val root = try {
                Json.parse(file.content)
            } catch (e: JsonParseException) {
                warnings += "${fileName(file.path)}: kein gültiges JSON (${e.message})"
                continue
            }

            val kind = classify(name, root)
            when (kind) {
                FileKind.FOLLOWERS -> {
                    val entries = parseRelationships(root, "relationships_followers")
                    followers = (followers ?: mutableListOf()).apply { addAll(entries) }
                    recognized += "${fileName(file.path)} → ${entries.size} Follower"
                }
                FileKind.FOLLOWING -> {
                    val entries = parseRelationships(root, "relationships_following")
                    following = (following ?: mutableListOf()).apply { addAll(entries) }
                    recognized += "${fileName(file.path)} → ${entries.size} gefolgte Konten"
                }
                FileKind.POSTS -> {
                    val p = parsePosts(root)
                    posts += p
                    recognized += "${fileName(file.path)} → ${p.size} Beiträge"
                }
                FileKind.REELS -> {
                    val p = parseReels(root)
                    posts += p
                    recognized += "${fileName(file.path)} → ${p.size} Reels"
                }
                FileKind.PERSONAL -> {
                    profile = parsePersonalInfo(root)
                    recognized += "${fileName(file.path)} → Profilinformationen"
                }
                FileKind.UNKNOWN -> warnings += "${fileName(file.path)}: Struktur nicht erkannt – übersprungen"
            }
        }

        if (htmlFilesSeen && followers == null && following == null) {
            warnings += "Der Export liegt im HTML-Format vor. Bitte den Export bei Instagram im Format „JSON“ anfordern."
        }

        return ParsedExport(
            profile = profile,
            followers = followers?.let { dedupe(it) },
            following = following?.let { dedupe(it) },
            posts = posts.distinctBy { it.id }.sortedByDescending { it.timestamp ?: 0L },
            recognizedFiles = recognized,
            warnings = warnings,
        )
    }

    private enum class FileKind { FOLLOWERS, FOLLOWING, POSTS, REELS, PERSONAL, UNKNOWN }

    private fun classify(name: String, root: JsonValue): FileKind {
        val obj = root.obj
        if (obj != null) {
            if (obj["relationships_following"] != null) return FileKind.FOLLOWING
            if (obj["relationships_followers"] != null) return FileKind.FOLLOWERS
            if (obj["ig_reels_media"] != null) return FileKind.REELS
            if (obj["profile_user"] != null) return FileKind.PERSONAL
        }
        val arr = root.arr
        if (arr != null) {
            val first = arr.firstOrNull()?.obj
            val looksLikeRelationships = arr.isEmpty() || first?.get("string_list_data") != null
            val looksLikePosts = arr.isEmpty() || first?.get("media") != null
            if (followersName.matches(name) && looksLikeRelationships) return FileKind.FOLLOWERS
            if (followingName.matches(name) && looksLikeRelationships) return FileKind.FOLLOWING
            if (postsName.matches(name) && looksLikePosts) return FileKind.POSTS
            // Einzeln ausgewählte, umbenannte Datei: Relationship-Struktur ohne eindeutigen Namen
            if (first?.get("string_list_data") != null) {
                if (name.contains("following")) return FileKind.FOLLOWING
                if (name.contains("follower")) return FileKind.FOLLOWERS
            }
            if (first?.get("media") != null && name.contains("post")) return FileKind.POSTS
        }
        return FileKind.UNKNOWN
    }

    private fun parseRelationships(root: JsonValue, key: String): List<AccountEntry> {
        val items = root.obj?.get(key)?.arr ?: root.arr ?: emptyList()
        return items.mapNotNull { item ->
            val o = item.obj ?: return@mapNotNull null
            val sld = o["string_list_data"]?.arr?.firstOrNull()?.obj
            val href = sld?.get("href")?.str?.let(::fixMojibake)
            val value = sld?.get("value")?.str?.let(::fixMojibake)
            val title = o["title"]?.str?.let(::fixMojibake)
            val username = listOf(value, title, usernameFromUrl(href))
                .firstOrNull { !it.isNullOrBlank() }?.trim()?.removePrefix("@")
                ?: return@mapNotNull null
            AccountEntry(
                username = username,
                profileUrl = href ?: "https://www.instagram.com/$username/",
                timestamp = sld?.get("timestamp")?.long?.takeIf { it > 0 },
            )
        }
    }

    private fun parsePosts(root: JsonValue): List<PostEntry> =
        (root.arr ?: emptyList()).mapNotNull { postFromExport(it, reel = false) }

    private fun parseReels(root: JsonValue): List<PostEntry> =
        (root.obj?.get("ig_reels_media")?.arr ?: emptyList()).mapNotNull { postFromExport(it, reel = true) }

    private fun postFromExport(item: JsonValue, reel: Boolean): PostEntry? {
        val o = item.obj ?: return null
        val media = o["media"]?.arr?.mapNotNull { it.obj } ?: emptyList()
        val first = media.firstOrNull()
        val ts = o["creation_timestamp"]?.long ?: first?.get("creation_timestamp")?.long
        val caption = (o["title"]?.str?.takeIf { it.isNotBlank() } ?: first?.get("title")?.str)
            ?.let(::fixMojibake)?.takeIf { it.isNotBlank() }
        val uri = first?.get("uri")?.str
        val kind = when {
            reel -> PostKind.REEL
            media.size > 1 -> PostKind.CAROUSEL
            uri != null && (uri.endsWith(".mp4", true) || uri.endsWith(".mov", true)) -> PostKind.VIDEO
            uri != null -> PostKind.IMAGE
            else -> PostKind.POST
        }
        if (ts == null && uri == null && caption == null) return null
        return PostEntry(
            id = uri ?: "export-${ts}-${caption.hashCode()}",
            kind = kind,
            timestamp = ts,
            caption = caption,
            mediaCountInPost = media.size.coerceAtLeast(1),
        )
    }

    private fun parsePersonalInfo(root: JsonValue): ExportProfileInfo? {
        val map = root.obj?.get("profile_user")?.arr?.firstOrNull()?.obj?.get("string_map_data")?.obj?.map
            ?: return null
        fun find(vararg keys: String): String? {
            for ((k, v) in map) {
                val lk = k.lowercase()
                if (keys.any { lk == it }) {
                    return v.obj?.get("value")?.str?.let(::fixMojibake)?.takeIf { it.isNotBlank() }
                }
            }
            return null
        }
        val privateRaw = find("private account", "privates konto", "privat")
        return ExportProfileInfo(
            username = find("username", "benutzername", "nutzername"),
            name = find("name"),
            bio = find("bio", "biografie", "steckbrief"),
            website = find("website", "webseite"),
            isPrivate = when (privateRaw?.lowercase()) {
                "true", "wahr", "ja", "yes" -> true
                "false", "falsch", "nein", "no" -> false
                else -> null
            },
        )
    }

    private fun dedupe(list: List<AccountEntry>): List<AccountEntry> = list.distinctBy { it.key }

    fun usernameFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val m = Regex("instagram\\.com/(?:_u/)?([A-Za-z0-9._]+)").find(url) ?: return null
        return m.groupValues[1].takeIf { it.isNotBlank() }
    }

    fun fileName(path: String): String = path.substringAfterLast('/').substringAfterLast('\\')

    /**
     * Instagram-Exporte kodieren Nicht-ASCII-Zeichen als Ã¤ o. Ä. (UTF-8-Bytes als Latin-1).
     * Diese Funktion stellt die ursprünglichen Zeichen wieder her, wenn das eindeutig möglich ist.
     */
    fun fixMojibake(s: String): String {
        if (s.none { it.code in 0x80..0xFF } || s.any { it.code > 0xFF }) return s
        val bytes = ByteArray(s.length) { s[it].code.toByte() }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            s
        }
    }
}
