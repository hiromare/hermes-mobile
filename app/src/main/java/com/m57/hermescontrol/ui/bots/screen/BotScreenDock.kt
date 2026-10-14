package com.m57.hermescontrol.ui.bots.screen

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** One composable above the normal chat transcript, not a second chat implementation. */
@Composable
fun BotScreenDock(
    profile: String,
    modifier: Modifier = Modifier,
) {
    val expanded = BotScreenDockState.expanded
    DisposableEffect(profile) {
        onDispose { BotScreenDockState.clearControls(profile) }
    }
    val imeHeight = WindowInsets.ime.getBottom(LocalDensity.current)
    // Preserve the normal chat composer when the keyboard is present.
    val dockHeight =
        if (imeHeight > 0) {
            112.dp
        } else if (expanded) {
            300.dp
        } else {
            196.dp
        }
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .height(dockHeight)
                .testTag("chat_bot_screen_dock"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        BotScreenPanel(
            profile = profile,
            modifier = Modifier.fillMaxSize(),
            header = { _, controls ->
                SideEffect { BotScreenDockState.publish(profile, controls) }
            },
        )
    }
}
