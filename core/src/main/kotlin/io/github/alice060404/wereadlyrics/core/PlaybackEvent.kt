package io.github.alice060404.wereadlyrics.core

sealed interface PlaybackEvent {
    val sessionId: Long
    val sequence: Long
}

data class SessionStarted(
    override val sessionId: Long,
    override val sequence: Long,
    val chapterId: String? = null,
) : PlaybackEvent

data class TextPrepared(
    override val sessionId: Long,
    override val sequence: Long,
    val itemId: String,
    val text: String,
) : PlaybackEvent

data class PlaybackStarted(
    override val sessionId: Long,
    override val sequence: Long,
    val itemId: String,
    val sourceStart: Int,
    val sourceEndInclusive: Int,
) : PlaybackEvent

data class RangeAdvanced(
    override val sessionId: Long,
    override val sequence: Long,
    val itemId: String,
    val sourceStart: Int,
    val sourceEndInclusive: Int,
) : PlaybackEvent

data class Paused(
    override val sessionId: Long,
    override val sequence: Long,
) : PlaybackEvent

data class PositionAdvanced(
    override val sessionId: Long,
    override val sequence: Long,
    val itemId: String,
    val characterOffset: Int,
) : PlaybackEvent

data class Resumed(
    override val sessionId: Long,
    override val sequence: Long,
) : PlaybackEvent

data class Seeked(
    override val sessionId: Long,
    override val sequence: Long,
) : PlaybackEvent

data class ChapterChanged(
    override val sessionId: Long,
    override val sequence: Long,
    val chapterId: String,
) : PlaybackEvent

data class Stopped(
    override val sessionId: Long,
    override val sequence: Long,
) : PlaybackEvent
