package com.m57.hermescontrol.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.m57.hermescontrol.MainActivity
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.remote.OkHttpProvider
import com.m57.hermescontrol.data.ws.KanbanEvent
import com.m57.hermescontrol.data.ws.KanbanEventsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/** Notifications are only for terminal or human-action events, never heartbeat/replay noise. */
internal fun kanbanEventAlertKind(kind: String): String? =
    when (kind) {
        "completed" -> "completed"
        "blocked", "needs_input", "review_requested", "failed" -> "needs attention"
        else -> null
    }

/**
 * Subscribes to the server's current Kanban board while the existing foreground
 * service is alive. Reuses the dashboard's authenticated WebSocket and existing
 * chat notification channel: no second daemon, secret, or background poller.
 *
 * A per-server/board event cursor survives service recreation. The very first
 * connection ignores historical events by timestamp; subsequent reconnects
 * replay only unseen events. No task descriptions or event payloads are exposed
 * on the lock screen.
 */
internal class KanbanBackgroundAlertMonitor(
    private val context: Context,
) {
    private val eventsClient = KanbanEventsClient()
    private val prefs = context.getSharedPreferences("hermes_kanban_alert_cursors", Context.MODE_PRIVATE)
    private var connected = false
    private val startupEpochSeconds = System.currentTimeMillis() / 1000

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                val board = fetchCurrentBoard() ?: return@launch
                val endpoint = AuthManager.endpointForBuild().baseUrl.toString()
                val key = "events_" + (endpoint + "|" + board).hashCode().toString()
                var cursor = prefs.getLong(key, 0L)
                val firstConnection = !prefs.contains(key)
                connected = true
                eventsClient.connect(
                    scope = scope,
                    board = board,
                    since = cursor,
                    onEvents = { envelope ->
                        envelope.events.sortedBy { it.id }.forEach { event ->
                            if (event.id <= cursor) return@forEach
                            cursor = event.id
                            // Persist the cursor even for heartbeat and suppressed historical events.
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
                            Log.d(TAG, "Kanban alert stream status: $status")
                        }
                    },
                )
            } catch (e: Exception) {
                Log.w(TAG, "Kanban background alerts unavailable: " + e.javaClass.simpleName)
            }
        }
    }

    fun stop() {
        if (connected) {
            connected = false
            eventsClient.disconnect()
        }
    }

    private fun fetchCurrentBoard(): String? {
        val url = AuthManager.endpointForBuild().resolve("api/plugins/kanban/boards")
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .build()
        OkHttpProvider.probe.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body.string()
            val obj = OkHttpProvider.json.parseToJsonElement(body).jsonObject
            val current = obj["current"]?.jsonPrimitive?.content
            if (!current.isNullOrBlank()) return current
            return obj["boards"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.content
        }
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
            Intent(context, MainActivity::class.java).apply {
                setPackage(context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                taskId.hashCode(),
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
        manager.notify(KANBAN_NOTIFICATION_BASE + (taskId.hashCode() and 0x00ffffff), notification)
    }

    private companion object {
        const val TAG = "KanbanBackgroundAlerts"
        const val KANBAN_NOTIFICATION_BASE = 20_000
    }
}
