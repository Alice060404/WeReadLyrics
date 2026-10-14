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

不要提交 APK、签名密钥、反编译树、书籍正文或设备日志。
