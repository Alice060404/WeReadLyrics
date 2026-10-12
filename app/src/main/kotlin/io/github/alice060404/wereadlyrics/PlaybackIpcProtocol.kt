package io.github.alice060404.wereadlyrics

internal object PlaybackIpcProtocol {
    const val STATUS_IDLE = 0
    const val STATUS_BUFFERING = 1
    const val STATUS_PLAYING = 2
    const val STATUS_PAUSED = 3
    const val MAX_TEXT_LENGTH = 4_096
}
