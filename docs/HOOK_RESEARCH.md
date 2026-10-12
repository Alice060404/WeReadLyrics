# 微信读书 10.2.2 Hook 调查

## 样本与方法

- 样本：一加 11 已安装的 `com.tencent.weread` 10.2.2（versionCode 10167650），仅有 `base.apk`。
- SHA-256：`e7af3231bf9ffb0d81d71bfc4ea88e9a5261cc754623ee8fd80863ef7846fd9b`。
- 工具：JADX 1.5.6、Android SDK `apkanalyzer`/`aapt2`。APK、反编译树和 DEX 清单只保存在被 Git 忽略的 `local-data/`。
- 限制：JADX 对约 15,365 个类报告 15,605 个错误，主要包含 Kotlin metadata 和复杂控制流问题。下列类与签名已用 `apkanalyzer dex packages/code` 从原始 DEX 复核；生成的 Java 仅用于阅读调用关系。

## 架构证据

书籍朗读使用微信读书自有的 `com.tencent.weread.tts`、PCM 数据源和 `AudioTrack` 播放链，APK 同时包含 `libweread-tts.so`。Android `TextToSpeech` 的直接引用位于阅读器和公众号页面的局部发音流程，方法名为 `playLocalTtsPronounce`，不能视为 AI 朗读主链。

已确认的静态调用关系：

```text
TrackPlayer.feedAudioTrack（读取并写入 PCM）
  -> PlayStateListener.stateChanged(STATE_ON_PROGRESS=8, Float)
  -> WXTTSPlayer / WXTTSOfflinePlayer 计算字符进度
  -> TTSCallBack.onProgress(utteranceId, charProgress)
  -> TTSPlayer.onProgress
  -> highLightTTSBag（将字符位置映射到 BagRange）
  -> TTSHighLightBroadcaster.startHighLight
  -> TTSReaderHighLightWatcher.ttsHighlight
```

## 候选探测点

以下定义均位于 `classes5.dex`。

| 候选 | 可观察信息 | 静态可信度 | 风险与待验证项 |
| --- | --- | --- | --- |
| `TTSHighLightBroadcaster.startHighLight(TTSBag, int, int): void` | 当前 bag、文本、utteranceId、高亮起止位置 | 已真机验证 | 实际发声时逐句触发；范围随播放推进，跳转或切章后重置 |
| `TTSHighLightBroadcaster.pauseHighLight(TTSBag): void` | 暂停事件 | 未观察到 | 实测暂停和停止均可能走 `stopHighLight` |
| `TTSHighLightBroadcaster.stopHighLight(TTSBag): void` | 停止或清除事件 | 已真机验证 | 表示高亮清除，不能单独区分暂停与最终停止 |
| `TTSPlayer.onStart/onProgress/onPause/onResume/onStop(String[, int]): void` | utterance 生命周期与字符进度 | 部分真机验证 | `onStop` 每个 utterance 结束时触发；pause/resume 未稳定出现，不适合作为产品控制状态 |
| `TTSBookPlayer.onProgress(String, int): void` | 书籍句内进度及章节累计位置 | 中高 | 是覆盖方法；同时 Hook 父类可能造成重复事件 |
| `TTSBookPlayer.start/pause/resume/stop/seekTo/foreAhead/foreBack` | 书籍朗读控制入口 | 已真机验证 | UI 操作可能组合或绕过同名方法；需按 stop+start、后续高亮和超时归一化 |

`TTSBag` 提供 `getText()`、`getUtteranceId()`、`getRanges()` 和 `getStart()`；`TTSBookBag` 还提供章节 UID、章节字符位置和队列索引。P3 默认只记录事件、长度、短哈希、范围及相对时间，不记录正文、书名或 bookId。

## P3 最小实验

三轮增量实验依次验证高亮广播、utterance 回调和书籍播放器控制入口；所有探针异常隔离且不修改参数、返回值或宿主控制流。最终时间轴及人工对照证明高亮 start 对应当前实际发声项，P4 采用该点作为文本事件源，并将控制调用视为需要归一化的提示信号。
