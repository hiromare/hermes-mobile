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
    fun `monitors multiple active boards regardless of server current`() {
        assertEquals(
            listOf("dots-personal-agent", "dots-ux-acceptance", "default"),
            selectKanbanAlertBoards(
                listOf("dots-personal-agent", "dots-ux-acceptance", "default", "dots-personal-agent"),
            ),
        )
    }

    @Test
    fun `rejects invalid and null-like board identifiers and limits sockets`() {
        assertEquals(
            listOf("default", "board_2"),
            selectKanbanAlertBoards(listOf("null", "", "default", "board_2", "a/b", "../oops")),
        )
        assertEquals(12, selectKanbanAlertBoards((1..30).map { "board-$it" }).size)
    }

    @Test
    fun `does not notify for ordinary heartbeat or replay events`() {
        listOf("created", "claimed", "spawned", "heartbeat", "commented", "unblocked", "promoted", "archived")
            .forEach { assertNull(kanbanEventAlertKind(it)) }
    }
}
