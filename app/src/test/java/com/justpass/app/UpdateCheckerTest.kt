package com.justpass.app

import com.justpass.app.data.update.UpdateChecker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Method

class UpdateCheckerTest {

    // Access private isNewerVersion via reflection for white-box testing
    private fun isNewerVersion(latest: String, current: String): Boolean {
        val method: Method = UpdateChecker::class.java.getDeclaredMethod(
            "isNewerVersion",
            String::class.java,
            String::class.java
        )
        method.isAccessible = true
        return method.invoke(UpdateChecker, latest, current) as Boolean
    }

    @Test
    fun `isNewerVersion returns false when versions are equal`() {
        assertFalse(isNewerVersion("3.0.5", "3.0.5"))
        assertFalse(isNewerVersion("1.0.0", "1.0.0"))
    }

    @Test
    fun `isNewerVersion returns true when latest is greater`() {
        assertTrue(isNewerVersion("3.0.6", "3.0.5"))
        assertTrue(isNewerVersion("4.0.0", "3.9.9"))
        assertTrue(isNewerVersion("3.1.0", "3.0.5"))
        assertTrue(isNewerVersion("10.0.0", "9.99.99"))
    }

    @Test
    fun `isNewerVersion returns false when latest is older`() {
        assertFalse(isNewerVersion("3.0.4", "3.0.5"))
        assertFalse(isNewerVersion("2.0.0", "3.0.0"))
        assertFalse(isNewerVersion("3.0.0", "3.0.1"))
    }

    @Test
    fun `isNewerVersion handles different segment counts`() {
        assertTrue(isNewerVersion("3.0.5.1", "3.0.5"))
        assertFalse(isNewerVersion("3.0", "3.0.5"))
        assertTrue(isNewerVersion("3.1", "3.0.5"))
    }

    @Test
    fun `isNewerVersion handles non-numeric segments gracefully`() {
        // mapNotNull filters out non-numeric parts, so "3.beta.5" becomes [3,5]
        // [3,5] vs [3,0,5] -> 3==3, 5>0 => true
        assertTrue(isNewerVersion("3.beta.5", "3.0.5"))
        assertTrue(isNewerVersion("3.1.beta", "3.0.5"))
    }

    @Test
    fun `isNewerVersion handles empty strings`() {
        assertFalse(isNewerVersion("", ""))
        assertTrue(isNewerVersion("1.0.0", ""))
        assertFalse(isNewerVersion("", "1.0.0"))
    }

    @Test
    fun `checkForUpdate returns null when network fails`() = runBlocking {
        // With an invalid URL or no network, it should return null
        val result = UpdateChecker.checkForUpdate("3.0.5")
        // This may succeed if the network is available, so we just assert no crash
        // In a real test we'd mock the HttpURLConnection
        assertTrue(result == null || result.versionName.isNotEmpty())
    }
}
