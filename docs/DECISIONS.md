# 技术决策与证据

核验日期：2026-10-12。以下链接为官方资料；文档说明 API 形式，不证明两台设备已经支持它。

## P0 构建链候选

- 已观测：Ubuntu 24.04 WSL2、JDK 17.0.20.1、Android SDK Platform 36、Build Tools 36.0.0、Platform Tools 37.0.1。系统没有全局 Gradle。
- 已生成 Gradle 8.13 Wrapper。官方发行包 SHA-256 为 `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`，下载文件校验通过；Wrapper 配置包含同一 `distributionSha256Sum`，全新缓存的 `./gradlew --version` 下载和启动成功。
- 候选应用构建链：AGP 8.13.2、Kotlin Gradle plugin 2.3.0、JDK 17、`compileSdk` 36。AGP 8.13 的兼容表要求 Gradle 8.13 与 JDK 17、最高支持 API 36.1；8.13.2 明确支持 Kotlin 2.3。Kotlin 官方兼容表将 Kotlin 2.3.0 与 Gradle 8.13、AGP 8.13.0 列为完全支持。AGP 与 Kotlin 依赖尚未加入或解析，最终以 Android 工程构建结果确认。
- 资料：[AGP 8.13 兼容与 Kotlin 2.3 支持](https://developer.android.com/build/releases/agp-8-13-0-release-notes)、[Kotlin Gradle plugin 兼容表](https://kotlinlang.org/docs/gradle-configure-project.html)、[Android Kotlin/D8/R8 兼容表](https://developer.android.com/build/kotlin-support)、[Gradle Wrapper 生成与校验](https://docs.gradle.org/current/userguide/gradle_wrapper.html)。

## P0 LSPosed API

- 用户提供的一加 11 截图确认：LSPosed `2.1.1 (7790)` 已激活，框架明确报告 **API 102**；管理器寄生包名为 `com.android.shell`。Root 环境为 SukiSU `4.2.0 (40900-2)`，使用 Zygisk Next。截图仅作本地证据，不提交 Git。
- [LSPosed Modern API 官方 Wiki](https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API)说明现代入口为 `META-INF/xposed/java_init.list`、作用域为 `META-INF/xposed/scope.list`，与传统 `assets/xposed_init` 不同；这只是 API 差异，不代表当前设备兼容。
- Maven Central 元数据确认 `io.github.libxposed:api:102.0.0` 是发布的 API 102 AAR；下载文件 SHA-256 `423484a6e1807e7a423c4b88fcd8176d104318259d91791877fed88fe91479d0` 校验通过。接口检查确认 `XposedModule` 使用无参构造，并通过 `onPackageLoaded(PackageLoadedParam)` 接收包加载事件。
- **决策：P1 使用 Modern libxposed API 102，依赖采用 `compileOnly("io.github.libxposed:api:102.0.0")`；不混入 Legacy XposedBridge。** 模块使用 `META-INF/xposed/` 元数据，并将静态作用域限制为实测包名 `com.tencent.weread`。
- 官方 `libxposed/api` 与 `libxposed/example` GitHub 仓库在核验时不可直接克隆；依赖真实性以 Maven Central POM、AAR 校验值、AAR 接口和 LSPosed 官方 Wiki 交叉核对。Redmi 的 API 能力仍须在 P7 单独验证。

## P0 ADB 路径

- Linux ADB 可执行文件为 `$ANDROID_HOME/platform-tools/adb`；受限沙箱内不能绑定本地 5037 端口。设备操作统一通过现有 Windows ADB `/mnt/c/dev/platform-tools/adb.exe`，避免争用两个 ADB server。
- 提权后的 Windows ADB 已确认一加 11 为 `device`。只读基线：产品/型号 `PHB110`、Android API 36、ABI `arm64-v8a`。
- 设备实测微信读书包名为 `com.tencent.weread`，`versionName=10.2.2`、`versionCode=10167650`、`minSdk=24`、`targetSdk=33`。检查时主进程未运行；APK 位于系统应用数据目录，未拉取或提交。
- `wslpath -w` 可转换构建产物路径，后续安装前仍须获得用户许可。

## P1 最小模块边界

- 固定应用 ID/命名空间为 `io.github.alice060404.wereadlyrics`，`minSdk=24`、`targetSdk=36`、`compileSdk=36`。
- 模块入口只处理静态作用域中的 `com.tencent.weread`，记录包名、进程名和首次加载标志；不 Hook 任何方法，不读取文本，不改变宿主返回值。
- 模块数据禁止云备份和设备迁移。API 102 依赖为 `compileOnly`；APK DEX 检查确认未打包 `io.github.libxposed` 或 Legacy XposedBridge 实现类。

# ADR-003：P3 优先探测现有 TTS 高亮广播链

- **状态：** 已验证
- **日期：** 2026-10-12
- **决定：** P3 第一轮只读探测 `TTSHighLightBroadcaster.startHighLight/pauseHighLight/stopHighLight`。不从 Android `TextToSpeech` 开始，也不先 Hook 底层 `AudioTrack`。
- **依据：** 微信读书 10.2.2 的原始 DEX 确认高亮方法接收 `TTSBag` 与字符范围；静态调用链把它连接到在线/离线 WXTTS 播放进度。系统 `TextToSpeech` 的直接调用属于局部发音流程。
- **结果：** 一加 11 综合时间轴与人工观察确认 start 高亮随实际发声推进，跳转和切章后能重置到新文本范围。人工观察不能给出毫秒级误差。

# ADR-004：P4 以高亮事件为文本真值并归一化控制组合

- **状态：** 已验证
- **日期：** 2026-10-12
- **决定：** `TTSHighLightBroadcaster.startHighLight` 作为当前文本与范围事件源；pause/stop 高亮及 `TTSBookPlayer` 控制方法作为状态提示，不把单个 Java 方法名直接映射成产品状态。
- **依据：** 真机中暂停可表现为 control pause，继续可表现为相邻 control stop+start 或 start+resume，最终停止也可能表现为 pause。所有场景中，后续高亮是否恢复及文本 token/范围是否重置能可靠反映播放会话变化。
- **实现约束：** P4 使用单调序号、有界状态和显式会话重置；用 JVM 测试覆盖预取、乱序、暂停、继续、停止、跳转与切章。不得依据未出现的 callback pause/resume 推断状态。stop+start 合并窗口和暂停清理超时由接入层显式提供，在真机验证前不固化为产品常量。
- **验证结果：** 一加 11 的 0.5.0 标准事件从序号 1 单调推进到 30；跳转、切章和停止均清除了旧项，最终进入 `IDLE` 且正文长度归零。1 秒合并窗口和 15 秒清理超时仅作为当前诊断值保留。

# ADR-005：P5 先由可见界面启动独立悬浮服务

- **状态：** 模拟模式已验证
- **日期：** 2026-10-12
- **决定：** 先实现不接收 Hook 数据的本地模拟悬浮窗。用户从模块 Activity 授予 `SYSTEM_ALERT_WINDOW` 后主动启动 `specialUse` 前台服务；服务暂不导出。
- **依据：** Android 14 起目标应用必须声明前台服务类型及对应权限；Android 15 起持有悬浮权限也不能在没有可见覆盖层时从后台任意启动前台服务。由可见 Activity 启动可满足系统约束，并将 UI 验证与后续 IPC 隔离。
- **界面约束：** 使用 `TYPE_APPLICATION_OVERLAY`，最多两行、末尾省略、可拖动并保存位置。模拟文字全部来自模块资源，不读取书籍内容。
- **资料：** [前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types)、[后台启动限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)、[`TYPE_APPLICATION_OVERLAY`](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)。
- **验证结果：** 一加 11 上授权、周期更新、两行省略、屏幕内拖动、跨桌面/浏览器/微信读书显示、通知隐藏和位置恢复均由用户确认正常。

# ADR-006：P5 使用逐次鉴权的单向 Binder IPC

- **状态：** 已验证
- **日期：** 2026-10-12
- **决定：** 私有 `OverlayService` 只接受模块自身的显式启动；单独导出的 `PlaybackBridgeService` 提供单向 AIDL。每次 Binder 方法调用都用调用 UID 反查包名，并校验微信读书签名证书 SHA-256。
- **依据：** 微信读书 10.2.2 的设备 APK 实测证书摘要为 `e4dd2f049941989ffafa45aef52eda9701372c27bc1708e89c217935140bfa1c`。签名权限不可用，因为微信读书与模块并非同一签名。仅检查包名不足以识别侧载仿冒包。
- **生命周期：** 用户必须先从可见 Activity 启动实时模式；私有悬浮服务随后保持 Bridge 存活。Hook 客户端不用 `BIND_AUTO_CREATE`，因此不能在后台自行创建服务或窗口。
- **数据边界：** AIDL 只传当前标准状态和最多 4096 字符的当前项；不传整章，不写磁盘，不发广播。单向调用避免阻塞微信读书线程，服务端只保留最新待刷新项并再次检查会话序号。
- **资料：** [Android IPC 安全建议](https://developer.android.com/privacy-and-security/security-tips)、[Binder 调用 UID](https://developer.android.com/reference/android/os/Binder#getCallingUid())、[PendingIntent 发送方风险与签名核验](https://developer.android.com/privacy-and-security/risks/sender-of-pending-intents)。
- **验证结果：** 0.7.1 在一加 11 上完成真实字幕和完整生命周期验证；shell 无法启动未导出的 Overlay Service。0.7.2 的独立探针以不同 UID 成功绑定 Bridge，但调用被明确拒绝且没有进入更新总线；随后微信读书正向回归仍被接受并正常更新字幕。

# ADR-007：未知微信读书版本默认禁用业务 Hook

- **状态：** 已验证
- **日期：** 2026-10-14
- **决定：** 仅允许 `com.tencent.weread` versionCode 10167650（10.2.2）安装内部 TTS Hook。未知版本、版本查询失败或应用入口变化时只记录脱敏原因，不尝试业务 Hook。
- **实现：** Modern API 的 `PackageLoadedParam` 提供 `ApplicationInfo` 但没有版本码。模块先 Hook 已由 APK Manifest 验证的 `WRApplication.onCreate()`；原方法完成后从实例取得 Context，通过 PackageManager 读取 versionCode，再决定是否安装业务 Hook。该入口是公开 `Application.onCreate()` 覆盖，不依赖隐藏 `Application.attach()`。
- **验证结果：** 三项纯 Kotlin 测试覆盖正确版本、未知版本和错误包名。0.9.0 在 Redmi 上按顺序记录引导安装、10.2.2 版本接受和十个业务 Hook 安装；真实字幕、暂停/恢复和停止清屏回归通过。
