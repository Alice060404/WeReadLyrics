package io.github.alice060404.wereadlyrics.core

class PlaybackStateMachine(
    private val preparedLimit: Int = DEFAULT_PREPARED_LIMIT,
) {
    private val prepared = LinkedHashMap<String, String>()
    private var state = PlaybackState()

    init {
        require(preparedLimit > 0) { "preparedLimit must be positive" }
    }

    fun snapshot(): PlaybackState = state

    fun accept(event: PlaybackEvent): PlaybackState {
        if (event.sequence < 0) return state
        if (event is SessionStarted) return startSession(event)
        if (event.sessionId != state.sessionId || event.sequence <= state.lastSequence) return state

        state = state.copy(lastSequence = event.sequence)
        when (event) {
            is TextPrepared -> prepare(event)
            is PlaybackStarted -> startPlayback(event)
            is RangeAdvanced -> advance(event)
            is PositionAdvanced -> {
                val current = state.current
                if (state.status == PlaybackStatus.PLAYING && current?.itemId == event.itemId &&
                    event.characterOffset in current.text.indices
                ) {
                    state = state.copy(current = current.copy(characterOffset = event.characterOffset))
                }
            }
            is Paused -> pause()
            is Resumed -> resume()
            is Seeked -> resetForDiscontinuity()
            is ChapterChanged -> changeChapter(event)
            is Stopped -> stop()
            is SessionStarted -> error("handled above")
        }
        return state
    }

    private fun startSession(event: SessionStarted): PlaybackState {
        val currentSession = state.sessionId
        if (event.sessionId <= 0 || (currentSession != null && event.sessionId <= currentSession)) return state

        prepared.clear()
        state = PlaybackState(
            sessionId = event.sessionId,
            lastSequence = event.sequence,
            status = PlaybackStatus.BUFFERING,
            chapterId = event.chapterId,
        )
        return state
    }

    private fun prepare(event: TextPrepared) {
        if (event.itemId.isBlank() || event.text.isEmpty()) return
        if (!prepared.containsKey(event.itemId) && prepared.size == preparedLimit) {
            prepared.remove(prepared.keys.first())
        }
        prepared[event.itemId] = event.text
        state = state.copy(preparedCount = prepared.size)
    }

    private fun startPlayback(event: PlaybackStarted) {
        if (!event.hasValidRange()) return
        val text = prepared.remove(event.itemId) ?: return
        state = state.copy(
            status = PlaybackStatus.PLAYING,
            current = DisplayItem(
                itemId = event.itemId,
                text = text,
                sourceStart = event.sourceStart,
                sourceEndInclusive = event.sourceEndInclusive,
            ),
            preparedCount = prepared.size,
        )
    }

    private fun advance(event: RangeAdvanced) {
        val current = state.current ?: return
        if (state.status != PlaybackStatus.PLAYING || current.itemId != event.itemId || !event.hasValidRange()) {
            return
        }
        state = state.copy(
            current = current.copy(
                sourceStart = event.sourceStart,
                sourceEndInclusive = event.sourceEndInclusive,
            ),
        )
    }

    private fun pause() {
        if (state.status != PlaybackStatus.IDLE) {
            state = state.copy(status = PlaybackStatus.PAUSED)
        }
    }

    private fun resume() {
        if (state.status == PlaybackStatus.PAUSED) {
            state = state.copy(
                status = if (state.current == null) PlaybackStatus.BUFFERING else PlaybackStatus.PLAYING,
            )
        }
    }

    private fun resetForDiscontinuity() {
        prepared.clear()
        state = state.copy(
            status = PlaybackStatus.BUFFERING,
            current = null,
            preparedCount = 0,
        )
    }

    private fun changeChapter(event: ChapterChanged) {
        resetForDiscontinuity()
        state = state.copy(chapterId = event.chapterId)
    }

    private fun stop() {
        prepared.clear()
        state = state.copy(
            status = PlaybackStatus.IDLE,
            current = null,
            preparedCount = 0,
        )
    }

    private fun PlaybackStarted.hasValidRange(): Boolean =
        sourceStart >= 0 && sourceEndInclusive >= sourceStart

    private fun RangeAdvanced.hasValidRange(): Boolean =
        sourceStart >= 0 && sourceEndInclusive >= sourceStart

    private companion object {
        const val DEFAULT_PREPARED_LIMIT = 8
    }
}
