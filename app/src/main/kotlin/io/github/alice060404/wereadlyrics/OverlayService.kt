package io.github.alice060404.wereadlyrics

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator

class OverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val updateLock = Any()
    private lateinit var windowManager: WindowManager
    private var overlayView: ScrollingSubtitleView? = null
    private var overlayTargetAlpha = 1f
    private var mockIndex = 0
    private var mockOffset = 0
    private var mode = Mode.OFF
    private var bridgeBound = false
    private var pendingUpdate: PlaybackUpdate? = null
    private var updatePosted = false
    private var lastSourceInstanceId: Long? = null
    private var lastSessionId: Long? = null
    private var lastSequence = Long.MIN_VALUE

    private val bridgeConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) = Unit
        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }

    private val mockTicker = object : Runnable {
        override fun run() {
            val text = getString(MOCK_LINES[mockIndex % MOCK_LINES.size])
            showOverlayText(text, characterOffset = mockOffset)
            mockOffset += 1
            if (mockOffset >= text.length + MOCK_TRAILING_TICKS) {
                mockIndex += 1
                mockOffset = 0
            }
            handler.postDelayed(this, MOCK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()
        PlaybackUpdateBus.consumer = ::enqueuePlaybackUpdate
        bridgeBound = bindService(
            Intent(this, PlaybackBridgeService::class.java),
            bridgeConnection,
            BIND_AUTO_CREATE,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_SHOW_MOCK -> startMockMode()
            ACTION_START_LIVE -> startLiveMode()
            ACTION_HIDE -> stopSelf()
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mode = Mode.OFF
        PlaybackUpdateBus.consumer = null
        handler.removeCallbacksAndMessages(null)
        hideOverlayView()
        if (bridgeBound) {
            runCatching { unbindService(bridgeConnection) }
            bridgeBound = false
        }
        super.onDestroy()
    }

    private fun startMockMode() {
        mode = Mode.MOCK
        startAsForeground(R.string.overlay_notification_mock)
        mockIndex = 0
        mockOffset = 0
        handler.removeCallbacks(mockTicker)
        mockTicker.run()
    }

    private fun startLiveMode() {
        mode = Mode.LIVE
        startAsForeground(R.string.overlay_notification_live)
        handler.removeCallbacks(mockTicker)
        hideOverlayView()
        resetOrdering()
    }

    private fun startAsForeground(notificationText: Int) {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val hideOverlay = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).setAction(ACTION_HIDE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, CHANNEL_ID)
        } else {
            android.app.Notification.Builder(this)
        }
        val notification = notificationBuilder
            .setSmallIcon(R.drawable.ic_module)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(notificationText))
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(
                android.app.Notification.Action.Builder(
                    null,
                    getString(R.string.overlay_notification_stop),
                    hideOverlay,
                ).build(),
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun enqueuePlaybackUpdate(update: PlaybackUpdate) {
        synchronized(updateLock) {
            pendingUpdate = update
            if (updatePosted) return
            updatePosted = true
        }
        handler.post(::consumeLatestPlaybackUpdate)
    }

    private fun consumeLatestPlaybackUpdate() {
        val update = synchronized(updateLock) {
            updatePosted = false
            pendingUpdate.also { pendingUpdate = null }
        } ?: return
        if (mode != Mode.LIVE || !acceptOrdering(update)) return

        val visualChanged = when (update.status) {
            PlaybackIpcProtocol.STATUS_PLAYING -> {
                if (update.text.isEmpty()) hideOverlayView() else {
                    showOverlayText(update.text, 1f, update.characterOffset)
                }
            }

            PlaybackIpcProtocol.STATUS_PAUSED -> {
                if (update.text.isEmpty()) hideOverlayView() else {
                    showOverlayText(update.text, PAUSED_ALPHA, update.characterOffset, playing = false)
                }
            }

            PlaybackIpcProtocol.STATUS_IDLE,
            PlaybackIpcProtocol.STATUS_BUFFERING,
            -> hideOverlayView()

            else -> false
        }
        if (!visualChanged) return
        Log.i(
            LOG_TAG,
            "Overlay applied: session=${update.sessionId}, seq=${update.sequence}, " +
                "status=${update.status}, textLength=${update.text.length}",
        )
    }

    private fun acceptOrdering(update: PlaybackUpdate): Boolean {
        if (lastSourceInstanceId != update.sourceInstanceId || lastSessionId != update.sessionId) {
            lastSourceInstanceId = update.sourceInstanceId
            lastSessionId = update.sessionId
            lastSequence = update.sequence
            return true
        }
        if (update.sequence <= lastSequence) return false
        lastSequence = update.sequence
        return true
    }

    private fun resetOrdering() {
        lastSourceInstanceId = null
        lastSessionId = null
        lastSequence = Long.MIN_VALUE
    }

    private fun showOverlayText(
        text: String,
        alpha: Float = 1f,
        characterOffset: Int = 0,
        playing: Boolean = true,
    ): Boolean {
        overlayView?.let { view ->
            val textChanged = view.text.toString() != text
            val alphaChanged = overlayTargetAlpha != alpha
            val contentChanged = view.update(text, characterOffset, playing)
            if (!textChanged && !alphaChanged) return contentChanged

            overlayTargetAlpha = alpha
            view.animate().cancel()
            if (textChanged) {
                view.alpha = 0f
                view.translationY = 3.dp.toFloat()
            }
            view.animate()
                .alpha(alpha)
                .translationY(0f)
                .setDuration(TEXT_TRANSITION_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
            return true
        }

        overlayTargetAlpha = alpha
        val view = ScrollingSubtitleView(this).apply {
            update(text, characterOffset, playing)
            this.alpha = alpha
        }
        val parameters = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences.getInt(PREF_X, 24.dp)
            y = preferences.getInt(PREF_Y, 120.dp)
        }
        installDragHandler(view, parameters)
        view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            // Longer text can widen the subtitle after it was dragged to an edge.
            val maxX = (resources.displayMetrics.widthPixels - view.width).coerceAtLeast(0)
            val maxY = (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
            val x = parameters.x.coerceIn(0, maxX)
            val y = parameters.y.coerceIn(0, maxY)
            if (parameters.x != x || parameters.y != y) {
                parameters.x = x
                parameters.y = y
                windowManager.updateViewLayout(view, parameters)
            }
        }

        windowManager.addView(view, parameters)
        overlayView = view
        return true
    }

    private fun hideOverlayView(): Boolean {
        val view = overlayView ?: return false
        view.animate().cancel()
        runCatching { windowManager.removeView(view) }
        overlayView = null
        return true
    }

    private fun installDragHandler(view: View, parameters: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = parameters.x
                    initialY = parameters.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val maxX = (resources.displayMetrics.widthPixels - view.width).coerceAtLeast(0)
                    val maxY = (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
                    parameters.x = (initialX + event.rawX - initialTouchX).toInt().coerceIn(0, maxX)
                    parameters.y = (initialY + event.rawY - initialTouchY).toInt().coerceIn(0, maxY)
                    windowManager.updateViewLayout(view, parameters)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    preferences.edit()
                        .putInt(PREF_X, parameters.x)
                        .putInt(PREF_Y, parameters.y)
                        .apply()
                    view.performClick()
                    true
                }

                else -> false
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private val preferences
        get() = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private enum class Mode {
        OFF,
        MOCK,
        LIVE,
    }

    companion object {
        private const val ACTION_SHOW_MOCK = "io.github.alice060404.wereadlyrics.action.SHOW_MOCK"
        private const val ACTION_START_LIVE = "io.github.alice060404.wereadlyrics.action.START_LIVE"
        private const val ACTION_HIDE = "io.github.alice060404.wereadlyrics.action.HIDE"
        private const val CHANNEL_ID = "overlay"
        private const val NOTIFICATION_ID = 1
        private const val MOCK_INTERVAL_MS = 150L
        private const val MOCK_TRAILING_TICKS = 8
        private const val TEXT_TRANSITION_MS = 180L
        private const val PAUSED_ALPHA = 0.65f
        private const val PREFERENCES_NAME = "overlay_position"
        private const val PREF_X = "x"
        private const val PREF_Y = "y"
        private const val LOG_TAG = "WeReadLyrics"

        private val MOCK_LINES = intArrayOf(
            R.string.mock_line_first,
            R.string.mock_line_drag,
            R.string.mock_line_long,
        )

        fun showMockIntent(context: Context): Intent =
            Intent(context, OverlayService::class.java).setAction(ACTION_SHOW_MOCK)

        fun startLiveIntent(context: Context): Intent =
            Intent(context, OverlayService::class.java).setAction(ACTION_START_LIVE)
    }
}
