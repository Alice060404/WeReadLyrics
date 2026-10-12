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

- **状态：** 已决定，等待动态验证
- **日期：** 2026-10-12
- **决定：** P3 第一轮只读探测 `TTSHighLightBroadcaster.startHighLight/pauseHighLight/stopHighLight`。不从 Android `TextToSpeech` 开始，也不先 Hook 底层 `AudioTrack`。
- **依据：** 微信读书 10.2.2 的原始 DEX 确认高亮方法接收 `TTSBag` 与字符范围；静态调用链把它连接到在线/离线 WXTTS 播放进度。系统 `TextToSpeech` 的直接调用属于局部发音流程。
- **边界：** 当前只证明类、签名和静态调用关系存在。AI 朗读实际调用、线程、频率、预取影响和时序准确度必须在一加 11 上验证。
