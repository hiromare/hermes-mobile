---
title: Hermes Mobile 完成形UX契約 — 既存機能非退行監査
updated: 2026-10-15
status: design-audit-not-accepted
scope: Android Hermes Mobile + Hermes AgentHub integration
---

# Hermes Mobile 完成形UX契約 — 既存機能を失わずに自律エージェントへ進化

## 0. 位置付けと証拠の強さ

本書は「まず画面を作る」方針を撤回し、**実装済みの操作・状態・文脈と、それらをつなぐ完成形の体験**を先に定義するための基線。2026-10-15時点の Mac上のソース（/Users/kokiyamamoto/Developer/hermes-mobile-ux-preview、ベース faba545、レビュー枝 bf0ae2d 系）を静的監査し、Androidエミュレータ emulator-5554 で**通常チャット／履歴／Bot／Kanbanの画面を開く読み取り確認**を実施した。認証情報・秘密・ユーザーの会話本文は記録しない。エミュレータではタスク実行、通知E2E、ツール使用、承認実行、複数端末同期は**未実証**。

判断の根拠は3段階で記録する。
- **S: ソース確認** — UI構成／メソッド／状態・API呼出経路がある。現環境で動作したとは意味しない。
- **U: 実機UI確認** — emulator-5554上で目的画面や表示・遷移を確認。サーバー操作・データの更新はしていない。
- **E: 実操作E2E** — 実行→外部読戻し→再起動/別端末→同一ID確認を含む。今回の監査では未実施。

Hermes本番 v0.21.6 の受入状況・未完了事項は親ノート「Hermes Dots Personal Agent」を正とする。ここに製品の機能が存在することと、Hermes Mobile経由の受入が終わったことを混同しない。

## 1. 完成形の製品ビジョンと不変条件

**人間からは一人のパーソナルAIに見え、裏側では専門Profile・Group・独立Kanban Workerが仕事を進める。自律発見・着手・再開・報告・介入がつながる。その一方で、既存Hermesの全操作を元と同等以上に行える。**

不変条件：
1. 既存画面へのリンクだけでは「機能維持」と認めない。**入力、状態変化、途中の操作、エラー復旧、完了までの経路**を保証する。
2. Chatの利用可能な縦領域を削らない。会話切替やモード選択は必要時に開く。コンポーザーの既存操作（モデル/思考/添付/録音/停止等）を省略しない。
3. Conversation / Session ≠ Kanban Task。各々の正本を二重化せず task_id / session_id / profile_id / room_id / project の関連を追跡する。
4. Group ChatはBot同士の対話・発言調整の場。Kanban Swarmの永続Workerと等価にしない。Group Chatの画面が閉じても永続作業が必要ならKanbanに引き渡す。
5. 一つの行為に対する承認・許可は人間のポリシーと既存実行系が保持。一般作業は広い事前委任、不可逆な例外だけ承認。UI操作の省略で強制承認を迂回しない。
6. Android・Mac・PC・ChatGPTで、仕事の正本と外部実行の事実を照合可能にする。推測で「完了」表示しない。
7. 接続切替・プロフィール切替・失効・オフラインで、別アカウントのデータや下書きを交差表示させない。
8. 既存のWiki/秘密管理/Cron/Kanbanを再利用し、第二のタスク正本DBや独自スケジューラを増やさない。

## 2. 情報オブジェクトの責務（画面名より先に固定）

| オブジェクト | 正本・ID | 保持/操作する状態 | 混ぜてはいけないもの |
| --- | --- | --- | --- |
| **Conversation/Session** | Hermes Session ID。履歴API・WS | 本文、履歴、モデル、入力下書き、添付、送信待ち、ストリーム、検索・タイムライン、起点(source)、プロジェクト、ピン/アーカイブ/分岐 | Kanban workerの永続実行をsessionの生存に依存させない |
| **Profile/Bot** | Hermes Profile名・Bot metadata | 役割、モデル、ツール・権限、記憶、active/last/canonical session、グループ所属 | 個人のチャット1件と同一視しない。ProfileとBotsの管理面は重複責務を整理する |
| **Group/Room** | group name/room snapshot + membership | メンバー、Group Chatメッセージ、active speaker、発言上限、room lease、再開可能なログ | Android側の逐次会話を自律永続Kanbanと誤認しない |
| **Kanban Task** | Hermes board + task_id | status、assignee、priority、parent/children、run_id、worker PID/heartbeat、approval/blocked、PR/証拠・添付・イベント、再割当・停止再開 | チャットの「進行中」表示を正本にしない |
| **Project** | Hermes projects | プロジェクト文脈、フォルダ、関連するsession | 個人Google Tasks正本、Kanban正本の代用にしない |
| **Routine/Event** | Cron job・Webhook・Channels | 定期/イベント起点、変更監視、送信先、実行履歴 | 自動生成されたSessionを通常の会話と混在させない |
| **Inbox/Activity** | 既存イベント・Kanban・会話からの読取ビュー | 確認が必要なこと、重要な差分、完了報告、通知の既読/抑制（将来の導入要件） | 二つ目のタスク管理・独立した実行正本にしない |
| **Capability/Asset** | Skills / Toolsets / MCP / Plugins / Files / Bot Screen | 実行能力、接続設定、成果物、実際のPC/ブラウザ操作 | ダミーのメニュー名だけで能力を実装済みとしない |
| **Identity/Security** | AuthManager / Credential Vault・既存認可 | プロファイル/接続ごとの資格情報、OAuth、委任、外部ブラウザからの復帰 | 秘密原本をUI状態やログで複製しない |

## 3. ソース監査：既存機能の完全な移管対象

主なエントリポイントは app/src/main/java/com/m57/hermescontrol/ScreenRegistry.kt の **28画面**と Navigation.kt のドリルダウン（GroupChat、Kanban詳細、設定各頁、Toolset、MemoryProvider等）。各画面の入口だけでなく、以下の既存挙動を残す。

| 領域 | 現行で確認した主要操作・状態 (S) | 根拠コード | UX再編時に失われやすい能力 |
| --- | --- | --- | --- |
| **Chat** | session新規、ストリーム、検索、履歴タイムライン、メッセージ/ツール証拠、コンテキスト使用量、モデル/推論/高速切替、Slash command、送信待ち再送・破棄、音声メモ/録音・画像/ファイル/カメラ、添付保存、生成停止、後処理 | ui/chat/ChatScreen.kt; ui/chat/ChatViewModel.kt; ui/chat/components/ChatInputBar.kt; ChatLifecycleEffects.kt | 「全画面チャット」を口実にコンポーザーや例外状態を削除 |
| **承認/接続復旧** | Clarify、sudo/secret入力導線、接続/再ログイン、画面上の進行・失敗通知、外部Browserからの復帰 | ui/chat/ChatViewModel.kt; ui/chat/components/ClarifyBubble.kt; ui/chat/components/ChatConnectionBanner.kt | 会話内の承認をすべてInboxへ強制移動し、処理文脈を失う |
| **History/Sessions** | 会話/自動化の区分、起点ソース・Project絞り込み、検索、ページング、ピン/名前変更/非表示/アーカイブ、複数選択・削除、分岐・親Session、キャッシュ、同時更新、破損データ耐性 | ui/sessions/SessionsScreen.kt; ui/sessions/SessionsViewModel.kt; data/local/SessionListCacheStore.kt | 最近の会話Pickerへの置換で、検索/フィルタ/管理が消える |
| **Profiles** | 有効Profile選択、作成/複製/削除/非表示、Soul編集、説明・モデル・ピン、Hubからの作成 | ui/profiles/ProfilesViewModel.kt | 「Botカタログ」と統合して詳細設定を隠す |
| **Bots** | Bot検索/選択/作成/編集/削除、active now、Group作成・解散、メンバー維持 | ui/bots/BotsViewModel.kt; BotsScreen.kt | ProfileとBotが二重の実態に見え、状態が矛盾 |
| **Group Chat** | 各BotのSession確保・発言、@mentionの応答者決定、発言上限、継続応答、停止、room snapshot/lease、設定 | ui/bots/group/GroupChatViewModel.kt; GroupChatScreen.kt | チャット画面の「全員ボタン」で現在Sessionを黙って別構造に変える |
| **Kanban board** | ボード作成/変更/削除、検索・フィルタ、列表示、移動、複数選択、一括移動/割当/削除/アーカイブ、Workflows、import/export、Worker・オーケストレーション設定 | ui/kanban/KanbanScreen.kt; KanbanViewModel.kt | 仕事リストや簡略ボードに置換して、従来の高機能操作を剥がす |
| **Kanban Task** | 概要・議論・Runs・Activity・Files、担当/モデル変更、見積・説明・依存リンク、添付、コメント、再キュー、reclaim、停止・実行検査 | ui/kanban/KanbanTaskScreen.kt; KanbanTaskViewModel.kt; data/model/KanbanTaskModels.kt | 完了表示だけの単純なTaskカードにする |
| **Projects** | 作成・編集・アーカイブ・削除・フォルダ連携/primary設定 | ui/projects/ProjectsViewModel.kt | Projectsをただのタグにしてファイル関係を壊す |
| **Cron/Webhooks** | 作成/編集、監視モード、単発起動、pause/resume、配送先、Cronレポート・履歴、Webhook購読・削除 | ui/cron/CronJobsViewModel.kt; ui/webhooks/WebhooksViewModel.kt | ホーム上の「自動化」の一行だけに集約し詳細管理を失う |
| **Skills/MCP/Toolsets/Plugins** | 有効化/無効化、検索/インストール/更新、Hub/カタログ、環境変数とMCP OAuth、接続検証、Provider/Plugin設定 | ui/skills; ui/mcp; ui/toolsets; ui/plugins | カタログ表示はあっても実際に設定・修正できない |
| **Files/Connections** | ファイル作成/アップロード/ダウンロード/削除、接続プロファイル、OAuthコネクターの有効化・Policy・ブラウザ復帰 | ui/files; ui/settings; ui/connectors | 新UIでURI/Storage Access・外部認証の復帰が欠ける |
| **Models/Settings** | 主モデル/補助モデル/MoA、費用の高いモデルの確認、外観/言語/送信設定/セッション復元/通知、接続切替 | ui/model; ui/settings | トグルの値がProfile/Session/全体のどこに効くか不明になる |
| **管理・監査** | Channels、Gateway、System、Logs、Analytics、Billing、Keys、Pairing、Config、Memory、Providers、Achievements等 | ScreenRegistry.kt; 対応ViewModel | 高度な操作を「全機能リンク」だけに追いやる |
| **Push/返信** | Chat完了通知、通知への返信、Kanban通知タップ→特定task_id、Foreground Service、Turn相関境界 | notification/ChatNotificationService.kt; NotificationReplyReceiver.kt; KanbanNotificationTapActivity.kt; MainActivity.kt; NavigationController.kt | 通知タップ後に別Sessionや別taskを開く・返信重複・バックグラウンド喪失 |
| **ナビゲーション・隔離** | ルート/ドリルダウンのBack復帰、チャットへのreturn、通知起動、profile/connectionのスコープ分離 | NavigationController.kt; Navigation.kt; data/local/AuthManager.kt; SessionsViewModel.kt | タブ切替時にSessionやBack stackが消える、別接続の内容が混ざる |

ソースの機能存在（S）を列挙したものであり、各機能の現実の成功（E）までは主張しない。未監査の小機能も削除可能という意味ではない。全既存画面＋ドリルダウンからの操作は移行時の**自動生成可能な台帳**に含める。

## 4. 完成形の主要な「行動のシナリオ」

以下のシナリオは、個別画面ではなく**開始→途中状態→例外→再開→成果**の契約として受け入れる。新UIのコンポーネントに移行しても、従来操作をすべて実行可能にする。

| ID | 利用者の行動・期待する全経路 | 完了基準 |
| --- | --- | --- |
| F01 | 通常Chatで指示→送信→ストリーミング→ツール・根拠確認→必要時停止→再送/継続 | 現行Chatと同等の全操作と途中状態を保持 |
| F02 | 添付・写真・音声を送る→進行中に別アプリへ→復帰→再開 | 下書き/添付/返信が勝手に失われず、結果の二重表示なし |
| F03 | 1つのBotと話す→別Sessionを作成→検索/アーカイブ→元のSession再開 | BotとSessionを別IDで管理し履歴の管理操作維持 |
| F04 | Group Chatで @all /個別メンション→複数Botの議論→途中停止→続きを話す | Bot単位の発言とroom設定を保持。Kanban実行とは区別 |
| F05 | 議論の結論から継続タスク作成→Kanbanで分業→Worker途中停止・再開→レビュー | 同一task_id、担当・run・成果・検証根拠を追跡 |
| F06 | Kanbanで作成/列移動/依存リンク/再割当/一括操作/見積・添付・export | 従来のボード・詳細・管理操作を新UIからも同じように実行 |
| F07 | GitHub/Cron/Webhookの自律イベント→重複なく起票→並列実行→結果報告 | 本人指示なし・通常事前委任範囲で実作業が進む。外部読戻しを完了証拠にする |
| F08 | AIがメールを整理→初回だけ分類ルールを本人に確認→以後は委任範囲で自動 | 承認済みスコープ内の再確認を抑え、取り消し可能、外部操作の証拠あり |
| F09 | Webログインで停止→Bot Screen/Browserを人間が引き継ぐ→AIへ戻す | 秘密値は漏らさず、元task_id/同じ実行点に再参加 |
| F10 | 仕事完了のPushを押す→該当task/議論/成果を確認→同じ仕事へ返信 | 通知と遷移先IDが一致。Android再起動や別端末からの再開可 |
| F11 | 既存履歴の検索/ピン/隠す/アーカイブ/分岐/一括削除 | 操作できるだけでなく既存データの一覧・復帰・更新を保つ |
| F12 | Cron/MCP/Skills/Plugins/Channels/Modelsの管理→設定変更→接続確認 | 「全機能へ行ける」でなく設定・更新・テスト・復旧を完了 |
| F13 | Google複数アカウント/プロフィールを切替→関連Chat/履歴を操作 | 通常利用の接続を維持し、他接続の資格情報/Sessionが混線しない |
| F14 | 失敗・認証切れ・429・端末切断→状態表示→回復・本人介入→再開 | 従来の失敗時回復が残り、成功でない処理を「完了」と表示しない |

## 5. 完成形のUXアーキテクチャ（タブ数や配置は未決）

**単一の中心会話 + 複数の目的/役割別会話 + 永続Work。すべてを同じ画面へ押し込まない。**

- **会話サーフェス**: チャット本文を主役にする。モデル・推論・録音・添付・失敗復旧・承認・停止は文脈付きの既存UIを保持。Session切替やBot/Group選択は必要時のSheet等に退避してよいが、全機能と操作速度は悪化させない。人間が「全員と話す」を選んだ際、既存1:1 Sessionを密かにGroupへ変えず、別のRoom/Sessionを明示する。
- **仕事サーフェス**: Kanbanの現行ボード・タスク詳細を完全に活かす。全体サマリーが必要なら **同じKanban IDから派生する別表示**とし、編集・復旧・Worker詳細を失わせない。Group議論は同じtask_idに紐付けた参照として扱う。
- **Inbox/報告**: 問題発見、本人判断、重要差分、成果通知を並べる**既存イベントからの投影ビュー**。同じ「実行中の仕事」を重複リスト化しない。承認は発生した場でもInboxでも同一の要求ID/権限を参照する。
- **エージェント管理**: Profile、Bot、Group、Group Chat、Kanban Swarmの意味を分ける。発見と実行の選択は原則AIが担当し、本人は明示的に専門BotやGroupを指定できる。新規専用Bot DBを作らない。
- **高度な機能**: 既存の画面/操作を機能ごとに維持。各サーフェスから関連する実画面へ**作業文脈と戻り先を持って**移動する。メニューから開くだけのリンクは最低条件でしかない。
- **マルチデバイス**: task_idやsession_idを基点として同一の実作業を開く。ローカル状態の推測で「実行中/完了」を表示しない。

UIナビゲーションの下部/上部という議論を**ここで凍結しない**。既存の操作契約を満たした上で、実際のチャット表示面積・タップ数・状態遷移を比較して決める。

## 6. 全機能非退行の受入ゲート

| Gate | 検証項目 | 合格の証拠 |
| --- | --- | --- |
| G0 | 元の機能台帳作成 | 全ScreenRegistry・Settingsドリルダウン・チャット/Group/Kanban内操作を列挙、削除0件。追加変更は理由付き |
| G1 | UI同等性 | 主要操作の到達・実行・戻り先、チャット本文の可視高さ、操作タップ数の旧版比較 |
| G2 | 行為と状態の維持 | F01〜F06・F11〜F12 の実E2E。途中終了/復帰・エラーを含む |
| G3 | 自律性と権限 | F07〜F09 の事前委任/承認例外/秘密非露出/元タスク再開の実証 |
| G4 | 連携・端末間一貫性 | F10・F13〜F14、通知tap→同一ID、再起動、別端末、同期競合の検証 |
| G5 | 長期耐久 | 24〜72時間監視、2+Worker並行、料金とログ、外部読戻しと重複回避 |

すべてE2E試験の費用・危険・外部効果を事前に確認する。**この文書を書いたことでどのGateも合格とはしない**。旧版/新UIの比較では、操作名だけでなく成功状態と遷移先IDを比較する。モデル品質やBackendの既知不具合をAndroid UIだけの問題と誤認しない。

## 7. 現時点の検証状況・差分と決定保留

**U確認済み**: Mac Androidエミュレータで元の日本語チャット（入力欄、ナビゲーション、モデル表示）、元の履歴（会話・自動化・検索）、Bot画面（グループ）、Kanban画面（ボード・タスクへの入口）を表示。画面上のメニュー等の書き込み系操作は未実行。会話内容などユーザーデータは本記録に採録しない。

**S確認済み**: ChatViewModel / SessionsViewModel / BotsViewModel / GroupChatViewModel / KanbanViewModel/TaskViewModel /各設定・機能の責務、Notification→NavigationController経路、AuthManager接続スコープ、既存テストの存在。

**E未検証**: モデル切替実行、添付/音声、Bot間の実発話、Worker実動作、Kanban操作、Cron実発火、通知実機往復、Login takeover、Profile/connection isolationの操作E2E。テストコードの存在≠E2E合格。

**発見したUX試作の問題**: 完成形を先に定義せずに作った初版（feat/mobile-ux-preview-bottom-nav-20261015）は、固定タブ/モード列でチャットの縦を縮め、既存のComposer/通知/編集機能の大半を表現できず、実際のCapabilityの代わりに擬似データを使った。「本物の画面へアクセス可」に直しただけでも機能の非退行は証明できない。この試作は**設計素材であり本採用しない**。未マージを維持する。

**未決事項**:
1. 既存Hermesの現行実行機能と、Androidだけで未提供の機能の差分を項目単位に確定する。
2. マルチSessionの別Bot/Group表示構造を、実バックエンドのID/起点/復元仕様に合わせる。
3. Inboxの権威あるイベント・要求ID・既読状態を既存正本へ対応付ける（新DBを作るかは既存APIを確認後）。
4. 完成形UIのナビ配置・常時要素数は、旧版と新案の実操作比較を終えるまで決めない。
5. Group ChatとKanban Swarmの双方向handoffはAPI有無と停止/再開契約を先に検証。

## 8. これからの設計作業の順番

1. **旧版の操作契約を固定**: ScreenRegistry＋各Screenの操作イベント＋ViewModelのAPI・エラー・復元を機械抽出し、F01〜F14へ対応付け。既存自動テスト/手動テストの対応表を作成。
2. **理想UXの詳細シナリオ**: 同じ操作をどう短くするか、いつ通知し、いつ介入するか、Botはどこで自動選択されるかを書き切る。Chat/Group/Kanbanの状態・IDの保存先も決定。
3. **実画面を用いた比較試作**: 既存ChatInputBar/GroupChatScreen/KanbanTaskScreenを再利用し、既存の高度な操作を失わずにレイアウトだけ調整。擬似データで「完成」としない。
4. **旧版 vs 改善版のE2E回帰**: 通常チャット/Group/Kanban/検索/設定/通知/切断復帰の成功証拠を比較。
5. **本人による体験レビュー**: 完成像と回帰条件を満たした候補のみ、Macエミュレータで提示。マージ・Flip7更新は別途承認。

### 禁止・保留
- 本人の明示確認まで本PR群をマージしない。Flip7は触らない。Proxmoxはread-only含め触らない。
- 保留中の4090/SESSION_NOT_OWNED、GPT-Live音声、本番認証/秘密操作をUX監査の名目で再開しない。
- 実データ接続のないデモを「既存機能の置き換え可能」と評価しない。

## 9. ソース参照・検証した画面

- app/src/main/java/com/m57/hermescontrol/ScreenRegistry.kt
- app/src/main/java/com/m57/hermescontrol/Navigation.kt / NavigationController.kt / MainActivity.kt
- app/src/main/java/com/m57/hermescontrol/ui/chat/ChatScreen.kt / ChatViewModel.kt
- app/src/main/java/com/m57/hermescontrol/ui/sessions/SessionsScreen.kt / SessionsViewModel.kt
- app/src/main/java/com/m57/hermescontrol/ui/bots/BotsViewModel.kt / group/GroupChatViewModel.kt
- app/src/main/java/com/m57/hermescontrol/ui/kanban/KanbanScreen.kt / KanbanTaskScreen.kt / KanbanViewModel.kt / KanbanTaskViewModel.kt
- app/src/main/java/com/m57/hermescontrol/data/model/KanbanTaskModels.kt / Profile.kt
- app/src/main/java/com/m57/hermescontrol/notification/ChatNotificationService.kt / KanbanNotificationTapActivity.kt
- app/src/main/java/com/m57/hermescontrol/ui/settings, cron, webhooks, mcp, skills, plugins, projects, files, model, connectors, system等
- 親要件：10-Knowledge/Projects/Hermes Dots Personal Agent.md
- 先行案：10-Knowledge/Projects/Hermes Mobile UX Architecture 20261015.md

この設計は**機能非退行の契約と完成形の方向**を定めるもので、UI詳細の最終確定やE2E実証を宣言しない。

## 10. 今回追加実施した既存テストのベースライン

2026-10-15、Macのローカル開発ブランチでGradle **:app:testDebugUnitTest** を以下の既存テストクラスに限定して実行。結果はすべて failures=0 / errors=0 / skipped=0。

| テストクラス | 成功件数 | 対象 |
| --- | ---: | --- |
| NavigationControllerTest | 30 | ナビゲーション遷移・戻り先制御 |
| SessionsViewModelTest | 38 | 履歴・選択・セッション状態 |
| GroupChatViewModelTest | 11 | Group Chat状態・発話制御 |
| KanbanViewModelTest | 14 | ボード状態・タスク操作 |
| KanbanTaskViewModelTest | 18 | タスク詳細・実行制御 |
| KanbanBackgroundAlertMonitorTest | 11 | Kanban通知監視 |
| **合計** | **122** | **単体テスト。機能E2Eは未実証** |

再現コマンド：Android Studio付属JBRを JAVA_HOME に設定し、offline Gradle で :app:testDebugUnitTest と各クラス名を --tests で指定。ログはMacローカルの /tmp/hermes-ux-audit-tests.log および /tmp/hermes-ux-contract-targeted-tests.log、XMLは app/build/test-results/testDebugUnitTest に生成。実証の範囲はユニットテストとエミュレータの表示・遷移確認まで。
