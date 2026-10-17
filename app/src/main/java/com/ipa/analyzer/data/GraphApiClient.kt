package com.ipa.analyzer.data

import com.ipa.analyzer.core.api.BusinessDiscovery
import com.ipa.analyzer.core.api.LookupResult
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** Zugangsdaten für die offizielle Instagram Graph API (vom Nutzer selbst hinterlegt). */
data class ApiCredentials(val accessToken: String, val igUserId: String, val apiVersion: String) {
    val isComplete: Boolean get() = accessToken.isNotBlank() && igUserId.isNotBlank()
}

/**
 * Ruft öffentliche Profildaten ausschließlich über die offizielle Business-Discovery-Schnittstelle ab.
 * Kein Scraping, keine inoffiziellen Endpunkte.
 */
class GraphApiClient(private val timeoutMs: Int = 15_000) {

    fun lookup(rawUsername: String, credentials: ApiCredentials): LookupResult {
        val username = BusinessDiscovery.normalizeUsername(rawUsername)
            ?: return LookupResult.InvalidUsername(rawUsername)
        if (!credentials.isComplete) return LookupResult.NotConfigured

        val url = BusinessDiscovery.buildUrl(credentials.apiVersion, credentials.igUserId, username, credentials.accessToken)
        var conn: HttpURLConnection? = null
        return try {
            conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            BusinessDiscovery.parseResponse(username, body)
        } catch (e: IOException) {
            LookupResult.NetworkError("Keine Verbindung zur Instagram-Schnittstelle: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            conn?.disconnect()
        }
    }
}
