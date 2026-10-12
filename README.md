# WeReadLyrics

计划开发的 Android LSPosed 模块：在微信读书 AI 朗读时，向其他应用上方的悬浮窗显示当前实际读到的句子。项目目前处于 **P3 只读动态探测**；现有 APK 只记录脱敏的朗读高亮事件，不包含悬浮窗功能，也不改变微信读书行为。

- [完整开发规划](development-guide.md)
- [阶段与验收](docs/PLAN.md)
- [当前进度与阻塞](docs/PROGRESS.md)
- [技术决策与证据](docs/DECISIONS.md)
- [P1 真机验证步骤](docs/P1_DEVICE_TEST.md)
- [P2 Hook 调查](docs/HOOK_RESEARCH.md)
- [P3 真机验证步骤](docs/P3_DEVICE_TEST.md)

当前入口使用设备已确认的 Modern libxposed API 102。P3 只读探测证明文本与实际播放项关联之后，才开发同步悬浮窗功能。

## 本地构建

要求 JDK 17 和 Android SDK Platform 36。项目 Wrapper 会校验 Gradle 8.13 发行包：

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。若网络必须经过代理，请在本机 Gradle 用户配置或 JVM 参数中设置代理，不要向仓库提交含凭据的代理配置。
