package io.github.alice060404.wereadlyrics.ui

import android.os.Build
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView
import io.github.alice060404.wereadlyrics.R
import kotlin.math.roundToInt

/** Settings values stay independent of the selected navigation style. */
internal class GlassSettingsControls(
    private val container: LinearLayout,
    private val preferences: PersonalizationPreferences,
    private val onChanged: (GlassSettings) -> Unit,
) {
    private var settings = preferences.glassSettings
    private val refreshers = mutableListOf<() -> Unit>()

    init {
        addSlider(R.string.glass_height, 56, 96, true, true, { it.height }) { s, v -> s.copy(height = v) }
        addSlider(R.string.glass_blur, 0, 24, true, Build.VERSION.SDK_INT >= 31, { it.blur }) { s, v -> s.copy(blur = v) }
        addSlider(R.string.glass_opacity, 0, 100, false, true, { it.opacity }) { s, v -> s.copy(opacity = v) }
        addSlider(R.string.glass_refraction, 0, 200, false, Build.VERSION.SDK_INT >= 33, { it.refraction }) { s, v -> s.copy(refraction = v) }
        addSlider(R.string.glass_dispersion, 0, 200, false, Build.VERSION.SDK_INT >= 33, { it.dispersion }) { s, v -> s.copy(dispersion = v) }
        addSlider(R.string.glass_elasticity, 0, 100, false, true, { it.elasticity }) { s, v -> s.copy(elasticity = v) }
        container.addView(MaterialTextView(container.context).apply {
            setText(R.string.glass_elasticity_hint)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        })
        if (Build.VERSION.SDK_INT < 33) {
            container.addView(MaterialTextView(container.context).apply {
                setText(if (Build.VERSION.SDK_INT < 31) R.string.glass_legacy_hint else R.string.glass_blur_only_hint)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            })
        }
        container.addView(MaterialButton(container.context).apply {
            setText(R.string.glass_reset)
            setOnClickListener {
                publish(GlassSettings())
                refreshers.forEach { it() }
            }
        })
    }

    private fun publish(value: GlassSettings) {
        settings = value.normalized()
        preferences.glassSettings = settings
        onChanged(settings)
    }

    private fun addSlider(
        title: Int,
        minimum: Int,
        maximum: Int,
        dp: Boolean,
        supported: Boolean,
        read: (GlassSettings) -> Int,
        change: (GlassSettings, Int) -> GlassSettings,
    ) {
        val context = container.context
        val name = context.getString(title)
        val label = MaterialTextView(context).apply {
            id = View.generateViewId()
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            setPadding(0, (16 * resources.displayMetrics.density).roundToInt(), 0, 0)
        }
        val slider = Slider(context).apply {
            id = View.generateViewId()
            isSaveEnabled = false
            valueFrom = minimum.toFloat()
            valueTo = maximum.toFloat()
            stepSize = 1f
            value = read(settings).toFloat()
            isEnabled = supported
            contentDescription = name
            setLabelFormatter { value -> if (dp) "${value.roundToInt()} dp" else "${value.roundToInt()}%" }
        }
        label.labelFor = slider.id
        val updateLabel = {
            label.text = context.getString(if (dp) R.string.glass_value_dp else R.string.glass_value_percent, name, read(settings))
        }
        updateLabel()
        refreshers += {
            slider.value = read(settings).toFloat()
            updateLabel()
        }
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                publish(change(settings, value.roundToInt()))
                updateLabel()
            }
        }
        container.addView(label)
        container.addView(slider)
    }
}
