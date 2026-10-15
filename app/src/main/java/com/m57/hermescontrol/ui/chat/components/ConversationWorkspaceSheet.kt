package com.m57.hermescontrol.ui.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.ui.bots.BotsViewModel
import com.m57.hermescontrol.ui.chat.SessionUi

/** Return true when leaving the conversation may abandon an unsent local draft. */
internal fun workspaceHasUnsavedDraft(
    composerText: CharSequence,
    pendingAttachmentCount: Int,
    isSending: Boolean,
): Boolean = composerText.isNotBlank() || pendingAttachmentCount > 0 || isSending

/**
 * Experimental conversation/work surface over the *real* Hermes chat.
 *
 * Uses the existing session and bot/group backends and navigates to existing
 * History, Bots, Group Chat and Kanban UIs. No simulated agents or task state.
 * Group/Task correlation is not implemented; do not claim a handoff occurred.
 *
 * The parent guards against losing local composer text/attachments on any
 * session switch or navigation. This is deliberately opt-in in the .dev APK.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationWorkspaceSheet(
    activeTitle: String,
    activeSessionId: String?,
    sessions: List<SessionUi>,
    hasComposerDraft: Boolean,
    onDismiss: () -> Unit,
    onSwitchSession: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenBots: () -> Unit,
    onOpenKanban: () -> Unit,
    botsViewModel: BotsViewModel = viewModel { BotsViewModel(autoLoad = false) },
) {
    val botsState by botsViewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(Unit) {
        botsViewModel.loadBots()
    }
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("会話と仕事", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "現在の会話：$activeTitle",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hasComposerDraft) {
                Text(
                    "入力中の文章や添付があります。内容を失わないよう、送信または整理するまで切り替えを保留します。",
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item { SectionHeading("会話を再開する") }
                item {
                    WorkspaceDestination(
                        label = "すべての会話・検索・管理",
                        enabled = !hasComposerDraft,
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        onClick = {
                            onDismiss()
                            onOpenHistory()
                        },
                        testTag = "workspace_history",
                    )
                }
                items(sessions.distinctBy { it.id }.take(3), key = { "session:${it.id}" }) { session ->
                    TextButton(
                        onClick = {
                            onDismiss()
                            onSwitchSession(session.id)
                        },
                        enabled = !hasComposerDraft && session.id != activeSessionId,
                        modifier = Modifier.fillMaxWidth().testTag("workspace_session_${session.id}"),
                    ) {
                        Text(
                            session.title.ifBlank { "会話" },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (session.id == activeSessionId) {
                            Text("現在", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp)) }
                item { SectionHeading("専門Bot・グループと相談") }
                when {
                    botsState.isLoading -> {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                                Text("Groupを読み込んでいます", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    botsState.errorMessage != null -> {
                        item {
                            Text("Groupの取得に失敗しました。Bot管理画面から確認できます。", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    botsState.allGroups.isEmpty() -> {
                        item {
                            Text("まだ登録済みGroupがありません。Bot画面から作成できます。", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    else -> {
                        items(botsState.allGroups.take(5), key = { "group:${it.name}" }) { group ->
                            WorkspaceDestination(
                                label = "${group.name}（${group.members.size}体）",
                                enabled = !hasComposerDraft,
                                icon = { Icon(Icons.Default.Groups, contentDescription = null) },
                                onClick = {
                                    onDismiss()
                                    onOpenGroup(group.name)
                                },
                                testTag = "workspace_group",
                            )
                        }
                    }
                }
                item {
                    WorkspaceDestination(
                        label = "Bot・Groupの管理と作成",
                        enabled = !hasComposerDraft,
                        icon = { Icon(Icons.Default.SmartToy, contentDescription = null) },
                        onClick = {
                            onDismiss()
                            onOpenBots()
                        },
                        testTag = "workspace_bots",
                    )
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp)) }
                item { SectionHeading("継続する仕事") }
                item {
                    WorkspaceDestination(
                        label = "Kanban・Worker・タスク詳細",
                        enabled = !hasComposerDraft,
                        icon = { Icon(Icons.Default.ViewKanban, contentDescription = null) },
                        onClick = {
                            onDismiss()
                            onOpenKanban()
                        },
                        testTag = "workspace_kanban",
                    )
                }
                item {
                    Text(
                        "この試作は実画面につながりますが、会話とKanbanタスクの自動紐付け・引き継ぎはまだ未実装です。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        modifier = Modifier.padding(vertical = 5.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun WorkspaceDestination(
    label: String,
    enabled: Boolean,
    testTag: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    ) {
        icon()
        Spacer(Modifier.padding(horizontal = 6.dp))
        Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
    }
}
