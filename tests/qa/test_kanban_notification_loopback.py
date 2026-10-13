"""QA fixture contract tests, no Android device or real Hermes credentials required."""
from __future__ import annotations

import asyncio
import unittest

from aiohttp import client_exceptions
from aiohttp.test_utils import AioHTTPTestCase

from scripts.qa import kanban_notification_loopback as fixture


class KanbanLoopbackFixtureTests(AioHTTPTestCase):
    async def get_application(self):
        return fixture.create_app()

    async def asyncSetUp(self):
        fixture.clients.clear()
        fixture.kanban_clients.clear()
        fixture.metrics_state["http_paths"] = []
        fixture.metrics_state["ws_clients"] = 0
        fixture.metrics_state["kanban_ws_clients"] = 0
        fixture.metrics_state["sent_events"] = []
        fixture.event_seq = 1000
        fixture.second_event_seq = 1000
        await super().asyncSetUp()

    async def test_synthetic_task_requires_exact_id(self):
        known = await self.client.get(f"/api/plugins/kanban/tasks/{fixture.TASK_ID}")
        self.assertEqual(200, known.status)
        self.assertEqual(fixture.TASK_ID, (await known.json())["task"]["id"])

        unknown = await self.client.get("/api/plugins/kanban/tasks/t_ffffffff")
        self.assertEqual(404, unknown.status)
        self.assertEqual("task_not_found", (await unknown.json())["error"])

    async def test_websocket_rejects_unknown_board(self):
        with self.assertRaises(client_exceptions.WSServerHandshakeError) as caught:
            await self.client.ws_connect("/api/plugins/kanban/events?board=other-board&since=0")
        self.assertEqual(400, caught.exception.status)
        self.assertEqual(0, len(fixture.kanban_clients))

    async def test_valid_board_event_has_matching_task_and_cursor(self):
        socket = await self.client.ws_connect(
            f"/api/plugins/kanban/events?board={fixture.BOARD}&since=0"
        )
        self.assertEqual(1, len(fixture.kanban_clients))
        response = await self.client.post("/trigger")
        self.assertEqual(200, response.status)
        emitted = await response.json()
        self.assertEqual(1, emitted["sent_to_ws"])
        received = await socket.receive_json(timeout=3)
        self.assertEqual(emitted["event"], received)
        self.assertEqual(fixture.TASK_ID, received["events"][0]["task_id"])
        self.assertEqual("completed", received["events"][0]["kind"])
        self.assertEqual(received["cursor"], received["events"][0]["id"])
        await socket.close()

    async def test_reconnecting_client_receives_event_newer_than_saved_cursor(self):
        socket = await self.client.ws_connect(
            f"/api/plugins/kanban/events?board={fixture.BOARD}&since=1750"
        )
        response = await self.client.post("/trigger")
        sent = await response.json()
        self.assertEqual(1751, sent["event"]["cursor"])
        self.assertEqual(1751, (await socket.receive_json(timeout=3))["events"][0]["id"])
        await socket.close()

    async def test_invalid_event_cursor_is_rejected(self):
        with self.assertRaises(client_exceptions.WSServerHandshakeError) as caught:
            await self.client.ws_connect(f"/api/plugins/kanban/events?board={fixture.BOARD}&since=oops")
        self.assertEqual(400, caught.exception.status)

    async def test_board_discovery_and_task_detail_isolation(self):
        boards_response = await self.client.get("/api/plugins/kanban/boards")
        self.assertEqual(200, boards_response.status)
        boards = (await boards_response.json())["boards"]
        self.assertEqual([fixture.BOARD, fixture.SECOND_BOARD], [b["slug"] for b in boards])
        self.assertEqual([True, False], [b["isCurrent"] for b in boards])

        other = await self.client.get(
            f"/api/plugins/kanban/tasks/{fixture.SECOND_TASK_ID}?board={fixture.SECOND_BOARD}"
        )
        self.assertEqual(200, other.status)
        self.assertEqual(fixture.SECOND_TASK_ID, (await other.json())["task"]["id"])
        wrong_board = await self.client.get(
            f"/api/plugins/kanban/tasks/{fixture.SECOND_TASK_ID}?board={fixture.BOARD}"
        )
        self.assertEqual(404, wrong_board.status)

    async def test_secondary_board_event_does_not_leak_to_primary_board(self):
        primary = await self.client.ws_connect(
            f"/api/plugins/kanban/events?board={fixture.BOARD}&since=0"
        )
        secondary = await self.client.ws_connect(
            f"/api/plugins/kanban/events?board={fixture.SECOND_BOARD}&since=0"
        )
        try:
            response = await self.client.post(f"/trigger?board={fixture.SECOND_BOARD}")
            self.assertEqual(200, response.status)
            emitted = await response.json()
            self.assertEqual(1, emitted["sent_to_ws"])
            received = await secondary.receive_json(timeout=3)
            self.assertEqual(emitted["event"], received)
            self.assertEqual(fixture.SECOND_TASK_ID, received["events"][0]["task_id"])
            with self.assertRaises(asyncio.TimeoutError):
                await primary.receive_json(timeout=0.2)

            response = await self.client.post(f"/trigger?board={fixture.BOARD}")
            self.assertEqual(200, response.status)
            emitted = await response.json()
            self.assertEqual(1, emitted["sent_to_ws"])
            received = await primary.receive_json(timeout=3)
            self.assertEqual(emitted["event"], received)
            self.assertEqual(fixture.TASK_ID, received["events"][0]["task_id"])
            with self.assertRaises(asyncio.TimeoutError):
                await secondary.receive_json(timeout=0.2)
        finally:
            await primary.close()
            await secondary.close()

    async def test_secondary_cursor_does_not_advance_primary_cursor(self):
        secondary = await self.client.ws_connect(
            f"/api/plugins/kanban/events?board={fixture.SECOND_BOARD}&since=1750"
        )
        primary = await self.client.ws_connect(
            f"/api/plugins/kanban/events?board={fixture.BOARD}&since=0"
        )
        try:
            other = await (await self.client.post(
                f"/trigger?board={fixture.SECOND_BOARD}"
            )).json()
            self.assertEqual(1751, other["event"]["cursor"])
            self.assertEqual(1751, (await secondary.receive_json(timeout=3))["cursor"])
            first = await (await self.client.post("/trigger")).json()
            self.assertEqual(1001, first["event"]["cursor"])
            self.assertEqual(1001, (await primary.receive_json(timeout=3))["cursor"])
        finally:
            await primary.close()
            await secondary.close()

    async def test_trigger_unknown_board_is_rejected_without_broadcast(self):
        primary = await self.client.ws_connect(
            f"/api/plugins/kanban/events?board={fixture.BOARD}&since=0"
        )
        try:
            response = await self.client.post("/trigger?board=other-board")
            self.assertEqual(400, response.status)
            self.assertEqual("board_not_found", (await response.json())["error"])
            self.assertEqual([], fixture.metrics_state["sent_events"])
            with self.assertRaises(asyncio.TimeoutError):
                await primary.receive_json(timeout=0.2)
        finally:
            await primary.close()

    async def test_no_connected_kanban_client_no_notification_event(self):
        reply = await self.client.post("/trigger")
        self.assertEqual(200, reply.status)
        self.assertEqual(0, (await reply.json())["sent_to_ws"])


if __name__ == "__main__":
    unittest.main()
