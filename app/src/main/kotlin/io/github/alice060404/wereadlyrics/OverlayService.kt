package io.github.alice060404.wereadlyrics

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class OverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var overlayView: TextView? = null
    private var mockIndex = 0

    private val mockTicker = object : Runnable {
        override fun run() {
            overlayView?.setText(MOCK_LINES[mockIndex % MOCK_LINES.size])
            mockIndex += 1
            handler.postDelayed(this, MOCK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        showOverlay()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlayView?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        overlayView = null
        super.onDestroy()
    }

    private fun startAsForeground() {
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
            .setContentText(getString(R.string.overlay_notification_text))
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

    private fun showOverlay() {
        if (overlayView != null) return

        val view = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(18.dp, 12.dp, 18.dp, 12.dp)
            background = GradientDrawable().apply {
                setColor(Color.argb(220, 24, 31, 46))
                cornerRadius = 14.dp.toFloat()
            }
        }
        val parameters = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * OVERLAY_WIDTH_RATIO).toInt(),
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

        windowManager.addView(view, parameters)
        overlayView = view
        mockIndex = 0
        handler.removeCallbacks(mockTicker)
        mockTicker.run()
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

    companion object {
        private const val ACTION_SHOW_MOCK = "io.github.alice060404.wereadlyrics.action.SHOW_MOCK"
        private const val ACTION_HIDE = "io.github.alice060404.wereadlyrics.action.HIDE"
        private const val CHANNEL_ID = "overlay"
        private const val NOTIFICATION_ID = 1
        private const val MOCK_INTERVAL_MS = 3_000L
        private const val OVERLAY_WIDTH_RATIO = 0.88
        private const val PREFERENCES_NAME = "overlay_position"
        private const val PREF_X = "x"
        private const val PREF_Y = "y"

        private val MOCK_LINES = intArrayOf(
            R.string.mock_line_first,
            R.string.mock_line_drag,
            R.string.mock_line_long,
        )

        fun showMockIntent(context: Context): Intent =
            Intent(context, OverlayService::class.java).setAction(ACTION_SHOW_MOCK)
    }
}
