# Android 16 Kanban notification E2E (isolated loopback fixture)

This is a **development test only**. It tests a real Android app, Android notification
manager and notification tap, with synthetic events and a local mock Hermes server.
It is not a production AgentHub authentication or notification acceptance test.

## Safety boundaries

- The server binds **127.0.0.1** on the Mac; the emulator reaches it only through
  `adb reverse`. Never expose this synthetic authentication server to a LAN,
  Cloudflare, Tailscale, or the public Internet.
- Use the CI **debug** APK with package `com.m57.hermescontrol.dev`, not the
  separately signed `com.m57.hermescontrol` evaluation application. The two
  package IDs coexist, preserving the evaluation app's data and credentials.
- The fixture accepts arbitrary synthetic test login values. Never enter a real
  password or token. Its test session cookie/WS ticket are not production credentials.
- Only synthetic board `dots-personal-agent` and task `t_1234abcd` are involved.
  No write is sent to a real Hermes installation.

## Run on Mac with an Android 16 emulator

```sh
python3 -m pip install 'aiohttp>=3.12,<4'
python3 scripts/qa/kanban_notification_loopback.py
# Second terminal:
adb -s emulator-5554 reverse tcp:18588 tcp:18588
adb -s emulator-5554 install -r app-debug.apk  # CI debug APK (.dev applicationId)
adb -s emulator-5554 shell pm grant com.m57.hermescontrol.dev android.permission.POST_NOTIFICATIONS
```

In the **Hermes Dev** application, open **Auth Login**. Set the dashboard URL
to `http://127.0.0.1:18588/` and select **Probe dashboard**. This deliberately
minimal fixture may show `Session Token`, `Username`, and `Password` fields:
enter *only* synthetic nonempty username/password (for example `qatest` /
`qatest`) and select **Connect**. This is only to exercise the existing gated
session/ticket login path. A "Reconnecting" chat indicator is expected because
the fixture does not implement the Hermes conversational WebSocket protocol.

In Settings → Behavior turn on **Stay connected in background** and
**Notify when a session completes**, then send the application to Home.
Check that the Android foreground background-service notification exists and
the fixture has one Kanban WebSocket subscriber:

```sh
curl -s http://127.0.0.1:18588/metrics
# Expect "kanban_ws_clients": 1, after app is in background.
curl -s -X POST http://127.0.0.1:18588/trigger
# Expect "sent_to_ws": 1.
adb -s emulator-5554 shell dumpsys notification --noredact
# Filter locally for com.m57.hermescontrol.dev / Hermes Kanban /
# t_1234abcd · completed · dots-personal-agent.
adb -s emulator-5554 shell cmd statusbar expand-notifications
```

**Tap** the actual **Hermes Kanban** Android system notification, rather than
simulating an intent directly. The app should navigate to the Kanban task
details displaying `Synthetic E2E alert`, `#1234abcd • dots-personal-agent`
and `DONE`. The fixture `/metrics` should record a request to
`/api/plugins/kanban/tasks/t_1234abcd`. This verifies **the same task ID**
from emitted WebSocket event through system notification into REST-backed UI.

### Repeated runs with an already-persisted event cursor

The test fixture now reads the `since` parameter on the Kanban WebSocket
subscription and advances its synthetic event sequence to at least that cursor.
This prevents a new fixture process from replaying event IDs that the app has
already consumed in earlier QA sessions. Bad or out-of-range cursors are rejected
with HTTP 400. The fixture contract suite includes tests for both cases.

Verified 2026-10-13 on Mac `emulator-5554`: after logging into the isolated
fixture, the dev app was force-stopped and launched again **without clearing app
data**. It reopened the authenticated Chat screen. Backgrounding it reconnected
the Kanban WebSocket with `since=1008`. A single `/trigger` emitted event 1009;
Android posted `Hermes Kanban` for `t_1234abcd`. Tapping the real OS notification
opened `Synthetic E2E alert`, `#1234abcd • dots-personal-agent`, status `DONE`,
and `/api/plugins/kanban/tasks/t_1234abcd` appeared in fixture metrics.
This remains isolated synthetic QA, not production account acceptance.

## Readback and separate production acceptance

Observed 2026-10-12 on Mac Android emulator `Medium_Phone_API_36.1`:
WS subscriber = 1, event = `completed`, OS `NotificationRecord` present,
actual shade tap opened `com.m57.hermescontrol.dev`, exact task ID and
`DONE` displayed, detail REST endpoint fetched. **Synthetic-backend E2E: PASS**.

Original production acceptance `dots-personal-agent / t_5744eca9` remains
**blocked** pending legitimate account sign-in on the evaluation application
or another authorized real-client session, a fresh real Kanban task, notification
delivery, actual tap, and readback against the authoritative AgentHub Kanban.
The evaluation app was still at the unauthenticated landing/login screen when
checked. CI pass or mock E2E must never be misreported as production acceptance.

## Cleanup (does not remove the original evaluation app)

```sh
adb -s emulator-5554 shell am force-stop com.m57.hermescontrol.dev
adb -s emulator-5554 reverse --remove tcp:18588
# Stop the local Python process with Ctrl-C.
```

Do **not** use `pm clear`, `adb uninstall`, `-wipe-data`, or replace the
original evaluation app to complete this test.
