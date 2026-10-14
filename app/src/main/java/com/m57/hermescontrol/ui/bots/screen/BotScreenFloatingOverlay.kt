package com.m57.hermescontrol.ui.bots.screen

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/** Geometry shared by touch gestures and unit tests. All sizes are in dp. */
internal object BotScreenFloatGeometry {
    const val MIN_WIDTH = 200f
    const val MIN_HEIGHT = 154f
    const val MARGIN = 8f

    fun clampSize(
        size: Float,
        available: Float,
        minimum: Float,
    ): Float =
        size.coerceIn(
            minimum.coerceAtMost((available - MARGIN * 2).coerceAtLeast(1f)),
            (available - MARGIN * 2).coerceAtLeast(1f),
        )

    fun clampPosition(
        position: Float,
        available: Float,
        size: Float,
    ): Float = position.coerceIn(0f, (available - size).coerceAtLeast(0f))
}

/**
 * Mounted once by MainNavigation, outside NavDisplay, so moving between Chat,
 * Kanban, Bots, Settings etc never recreates the WebView or its viewer identity.
 * Drag is confined to the header; resize is confined to the slim footer grip.
 * Touches on the framebuffer remain available to noVNC after Take over.
 */
@Composable
fun BotScreenFloatingOverlay(
    profile: String,
    modifier: Modifier = Modifier,
) {
    var xDp by rememberSaveable(profile) { mutableFloatStateOf(-1f) }
    var yDp by rememberSaveable(profile) { mutableFloatStateOf(96f) }
    var widthDp by rememberSaveable(profile) { mutableFloatStateOf(264f) }
    var heightDp by rememberSaveable(profile) { mutableFloatStateOf(200f) }
    val density = LocalDensity.current
    val expanded = BotScreenDockState.expanded

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fullWidth = maxWidth.value
        val fullHeight = maxHeight.value
        val width = BotScreenFloatGeometry.clampSize(widthDp, fullWidth, BotScreenFloatGeometry.MIN_WIDTH)
        val height = BotScreenFloatGeometry.clampSize(heightDp, fullHeight, BotScreenFloatGeometry.MIN_HEIGHT)
        val px = BotScreenFloatGeometry.clampPosition(if (xDp < 0f) fullWidth - width - 10f else xDp, fullWidth, width)
        val py = BotScreenFloatGeometry.clampPosition(yDp, fullHeight, height)
        // The toolbar preset is optional: users can freely resize from the grip.
        androidx.compose.runtime.LaunchedEffect(expanded) {
            if (expanded) {
                widthDp = BotScreenFloatGeometry.clampSize(350f, fullWidth, BotScreenFloatGeometry.MIN_WIDTH)
                heightDp = BotScreenFloatGeometry.clampSize(275f, fullHeight, BotScreenFloatGeometry.MIN_HEIGHT)
            } else if (widthDp >= 340f && heightDp >= 260f) {
                widthDp = 264f
                heightDp = 200f
            }
        }
        Surface(
            modifier =
                Modifier
                    .offset { IntOffset(with(density) { px.dp.roundToPx() }, with(density) { py.dp.roundToPx() }) }
                    .width(width.dp)
                    .height(height.dp)
                    .testTag("floating_bot_screen"),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 10.dp,
            tonalElevation = 3.dp,
        ) {
            Column {
                BotScreenPanel(
                    profile = profile,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    header = { name, controls ->
                        SideEffect { BotScreenDockState.publish(profile, controls) }
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .testTag("floating_bot_screen_drag_handle")
                                    .pointerInput(profile, fullWidth, fullHeight) {
                                        detectDragGestures { change, drag ->
                                            change.consume()
                                            val dx = with(density) { drag.x.toDp().value }
                                            val dy = with(density) { drag.y.toDp().value }
                                            val currentWidth =
                                                BotScreenFloatGeometry.clampSize(
                                                    widthDp,
                                                    fullWidth,
                                                    BotScreenFloatGeometry.MIN_WIDTH,
                                                )
                                            val currentHeight =
                                                BotScreenFloatGeometry.clampSize(
                                                    heightDp,
                                                    fullHeight,
                                                    BotScreenFloatGeometry.MIN_HEIGHT,
                                                )
                                            val currentX = if (xDp < 0f) fullWidth - currentWidth - 10f else xDp
                                            xDp =
                                                BotScreenFloatGeometry.clampPosition(
                                                    currentX + dx,
                                                    fullWidth,
                                                    currentWidth,
                                                )
                                            yDp =
                                                BotScreenFloatGeometry.clampPosition(
                                                    yDp + dy,
                                                    fullHeight,
                                                    currentHeight,
                                                )
                                        }
                                    },
                        ) {
                            BotScreenHeader(
                                profile = name,
                                controls = controls,
                                onClose = BotScreenDockState::close,
                            )
                        }
                    },
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(20.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .size(width = 48.dp, height = 20.dp)
                                .testTag("floating_bot_screen_resize_handle")
                                .pointerInput(profile, fullWidth, fullHeight) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        val dw = with(density) { drag.x.toDp().value }
                                        val dh = with(density) { drag.y.toDp().value }
                                        widthDp =
                                            BotScreenFloatGeometry.clampSize(
                                                widthDp + dw,
                                                fullWidth,
                                                BotScreenFloatGeometry.MIN_WIDTH,
                                            )
                                        heightDp =
                                            BotScreenFloatGeometry.clampSize(
                                                heightDp + dh,
                                                fullHeight,
                                                BotScreenFloatGeometry.MIN_HEIGHT,
                                            )
                                    }
                                },
                    )
                }
            }
        }
    }
}
