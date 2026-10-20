package io.github.alice060404.wereadlyrics

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.util.Log
import io.github.alice060404.wereadlyrics.core.TrustedCallerPolicy
import java.security.MessageDigest

class PlaybackBridgeService : Service() {
    private val sourceLock = Any()
    private var trackedSource: TrackedSource? = null
    private val callerPolicy = TrustedCallerPolicy(
        expectedPackage = TRUSTED_PACKAGE,
        expectedCertificateSha256 = TRUSTED_CERTIFICATE_SHA256,
    )

    private val playbackSink = object : IPlaybackSink.Stub() {
        override fun publishState(
            sourceInstanceId: Long,
            sessionId: Long,
            sequence: Long,
            status: Int,
            text: String,
            sourceToken: IBinder,
        ) {
            publishPositionedState(sourceInstanceId, sessionId, sequence, status, text, sourceToken, 0)
        }

        override fun publishPositionedState(
            sourceInstanceId: Long,
            sessionId: Long,
            sequence: Long,
            status: Int,
            text: String,
            sourceToken: IBinder,
            characterOffset: Int,
        ) {
            if (!isTrustedCaller()) return
            if (text.length > PlaybackIpcProtocol.MAX_TEXT_LENGTH) {
                Log.w(LOG_TAG, "IPC rejected: text exceeds limit")
                return
            }
            if (sessionId <= 0 || sequence < 0 || status !in VALID_STATUSES ||
                characterOffset < 0 || characterOffset > (text.length - 1).coerceAtLeast(0)
            ) {
                Log.w(LOG_TAG, "IPC rejected: invalid state")
                return
            }
            val update = PlaybackUpdate(
                sourceInstanceId = sourceInstanceId,
                sessionId = sessionId,
                sequence = sequence,
                status = status,
                text = text,
                characterOffset = characterOffset,
            )
            if (!publishTracked(update, sourceToken)) return
            Log.i(
                LOG_TAG,
                "IPC accepted: session=$sessionId, seq=$sequence, status=$status, " +
                    "textLength=${text.length}",
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder = playbackSink

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        synchronized(sourceLock) {
            trackedSource?.unlink()
            trackedSource = null
        }
        super.onDestroy()
    }

    private fun publishTracked(update: PlaybackUpdate, sourceToken: IBinder): Boolean =
        synchronized(sourceLock) {
            val current = trackedSource
            if (current?.token !== sourceToken || current.sourceInstanceId != update.sourceInstanceId) {
                current?.unlink()
                val deathRecipient = IBinder.DeathRecipient {
                    onSourceDied(update.sourceInstanceId, sourceToken)
                }
                try {
                    sourceToken.linkToDeath(deathRecipient, 0)
                } catch (_: Throwable) {
                    Log.w(LOG_TAG, "IPC source already unavailable")
                    return@synchronized false
                }
                trackedSource = TrackedSource(
                    sourceInstanceId = update.sourceInstanceId,
                    sessionId = update.sessionId,
                    sequence = update.sequence,
                    token = sourceToken,
                    deathRecipient = deathRecipient,
                )
            } else {
                trackedSource = current.copy(
                    sessionId = update.sessionId,
                    sequence = update.sequence,
                )
            }
            PlaybackUpdateBus.publish(update)
            true
        }

    private fun onSourceDied(sourceInstanceId: Long, sourceToken: IBinder) {
        val idleUpdate = synchronized(sourceLock) {
            val current = trackedSource
            if (current?.sourceInstanceId != sourceInstanceId || current.token !== sourceToken) return
            trackedSource = null
            PlaybackUpdate(
                sourceInstanceId = current.sourceInstanceId,
                sessionId = current.sessionId,
                sequence = current.sequence + 1,
                status = PlaybackIpcProtocol.STATUS_IDLE,
                text = "",
            )
        }
        PlaybackUpdateBus.publish(idleUpdate)
        Log.i(
            LOG_TAG,
            "IPC source disconnected: session=${idleUpdate.sessionId}, " +
                "seq=${idleUpdate.sequence}",
        )
    }

    private fun isTrustedCaller(): Boolean {
        val callingUid = Binder.getCallingUid()
        if (callingUid == Process.myUid()) return true

        val packages = packageManager.getPackagesForUid(callingUid)?.toSet().orEmpty()
        val digests = if (TRUSTED_PACKAGE in packages) {
            mapOf(TRUSTED_PACKAGE to certificateDigests(TRUSTED_PACKAGE))
        } else {
            emptyMap()
        }
        if (!callerPolicy.isTrusted(packages, digests)) {
            Log.w(LOG_TAG, "IPC rejected: uid=$callingUid")
            return false
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun certificateDigests(packageName: String): Set<String> {
        val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val info = packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            ).signingInfo ?: return emptySet()
            if (info.hasMultipleSigners()) {
                info.apkContentsSigners
            } else {
                info.signingCertificateHistory
            }
        } else {
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
        }
        return signatures.orEmpty().mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString(separator = "") { byte ->
                    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
                }
        }
    }

    private companion object {
        const val TRUSTED_PACKAGE = "com.tencent.weread"
        const val TRUSTED_CERTIFICATE_SHA256 =
            "e4dd2f049941989ffafa45aef52eda9701372c27bc1708e89c217935140bfa1c"
        const val LOG_TAG = "WeReadLyrics"
        val VALID_STATUSES = setOf(
            PlaybackIpcProtocol.STATUS_IDLE,
            PlaybackIpcProtocol.STATUS_BUFFERING,
            PlaybackIpcProtocol.STATUS_PLAYING,
            PlaybackIpcProtocol.STATUS_PAUSED,
        )
    }

    private data class TrackedSource(
        val sourceInstanceId: Long,
        val sessionId: Long,
        val sequence: Long,
        val token: IBinder,
        val deathRecipient: IBinder.DeathRecipient,
    ) {
        fun unlink() {
            runCatching { token.unlinkToDeath(deathRecipient, 0) }
        }
    }
}
