package app.shouzhu.focus

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

/**
 * 一个壳：WebView 跑 Web 端的界面，原生只补三件 Web 做不到的事
 *   1. 读取系统“使用情况访问”（今天的手机前台时长）
 *   2. 每晚到点发系统通知
 *   3. 前台计时（锁屏也计时，结束自动打勾）
 *
 * 调试：可以在 SharedPreferences 里设置 webUrl 指向局域网服务器，
 *      这样改 Web 端不用每次重装 APK。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var pendingDone = false

    private val doneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == FocusService.ACTION_DONE) {
                pendingDone = true
                runOnUiThread {
                    web.evaluateJavascript("window.onNativeFocusDone && window.onNativeFocusDone()", null)
                    Toast.makeText(this@MainActivity, "这一块完成了", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        setupWebView()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }

        val filter = IntentFilter(FocusService.ACTION_DONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(doneReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(doneReceiver, filter)
        }

        // 恢复上次设置的提醒
        val sp = getSharedPreferences("shouzhu", Context.MODE_PRIVATE)
        val hour = sp.getInt("reminderHour", 21)
        if (hour > 0) ReminderReceiver.schedule(this, hour)

        val url = sp.getString("webUrl", null)
        if (url != null) web.loadUrl(url) else web.loadUrl("file:///android_asset/www/index.html")

        // 从“对账提醒”通知点进来时，直接落在今天
        if (intent?.getStringExtra("scroll") == "today") {
            web.postDelayed({ web.evaluateJavascript("App.switchView(\"today\")", null) }, 800)
        }
    }

    private fun setupWebView() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            textZoom = 100
        }
        web.webChromeClient = WebChromeClient()
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(this), "NativeBridge")
    }

    override fun onDestroy() {
        try { unregisterReceiver(doneReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    /** 暴露给 JS 的桥。所有方法都必须标 @JavascriptInterface。 */
    class Bridge(activity: MainActivity) {
        private val ref = WeakReference(activity)
        private val ctx: Context? get() = ref.get()

        @JavascriptInterface
        fun readPhoneHours(): Float {
            val c = ctx ?: return -1f
            return UsageStatsReader.foregroundHoursToday(c) ?: -1f
        }

        @JavascriptInterface
        fun readAppUsage(): String {
            val c = ctx ?: return "[]"
            return UsageStatsReader.topAppsToday(c)
        }

        @JavascriptInterface
        fun openUsageAccess() {
            ctx?.let { UsageStatsReader.openSettings(it) }
        }

        @JavascriptInterface
        fun scheduleReminder(hour: Int) {
            val c = ctx ?: return
            c.getSharedPreferences("shouzhu", Context.MODE_PRIVATE).edit().putInt("reminderHour", hour).apply()
            ReminderReceiver.schedule(c, hour)
        }

        @JavascriptInterface
        fun startFocus(minutes: Int) {
            val c = ctx ?: return
            FocusService.start(c, minutes, "守住 · 专注")
        }

        @JavascriptInterface
        fun stopFocus() {
            ctx?.let { FocusService.stop(it) }
        }
    }
}
