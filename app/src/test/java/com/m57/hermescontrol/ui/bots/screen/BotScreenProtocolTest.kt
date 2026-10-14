package com.m57.hermescontrol.ui.bots.screen

import com.m57.hermescontrol.data.remote.CleartextPolicy
import com.m57.hermescontrol.data.remote.ServerEndpoint
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BotScreenProtocolTest {
    @Test
    fun statusAndLeaseParseCorrectly() {
        val status =
            BotScreenProtocol.parseStatus(
                Json.parseToJsonElement(
                    """{"supported":true,"installed":true,"running":true,"missing":[],"lease":{"holder":"human","viewer_hash":"ba7816bf8f01"}}""",
                ),
            )
        assertTrue(status.running)
        assertTrue(status.lease.heldBy("abc"))
        assertFalse(status.lease.heldBy("other"))
        assertEquals("ba7816bf8f01", BotScreenProtocol.viewerHash("abc"))
    }

    @Test
    fun scopedDisplayUrlUsesTicketOnlyAndPreservesProxyPrefix() {
        val endpoint = ServerEndpoint.parse("https://example.com/hermes/")
        val observation =
            BotScreenProtocol.parseObservation(
                Json.parseToJsonElement("""{"viewer_id":"viewer","ticket":"one-use","path":"/api/display/ws"}"""),
            )
        assertEquals(
            "wss://example.com/hermes/api/display/ws?display_ticket=one-use",
            BotScreenProtocol.displaySocketUrl(endpoint, observation),
        )
    }

    @Test
    fun observationRejectsUnexpectedEndpointOrMissingIdentity() {
        assertThrows(IllegalArgumentException::class.java) {
            BotScreenProtocol.parseObservation(
                Json.parseToJsonElement("""{"viewer_id":"x","ticket":"t","path":"https://evil.test/"}"""),
            )
        }
        assertThrows(IllegalStateException::class.java) {
            BotScreenProtocol.parseObservation(
                Json.parseToJsonElement("""{"ticket":"t","path":"/api/display/ws"}"""),
            )
        }
    }

    @Test
    fun bundledAssetsShareDashboardOriginWithoutAllowingRemoteContent() {
        val endpoint = ServerEndpoint.parse("https://example.com/hermes/")
        val page = BotScreenAssets.pageUrl(endpoint)
        assertEquals("https://example.com/hermes/mobile-bot-screen/index.html", page)
        assertEquals("bot_screen/index.html", BotScreenAssets.localAssetPath(page, endpoint))
        assertEquals(
            "bot_screen/novnc/core/rfb.js",
            BotScreenAssets.localAssetPath("https://example.com/hermes/mobile-bot-screen/novnc/core/rfb.js", endpoint),
        )
        assertEquals(
            null,
            BotScreenAssets.localAssetPath("https://evil.example/mobile-bot-screen/index.html", endpoint),
        )
        assertEquals(
            null,
            BotScreenAssets.localAssetPath("https://example.com/hermes/mobile-bot-screen/../api/status", endpoint),
        )
        assertEquals(null, BotScreenAssets.localAssetPath("https://example.com/hermes/api/status", endpoint))
    }

    @Test
    fun stoppedAndForeignLeaseRemainViewOnly() {
        val status =
            BotScreenProtocol.parseStatus(
                Json.parseToJsonElement(
                    """{"supported":true,"installed":false,"running":false,"missing":["Xvnc"],"lease":{"holder":"agent"}}""",
                ),
            )
        assertFalse(status.running)
        assertEquals(listOf("Xvnc"), status.missing)
        assertFalse(status.lease.heldBy("viewer"))
    }
}
