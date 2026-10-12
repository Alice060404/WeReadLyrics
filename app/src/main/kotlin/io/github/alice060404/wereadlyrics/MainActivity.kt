package io.github.alice060404.wereadlyrics

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var permissionStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        permissionStatus.text = if (Settings.canDrawOverlays(this)) {
            getString(R.string.overlay_permission_granted)
        } else {
            getString(R.string.overlay_permission_missing)
        }
    }

    private fun buildContentView(): View {
        val spacing = 16.dp
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24.dp, 32.dp, 24.dp, 32.dp)
            setBackgroundColor(Color.WHITE)
        }

        content.addView(TextView(this).apply {
            setText(R.string.app_name)
            textSize = 26f
            setTextColor(Color.rgb(25, 31, 43))
        }, matchWrap().apply { bottomMargin = 8.dp })

        content.addView(TextView(this).apply {
            setText(R.string.screen_intro)
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(75, 85, 99))
        }, matchWrap().apply { bottomMargin = spacing })

        permissionStatus = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(25, 31, 43))
        }
        content.addView(permissionStatus, matchWrap().apply { bottomMargin = spacing })

        content.addView(actionButton(R.string.grant_overlay_permission) { openOverlaySettings() }, matchWrap())
        content.addView(actionButton(R.string.show_mock_overlay) { showMockOverlay() }, matchWrap())
        content.addView(actionButton(R.string.hide_mock_overlay) { hideMockOverlay() }, matchWrap())

        content.addView(TextView(this).apply {
            setText(R.string.screen_hint)
            textSize = 14f
            setTextColor(Color.rgb(107, 114, 128))
            gravity = Gravity.CENTER
        }, matchWrap().apply { topMargin = spacing })

        return content
    }

    private fun actionButton(label: Int, action: () -> Unit): Button = Button(this).apply {
        setText(label)
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun openOverlaySettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private fun showMockOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        val intent = OverlayService.showMockIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun hideMockOverlay() {
        stopService(Intent(this, OverlayService::class.java))
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST_CODE)
        }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    )

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private companion object {
        const val NOTIFICATION_REQUEST_CODE = 100
    }
}
