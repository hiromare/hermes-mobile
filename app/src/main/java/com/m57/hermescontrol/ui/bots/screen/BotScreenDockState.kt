package com.m57.hermescontrol.ui.bots.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** UI-only preference; the normal ChatViewModel owns messages and their session. */
object BotScreenDockState {
    var profile: String? by mutableStateOf(null)
        private set

    var expanded: Boolean by mutableStateOf(false)
        private set

    var controls: BotScreenControls? by mutableStateOf(null)
        private set

    // Don't publish recreated Compose lambdas as state on every recompose.
    private var latestActions: BotScreenControls? = null

    fun show(profile: String) {
        require(profile.isNotBlank())
        if (this.profile != profile) {
            controls = null
            latestActions = null
            expanded = false
        }
        this.profile = profile
    }

    fun close() {
        profile = null
        controls = null
        latestActions = null
        expanded = false
    }

    fun visibleFor(activeProfile: String): Boolean = profile == activeProfile

    fun publish(
        profile: String,
        next: BotScreenControls,
    ) {
        if (this.profile != profile) return
        latestActions = next
        val previous = controls
        if (previous == null ||
            previous.running != next.running || previous.loading != next.loading ||
            previous.busy != next.busy || previous.ownsControl != next.ownsControl ||
            previous.viewerOccupied != next.viewerOccupied || previous.error != next.error ||
            previous.unavailable != next.unavailable
        ) {
            controls = next
        }
    }

    fun clearControls(profile: String) {
        if (this.profile == profile) {
            latestActions = null
            controls = null
        }
    }

    fun toggleExpanded() {
        expanded = !expanded
    }

    fun reconnect() {
        latestActions?.onReconnect?.invoke()
    }

    fun start() {
        latestActions?.onStart?.invoke()
    }

    fun toggleControl() {
        latestActions?.onToggleControl?.invoke()
    }
}
