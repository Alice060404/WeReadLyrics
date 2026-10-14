# 兼容性与交付状态

## 支持边界

| 项目 | 已验证值 |
| --- | --- |
| 模块 | WeReadLyrics 0.9.0（versionCode 11，Debug） |
| 模块 API | Modern libxposed API 102 |
| 微信读书 | 10.2.2（versionCode 10167650） |
| 微信读书签名 SHA-256 | `e4dd2f049941989ffafa45aef52eda9701372c27bc1708e89c217935140bfa1c` |
| 构建链 | JDK 17、Gradle 8.13、AGP 8.13.2、Kotlin 2.3.0、compileSdk 36 |

未知微信读书版本默认不安装内部业务 Hook。微信读书更新后，即使类名未变，也必须重新比对 APK、运行只读探针并完成真机验证。

## 设备结果

| 设备 | 系统与框架 | 结果 |
| --- | --- | --- |
| 一加 11（PHB110） | ColorOS 16、Android 16/API 36、LSPosed 2.1.1（7790/API 102） | 0.8.0 完整通过功能、IPC 安全、宿主退出恢复及 42 分钟连续运行；最终 0.9.0 快速回归通过 |
| Redmi K80 至尊版（dali） | HyperOS 3、Android 16/API 36、LSPosed 2.1.1（7790） | 当前临时 Root 会话可用；0.8.0 通过完整场景和 10 分钟运行，0.9.0 版本门禁及朗读链路回归通过 |

Redmi 的临时 Root 会在重启后失效。模块 APK 和设置可以保留，但 Root 与 LSPosed 注入恢复前没有 Hook 字幕能力；测试未通过重启验证该失效路径。

## 当前产物

- 路径：`app/build/outputs/apk/debug/app-debug.apk`
- 构建日期：2026-10-14
- 版本：0.9.0（versionCode 11）
- SHA-256：`8beb454f2aa7ac857b18dc73246e8b0663208b4676c5d4a2ec24f1630c8df00d`
- 签名：Android Debug 签名，仅用于当前开发测试
- 建议里程碑标签：`v0.9.0-mvp`

项目没有自动发布、创建标签或推送远程。发布版本应使用受控签名、重新记录哈希，并重新执行两台设备的兼容性检查。
