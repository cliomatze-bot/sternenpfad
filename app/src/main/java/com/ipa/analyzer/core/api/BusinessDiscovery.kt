package com.ipa.analyzer.core.api

import com.ipa.analyzer.core.json.Json
import com.ipa.analyzer.core.json.JsonValue
import com.ipa.analyzer.core.model.PostEntry
import com.ipa.analyzer.core.model.PostKind
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Öffentliche Profildaten, wie sie die offizielle Instagram Graph API (Business Discovery) liefert. */
data class PublicProfile(
    val id: String?,
    val username: String,
    val name: String?,
    val biography: String?,
    val website: String?,
    val profilePictureUrl: String?,
    val followersCount: Long?,
    val followsCount: Long?,
    val mediaCount: Long?,
)

sealed class LookupResult {
    data class Success(val profile: PublicProfile, val posts: List<PostEntry>, val fetchedAt: Long) : LookupResult()
    /** Konto existiert nicht oder ist kein professionelles (Business/Creator-)Konto. */
    data class NotAvailable(val username: String, val apiMessage: String?) : LookupResult()
    data class AuthError(val message: String) : LookupResult()
    data class RateLimited(val message: String) : LookupResult()
    data class ApiError(val code: Int?, val message: String) : LookupResult()
    data class NetworkError(val message: String) : LookupResult()
    data object NotConfigured : LookupResult()
    data class InvalidUsername(val input: String) : LookupResult()
}

object BusinessDiscovery {

    const val DEFAULT_API_VERSION = "v23.0"
    private val usernamePattern = Regex("^[A-Za-z0-9._]{1,30}$")

    fun normalizeUsername(input: String): String? {
        val trimmed = input.trim()
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("www.").removePrefix("instagram.com/")
            .removePrefix("@")
            .substringBefore('/').substringBefore('?')
        return trimmed.takeIf { usernamePattern.matches(it) }
    }

    fun buildUrl(apiVersion: String, igUserId: String, username: String, accessToken: String, mediaLimit: Int = 25): String {
        val fields = "business_discovery.username($username){id,username,name,biography,website," +
            "profile_picture_url,followers_count,follows_count,media_count," +
            "media.limit($mediaLimit){id,caption,media_type,media_product_type,timestamp,like_count," +
            "comments_count,permalink,media_url,thumbnail_url}}"
        val v = apiVersion.trim().ifEmpty { DEFAULT_API_VERSION }
        return "https://graph.facebook.com/$v/${enc(igUserId.trim())}?fields=${enc(fields)}&access_token=${enc(accessToken.trim())}"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Wertet eine Antwort der Graph API aus (HTTP-Body, unabhängig vom Statuscode). */
    fun parseResponse(username: String, body: String, now: Long = System.currentTimeMillis()): LookupResult {
        val root = Json.parseOrNull(body)?.obj
            ?: return LookupResult.ApiError(null, "Unerwartete Antwort der Schnittstelle")

        root["error"]?.obj?.let { err ->
            val code = err["code"]?.long?.toInt()
            val sub = err["error_subcode"]?.long?.toInt()
            val msg = err["error_user_msg"]?.str ?: err["message"]?.str ?: "Unbekannter Fehler"
            return when {
                code == 190 || code == 102 -> LookupResult.AuthError(
                    "Zugriffstoken ungültig oder abgelaufen. Bitte in den Einstellungen ein gültiges Token hinterlegen.")
                code == 10 || code == 200 || (code != null && code in 200..299) -> LookupResult.AuthError(
                    "Fehlende Berechtigung (benötigt: instagram_basic, pages_read_engagement bzw. instagram_business_basic). API: $msg")
                code == 4 || code == 17 || code == 32 || code == 613 -> LookupResult.RateLimited(
                    "Anfragelimit der Instagram-Schnittstelle erreicht. Bitte später erneut versuchen.")
                code == 110 || sub == 2207013 || (code == 100 && msg.contains("business", true)) ||
                    (code == 100 && msg.contains("Cannot find User", true)) ->
                    LookupResult.NotAvailable(username, msg)
                else -> LookupResult.ApiError(code, msg)
            }
        }

        val bd = root["business_discovery"]?.obj ?: return LookupResult.NotAvailable(username, null)
        val profile = PublicProfile(
            id = bd["id"]?.str,
            username = bd["username"]?.str ?: username,
            name = bd["name"]?.str,
            biography = bd["biography"]?.str,
            website = bd["website"]?.str,
            profilePictureUrl = bd["profile_picture_url"]?.str,
            followersCount = bd["followers_count"]?.long,
            followsCount = bd["follows_count"]?.long,
            mediaCount = bd["media_count"]?.long,
        )
        val posts = bd["media"]?.obj?.get("data")?.arr?.mapNotNull(::parseMedia) ?: emptyList()
        return LookupResult.Success(profile, posts, now)
    }

    private val tsFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

    fun parseTimestamp(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(s, tsFormat).toEpochSecond() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(s).toEpochSecond() }.getOrNull()
    }

    private fun parseMedia(v: JsonValue): PostEntry? {
        val o = v.obj ?: return null
        val type = o["media_type"]?.str
        val product = o["media_product_type"]?.str
        val kind = when {
            product == "REELS" -> PostKind.REEL
            product == "STORY" -> PostKind.STORY
            type == "CAROUSEL_ALBUM" -> PostKind.CAROUSEL
            type == "VIDEO" -> PostKind.VIDEO
            type == "IMAGE" -> PostKind.IMAGE
            else -> PostKind.UNKNOWN
        }
        return PostEntry(
            id = o["id"]?.str ?: return null,
            kind = kind,
            timestamp = parseTimestamp(o["timestamp"]?.str),
            caption = o["caption"]?.str,
            likeCount = o["like_count"]?.long,
            commentCount = o["comments_count"]?.long,
            permalink = o["permalink"]?.str,
            mediaUrl = o["thumbnail_url"]?.str ?: o["media_url"]?.str,
        )
    }
}
