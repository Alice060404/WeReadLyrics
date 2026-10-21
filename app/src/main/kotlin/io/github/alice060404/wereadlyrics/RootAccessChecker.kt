package io.github.alice060404.wereadlyrics

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit

internal enum class RootAccess {
    NOT_CHECKED, CHECKING, GRANTED, NOT_GRANTED, UNAVAILABLE, TIMED_OUT,
}

/** Only called after a user tap. No persistent root shell is retained. */
internal class RootAccessChecker(
    private val timeoutMillis: Long = 15_000,
    private val startProcess: () -> Process = {
        ProcessBuilder("su", "-c", "id -u").redirectErrorStream(true).start()
    },
) : Closeable {
    private val lock = Any()
    private var process: Process? = null
    private var closed = false

    fun check(): RootAccess {
        val command = try {
            synchronized(lock) {
                if (closed) return RootAccess.UNAVAILABLE
                startProcess().also { process = it }
            }
        } catch (_: IOException) {
            return RootAccess.UNAVAILABLE
        } catch (_: SecurityException) {
            return RootAccess.UNAVAILABLE
        }

        return try {
            if (!command.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                RootAccess.TIMED_OUT
            } else {
                val output = command.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(512)
                    val length = reader.read(buffer)
                    if (length > 0) String(buffer, 0, length).trim() else ""
                }
                if (command.exitValue() == 0 && output == "0") RootAccess.GRANTED else RootAccess.NOT_GRANTED
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            RootAccess.UNAVAILABLE
        } catch (_: IOException) {
            RootAccess.UNAVAILABLE
        } finally {
            command.destroyForcibly()
            synchronized(lock) {
                if (process === command) process = null
            }
        }
    }

    override fun close() = synchronized(lock) {
        closed = true
        process?.destroyForcibly()
        process = null
    }
}
