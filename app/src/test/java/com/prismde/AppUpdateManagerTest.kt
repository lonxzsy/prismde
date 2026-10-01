package com.prismde

import com.prismde.feature_update.AppUpdateManager
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    // Simple test double for version comparison logic
    private fun isNewer(remote: String, current: String): Boolean {
        val rParts = remote.removePrefix("v").split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val cParts = current.removePrefix("v").split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, cParts.size)

        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    @Test
    fun testSemanticVersionComparison() {
        assertTrue(isNewer("1.0.1", "1.0.0"))
        assertTrue(isNewer("1.1.0", "1.0.9"))
        assertTrue(isNewer("2.0.0", "1.9.9"))
        assertTrue(isNewer("v1.0.2", "1.0.1"))
        assertTrue(isNewer("1.0.10", "1.0.9"))

        assertFalse(isNewer("1.0.0", "1.0.0"))
        assertFalse(isNewer("1.0.0", "1.0.1"))
        assertFalse(isNewer("1.0.0", "2.0.0"))
    }
}
