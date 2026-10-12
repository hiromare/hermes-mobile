package com.m57.hermescontrol.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KanbanBackgroundAlertMonitorTest {
    @Test
    fun `notifies for completion`() {
        assertEquals("completed", kanbanEventAlertKind("completed"))
    }

    @Test
    fun `notifies when human intervention is needed`() {
        listOf("blocked", "needs_input", "review_requested", "failed").forEach {
            assertEquals("needs attention", kanbanEventAlertKind(it))
        }
    }

    @Test
    fun `does not notify for ordinary heartbeat or replay events`() {
        listOf("created", "claimed", "spawned", "heartbeat", "commented", "unblocked", "promoted", "archived")
            .forEach { assertNull(kanbanEventAlertKind(it)) }
    }
}
