# 构建与安装

## 开发环境

已验证工具链为 JDK 17、Android SDK Platform/Build Tools 36、Gradle Wrapper 8.13、AGP 8.13.2 和 Kotlin 2.3.0。设置 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT`，并确保 `$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer` 可执行。模块使用 Modern libxposed API 102，不兼容传统 Xposed API 配置。

首次构建需要访问 Gradle 官方分发站、Google Maven 和 Maven Central：

```bash
./gradlew :core:test :app:assembleDebug :app:lintDebug --no-daemon
```

依赖缓存完成后运行统一离线检查：

```bash
./scripts/check-debug-build.sh
```

脚本执行 28 项 JVM 测试、Debug 构建和 lint，并检查包名、Modern Xposed 入口、作用域、版本与 APK SHA-256。产物位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 设备安装

当前 Debug APK 仅支持微信读书 10.2.2（versionCode 10167650）。先确认设备 Root 与 LSPosed 仍有效，再经设备所有者许可安装：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

从 WSL 调用 Windows `adb.exe` 时先用 `wslpath -w` 转换 APK 路径。安装后在 LSPosed 中启用 WeReadLyrics，作用域只选择微信读书。打开模块应用，授予悬浮窗和通知权限，点击“启动实时字幕”，再完全关闭并重新打开微信读书。无需重启设备。

启动 AI 朗读后，字幕应显示当前发声文字；暂停时字幕变淡，正常停止后最迟约 15 秒清除。通知中的“隐藏”或应用内“停止悬浮字幕”会关闭前台服务，之后朗读不会自行重新打开窗口。

## 安全停用与卸载

优先在 LSPosed 中取消微信读书作用域或禁用模块，再关闭实时字幕。需要卸载时，经设备所有者确认后执行 `adb uninstall io.github.alice060404.wereadlyrics`。不要清除微信读书数据，也不要为模块测试重启、刷机或修改分区。
