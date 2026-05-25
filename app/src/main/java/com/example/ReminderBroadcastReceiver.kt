package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("ReminderReceiver", "Received broadcast action: $action")

        if (action == "android.intent.action.BOOT_COMPLETED") {
            // Reschedule all active future alarms after device reboot
            Log.d("ReminderReceiver", "Rescheduling alarms on boot...")
            val db = AppDatabase.getDatabase(context)
            val dao = db.taskDao()
            CoroutineScope(Dispatchers.IO).launch {
                val now = System.currentTimeMillis()
                val tasks = dao.getActiveTasksAfter(now)
                for (task in tasks) {
                    ReminderScheduler.schedule(context, task)
                }
            }
        } else if (action == "com.example.ACTION_SHOW_REMINDER") {
            val taskId = intent.getLongExtra("TASK_ID", -1L)
            val title = intent.getStringExtra("TASK_TITLE") ?: "Compromisso de Hoje"
            val desc = intent.getStringExtra("TASK_DESC") ?: "Você tem uma tarefa pendente na agenda!"

            if (taskId != -1L) {
                // Instantly present the notification
                NotificationHelper.showReminderNotification(context, taskId, title, desc)
            }
        }
    }
}
