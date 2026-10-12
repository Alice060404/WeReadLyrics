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
