# WeReadLyrics

Android LSPosed 模块：在微信读书 AI 朗读时，向其他应用上方的悬浮窗显示当前实际读到的句子。一加 11 端到端验证已通过；Redmi K80 至尊版在当前临时 Root 会话中可用。项目准备进入 P8 交付维护。

- [完整开发规划](development-guide.md)
- [阶段与验收](docs/PLAN.md)
- [当前进度与阻塞](docs/PROGRESS.md)
- [技术决策与证据](docs/DECISIONS.md)
- [P1 真机验证步骤](docs/P1_DEVICE_TEST.md)
- [P2 Hook 调查](docs/HOOK_RESEARCH.md)
- [P3 真机验证步骤](docs/P3_DEVICE_TEST.md)
- [P4 日志层同步验证](docs/P4_DEVICE_TEST.md)
- [P4 验证结果](docs/P4_RESULTS.md)
- [P5 模拟悬浮窗验证步骤](docs/P5_DEVICE_TEST.md)
- [P5 实时字幕与 IPC 验证步骤](docs/P5_LIVE_DEVICE_TEST.md)
- [P5 IPC 调用方负向验证](docs/P5_IPC_SECURITY_TEST.md)
- [P6 一加端到端与稳定性验证](docs/P6_DEVICE_TEST.md)
- [P7 Redmi 适配验证](docs/P7_DEVICE_TEST.md)

当前入口使用设备已确认的 Modern libxposed API 102。P3 已在一加 11 上证明高亮文本与实际播放项关联；P4 已通过 22 项同步逻辑测试和日志层真机验证；P5 已通过 25 项 JVM 测试、真实字幕与 IPC 安全验证；P6 已通过完整功能场景和 42 分钟连续运行验证。

## 本地构建

要求 JDK 17 和 Android SDK Platform 36。项目 Wrapper 会校验 Gradle 8.13 发行包：

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :core:test
./scripts/check-debug-build.sh
```

前三条命令可单独执行；脚本离线运行测试、构建和 lint，并检查 Debug APK 的模块入口、作用域、版本与 SHA-256。Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。若网络必须经过代理，请在本机 Gradle 用户配置或 JVM 参数中设置代理，不要向仓库提交含凭据的代理配置。
