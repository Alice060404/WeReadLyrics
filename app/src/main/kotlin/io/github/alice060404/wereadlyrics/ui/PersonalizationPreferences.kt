package io.github.alice060404.wereadlyrics.ui

import android.content.Context

internal enum class NavigationStyle(val preferenceValue: String) {
    LIQUID_GLASS("liquid_glass"),
    MATERIAL3("material3"),
    ;

    companion object {
        fun fromPreference(value: String?): NavigationStyle =
            entries.firstOrNull { it.preferenceValue == value } ?: LIQUID_GLASS
    }
}

internal class PersonalizationPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("personalization", Context.MODE_PRIVATE)

    var navigationStyle: NavigationStyle
        get() = NavigationStyle.fromPreference(preferences.getString("navigation_style", null))
        set(value) {
            preferences.edit().putString("navigation_style", value.preferenceValue).apply()
        }

    var glassSettings: GlassSettings
        get() = GlassSettings(
            height = preferences.getInt("glass_height", 64),
            blur = preferences.getInt("glass_blur", 8),
            opacity = preferences.getInt("glass_opacity", 40),
            refraction = preferences.getInt("glass_refraction", 100),
            dispersion = preferences.getInt("glass_dispersion", 100),
            elasticity = preferences.getInt("glass_elasticity", 100),
        ).normalized()
        set(value) {
            val safe = value.normalized()
            preferences.edit()
                .putInt("glass_height", safe.height)
                .putInt("glass_blur", safe.blur)
                .putInt("glass_opacity", safe.opacity)
                .putInt("glass_refraction", safe.refraction)
                .putInt("glass_dispersion", safe.dispersion)
                .putInt("glass_elasticity", safe.elasticity)
                .apply()
        }

}
