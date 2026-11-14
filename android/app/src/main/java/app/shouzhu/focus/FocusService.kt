package app.shouzhu.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * 前台计时服务：让“开始这一块”之后，锁屏 / 切后台也不掉计时。
 * 每 5 秒刷新通知，结束时广播 ACTION_DONE，由 MainActivity 转给 WebView 打勾。
 */
class FocusService : Service() {

    companion object {
        const val ACTION_START = "app.shouzhu.focus.START"
        const val ACTION_STOP = "app.shouzhu.focus.STOP"
        const val ACTION_DONE = "app.shouzhu.focus.DONE"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_TITLE = "title"
        const val CHANNEL_ID = "focus_timer"
        const val NOTIF_ID = 1001

        fun start(context: Context, minutes: Int, title: String) {
            val i = Intent(context, FocusService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MINUTES, minutes)
                putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, FocusService::class.java).apply { action = ACTION_STOP })
        }
    }

    private var endAt = 0L
    private var title = "专注"
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val left = endAt - System.currentTimeMillis()
            if (left <= 0) {
                finish()
                return
            }
            updateNotification()
            handler.postDelayed(this, 5_000)
        }
    }
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 25)
                title = intent.getStringExtra(EXTRA_TITLE) ?: "专注"
                endAt = System.currentTimeMillis() + minutes * 60_000L
                running = true
                startForeground(NOTIF_ID, buildNotification(remainingText()))
                handler.postDelayed(ticker, 5_000)
            }
            ACTION_STOP -> {
                running = false
                handler.removeCallbacks(ticker)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun remainingText(): String {
        val left = ((endAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        return "%02d:%02d".format(left / 60, left % 60)
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(remainingText()))
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "专注计时", NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
        val open = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun finish() {
        running = false
        handler.removeCallbacks(ticker)
        sendBroadcast(Intent(ACTION_DONE).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
}
