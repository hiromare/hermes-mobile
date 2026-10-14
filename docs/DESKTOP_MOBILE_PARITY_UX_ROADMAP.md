# Hermes Mobile — Desktop parity and UX roadmap

Source audit: 2026-10-15 (Desktop checkout `818c13be`, Mobile development branch `feat/mobile-bot-screen-20261015`).
This is a **source-level inventory**, not a claim that every feature works on the deployed gateway.
New UI and privileged commands must be verified against the connected gateway protocol and permissions before implementation.

## Product goal

Do **the same meaningful work as Hermes Desktop**, using mobile-native UI, without replicating Desktop's dense panel layout. Treat Chat as the primary workspace; support non-disruptive tools, context preservation and one-handed actions. Keep existing session/chat/approval flows as the single source of truth.

## Confirmed source-level differences

Legend: **Desktop** = implemented Desktop UI, **Mobile** = existing or missing native UI, **Backend gate** = whether compatibility/exposure must be checked. Items are proposals, not unverified assertions about production availability.

| Area | Desktop evidence | Mobile evidence / state | Priority and required acceptance |
|---|---|---|---|
| Terminal workspace | `apps/desktop/src/app/right-sidebar/terminal/` (persistent terminal, selection, clipboard, session hook) | No terminal screen in `ui/` or `NavigationKeys.kt` | **P0**: session-scoped, audited terminal with scrollback, copy/paste, keyboard toolbar, disconnect/reconnect; **first verify** gateway shell/PTY authorization and routing. No privileged raw command without consent. |
| Git change review | `apps/desktop/src/app/right-sidebar/review/` (file tree, churn, scope, ship bar) | No dedicated diff/review screen | **P0**: tree of changed files, unified diff, review status and navigation; check review API/permissions before modifying commits. |
| Working file tree & preview | `apps/desktop/src/app/right-sidebar/files/`, `chat/right-rail/preview-file.tsx` | `ui/files/FilesScreen.kt` supports listing/upload/download/create folder/delete, **but no Desktop-equivalent integrated project tree and preview/editor** | **P0**: browse active project, syntax/read-only preview first, then guarded editing and upload. Preserve existing Files management screen. |
| Browser/artifact preview | `apps/desktop/src/app/chat/right-rail/preview-{pane,browser-bar,artifact,console}.tsx` | No matching in-app live preview workspace in native UI; browser sign-in/return is present | **P1**: preview active browser/artifact from chat without losing input/session; validate source URL/content trust, auth, read-only default. |
| Command palette / quick actions | `apps/desktop/src/app/command-palette/`, `quick-entry/`, `command-center/` | Per-screen actions and chat slash commands exist; no equivalent universal palette | **P1**: global search/action sheet with current context and permission-aware actions. Prioritize 5 daily operations before listing every command. |
| Session import from other agents/clients | `apps/desktop/src/app/session-import/` | Existing session history, search and `/fork` via `session.branch_whole`/`session.branch`; no equivalent external-session import wizard | **P2**: preview and selectively import external session with deduplication and provenance. Do **not** label normal history/fork as missing. |
| Live speech-to-speech | `apps/desktop/src/lib/voice-live.ts`, `chat/composer/hooks/use-voice-live-conversation.ts`; server `/api/audio/voice-live/{status,session}` | Speech input/voice notes/TTS exist (`ui/chat/SpeechInputHelper.kt`, `VoiceNoteRecorder.kt`, `components/AndroidSpeech.kt`); **no GPT-Live WebRTC session** | **Deferred by user**. Requires explicit cost opt-in, session-scoped delegation, mic/privacy/stop controls, interruption, usage cap. Do not replace existing free voice input. |
| Bot Screen floating viewer | Desktop `plugins/hermes-bots/screen-pane.tsx`; existing RFB and lease APIs | Mobile `ui/bots/screen/` has in-app floating viewer, drag and resize, shared across Chat/Kanban etc on development branch; not yet on Flip7 | **P0 acceptance**: no duplicate header, no overlay covering image, smooth resize/drag/IME/rotation, Take over/Hand back, session preserved; final cross-page and real-device acceptance pending user approval. |
| Starmap / pet overlay | `apps/desktop/src/app/{starmap,pet-overlay,pet-generate}/` | No corresponding Mobile screens | **P3** (optional): decide whether meaningful on a phone instead of porting decorative Desktop chrome blindly. |

## Do not reimplement: capabilities already present on Mobile

- Normal chat, history, send queue, file/image attachment, approvals and session fork/branch.
- Profiles, bots and group chat; Kanban, cron, skills, toolsets, MCP, memory, providers, billing/analytics and system settings.
- Managed file list, uploads/downloads, directory create/delete; voice notes, speech input, response TTS.
- The ongoing Bot Screen work. Improve it rather than building a second viewer or VNC service.

## Backend/UI gaps to verify separately

1. **Remote shell/PTY:** Desktop's terminal may use Desktop IPC or a dedicated gateway route; a local Desktop component is **not proof** Mobile can call that route. Verify supported backend methods, token scope, session lease, audit logging and permission approval. Build neither arbitrary SSH nor secret reveal into Mobile as a shortcut.
2. **Review and preview data:** Trace Desktop IPC and gateway contracts. Prefer stable authenticated server endpoints or introduce a narrowly scoped feature, rather than exposing host files or a broad execution proxy.
3. **Live voice:** Backend REST endpoints exist; Mobile still needs WebRTC/client delegation. Paused by request; not a priority or a free service by default.
4. **Host sound from Bot Screen:** RFB is video/control only; audio is not implemented. Investigate separately from live conversational voice. No audio daemon changes on production VMs without approval.
5. **Multiple-device chat continuity:** Existing `SESSION_NOT_OWNED` issue is a distinct backend/session-owner interoperability gate. Avoid confusing it with UI parity; track as its own reliability task.

## Mobile-first UX workstream

**Design language:** one app toolbar; one contextual action menu; no stacked headers or permanent floating controls obscuring work. Reusable text sizing, skeleton/loading/error/empty states, accessibility labels and safe insets. Dark/light mode and foldable dimensions should remain first-class.

**Context preservation:** tool workspaces opened from chat must return to the same draft, current Bot profile, session, approvals and scroll state. Avoid mounting duplicate ChatViewModels/WebViews; keep one owner for navigation-independent tools.

**Work surface:** use a bottom sheet or tabs for terminal/file/diff/preview with a compact expanded mode. Floating windows are for live screen/video or a single actively monitored tool, not all content. On narrow phones, a full-screen panel is acceptable; on unfolded devices allow split content.

**Input:** one-handed buttons, IME avoidance, gesture-safe drag handle vs remote screen touch, keyboard shortcuts for terminal and code review, selectable/copyable text, large enough touch targets.

**Reliability/privacy:** preserve leases during navigation, visibly show remote/safe-mode state, require confirmation for consequential actions, block cross-profile content leaks, don't expose credentials/session tickets, prevent untrusted web content from controlling native navigation.

## Execution slices (small PRs, not one monolithic redesign)

1. **P0-A UX baseline:** screenshot/interaction audit of Chat, Bots, Kanban, Files and settings at compact and foldable widths; log redundant actions, header duplication, readability and navigation; establish reusable work surface. No server change.
2. **P0-B File/project read-only preview:** introduce a universal work panel with file tree and read-only file preview, reusing existing authorized file APIs where possible.
3. **P0-C Git review:** add source-aware diff viewer and changed-file navigation; ship viewing before mutation (stage/commit/merge requires separate authorization).
4. **P0-D Terminal:** verify server PTY/transport/consent first; then add native terminal with reconnection and keyboard toolbar.
5. **P1 Browser/artifact preview and quick actions:** expand work panel, preserve chat and permissions.
6. **P2 Other Desktop-only surfaces:** session import, additional productivity UIs. Live voice and Bot Screen audio stay deferred; evaluate costs and safety later.

## Acceptance gates

- **Functional:** feature uses the same authorized profile and session; backend failure is actionable and does not silently fall back to a different account.
- **UX:** user can interact with a tool and chat without losing the draft; no unnecessary duplicate toolbar; readable on a phone and a foldable; back behavior predictable.
- **Quality:** targeted unit/UI tests, Android debug build and lint; real Android emulator navigation and gesture verification. Document the difference between source, emulator, gateway and phone acceptance.
- **Release:** keep all work on reviewable Git branches. **Do not install/update the Galaxy Flip7 or change production VMs until explicitly authorized.**

## Evidence index

- Desktop UI: `../hermes-dots-desktop/apps/desktop/src/app/right-sidebar/`, `chat/right-rail/`, `command-palette/`, `session-import/`, `quick-entry/`, `starmap/`, `pet-overlay/`.
- Mobile UI: `app/src/main/java/com/m57/hermescontrol/ui/{chat,files,bots,kanban,projects}/`, `NavigationKeys.kt`.
- Existing parity work: `docs/bot-screen-mobile.md` and GitHub PR #11.
