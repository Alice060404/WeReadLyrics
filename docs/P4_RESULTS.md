# P4 同步核心验证结果

日期：2026-10-12。设备：一加 11（PHB110，Android API 36）；微信读书 10.2.2；LSPosed 2.1.1 (7790)；模块 0.5.0（versionCode 5）。

## 自动验证

- `core` 的 22 项 JVM 测试全部通过，覆盖预取、乱序、暂停、继续、跳转、切章、停止、会话重启、有界队列、非法范围和同句重播。
- `./gradlew test :app:assembleDebug :app:lintDebug --offline --no-daemon` 成功。
- APK 中包含同步核心，不包含 `libxposed` 实现；Modern API 102 元数据、v2 签名及版本信息检查通过。

## 真机时间轴

按 `P4_DEVICE_TEST.md` 完成播放、暂停、继续、前后跳、切章和停止。脱敏日志的标准事件序号从 1 单调递增到 30：

- 会话以 `SessionStarted` 开始，章节与正文准备后进入 `PLAYING`。
- 连续播放时，正文长度、短 token 和字符范围随实际发声项推进。
- 前后跳产生 `Seeked` 并清除旧项；切章产生 `ChapterChanged`，随后从新章节范围重新开始。
- 暂停产生 `Paused`；停止并等待 15 秒后产生 `Stopped`，最终为 `IDLE`、`textLength=0`、无当前 token 和范围。
- 未出现 `Probe observation failed`、`Sync flush failed`、Hook 安装失败或宿主崩溃。

## 人工验证与结论

用户确认全程发声、内置高亮、暂停、继续、跳转、切章和停止均正常。该观察证明本轮没有可见宿主回归，但不代表毫秒级同步精度。

**P4 结论：PASS。** 同步核心满足进入 P5 的硬门槛。1 秒重启合并窗口和 15 秒空闲清理超时仍是诊断参数，后续需结合悬浮窗体验继续校准。
