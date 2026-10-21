package io.github.alice060404.wereadlyrics

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.color.DynamicColors
import io.github.alice060404.wereadlyrics.ui.GlassSettingsControls
import io.github.alice060404.wereadlyrics.ui.NavigationBarHostView
import io.github.alice060404.wereadlyrics.ui.NavigationStyle
import io.github.alice060404.wereadlyrics.ui.PersonalizationPreferences

class MainActivity : AppCompatActivity() {
    private lateinit var permissionStatus: TextView
    private lateinit var environment: EnvironmentViewModel
    private lateinit var navigation: NavigationBarHostView
    private lateinit var personalizationPreferences: PersonalizationPreferences
    private var personalizationVisible = false
    private val personalizationBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            showPersonalization(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        onBackPressedDispatcher.addCallback(this, personalizationBack)
        personalizationPreferences = PersonalizationPreferences(this)
        personalizationVisible = savedInstanceState?.getBoolean(PERSONALIZATION_KEY, false) ?: false
        val root = findViewById<View>(R.id.main_content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
        listOf(
            R.id.home_title,
            R.id.functions_title,
            R.id.settings_title,
            R.id.personalization_title,
            R.id.navigation_style_title,
            R.id.framework_title,
            R.id.root_title,
            R.id.permission_title,
            R.id.playback_title,
            R.id.preview_title,
            R.id.setup_title,
        ).forEach { id -> ViewCompat.setAccessibilityHeading(findViewById(id), true) }
        permissionStatus = findViewById(R.id.permission_status)
        findViewById<Button>(R.id.overlay_permission).setOnClickListener { openOverlaySettings() }
        findViewById<Button>(R.id.start_live).setOnClickListener { startLiveOverlay() }
        findViewById<Button>(R.id.show_mock).setOnClickListener { showMockOverlay() }
        findViewById<Button>(R.id.hide_overlay).setOnClickListener { hideOverlay() }
        environment = ViewModelProvider(this)[EnvironmentViewModel::class.java]
        observeEnvironment()
        findViewById<Button>(R.id.check_root).setOnClickListener { environment.checkRoot() }
        findViewById<Button>(R.id.refresh_environment).setOnClickListener { refreshEnvironment() }
        navigation = findViewById(R.id.bottom_navigation)
        navigation.setBackdropSource(findViewById(R.id.page_content))
        navigation.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateNavigationClearance()
        }
        navigation.applyGlassSettings(personalizationPreferences.glassSettings)
        applyNavigationStyle(personalizationPreferences.navigationStyle)
        configurePersonalization()
        navigation.setOnItemSelectedListener { itemId ->
            showPage(itemId)
            if (itemId == R.id.nav_home) refreshEnvironment()
        }
        val selectedPage = savedInstanceState?.getInt(SELECTED_PAGE_KEY, R.id.nav_home) ?: R.id.nav_home
        navigation.selectedItemId = selectedPage
        showPage(navigation.selectedItemId)
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        refreshEnvironment()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(SELECTED_PAGE_KEY, navigation.selectedItemId)
        outState.putBoolean(PERSONALIZATION_KEY, personalizationVisible)
        super.onSaveInstanceState(outState)
    }

    private fun showPage(selectedId: Int) {
        if (selectedId != R.id.nav_settings) personalizationVisible = false
        listOf(
            R.id.nav_home to R.id.page_home,
            R.id.nav_functions to R.id.page_functions,
            R.id.nav_settings to R.id.page_settings,
        ).forEach { (navigationId, pageId) ->
            val visible = navigationId == selectedId && !(pageId == R.id.page_settings && personalizationVisible)
            findViewById<View>(pageId).visibility = if (visible) View.VISIBLE else View.GONE
        }
        findViewById<View>(R.id.page_personalization).visibility =
            if (selectedId == R.id.nav_settings && personalizationVisible) View.VISIBLE else View.GONE
        personalizationBack.isEnabled = selectedId == R.id.nav_settings && personalizationVisible
        navigation.requestBackdropUpdate()
    }

    private fun configurePersonalization() {
        GlassSettingsControls(findViewById<LinearLayout>(R.id.glass_parameters), personalizationPreferences) { settings ->
            navigation.applyGlassSettings(settings)
        }
        val entry = findViewById<View>(R.id.personalization_entry)
        ViewCompat.setScreenReaderFocusable(entry, true)
        entry.setOnClickListener { showPersonalization(true) }
        findViewById<View>(R.id.back_to_settings).setOnClickListener { showPersonalization(false) }
        findViewById<RadioGroup>(R.id.navigation_style_group).setOnCheckedChangeListener { _, checkedId ->
            val style = when (checkedId) {
                R.id.navigation_style_glass -> NavigationStyle.LIQUID_GLASS
                R.id.navigation_style_material -> NavigationStyle.MATERIAL3
                else -> return@setOnCheckedChangeListener
            }
            personalizationPreferences.navigationStyle = style
            applyNavigationStyle(style)
        }
    }

    private fun showPersonalization(visible: Boolean) {
        personalizationVisible = visible
        showPage(R.id.nav_settings)
    }

    private fun applyNavigationStyle(style: NavigationStyle) {
        navigation.setStyle(style)
        val glass = style == NavigationStyle.LIQUID_GLASS
        findViewById<View>(R.id.glass_parameters_card).visibility = if (glass) View.VISIBLE else View.GONE
        val density = resources.displayMetrics.density
        navigation.layoutParams = (navigation.layoutParams as FrameLayout.LayoutParams).apply {
            marginStart = if (glass) (24 * density).toInt() else 0
            marginEnd = if (glass) (24 * density).toInt() else 0
            bottomMargin = if (glass) (12 * density).toInt() else 0
        }
        val label = getString(if (glass) R.string.navigation_style_glass else R.string.navigation_style_material)
        findViewById<TextView>(R.id.personalization_summary).text = getString(R.string.navigation_style_summary, label)
        findViewById<TextView>(R.id.navigation_style_description).setText(
            if (glass) R.string.navigation_style_glass_description else R.string.navigation_style_material_description,
        )
        findViewById<RadioGroup>(R.id.navigation_style_group).check(
            if (glass) R.id.navigation_style_glass else R.id.navigation_style_material,
        )
        updateNavigationClearance()
    }

    private fun updateNavigationClearance() {
        val extra = if (personalizationPreferences.navigationStyle == NavigationStyle.LIQUID_GLASS) 36 else 16
        val clearance = navigation.height + (extra * resources.displayMetrics.density).toInt()
        listOf(R.id.page_home, R.id.page_functions, R.id.page_settings, R.id.page_personalization).forEach { id ->
            findViewById<View>(id).setPadding(0, 0, 0, clearance)
        }
        navigation.requestBackdropUpdate()
    }

    private fun refreshEnvironment() {
        val granted = Settings.canDrawOverlays(this)
        permissionStatus.text = if (granted) {
            getString(R.string.overlay_permission_granted)
        } else {
            getString(R.string.overlay_permission_missing)
        }
        findViewById<Button>(R.id.overlay_permission).setText(
            if (granted) R.string.manage_overlay_permission else R.string.grant_overlay_permission,
        )
        environment.refreshFramework(FrameworkConnection.services.value.orEmpty())
        navigation.requestBackdropUpdate()
    }

    private fun observeEnvironment() {
        FrameworkConnection.services.observe(this) { services -> environment.refreshFramework(services) }
        environment.root.observe(this) { state ->
            val text = when (state) {
                RootAccess.NOT_CHECKED -> R.string.root_not_checked
                RootAccess.CHECKING -> R.string.root_checking
                RootAccess.GRANTED -> R.string.root_granted
                RootAccess.NOT_GRANTED -> R.string.root_not_granted
                RootAccess.UNAVAILABLE -> R.string.root_unavailable
                RootAccess.TIMED_OUT -> R.string.root_timed_out
            }
            findViewById<TextView>(R.id.root_status).setText(text)
            findViewById<Button>(R.id.check_root).isEnabled = state != RootAccess.CHECKING
            navigation.requestBackdropUpdate()
        }
        environment.framework.observe(this) { status ->
            findViewById<TextView>(R.id.framework_status).text = when (status.state) {
                FrameworkState.CHECKING -> getString(R.string.framework_checking)
                FrameworkState.DISCONNECTED -> getString(R.string.framework_disconnected)
                FrameworkState.UNAVAILABLE -> getString(R.string.framework_unavailable)
                FrameworkState.CONNECTED -> if (status.api >= EnvironmentViewModel.REQUIRED_API) {
                    getString(R.string.framework_connected, status.name, status.version, status.api)
                } else {
                    getString(
                        R.string.framework_incompatible, status.name, status.api, EnvironmentViewModel.REQUIRED_API,
                    )
                }
            }
            findViewById<TextView>(R.id.scope_status).setText(
                when {
                    status.state != FrameworkState.CONNECTED -> R.string.scope_unknown
                    status.hasWeReadScope -> R.string.scope_enabled
                    else -> R.string.scope_missing
                },
            )
            findViewById<Button>(R.id.refresh_environment).isEnabled = status.state != FrameworkState.CHECKING
            navigation.requestBackdropUpdate()
        }
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

    private fun startLiveOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        val intent = OverlayService.startLiveIntent(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun hideOverlay() {
        stopService(Intent(this, OverlayService::class.java))
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST_CODE)
        }
    }

    private companion object {
        const val NOTIFICATION_REQUEST_CODE = 100
        const val SELECTED_PAGE_KEY = "selected_page"
        const val PERSONALIZATION_KEY = "personalization_visible"
    }
}
