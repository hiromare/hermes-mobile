package com.m57.hermescontrol.ui.bots.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.R

/** The viewer never covers the framebuffer with a toolbar or status chip. */
data class BotScreenControls(
    val running: Boolean,
    val loading: Boolean,
    val busy: Boolean,
    val ownsControl: Boolean,
    val viewerOccupied: Boolean,
    val error: String?,
    val unavailable: String?,
    val onReconnect: () -> Unit,
    val onStart: () -> Unit,
    val onToggleControl: () -> Unit,
)

@Composable
fun BotScreenHeader(
    profile: String,
    controls: BotScreenControls,
    expanded: Boolean? = null,
    onResize: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    var menuExpanded by remember(profile) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                profile,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    controls.error != null -> controls.error
                    controls.loading -> stringResource(R.string.bot_screen_connecting)
                    !controls.running -> controls.unavailable ?: stringResource(R.string.bot_screen_stopped)
                    controls.ownsControl -> stringResource(R.string.bot_screen_controlling)
                    controls.viewerOccupied -> stringResource(R.string.bot_screen_in_use)
                    else -> stringResource(R.string.bot_screen_read_only)
                },
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (controls.error !=
                        null
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        androidx.compose.foundation.layout.Box {
            IconButton(
                onClick = { menuExpanded = true },
                enabled = !controls.loading,
                modifier = Modifier.testTag("bot_screen_controls_menu"),
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.bot_screen_controls))
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                if (!controls.running) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.bot_screen_start)) },
                        onClick = {
                            menuExpanded = false
                            controls.onStart()
                        },
                        enabled = !controls.busy && controls.unavailable != null,
                        modifier = Modifier.testTag("bot_screen_start"),
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.bot_screen_reconnect)) },
                        onClick = {
                            menuExpanded = false
                            controls.onReconnect()
                        },
                        enabled = !controls.busy,
                        modifier = Modifier.testTag("bot_screen_reconnect"),
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (controls.ownsControl) {
                                        R.string.bot_screen_hand_back
                                    } else {
                                        R.string.bot_screen_take_over
                                    },
                                ),
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            controls.onToggleControl()
                        },
                        enabled = !controls.busy,
                        modifier =
                            Modifier.testTag(
                                if (controls.ownsControl) "bot_screen_hand_back" else "bot_screen_take_over",
                            ),
                    )
                }
            }
        }
        if (expanded != null && onResize != null) {
            IconButton(onClick = onResize, modifier = Modifier.testTag("chat_bot_screen_resize")) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription =
                        stringResource(if (expanded) R.string.bot_screen_smaller else R.string.bot_screen_larger),
                )
            }
        }
        if (onClose != null) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("chat_bot_screen_close")) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.bot_screen_close))
            }
        }
    }
}
