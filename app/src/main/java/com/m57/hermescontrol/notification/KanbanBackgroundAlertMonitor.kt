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
import com.m57.hermescontrol.data.model.KanbanBoardsResponse
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.OkHttpProvider
import com.m57.hermescontrol.data.repository.KanbanRepository
import com.m57.hermescontrol.data.repository.KanbanRepositoryImpl
import com.m57.hermescontrol.data.ws.KanbanEventsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString

private val BOARD_SLUG_PATTERN = Regex("[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}")
private const val MAX_MONITORED_BOARDS = 12
/** Collision-free task identity across boards. */
internal fun kanbanNotificationTag(board: String, taskId: String): String = "kanban:$board:$taskId"

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
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val intent =
            Intent(context, KanbanNotificationTapActivity::class.java).apply {
                setPackage(context.packageName)
                action = KanbanNotificationTapActivity.ACTION_OPEN_KANBAN_NOTIFICATION
                data =
                    Uri.Builder()
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
    }

    private companion object {
        const val TAG = "KanbanBackgroundAlerts"
    }
}
