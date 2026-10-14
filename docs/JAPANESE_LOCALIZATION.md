# Japanese localization (ja)

## Coverage and availability

- Added `app/src/main/res/values-ja/strings.xml` containing all existing English resource keys (including plurals) as of this branch.
- Added **日本語** to the in-app language picker in Settings > Language. Android's Japanese system locale also selects these resources when the in-app preference is System.
- The source English resource definitions are the fallback for any future untranslated keys. Feature branches that add English strings (e.g. multi-session chat switcher, Inbox / Agents / Teams UI) must add corresponding `values-ja` entries before merge.
- Verified the same resource keys and format tokens (`%1$s`, `%d`, etc.) in both locales. This comparison should be rerun as resources evolve.

## Quality status

This is an **initial full-coverage Japanese translation**, not a native-editor-finalized release. A first pass covered every resource string and plural, followed by manual corrections of more than 700 UX-critical entries (navigation, direct chat, Bots/Groups, sessions, Kanban, credentials/approvals/deletions, notifications, settings). Additional strings may need idiomatic review in the less frequently used settings and management flows. UI display strings embedded directly in Kotlin were extracted in the most visible and safety-critical screens; some remaining developer-oriented labels, examples and dynamically generated messages still require future extraction.

The term **Bot** remains a product-level identity; **Group Chat** means a multi-bot discussion, not a Kanban Swarm durable worker run. Future Inbox / Work / Agent architecture should retain this vocabulary distinction.

## Manual acceptance

1. Use a Mac Android emulator with `com.m57.hermescontrol.dev`. **Do not install to Flip7 until user approval.**
2. Settings > Language > 日本語 should recreate the Activity and switch navigation plus existing content into Japanese; changing back to English should also work.
3. Check Chat and approval prompts, Bots/Group Chat, Sessions, Kanban status and destructive confirmations, and login/credential handling. Verify no `String.format` runtime exceptions.
4. Check narrow layouts, long Japanese labels, truncation, accessibility content descriptions, IME overlap, and both themes.
5. Record and fix any awkward translations in the reviewed resource, not with on-the-fly translation of user data or code.
