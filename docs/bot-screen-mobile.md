# Hermes Mobile Bot Screen

Open **Bots** and tap the eye icon to view the selected bot's virtual Linux desktop. Running screens connect automatically; otherwise use **Start screen**. **Reconnect** gets a new one-use display ticket.

![Android emulator Bot Screen controls](screenshots/bot-screen-mobile-emulator.png)

The viewer starts read-only. **Take over** acquires a Hermes `display.lease` for touch/keyboard input, **Hand back** returns control to the agent, and another viewer's takeover revokes local control. Leaving the screen attempts to return the lease. Network loss may leave a server lease behind.

The app uses profile-scoped `display.status`, `display.start`, `display.observe`, `display.lease.acquire`, and `display.lease.release` over the existing JSON-RPC connection. The `api/display/ws` RFB bridge uses a single-use, 30-second `display_ticket`, not a reusable login token. WebView serves locally packaged assets only and restricts the socket to the configured dashboard origin and reverse-proxy prefix. Release builds require encryption. noVNC 1.7.0 and vendor licenses are bundled under `app/src/main/assets/bot_screen/novnc/`.

Verification on 2026-10-15: Gradle compile, four new protocol tests and `assembleDebug` succeeded. The Android 16 emulator installed the APK and displayed **Bot Screen · default** with **Another viewer controls this Bot Screen**, **Reconnect**, and **Take over**. AgentHub reported a running 1440x900 Bot Screen. Live framebuffer, touch control/handback, and Flip7 production installation are not yet verified; the existing human viewer was not interrupted.
