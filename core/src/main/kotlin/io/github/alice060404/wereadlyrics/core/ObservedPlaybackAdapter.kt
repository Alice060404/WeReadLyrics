package io.github.alice060404.wereadlyrics.core

class ObservedPlaybackAdapter(
    private val restartWindowMs: Long,
    private val idleStopTimeoutMs: Long,
) {
    private var nextSessionId = 1L
    private var sessionId: Long? = null
    private var sequence = 0L
    private var lastObservedAtMs = -1L
    private var currentItemId: String? = null
    private var currentSourceStart: Int? = null
    private var chapterId: String? = null
    private var pendingPauseAtMs: Long? = null
    private var pendingStopAtMs: Long? = null
    private var isPaused = false

    init {
        require(restartWindowMs >= 0) { "restartWindowMs must not be negative" }
        require(idleStopTimeoutMs > restartWindowMs) {
            "idleStopTimeoutMs must be greater than restartWindowMs"
        }
    }

    fun accept(observed: ObservedPlaybackEvent): List<PlaybackEvent> {
        if (observed.observedAtMs < lastObservedAtMs) return emptyList()

        val output = mutableListOf<PlaybackEvent>()
        expirePending(observed.observedAtMs, output)
        lastObservedAtMs = observed.observedAtMs

        when (observed) {
            is HighlightObserved -> acceptHighlight(observed, output)
            is HighlightCleared -> Unit
            is ControlObserved -> acceptControl(observed, output)
        }
        return output
    }

    fun flush(observedAtMs: Long): List<PlaybackEvent> {
        if (observedAtMs < lastObservedAtMs) return emptyList()
        val output = mutableListOf<PlaybackEvent>()
        expirePending(observedAtMs, output)
        lastObservedAtMs = observedAtMs
        return output
    }

    private fun acceptHighlight(observed: HighlightObserved, output: MutableList<PlaybackEvent>) {
        if (
            observed.itemId.isBlank() ||
            observed.text.isEmpty() ||
            observed.sourceStart < 0 ||
            observed.sourceEndInclusive < observed.sourceStart
        ) {
            return
        }
        ensureSession(observed.chapterId, output)
        val activeSession = sessionId ?: return

        if (observed.chapterId != null && observed.chapterId != chapterId) {
            emit(output) { eventSequence ->
                ChapterChanged(activeSession, eventSequence, observed.chapterId)
            }
            resetCurrent()
        }
        if (observed.chapterId != null) chapterId = observed.chapterId

        val movedBackward = currentSourceStart?.let { observed.sourceStart < it } == true
        if (movedBackward) {
            emit(output) { eventSequence -> Seeked(activeSession, eventSequence) }
            resetCurrent()
        }

        if (currentItemId == observed.itemId) {
            emit(output) { eventSequence ->
                RangeAdvanced(
                    activeSession,
                    eventSequence,
                    observed.itemId,
                    observed.sourceStart,
                    observed.sourceEndInclusive,
                )
            }
        } else {
            emit(output) { eventSequence ->
                TextPrepared(activeSession, eventSequence, observed.itemId, observed.text)
            }
            emit(output) { eventSequence ->
                PlaybackStarted(
                    activeSession,
                    eventSequence,
                    observed.itemId,
                    observed.sourceStart,
                    observed.sourceEndInclusive,
                )
            }
        }

        currentItemId = observed.itemId
        currentSourceStart = observed.sourceStart
        pendingStopAtMs = null
    }

    private fun acceptControl(observed: ControlObserved, output: MutableList<PlaybackEvent>) {
        when (observed.action) {
            ControlAction.START -> startOrRestart(observed.observedAtMs, output)
            ControlAction.PAUSE -> pause(observed.observedAtMs, output)
            ControlAction.RESUME -> resume(output)
            ControlAction.STOP -> if (sessionId != null) pendingStopAtMs = observed.observedAtMs
            ControlAction.SEEK,
            ControlAction.FORWARD,
            ControlAction.BACK,
            -> seek(output)
        }
    }

    private fun startOrRestart(observedAtMs: Long, output: MutableList<PlaybackEvent>) {
        val activeSession = sessionId
        val stopAt = pendingStopAtMs
        if (activeSession != null && stopAt != null && observedAtMs - stopAt <= restartWindowMs) {
            emit(output) { eventSequence -> Seeked(activeSession, eventSequence) }
            resetCurrent()
            pendingStopAtMs = null
            pendingPauseAtMs = null
            isPaused = false
            return
        }

        if (activeSession == null) {
            ensureSession(chapterId = null, output)
        } else if (pendingPauseAtMs != null) {
            emit(output) { eventSequence -> Resumed(activeSession, eventSequence) }
            pendingPauseAtMs = null
            isPaused = false
        }
    }

    private fun pause(observedAtMs: Long, output: MutableList<PlaybackEvent>) {
        val activeSession = sessionId ?: return
        if (!isPaused) {
            emit(output) { eventSequence -> Paused(activeSession, eventSequence) }
        }
        isPaused = true
        pendingPauseAtMs = observedAtMs
    }

    private fun resume(output: MutableList<PlaybackEvent>) {
        val activeSession = sessionId
        if (activeSession == null) {
            ensureSession(chapterId = null, output)
            return
        }
        if (!isPaused) return
        emit(output) { eventSequence -> Resumed(activeSession, eventSequence) }
        isPaused = false
        pendingPauseAtMs = null
        pendingStopAtMs = null
    }

    private fun seek(output: MutableList<PlaybackEvent>) {
        val activeSession = sessionId ?: return
        emit(output) { eventSequence -> Seeked(activeSession, eventSequence) }
        resetCurrent()
        isPaused = false
        pendingPauseAtMs = null
        pendingStopAtMs = null
    }

    private fun expirePending(observedAtMs: Long, output: MutableList<PlaybackEvent>) {
        val stopAt = pendingStopAtMs
        if (stopAt != null && observedAtMs - stopAt >= restartWindowMs) {
            stopSession(output)
            return
        }

        val pauseAt = pendingPauseAtMs
        if (pauseAt != null && observedAtMs - pauseAt >= idleStopTimeoutMs) {
            stopSession(output)
        }
    }

    private fun ensureSession(chapterId: String?, output: MutableList<PlaybackEvent>) {
        if (sessionId != null) return
        val newSessionId = nextSessionId++
        sessionId = newSessionId
        sequence = 0
        this.chapterId = chapterId
        emit(output) { eventSequence -> SessionStarted(newSessionId, eventSequence, chapterId) }
    }

    private fun stopSession(output: MutableList<PlaybackEvent>) {
        val activeSession = sessionId ?: return
        emit(output) { eventSequence -> Stopped(activeSession, eventSequence) }
        sessionId = null
        sequence = 0
        chapterId = null
        resetCurrent()
        isPaused = false
        pendingPauseAtMs = null
        pendingStopAtMs = null
    }

    private fun resetCurrent() {
        currentItemId = null
        currentSourceStart = null
    }

    private inline fun emit(
        output: MutableList<PlaybackEvent>,
        create: (sequence: Long) -> PlaybackEvent,
    ) {
        sequence += 1
        output += create(sequence)
    }
}
