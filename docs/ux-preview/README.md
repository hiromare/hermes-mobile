# Hermes Mobile — conversation-first UX preview (2026-10-15 revision)

**User feedback addressed:** original demo wasted chat viewport height and confused conversation/session, mode, attention and long-running work. The new demo separates those information objects before arranging UI.

## Information architecture

| Entity | One source of truth | Where a user interacts |
| --- | --- | --- |
| **Conversation / Session** | Owns transcript, participants, mode, draft, related work | Full-height Conversation screen; title opens conversation picker |
| **Conversation mode** | Property of each conversation (auto, one-to-one, team, all), not a global switch | New conversation flow; current title reflects selected conversation |
| **Kanban Task** | Durable `task_id` owns actual work, worker state, verification, artifacts, handoff | Work list, Kanban board, task detail |
| **Inbox item** | Derived from work/activity requiring human attention, not a second Kanban | Inbox only for approval, important suggestions, announcements |
| **Agent / Group / Routine / Settings** | Capability and operations; not top-level daily destinations | Navigation / full-features / agents catalog |

From conversation, contextual task links open the durable work item. From Inbox, an action opens the corresponding work detail. From a task detail, a related conversation can be opened. Real linkage is a **future integration**, not implemented by this demo.

## Space allocation

- **Conversation:** a single top app-bar row with the active conversation name, an expandable transcript, then the composer. No persistent bottom tabs, session list row, participant chips, or mode button row. Navigation is accessible from the left icon; title opens conversation picker and top-right plus starts a new conversation.
- **Inbox and Work:** bottom tabs appear because these are not transcript-dense screens. Inbox lists attention-demanding items, not every running task; Work lists all durable tasks and offers a horizontally scrollable Kanban board.
- Conversation audience selection happens when **creating a conversation**, preventing mode changes from silently mixing a 1:1 chat and Group messages within the same session.
- The preview is launched **only in debug `com.m57.hermescontrol.dev`**. No feature is merged into the app's production UI. No real AI, task, approval, identity or credential is accessed.

## Screenshots (revised on Mac Android emulator)

| Full-height Chat | All-member Chat | Session picker | Navigation |
| --- | --- | --- | --- |
| ![chat](chat-revised.png) | ![all](chat-all-revised.png) | ![sessions](conversations-revised.png) | ![navigation](navigation-revised.png) |

| Inbox | Work | Kanban | Create conversation |
| --- | --- | --- | --- |
| ![inbox](inbox-revised.png) | ![work](work-revised.png) | ![kanban](kanban-revised.png) | ![new](new-conversation-revised.png) |

Older initial mock files (chat-auto.png etc) are retained for before/after comparison.

## Validation

- Gradle `:app:assembleDebug :app:ktlintCheck`: PASS.
- Android emulator `emulator-5554` with `ja-JP`: PASS.
- Actual UI tests: chat has **no persistent navigation or mode row**; title opens session picker; choosing all-member room shows 3 bot replies; navigation -> Inbox -> Work -> Kanban -> Chat keeps the conversation; new conversation asks for audience and creates a separate session.
- All examples and conversation responses are sample data, **not live Hermes**.

## Known gaps

Real Inbox aggregation, persistent multi-mode room model, task-session linkage, actual bot profile selection, capability discovery, account/role permissions, real progress, and background task execution are not yet connected. The entire preview is segregated in an independent worktree and does not touch the unsynced session-switcher prototype, production AgentHub, Proxmox, or Flip7.
