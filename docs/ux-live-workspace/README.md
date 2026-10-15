# Hermes Mobile: real-screen workspace entry — experimental

This is the **first working UI integration** of the finished-experience blueprint, not a product-complete rewrite.

## Intended interaction

- Launch the original Hermes chat in a **.dev debug APK**. The original `ChatScreen`, `ChatInputBar`, voice/attachments, model & reasoning controls, streaming, errors, approvals, progress, and stop generation remain in place.
- Tap the **existing chat title** to expand a contextual sheet without adding a persistent row to the transcript. No bottom-navigation bar is added.
- The sheet shows the **actual session names** from `ChatViewModel`, and loads the **actual existing Bot/Group roster** from `BotsViewModel`. It opens the **real** History, Group Chat, Bots, and Kanban screens through the app's `NavigationController`.
- A local unsent text draft or pending attachment disables context-changing actions, with an explanation. This is a guard in the *new shortcut*; not a claim that every original navigation path preserves drafts.
- Group Chat remains a **discussion**, not durable Kanban workers; no automatic conversation→task handoff or session/task correlation is asserted. Those require backend work and E2E tests.

## Scope and non-regression

- Experimental sheet is gated behind `BuildConfig.DEBUG && applicationId.endsWith(".dev")`; it does not appear in release builds.
- The original drawer and all Hermes screens remain accessible. No `NavigationController`, server, task storage, or API changes.
- This is not the legacy mock: it has no fabricated replies/tasks or duplicate source of truth.
- Mac Android emulator only. **No Flip7 / Proxmox / production Hermes modifications**. No merge without approval.
- This does **not** implement the complete v1 vision: no autonomous Bot routing, persistence of a conversation-to-Group handoff, full Inbox, task correlation, notification integration or multi-device E2E.

## Acceptance (collect real evidence)

| Check | Expect |
| --- | --- |
| Normal Chat | Existing chat title, composer, attachment, model, reasoning, voice and interrupt remain |
| Title tap | Real contextual sheet opens without permanent additional vertical UI |
| Current sessions | List of real sessions, current session selected/disabled |
| Bot / Groups | Loads registered Groups, navigates to real Group Chat or Bots management |
| Work | Opens real full Kanban (not a simplified replacement) |
| Draft protection | With typed text, context-changing shortcuts are disabled, returning leaves text intact |
| Normal navigation | Drawer and old pages continue working |
| Back / restore | Group/Bots/Kanban return to original Chat navigation and preserve server-backed session identity |

A successful compile or visual inspection alone does not prove all these user-operation scenarios. Explicitly mark pending checks rather than assuming a pass.

Design reference: [`HERMES_MOBILE_END_STATE_UX_BLUEPRINT_20261015.md`](https://github.com/hiromare/hermes-mobile/blob/docs/hermes-mobile-nonregression-ux-20261015/docs/HERMES_MOBILE_END_STATE_UX_BLUEPRINT_20261015.md) (Draft PR #15).

## Recorded verification — 2026-10-15

- `:app:assembleDebug :app:ktlintCheck` **PASS** for the initial prototype.
- `:app:testDebugUnitTest --tests com.m57.hermescontrol.ui.chat.components.ConversationWorkspacePolicyTest --tests com.m57.hermescontrol.NavigationControllerTest` **PASS**: 5+30=35 tests, 0 failures.
- Mac Android emulator `emulator-5554`: real original Chat loads with native model/reasoning, attachment, mic, and text composer; title opens the sheet; sheet→real Kanban→back Chat, sheet→real History→back Chat, sheet→real Bots→back Chat **observed PASS**.
- Tested a local unsent draft: guard warning displayed; the test input was subsequently removed without sending. Keyboard initially limited the sheet height, so the prototype now calls `LocalSoftwareKeyboardController.hide()` on sheet opening **without changing the draft**. This correction was rebuilt, and the final emulator UI test confirmed the reordered full-History entry and original chat composer.
- Untested: choosing an actual Group Room and returning, multiple session switching with server resume, selected-task correlation, Background Worker handoff, logged-in web takeover, Galaxy Z Flip7.

No screenshots with personal session titles or Group names are published to the GitHub branch. Use the Mac emulator for the authentic layout.

### Final layout refinement

User's information architecture concern also applies to sheets: **full History** is now above the abbreviated (max 3) recent sessions; the sheet skips partial expansion so it does not hide the Work or Group actions behind a dense list. Opening the sheet hides the software keyboard **without clearing the draft**. The actual composer and transcript remain unchanged when the sheet is dismissed.

This is a **step toward** the complete end-state blueprint, not a claim of feature parity for the unfinished AI routing, Room→Task handoff and external takeover paths.

### Final verification (after layout and keyboard changes)

- `:app:assembleDebug`, `:app:ktlintCheck`, and targeted `:app:testDebugUnitTest`: **BUILD SUCCESSFUL** (2026-10-15 final build).
- `ConversationWorkspacePolicyTest` (5) + `NavigationControllerTest` (30): **35 passed, 0 failed**.
- Fresh installed APK on `emulator-5554`: the native composer still shows original Attach/Reasoning/Mic actions; title tap opens the real-data sheet; **full History shortcut is immediately visible**; History opens and Back returns to the original Chat; all passed. No message, task, approval, or credential mutations were performed.

**Unverified**: Group Room live handoff, full contextual Kanban correlation, app-close/resume workflow, multiple-device consistency. The original feature audit and end-state specification remain separate documents.
