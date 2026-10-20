# WeReadLyrics

Android LSPosed 模块：在微信读书 AI 朗读时，向其他应用上方的悬浮窗显示当前实际读到的句子。0.10.0 使用透明文字和阴影，长句在两行窗口内随句内字符进度滚动，不再以省略号截断。

当前仅启用微信读书 10.2.2（versionCode 10167650）的内部 Hook。未知版本会安全停用业务 Hook，等待重新分析和验证。升级模块后，需彻底退出并重新打开微信读书，让新的字符进度 Hook 生效。

当前入口使用设备已确认的 Modern libxposed API 102。同步核心通过 34 项 JVM 测试；0.10.0 已完成模拟器滚动显示与实际朗读进度链路验证，真机回归待完成。0.9.0 曾在一加 11 和 Redmi K80 至尊版验证可用。正文只在进程内传递，不写入磁盘或诊断日志。

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

push 到 `main` 或在 Actions 中手动运行，只执行核心测试、Debug lint 和 Debug 构建，上传 `debug-apk` 工作流产物，不创建 Release，也不需要签名 Secrets。工具链使用 JDK 17、Android SDK 36 / Build Tools 36.0.0。

只有推送 `v*` 标签才执行正式签名构建、Release lint、证书核验并发布 GitHub Release，附上 APK 和 `SHA256SUMS`。正式标签使用 `v0.10.0`；测试版可使用 `v0.10.0-beta.1`，并自动标记为预发布。标签必须与 APK 的 `versionName` 完全一致（加上前缀 `v`），不匹配会中止发布；测试版也需先将 `versionName` 改为对应的 `0.10.0-beta.1`。

当前版本为 `versionName = "0.10.0"`、`versionCode = 12`。后续升级时需手动更新版本号并递增 `versionCode`。提交并检查改动后，手动发布：

```bash
git push origin main
git tag -a v0.10.0 -m "Release v0.10.0"
git push origin v0.10.0
```

重跑同一次标签工作流会更新该 Release 的附件，不改变标签。无需额外配置 `GITHUB_TOKEN`。构建成功不代表真机注入或朗读同步已经通过验证。

不要提交 APK、签名密钥、反编译树、书籍正文或设备日志。
