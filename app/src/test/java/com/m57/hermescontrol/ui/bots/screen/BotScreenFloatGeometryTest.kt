package com.m57.hermescontrol.ui.bots.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class BotScreenFloatGeometryTest {
    @Test
    fun clampsDragWithinViewport() {
        assertEquals(0f, BotScreenFloatGeometry.clampPosition(-800f, 400f, 250f))
        assertEquals(150f, BotScreenFloatGeometry.clampPosition(999f, 400f, 250f))
        assertEquals(60f, BotScreenFloatGeometry.clampPosition(60f, 400f, 250f))
    }

    @Test
    fun limitsResizeAndWorksOnSmallScreens() {
        assertEquals(200f, BotScreenFloatGeometry.clampSize(90f, 430f, 200f))
        assertEquals(414f, BotScreenFloatGeometry.clampSize(990f, 430f, 200f))
        assertEquals(164f, BotScreenFloatGeometry.clampSize(500f, 180f, 200f))
        assertEquals(0f, BotScreenFloatGeometry.clampPosition(90f, 170f, 180f))
    }
}
