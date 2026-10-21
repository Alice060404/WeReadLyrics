# WeReadLyrics

Android LSPosed 模块：在微信读书 AI 朗读时，向其他应用上方的悬浮窗显示当前实际读到的句子。0.10.0 使用透明文字和阴影，长句在两行窗口内随句内字符进度滚动，不再以省略号截断。

当前仅启用微信读书 10.2.2（versionCode 10167650）的内部 Hook。未知版本会安全停用业务 Hook，等待重新分析和验证。升级模块后，需彻底退出并重新打开微信读书，让新的字符进度 Hook 生效。

当前入口使用设备已确认的 Modern libxposed API 102。同步核心通过 34 项 JVM 测试；0.10.0 已完成模拟器滚动显示与实际朗读进度链路验证，真机回归待完成。0.9.0 曾在一加 11 和 Redmi K80 至尊版验证可用。正文只在进程内传递，不写入磁盘或诊断日志。

应用采用 Material 3 Expressive（Material Components 1.14.0），支持跟随系统深浅色主题，在支持的 Android 12 及以上设备上使用壁纸动态配色。底部导航从左到右为主页、功能、设置：主页检查框架连接、微信读书作用域和悬浮窗权限，可手动检测当前应用的 Root 授权；功能页提供实时字幕、演示字幕与停止操作；设置页提供“个性化设置”，可选择液态玻璃（默认）或 Material 3 底部导航，立即应用并自动保存。切换样式时保留当前页面，重启后仍使用已保存的样式。液态玻璃支持调节导航栏高度（56–96 dp，字体需要时自动增高）、背景模糊（0–24 dp）、底色不透明度（0–100%）、折射和色散强度（0–200%），动效弹性（0–100%，调低可减弱膨胀、拉伸和回弹，0% 保留切页滑动并关闭弹性形变，100% 为原有效果），实时预览、自动保存，可一键恢复玻璃默认参数；Material 3 样式下隐藏玻璃参数并保留数值。旧版 Android 不支持的参数会禁用并提示。悬浮字幕继续使用透明文字与两行滚动。

接入官方 libxposed 状态服务后，最低安装版本为 Android 8.0（API 26）。本应用无需直接获得 Root，未授权 Root 不会阻止通过 LSPosed 使用；只有点按检测按钮时才会尝试请求 Root。框架连接与作用域已选择并不代表实际朗读同步已通过验证；框架未连接时显示待确认状态。

液态玻璃导航样式复用 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) 的圆角折射着色器并适配为原生 Android View。导航项不使用 Material 涟漪或按下背景：选中透镜在图标、文字上方放大并折射其主题色副本，按压时膨胀、高光与阴影增强，移动时随速度形变，松手后弹簧回落；支持横向拖动切页。Android 13 及以上使用模糊与折射，Android 12 使用模糊，Android 8–11 使用半透明玻璃外观。导航浮在页面上方，页面底部预留滚动空间；页面背景只在滚动、切页或状态更新时记录导航后方的局部内容，动效帧只更新导航内部的合成层。授权文件与来源说明随 APK 放在 `assets/licenses/`，构建无需引用本机 AndroidLiquidGlass 目录。实际设备上的玻璃效果与动画流畅度待验证。

## 本地构建

要求 JDK 17 和 Android SDK Platform 36。项目 Wrapper 会校验 Gradle 8.13 发行包：

```bash
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :core:test
./scripts/check-debug-build.sh
```

前三条命令可单独执行；脚本离线运行测试、构建和 lint，并检查 Debug APK 的模块入口、作用域、版本与 SHA-256。Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。若网络必须经过代理，请在本机 Gradle 用户配置或 JVM 参数中设置代理，不要向仓库提交含凭据的代理配置。

安装 Debug APK 后，在 LSPosed 中启用模块并将作用域限定为微信读书；打开模块应用授予悬浮窗权限并启动实时字幕。停用时先停止实时字幕，再在 LSPosed 中取消作用域或禁用模块。

## 正式签名与自动发布

可以复用其他应用的 Android 签名密钥。将 `keystore.properties.example` 复制为 `keystore.properties`，填写已有密钥的绝对路径、密钥库密码、别名和私钥密码；本地配置和密钥不会被 Git 跟踪。也可以通过 `KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD` 环境变量提供，环境变量优先。

```bash
chmod 600 keystore.properties
./gradlew :core:test :app:lintRelease :app:assembleRelease
```

正式 APK 位于 `app/build/outputs/apk/release/app-release.apk`。缺少签名配置时 Release 构建会失败；Debug 构建仍使用 Android 调试密钥。若设备上已有 Debug 签名版本，首次改装正式签名版本需要先卸载旧版本，再安装并重新配置模块权限和作用域。

在本仓库的 **Settings → Secrets and variables → Actions → Repository secrets** 配置以下四项（名称与 shiguangschedule 一致，但值需要在本仓库单独配置）：

| Secret | 内容 |
| --- | --- |
| `KEYSTORE_BASE64` | 原有密钥库文件的 Base64 编码 |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 签名私钥别名 |
| `KEY_PASSWORD` | 签名私钥密码 |

push 到 `main` 或在 Actions 中手动运行，执行发布脚本测试、核心测试、Debug lint 和 Debug 构建，上传 `debug-apk` 工作流产物，不创建 Release，也不需要签名 Secrets。工具链使用 JDK 17、Android SDK 36 / Build Tools 36.0.0。

只有推送 `v*` 标签才执行正式签名构建、Release lint、证书核验并发布 GitHub Release，同时附上正式签名 APK（`WeReadLyrics-v版本号.apk`）、Debug APK（`WeReadLyrics-v版本号-debug.apk`）和包含两者校验和的 `SHA256SUMS`。Debug APK 使用构建环境的调试签名，与正式签名不同，两个版本不能互相覆盖安装。正式标签使用 `v0.10.0`；测试版可使用 `v0.10.0-beta.1`，并自动标记为预发布。标签必须与 APK 的 `versionName` 完全一致（加上前缀 `v`），不匹配会中止发布；测试版也需先将 `versionName` 改为对应的 `0.10.0-beta.1`。

版本号以 `app/build.gradle.kts` 为准。完成开发并提交所有改动后，在 WSL / Linux 中运行发布脚本（需要 Git、Python 3、origin 推送权限及上述四项签名 Secrets）：

```bash
# 预览：默认补丁号 +1，versionCode +1，不修改文件或推送
./scripts/publish-release.py --dry-run

# 一键发布：例如 0.10.0 / 12 → 0.10.1 / 13
./scripts/publish-release.py

# 也可指定更高版本；预发布标签会被 Actions 标记为预发布
./scripts/publish-release.py 0.11.0
# ./scripts/publish-release.py 0.11.0-beta.1
```

脚本要求当前分支为 `main` 且工作区干净，检查本地/远端标签重复和远端 main 是否已包含在本地历史中。检查通过后只修改 Gradle 版本字段，创建发布提交和附注标签，再用 `git push --atomic` 一次推送 main 与新标签。`--dry-run` 会读取远端引用，但不修改文件、提交、标签或远端。脚本不读取签名密钥，实际构建、核验和两种 APK 发布由 Actions 完成；推送成功不等于 Release 已发布，需查看 Actions 结果。

脚本集成测试可通过 `python3 -B -m unittest discover -s scripts/tests -v` 运行，测试只操作临时本地仓库，不连接 GitHub；CI 会在构建前执行这些测试。

如果推送失败，脚本保留本地发布提交和标签，并输出重试推送命令。解决认证或远端分支冲突后按输出命令重试；不要直接再次运行版本递增命令，以免重复递增。若远端已有同名标签，应先核对其内容，不要强制覆盖。

重跑同一次标签工作流会更新该 Release 的附件，不改变标签。无需额外配置 `GITHUB_TOKEN`。构建成功不代表真机注入或朗读同步已经通过验证。

不要提交 APK、签名密钥、反编译树、书籍正文或设备日志。
