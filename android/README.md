# Android 版（守住 · Focus Guard）

这不是另一个 App，是**同一个界面的原生外壳**。Web 端负责排程与界面，原生只补三件 Web 做不到的事：

| 能力 | Web 端 | 安卓版 |
|---|---|---|
| 读取手机使用时长 | 手动抄数字 | `UsageStatsManager` 自动读今天的前台总时长 |
| 每晚对账提醒 | 无 | `AlarmManager` + 本地通知 |
| 90 分钟计时 | 只能跳个 toast | 前台服务，锁屏也计时，结束自动打勾 |
| 数据存储 | localStorage | 同一个 localStorage（WebView） |

---

## 怎么编译

需要 **Android Studio**（或只装 SDK + JDK 17）。本项目不依赖任何私有服务，全部离线。

```bash
cd focus-guard/android
# 用 Android Studio 打开这个目录，等 Gradle 同步完成
# 或用命令行：
./gradlew assembleDebug
```

APK 会出现在 `app/build/outputs/apk/debug/app-debug.apk`，传到手机安装即可。

> 首次同步会下载 Gradle 8.7 与 Android Gradle Plugin 8.5。国内网络慢的话，在 `gradle.properties` 里加镜像或开代理。

## 装完后要做的两件事

1. **授予“使用情况访问”**
   设置 → 应用 → 守住 → 权限 → 使用情况访问（不同 ROM 路径略有差异）
   或直接点 App 里「设置 → 自动读取 → 开启自动读取」跳到系统页。
   **没授予也不会崩**：App 会自动退回手动录入。

2. **允许通知**
   首次打开会请求通知权限。不给的话，每晚提醒不响，其他功能照常。

> vivo / iQOO 用户额外注意：系统会杀后台。需要在「i管家 → 应用管理 → 守住 → 自启动/后台高耗电」里放行，否则锁屏计时可能被中断。

## 怎么调试 Web 端（不重装 APK）

App 支持把 WebView 指向局域网服务器：

```bash
# 电脑上
cd focus-guard && node serve.js 8787

# 让 App 读这个地址（二选一）
adb shell "run-as app.shouzhu.focus sh -c 'echo \"<map><string name=\\\"webUrl\\\">http://192.168.1.10:8787/</string></map>\" > shared_prefs/shouzhu.xml'"
```

或者临时改 `MainActivity.kt` 里的 `webUrl`。清掉这个键就回到内置版本。

## 目录

```
android/
├── settings.gradle.kts
├── build.gradle.kts
└── app/
    ├── build.gradle.kts          # 含 syncWebAssets：把 ../ 的 Web 资源拷进 assets
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/app/shouzhu/focus/
        │   ├── MainActivity.kt       # WebView + JS 桥
        │   ├── UsageStatsReader.kt   # 今天的前台时长
        │   ├── FocusService.kt       # 前台计时
        │   ├── ReminderReceiver.kt   # 每晚提醒
        │   └── BootReceiver.kt       # 重启后恢复提醒
        └── res/…
```

## 已知边界

- **无法读取“昨天”的精确数据做补录**：`UsageStats` 只给聚合值，历史数据的粒度取决于系统（部分 ROM 只保留日级）。
- **小米/华为的“应用使用时间”口径和 UsageStats 不完全一致**，会有几十分钟误差。它是参考值，不是审计值。
- **iOS 做不到**：Apple 不开放使用时长给第三方 App（Screen Time API 只给家长控制类）。iOS 上只能用手动版，或引导用户看系统“屏幕使用时间”。
