package app.shouzhu.focus

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

/** 每晚对账提醒：AlarmManager 定时，触发时发一条本地通知。 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "对账提醒", NotificationManager.IMPORTANCE_DEFAULT)
            nm.createNotificationChannel(ch)
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("scroll", "today")
        }
        val pi = PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_IMMUTABLE)
        val sp = context.getSharedPreferences("shouzhu", Context.MODE_PRIVATE)
        val title = sp.getString("reminderTitle", null) ?: "今天过得怎么样"
        val body = sp.getString("reminderText", null) ?: "花 10 秒记一下手机时长，顺便看看今天的块。"
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        nm.notify(NOTIF_ID, notif)
    }

    companion object {
        const val CHANNEL_ID = "daily_checkin"
        const val NOTIF_ID = 2001
        private const val REQ = 2002

        /** hour = 0 表示关闭 */
        fun schedule(context: Context, hour: Int, minute: Int = 0) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pendingIntent(context)
            am.cancel(pi)
            if (hour <= 0) return

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }

            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                am.canScheduleExactAlarms() else true
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        }

        private fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context, REQ,
                Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
