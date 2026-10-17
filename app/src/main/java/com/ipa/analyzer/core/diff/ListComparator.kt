package com.ipa.analyzer.core.diff

import com.ipa.analyzer.core.model.AccountEntry
import com.ipa.analyzer.core.model.PostEntry
import com.ipa.analyzer.core.model.PostKind
import java.time.Instant
import java.time.ZoneId

/** Ergebnis eines Vergleichs zweier Listen A (älter/Basis) und B (neuer/Ziel). */
data class ListDiff(
    /** In B, aber nicht in A. */
    val added: List<AccountEntry>,
    /** In A, aber nicht in B. */
    val removed: List<AccountEntry>,
    /** In beiden Listen. */
    val unchanged: List<AccountEntry>,
)

enum class DiffCategory(val label: String) { ADDED("Neu"), REMOVED("Entfernt"), UNCHANGED("Unverändert") }

object ListComparator {

    fun compare(a: List<AccountEntry>, b: List<AccountEntry>): ListDiff {
        val aKeys = a.mapTo(HashSet()) { it.key }
        val bKeys = b.mapTo(HashSet()) { it.key }
        return ListDiff(
            added = b.filter { it.key !in aKeys },
            removed = a.filter { it.key !in bKeys },
            unchanged = b.filter { it.key in aKeys },
        )
    }

    /** Beziehungen innerhalb eines Datenstands. */
    data class Relationship(
        /** Du folgst ihnen, sie folgen dir nicht (laut Export). */
        val notFollowingBack: List<AccountEntry>,
        /** Sie folgen dir, du folgst ihnen nicht. */
        val fans: List<AccountEntry>,
        /** Gegenseitig. */
        val mutual: List<AccountEntry>,
    )

    fun relationship(followers: List<AccountEntry>, following: List<AccountEntry>): Relationship {
        val d = compare(followers, following)
        return Relationship(notFollowingBack = d.added, fans = d.removed, mutual = d.unchanged)
    }
}

enum class SortOrder(val label: String) {
    NAME_ASC("Name A–Z"),
    NAME_DESC("Name Z–A"),
    DATE_NEWEST("Neueste zuerst"),
    DATE_OLDEST("Älteste zuerst"),
}

object ListQuery {
    fun apply(list: List<AccountEntry>, query: String, sort: SortOrder, onlyWithDate: Boolean = false): List<AccountEntry> {
        val q = query.trim().removePrefix("@").lowercase()
        val filtered = list.asSequence()
            .filter { q.isEmpty() || it.key.contains(q) }
            .filter { !onlyWithDate || it.timestamp != null }
            .toList()
        return when (sort) {
            SortOrder.NAME_ASC -> filtered.sortedBy { it.key }
            SortOrder.NAME_DESC -> filtered.sortedByDescending { it.key }
            SortOrder.DATE_NEWEST -> filtered.sortedWith(compareByDescending<AccountEntry> { it.timestamp ?: Long.MIN_VALUE }.thenBy { it.key })
            SortOrder.DATE_OLDEST -> filtered.sortedWith(compareBy<AccountEntry> { it.timestamp ?: Long.MAX_VALUE }.thenBy { it.key })
        }
    }
}

data class MonthCount(val year: Int, val month: Int, val count: Int)

data class PostStats(
    val total: Int,
    val byKind: Map<PostKind, Int>,
    val firstTimestamp: Long?,
    val lastTimestamp: Long?,
    val perMonth: List<MonthCount>,
    val avgLikes: Double?,
    val avgComments: Double?,
    val postsPerWeek: Double?,
)

object PostAnalyzer {
    fun stats(posts: List<PostEntry>, zone: ZoneId = ZoneId.systemDefault()): PostStats {
        val dated = posts.mapNotNull { it.timestamp }
        val first = dated.minOrNull()
        val last = dated.maxOrNull()
        val perMonth = dated
            .map { Instant.ofEpochSecond(it).atZone(zone) }
            .groupBy { it.year to it.monthValue }
            .map { (k, v) -> MonthCount(k.first, k.second, v.size) }
            .sortedWith(compareBy({ it.year }, { it.month }))
        val likes = posts.mapNotNull { it.likeCount }
        val comments = posts.mapNotNull { it.commentCount }
        val weeks = if (first != null && last != null && last > first) (last - first) / (7.0 * 24 * 3600) else null
        return PostStats(
            total = posts.size,
            byKind = posts.groupingBy { it.kind }.eachCount(),
            firstTimestamp = first,
            lastTimestamp = last,
            perMonth = perMonth,
            avgLikes = if (likes.isEmpty()) null else likes.average(),
            avgComments = if (comments.isEmpty()) null else comments.average(),
            postsPerWeek = weeks?.takeIf { it >= 1.0 }?.let { dated.size / it },
        )
    }
}
