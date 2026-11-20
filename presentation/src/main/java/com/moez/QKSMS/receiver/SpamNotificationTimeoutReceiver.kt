package com.moez.QKSMS.receiver

import com.klinker.android.send_message.PendingIntents

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

/** Android 8+ uses Notification.timeoutAfter; older versions use a process-independent alarm. */
class SpamNotificationTimeoutReceiver : BroadcastReceiver() {
    companion object {
        fun setTimeout(context: Context, threadId: Long, temporary: Boolean) {
            if (threadId >= 0 || Build.VERSION.SDK_INT >= 26) return
            val expiry = PendingIntent.getBroadcast(context, threadId.toInt(),
                Intent(context, SpamNotificationTimeoutReceiver::class.java)
                    .setAction("${context.packageName}.EXPIRE_SPAM_NOTIFICATION")
                    .putExtra("threadId", threadId), PendingIntents.immutable(PendingIntent.FLAG_UPDATE_CURRENT))
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.cancel(expiry)
            if (temporary) alarm.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 10_000L, expiry)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra("threadId", 0)
        if (threadId < 0) (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(threadId.toInt())
    }
}
