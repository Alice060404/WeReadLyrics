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
}
