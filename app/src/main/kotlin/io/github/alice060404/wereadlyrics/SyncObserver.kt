package io.github.alice060404.wereadlyrics

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.alice060404.wereadlyrics.core.ControlAction
import io.github.alice060404.wereadlyrics.core.ControlObserved
import io.github.alice060404.wereadlyrics.core.HighlightCleared
import io.github.alice060404.wereadlyrics.core.HighlightObserved
import io.github.alice060404.wereadlyrics.core.ObservedPlaybackAdapter
import io.github.alice060404.wereadlyrics.core.ObservedPlaybackEvent
import io.github.alice060404.wereadlyrics.core.PlaybackEvent
import io.github.alice060404.wereadlyrics.core.PlaybackStateMachine

internal class SyncObserver(
    private val logger: (String) -> Unit,
) {
    private val adapter = ObservedPlaybackAdapter(
        restartWindowMs = RESTART_WINDOW_MS,
        idleStopTimeoutMs = IDLE_STOP_TIMEOUT_MS,
    )
    private val stateMachine = PlaybackStateMachine()
    private val lock = Any()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun onHighlight(
        itemId: String,
        text: String,
        sourceStart: Int,
        sourceEndInclusive: Int,
        chapterId: String?,
    ) {
        accept(
            HighlightObserved(
                observedAtMs = SystemClock.elapsedRealtime(),
                itemId = itemId,
                text = text,
                sourceStart = sourceStart,
                sourceEndInclusive = sourceEndInclusive,
                chapterId = chapterId,
            ),
        )
    }

    fun onHighlightCleared() {
        accept(HighlightCleared(SystemClock.elapsedRealtime()))
    }

    fun onControl(action: ControlAction) {
        accept(ControlObserved(SystemClock.elapsedRealtime(), action))
        val delayMs = when (action) {
            ControlAction.PAUSE -> IDLE_STOP_TIMEOUT_MS
            ControlAction.STOP -> RESTART_WINDOW_MS
            else -> return
        }
        mainHandler.postDelayed(
            { safelyFlush() },
            delayMs,
        )
    }

    private fun accept(observed: ObservedPlaybackEvent) {
        val messages = synchronized(lock) {
            adapter.accept(observed).map(::reduceToLog)
        }
        messages.forEach(logger)
    }

    private fun flush() {
        val messages = synchronized(lock) {
            adapter.flush(SystemClock.elapsedRealtime()).map(::reduceToLog)
        }
        messages.forEach(logger)
    }

    private fun safelyFlush() {
        try {
            flush()
        } catch (error: Throwable) {
            try {
                logger("Sync flush failed: error=${error.javaClass.name}")
            } catch (_: Throwable) {
                // Delayed diagnostics must never crash the host main thread.
            }
        }
    }

    private fun reduceToLog(event: PlaybackEvent): String {
        val state = stateMachine.accept(event)
        val current = state.current
        val range = current?.let { "${it.sourceStart}:${it.sourceEndInclusive}" } ?: "none"
        return "Sync event=${event.javaClass.simpleName}, session=${event.sessionId}, " +
            "seq=${event.sequence}, status=${state.status}, textLength=${current?.text?.length ?: 0}, " +
            "itemToken=${current?.itemId ?: "none"}, range=$range"
    }

    private companion object {
        const val RESTART_WINDOW_MS = 1_000L
        const val IDLE_STOP_TIMEOUT_MS = 15_000L
    }
}
