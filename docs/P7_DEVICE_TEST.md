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
