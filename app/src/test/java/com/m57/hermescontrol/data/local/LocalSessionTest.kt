package com.m57.hermescontrol.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSessionTest {
    @Test
    fun gatedSessionUsesCookieAfterWsTicketExpires() {
        assertTrue(hasLocalSession(gated = true, token = null, sessionCookie = "stored-cookie"))
        assertFalse(hasLocalSession(gated = true, token = "old-ticket", sessionCookie = null))
    }

    @Test
    fun loopbackSessionRequiresToken() {
        assertTrue(hasLocalSession(gated = false, token = "server-token", sessionCookie = null))
        assertFalse(hasLocalSession(gated = false, token = null, sessionCookie = "old-cookie"))
    }

    @Test
    fun freshInstallHasNoLocalSession() {
        assertFalse(hasLocalSession(gated = true, token = null, sessionCookie = null))
    }
}
