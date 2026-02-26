# Biscuit 骑行码表 — 全面代码审查报告

> 审查范围：全部源码、资源文件、构建配置、清单文件  
> 目标：达到应用商店上架标准

---

## 一、构建与配置问题

### 1.1 versionName 使用动态时间戳
- **文件**: `app/build.gradle`
- **问题**: `versionName "1.2." + (new Date()).format('yyyymmddHHMMSS')` 中格式字符串有误，`mm` 是分钟不是月份，`MM` 是月份不是分钟，`SS` 不是标准格式。
- **影响**: 每次构建的版本号不可预测，应用商店要求版本号有意义且递增。
- **建议**: 改为 `"1.2." + (new Date()).format('yyyyMMdd')` 或使用 git commit count 作为 versionCode，手动管理 versionName。

### 1.2 Release 构建未启用混淆
- **文件**: `app/build.gradle`
- **问题**: `minifyEnabled false`，release 包未做代码混淆和压缩。
- **影响**: APK 体积偏大，代码容易被反编译。
- **建议**: 上架前启用 `minifyEnabled true` 并配置好 ProGuard 规则。

### 1.3 kapt 已过时
- **问题**: 项目使用 `kapt` 处理 Room 注解，Kotlin 2.0+ 推荐使用 KSP（Kotlin Symbol Processing）。
- **建议**: 迁移到 `com.google.devtools.ksp` 插件，编译速度会有明显提升。

### 1.4 exportSchema = false
- **文件**: `BiscuitDatabase.kt`
- **问题**: Room 数据库 `exportSchema = false`，无法追踪数据库 schema 变更历史。
- **建议**: 设为 `true` 并配置 `room.schemaLocation`，方便后续数据库迁移验证。

---

## 二、安全与隐私问题（上架必修）

### 2.1 天地图 API Token 硬编码在 strings.xml
- **文件**: `res/values/strings.xml`
- **问题**: `<string name="tianditu_token">7fc1e336835f83459b7db4698b9810e1</string>` 直接暴露在代码中。
- **影响**: 任何人反编译 APK 即可获取 token，可能被滥用导致配额耗尽或被封禁。
- **建议**: 将 token 移到 `local.properties` 或通过 BuildConfig 注入，不要提交到版本控制。

### 2.2 Mock GPS 服务器监听所有网络接口
- **文件**: `Sensor.kt` → `PositionSensor.startMockServer()`
- **问题**: `ServerSocket(7777)` 绑定到 `0.0.0.0`，任何同网络设备都可以连接并注入虚假 GPS 数据。
- **影响**: 安全风险，虽然是调试功能但 release 包中仍然存在。
- **建议**: 
  - 绑定到 `127.0.0.1`（仅本机）
  - Release 构建中完全移除 mock GPS 功能（通过 BuildConfig 开关）
  - 或至少在 `ServerSocket` 构造时指定 `InetAddress.getLoopbackAddress()`

### 2.3 WRITE_EXTERNAL_STORAGE 权限
- **文件**: `AndroidManifest.xml`
- **问题**: 声明了 `WRITE_EXTERNAL_STORAGE`，但 targetSdk 35 上此权限已无效（Android 10+ 使用 Scoped Storage）。
- **影响**: Google Play 审核可能因不必要的权限被拒。
- **建议**: 移除此权限，GPX 导出已经使用 MediaStore API，不需要此权限。

### 2.4 WakeLock 未设置超时
- **文件**: `BiscuitService.kt`
- **问题**: `wakeLock.acquire()` 没有超时参数，如果 service 异常未释放，会一直持有 WakeLock 导致电量耗尽。
- **建议**: 使用 `acquire(timeout)` 设置合理超时（如 12 小时），或确保所有异常路径都释放 WakeLock。

---

## 三、稳定性与崩溃风险

### 3.1 Session.duration() 空指针风险
- **文件**: `Session.kt`
- **问题**: `fun duration(): Duration = Duration.between(start, end)` — 当 `end` 为 null 时会抛 NPE。
- **影响**: HistoryFragment 中 `session.duration()` 在 session 未关闭时会崩溃。
- **建议**: 改为 `fun duration(): Duration = if (end != null) Duration.between(start, end) else Duration.ZERO`

### 3.2 HistoryFragment 中 session.end!! 强制解包
- **文件**: `HistoryFragment.kt` → `SessionAdapter.ViewHolder.bind()`
- **问题**: `db.trackpointDao().getTrackpointsForSession(session.start, session.end!!)` — 虽然查询的是 closed sessions，但如果数据库状态不一致，`end` 为 null 时会崩溃。
- **建议**: 使用安全调用 `session.end ?: return`。

### 3.3 数据库操作在主线程
- **文件**: `BiscuitService.kt` → `updaterThread`
- **问题**: `db.sessionDao().start(Instant.now())` 在 updaterThread 中调用，这个线程不是主线程所以 OK。但 `HistoryFragment` 中的 `Thread { ... }.start()` 手动创建线程来做数据库操作，没有错误处理的统一机制。
- **建议**: 使用 Room 的 `suspend` 函数 + Coroutines，或至少统一使用 `Executors`。

### 3.4 updaterThread 中的 sleep 可能导致 ANR
- **文件**: `BiscuitService.kt`
- **问题**: `updaterThread` 是一个无限循环的普通 Thread，使用 `sleep()` 控制节奏。如果 `safeUpdateInterval` 被设为 0（虽然有保护），或者循环内抛出未捕获异常，线程会静默死亡。
- **建议**: 添加 try-catch 包裹整个循环体，记录异常日志。

### 3.5 BikeActivity.onBackPressed() 已废弃
- **文件**: `BikeActivity.kt`
- **问题**: `onBackPressed()` 在 Android 13+ 已废弃，应使用 `OnBackPressedCallback`。
- **建议**: 迁移到 `onBackPressedDispatcher.addCallback()`。

---

## 四、功能逻辑问题

### 4.1 距离单位不一致
- **问题**: `PositionSensor` 中 `distance` 以米为单位累加（`distanceTo` 返回米），`SpeedSensor` 中 `distance` 以米为单位（`wheelCircumference = 2.105` 米）。但 `Trackpoint.distance` 是 Float 类型，长距离骑行（>100km）时精度会丢失。
- **建议**: 将 `Trackpoint.distance` 改为 Double 类型，或至少在 `RideStatsCalculator` 中注意精度问题。并且使用中要自动切换单位,如超过1000m后从米到km.

### 4.2 卡路里计算过于简化
- **文件**: `CalorieCalculator.kt`
- **问题**: 公式 `ridingTimeHours × avgSpeedKmh × weightKg × 0.4` 过于粗糙，且体重硬编码为 65kg。
- **建议**: 在设置中添加体重输入选项，使用更科学的 MET（代谢当量）公式。

### 4.3 Session 名称存储在 SharedPreferences
- **文件**: `HomeFragment.kt`
- **问题**: `prefs.edit().putString("session_name_${recordingStartTime}", sessionName).apply()` — session 名称存在 SharedPreferences 而非数据库。
- **影响**: 清除应用数据后名称丢失，且 SharedPreferences 不适合存储大量键值对。
- **建议**: 在 Session 实体中添加 `name` 字段，通过数据库迁移实现。

### 4.4 TrackPicker 功能未完成
- **文件**: `TrackPicker.kt` / `TrackFragment.kt`
- **问题**: `popupTrackPicker` 中点击 track 的 `setOnClickListener` 只有 `Log.d("hey", "clicked on ${s.start}")`，没有实际功能。
- **建议**: 暂时隐藏此功能入口。

### 4.5 录制状态恢复逻辑有竞态风险
- **文件**: `HomeFragment.kt` → `restoreRecordingState()`
- **问题**: 使用 30 秒超时判断 service 是否存活（`staleMs > 30_000`），但如果手机休眠后恢复，service 可能还在运行只是 tick 延迟了。
- **建议**: 通过 bindService 检查 service 是否真正存活，而非依赖时间戳。

---

## 五、性能问题

### 5.1 HistoryFragment 在 RecyclerView 中启动线程加载数据
- **文件**: `HistoryFragment.kt` → `SessionAdapter.ViewHolder.bind()`
- **问题**: 每个列表项的 `bind()` 方法都启动一个新 `Thread` 来查询数据库。快速滚动时会创建大量线程。
- **建议**: 使用协程 + `viewModelScope`，或使用线程池（`Executors.newFixedThreadPool`）。

### 5.2 TrackViewModel 中 track 列表无限增长
- **文件**: `TrackViewModel.kt`
- **问题**: `move()` 方法不断向 `ArrayList<GeoPoint>` 添加点，长时间骑行会导致内存持续增长。
- **建议**: 设置上限（如保留最近 10000 个点），或使用更高效的数据结构。

### 5.3 地图 Polyline 每次更新都重建
- **文件**: `TrackFragment.kt`
- **问题**: `model.track.observe` 中每次都创建新的 `Polyline()` 并设置所有点，随着点数增加性能会下降。
- **建议**: 复用 Polyline 对象，只添加新的点。

---

## 六、UI/UX 问题

### 6.1 底部导航栏高度异常
- **文件**: `res/values/dimens.xml`
- **问题**: `design_bottom_navigation_height` 被覆盖为 90dp，但 `activity_bike.xml` 中 `nav_view` 高度设为 48dp。两者矛盾。
- **建议**: 统一高度，Material Design 推荐 56dp-80dp。

### 6.2 屏幕方向锁定为竖屏
- **文件**: `AndroidManifest.xml`
- **问题**: `android:screenOrientation="portrait"` 锁定竖屏。
- **影响**: 部分用户可能希望横屏使用（如手机横向安装在车把上）。
- **建议**: 至少提供设置选项让用户选择屏幕方向。

### 6.3 硬编码中文字符串
- **问题**: 大量中文字符串直接写在代码中而非 strings.xml，如：
  - `BiscuitService.kt`: "正在记录骑行"、"已自动暂停记录"
  - `HomeFragment.kt`: "输入骑行名称（可选）"、"停止记录"、"保存"、"放弃"
  - `TrackFragment.kt`: "正在获取GPS定位，请稍候"、"自动跟踪已开启"
  - `HistoryDetailFragment.kt`: "骑行详情"、"收起地图"、"展开地图"
- **影响**: 无法国际化，Google Play 全球发布时只有中文。
- **建议**: 所有用户可见字符串移到 `strings.xml`，后续可添加英文翻译。

### 6.4 缺少空状态和加载状态
- **问题**: 
  - 传感器页面初始状态没有任何文字提示（等待第一次广播）
  - 历史详情页加载数据时没有 loading 指示器
- **建议**: 添加 ProgressBar 或 skeleton loading。

### 6.5 停止录制对话框缺少"取消"选项
- **文件**: `HomeFragment.kt` → `showStopDialog()`
- **问题**: 对话框只有"保存"和"放弃"，`setCancelable(false)` 禁止了返回键取消。用户如果误触长按完成，无法取消操作。
- **建议**: 添加"取消"按钮或设置 `setCancelable(true)`。

---

## 七、Android 兼容性问题

### 7.1 Android 14+ 前台服务限制
- **问题**: Android 14 (API 34) 对前台服务类型有更严格的限制。当前声明了 `foregroundServiceType="location"`，这是正确的，但需要确保在所有启动路径上都正确调用 `startForeground()`。
- **状态**: 代码中已处理，但 `onStartCommand` 中 `stop_service` 分支没有先调用 `startForeground()`，在 Android 12+ 上如果 service 是通过 `startForegroundService()` 启动的，5 秒内必须调用 `startForeground()`。
- **建议**: 在 `stop_service` 分支也先调用 `startForeground()` 再 `stopForeground()`。

### 7.2 Deprecated API 使用
- `getParcelableExtra(String)` — 已在 API 33 废弃（代码中已有兼容处理 ✓）
- `onBackPressed()` — 已在 API 33 废弃（需迁移）
- `Vibrator` — 应使用 `VibratorManager`（API 31+）

### 7.3 缺少 Android 16 (API 36) 适配
- **问题**: targetSdk 35，但 Android 16 即将发布，需要关注：
  - 新的权限模型变化
  - 前台服务的进一步限制
  - Edge-to-edge 强制要求

---

## 八、数据完整性问题

### 8.1 Trackpoint 删除功能缺失
- **问题**: `TrackpointDao` 没有删除方法。`HomeFragment.discardRecording()` 删除了 Session 但没有删除对应的 Trackpoints。
- **影响**: 数据库中会积累孤立的 trackpoint 数据，浪费存储空间。
- **建议**: 添加 `@Query("DELETE FROM trackpoint WHERE ts >= :startTime AND ts < :endTime")` 方法，并在 discard 时调用。

### 8.2 Session 关闭时机问题
- **文件**: `BiscuitService.kt` → `updaterThread`
- **问题**: `db.sessionDao().start(Instant.now())` 在 service 启动时调用，会先关闭所有未关闭的 session。但如果 app 被强杀，session 永远不会被关闭。
- **影响**: 下次启动时会关闭上次的 session，但关闭时间是新 session 的开始时间，不是实际结束时间。
- **建议**: 在 `onDestroy` 或 `shutdown` 中显式关闭当前 session。

---

## 九、代码质量问题

### 9.1 BiscuitService 过于臃肿
- **问题**: `BiscuitService.kt` 承担了太多职责：传感器管理、自动暂停逻辑、距离计算、通知管理、数据持久化、广播发送。
- **建议**: 虽然已经提取了 `AutoPauseLogic` 和 `PauseAwareDistanceTracker`，但 service 中仍然有重复的逻辑。建议进一步重构，让 service 只做协调工作。

### 9.2 SharedPreferences 滥用
- **问题**: 大量状态通过 SharedPreferences 在 Service 和 Fragment 之间传递（`is_recording`、`is_manual_paused`、`accumulated_duration_ms` 等）。
- **影响**: 难以维护，容易出现状态不一致。
- **建议**: 使用 `LiveData` + `ViewModel` 或 `StateFlow` 进行状态管理，Service 通过 Binder 直接暴露状态。

### 9.3 日志中包含 "HEY" 等调试信息
- **文件**: `SessionDao.kt`
- **问题**: `Log.d("HEY", "inserting $session")` — 不专业的日志标签。
- **建议**: 统一使用类名作为 TAG。

### 9.4 未使用的代码
- `Trackpoint.wheelRevolutions` 标注为 "no longer used" 但仍在数据库 schema 中
- `mobile_navigation.xml` 定义了完整的导航图但实际使用的是手动 Fragment 管理
- `TrackViewModel.get()` 方法没有返回值，无意义

---

## 十、上架前必须修复的问题清单

| 优先级 | 问题 | 类别 |
|--------|------|------|
| 🔴 P0 | 天地图 Token 硬编码暴露 | 安全 |
| 🔴 P0 | Session.duration() NPE 风险 | 崩溃 |
| 🔴 P0 | HistoryFragment session.end!! 强制解包 | 崩溃 |
| 🔴 P0 | Release 包包含 Mock GPS 服务器 | 安全 |
| 🔴 P0 | WRITE_EXTERNAL_STORAGE 多余权限 | 审核 |
| 🟡 P1 | stop_service 分支未调用 startForeground | 兼容性 |
| 🟡 P1 | versionName 格式错误 | 构建 |
| 🟡 P1 | 硬编码中文字符串（无法国际化） | UX |
| 🟡 P1 | WakeLock 无超时 | 电量 |
| 🟡 P1 | Trackpoint 删除缺失导致数据泄漏 | 数据 |
| 🟡 P1 | Release 未启用混淆 | 安全 |
| 🟢 P2 | kapt → KSP 迁移 | 性能 |
| 🟢 P2 | RecyclerView 线程管理 | 性能 |
| 🟢 P2 | onBackPressed 废弃 API | 兼容性 |
| 🟢 P2 | 停止对话框缺少取消选项 | UX |
| 🟢 P2 | Session 名称应存数据库 | 架构 |

---

## 总结

Biscuit 作为一个骑行码表应用，核心功能（GPS 轨迹记录、ANT+ 传感器、自动暂停、历史记录、GPX 导出）已经比较完整。代码结构清晰，有提取独立逻辑类的意识（AutoPauseLogic、PauseAwareDistanceTracker、RideStatsCalculator 等），也有属性测试覆盖。

要达到应用商店上架标准，最紧迫的是修复 P0 级别的安全和崩溃问题。P1 级别的问题建议在第一个版本发布前解决。P2 级别可以作为后续迭代优化。
