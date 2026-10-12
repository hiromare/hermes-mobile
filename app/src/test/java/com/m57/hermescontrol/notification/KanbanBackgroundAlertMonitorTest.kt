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
    fun `uses wire slug when current board is null`() {
        val json =
            """{"current":null,"boards":[{"slug":"dots-personal-agent","name":"Personal"},{"slug":"default","name":"Default"}]}"""
        assertEquals(listOf("dots-personal-agent", "default"), parseKanbanAlertBoards(json))
    }

    @Test
    fun `ignores stale server current because all active boards are monitored`() {
        val json =
            """{"current":"archived-board","boards":[{"slug":"board-a"},{"slug":"board-b"}]}"""
        assertEquals(listOf("board-a", "board-b"), parseKanbanAlertBoards(json))
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
