package io.github.alice060404.wereadlyrics.core

sealed interface ObservedPlaybackEvent {
    val observedAtMs: Long
}

data class HighlightObserved(
    override val observedAtMs: Long,
    val itemId: String,
    val text: String,
    val sourceStart: Int,
    val sourceEndInclusive: Int,
    val chapterId: String? = null,
) : ObservedPlaybackEvent

data class HighlightCleared(
    override val observedAtMs: Long,
) : ObservedPlaybackEvent

data class CharacterProgressObserved(
    override val observedAtMs: Long,
    val itemId: String,
    val characterOffset: Int,
) : ObservedPlaybackEvent

enum class ControlAction {
    START,
    PAUSE,
    RESUME,
    STOP,
    SEEK,
    FORWARD,
    BACK,
}

data class ControlObserved(
    override val observedAtMs: Long,
    val action: ControlAction,
) : ObservedPlaybackEvent
