# P7 Redmi K80 至尊版适配验证

## 安全边界

目标机为 Redmi K80 至尊版（`dali`），当前依赖重启后失效的临时 Root。测试期间不得重启、刷机、修改分区或调整 Root/LSPosed 实现。将 APK 安装、Root、LSPosed 注入和字幕运行分别验证；任何一步失败都不得推断后续步骤已通过。

## 已确认基线

- 设备：Xiaomi `25060RK16C` / `dali`，arm64，Android 16（API 36）。
- 系统：HyperOS `OS3.0.303.0.WONCNXM`，安全补丁 `2026-07-05`。
- 当前 ADB shell 与 `su -c id` 均返回 UID 0、SELinux 上下文 `u:r:ksu:s0`，临时 Root 当前有效。
- `/data/adb/modules/zygisk_lsposed/module.prop` 报告 LSPosed `v2.1.1 (7790)`；`lspd` 与 `zygisk_lsposed` 进程正在运行。管理器 APK 显示 2.1.0，不能用其版本替代框架模块版本。
- 微信读书为 `com.tencent.weread` 10.2.2（versionCode 10167650）。WeReadLyrics 尚未安装。
- Redmi 与一加安装包的腾讯签名证书 SHA-256 均为 `e4dd2f049941989ffafa45aef52eda9701372c27bc1708e89c217935140bfa1c`。7,152 个 ZIP 条目、Manifest 和 `classes.dex` 至 `classes7.dex` 内容一致；整包 SHA-256 不同来自 ZIP 条目之外的单字节差异，不代表业务 DEX 不同。

## 验证步骤

1. 运行 `./scripts/check-debug-build.sh`，记录 0.8.0 Debug APK 的 SHA-256。
2. 经用户许可安装同一 APK，不重启设备；分别确认包版本、悬浮窗权限和 LSPosed 模块识别状态。
3. 由用户在 LSPosed 中启用模块并仅勾选微信读书作用域，再完全关闭并打开微信读书。
4. 日志确认 Modern API 模块进入微信读书主进程且 Hook 全部安装一次；若需要重启设备才能生效，停止测试并记录限制。
5. 启动实时字幕，验证播放、暂停/恢复、前后跳、切章、倍速、前后台、锁屏恢复和停止清屏。
6. 记录 HyperOS 的悬浮权限、通知和后台行为差异；至少连续播放 10 分钟，检查崩溃、重复 Hook、IPC 失败和文字残留。

## 判定

最终结论只能为：完整可用、当前 Root 会话可用、需要用户手动恢复环境或不兼容。必须明确重启后的临时 Root 限制以及未覆盖事项。

## 当前结果

`./scripts/check-debug-build.sh` 已通过离线测试、构建、lint 和 APK 元数据检查；APK 为 0.8.0（versionCode 10），SHA-256 为 `8b42d89430e30f629729d9458ab0ef0115ab08efde2ce94ec9971dbe27566f1b`。

经用户许可，APK 已安装到 Redmi，包管理器确认版本正确且安装成功。安装后模块进程没有自动启动，`SYSTEM_ALERT_WINDOW` 初始状态为 `ignore`。尚未授予悬浮权限、启用 LSPosed 作用域或验证注入，因此不能据安装成功宣称模块可用。

用户授予悬浮权限、启动实时字幕服务，并在 LSPosed 中启用模块且仅选择微信读书作用域；全程未重启设备。重新打开微信读书后，日志确认 Modern API 模块进入主进程，`Module loaded` 与 `Probe install complete` 各出现一次，十个 Hook 全部安装成功。用户确认朗读、字幕内容、内置高亮和暂停/恢复均正常。

HyperOS 场景测试有效会话持续 10 分 20.058 秒。同步状态与 Bridge 接收序号均为 1 至 159，全部唯一且无缺失；80 次 `Overlay applied` 对应实际视觉变化。用户确认前后跳、切章、倍速、前后台、锁屏恢复及停止清屏均正常，没有旧句残留、重复悬浮窗、卡顿或闪退。日志没有 `FATAL EXCEPTION`、Hook/IPC 失败、IPC 拒绝或异常断开。

测试开始时模块 PSS/RSS/Swap PSS 为 67,770/166,776/24,882 KB，结束时为 74,753/142,008/37,511 KB；样本负载与回收状态不同，未观察到用户可见的持续恶化。微信读书 PSS 从 1,038,590 KB 降至 675,976 KB，说明离散 PSS 不适合直接判断泄漏。

字幕可见时，经用户许可强制停止微信读书。微信读书进程退出后，Bridge 记录 `IPC source disconnected`，6 毫秒后应用 `IDLE` 空状态；用户确认字幕消失，模块前台服务保持存活。再次打开微信读书后，新进程中 Hook 仅安装一次，IPC 和字幕自动恢复，用户确认行为正常。

P7 最终结论为 **当前 Root 会话可用**。Redmi K80 至尊版在当前临时 Root、LSPosed 2.1.1（7790）会话中通过全部计划场景。设备重启会使临时 Root 失效；APK 仍可保留，但在 Root 和 LSPosed 注入恢复前不会获得 Hook 字幕能力。本阶段没有重启设备，也未验证失去 Root 后的实际表现。
