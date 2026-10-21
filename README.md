# WeReadLyrics

## 项目介绍

WeReadLyrics 是一个 Android LSPosed 模块，在微信读书 AI 朗读时，通过悬浮字幕显示当前读到的句子。字幕可跨应用显示、拖动位置，长句在两行窗口内随朗读进度滚动。

应用提供环境检查、实时字幕、演示字幕和个性化设置，支持深浅色主题、Material 3 与液态玻璃底部导航。液态玻璃可调整高度、模糊、不透明度、折射、色散和动效弹性，设置自动保存。

玻璃效果基于 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) 适配，相关授权与来源说明位于 `app/src/main/assets/licenses/`。

## 使用条件

- Android 8.0（API 26）或更高版本。
- 已安装并正常运行支持 Modern libxposed API 102 的 LSPosed / Xposed 框架。
- 微信读书 **10.2.2**（versionCode **10167650**）。其他版本会停用业务 Hook。
- 在框架中启用本模块，将作用域设为微信读书，并授予模块应用悬浮窗权限。

模块应用无需单独获得 Root 授权；主页的 Root 检测是可选功能。液态玻璃的背景模糊需要 Android 12 及以上，折射与色散需要 Android 13 及以上，旧系统会禁用相应设置。

## 构建方法

需要 JDK 17、Android SDK Platform 36 和 Build Tools 36.0.0。配置 Android SDK 路径（`local.properties` 的 `sdk.dir` 或 `ANDROID_HOME`），使用项目自带的 Gradle 8.13 Wrapper 构建：

```bash
./gradlew :app:assembleDebug
```

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。Windows 下可使用 `gradlew.bat` 执行相同任务。

构建正式签名版本时，将 `keystore.properties.example` 复制为 `keystore.properties`，填写密钥库路径、密码和别名，然后执行：

```bash
./gradlew :app:assembleRelease
```

Release APK 输出到 `app/build/outputs/apk/release/app-release.apk`。签名配置和密钥仅保存在本地；也可通过 `KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD` 环境变量提供，环境变量优先。

## 使用方法

1. 安装模块 APK，在 LSPosed 中启用模块，将作用域限定为微信读书。
2. 彻底退出并重新打开微信读书，使模块生效；升级模块后也需执行此操作。
3. 打开模块应用，在“主页”检查框架连接、作用域及悬浮窗权限。
4. 在“功能”页启动实时字幕，再进入微信读书开启 AI 朗读。也可启动演示字幕，体验文字滚动和拖动位置。
5. 在“设置 → 个性化设置”中选择导航栏样式、调整液态玻璃参数；调低“动效弹性”可减弱膨胀与回弹。

停止使用时，在“功能”页停止悬浮字幕；如需停用模块，在 LSPosed 中取消作用域或禁用模块。Debug 与 Release 使用不同签名时不能互相覆盖安装，切换签名需先卸载旧版本，再重新配置模块权限和作用域。
