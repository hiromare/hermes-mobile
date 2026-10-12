package com.m57.hermescontrol.notification

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.m57.hermescontrol.MainActivity
import com.m57.hermescontrol.NavigationController

/**
 * Private notification tap trampoline.
 *
 * PendingIntent launches this non-exported activity directly, so Android can
 * grant a user-initiated activity launch. A BroadcastReceiver attempting to
 * start an activity is blocked by Android 16 background-activity-launch rules.
 *
 * No public launcher action or forgeable extras are accepted by MainActivity.
 */
class KanbanNotificationTapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        val board = intent?.getStringExtra(EXTRA_KANBAN_BOARD)
        val taskId = intent?.getStringExtra(EXTRA_KANBAN_TASK)
        if (
            intent?.action == ACTION_OPEN_KANBAN_NOTIFICATION &&
            board != null &&
            taskId != null &&
            BOARD_PATTERN.matches(board) &&
            TASK_PATTERN.matches(taskId)
        ) {
            NavigationController.openKanbanTaskFromNotification(board, taskId)
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
            )
        }
        finish()
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
