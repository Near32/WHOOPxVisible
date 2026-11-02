package io.github.kevin.visibleclone.data.whoop

import io.github.kevin.visibleclone.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * WHOOP OAuth 2.0 with PKCE (S256), using only java.net — no HTTP library.
 *
 * WHOOP's token endpoint accepts a client secret, which is compiled in from local.properties for
 * this single-user sideloaded app. That is a deliberate, documented trade-off: there is no server
 * to hide it behind, and the app is never distributed.
 */
class WhoopAuthClient(private val transport: HttpTransport = HttpTransport()) {

    companion object {
        const val AUTH_URL = "https://api.prod.whoop.com/oauth/oauth2/auth"
        const val TOKEN_URL = "https://api.prod.whoop.com/oauth/oauth2/token"
        const val REDIRECT_URI = "visibleclone://oauth"
        val SCOPES = listOf(
            "read:recovery", "read:cycles", "read:sleep", "read:workout",
            "read:profile", "read:body_measurement", "offline",
        )
    }

    data class PkcePair(val verifier: String, val challenge: String)

    fun newPkce(): PkcePair {
        val bytes = ByteArray(64).also { SecureRandom().nextBytes(it) }
        val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        return PkcePair(verifier, challenge)
    }

    fun newState(): String =
        (1..12).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"[SecureRandom().nextInt(32)] }.joinToString("")

    fun authorizationUrl(state: String, pkce: PkcePair): String {
        val params = mapOf(
            "client_id" to BuildConfig.WHOOP_CLIENT_ID,
            "redirect_uri" to REDIRECT_URI,
            "response_type" to "code",
            "scope" to SCOPES.joinToString(" "),
            "state" to state,
            "code_challenge" to pkce.challenge,
            "code_challenge_method" to "S256",
        )
        return AUTH_URL + "?" + params.entries.joinToString("&") { (k, v) -> "$k=${enc(v)}" }
    }

    suspend fun exchangeCode(code: String, verifier: String): TokenResponse {
        val form = mapOf(
            "grant_type" to "authorization_code",
            "code" to code,
            "client_id" to BuildConfig.WHOOP_CLIENT_ID,
            "client_secret" to BuildConfig.WHOOP_CLIENT_SECRET,
            "redirect_uri" to REDIRECT_URI,
            "code_verifier" to verifier,
        )
        return parseToken(transport.postForm(TOKEN_URL, form))
    }

    suspend fun refresh(refreshToken: String): TokenResponse {
        val form = mapOf(
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
            "client_id" to BuildConfig.WHOOP_CLIENT_ID,
            "client_secret" to BuildConfig.WHOOP_CLIENT_SECRET,
            "scope" to "offline",
        )
        return parseToken(transport.postForm(TOKEN_URL, form))
    }

    private fun parseToken(body: String): TokenResponse {
        val obj = Json.parseToJsonElement(body).jsonObject
        fun str(k: String) = obj[k]?.jsonPrimitive?.content
        val access = str("access_token") ?: throw IOException("No access_token in $body")
        val expiresIn = str("expires_in")?.toLongOrNull() ?: 3600L
        return TokenResponse(
            accessToken = access,
            refreshToken = str("refresh_token"),
            expiresAtMs = System.currentTimeMillis() + expiresIn * 1000,
            scope = str("scope"),
        )
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMs: Long,
    val scope: String?,
)

/** Minimal form-POST + JSON body transport. */
class HttpTransport {

    suspend fun postForm(url: String, form: Map<String, String>): String = withContext(Dispatchers.IO) {
        val payload = form.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }.toByteArray()

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        conn.outputStream.use { it.write(payload) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw IOException("HTTP $code: $text")
        text
    }

    suspend fun get(url: String, bearer: String): String = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $bearer")
            setRequestProperty("Accept", "application/json")
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        if (code == 429) throw RateLimitedException(text)
        if (code == 401) throw UnauthorizedException(text)
        if (code !in 200..299) throw IOException("HTTP $code: $text")
        text
    }
}

class RateLimitedException(msg: String) : IOException(msg)
class UnauthorizedException(msg: String) : IOException(msg)
