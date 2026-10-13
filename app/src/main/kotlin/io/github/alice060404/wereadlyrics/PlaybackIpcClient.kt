package io.github.alice060404.wereadlyrics

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import io.github.alice060404.wereadlyrics.core.PlaybackState
import io.github.alice060404.wereadlyrics.core.PlaybackStatus
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference

internal class PlaybackIpcClient(
    private val logger: (String) -> Unit,
) {
    private val sourceInstanceId = SecureRandom().nextLong()
    private val sourceToken = Binder()
    private val latestState = AtomicReference<PlaybackState>()
    private val lock = Any()

    @Volatile
    private var sink: IPlaybackSink? = null
    private var binding = false
    private var lastBindAttemptMs: Long? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            synchronized(lock) {
                sink = IPlaybackSink.Stub.asInterface(service)
                binding = false
            }
            logger("IPC connected")
            latestState.get()?.let(::send)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(lock) {
                sink = null
                binding = false
            }
            logger("IPC disconnected")
        }

        override fun onBindingDied(name: ComponentName?) {
            onServiceDisconnected(name)
        }

        override fun onNullBinding(name: ComponentName?) {
            onServiceDisconnected(name)
        }
    }

    fun connect(context: Context) {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            val retryTooSoon = lastBindAttemptMs?.let { lastAttempt ->
                now - lastAttempt < BIND_RETRY_MS
            } ?: false
            if (sink != null || binding || retryTooSoon) return
            lastBindAttemptMs = now
            binding = true
        }

        try {
            val intent = Intent().setClassName(MODULE_PACKAGE, BRIDGE_SERVICE_CLASS)
            val accepted = context.applicationContext.bindService(intent, connection, 0)
            if (!accepted) {
                synchronized(lock) { binding = false }
            }
            logger("IPC bind requested=$accepted")
        } catch (error: Throwable) {
            synchronized(lock) { binding = false }
            logger("IPC bind failed: error=${error.javaClass.name}")
        }
    }

    fun publish(state: PlaybackState) {
        latestState.set(state)
        send(state)
    }

    private fun send(state: PlaybackState) {
        val target = sink ?: return
        val sessionId = state.sessionId ?: return
        val text = state.current?.text.orEmpty().take(PlaybackIpcProtocol.MAX_TEXT_LENGTH)
        val status = when (state.status) {
            PlaybackStatus.IDLE -> PlaybackIpcProtocol.STATUS_IDLE
            PlaybackStatus.BUFFERING -> PlaybackIpcProtocol.STATUS_BUFFERING
            PlaybackStatus.PLAYING -> PlaybackIpcProtocol.STATUS_PLAYING
            PlaybackStatus.PAUSED -> PlaybackIpcProtocol.STATUS_PAUSED
        }
        try {
            target.publishState(
                sourceInstanceId,
                sessionId,
                state.lastSequence,
                status,
                text,
                sourceToken,
            )
        } catch (error: Throwable) {
            synchronized(lock) {
                sink = null
                binding = false
            }
            logger("IPC publish failed: error=${error.javaClass.name}")
        }
    }

    private companion object {
        const val MODULE_PACKAGE = "io.github.alice060404.wereadlyrics"
        const val BRIDGE_SERVICE_CLASS = "$MODULE_PACKAGE.PlaybackBridgeService"
        const val BIND_RETRY_MS = 2_000L
    }
}
