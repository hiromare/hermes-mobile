package com.m57.hermescontrol.ui.bots.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BotScreenDockStateTest {
    @Test
    fun dockMatchesOnlyTheSelectedBot() {
        try {
            BotScreenDockState.show("research")
            assertTrue(BotScreenDockState.visibleFor("research"))
            assertFalse(BotScreenDockState.visibleFor("default"))
        } finally {
            BotScreenDockState.close()
        }
        assertFalse(BotScreenDockState.visibleFor("research"))
    }

    @Test
    fun emptyProfileIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { BotScreenDockState.show("") }
        assertFalse(BotScreenDockState.visibleFor(""))
    }
}
