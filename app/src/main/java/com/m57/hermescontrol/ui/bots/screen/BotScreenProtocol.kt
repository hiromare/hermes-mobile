package com.m57.hermescontrol.ui.bots.screen

import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.ws.toJsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.security.MessageDigest

/** The Desktop gateway contract: display.* is scoped to the selected bot, not the active chat profile. */
internal object BotScreenProtocol {
    data class Lease(
        val holder: String = "agent",
        val viewerHash: String? = null,
    ) {
        fun heldBy(viewerId: String?): Boolean =
            holder == "human" && viewerId != null && viewerHash == BotScreenProtocol.viewerHash(viewerId)
    }

    data class Status(
        val supported: Boolean,
        val installed: Boolean,
        val running: Boolean,
        val missing: List<String>,
        val blocker: String?,
        val installCommand: String?,
        val lease: Lease,
    )

    data class Observation(
        val viewerId: String,
        val ticket: String,
        val path: String,
    )

    fun parseStatus(result: Any?): Status {
        val data = result.toJsonElement() as? JsonObject ?: error("Invalid display.status response")
        return Status(
            supported = data.bool("supported"),
            installed = data.bool("installed"),
            running = data.bool("running"),
            missing = (data["missing"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            }.orEmpty(),
            blocker = data.string("blocker"),
            installCommand = data.string("install_command"),
            lease = parseLease(data["lease"]),
        )
    }

    fun parseLease(result: Any?): Lease {
        val data = result.toJsonElement() as? JsonObject ?: error("Invalid display lease")
        return Lease(holder = data.string("holder") ?: "agent", viewerHash = data.string("viewer_hash"))
    }

    fun parseObservation(result: Any?): Observation {
        val data = result.toJsonElement() as? JsonObject ?: error("Invalid display.observe response")
        val path = data.string("path") ?: error("Missing display path")
        require(path == "/api/display/ws") { "Unexpected display endpoint" }
        return Observation(
            viewerId = data.string("viewer_id")?.takeIf { it.isNotBlank() } ?: error("Missing viewer ID"),
            ticket = data.string("ticket")?.takeIf { it.isNotBlank() } ?: error("Missing display ticket"),
            path = path,
        )
    }

    /** Ticket is single-use, tied to the configured dashboard origin and never sent to any other host. */
    fun displaySocketUrl(endpoint: ServerEndpoint, observation: Observation): String =
        endpoint.webSocketUrl(
            path = observation.path.removePrefix("/"),
            authParameter = "ticket",
            credential = observation.ticket,
        ).replace("?ticket=", "?display_ticket=")

    fun viewerHash(viewerId: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(viewerId.toByteArray(Charsets.UTF_8))
            .take(6)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun JsonObject.bool(name: String): Boolean = (get(name) as? JsonPrimitive)?.booleanOrNull ?: false

    private fun JsonObject.string(name: String): String? = (get(name) as? JsonPrimitive)?.contentOrNull
}
