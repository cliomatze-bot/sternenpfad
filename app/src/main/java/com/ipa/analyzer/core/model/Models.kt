package com.ipa.analyzer.core.model

/** Ein Konto aus einer Follower- oder Following-Liste. */
data class AccountEntry(
    val username: String,
    val profileUrl: String? = null,
    /** Zeitpunkt (Unix-Sekunden), zu dem gefolgt wurde – sofern im Export enthalten. */
    val timestamp: Long? = null,
) {
    val key: String get() = username.lowercase()
}

enum class PostKind(val label: String) {
    POST("Beitrag"), REEL("Reel"), VIDEO("Video"), CAROUSEL("Karussell"), IMAGE("Bild"), STORY("Story"), UNKNOWN("Unbekannt")
}

/** Ein Beitrag – aus einem Datenexport oder aus der offiziellen Graph API. */
data class PostEntry(
    val id: String,
    val kind: PostKind,
    /** Veröffentlichungszeitpunkt (Unix-Sekunden). */
    val timestamp: Long?,
    val caption: String? = null,
    val likeCount: Long? = null,
    val commentCount: Long? = null,
    val permalink: String? = null,
    val mediaUrl: String? = null,
    val mediaCountInPost: Int = 1,
)

/** Profilinformationen aus dem eigenen Datenexport. */
data class ExportProfileInfo(
    val username: String? = null,
    val name: String? = null,
    val bio: String? = null,
    val website: String? = null,
    val isPrivate: Boolean? = null,
)

/** Ein vom Nutzer importierter Datenstand. */
data class Snapshot(
    val id: String,
    /** Importzeitpunkt (Unix-Millisekunden). */
    val createdAt: Long,
    val label: String,
    val sourceName: String,
    val profile: ExportProfileInfo?,
    val followers: List<AccountEntry>,
    val following: List<AccountEntry>,
    val posts: List<PostEntry>,
    val hasFollowers: Boolean,
    val hasFollowing: Boolean,
)

/** Kurzinfo für die Verlaufsliste – ohne komplette Listen zu laden. */
data class SnapshotSummary(
    val id: String,
    val createdAt: Long,
    val label: String,
    val sourceName: String,
    val ownerUsername: String?,
    val followerCount: Int,
    val followingCount: Int,
    val postCount: Int,
    val hasFollowers: Boolean,
    val hasFollowing: Boolean,
)

fun Snapshot.summary() = SnapshotSummary(
    id = id, createdAt = createdAt, label = label, sourceName = sourceName,
    ownerUsername = profile?.username,
    followerCount = followers.size, followingCount = following.size, postCount = posts.size,
    hasFollowers = hasFollowers, hasFollowing = hasFollowing,
)

enum class ListType(val label: String) { FOLLOWERS("Follower"), FOLLOWING("Gefolgt") }

fun Snapshot.list(type: ListType) = when (type) {
    ListType.FOLLOWERS -> followers
    ListType.FOLLOWING -> following
}
