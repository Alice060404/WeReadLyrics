package io.github.alice060404.wereadlyrics

internal data class PlaybackUpdate(
    val sourceInstanceId: Long,
    val sessionId: Long,
    val sequence: Long,
    val status: Int,
    val text: String,
)

internal object PlaybackUpdateBus {
    @Volatile
    var consumer: ((PlaybackUpdate) -> Unit)? = null

    fun publish(update: PlaybackUpdate) {
        consumer?.invoke(update)
    }
}
