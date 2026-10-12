# 开发进度

更新：2026-10-12。当前：P0 环境与设备基线，**PARTIAL**。

## 已完成

- 阅读并保留仓库现有规划、`AGENTS.md`、`.gitignore` 和 README；Git 已初始化但原先没有提交。
- 核查 WSL2 Ubuntu 24.04.4、JDK 17.0.20.1、SDK Platform 36、Build Tools 36.0.0、Platform Tools 37.0.1、Git 2.43.0；工作区位于 Linux 文件系统，剩余空间约 909 GB。
- 查询官方 AGP、Kotlin、Gradle 与 LSPosed 文档，在 [DECISIONS.md](DECISIONS.md) 区分候选构建链与尚未确认的模块 API。
- `.gitignore` 补充 `/local-data/`，防止后续本地分析材料被提交。
- 从 Gradle 官方地址下载 8.13 发行包并验证 SHA-256；生成带发行包校验值的项目 Wrapper，并用全新缓存验证下载与启动。
- 通过 Windows ADB 确认一加 11 已授权连接；只读确认 Android API 36、arm64，以及微信读书 `com.tencent.weread` 版本 10.2.2（versionCode 10167650）。未启动 App、未拉取 APK、未修改设备。
- 创建首个仓库提交 `93ac748`（`chore: establish project baseline`），包含规划、环境证据、忽略规则和已验证的 Gradle Wrapper。

## 验证与阻塞

- 主机命令、SDK 已安装包、Wrapper 下载校验与启动均成功。尚无 Android 模块，因此 APK 编译和自动测试未执行；一加真机仅完成只读基线，Redmi 未连接也未测试。
- 受限环境内 Linux ADB 不能绑定本地端口；经授权在沙箱外调用 Windows ADB 的路径可用。
- Gradle/Java 不自动采用 shell 代理变量；当前验证命令显式传递 JVM HTTP/HTTPS 代理。后续应采用不含凭据的本机 Gradle 代理配置或同样的临时 JVM 参数。
- 一加 11 的 LSPosed 实际分支、构建来源和支持的模块 API 仍未验证；不能进入依赖该结论的 P1。
- 无 Root 的包与系统属性查询未发现 LSPosed 标识；需要管理器“关于”页或安装来源信息完成判断。

## 下一步

取得一加 11 的 LSPosed 来源、构建和 API 证据，再完成 P0 验收。无需对 Redmi 执行操作。
