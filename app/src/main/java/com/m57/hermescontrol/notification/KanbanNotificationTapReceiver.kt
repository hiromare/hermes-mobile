package com.m57.hermescontrol.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.m57.hermescontrol.MainActivity
import com.m57.hermescontrol.NavigationController

/**
 * Non-exported notification-tap entry point.
 *
 * MainActivity is exported for the launcher, so public action strings / extras
 * there are forgeable by other apps. An immutable PendingIntent to this private
 * receiver is instead created by our notification and delivered by Android.
 *
 * Pending navigation is held in the same application process before the
 * launcher activity is brought to front, so cold-start and already-running
 * MainActivity both consume the same task ID.
 */
class KanbanNotificationTapReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_OPEN_KANBAN_NOTIFICATION) return
        val board = intent.getStringExtra(EXTRA_KANBAN_BOARD) ?: return
        val taskId = intent.getStringExtra(EXTRA_KANBAN_TASK) ?: return
        if (!BOARD_PATTERN.matches(board) || !TASK_PATTERN.matches(taskId)) return

        NavigationController.openKanbanTaskFromNotification(board, taskId)
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
        )
    }

    companion object {
        const val ACTION_OPEN_KANBAN_NOTIFICATION =
            "com.m57.hermescontrol.INTERNAL_OPEN_KANBAN_NOTIFICATION"
        const val EXTRA_KANBAN_BOARD = "com.m57.hermescontrol.EXTRA_KANBAN_BOARD"
        const val EXTRA_KANBAN_TASK = "com.m57.hermescontrol.EXTRA_KANBAN_TASK"

        private val BOARD_PATTERN = Regex("[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}")
        private val TASK_PATTERN = Regex("t_[a-zA-Z0-9_-]{4,64}")
    }
}
