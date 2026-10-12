"""QA fixture contract tests, no Android device or real Hermes credentials required."""
from __future__ import annotations

import unittest

from aiohttp import client_exceptions
from aiohttp.test_utils import AioHTTPTestCase

from scripts.qa import kanban_notification_loopback as fixture


class KanbanLoopbackFixtureTests(AioHTTPTestCase):
    async def get_application(self):
        return fixture.app

    async def asyncSetUp(self):
        fixture.clients.clear()
        fixture.kanban_clients.clear()
        fixture.metrics_state["http_paths"] = []
        fixture.metrics_state["ws_clients"] = 0
        fixture.metrics_state["kanban_ws_clients"] = 0
        fixture.metrics_state["sent_events"] = []
        fixture.event_seq = 1000
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

    async def test_no_connected_kanban_client_no_notification_event(self):
        reply = await self.client.post("/trigger")
        self.assertEqual(200, reply.status)
        self.assertEqual(0, (await reply.json())["sent_to_ws"])


if __name__ == "__main__":
    unittest.main()
