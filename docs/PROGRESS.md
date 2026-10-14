# 开发进度

更新：2026-10-14。当前：P7 已通过，结论为当前 Root 会话可用；准备进入 P8 交付维护。

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
- 用户人工验证微信读书 AI 朗读能够正常发声，未观察到异常；P1 构建、注入和基础无回归验收完成。
- 经用户授权拉取微信读书 10.2.2 单一 `base.apk`，校验 ZIP 完整性与 SHA-256，并确认 APK 和反编译树均受 Git 忽略规则保护。
- 从官方 GitHub Release 安装并校验 JADX 1.5.6；结合 `apkanalyzer` 复核 `classes5.dex` 中的 TTS 模型、播放回调和高亮广播签名，形成 `HOOK_RESEARCH.md`。
- 实现 P3 第一轮只读探针：精确 Hook 高亮 start/pause/stop，始终执行原调用；日志只记录长度、随机盐短哈希、范围、线程、序号和相对时间。
- 经用户授权在一加 11 覆盖安装 P3 Debug APK；系统确认模块已更新为 0.2.0（versionCode 2）。
- 完成两轮朗读探测；实时日志和用户人工观察支持高亮事件对应实际发声，但暂停与停止均表现为 stop，第一轮结论为 `PARTIAL`，详见 `P3_RESULTS.md`。
- 在 0.3.0 中增加 `TTSPlayer` start/pause/resume/stop 生命周期探针，用于区分第一轮无法判断的暂停与最终停止；仍不记录高频 progress 或原始标识。
- 0.3.0 已通过离线 `:app:assembleDebug` 和 `:app:lintDebug`；APK 版本、API 102 模块元数据、v2 调试签名及 `compileOnly` 打包边界检查通过。
- 经用户授权将 0.3.0 覆盖安装到一加 11；Android 包管理器报告安装成功，并确认 versionCode 3、versionName 0.3.0。
- 用户重新打开微信读书后，LSPosed 日志确认模块进入 `com.tencent.weread` 主进程、`firstPackage=true`，高亮三个和播放器生命周期四个 Hook 均安装完成。
- 0.3.0 第二轮实测中，用户确认发声、高亮、暂停、继续和停止均正常；日志未出现 callback pause/resume，callback stop 在每个 utterance 结束时触发，因此仍为 `PARTIAL`。
- 原始 DEX 核实 `TTSBookPlayer` 的七个低频控制方法；0.4.0 将探针移到 start/pause/resume/stop/seek/foreAhead/foreBack，并移除易误判的 callback 生命周期探针。
- 0.4.0 已通过离线 `:app:assembleDebug` 和 `:app:lintDebug`；APK 版本、API 102 模块元数据、v2 调试签名及 `compileOnly` 打包边界检查通过。
- 经用户授权将 0.4.0 覆盖安装到一加 11；Android 包管理器报告安装成功，并确认 versionCode 4、versionName 0.4.0。
- 用户重新打开微信读书后，LSPosed 日志确认 0.4.0 进入主进程且十个高亮/书籍播放器控制 Hook 均安装完成。
- 0.4.0 基础控制实测中，用户确认行为均正常；日志显示开始为 control start、暂停为 control pause、继续为相邻 stop+start，最终停止也可能表现为 pause。控制映射通过，但阶段结论保持 `PARTIAL`。
- 完成约 2 分钟综合样本：连续播放、前后跳、切章、前后台、变速和停止均由用户确认正常；日志显示高亮连续推进及两次新文本范围重置，无探针失败或崩溃。P3 最终结论为 `PASS`。
- 新增纯 Kotlin `core` 模块、标准事件模型和有界同步状态机；以会话号及单调序号拒绝旧事件，预取不抢占当前文本，跳转/切章/停止会清除旧状态。
- `PlaybackStateMachineTest` 共 10 项，覆盖预取、乱序、暂停、继续、跳转、切章、停止、会话重启、有界队列、非法范围及同句重播；0 跳过、0 失败、0 错误。
- 新增观测事件适配器，将 P3 实测的 pause、stop+start、范围回退和章节变化归一化为标准事件；12 项适配器测试通过，core 合计 22 项测试、0 失败。时间窗口由接入层显式配置，尚未宣称产品级数值。
- 0.5.0 将 core 接入只读 Hook：正文只在进程内传递，标准状态日志仅含会话、序号、状态、长度、短 token 和范围；延迟超时回调单独隔离异常。
- 0.5.0 已通过全项目测试、Debug 构建和 lint；APK 版本、v2 调试签名、API 102 元数据、core DEX 打包和 libxposed `compileOnly` 边界检查通过。
- 经用户授权将 0.5.0 覆盖安装到一加 11；Android 包管理器报告安装成功，并确认 versionCode 5、versionName 0.5.0。
- 用户重新打开微信读书后，LSPosed 日志确认 0.5.0 进入主进程且十个 Hook 安装完成；未出现探针或同步初始化错误。
- 完成 0.5.0 日志层真机验证：标准事件序号 1 至 30 单调递增；前后跳清除旧项，切章后从新范围开始，停止超时后进入 `IDLE` 且正文长度清零。
- 用户确认播放、暂停、继续、前后跳、切章和停止期间发声与内置高亮均正常；未发现同步错误、Hook 失败或宿主崩溃。P4 最终结论为 `PASS`，详见 `P4_RESULTS.md`。
- P5 0.6.0 新增最小管理界面和本地模拟悬浮服务：系统权限引导、显隐、三条周期模拟文字、两行末尾省略、屏幕内拖动及位置保存。服务采用 `specialUse` 前台类型且不导出，尚未接入 Hook 或 IPC。
- 0.6.0 的 22 项 core 测试、Debug 构建和 lint 均通过；APK 为 v2 签名，Manifest 与版本检查通过。真机模拟模式尚未验证。
- 经用户授权将 0.6.0 覆盖安装到一加 11；ADB 报告安装成功，设备确认 versionCode 6、versionName 0.6.0、targetSdk 36 及预期的悬浮窗/前台服务/通知权限声明。
- 用户在一加 11 确认 0.6.0 的授权路径、三条文字更新、两行省略、拖动、跨桌面/浏览器/微信读书显示、通知隐藏和位置恢复均正常；P5 模拟模式结论为 `PASS`。
- P5 0.7.0 将私有悬浮服务与导出 Binder Bridge 分离；AIDL 使用单向调用，Bridge 逐次校验 UID、固定包名和设备 APK 实测证书，Hook 不自动创建服务。正文只在内存传递且限制为 4096 字符。
- 通过微信读书实际 Manifest 与 DEX 确认 `WRApplication` 的 Tinker 父链及 `TinkerApplication.getInstance()`，Hook 在真实高亮事件发生后用它取得 Context 并绑定已运行 Bridge，未编造业务类或字段。
- 0.7.0 的 25 项 core 测试、Debug 构建和 lint 均通过；lint 仅保留既有 Gradle 版本提示。APK 为 v2 签名，AIDL、Bridge、客户端及 core 均存在于 DEX，libxposed 仍仅为外部引用。
- 0.7.0 首轮真机中 Hook 与标准状态正常，但没有发起 IPC 绑定，悬浮字幕未出现。脱敏日志定位到首次绑定时间哨兵参与减法后溢出，错误触发重试节流；改用 nullable 首次状态修复，准备发布 0.7.1。
- 0.7.1 已安装到一加 11。日志确认首次 bind 返回 true、连接成功，Bridge 与 Overlay 连续接受并应用状态至序号 62，覆盖播放、暂停、恢复与最终 `IDLE`；未见 IPC/Hook 错误或崩溃。
- 用户确认 0.7.1 字幕成功出现、内容与当前发声一致，微信读书发声与内置行为正常。P5 基本真实链路通过，但完整阶段结论仍为 `PARTIAL`。
- 用户完成 0.7.1 完整交互复测：跨应用、暂停/恢复、前后跳、切章、停止后清屏、通知保留，以及关闭实时字幕后再次朗读不自启均正常。
- 脱敏日志与人工结果一致：出现 `PAUSED`、`Resumed`、章节/会话重建和 15 秒后的 `IDLE`；用户关闭后 IPC 断开，后续朗读事件没有触发 `Overlay applied`，且未见崩溃。
- shell 尝试显式启动私有 `OverlayService` 被 `Requires permission not exported` 拒绝；模块进程未启动，系统记录显示 `app=null`、`startForegroundCount=0`。
- 新增独立、无权限的 IPC 调用方探针。真机确认探针 UID 与微信读书、模块均不同；Bridge 明确记录拒绝，未进入更新总线，探针解绑后服务退出且已从设备卸载。
- 0.7.2 正向回归中模块重新注入微信读书主进程，连续完成 `IPC accepted` 与 `Overlay applied`；用户确认字幕、发声和内置高亮正常。P5 最终结论为 `PASS`。
- P6 0.8.0 仅在文字、透明度或显隐变化时更新悬浮视图，避免预备状态造成相同文本重复重绘；25 项 JVM 测试、Debug 构建和 lint 通过。
- 新增 Binder 进程生命令牌：可信微信读书进程退出时，Bridge 生成 `IDLE` 更新清除残留字幕；独立探针已同步新 AIDL 并通过构建，真机死亡通知待验证。
- 新增 `scripts/check-debug-build.sh`，离线执行测试、构建和 lint，并校验 APK 包名、版本、Modern Xposed 入口、作用域与 SHA-256；脚本已实际运行通过。
- 0.8.0 已安装到一加 11。强制停止正在朗读的微信读书后，Binder 死亡通知与 `IDLE` 清屏在同一毫秒记录，用户确认字幕消失；重新打开后 IPC 与字幕正常恢复。
- 真机日志确认预备/播放状态均被接收，但相同视觉内容只产生一次 `Overlay applied`，重绘去重生效；未见模块或宿主崩溃。
- 用户确认 0.8.0 三次启动/停止、最低/最高倍速、跨应用、锁屏恢复和约 20 秒断网恢复均正常；未观察到错句、明显时序异常、重复窗口或通知。
- 对应日志覆盖多个暂停/恢复及连续播放状态，无崩溃、Hook 安装失败或 IPC 发布失败；功能场景结束时模块 PSS 约 43 MB，尚不能单独据此判断泄漏。
- 新进程连续运行样本覆盖约 17 分 29 秒有效观测；用户确认暂停/恢复、前后台切换和倍速变化均正常。主进程仅安装一次 Hook，436 次 IPC 更新产生 219 次实际视觉更新，未见崩溃、Hook/IPC 失败或拒绝。
- 结束时模块 PSS 为 57,690 KB，微信读书主进程 PSS 为 483,194 KB。模块相较中途单点约增加 14 MB，但离散样本不足以判断内存泄漏；因连续运行未达到 30 分钟，P6 保持 `PARTIAL`。
- 最终连续测试有效会话达到 42 分 06.734 秒。同步状态与 Bridge 接收序号均为 1 至 1151，全部唯一且无缺失；575 次实际视觉更新覆盖持续播放、2 次暂停/恢复、5 次跳转和 6 次章节变化。
- 用户确认本轮暂停/恢复、前后台切换、倍速变化、字幕同步及宿主行为均正常；日志没有崩溃、Hook/IPC 失败、拒绝或异常断开。P6 最终结论为 `PASS`。
- 该轮模块 PSS 从空闲态 40,551 KB 变为播放态 79,433 KB。因采样负载不同，约 38 MB 差值仅列为后续关注项，不据此宣称存在或不存在内存泄漏。
- 用户已明确允许 P7 目标机测试。ADB 识别 Redmi K80 至尊版为 `25060RK16C` / `dali`，系统为 Android 16、API 36、HyperOS `OS3.0.303.0.WONCNXM`，ABI 为 arm64。
- 只读检查确认当前 ADB shell 与 `su` 均为 `ksu` Root 上下文；LSPosed 模块为 2.1.1（7790），`lspd` 与 Zygisk companion 正在运行。WeReadLyrics 尚未安装。
- Redmi 微信读书为 10.2.2（versionCode 10167650）。与一加 APK 的签名、7,152 个 ZIP 条目、Manifest 和全部 DEX 一致；整包仅签名块附近存在单字节差异，业务 Hook 目标代码相同。
- P7 构建检查再次通过，0.8.0 APK SHA-256 保持 `8b42d89430e30f629729d9458ab0ef0115ab08efde2ce94ec9971dbe27566f1b`。经用户许可安装到 Redmi 后，包管理器确认 versionCode 10；模块未自动启动，悬浮权限初始为 `ignore`。
- 用户手动授予悬浮权限并启用微信读书作用域后，Redmi 主进程仅记录一次模块加载与 Hook 安装；真实朗读、字幕、内置高亮和暂停/恢复均正常。
- HyperOS 场景测试持续 10 分 20.058 秒；状态机与 IPC 序号 1 至 159 完整且唯一。用户确认前后跳、切章、倍速、前后台、锁屏恢复和停止清屏均正常，日志无崩溃、Hook/IPC 失败或拒绝。
- 强制停止微信读书后，Binder 死亡通知在 6 毫秒后触发空状态清屏；模块服务保持运行。重新打开后新进程仅安装一次 Hook，IPC 与字幕恢复正常。
- P7 结论为“当前 Root 会话可用”。测试全程未重启；设备重启导致临时 Root 失效后，模块 APK 即使仍安装，也无法在 Root 与 LSPosed 注入恢复前提供 Hook 字幕。

## 验证与阻塞

- 主机命令、SDK 已安装包、Wrapper 下载校验与启动均成功；Android 模块已通过编译和静态检查。一加已完成 P1 注入与基础回归，Redmi 未连接也未测试。
- 受限环境内 Linux ADB 不能绑定本地端口；经授权在沙箱外调用 Windows ADB 的路径可用。
- Gradle/Java 不自动采用 shell 代理变量；当前验证命令显式传递 JVM HTTP/HTTPS 代理。后续应采用不含凭据的本机 Gradle 代理配置或同样的临时 JVM 参数。
- P0 自动验证与一加只读基线已完成；Redmi 未连接且不在当前阶段测试。
- P3 最终验证版本为 0.4.0（versionCode 4），当时 APK SHA-256 为 `ea714374ee7126be769339df4b59f74f95b868be7f0c70dc3eeca87e60af0811`；现已由 P4 构建覆盖。
- 当前 P4 Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，版本 0.5.0（versionCode 5），SHA-256 为 `b720f8590e4d0456b731b0303351b2aff52c8bf30474e67b0a1dc743394f3dd9`；已安装到一加 11。
- Lint 仅保留一个版本提示：Gradle 8.14.6 已存在。当前继续使用与选定 AGP 8.13.2 官方兼容表一致的 Gradle 8.13。
- APK 安装、LSPosed 模块识别、作用域和主进程加载日志已通过一加 11 真机验证；日志不含书籍原文。P1 仅验证 AI 朗读基础无回归，P3 随后完成了高亮与发声关联测试。
- P2 找到文本与播放状态候选点；JADX 报告大量反编译错误，关键签名已用原始 DEX 工具复核。P3 已完成第一轮真机时序验证，结论为 `PARTIAL`。
- P3 0.2.0 探针已确认三个高亮 Hook 成功；0.3.0 的七个 Hook 已加载并完成第二轮测试，但回调生命周期不能区分用户控制。
- P3 探针已在真机成功加载且未观察到宿主回归；当前文本与实际播放项关联通过硬门槛。控制方法名与产品状态并非一一对应，该限制转入 P4 状态机处理。
- `./gradlew test :app:assembleDebug :app:lintDebug --offline --no-daemon` 成功；新增 `core` 未破坏 Android 模块。首次离线解析因缺少 Kotlin JVM 插件标记失败，随后从项目已配置的官方仓库解析 2.3.0，后续离线测试通过。
- P4 真机证据只保留脱敏事件摘要；未记录正文、书名、原始 utteranceId 或章节号。人工验证证明无可见回归，不等同于毫秒级同步精度测量。
- P5 模拟模式 APK 为 0.6.0（versionCode 6），当时 SHA-256 为 `5bc43dbf892b76de12c8d8bc57bc2e2b4ff73d095bac669380f71b34a62531ed`。
- P5 实时模式修复 APK：`app/build/outputs/apk/debug/app-debug.apk`，版本 0.7.1（versionCode 8），SHA-256 为 `f7fe55d92b4a7e94c85a60a0ac7993a95646f640de837072d8a55b09fde3a2b4`；已安装并通过基本链路验证。
- P5 当前 APK：`app/build/outputs/apk/debug/app-debug.apk`，版本 0.7.2（versionCode 9），SHA-256 为 `2c966889814e0747cf58dfde0c07fd181f0d9a569f35fa140d070ea39c3b2283`；已安装到一加 11 并通过正向与负向 IPC 回归。
- P6 当前 APK：`app/build/outputs/apk/debug/app-debug.apk`，版本 0.8.0（versionCode 10），SHA-256 为 `8b42d89430e30f629729d9458ab0ef0115ab08efde2ce94ec9971dbe27566f1b`；已安装到一加 11 并通过完整端到端与稳定性验证。

## 下一步

进入 P8：完善安装、构建、兼容性、故障排查与安全回滚文档，并在干净 Gradle 用户目录复现构建。
