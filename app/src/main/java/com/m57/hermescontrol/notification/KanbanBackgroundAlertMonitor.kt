package com.m57.hermescontrol.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.model.KanbanBoardResponse
import com.m57.hermescontrol.data.model.KanbanBoardsResponse
import com.m57.hermescontrol.data.model.KanbanTask
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.OkHttpProvider
import com.m57.hermescontrol.data.repository.KanbanRepository
import com.m57.hermescontrol.data.repository.KanbanRepositoryImpl
import com.m57.hermescontrol.data.ws.KanbanEventsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString

private val BOARD_SLUG_PATTERN = Regex("[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}")
private const val MAX_MONITORED_BOARDS = 12
private const val PERSONAL_AGENT_BOARD = "dots-personal-agent"
private const val MAX_PENDING_MAIL_ATTENTION = 3

// Historical events are silent; remind only currently blocked owner mail decisions.
internal fun pendingMailAttentionTasks(board: KanbanBoardResponse): List<KanbanTask> =
    board.columns
        .filter { it.name.equals("blocked", ignoreCase = true) }
        .flatMap { it.tasks }
        .filter {
            it.status.equals("blocked", ignoreCase = true) &&
                it.title.startsWith("Gmail 広告の扱いを確認 (") &&
                it.id.matches(Regex("t_[a-zA-Z0-9_-]{4,64}"))
        }
        .distinctBy { it.id }
        .sortedWith(compareBy<KanbanTask> { it.createdAt ?: Long.MAX_VALUE }.thenBy { it.id })
        .take(MAX_PENDING_MAIL_ATTENTION)

internal suspend fun loadPendingMailAttention(
    repository: KanbanRepository,
    board: String,
): List<KanbanTask> =
    if (board != PERSONAL_AGENT_BOARD) {
        emptyList()
    } else {
        when (val response = repository.getBoard(board = board)) {
            is NetworkResult.Success -> pendingMailAttentionTasks(response.data)
            is NetworkResult.Failure -> emptyList()
        }
    }

/** Collision-free task identity across boards. */
internal fun kanbanNotificationTag(
    board: String,
    taskId: String,
): String = "kanban:$board:$taskId"

/** Notifications are for terminal or human-action events, never heartbeat noise. */
internal fun kanbanEventAlertKind(kind: String): String? =
    when (kind) {
        "completed" -> "completed"
        "blocked", "needs_input", "review_requested", "failed" -> "needs attention"
        else -> null
    }

/** Validate, de-duplicate, and cap untrusted board IDs from the dashboard API. */
internal fun selectKanbanAlertBoards(ids: List<String>): List<String> =
    ids
        .filter { it != "null" && BOARD_SLUG_PATTERN.matches(it) }
        .distinct()
        .take(MAX_MONITORED_BOARDS)

/** Use the same wire model as the Kanban screen (JSON uses "slug", not "id"). */
internal fun parseKanbanAlertBoards(body: String): List<String> =
    selectKanbanAlertBoards(
        OkHttpProvider
            .json
            .decodeFromString<KanbanBoardsResponse>(body)
            .boards
            .map { it.id },
    )

/** Board discovery uses the Kanban screen's authenticated REST client, including token/cookie modes. */
internal suspend fun loadKanbanAlertBoards(repository: KanbanRepository): List<String> =
    when (val result = repository.getBoards(includeArchived = false)) {
        is NetworkResult.Success -> selectKanbanAlertBoards(result.data.boards.map { it.id })
        is NetworkResult.Failure -> emptyList()
    }

/**
 * Monitor all accessible active boards rather than server-current: the mobile
 * Kanban screen can select a different board locally without updating
 * server-current. One bounded WS per board reuses Hermes' native WS auth.
 *
 * The monitor lives ONLY in the app's existing opted-in foreground service:
 * no second daemon, timer, credential, or polling system. Each server/board
 * cursor is persistent; initial connections ignore historical events by age.
 */
internal class KanbanBackgroundAlertMonitor(
    private val context: Context,
    private val repository: KanbanRepository = KanbanRepositoryImpl(),
) {
    private val clients = mutableListOf<KanbanEventsClient>()
    private val prefs = context.getSharedPreferences("hermes_kanban_alert_cursors", Context.MODE_PRIVATE)
    private val startupEpochSeconds = System.currentTimeMillis() / 1000

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val boards = loadKanbanAlertBoards(repository)
                if (!scope.isActive) return@launch
                if (boards.size >= MAX_MONITORED_BOARDS) {
                    Log.w(TAG, "Kanban notification board subscription limit reached")
                }
                val endpoint = AuthManager.endpointForBuild().baseUrl.toString()
                // Reuse the existing opted-in foreground monitor; no new
                // owner authorization path or unattended background daemon.
                if (scope.isActive && PERSONAL_AGENT_BOARD in boards) {
                    val pending =
                        try {
                            loadPendingMailAttention(repository, PERSONAL_AGENT_BOARD)
                        } catch (e: Exception) {
                            // A backlog lookup must never prevent the ordinary
                            // authenticated Kanban event streams from starting.
                            Log.w(TAG, "Owner-mail attention read unavailable: " + e.javaClass.simpleName)
                            emptyList()
                        }
                    if (com.m57.hermescontrol.BuildConfig.DEBUG) {
                        Log.d(TAG, "Startup owner-mail attention tasks: " + pending.size)
                    }
                    pending.forEach { task ->
                        val key = "owner_mail_attention_v2|" + endpoint + "|" + PERSONAL_AGENT_BOARD + "|" + task.id
                        if (!prefs.getBoolean(key, false)) {
                            // Android may shed queued notifications during a burst.
                            // Persist delivery only after it appears in activeNotifications.
                            if (notifyTask(PERSONAL_AGENT_BOARD, task.id, "Gmail proposal needs your decision")) {
                                delay(800)
                                val delivered =
                                    try {
                                        val manager =
                                            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                        manager.activeNotifications.any {
                                            it.tag == kanbanNotificationTag(PERSONAL_AGENT_BOARD, task.id) && it.id == 0
                                        }
                                    } catch (e: RuntimeException) {
                                        Log.w(TAG, "Owner-mail notification check failed: " + e.javaClass.simpleName)
                                        false
                                    }
                                if (delivered) {
                                    prefs.edit().putBoolean(key, true).apply()
                                } else {
                                    Log.w(TAG, "Owner-mail attention notification not visible to Android")
                                }
                            }
                        }
                    }
                }
                boards.forEach { board ->
                    if (!scope.isActive) return@launch
                    val key = "events_" + (endpoint + "|" + board).hashCode().toString()
                    var cursor = prefs.getLong(key, 0L)
                    val firstConnection = !prefs.contains(key)
                    val client = KanbanEventsClient()
                    clients.add(client)
                    client.connect(
                        scope = scope,
                        board = board,
                        since = cursor,
                        onEvents = { envelope ->
                            envelope.events.sortedBy { it.id }.forEach { event ->
                                if (event.id <= cursor) return@forEach
                                cursor = event.id
                                prefs.edit().putLong(key, cursor).apply()
                                val kind = kanbanEventAlertKind(event.kind) ?: return@forEach
                                val taskId =
                                    event.taskId?.takeIf { it.matches(Regex("t_[a-zA-Z0-9_-]{4,64}")) }
                                        ?: return@forEach
                                if (firstConnection && (event.createdAt ?: 0) < startupEpochSeconds - 2) {
                                    return@forEach
                                }
                                notifyTask(board, taskId, kind)
                            }
                        },
                        onStatus = { status ->
                            if (com.m57.hermescontrol.BuildConfig.DEBUG) {
                                Log.d(TAG, "Kanban $board alert stream status: $status")
                            }
                        },
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Kanban background alerts unavailable: " + e.javaClass.simpleName)
            }
        }
    }

    fun stop() {
        clients.forEach { it.disconnect() }
        clients.clear()
    }

    private fun notifyTask(
        board: String,
        taskId: String,
        kind: String,
    ): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val intent =
            Intent(context, KanbanNotificationTapActivity::class.java).apply {
                setPackage(context.packageName)
                action = KanbanNotificationTapActivity.ACTION_OPEN_KANBAN_NOTIFICATION
                data =
                    Uri
                        .Builder()
                        .scheme("hermes-kanban")
                        .authority("task")
                        .appendPath(board)
                        .appendPath(taskId)
                        .build()
                putExtra(KanbanNotificationTapActivity.EXTRA_KANBAN_BOARD, board)
                putExtra(KanbanNotificationTapActivity.EXTRA_KANBAN_TASK, taskId)
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val text = "$taskId · $kind · $board"
        val notification =
            NotificationCompat
                .Builder(context, ChatNotificationService.CHAT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Hermes Kanban")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(kanbanNotificationTag(board, taskId), 0, notification)
        return true
    }

    private companion object {
        const val TAG = "KanbanBackgroundAlerts"
    }
}
