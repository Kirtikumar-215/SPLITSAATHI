package com.example

import com.example.data.remote.SupabaseClientManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SupabaseClientManagerTest {

    @Test
    fun testPlaceholderDetection() {
        // In default test/build environments without live keys, placeholder values are detected
        // and isConfigured correctly reports false.
        assertFalse(
            "SupabaseClientManager should not treat placeholder credentials as configured",
            SupabaseClientManager.isConfigured
        )
    }

    @Test
    fun testClientOrNullSafeAccess() {
        // clientOrNull must return null or safe instance and never crash on placeholder/empty credentials
        val client = SupabaseClientManager.clientOrNull
        assertNull("clientOrNull should return null when placeholder credentials are used", client)
    }

    @Test
    fun testPluginAccessorsSafeAccess() {
        assertNull("authOrNull should be null when client is not configured", SupabaseClientManager.authOrNull)
        assertNull("postgrestOrNull should be null when client is not configured", SupabaseClientManager.postgrestOrNull)
        assertNull("realtimeOrNull should be null when client is not configured", SupabaseClientManager.realtimeOrNull)
    }

    @Test
    fun testRequireClientThrowsClearExceptionWhenUnconfigured() {
        val exception = assertThrows(IllegalStateException::class.java) {
            SupabaseClientManager.requireClient()
        }
        assertNotNull(exception.message)
        assert(exception.message!!.contains("Supabase client is not configured"))
    }
}
