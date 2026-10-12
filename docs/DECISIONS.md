# 技术决策与证据

核验日期：2026-10-12。以下链接为官方资料；文档说明 API 形式，不证明两台设备已经支持它。

## P0 构建链候选

- 已观测：Ubuntu 24.04 WSL2、JDK 17.0.20.1、Android SDK Platform 36、Build Tools 36.0.0、Platform Tools 37.0.1。系统没有全局 Gradle。
- 已生成 Gradle 8.13 Wrapper。官方发行包 SHA-256 为 `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`，下载文件校验通过；Wrapper 配置包含同一 `distributionSha256Sum`，全新缓存的 `./gradlew --version` 下载和启动成功。
- 候选应用构建链：AGP 8.13.2、Kotlin Gradle plugin 2.3.0、JDK 17、`compileSdk` 36。AGP 8.13 的兼容表要求 Gradle 8.13 与 JDK 17、最高支持 API 36.1；8.13.2 明确支持 Kotlin 2.3。Kotlin 官方兼容表将 Kotlin 2.3.0 与 Gradle 8.13、AGP 8.13.0 列为完全支持。AGP 与 Kotlin 依赖尚未加入或解析，最终以 Android 工程构建结果确认。
- 资料：[AGP 8.13 兼容与 Kotlin 2.3 支持](https://developer.android.com/build/releases/agp-8-13-0-release-notes)、[Kotlin Gradle plugin 兼容表](https://kotlinlang.org/docs/gradle-configure-project.html)、[Android Kotlin/D8/R8 兼容表](https://developer.android.com/build/kotlin-support)、[Gradle Wrapper 生成与校验](https://docs.gradle.org/current/userguide/gradle_wrapper.html)。

## P0 LSPosed API 待定

- 用户提供的管理界面版本为 `2.1.1 (7790)`；尚无设备端分支、构建来源或 API 能力证据，因此**暂不选择 Modern libxposed 或 Legacy XposedBridge，不创建模块入口**。
- [LSPosed Modern API 官方 Wiki](https://github.com/LSPosed/LSPosed/wiki/Develop-Xposed-Modules-Using-Modern-Xposed-API)说明现代入口为 `META-INF/xposed/java_init.list`、作用域为 `META-INF/xposed/scope.list`，与传统 `assets/xposed_init` 不同；这只是 API 差异，不代表当前设备兼容。
- 待取得一加 11 的 LSPosed 管理器关于框架分支、来源、构建信息及 API 支持的只读证据，再选模块 API。Redmi 的能力须单独验证。

## P0 ADB 路径

- Linux ADB 可执行文件为 `$ANDROID_HOME/platform-tools/adb`；受限沙箱内不能绑定本地 5037 端口。设备操作统一通过现有 Windows ADB `/mnt/c/dev/platform-tools/adb.exe`，避免争用两个 ADB server。
- 提权后的 Windows ADB 已确认一加 11 为 `device`。只读基线：产品/型号 `PHB110`、Android API 36、ABI `arm64-v8a`。
- 设备实测微信读书包名为 `com.tencent.weread`，`versionName=10.2.2`、`versionCode=10167650`、`minSdk=24`、`targetSdk=33`。检查时主进程未运行；APK 位于系统应用数据目录，未拉取或提交。
- `wslpath -w` 可转换构建产物路径，后续安装前仍须获得用户许可。
