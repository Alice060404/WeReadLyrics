# WeReadLyrics

Android LSPosed 模块：在微信读书 AI 朗读时，向其他应用上方的悬浮窗显示当前实际读到的句子。最终 0.9.0 已在一加 11 通过快速回归，并在 Redmi K80 至尊版当前临时 Root 会话中验证可用。

当前 0.9.0 仅启用微信读书 10.2.2（versionCode 10167650）的内部 Hook。未知版本会安全停用业务 Hook，等待重新分析和验证。

当前入口使用设备已确认的 Modern libxposed API 102。同步核心通过 28 项 JVM 测试；悬浮窗、逐次鉴权 IPC、宿主退出清屏及版本门禁均有真机证据。正文只在进程内传递，不写入磁盘或诊断日志。

## 本地构建

要求 JDK 17 和 Android SDK Platform 36。项目 Wrapper 会校验 Gradle 8.13 发行包：

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :core:test
./scripts/check-debug-build.sh
```

前三条命令可单独执行；脚本离线运行测试、构建和 lint，并检查 Debug APK 的模块入口、作用域、版本与 SHA-256。Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。若网络必须经过代理，请在本机 Gradle 用户配置或 JVM 参数中设置代理，不要向仓库提交含凭据的代理配置。

安装 Debug APK 后，在 LSPosed 中启用模块并将作用域限定为微信读书；打开模块应用授予悬浮窗权限并启动实时字幕。停用时先停止实时字幕，再在 LSPosed 中取消作用域或禁用模块。

## 正式签名与自动发布

可以复用其他应用的 Android 签名密钥。将 `keystore.properties.example` 复制为 `keystore.properties`，填写已有密钥的绝对路径、密钥库密码、别名和私钥密码；本地配置和密钥不会被 Git 跟踪。也可以通过 `KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD` 环境变量提供，环境变量优先。

```bash
chmod 600 keystore.properties
./gradlew :core:test :app:lintRelease :app:assembleRelease
```

正式 APK 位于 `app/build/outputs/apk/release/app-release.apk`。缺少签名配置时 Release 构建会失败；Debug 构建仍使用 Android 调试密钥。若设备上已有 Debug 签名版本，首次改装正式签名版本需要先卸载旧版本，再安装并重新配置模块权限和作用域。

在本仓库的 **Settings → Secrets and variables → Actions → Repository secrets** 配置以下四项（名称与 shiguangschedule 一致，但值需要在本仓库单独配置）：

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | 原有密钥库文件的 Base64 编码 |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 签名私钥别名 |
| `KEY_PASSWORD` | 签名私钥密码 |

push 到 `main` 后，GitHub Actions 使用 JDK 17、Android SDK 36 / Build Tools 36.0.0，依次运行核心测试、Release lint、正式签名构建及证书核验，再自动发布公开的 GitHub Release，附上 APK 和 `SHA256SUMS`。也可在 Actions 中手动运行；仅允许从 `main` 发布。无需额外配置 `GITHUB_TOKEN`。

Release 标签使用 `v<versionName>-build.<run_number>`，例如 `v0.9.0-build.1`。每次 push 生成独立 Release；重跑同一次 workflow 会更新该 Release 的附件。APK 内的 `versionName`、`versionCode` 以 `app/build.gradle.kts` 为准，正式版本升级时应同步递增。构建成功不代表真机注入或朗读同步已经通过验证。

不要提交 APK、签名密钥、反编译树、书籍正文或设备日志。
