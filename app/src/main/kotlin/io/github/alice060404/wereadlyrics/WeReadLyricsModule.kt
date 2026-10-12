package io.github.alice060404.wereadlyrics

import android.app.Application
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import io.github.alice060404.wereadlyrics.core.ControlAction
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class WeReadLyricsModule : XposedModule() {
    private val ipcClient = PlaybackIpcClient { message -> log(Log.INFO, LOG_TAG, message) }
    private val syncObserver = SyncObserver(
        logger = { message -> log(Log.INFO, LOG_TAG, message) },
        stateSink = ipcClient::publish,
    )

    override fun onPackageLoaded(param: PackageLoadedParam) {
        if (param.packageName != TARGET_PACKAGE) return

        val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            TARGET_PACKAGE
        }

        log(
            Log.INFO,
            LOG_TAG,
            "Module loaded: package=${param.packageName}, process=$processName, " +
                "firstPackage=${param.isFirstPackage}",
        )

        if (processName == TARGET_PACKAGE && param.isFirstPackage && probesInstalled.compareAndSet(false, true)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                installProbes(param.defaultClassLoader)
            } else {
                log(Log.WARN, LOG_TAG, "Probe unavailable below Android 10")
            }
        }
    }

    @SuppressLint("NewApi")
    private fun installProbes(classLoader: ClassLoader) {
        try {
            val bagClass = Class.forName(TTS_BAG_CLASS, false, classLoader)
            val bookBagClass = Class.forName(TTS_BOOK_BAG_CLASS, false, classLoader)
            val broadcasterClass = Class.forName(HIGHLIGHT_BROADCASTER_CLASS, false, classLoader)
            val bookPlayerClass = Class.forName(TTS_BOOK_PLAYER_CLASS, false, classLoader)
            val accessors = HighlightAccessors(
                textGetter = bagClass.getMethod("getText"),
                utteranceGetter = bagClass.getMethod("getUtteranceId"),
                bookBagClass = bookBagClass,
                chapterGetter = bookBagClass.getMethod("getChapterUid"),
                applicationGetter = Class.forName(TINKER_APPLICATION_CLASS, false, classLoader)
                    .getMethod("getInstance"),
            )

            installHighlightProbe(
                broadcasterClass.getDeclaredMethod(
                    "startHighLight",
                    bagClass,
                    Integer.TYPE,
                    Integer.TYPE,
                ),
                "start",
                accessors,
            )
            installHighlightProbe(
                broadcasterClass.getDeclaredMethod("pauseHighLight", bagClass),
                "pause",
                accessors,
            )
            installHighlightProbe(
                broadcasterClass.getDeclaredMethod("stopHighLight", bagClass),
                "stop",
                accessors,
            )
            installControlProbe(bookPlayerClass.getDeclaredMethod("start"), "start")
            installControlProbe(bookPlayerClass.getDeclaredMethod("pause"), "pause")
            installControlProbe(bookPlayerClass.getDeclaredMethod("resume"), "resume")
            installControlProbe(bookPlayerClass.getDeclaredMethod("stop"), "stop")
            installControlProbe(bookPlayerClass.getDeclaredMethod("seekTo", java.lang.Long.TYPE), "seek")
            installControlProbe(bookPlayerClass.getDeclaredMethod("foreAhead"), "fore_ahead")
            installControlProbe(bookPlayerClass.getDeclaredMethod("foreBack"), "fore_back")

            log(
                Log.INFO,
                LOG_TAG,
                "Probe install complete: highlight start/pause/stop; " +
                    "book control start/pause/resume/stop/seek/fore_ahead/fore_back",
            )
        } catch (error: Throwable) {
            log(
                Log.ERROR,
                LOG_TAG,
                "Probe install failed: ${error.javaClass.name}",
            )
        }
    }

    @SuppressLint("NewApi")
    private fun installHighlightProbe(method: Method, event: String, accessors: HighlightAccessors) {
        hook(method)
            .setId("$MODULE_ID:highlight_$event")
            .intercept(
                XposedInterface.Hooker { chain ->
                    try {
                        logHighlightProbe(event, chain.args, accessors)
                    } catch (error: Throwable) {
                        logObservationFailure("highlight_$event", error)
                    }
                    chain.proceed()
                },
            )
    }

    @SuppressLint("NewApi")
    private fun installControlProbe(method: Method, event: String) {
        hook(method)
            .setId("$MODULE_ID:control_$event")
            .intercept(
                XposedInterface.Hooker { chain ->
                    try {
                        logControlProbe(event)
                    } catch (error: Throwable) {
                        logObservationFailure("control_$event", error)
                    }
                    chain.proceed()
                },
            )
    }

    private fun logHighlightProbe(event: String, args: List<Any?>, accessors: HighlightAccessors) {
        val bag = args.firstOrNull()
        val text = bag?.let { accessors.textGetter.invoke(it) as? String }.orEmpty()
        val utteranceId = bag?.let { accessors.utteranceGetter.invoke(it) as? String }.orEmpty()
        val range = if (event == "start") {
            "${args.getOrNull(1) as? Int}:${args.getOrNull(2) as? Int}"
        } else {
            "none"
        }
        val sequence = eventSequence.incrementAndGet()
        val elapsedMs = SystemClock.elapsedRealtime() - probeStartElapsedMs

        log(
            Log.INFO,
            LOG_TAG,
            "Probe event=$event, seq=$sequence, elapsedMs=$elapsedMs, " +
                "textLength=${text.length}, textToken=${text.sessionToken()}, range=$range, " +
                "thread=${Thread.currentThread().name}",
        )

        if (event == "start") {
            connectIpc(accessors)
            val sourceStart = args.getOrNull(1) as? Int ?: return
            val sourceEndInclusive = args.getOrNull(2) as? Int ?: return
            val chapterId = if (bag != null && accessors.bookBagClass.isInstance(bag)) {
                (accessors.chapterGetter.invoke(bag) as? Int)?.toString()
            } else {
                null
            }
            syncObserver.onHighlight(
                itemId = (utteranceId.ifEmpty { text }).sessionToken(),
                text = text,
                sourceStart = sourceStart,
                sourceEndInclusive = sourceEndInclusive,
                chapterId = chapterId,
            )
        } else {
            syncObserver.onHighlightCleared()
        }
    }

    private fun logControlProbe(event: String) {
        val sequence = eventSequence.incrementAndGet()
        val elapsedMs = SystemClock.elapsedRealtime() - probeStartElapsedMs

        log(
            Log.INFO,
            LOG_TAG,
            "Probe event=control_$event, seq=$sequence, elapsedMs=$elapsedMs, " +
                "thread=${Thread.currentThread().name}",
        )
        syncObserver.onControl(
            when (event) {
                "start" -> ControlAction.START
                "pause" -> ControlAction.PAUSE
                "resume" -> ControlAction.RESUME
                "stop" -> ControlAction.STOP
                "seek" -> ControlAction.SEEK
                "fore_ahead" -> ControlAction.FORWARD
                "fore_back" -> ControlAction.BACK
                else -> error("Unknown control event: $event")
            },
        )
    }

    private fun logObservationFailure(event: String, error: Throwable) {
        log(
            Log.ERROR,
            LOG_TAG,
            "Probe observation failed: event=$event, error=${error.javaClass.name}",
        )
    }

    private fun connectIpc(accessors: HighlightAccessors) {
        try {
            val context = accessors.applicationGetter.invoke(null) as? Context ?: return
            ipcClient.connect(context)
        } catch (error: Throwable) {
            log(
                Log.WARN,
                LOG_TAG,
                "IPC context unavailable: error=${error.javaClass.name}",
            )
        }
    }

    private fun String.sessionToken(): String {
        if (isEmpty()) return "empty"
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(sessionSalt)
        val hash = digest.digest(toByteArray(StandardCharsets.UTF_8))
        return hash.take(TOKEN_BYTES).joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.tencent.weread"
        const val LOG_TAG = "WeReadLyrics"
        const val MODULE_ID = "io.github.alice060404.wereadlyrics"
        const val TTS_BAG_CLASS = "com.tencent.weread.tts.model.TTSBag"
        const val TTS_BOOK_BAG_CLASS = "com.tencent.weread.tts.model.TTSBookBag"
        const val TTS_BOOK_PLAYER_CLASS = "com.tencent.weread.tts.TTSBookPlayer"
        const val HIGHLIGHT_BROADCASTER_CLASS = "com.tencent.weread.tts.TTSHighLightBroadcaster"
        const val TINKER_APPLICATION_CLASS = "com.tencent.tinker.loader.app.TinkerApplication"
        const val TOKEN_BYTES = 6

        val probesInstalled = AtomicBoolean(false)
        val eventSequence = AtomicLong(0)
        val probeStartElapsedMs = SystemClock.elapsedRealtime()
        val sessionSalt = ByteArray(16).also(SecureRandom()::nextBytes)
    }

    private data class HighlightAccessors(
        val textGetter: Method,
        val utteranceGetter: Method,
        val bookBagClass: Class<*>,
        val chapterGetter: Method,
        val applicationGetter: Method,
    )
}
