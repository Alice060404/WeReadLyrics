package io.github.alice060404.wereadlyrics.ipcprobe

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.util.Log
import io.github.alice060404.wereadlyrics.IPlaybackSink

class ProbeActivity : Activity() {
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            Log.i(LOG_TAG, "Connected from uid=${Process.myUid()}")
            IPlaybackSink.Stub.asInterface(service).publishState(
                SOURCE_INSTANCE_ID,
                1L,
                0L,
                STATUS_IDLE,
                "",
            )
            Log.i(LOG_TAG, "Probe transaction sent")
            window.decorView.postDelayed(::finish, FINISH_DELAY_MS)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.i(LOG_TAG, "Bridge disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent().setComponent(
            ComponentName(MODULE_PACKAGE, BRIDGE_SERVICE_CLASS),
        )
        bound = bindService(intent, connection, Context.BIND_AUTO_CREATE)
        Log.i(LOG_TAG, "Bind requested=$bound")
        if (!bound) finish()
    }

    override fun onDestroy() {
        if (bound) {
            unbindService(connection)
            bound = false
        }
        super.onDestroy()
    }

    private companion object {
        const val LOG_TAG = "WeReadLyricsProbe"
        const val MODULE_PACKAGE = "io.github.alice060404.wereadlyrics"
        const val BRIDGE_SERVICE_CLASS = "$MODULE_PACKAGE.PlaybackBridgeService"
        const val SOURCE_INSTANCE_ID = 0x49504350524f4245L
        const val STATUS_IDLE = 0
        const val FINISH_DELAY_MS = 1_000L
    }
}
