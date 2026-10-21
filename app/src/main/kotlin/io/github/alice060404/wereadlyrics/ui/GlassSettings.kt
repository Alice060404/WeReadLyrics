package io.github.alice060404.wereadlyrics.ui

/** User values in dp or percent. Clamp persisted values before measurement or shaders. */
internal data class GlassSettings(
    val height: Int = 64,
    val blur: Int = 8,
    val opacity: Int = 40,
    val refraction: Int = 100,
    val dispersion: Int = 100,
    val elasticity: Int = 100,
) {
    fun normalized() = copy(
        height = height.coerceIn(56, 96),
        blur = blur.coerceIn(0, 24),
        opacity = opacity.coerceIn(0, 100),
        refraction = refraction.coerceIn(0, 200),
        dispersion = dispersion.coerceIn(0, 200),
        elasticity = elasticity.coerceIn(0, 100),
    )
}
