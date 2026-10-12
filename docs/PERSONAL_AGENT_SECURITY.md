# Personal Agent Android client — secure deployment baseline

This is the `hiromare` fork's initial, **not-yet-validated** deployment baseline for using Hermes Mobile to manage a personal Hermes Agent with powerful connected tools.

## Connection and authentication

- Release builds require HTTPS/WSS at the Android platform network-security and `ServerEndpoint.parseForBuild` policy levels. The debug variant retains cleartext to test local fixtures; **do not use debug APKs with production credentials**.
- Do not expose an unauthenticated Hermes gateway to the public Internet. Prefer **Tailscale Serve HTTPS with tailnet access controls** (or an equivalently authenticated HTTPS reverse proxy) in front of a loopback-bound Hermes dashboard. Hermes session authentication is still required after the VPN / proxy boundary.
- Never use a long-lived Cloudflare Access service token as a substitute for Hermes authentication. If a service token is necessary, keep its scope as narrow as possible and preserve the user's existing central credential-management boundary.
- The Hermes agent must not receive raw GitHub, email, or infrastructure secrets from the phone app merely to make a mobile session work.
- Avoid printing or storing bearer/session tokens, WebSocket tickets, cookies, auth headers, or secrets in URL histories, logs, or task descriptions. Inspect fresh WS tickets and redirect handling before production approval.

## App permissions and authority

The upstream app currently requests microphone and package-install permissions, registers voice-assistant services, and exposes powerful server management UI (including environment keys). These are **review items**, not a claim of exploitation. Enable only the Android permissions the user needs; do not configure it as the system voice assistant until its authority and lifecycle are reviewed. Do not use an in-app APK updater for production builds without signature and distribution review.

The app is an **administrative client**, not merely a chat messenger. A phone session can inherit the gateway's full capabilities. Device lock, app storage encryption, authenticated transport, logout/revocation, and least-privilege server credentials all matter.

## Required end-to-end acceptance before production

1. Validate that release APK blocks `http://` and `ws://`, including a migrated saved HTTP profile; debug still permits localhost fixtures.
2. Validate self-hosted HTTPS login, session expiry, per-connection single-use WebSocket ticket, logout, reconnect, and no cross-origin auth/header leak.
3. Validate chat ↔ standard Hermes Kanban board `dots-personal-agent` task ID continuity; inspect run history after the Android app is backgrounded and reopened.
4. Validate a real needs-input/approval prompt from an ordinary, low-risk test task and the correct resume on the **same** task ID.
5. Validate a notification while the Android app is backgrounded or stopped; clearly distinguish local foreground-service notifications from true server push and don't claim off-process delivery until observed.
6. Validate Galaxy Z Flip7 compact and unfolded UI, Android notification reply behavior, authentication rejection, and disconnection recovery.
7. Run pinned unit/lint/Android tests in CI; review manifest, network security config, release artifacts, supply-chain dependencies, and APK signing before installing a privileged client.

This document is intentionally explicit about **unverified** behavior. No Hermes server credentials or public endpoints are stored in this repository.
