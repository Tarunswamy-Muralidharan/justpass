package com.justpass.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.justpass.app.data.analytics.Analytics
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class AnalyticsTest {

    @Test
    fun `extractNameFromToken returns null for empty string`() {
        assertNull(Analytics.extractNameFromToken(""))
    }

    @Test
    fun `extractNameFromToken returns null for malformed JWT`() {
        assertNull(Analytics.extractNameFromToken("not.a.jwt"))
        assertNull(Analytics.extractNameFromToken("only-one-part"))
        assertNull(Analytics.extractNameFromToken(""))
    }

    @Test
    fun `extractNameFromToken extracts name from valid JWT payload`() {
        val header = base64UrlEncode("{\"alg\":\"none\"}")
        val payload = base64UrlEncode("{\"name\":\"Tarun\",\"preferred_username\":\"tarun123\"}")
        val token = "$header.$payload.sig"
        assertEquals("Tarun", Analytics.extractNameFromToken(token))
    }

    @Test
    fun `extractNameFromToken falls back to preferred_username when name is empty`() {
        val header = base64UrlEncode("{\"alg\":\"none\"}")
        val payload = base64UrlEncode("{\"name\":\"\",\"preferred_username\":\"tarun123\"}")
        val token = "$header.$payload.sig"
        assertEquals("tarun123", Analytics.extractNameFromToken(token))
    }

    @Test
    fun `extractNameFromToken returns null when both name fields are missing`() {
        val header = base64UrlEncode("{\"alg\":\"none\"}")
        val payload = base64UrlEncode("{\"sub\":\"user1\"}")
        val token = "$header.$payload.sig"
        assertNull(Analytics.extractNameFromToken(token))
    }

    @Test
    fun `extractNameFromToken handles invalid base64 gracefully`() {
        assertNull(Analytics.extractNameFromToken("header.!!!.sig"))
    }

    private fun base64UrlEncode(input: String): String {
        return java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(input.toByteArray(Charsets.UTF_8))
    }
}
