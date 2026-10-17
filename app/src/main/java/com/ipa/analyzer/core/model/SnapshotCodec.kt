package com.ipa.analyzer.core.model

import com.ipa.analyzer.core.json.Json
import com.ipa.analyzer.core.json.JsonValue
import com.ipa.analyzer.core.json.jsonArr
import com.ipa.analyzer.core.json.jsonObj
import com.ipa.analyzer.core.json.toJson

/** (De-)Serialisierung der gespeicherten Datenstände als JSON. */
object SnapshotCodec {
    private const val VERSION = 1

    fun encode(s: Snapshot): String = Json.stringify(
        jsonObj(
            "version" to VERSION.toJson(),
            "id" to s.id.toJson(),
            "createdAt" to s.createdAt.toJson(),
            "label" to s.label.toJson(),
            "sourceName" to s.sourceName.toJson(),
            "hasFollowers" to JsonValue.Bool(s.hasFollowers),
            "hasFollowing" to JsonValue.Bool(s.hasFollowing),
            "profile" to s.profile?.let {
                jsonObj(
                    "username" to it.username.toJson(),
                    "name" to it.name.toJson(),
                    "bio" to it.bio.toJson(),
                    "website" to it.website.toJson(),
                    "isPrivate" to (it.isPrivate?.let { p -> JsonValue.Bool(p) } ?: JsonValue.Null),
                )
            },
            "followers" to jsonArr(s.followers.map(::encodeAccount)),
            "following" to jsonArr(s.following.map(::encodeAccount)),
            "posts" to jsonArr(s.posts.map(::encodePost)),
        )
    )

    fun encodeSummary(s: SnapshotSummary): String = Json.stringify(
        jsonObj(
            "id" to s.id.toJson(),
            "createdAt" to s.createdAt.toJson(),
            "label" to s.label.toJson(),
            "sourceName" to s.sourceName.toJson(),
            "ownerUsername" to s.ownerUsername.toJson(),
            "followerCount" to s.followerCount.toJson(),
            "followingCount" to s.followingCount.toJson(),
            "postCount" to s.postCount.toJson(),
            "hasFollowers" to JsonValue.Bool(s.hasFollowers),
            "hasFollowing" to JsonValue.Bool(s.hasFollowing),
        )
    )

    fun decodeSummary(text: String): SnapshotSummary {
        val o = Json.parse(text).obj ?: throw IllegalArgumentException("Ungültige Verlaufsdatei")
        return SnapshotSummary(
            id = o["id"]?.str ?: throw IllegalArgumentException("ID fehlt"),
            createdAt = o["createdAt"]?.long ?: 0L,
            label = o["label"]?.str ?: "",
            sourceName = o["sourceName"]?.str ?: "",
            ownerUsername = o["ownerUsername"]?.str,
            followerCount = o["followerCount"]?.long?.toInt() ?: 0,
            followingCount = o["followingCount"]?.long?.toInt() ?: 0,
            postCount = o["postCount"]?.long?.toInt() ?: 0,
            hasFollowers = o["hasFollowers"]?.bool ?: false,
            hasFollowing = o["hasFollowing"]?.bool ?: false,
        )
    }

    fun decode(text: String): Snapshot {
        val o = Json.parse(text).obj ?: throw IllegalArgumentException("Ungültige Verlaufsdatei")
        val p = o["profile"]?.obj
        return Snapshot(
            id = o["id"]?.str ?: throw IllegalArgumentException("ID fehlt"),
            createdAt = o["createdAt"]?.long ?: 0L,
            label = o["label"]?.str ?: "",
            sourceName = o["sourceName"]?.str ?: "",
            hasFollowers = o["hasFollowers"]?.bool ?: false,
            hasFollowing = o["hasFollowing"]?.bool ?: false,
            profile = p?.let {
                ExportProfileInfo(
                    username = it["username"]?.str,
                    name = it["name"]?.str,
                    bio = it["bio"]?.str,
                    website = it["website"]?.str,
                    isPrivate = it["isPrivate"]?.bool,
                )
            },
            followers = o["followers"]?.arr?.mapNotNull(::decodeAccount) ?: emptyList(),
            following = o["following"]?.arr?.mapNotNull(::decodeAccount) ?: emptyList(),
            posts = o["posts"]?.arr?.mapNotNull(::decodePost) ?: emptyList(),
        )
    }

    private fun encodeAccount(a: AccountEntry): JsonValue = jsonObj(
        "u" to a.username.toJson(),
        "h" to a.profileUrl?.toJson(),
        "t" to a.timestamp?.toJson(),
    )

    private fun decodeAccount(v: JsonValue): AccountEntry? {
        val o = v.obj ?: return null
        return AccountEntry(o["u"]?.str ?: return null, o["h"]?.str, o["t"]?.long)
    }

    private fun encodePost(p: PostEntry): JsonValue = jsonObj(
        "id" to p.id.toJson(),
        "kind" to p.kind.name.toJson(),
        "t" to p.timestamp?.toJson(),
        "caption" to p.caption?.toJson(),
        "likes" to p.likeCount?.toJson(),
        "comments" to p.commentCount?.toJson(),
        "permalink" to p.permalink?.toJson(),
        "n" to p.mediaCountInPost.toJson(),
    )

    private fun decodePost(v: JsonValue): PostEntry? {
        val o = v.obj ?: return null
        return PostEntry(
            id = o["id"]?.str ?: return null,
            kind = runCatching { PostKind.valueOf(o["kind"]?.str ?: "") }.getOrDefault(PostKind.UNKNOWN),
            timestamp = o["t"]?.long,
            caption = o["caption"]?.str,
            likeCount = o["likes"]?.long,
            commentCount = o["comments"]?.long,
            permalink = o["permalink"]?.str,
            mediaCountInPost = o["n"]?.long?.toInt() ?: 1,
        )
    }
}
