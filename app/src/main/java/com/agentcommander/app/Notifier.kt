package com.agentcommander.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

const val ACTION_STOP = "com.agentcommander.app.STOP"
const val ACTION_FINISH_LEARN = "com.agentcommander.app.FINISH_LEARN"
const val ACTION_PAUSE = "com.agentcommander.app.PAUSE"
const val ACTION_RESUME = "com.agentcommander.app.RESUME"

/** Handles the buttons on the control notification (works from any app). */
class KillReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        Engine.init(ctx)
        when (intent.action) {
            ACTION_STOP -> Engine.kill("Stopped from the notification")
            ACTION_FINISH_LEARN -> Engine.activeId?.let { Engine.finishLearning(it) }
            ACTION_PAUSE -> Engine.pause()
            ACTION_RESUME -> Engine.resume()
        }
    }
}

object Notifier {
    private const val CHANNEL = "agent_control"
    private const val NOTIF_ID = 1001

    private fun pi(ctx: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, code,
            Intent(ctx, KillReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun show(ctx: Context, agentId: String) {
        try {
            val a = Store.get(agentId) ?: return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Agent control", NotificationManager.IMPORTANCE_HIGH)
            )
            val open = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE
            )
            fun action(label: String, act: String, code: Int) =
                Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_launcher), label, pi(ctx, act, code)).build()

            val b = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("${a.name}: ${a.status.name.lowercase().replace('_', ' ')}")
                .setContentText(a.question.ifEmpty { a.log.lastOrNull() ?: "" })
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
            when (a.status) {
                AgentStatus.LEARNING -> b.addAction(action("Finish learning", ACTION_FINISH_LEARN, 2))
                AgentStatus.RUNNING -> b.addAction(action("Pause", ACTION_PAUSE, 3))
                AgentStatus.PAUSED -> b.addAction(action("Resume", ACTION_RESUME, 4))
                else -> {}
            }
            b.addAction(action("STOP", ACTION_STOP, 1))
            nm.notify(NOTIF_ID, b.build())
        } catch (_: Exception) {
            // Notification problems must never break the agent controls themselves.
        }
    }

    fun clear(ctx: Context) {
        try {
            ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        } catch (_: Exception) {
        }
    }
}
