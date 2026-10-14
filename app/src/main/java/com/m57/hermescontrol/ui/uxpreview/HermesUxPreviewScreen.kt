package com.m57.hermescontrol.ui.uxpreview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Isolated UX concept running only in the .dev debug APK.
 *
 * Sample tasks and messages are NOT backed by Hermes APIs. No prompt, approval,
 * event, or backend task is submitted from this concept screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesUxPreviewScreen() {
    var tab by rememberSaveable { mutableStateOf("chat") }
    var mode by rememberSaveable { mutableStateOf("auto") }
    var participant by rememberSaveable { mutableStateOf("開発Bot") }
    var session by rememberSaveable { mutableStateOf("Hermesとの会話") }
    var input by rememberSaveable { mutableStateOf("") }
    var localMessage by rememberSaveable { mutableStateOf("") }
    var board by rememberSaveable { mutableStateOf(false) }
    var selectedTask by rememberSaveable { mutableStateOf("") }
    var allFeatures by rememberSaveable { mutableStateOf(false) }
    var participantPicker by rememberSaveable { mutableStateOf(false) }
    var sessionPicker by rememberSaveable { mutableStateOf(false) }
    var featureDescription by rememberSaveable { mutableStateOf("") }
    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Hermes", fontWeight = FontWeight.Bold)
                        Text(
                            "UXコンセプト・実データではありません",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { allFeatures = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "全機能メニュー")
                    }
                },
                actions = {
                    IconButton(onClick = { allFeatures = true }) {
                        Icon(Icons.Default.Apps, contentDescription = "すべての機能")
                    }
                },
            )
        },
        bottomBar = {
            if (!keyboardVisible) {
                NavigationBar {
                    val tabs =
                        listOf(
                            Triple("home", "ホーム", Icons.Default.Home),
                            Triple("work", "仕事", Icons.Default.ViewKanban),
                            Triple("chat", "会話", Icons.Default.Chat),
                        )
                    tabs.forEach { (id, label, icon) ->
                        NavigationBarItem(
                            selected = tab == id,
                            onClick = {
                                tab = id
                                selectedTask = ""
                            },
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(label) },
                            alwaysShowLabel = true,
                        )
                    }
                }
            }
        },
    ) { insets ->
        Column(Modifier.padding(insets).fillMaxSize()) {
            when (tab) {
                "home" -> {
                    PreviewHeading("今日のHermes", "判断が必要なことと、進んでいる仕事だけをまとめます。")
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            DemoCard(
                                title = "確認待ち · 1件",
                                subtitle = "広告メールの整理ルールを、次回から自動化しますか？",
                                detail = "メール担当Bot · 例示データ",
                                onClick = {
                                    tab = "work"
                                    selectedTask = "広告メールの整理ルール"
                                },
                            )
                        }
                        item {
                            DemoCard(
                                title = "進行中 · Hermes MobileのUX改善",
                                subtitle = "UX Botと開発Botが並行して作業しています。",
                                detail = "実行ログ・成果物・Kanbanを見る",
                                onClick = {
                                    tab = "work"
                                    selectedTask = "Hermes MobileのUX改善"
                                },
                            )
                        }
                        item {
                            DemoCard(
                                title = "調査中 · SO-101の学習環境",
                                subtitle = "研究Botが学習・推論環境を確認しています。",
                                detail = "担当Bot・関連する会話を見る",
                                onClick = {
                                    tab = "work"
                                    selectedTask = "SO-101の学習環境"
                                },
                            )
                        }
                        item {
                            OutlinedButton(onClick = {
                                tab = "chat"
                                mode = "auto"
                            }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Chat, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Hermesに相談する")
                            }
                        }
                    }
                }

                "work" -> {
                    if (selectedTask.isNotEmpty()) {
                        Column(
                            Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TextButton(onClick = { selectedTask = "" }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("仕事に戻る")
                            }
                            Text(
                                selectedTask,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("この仕事の状態・担当Bot・成果物を一箇所に集約します。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            DemoCard("担当Bot・Team", "必要な専門AIが共同で進める", "Profile / Group / Worker の関係を確認")
                            DemoCard("履歴・証拠・成果物", "実行イベント、検証結果、ファイル", "完成版では既存Kanban task_idに紐づけます")
                            Button(onClick = {
                                tab = "chat"
                                mode = "team"
                                selectedTask = ""
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text("担当チームと会話する")
                            }
                            OutlinedButton(onClick = {
                                selectedTask = ""
                                board = true
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text("Kanbanボードを見る")
                            }
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("仕事", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            AssistChip(onClick = {
                                board = !board
                            }, label = {
                                Text(if (board) "一覧表示" else "Kanban表示")
                            }, leadingIcon = {
                                Icon(
                                    Icons.Default.ViewKanban,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            })
                        }
                        if (board) {
                            LazyRow(
                                contentPadding =
                                    androidx.compose.foundation.layout
                                        .PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(listOf("未着手", "進行中", "確認待ち", "完了")) { column ->
                                    Card(modifier = Modifier.width(230.dp)) {
                                        Column(
                                            Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Text(column, fontWeight = FontWeight.Bold)
                                            val examples =
                                                when (column) {
                                                    "進行中" -> listOf("Hermes MobileのUX改善", "SO-101の学習環境")
                                                    "確認待ち" -> listOf("広告メールの整理ルール")
                                                    else -> emptyList()
                                                }
                                            if (examples.isEmpty()) {
                                                Text(
                                                    "タスクなし",
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            examples.forEach { name ->
                                                Surface(
                                                    onClick = { selectedTask = name },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                ) {
                                                    Text(name, modifier = Modifier.padding(12.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(listOf("Hermes MobileのUX改善", "SO-101の学習環境", "広告メールの整理ルール")) { name ->
                                    DemoCard(
                                        title = name,
                                        subtitle = if (name.contains("広告")) "確認待ち" else "進行中",
                                        detail = "タップして担当・履歴・成果を確認",
                                        onClick = { selectedTask = name },
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(onClick = { sessionPicker = true }) {
                            Icon(Icons.Default.History, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(session, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
                        IconButton(onClick = {
                            session = "新しい会話"
                            localMessage = ""
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "新しい会話")
                        }
                    }
                    Text(
                        "会話する相手",
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(
                        contentPadding =
                            androidx.compose.foundation.layout
                                .PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            listOf("auto" to "おまかせ", "solo" to "1対1", "team" to "チーム", "all" to "全員"),
                        ) { (id, title) ->
                            FilterChip(selected = mode == id, onClick = { mode = id }, label = { Text(title) })
                        }
                    }
                    if (mode == "solo" || mode == "team") {
                        TextButton(onClick = { participantPicker = true }, modifier = Modifier.padding(start = 8.dp)) {
                            Icon(
                                if (mode ==
                                    "team"
                                ) {
                                    Icons.Default.Groups
                                } else {
                                    Icons.Default.Person
                                },
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (mode == "team") "開発チーム" else participant)
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                        }
                    }
                    HorizontalDivider()
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding =
                            androidx.compose.foundation.layout
                                .PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            Text(
                                "会話UIの試作 · 実際のAIとは接続しません",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text("この設計について意見を聞かせて", modifier = Modifier.padding(12.dp))
                                }
                            }
                        }
                        val replies =
                            when (mode) {
                                "all" -> {
                                    listOf(
                                        "開発Bot" to "既存のKanbanと会話を、同じ仕事につなげたいです。",
                                        "UX Bot" to "会話中でも進捗へすぐ移動できるとよさそうです。",
                                        "レビューBot" to "権限と処理状況が確認できる設計が重要です。",
                                    )
                                }

                                "team" -> {
                                    listOf("開発Bot" to "実装方針をまとめます。", "レビューBot" to "この案の懸念点を確認します。")
                                }

                                "solo" -> {
                                    listOf(participant to "個別Botに直接相談する画面です。")
                                }

                                else -> {
                                    listOf("Hermes" to "必要な専門Botを選んで仕事を進めます。結果は一つの仕事にまとめます。")
                                }
                            }
                        items(replies) { (agent, reply) ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(agent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Text(
                                        reply,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                        if (localMessage.isNotBlank()) {
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                    ) {
                                        Text(localMessage, modifier = Modifier.padding(12.dp))
                                    }
                                }
                                Text("画面上だけのメッセージ", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(if (mode == "all") "@all 全員へ質問する…" else "メッセージを入力") },
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
            }
        }
    }

    if (allFeatures) {
        ModalBottomSheet(onDismissRequest = { allFeatures = false }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Hermesの全機能", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("どの機能もなくしません。通常の画面から直接開くための入口です。", style = MaterialTheme.typography.bodySmall)
                if (featureDescription.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Text(featureDescription, modifier = Modifier.padding(14.dp))
                    }
                }
                listOf(
                    "Bots / Profiles" to "専門Botの役割、モデル、Skills、権限を管理",
                    "Group Chat / Team" to "複数Botとの会議、全員会話",
                    "Kanban / Swarm" to "長期仕事、Worker、レビュー、証拠を確認",
                    "Cron / Routines" to "定期実行や自動化を管理",
                    "Skills / MCP" to "ツール、連携先、能力を設定",
                    "Projects / Files" to "プロジェクトと成果物を確認",
                    "Bot Screen" to "AIの画面を確認・操作を引き継ぐ",
                    "Logs / Settings" to "モデル、費用、ログ、認証を管理",
                ).forEach { (title, detail) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (title.startsWith("Kanban")) {
                                    allFeatures = false
                                    tab = "work"
                                    board = true
                                    selectedTask =
                                        ""
                                } else {
                                    featureDescription = "$title：$detail（ナビゲーション接続は未実装のプレビューです）"
                                }
                            }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider()
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (sessionPicker) {
        ModalBottomSheet(onDismissRequest = { sessionPicker = false }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("会話を切り替える", style = MaterialTheme.typography.titleLarge)
                listOf("Hermesとの会話", "Hermes Mobile UX改善", "SO-101の研究", "新しい会話").forEach { item ->
                    TextButton(onClick = {
                        session = item
                        sessionPicker = false
                        localMessage = ""
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(item, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (participantPicker) {
        ModalBottomSheet(onDismissRequest = { participantPicker = false }) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("専門Botを選択", style = MaterialTheme.typography.titleLarge)
                listOf("開発Bot", "UX Bot", "レビューBot", "研究Bot", "生活管理Bot").forEach { item ->
                    TextButton(onClick = {
                        participant = item
                        participantPicker = false
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(item, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PreviewHeading(
    title: String,
    subtitle: String,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
