package app.shouzhu.focus

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar

/**
 * 读取“今天的前台使用总时长”。
 *
 * 权限：PACKAGE_USAGE_STATS 需要在 设置 → 应用 → 特殊权限 → 使用情况访问 里手动开启。
 * 没开时返回 null，Web 端会自动退回手动录入。
 */
object UsageStatsReader {

    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** 返回今天 0 点到现在的前台小时数；无权限返回 null。 */
    fun foregroundHoursToday(context: Context): Float? {
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
            val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now) ?: return 0f

            // 同一个包可能出现在多个 bucket 里，取最大值而不是累加，避免重复计数
            val perApp = HashMap<String, Long>()
            for (s in stats) {
                if (s.lastTimeUsed < start) continue
                val v = s.totalTimeInForeground.coerceAtLeast(0L)
                perApp[s.packageName] = maxOf(perApp[s.packageName] ?: 0L, v)
            }
            (perApp.values.sum().toFloat() / 3_600_000f)
        } catch (e: Exception) {
            null
        }
    }


    /** 今天使用最多的前几个应用，返回 JSON 字符串：[{name, minutes}] */
    fun topAppsToday(context: Context, limit: Int = 6): String {
        if (!hasPermission(context)) return "[]"
        return try {
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val pm = context.packageManager
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now) ?: return "[]"
            val perApp = HashMap<String, Long>()
            for (s in stats) {
                if (s.lastTimeUsed < start) continue
                val v = s.totalTimeInForeground.coerceAtLeast(0L)
                perApp[s.packageName] = maxOf(perApp[s.packageName] ?: 0L, v)
            }
            val top = perApp.entries.sortedByDescending { it.value }.take(limit)
            val arr = org.json.JSONArray()
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
            }
            arr.toString()
        } catch (e: Exception) {
            "[]"
        }
    }
    /** 打开系统“使用情况访问”授权页。 */
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
