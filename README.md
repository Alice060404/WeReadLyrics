# WeReadLyrics

计划开发的 Android LSPosed 模块：在微信读书 AI 朗读时，向其他应用上方的悬浮窗显示当前实际读到的句子。项目目前处于 **P0 环境与设备基线**，尚无可构建的模块、APK 或真机功能结论。

- [完整开发规划](development-guide.md)
- [阶段与验收](docs/PLAN.md)
- [当前进度与阻塞](docs/PROGRESS.md)
- [技术决策与证据](docs/DECISIONS.md)

在确认目标设备 LSPosed 分支和模块 API 之前，不建立 Hook 入口；P3 只读探测证明文本与实际播放项关联之后，才开发同步悬浮窗功能。
