package app.shouzhu.focus

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar

/**
 * 读取"今天各 App 的前台使用时长"。
 *
 * 早期版本用 queryUsageStats(INTERVAL_DAILY) 累加 totalTimeInForeground，
 * 那个数字是系统按 bucket 聚合的，跨天边界会把前一天的时间算进来，
 * 所以会出现"中午 12 点已经用了 14 小时"这种离谱值。
 *
 * 现在改成解析 UsageEvents（ACTIVITY_RESUMED / ACTIVITY_PAUSED），
 * 从今天 0 点开始逐条累加，并限制在"从 0 点到现在"的时间窗口内。
 */
object UsageStatsReader {

    fun hasPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /** 今天 0 点 → 现在，按事件流统计每个包的前台毫秒数 */
    private fun todayUsage(context: Context): Map<String, Long>? {
        if (!hasPermission(context)) return null
        return try {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            val events = manager.queryEvents(start, now) ?: return null

            val out = HashMap<String, Long>()
            val open = HashMap<String, Long>()
            val ev = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                val pkg = ev.packageName ?: continue
                when (ev.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        open[pkg] = ev.timeStamp
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val t = open.remove(pkg)
                        if (t != null && ev.timeStamp > t) {
                            out[pkg] = (out[pkg] ?: 0L) + (ev.timeStamp - t)
                        }
                    }
                }
            }
            // 还在前台的，算到现在
            for ((pkg, t) in open) {
                if (now > t) out[pkg] = (out[pkg] ?: 0L) + (now - t)
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    /** 今天 0 点到现在的前台小时数（不超过已经过去的时间） */
    fun foregroundHoursToday(context: Context): Float? {
        val usage = todayUsage(context) ?: return null
        val totalMs = usage.values.sum()
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val elapsed = (now - cal.timeInMillis).coerceAtLeast(1L)
        val capped = totalMs.coerceIn(0L, elapsed)
        return capped.toFloat() / 3_600_000f
    }

    /** 今天使用最多的前几个应用，返回 JSON：[{name, minutes}] */
    fun topAppsToday(context: Context, limit: Int = 6): String {
        val usage = todayUsage(context) ?: return "[]"
        return try {
            val pm = context.packageManager
            val top = usage.entries.sortedByDescending { it.value }.take(limit * 2)
            val arr = org.json.JSONArray()
            var count = 0
            for (e in top) {
                if (e.value < 60_000L) continue
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(e.key, 0)).toString()
                } catch (ex: Exception) {
                    e.key
                }
                val o = org.json.JSONObject()
                o.put("name", label)
                o.put("minutes", (e.value / 60_000L).toInt())
                arr.put(o)
                count++
                if (count >= limit) break
            }
            arr.toString()
        } catch (e: Exception) {
            "[]"
        }
    }

    fun openSettings(context: Context) {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }
}
