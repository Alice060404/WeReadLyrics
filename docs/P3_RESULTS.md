# P3 动态探测结果

## 第一轮结论：PARTIAL

设备为一加 11，微信读书 10.2.2，WeReadLyrics 0.2.0。用户执行启动朗读、暂停、继续、自然换句和停止，并确认发声、控制与宿主行为正常。第二轮使用实时 Logcat 捕获，避免系统日志滚动丢失。

| 相对时间 | 事件 | 文本长度 | 字符范围 | 线程 |
| ---: | --- | ---: | --- | --- |
| 0.000 s | start | 11 | 0:10 | `pool-14-thread-1` |
| 2.480 s | start | 15 | 12:26 | `pool-14-thread-1` |
| 6.723 s | start | 57 | 27:83 | `pool-14-thread-1` |
| 17.841 s | start | 32 | 84:115 | `pool-14-thread-1` |
| 24.038 s | stop | 0 | none | `WRCommonPool #2` |
| 31.939 s | stop | 0 | none | `WRCommonPool #1` |
| 32.604 s | start | 56 | 116:171 | `pool-14-thread-1` |
| 43.644 s | start | 35 | 172:206 | `pool-14-thread-1` |
| 48.580 s | stop | 0 | none | `WRCommonPool #4` |

各 start 使用不同的进程内随机盐短哈希；表中不保存哈希、正文、书名、bookId 或 utteranceId。用户人工确认内置高亮与实际语音一致，暂停期间高亮停止推进，继续后恢复，最终停止正常。范围连续递增且暂停区间约 8.6 秒没有 start 事件，支持高亮广播反映当前播放内容。

## 未解决问题

- 实测没有出现 `pauseHighLight`；暂停附近和最终停止均出现 `stopHighLight`，仅靠高亮广播不能可靠区分暂停、恢复前清理和最终停止。
- 尚未完成连续 2—3 分钟、跳句、切章、前后台和变速场景。
- 人工观察只能证明本轮样本的同步关系，不能给出毫秒级误差。

下一轮增加 `TTSPlayer.onStart/onPause/onResume/onStop` 只读生命周期探针，不增加高频 `onProgress` 日志。随后在同一设备补测上述场景，再给出最终 PASS/PARTIAL/FAIL。

## 第二轮结论：PARTIAL

WeReadLyrics 0.3.0 增加 `TTSPlayer.onStart/onPause/onResume/onStop` 回调探针。七个 Hook 均成功安装，用户再次执行播放约 20 秒、暂停、继续约 15 秒和停止，并报告发声、高亮和控制均正常。

实时日志中，高亮 `start` 的字符范围从 `0:10` 连续推进到 `84:115`；暂停附近出现高亮 `stop`，约 8.0 秒后以相同范围 `84:115` 恢复，最终停止后再次出现高亮 `stop`。没有出现 `player_pause` 或 `player_resume`。`player_stop` 在自然换句前反复出现，说明该回调表示单个 utterance 结束，不能表示用户停止。

反编译实现与实测一致：`TTSPlayer.onStop(String)` 刷新进度并调用 `nextBag()`；用户控制路径位于 `TTSBookPlayer.pause()/resume()/stop()` 及其父类实现。下一轮改为探测这些低频控制方法，回调探针只保留为诊断证据。

## 第三轮：控制映射通过，P3 仍为 PARTIAL

WeReadLyrics 0.4.0 改为探测 `TTSBookPlayer` 的低频控制入口。十个 Hook 均成功安装；用户执行开始、暂停、继续和停止后，确认发声、内置高亮和控制均正常。

| 相对时间 | 事件 | 说明 |
| ---: | --- | --- |
| 0.000 s | `control_start` | 用户开始朗读 |
| 2.069—8.777 s | 高亮 `start` | 范围从 `0:10` 推进至 `27:83` |
| 13.525 s | `control_pause` | 用户暂停 |
| 13.530 s | 高亮 `stop` | 暂停后清除高亮 |
| 22.651—22.653 s | `control_stop` + `control_start` | 用户继续，内部以重启播放实现 |
| 23.292 s | 高亮 `start` | 从 `84:115` 恢复推进 |
| 31.149 s | `control_pause` | 用户停止本轮朗读 |

控制方法名不能直接等同于产品状态：继续是相邻的 stop+start，暂停和本轮最终停止都可能调用 pause。后续同步核心需结合后续高亮、超时和会话边界归一化事件。P3 在完成连续播放、跳句、切章、前后台和变速样本前保持 `PARTIAL`。

## 最终结论：PASS

用户随后完成约 2 分钟综合样本，包括连续播放、前后跳、切换下一章、切到后台再返回、变速和停止，并确认每项发声与微信读书内置高亮均正常，无闪退、卡顿、重复播放或旧章节高亮残留。

探针记录 seq 14—67：正常播放时高亮范围连续递增；导航和章节变化期间出现多组 `control_stop` + `control_start`，随后两次从新文本 token 和 `0:*` 范围重新推进；返回稳定播放后范围从 `77:103` 连续推进至 `364:416`；最终 `control_pause` 后紧跟高亮 `stop`。未记录 `Probe observation failed`、`Probe install failed` 或崩溃。

这满足 P3 的硬门槛：`TTSHighLightBroadcaster.startHighLight` 提供的文本和字符范围属于实际播放进度链，并能在一加 11、微信读书 10.2.2 上关联当前发声项。精度结论限于人工观察，不能声称毫秒级误差；UI 操作不一定触发同名控制方法，P4 必须按事件组合、后续高亮和会话边界归一化状态。
