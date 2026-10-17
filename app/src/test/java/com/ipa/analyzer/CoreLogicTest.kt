package com.ipa.analyzer

import com.ipa.analyzer.core.api.BusinessDiscovery
import com.ipa.analyzer.core.api.LookupResult
import com.ipa.analyzer.core.diff.ListComparator
import com.ipa.analyzer.core.diff.ListQuery
import com.ipa.analyzer.core.diff.PostAnalyzer
import com.ipa.analyzer.core.diff.SortOrder
import com.ipa.analyzer.core.json.Json
import com.ipa.analyzer.core.json.JsonValue
import com.ipa.analyzer.core.model.AccountEntry
import com.ipa.analyzer.core.model.PostKind
import com.ipa.analyzer.core.model.Snapshot
import com.ipa.analyzer.core.parser.ExportFile
import com.ipa.analyzer.core.parser.InstagramExportParser
import com.ipa.analyzer.data.ExportReader
import com.ipa.analyzer.data.ImportException
import com.ipa.analyzer.data.SnapshotRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CoreLogicTest {

    // Strukturen entsprechen dem offiziellen Instagram-JSON-Export (Testeingaben, keine App-Daten).
    private val followersJson = """
        [
          {"title":"","media_list_data":[],"string_list_data":[{"href":"https://www.instagram.com/anna.k","value":"anna.k","timestamp":1700000000}]},
          {"title":"","media_list_data":[],"string_list_data":[{"href":"https://www.instagram.com/ben_92","value":"ben_92","timestamp":1710000000}]},
          {"title":"","media_list_data":[],"string_list_data":[{"href":"https://www.instagram.com/cara","value":"cara","timestamp":1690000000}]}
        ]
    """.trimIndent()

    // Neueres Layout: Benutzername im "title", string_list_data ohne "value"
    private val followingJson = """
        {"relationships_following":[
          {"title":"anna.k","string_list_data":[{"href":"https://www.instagram.com/_u/anna.k","timestamp":1700000100}]},
          {"title":"dora","string_list_data":[{"href":"https://www.instagram.com/_u/dora","timestamp":1720000000}]}
        ]}
    """.trimIndent()

    private val postsJson = """
        [
          {"media":[{"uri":"media/posts/202401/a.jpg","creation_timestamp":1704100000,"title":"SchÃ¶ner Tag"}]},
          {"media":[{"uri":"media/posts/202402/b.jpg","creation_timestamp":1706800000,"title":""},{"uri":"media/posts/202402/c.jpg","creation_timestamp":1706800000,"title":""}],"title":"Karussell","creation_timestamp":1706800000},
          {"media":[{"uri":"media/posts/202403/d.mp4","creation_timestamp":1709300000,"title":"Video"}]}
        ]
    """.trimIndent()

    private val reelsJson = """{"ig_reels_media":[{"media":[{"uri":"media/reels/x.mp4","creation_timestamp":1712000000,"title":"Reel 1"}]}]}"""

    private val personalJson = """
        {"profile_user":[{"string_map_data":{"Benutzername":{"href":"","value":"mein.konto","timestamp":0},
         "Name":{"value":"MÃ¼ller"},"Privates Konto":{"value":"True"}}}]}
    """.trimIndent()

    @Test fun jsonRoundTrip() {
        val text = """{"a":[1,2.5,"x\"y\n",true,null],"b":{"c":"äöü 😀"},"d":-3}"""
        val v = Json.parse(text)
        val again = Json.parse(Json.stringify(v))
        assertEquals(v, again)
        assertEquals("äöü 😀", v.obj!!["b"]!!.obj!!["c"]!!.str)
        assertEquals(-3L, v.obj!!["d"]!!.long)
    }

    @Test fun jsonRejectsGarbage() {
        assertNull(Json.parseOrNull("{\"a\":"))
        assertNull(Json.parseOrNull("[1,2"))
        assertNull(Json.parseOrNull("hello"))
        assertTrue(Json.parse("﻿[]") is JsonValue.Arr)
    }

    @Test fun parsesFollowersFollowingPostsAndProfile() {
        val r = InstagramExportParser.parse(listOf(
            ExportFile("connections/followers_and_following/followers_1.json", followersJson),
            ExportFile("connections/followers_and_following/following.json", followingJson),
            ExportFile("your_instagram_activity/content/posts_1.json", postsJson),
            ExportFile("your_instagram_activity/content/reels.json", reelsJson),
            ExportFile("personal_information/personal_information/personal_information.json", personalJson),
        ))
        assertEquals(listOf("anna.k", "ben_92", "cara"), r.followers!!.map { it.username })
        assertEquals(listOf("anna.k", "dora"), r.following!!.map { it.username })
        assertEquals(1720000000L, r.following!!.last().timestamp)
        assertEquals(4, r.posts.size)
        assertEquals(PostKind.REEL, r.posts.first().kind) // neueste zuerst
        assertEquals(PostKind.CAROUSEL, r.posts.first { it.caption == "Karussell" }.kind)
        assertEquals(PostKind.VIDEO, r.posts.first { it.caption == "Video" }.kind)
        assertEquals("Schöner Tag", r.posts.last().caption)
        assertEquals("mein.konto", r.profile!!.username)
        assertEquals("Müller", r.profile!!.name)
        assertEquals(true, r.profile!!.isPrivate)
        assertTrue(r.warnings.isEmpty())
    }

    @Test fun ignoresOtherRelationshipFilesInZip() {
        assertFalse(InstagramExportParser.isRelevantPath("connections/followers_and_following/pending_follow_requests.json"))
        assertFalse(InstagramExportParser.isRelevantPath("connections/followers_and_following/recently_unfollowed_profiles.json"))
        assertFalse(InstagramExportParser.isRelevantPath("connections/followers_and_following/close_friends.json"))
        assertTrue(InstagramExportParser.isRelevantPath("connections/followers_and_following/followers_12.json"))
        assertTrue(InstagramExportParser.isRelevantPath("following.json"))
    }

    @Test fun legacyFollowersFormatAndDeduplication() {
        val legacy = """{"relationships_followers":[
            {"title":"","string_list_data":[{"href":"https://www.instagram.com/Zoe","value":"Zoe","timestamp":1}]},
            {"title":"","string_list_data":[{"href":"https://www.instagram.com/zoe","value":"zoe","timestamp":2}]}]}"""
        val r = InstagramExportParser.parse(listOf(ExportFile("followers.json", legacy)))
        assertEquals(1, r.followers!!.size)
        assertNull(r.following)
    }

    @Test fun unknownAndBrokenFilesProduceWarnings() {
        val r = InstagramExportParser.parse(listOf(
            ExportFile("followers_1.json", "{kaputt"),
            ExportFile("something.json", """{"foo":1}"""),
        ))
        assertTrue(r.isEmpty)
        assertEquals(2, r.warnings.size)
    }

    @Test fun mojibakeIsRepaired() {
        assertEquals("Café", InstagramExportParser.fixMojibake("CafÃ©"))
        assertEquals("plain", InstagramExportParser.fixMojibake("plain"))
        assertEquals("ü ok", InstagramExportParser.fixMojibake("ü ok")) // bereits korrekt → unverändert
    }

    @Test fun compareListsAndRelationships() {
        val a = listOf(AccountEntry("anna"), AccountEntry("ben"), AccountEntry("cara"))
        val b = listOf(AccountEntry("Anna"), AccountEntry("cara"), AccountEntry("dora"))
        val d = ListComparator.compare(a, b)
        assertEquals(listOf("dora"), d.added.map { it.username })
        assertEquals(listOf("ben"), d.removed.map { it.username })
        assertEquals(listOf("Anna", "cara"), d.unchanged.map { it.username })

        val rel = ListComparator.relationship(followers = a, following = b)
        assertEquals(listOf("dora"), rel.notFollowingBack.map { it.username })
        assertEquals(listOf("ben"), rel.fans.map { it.username })
    }

    @Test fun searchAndSort() {
        val l = listOf(AccountEntry("bravo", timestamp = 20), AccountEntry("alpha", timestamp = 30),
            AccountEntry("charlie"), AccountEntry("alpine", timestamp = 10))
        assertEquals(listOf("alpha", "alpine"), ListQuery.apply(l, "@ALP", SortOrder.NAME_ASC).map { it.username })
        assertEquals(listOf("charlie", "bravo", "alpine", "alpha"), ListQuery.apply(l, "", SortOrder.NAME_DESC).map { it.username })
        assertEquals(listOf("alpha", "bravo", "alpine", "charlie"), ListQuery.apply(l, "", SortOrder.DATE_NEWEST).map { it.username })
        assertEquals(listOf("alpine", "bravo", "alpha", "charlie"), ListQuery.apply(l, "", SortOrder.DATE_OLDEST).map { it.username })
        assertEquals(3, ListQuery.apply(l, "", SortOrder.NAME_ASC, onlyWithDate = true).size)
    }

    @Test fun postStats() {
        val r = InstagramExportParser.parse(listOf(ExportFile("posts_1.json", postsJson)))
        val s = PostAnalyzer.stats(r.posts, ZoneOffset.UTC)
        assertEquals(3, s.total)
        assertEquals(3, s.perMonth.size)
        assertEquals(2024, s.perMonth.first().year)
        assertEquals(1, s.perMonth.first().month)
        assertNull(s.avgLikes) // Exporte enthalten keine Like-Zahlen – nichts erfinden
    }

    @Test fun repositoryRoundTripAndDelete() {
        val dir = Files.createTempDirectory("ipa").toFile()
        try {
            val repo = SnapshotRepository(dir)
            val parsed = InstagramExportParser.parse(listOf(
                ExportFile("followers_1.json", followersJson), ExportFile("following.json", followingJson),
                ExportFile("posts_1.json", postsJson)))
            val s1 = Snapshot("s1", 1000L, "Erster", "export.zip", parsed.profile, parsed.followers!!,
                parsed.following!!, parsed.posts, hasFollowers = true, hasFollowing = true)
            val s2 = s1.copy(id = "s2", createdAt = 2000L, followers = s1.followers.drop(1))
            repo.save(s1); repo.save(s2)
            assertEquals(listOf("s2", "s1"), repo.list().map { it.id })
            assertEquals(s1, repo.load("s1"))
            assertEquals(2, repo.list().first().followerCount)
            assertNull(repo.load("../etc/passwd"))
            assertTrue(repo.delete("s1"))
            assertEquals(listOf("s2"), repo.list().map { it.id })
            repo.deleteAll()
            assertTrue(repo.list().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun readsZipArchive() {
        val bytes = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { z ->
                fun put(n: String, c: String) { z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() }
                put("connections/followers_and_following/followers_1.json", followersJson)
                put("connections/followers_and_following/following.json", followingJson)
                put("connections/followers_and_following/pending_follow_requests.json", "{}")
                put("media/posts/a.jpg", "binary")
            }
        }.toByteArray()
        val r = ExportReader.read(listOf(ExportReader.Source("export.zip") { ByteArrayInputStream(bytes) }))
        assertEquals(3, r.followers!!.size)
        assertEquals(2, r.following!!.size)
    }

    @Test fun rejectsUnsupportedFile() {
        try {
            ExportReader.read(listOf(ExportReader.Source("bild.png") { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }))
            fail("Exception erwartet")
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("bild.png"))
        }
    }

    @Test fun businessDiscoverySuccess() {
        val body = """{"business_discovery":{"id":"178","username":"brand","name":"Brand","biography":"Bio",
            "followers_count":1200,"follows_count":15,"media_count":2,"profile_picture_url":"https://x/p.jpg",
            "media":{"data":[{"id":"1","media_type":"VIDEO","media_product_type":"REELS","timestamp":"2024-05-01T12:00:00+0000",
            "like_count":10,"comments_count":2,"permalink":"https://www.instagram.com/reel/1/"},
            {"id":"2","media_type":"CAROUSEL_ALBUM","media_product_type":"FEED","timestamp":"2024-04-01T08:30:00+0000"}]}},"id":"999"}"""
        val r = BusinessDiscovery.parseResponse("brand", body, now = 5L) as LookupResult.Success
        assertEquals(1200L, r.profile.followersCount)
        assertEquals(2, r.posts.size)
        assertEquals(PostKind.REEL, r.posts[0].kind)
        assertEquals(1714564800L, r.posts[0].timestamp)
        assertEquals(PostKind.CAROUSEL, r.posts[1].kind)
        assertNull(r.posts[1].likeCount)
    }

    @Test fun businessDiscoveryErrors() {
        val notFound = """{"error":{"message":"Invalid user id","type":"OAuthException","code":110,"error_subcode":2207013}}"""
        assertTrue(BusinessDiscovery.parseResponse("x", notFound) is LookupResult.NotAvailable)
        val badToken = """{"error":{"message":"Error validating access token","type":"OAuthException","code":190}}"""
        assertTrue(BusinessDiscovery.parseResponse("x", badToken) is LookupResult.AuthError)
        val limit = """{"error":{"message":"Application request limit reached","code":4}}"""
        assertTrue(BusinessDiscovery.parseResponse("x", limit) is LookupResult.RateLimited)
        assertTrue(BusinessDiscovery.parseResponse("x", "<html>") is LookupResult.ApiError)
    }

    @Test fun usernameNormalizationAndUrl() {
        assertEquals("some.user_1", BusinessDiscovery.normalizeUsername(" @some.user_1 "))
        assertEquals("abc", BusinessDiscovery.normalizeUsername("https://www.instagram.com/abc/?hl=de"))
        assertNull(BusinessDiscovery.normalizeUsername("bad name!"))
        assertNull(BusinessDiscovery.normalizeUsername(""))
        val url = BusinessDiscovery.buildUrl("v23.0", "123", "abc", "TOKEN")
        assertTrue(url.startsWith("https://graph.facebook.com/v23.0/123?fields=business_discovery.username%28abc%29"))
        assertTrue(url.endsWith("&access_token=TOKEN"))
        assertNotNull(BusinessDiscovery.parseTimestamp("2024-05-01T12:00:00+0000"))
    }
}
