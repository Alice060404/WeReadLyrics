# 故障排查

## LSPosed 中看不到模块

确认安装包为 `io.github.alice060404.wereadlyrics`，并检查 APK 内含 `META-INF/xposed/java_init.list` 与 `scope.list`。当前模块要求 Modern libxposed API 102；传统 `assets/xposed_init` 流程不适用。Redmi 重启后临时 Root 会失效，此时 APK 仍在，但 LSPosed 无法注入，必须先由设备所有者恢复原有临时 Root 环境。

## 有模块日志但没有业务 Hook

查看脱敏日志：

```bash
adb logcat -v time | rg 'WeReadLyrics|FATAL EXCEPTION'
```

正常顺序为 `Module loaded`、`Host compatibility bootstrap installed`、`Host version accepted`、`Probe install complete`。出现 `Unsupported host version` 表示微信读书不是已验证的 10.2.2/versionCode 10167650；模块会安全禁用业务 Hook。不要通过改常量强行兼容，应重新分析 APK 和真机事件。

## Hook 正常但字幕不出现

确认模块应用显示悬浮权限已授予，并重新点击“启动实时字幕”。系统应保留 WeReadLyrics 前台通知。日志中的 `IPC bind requested=true`、`IPC connected`、`IPC accepted` 和 `Overlay applied` 分别证明绑定、鉴权和显示链路；缺少哪一步就从对应环节排查。更新 APK 会结束旧前台服务，需要手动重新启动实时字幕。

## 字幕残留或不同步

正常停止允许最多约 15 秒清理窗口；强制退出微信读书应通过 Binder 死亡通知立即清屏。若内容与发声不符，记录设备、微信读书版本、操作步骤以及仅含长度、序号和状态的日志。不要提交书籍正文、原始 APK、完整反编译树或含账号信息的日志。

## 恢复微信读书正常状态

先在模块应用停止悬浮字幕，再在 LSPosed 取消微信读书作用域并完全关闭微信读书。仍有问题时，经确认卸载模块。模块不修改微信读书数据，无需清除应用数据；禁止使用重启、刷机或分区修改作为常规排障手段。
