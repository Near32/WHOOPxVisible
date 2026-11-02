package io.github.kevin.visibleclone.data.whoop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64

/**
 * Guards the handshake against the two silent failure modes: a redirect URI that does not match the
 * developer dashboard exactly, and a missing `offline` scope (which yields no refresh token).
 */
class WhoopAuthClientTest {

    private val client = WhoopAuthClient()

    private fun queryOf(url: String): Map<String, String> =
        url.substringAfter('?').split('&').associate {
            val (k, v) = it.split("=", limit = 2)
            k to URLDecoder.decode(v, "UTF-8")
        }

    @Test fun `pkce challenge is the base64url sha256 of the verifier`() {
        val pkce = client.newPkce()
        val expected = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(pkce.verifier.toByteArray()))
        assertEquals(expected, pkce.challenge)
        assertEquals(43, pkce.challenge.length)
        assertTrue("verifier must be 43-128 chars", pkce.verifier.length in 43..128)
    }

    @Test fun `authorization url carries every required parameter`() {
        val q = queryOf(client.authorizationUrl("ABCDEFGH", client.newPkce()))

        assertEquals("https://api.prod.whoop.com/oauth/oauth2/auth", URLDecoder.decode(
            client.authorizationUrl("ABCDEFGH", client.newPkce()).substringBefore('?'), "UTF-8"))
        assertEquals("code", q["response_type"])
        assertEquals("visibleclone://oauth", q["redirect_uri"])
        assertEquals("S256", q["code_challenge_method"])
        assertEquals("ABCDEFGH", q["state"])
        assertTrue(q["code_challenge"]!!.isNotBlank())
    }

    @Test fun `all scopes including offline are requested`() {
        val scope = queryOf(client.authorizationUrl("ABCDEFGH", client.newPkce()))["scope"]!!
            .split(" ").toSet()
        assertEquals(
            setOf(
                "read:recovery", "read:cycles", "read:sleep", "read:workout",
                "read:profile", "read:body_measurement", "offline",
            ),
            scope,
        )
        assertTrue("offline is required for a refresh token", "offline" in scope)
    }

    @Test fun `redirect uri matches the manifest and the dashboard`() {
        // Same literal appears in AndroidManifest.xml (scheme=visibleclone, host=oauth).
        assertEquals("visibleclone://oauth", WhoopAuthClient.REDIRECT_URI)
    }

    @Test fun `state is at least eight characters`() {
        assertTrue(client.newState().length >= 8)
        // Two calls must not collide.
        assertTrue(client.newState() != client.newState())
    }
}
