#!/usr/bin/env python3
"""Loopback-only synthetic Hermes dashboard for Android Kanban-notification E2E.

Test fixture. Never authenticate against or write to a real Hermes gateway.
Only binds 127.0.0.1, with no production credentials or stored secrets.
Dependency: aiohttp >= 3.12. Use adb reverse tcp:18588 tcp:18588.
"""
from __future__ import annotations

import time
from aiohttp import web

PORT = 18588
BOARD = "dots-personal-agent"
TASK_ID = "t_1234abcd"
SUBJECT = "Synthetic E2E alert"
TOKEN = "qa-local-session-not-a-real-credential"
clients: set[web.WebSocketResponse] = set()
kanban_clients: set[web.WebSocketResponse] = set()
event_seq = 1000
metrics_state = {"http_paths": [], "ws_clients": 0, "kanban_ws_clients": 0, "sent_events": []}


async def api(request: web.Request) -> web.Response:
    path = request.path
    metrics_state["http_paths"] = (metrics_state["http_paths"] + [path])[-40:]
    if path == "/":
        return web.Response(
            text=f'<script>window.__HERMES_SESSION_TOKEN__ = "{TOKEN}"</script>',
            content_type="text/html",
        )
    if path == "/api/status":
        return web.json_response({"auth_required": False, "auth_providers": []})
    if path == "/auth/password-login":
        # Synthetic test-only session, not accepted by any production gateway.
        return web.json_response(
            {"ok": True},
            headers={"Set-Cookie": "hermes_session_at=qa-local-cookie; Path=/; HttpOnly; SameSite=Lax"},
        )
    if path == "/api/auth/ws-ticket":
        return web.Response(text='{"ticket":"qa-local-ws-ticket"}', content_type="application/json")
    if path == "/api/sessions":
        return web.json_response({"sessions": [], "total": 0, "limit": 20, "offset": 0})
    if path == "/api/plugins/kanban/boards":
        return web.json_response(
            {"boards": [{"slug": BOARD, "name": "Dots QA", "isCurrent": True}], "current": BOARD}
        )
    if path == "/api/plugins/kanban/board":
        return web.json_response(
            {"columns": [{"name": "done", "tasks": []}], "latestEventId": event_seq, "now": int(time.time())}
        )
    if path.startswith("/api/plugins/kanban/tasks/"):
        requested_task_id = path.removeprefix("/api/plugins/kanban/tasks/").split("/", 1)[0]
        if requested_task_id != TASK_ID:
            return web.json_response({"error": "task_not_found"}, status=404)
        return web.json_response(
            {"task": {"id": TASK_ID, "title": SUBJECT, "status": "done",
                      "body": "No production impact", "created_by": "qa-local"},
             "comments": [], "events": []}
        )
    return web.json_response({"ok": True, "sessions": [], "projects": [], "boards": []})


async def ws(request: web.Request) -> web.StreamResponse:
    if request.path == "/api/plugins/kanban/events" and request.query.get("board") != BOARD:
        return web.json_response({"error": "board_not_found"}, status=400)
    socket = web.WebSocketResponse(heartbeat=30)
    await socket.prepare(request)
    clients.add(socket)
    if request.path == "/api/plugins/kanban/events":
        kanban_clients.add(socket)
    metrics_state["ws_clients"] = len(clients)
    metrics_state["kanban_ws_clients"] = len(kanban_clients)
    try:
        async for _ in socket:
            pass
    finally:
        clients.discard(socket)
        kanban_clients.discard(socket)
        metrics_state["ws_clients"] = len(clients)
        metrics_state["kanban_ws_clients"] = len(kanban_clients)
    return socket


async def trigger(_: web.Request) -> web.Response:
    global event_seq
    event_seq += 1
    now = int(time.time())
    payload = {
        "events": [{"id": event_seq, "task_id": TASK_ID, "kind": "completed", "created_at": now}],
        "cursor": event_seq,
    }
    sent = 0
    for socket in list(kanban_clients):
        if not socket.closed:
            try:
                await socket.send_json(payload)
                sent += 1
            except (ConnectionError, RuntimeError):
                pass
    metrics_state["sent_events"].append({"id": event_seq, "sent_to_ws": sent, "at": now})
    return web.json_response({"sent_to_ws": sent, "event": payload})


async def metrics(_: web.Request) -> web.Response:
    return web.json_response(metrics_state)


def create_app() -> web.Application:
    """Fresh isolated app per test event loop; production fixture still single-process."""
    app = web.Application()
    app.router.add_get("/api/plugins/kanban/events", ws)
    app.router.add_get("/api/ws", ws)
    app.router.add_post("/trigger", trigger)
    app.router.add_get("/metrics", metrics)
    app.router.add_route("*", "/{tail:.*}", api)
    return app


if __name__ == "__main__":
    web.run_app(create_app(), host="127.0.0.1", port=PORT)
