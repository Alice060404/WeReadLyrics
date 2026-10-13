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
        ) {
            if (!isTrustedCaller()) return
            if (text.length > PlaybackIpcProtocol.MAX_TEXT_LENGTH) {
                throw SecurityException("Playback text exceeds IPC limit")
            }
            if (sessionId <= 0 || sequence < 0 || status !in VALID_STATUSES) {
                throw SecurityException("Invalid playback IPC state")
            }
            PlaybackUpdateBus.publish(
                PlaybackUpdate(
                    sourceInstanceId = sourceInstanceId,
                    sessionId = sessionId,
                    sequence = sequence,
                    status = status,
                    text = text,
                ),
            )
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
}
