# 开发进度

更新：2026-10-12。当前：P1 最小 LSPosed 模块，**进行中**。

## 已完成

- 阅读并保留仓库现有规划、`AGENTS.md`、`.gitignore` 和 README；Git 已初始化但原先没有提交。
- 核查 WSL2 Ubuntu 24.04.4、JDK 17.0.20.1、SDK Platform 36、Build Tools 36.0.0、Platform Tools 37.0.1、Git 2.43.0；工作区位于 Linux 文件系统，剩余空间约 909 GB。
- 查询官方 AGP、Kotlin、Gradle 与 LSPosed 文档，在 [DECISIONS.md](DECISIONS.md) 区分候选构建链与尚未确认的模块 API。
- `.gitignore` 补充 `/local-data/`，防止后续本地分析材料被提交。
- 从 Gradle 官方地址下载 8.13 发行包并验证 SHA-256；生成带发行包校验值的项目 Wrapper，并用全新缓存验证下载与启动。
- 通过 Windows ADB 确认一加 11 已授权连接；只读确认 Android API 36、arm64，以及微信读书 `com.tencent.weread` 版本 10.2.2（versionCode 10167650）。未启动 App、未拉取 APK、未修改设备。
- 创建首个仓库提交 `93ac748`（`chore: establish project baseline`），包含规划、环境证据、忽略规则和已验证的 Gradle Wrapper。
- 检查用户提供的本地截图，确认一加 11 的 LSPosed 2.1.1 (7790) 已激活并报告 API 102；Root 环境为 SukiSU 4.2.0 与 Zygisk Next。
- 从 Maven Central 获取并校验 libxposed API 102 AAR，检查实际接口后确定 P1 使用 Modern API 102；不混用 Legacy API。
- 创建最小 Android/LSPosed 模块：API 102 入口、静态微信读书作用域、脱敏包/进程加载日志，不包含业务 Hook。
- `:app:assembleDebug` 与 `:app:lintDebug` 成功；APK 签名、Manifest、入口、模块属性、作用域和 DEX 内容检查通过。
- 创建提交 `2338575`（`feat: add minimal api 102 module`），固定上述 P1 最小模块及真机测试说明。
- 经用户授权，通过 ADB 将 Debug APK 安装到一加 11；系统确认包 `io.github.alice060404.wereadlyrics` 版本 0.1.0、versionCode 1，安装成功且尚未启动。
- 用户在 LSPosed 启用模块、将作用域限定为微信读书并重新打开 App；ADB 日志确认 WeReadLyrics 已加载到 `com.tencent.weread` 主进程，`firstPackage=true`。

## 验证与阻塞

- 主机命令、SDK 已安装包、Wrapper 下载校验与启动均成功；Android 模块已通过编译和静态检查。一加真机仅完成只读基线，Redmi 未连接也未测试。
- 受限环境内 Linux ADB 不能绑定本地端口；经授权在沙箱外调用 Windows ADB 的路径可用。
- Gradle/Java 不自动采用 shell 代理变量；当前验证命令显式传递 JVM HTTP/HTTPS 代理。后续应采用不含凭据的本机 Gradle 代理配置或同样的临时 JVM 参数。
- P0 自动验证与一加只读基线已完成；Redmi 未连接且不在当前阶段测试。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，本次 SHA-256 为 `8651902da4e3a273be638f75be65fd4dee8b82cb92326418331ea701c9e97fd0`；构建产物受 `.gitignore` 保护。
- Lint 仅保留一个版本提示：Gradle 8.14.6 已存在。当前继续使用与选定 AGP 8.13.2 官方兼容表一致的 Gradle 8.13。
- APK 安装、LSPosed 模块识别、作用域和主进程加载日志已通过一加 11 真机验证；日志不含书籍原文。微信读书 AI 朗读无回归仍待人工验证。

## 下一步

由用户在一加 11 启动一次微信读书 AI 朗读，确认播放、暂停、继续和停止仍正常；通过后关闭 P1，并在取得 APK 读取许可后进入 P2 静态分析。
