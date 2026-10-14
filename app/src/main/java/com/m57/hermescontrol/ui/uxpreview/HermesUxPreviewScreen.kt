package com.m57.hermescontrol.ui.uxpreview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.m57.hermescontrol.ScreenRegistry

/**
 * An isolated, local-data-only UX mock. This activity NEVER submits real prompts,
 * approvals, API calls or tasks. Only the Android .dev debug app launches it.
 *
 * Information architecture:
 * - Conversations: context / participants / sessions. Conversation mode belongs
 *   to its room, not a global toggle that silently changes the current session.
 * - Inbox: actionable attention items only, not a duplicate Work list.
 * - Work: durable tasks and Kanban presentations of the same jobs.
 * - Agents, integrations and administration: tools reachable via navigation.
 *
 * Chat is immersive: a single title row and a bottom composer, no persistent
 * bottom tabs or mode controls stealing transcript space.
 */
private data class DemoConversation(
    val id: Int,
    val title: String,
    val kind: String,
)

private val initialConversations =
    listOf(
        DemoConversation(1, "Hermesとの会話", "auto"),
        DemoConversation(2, "開発チーム", "team"),
        DemoConversation(3, "全員会議", "all"),
        DemoConversation(4, "開発Bot", "solo"),
    )

private data class DemoTask(
    val title: String,
    val status: String,
    val description: String,
)

private val demoTasks =
    listOf(
        DemoTask("Hermes MobileのUX改善", "実行中", "UX Bot・開発Bot・レビューBot"),
        DemoTask("SO-101の学習環境", "調査中", "研究Bot"),
        DemoTask("広告メールの整理ルール", "確認待ち", "メール担当Bot"),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesUxPreviewScreen(
    onOpenLiveScreen: (NavKey) -> Unit,
    onReturnToLiveApp: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf("chat") }
    var sheet by rememberSaveable { mutableStateOf("") }
    var conversations by remember { mutableStateOf(initialConversations) }
    var activeConversationId by rememberSaveable { mutableStateOf(1) }
    var input by rememberSaveable { mutableStateOf("") }
    var localMessage by rememberSaveable { mutableStateOf("") }
    var board by rememberSaveable { mutableStateOf(false) }
    var selectedTask by rememberSaveable { mutableStateOf("") }
    var featureInfo by rememberSaveable { mutableStateOf("") }
    val conversation =
        conversations.firstOrNull { it.id == activeConversationId }
            ?: initialConversations.first()
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    fun openSection(destination: String) {
        section = destination
        selectedTask = ""
        sheet = ""
    }

    fun createConversation(kind: String) {
        val newId = (conversations.maxOfOrNull { it.id } ?: 0) + 1
        val title =
            when (kind) {
                "all" -> "全員会議 #$newId"
                "team" -> "開発チーム #$newId"
                "solo" -> "開発Bot #$newId"
                else -> "Hermesとの会話 #$newId"
            }
        conversations = conversations + DemoConversation(newId, title, kind)
        activeConversationId = newId
        localMessage = ""
        input = ""
        openSection("chat")
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sheet = if (section == "chat") "conversations" else "navigation"
                                },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                when (section) {
                                    "chat" -> conversation.title
                                    "inbox" -> "受信箱"
                                    "work" -> "仕事"
                                    else -> "Hermes"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (section == "chat") {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "会話を切り替える",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { sheet = "navigation" }) {
                        Icon(Icons.Default.Menu, contentDescription = "ナビゲーション")
                    }
                },
                actions = {
                    if (section == "chat") {
                        IconButton(onClick = { sheet = "newConversation" }) {
                            Icon(Icons.Default.Add, contentDescription = "新しい会話")
                        }
                    }
                    IconButton(onClick = { sheet = "features" }) {
                        Icon(Icons.Default.Apps, contentDescription = "全機能")
                    }
                },
            )
        },
        bottomBar = {
            // Conversation owns the height. Global navigation lives in the
            // drawer on this page; tabs appear only on the less dense surfaces.
            if (section != "chat" && !keyboardVisible) {
                NavigationBar {
                    listOf(
                        Triple("inbox", "受信箱", Icons.Default.Home),
                        Triple("work", "仕事", Icons.Default.ViewKanban),
                        Triple("chat", "会話", Icons.AutoMirrored.Filled.Chat),
                    ).forEach { (destination, label, icon) ->
                        NavigationBarItem(
                            selected = section == destination,
                            onClick = { openSection(destination) },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { insets ->
        Column(Modifier.padding(insets).fillMaxSize()) {
            when (section) {
                "chat" -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            Text(
                                "UXプレビュー · 実際のAIとは接続しません",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (activeConversationId <= 4) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Text(
                                            "この設計について意見を聞かせて",
                                            modifier = Modifier.padding(12.dp),
                                        )
                                    }
                                }
                            }
                            val exampleReplies =
                                when (conversation.kind) {
                                    "all" -> {
                                        listOf(
                                            "開発Bot" to "会話と仕事の紐付けはKanban task_idを基準にします。",
                                            "UX Bot" to "会話中は履歴を最優先し、切替UIは必要な時だけ表示します。",
                                            "レビューBot" to "実行状況と権限は仕事の詳細から確認できるべきです。",
                                        )
                                    }

                                    "team" -> {
                                        listOf(
                                            "開発Bot" to "既存の仕組みで実装方針を整理します。",
                                            "レビューBot" to "権限や停止・再開の仕様を確認します。",
                                        )
                                    }

                                    "solo" -> {
                                        listOf(
                                            "開発Bot" to "この会話は専門Botとの1対1です。",
                                        )
                                    }

                                    else -> {
                                        listOf(
                                            "Hermes" to "必要な専門Botを選び、仕事を進めます。進捗は仕事から確認できます。",
                                        )
                                    }
                                }
                            items(exampleReplies) { (agent, response) ->
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(
                                        agent,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                    ) {
                                        Text(response, modifier = Modifier.padding(12.dp))
                                    }
                                }
                            }
                            if (conversation.kind == "team" || conversation.kind == "all") {
                                item {
                                    TextButton(onClick = {
                                        selectedTask = demoTasks.first().title
                                        section = "work"
                                    }) {
                                        Icon(Icons.Default.ViewKanban, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("関連する仕事を開く")
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                    }
                                }
                            }
                        } else {
                            item {
                                Text(
                                    "新しい会話です。ここから話しかけられます。",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (localMessage.isNotBlank()) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Text(localMessage, modifier = Modifier.padding(12.dp))
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    if (conversation.kind == "all") {
                                        "@all 全員へ質問する…"
                                    } else {
                                        "メッセージを入力"
                                    },
                                )
                            },
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp),
                        )
                        IconButton(
                            onClick = {
                                localMessage = input.trim()
                                input = ""
                            },
                            enabled = input.isNotBlank(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "デモメッセージを追加")
                        }
                    }
                }

                "inbox" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            PreviewHeading("確認してほしいこと", "重要な判断だけを表示。進捗一覧とは分けます。")
                        }
                        item {
                            DemoCard(
                                title = "確認待ち · 広告メールの整理",
                                subtitle = "同じ条件のメールを、今後は自動で整理してよいですか？",
                                detail = "理由と対象を確認する",
                                onClick = {
                                    selectedTask = "広告メールの整理ルール"
                                    section = "work"
                                },
                            )
                        }
                        item {
                            PreviewHeading("Hermesからの提案", "自律的に発見した重要なことを届けます。")
                        }
                        item {
                            DemoCard(
                                title = "UI改善のレビュー候補",
                                subtitle = "実装がまとまった後に最終確認をお願いする予定です。",
                                detail = "進行中の仕事を見る",
                                onClick = {
                                    selectedTask = "Hermes MobileのUX改善"
                                    section = "work"
                                },
                            )
                        }
                    }
                }

                "work" -> {
                    if (selectedTask.isNotEmpty()) {
                        Column(
                            Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            TextButton(onClick = { selectedTask = "" }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("仕事に戻る")
                            }
                            Text(
                                selectedTask,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "同じtask_idに、担当・実行状態・会話・証拠・成果物を紐づけます。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            DemoCard("担当・実行", "専門Bot / Team / Worker", "実際のWorker状態は未接続")
                            DemoCard("証拠・成果物", "実行ログ / テスト / ファイル", "タスク完了を推測で表示しない")
                            Button(
                                onClick = {
                                    activeConversationId = 2
                                    openSection("chat")
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("関連する会話へ")
                            }
                            OutlinedButton(
                                onClick = {
                                    selectedTask = ""
                                    board = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Kanban表示")
                            }
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("継続中の仕事", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            AssistChip(
                                onClick = { board = !board },
                                label = { Text(if (board) "一覧表示" else "Kanban表示") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ViewKanban,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                        }
                        if (board) {
                            LazyRow(
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(listOf("未着手", "実行中", "確認待ち", "完了")) { column ->
                                    Card(modifier = Modifier.width(240.dp)) {
                                        Column(
                                            Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text(column, fontWeight = FontWeight.Bold)
                                            val tasks =
                                                demoTasks.filter {
                                                    if (column == "実行中") {
                                                        it.status == "実行中" || it.status == "調査中"
                                                    } else {
                                                        it.status == column
                                                    }
                                                }
                                            if (tasks.isEmpty()) {
                                                Text(
                                                    "タスクなし",
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            tasks.forEach { task ->
                                                Surface(
                                                    onClick = { selectedTask = task.title },
                                                    shape = RoundedCornerShape(10.dp),
                                                ) {
                                                    Text(task.title, modifier = Modifier.padding(12.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(demoTasks) { task ->
                                    DemoCard(
                                        task.title,
                                        task.status,
                                        task.description,
                                        onClick = { selectedTask = task.title },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (sheet.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = {
            sheet = ""
            featureInfo = ""
        }) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (sheet) {
                    "navigation" -> {
                        Text("移動する", style = MaterialTheme.typography.titleLarge)
                        listOf(
                            Triple("chat", "会話", "AIに相談する・会話を再開"),
                            Triple("inbox", "受信箱", "確認待ち・提案・重要な通知"),
                            Triple("work", "仕事", "継続タスク・Kanban・成果物"),
                        ).forEach { (destination, label, subtitle) ->
                            SheetEntry(label, subtitle) { openSection(destination) }
                        }
                        HorizontalDivider()
                        SheetEntry("エージェント・チーム", "Profiles / Group Chat / 役割と能力") {
                            sheet = "agents"
                        }
                        SheetEntry("本来のHermesへ戻る", "既存のチャットと全機能を開く") {
                            sheet = ""
                            onReturnToLiveApp()
                        }
                        SheetEntry("既存の全機能", "元の機能を一つも隠さず直接開く") {
                            sheet = "features"
                        }
                    }

                    "conversations" -> {
                        Text("会話を選ぶ", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "会話相手・モード・履歴は会話ごとに管理します。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        conversations.forEach { item ->
                            SheetEntry(
                                item.title,
                                when (item.kind) {
                                    "all" -> "全員会話"
                                    "team" -> "チーム会話"
                                    "solo" -> "専門Botとの1対1"
                                    else -> "おまかせ"
                                },
                            ) {
                                activeConversationId = item.id
                                localMessage = ""
                                sheet = ""
                            }
                        }
                        HorizontalDivider()
                        SheetEntry("＋ 新しい会話", "相手・モードを選んで始める") {
                            sheet = "newConversation"
                        }
                    }

                    "newConversation" -> {
                        Text("新しい会話", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "普段はおまかせ。指定したい時だけ専門Botやチームを選びます。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        listOf(
                            Triple("auto", "おまかせ", "Hermesが必要な担当を判断"),
                            Triple("solo", "1対1", "専門Botを直接指定"),
                            Triple("team", "チーム", "既存Groupのメンバーと会話"),
                            Triple("all", "全員", "複数Botの意見を同じ会話へ"),
                        ).forEach { (kind, label, subtitle) ->
                            SheetEntry(label, subtitle) { createConversation(kind) }
                        }
                    }

                    "agents" -> {
                        Text("エージェント・チーム", style = MaterialTheme.typography.titleLarge)
                        listOf(
                            "開発Bot" to "コード・テスト・開発ツール",
                            "UX Bot" to "情報設計・ユーザー体験",
                            "レビューBot" to "検証・受入基準",
                            "研究Bot" to "論文・実験",
                            "開発チーム" to "専門Botの協議と協業",
                        ).forEach { (name, role) ->
                            SheetEntry(name, role) {
                                featureInfo = "$name の管理画面は本プレビューに未接続です。"
                            }
                        }
                        if (featureInfo.isNotEmpty()) Text(featureInfo, style = MaterialTheme.typography.bodySmall)
                    }

                    "features" -> {
                        Text("Hermesの既存機能", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "ここからはデモではなく、従来の実際のHermes画面に移動します。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        SheetEntry("本来のHermesを開く", "元のチャットと機能一覧へ戻る") {
                            sheet = ""
                            onReturnToLiveApp()
                        }
                        HorizontalDivider()
                        ScreenRegistry.ALL_SCREENS.forEach { screen ->
                            SheetEntry(
                                stringResource(screen.labelRes),
                                "既存の機能を開く",
                            ) {
                                sheet = ""
                                onOpenLiveScreen(screen.key)
                            }
                        }
                    }
                }
                Spacer(Modifier.padding(bottom = 10.dp))
            }
        }
    }
}

@Composable
private fun PreviewHeading(
    title: String,
    subtitle: String,
) {
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SheetEntry(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
    }
}

@Composable
private fun DemoCard(
    title: String,
    subtitle: String,
    detail: String = "",
    onClick: (() -> Unit)? = null,
) {
    Card(
        onClick = { onClick?.invoke() },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
