package io.github.alice060404.wereadlyrics.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.github.alice060404.wereadlyrics.R
import kotlin.math.roundToInt

/** Replaces only the navigation control, retaining the current page and its content. */
class NavigationBarHostView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private var glassSettings = GlassSettings()
    private var glass: LiquidGlassNavigationView? = null
    private var material: BottomNavigationView? = null
    private var backdropSource: View? = null
    private var itemSelected: ((Int) -> Unit)? = null
    private var currentItem = R.id.nav_home
    private var currentStyle: NavigationStyle? = null

    var selectedItemId: Int
        get() = currentItem
        set(value) {
            if (value !in listOf(R.id.nav_home, R.id.nav_functions, R.id.nav_settings)) return
            currentItem = value
            glass?.selectedItemId = value
            material?.selectedItemId = value
        }

    init {
        clipChildren = false
        clipToPadding = false
    }

    internal fun setStyle(style: NavigationStyle) {
        if (style == currentStyle) return
        currentStyle = style
        removeAllViews()
        glass = null
        material = null
        val control = when (style) {
            NavigationStyle.LIQUID_GLASS -> LiquidGlassNavigationView(context).also { view ->
                view.applySettings(glassSettings)
                view.elevation = 8.dp.toFloat()
                view.selectedItemId = currentItem
                backdropSource?.let(view::setBackdropSource)
                view.setOnItemSelectedListener(::onItemSelected)
                glass = view
            }
            NavigationStyle.MATERIAL3 -> BottomNavigationView(context).also { view ->
                view.inflateMenu(R.menu.bottom_navigation)
                view.labelVisibilityMode = com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED
                // The style choice does not reintroduce the removed pressed ripple.
                view.itemRippleColor = null
                view.selectedItemId = currentItem
                view.setOnItemSelectedListener { item ->
                    onItemSelected(item.itemId)
                    true
                }
                material = view
            }
        }
        addView(control, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
    }

    fun setOnItemSelectedListener(listener: (Int) -> Unit) {
        itemSelected = listener
    }

    fun setBackdropSource(source: View) {
        backdropSource = source
        glass?.setBackdropSource(source)
    }

    internal fun applyGlassSettings(settings: GlassSettings) {
        glassSettings = settings.normalized()
        glass?.applySettings(glassSettings)
    }

    fun requestBackdropUpdate() {
        glass?.requestBackdropUpdate()
    }

    private fun onItemSelected(itemId: Int) {
        currentItem = itemId
        itemSelected?.invoke(itemId)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()
}
