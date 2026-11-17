package app.shouzhu.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机后把每日提醒重新排上（AlarmManager 的定时在重启后会丢）。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val sp = context.getSharedPreferences("shouzhu", Context.MODE_PRIVATE)
        val hour = sp.getInt("reminderHour", 21)
        val minute = sp.getInt("reminderMinute", 0)
        if (hour > 0) ReminderReceiver.schedule(context, hour, minute)
    }
}
