package com.m57.hermescontrol.notification

import com.m57.hermescontrol.data.model.KanbanBoard
import com.m57.hermescontrol.data.model.KanbanBoardResponse
import com.m57.hermescontrol.data.model.KanbanBoardsResponse
import com.m57.hermescontrol.data.model.KanbanColumn
import com.m57.hermescontrol.data.model.KanbanTask
import com.m57.hermescontrol.data.remote.NetworkError
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.repository.KanbanRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KanbanBackgroundAlertMonitorTest {
    @Test
    fun testOnlyBlockedMailTasksQualify() {
        val item =
            KanbanTask(id = "t_mail0001", title = "Gmail 広告の扱いを確認 (personal)", status = "blocked", createdAt = 10L)
        val board =
            KanbanBoardResponse(
                columns =
                    listOf(
                        KanbanColumn(
                            name = "blocked",
                            tasks =
                                listOf(
                                    item,
                                    item.copy(),
                                    item.copy(id = "t_mail0002", status = "ready"),
                                    item.copy(id = "t_mail0003", title = "Other task"),
                                    item.copy(id = "t_mail0005", title = "Gmail 広告の扱いを確認 (untrusted)"),
                                    item.copy(id = "invalid-task-id"),
                                ),
                        ),
                        KanbanColumn(name = "done", tasks = listOf(item.copy(id = "t_mail0004"))),
                    ),
            )
        assertEquals(listOf("t_mail0001"), pendingMailAttentionTasks(board).map { it.id })
    }

    @Test
    fun testOwnerAttentionCappedOldestFirst() {
        val tasks =
            (1..6).reversed().map { n ->
                KanbanTask(
                    id = "t_mail000$n",
                    title = "Gmail 広告の扱いを確認 (personal)",
                    status = "blocked",
                    createdAt = n.toLong(),
                )
            }
        val board = KanbanBoardResponse(columns = listOf(KanbanColumn(name = "blocked", tasks = tasks)))
        assertEquals(listOf("t_mail0001", "t_mail0002", "t_mail0003"), pendingMailAttentionTasks(board).map { it.id })
    }

    @Test
    fun testOwnerAttentionScopedToExistingAuthenticatedBoard() =
        runTest {
            val repo = mockk<KanbanRepository>()
            coEvery { repo.getBoard(board = "dots-personal-agent") } returns
                NetworkResult.Success(
                    KanbanBoardResponse(
                        columns =
                            listOf(
                                KanbanColumn(
                                    name = "blocked",
                                    tasks =
                                        listOf(
                                            KanbanTask(
                                                id = "t_mail0001",
                                                title = "Gmail 広告の扱いを確認 (daily)",
                                                status = "blocked",
                                            ),
                                        ),
                                ),
                            ),
                    ),
                )
            assertEquals(emptyList<KanbanTask>(), loadPendingMailAttention(repo, "other-board"))
            assertEquals(1, loadPendingMailAttention(repo, "dots-personal-agent").size)
            coVerify(exactly = 1) { repo.getBoard(board = "dots-personal-agent") }
        }

    @Test
    fun testNoSyntheticAttentionOnApiError() =
        runTest {
            val repo = mockk<KanbanRepository>()
            coEvery { repo.getBoard(board = "dots-personal-agent") } returns
                NetworkResult.Failure(NetworkError.Http(503, "down"))
            assertEquals(emptyList<KanbanTask>(), loadPendingMailAttention(repo, "dots-personal-agent"))
        }

    @Test
    fun `notification tags are scoped to board and task`() {
        val task = "t_1234abcd"
        val first = kanbanNotificationTag("dots-personal-agent", task)
        val second = kanbanNotificationTag("other-board", task)
        assertNotEquals(first, second)
        assertEquals(first, kanbanNotificationTag("dots-personal-agent", task))
        assertNotEquals(first, kanbanNotificationTag("dots-personal-agent", "t_ffffffff"))
    }

    @Test
    fun `tags cannot collide even when the Java hash does`() {
        val task = "t_1234abcd"
        assertEquals(("Aa|" + task).hashCode(), ("BB|" + task).hashCode())
        assertNotEquals(kanbanNotificationTag("Aa", task), kanbanNotificationTag("BB", task))
    }

    @Test
    fun `board discovery uses repository endpoint`() =
        runTest {
            val repository = mockk<KanbanRepository>()
            coEvery { repository.getBoards(includeArchived = false) } returns
                NetworkResult.Success(
                    KanbanBoardsResponse(
                        boards = listOf(KanbanBoard(id = "default"), KanbanBoard(id = "other")),
                    ),
                )
            assertEquals(listOf("default", "other"), loadKanbanAlertBoards(repository))
            coVerify(exactly = 1) { repository.getBoards(includeArchived = false) }
        }

    @Test
    fun `board discovery failure does not bypass repository`() =
        runTest {
            val repository = mockk<KanbanRepository>()
            coEvery { repository.getBoards(includeArchived = false) } returns
                NetworkResult.Failure(NetworkError.Http(503, "Service temporarily unavailable"))
            assertEquals(emptyList<String>(), loadKanbanAlertBoards(repository))
            coVerify(exactly = 1) { repository.getBoards(includeArchived = false) }
        }

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
