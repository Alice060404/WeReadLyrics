# P5 IPC 调用方负向验证

## 目的

用独立 UID 的最小测试应用调用导出的 `PlaybackBridgeService`，确认 Bridge 丢弃非微信读书来源。探针不申请权限、不读取书籍数据，也不包含在模块 APK 中。

## 步骤

1. 构建并安装 0.7.2 模块 APK，重新打开微信读书，使更新后的 Hook 生效。
2. 构建并安装 `tools/ipc-probe` Debug APK；其包名为 `io.github.alice060404.wereadlyrics.ipcprobe`。
3. 启动 `ProbeActivity`。它用 `BIND_AUTO_CREATE` 绑定 Bridge，发送一条参数合法、正文为空的 `IDLE` 状态，约一秒后解绑并退出。
4. 检查脱敏日志：应先出现探针的 `Probe transaction sent`，随后出现模块的 `IPC rejected: uid=...`；不得出现对应的 `IPC accepted` 或 `Overlay applied`。
5. 检查微信读书播放与模块界面未受影响。验证后卸载探针 APK。

## 判定

- `PASS`：调用 UID 与模块、微信读书均不同；Bridge 明确记录拒绝；请求没有进入更新总线或悬浮窗。
- `FAIL`：请求被接受、创建字幕，或导致模块/微信读书崩溃。

安装、启动和卸载测试 APK 都属于设备修改，执行前取得用户许可。日志不得保存正文、书名或其他阅读数据。
