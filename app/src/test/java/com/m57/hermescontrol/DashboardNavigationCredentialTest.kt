package com.m57.hermescontrol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Credential shape is a UI gate only; server-side REST calls still validate the session. */
class DashboardNavigationCredentialTest {
    @Test
    fun `loopback token grants navigation`() {
        assertTrue(hasDashboardNavigationCredential("dashboard-token", gatedMode = false, sessionCookie = null))
    }

    @Test
    fun `gated persisted cookie grants navigation without ws ticket`() {
        assertTrue(hasDashboardNavigationCredential(null, gatedMode = true, sessionCookie = "saved-cookie"))
    }

    @Test
    fun `gated ws ticket grants navigation while cookie is being refreshed`() {
        assertTrue(hasDashboardNavigationCredential("ws-ticket", gatedMode = true, sessionCookie = null))
    }

    @Test
    fun `missing credentials defer pending notification until login`() {
        assertFalse(hasDashboardNavigationCredential(null, gatedMode = true, sessionCookie = null))
        assertFalse(hasDashboardNavigationCredential(null, gatedMode = false, sessionCookie = null))
    }

    @Test
    fun `cookie from a different auth mode must not authorize navigation`() {
        assertFalse(hasDashboardNavigationCredential(null, gatedMode = false, sessionCookie = "stale-cookie"))
    }

    @Test
    fun `blank credentials cannot grant navigation`() {
        assertFalse(hasDashboardNavigationCredential(" ", gatedMode = true, sessionCookie = " "))
    }
}
