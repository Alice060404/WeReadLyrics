package io.github.alice060404.wereadlyrics.core

enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
}

data class DisplayItem(
    val itemId: String,
    val text: String,
    val sourceStart: Int,
    val sourceEndInclusive: Int,
    val characterOffset: Int = 0,
)

data class PlaybackState(
    val sessionId: Long? = null,
    val lastSequence: Long = -1,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val chapterId: String? = null,
    val current: DisplayItem? = null,
    val preparedCount: Int = 0,
)
