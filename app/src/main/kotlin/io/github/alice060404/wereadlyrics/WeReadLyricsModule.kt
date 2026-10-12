package io.github.alice060404.wereadlyrics

import android.app.Application
import android.annotation.SuppressLint
import android.os.Build
import android.os.SystemClock
import android.util.Log
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
                installHighlightProbes(param.defaultClassLoader)
            } else {
                log(Log.WARN, LOG_TAG, "Probe unavailable below Android 10")
            }
        }
    }

    @SuppressLint("NewApi")
    private fun installHighlightProbes(classLoader: ClassLoader) {
        try {
            val bagClass = Class.forName(TTS_BAG_CLASS, false, classLoader)
            val broadcasterClass = Class.forName(HIGHLIGHT_BROADCASTER_CLASS, false, classLoader)
            val playerClass = Class.forName(TTS_PLAYER_CLASS, false, classLoader)
            val textGetter = bagClass.getMethod("getText")

            installHighlightProbe(
                broadcasterClass.getDeclaredMethod(
                    "startHighLight",
                    bagClass,
                    Integer.TYPE,
                    Integer.TYPE,
                ),
                "start",
                textGetter,
            )
            installHighlightProbe(
                broadcasterClass.getDeclaredMethod("pauseHighLight", bagClass),
                "pause",
                textGetter,
            )
            installHighlightProbe(
                broadcasterClass.getDeclaredMethod("stopHighLight", bagClass),
                "stop",
                textGetter,
            )
            installLifecycleProbe(playerClass.getDeclaredMethod("onStart", String::class.java), "start")
            installLifecycleProbe(playerClass.getDeclaredMethod("onPause", String::class.java), "pause")
            installLifecycleProbe(playerClass.getDeclaredMethod("onResume", String::class.java), "resume")
            installLifecycleProbe(playerClass.getDeclaredMethod("onStop", String::class.java), "stop")

            log(
                Log.INFO,
                LOG_TAG,
                "Probe install complete: highlight start/pause/stop; player start/pause/resume/stop",
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
    private fun installHighlightProbe(method: Method, event: String, textGetter: Method) {
        hook(method)
            .setId("$MODULE_ID:highlight_$event")
            .intercept(
                XposedInterface.Hooker { chain ->
                    try {
                        logHighlightProbe(event, chain.args, textGetter)
                    } catch (error: Throwable) {
                        logObservationFailure("highlight_$event", error)
                    }
                    chain.proceed()
                },
            )
    }

    @SuppressLint("NewApi")
    private fun installLifecycleProbe(method: Method, event: String) {
        hook(method)
            .setId("$MODULE_ID:player_$event")
            .intercept(
                XposedInterface.Hooker { chain ->
                    try {
                        logLifecycleProbe(event, chain.args)
                    } catch (error: Throwable) {
                        logObservationFailure("player_$event", error)
                    }
                    chain.proceed()
                },
            )
    }

    private fun logHighlightProbe(event: String, args: List<Any?>, textGetter: Method) {
        val bag = args.firstOrNull()
        val text = bag?.let { textGetter.invoke(it) as? String }.orEmpty()
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
    }

    private fun logLifecycleProbe(event: String, args: List<Any?>) {
        val utteranceId = args.firstOrNull() as? String ?: ""
        val sequence = eventSequence.incrementAndGet()
        val elapsedMs = SystemClock.elapsedRealtime() - probeStartElapsedMs

        log(
            Log.INFO,
            LOG_TAG,
            "Probe event=player_$event, seq=$sequence, elapsedMs=$elapsedMs, " +
                "utteranceLength=${utteranceId.length}, utteranceToken=${utteranceId.sessionToken()}, " +
                "thread=${Thread.currentThread().name}",
        )
    }

    private fun logObservationFailure(event: String, error: Throwable) {
        log(
            Log.ERROR,
            LOG_TAG,
            "Probe observation failed: event=$event, error=${error.javaClass.name}",
        )
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
        const val TTS_PLAYER_CLASS = "com.tencent.weread.tts.TTSPlayer"
        const val HIGHLIGHT_BROADCASTER_CLASS = "com.tencent.weread.tts.TTSHighLightBroadcaster"
        const val TOKEN_BYTES = 6

        val probesInstalled = AtomicBoolean(false)
        val eventSequence = AtomicLong(0)
        val probeStartElapsedMs = SystemClock.elapsedRealtime()
        val sessionSalt = ByteArray(16).also(SecureRandom()::nextBytes)
    }
}
