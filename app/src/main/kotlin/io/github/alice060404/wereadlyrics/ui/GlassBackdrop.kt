package io.github.alice060404.wereadlyrics.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.graphics.Outline
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils

/** Records only the strip behind the navigation, never the navigation itself. */
internal interface GlassBackdrop {
    fun capture(source: View, bounds: Rect, surfaceColor: Int)
    fun draw(canvas: Canvas)
    fun release()

    companion object {
        fun create(hardwareAccelerated: Boolean): GlassBackdrop =
            if (Build.VERSION.SDK_INT >= 29 && hardwareAccelerated) NodeBackdrop() else BitmapBackdrop()
    }
}

@RequiresApi(29)
private class NodeBackdrop : GlassBackdrop {
    private val node = RenderNode("WeReadLyrics navigation backdrop")

    override fun capture(source: View, bounds: Rect, surfaceColor: Int) {
        node.setPosition(0, 0, bounds.width(), bounds.height())
        val canvas = node.beginRecording()
        try {
            canvas.drawColor(surfaceColor)
            canvas.translate(-bounds.left.toFloat(), -bounds.top.toFloat())
            source.draw(canvas)
        } finally {
            node.endRecording()
        }
    }

    override fun draw(canvas: Canvas) {
        if (canvas.isHardwareAccelerated) canvas.drawRenderNode(node)
    }

    override fun release() = node.discardDisplayList()
}

private class BitmapBackdrop : GlassBackdrop {
    private var bitmap: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun capture(source: View, bounds: Rect, surfaceColor: Int) {
        val current = bitmap?.takeIf { it.width == bounds.width() && it.height == bounds.height() }
            ?: Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888).also { bitmap = it }
        Canvas(current).apply {
            drawColor(surfaceColor)
            translate(-bounds.left.toFloat(), -bounds.top.toFloat())
            source.draw(this)
        }
    }

    override fun draw(canvas: Canvas) {
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, paint) }
    }

    override fun release() {
        // Let the renderer finish using an uploaded bitmap before GC reclaims it.
        bitmap = null
    }
}

/** The selection lens sits above the labels and samples an accent-colored copy. */
internal class GlassSurfaceView(
    context: Context,
    private val surfaceColor: Int,
    private val isIndicator: Boolean,
) : FrameLayout(context) {
    var backdrop: GlassBackdrop? = null
        set(value) {
            field = value
            image.invalidate()
        }
    var sourceOffsetX = 0f
    var sourceOffsetY = 0f
    var interactionProgress = 0f
        set(value) {
            val progress = value.coerceIn(0f, 1f)
            if (kotlin.math.abs(field - progress) < 0.0001f) return
            field = progress
            if (isIndicator) elevation = 8f * resources.displayMetrics.density * field
            updateRefraction?.invoke(field)
            image.invalidate()
            invalidate()
        }
    var lightPositionX = 0f
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    private var updateRefraction: ((Float) -> Unit)? = null
    private val image = object : View(context) {
        private val fallback = Paint().apply { color = surfaceColor }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fallback)
            val checkpoint = canvas.save()
            canvas.translate(-sourceOffsetX, -sourceOffsetY)
            backdrop?.draw(canvas)
            canvas.restoreToCount(checkpoint)
        }
    }
    private val dark = ColorUtils.calculateLuminance(surfaceColor) < 0.5
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
    }
    private val innerShade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private val bounds = RectF()

    init {
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
            }
        }
        clipToOutline = true
        addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        val density = resources.displayMetrics.density
        updateRefraction = if (Build.VERSION.SDK_INT >= 33) {
            GlassEffects.install(image, w, h, density, isIndicator)
        } else {
            if (Build.VERSION.SDK_INT >= 31 && !isIndicator) {
                image.setRenderEffect(RenderEffect.createBlurEffect(8f * density, 8f * density, Shader.TileMode.CLAMP))
            }
            null
        }
        updateRefraction?.invoke(interactionProgress)
        rim.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.WHITE, Color.argb(20, 255, 255, 255), Color.argb(150, 255, 255, 255)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
        innerShade.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(), intArrayOf(Color.BLACK, Color.TRANSPARENT), null, Shader.TileMode.CLAMP,
        )
        invalidateOutline()
    }

    fun invalidateBackdrop() = image.invalidate()

    override fun onDrawForeground(canvas: Canvas) {
        val progress = interactionProgress
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        tint.color = if (isIndicator) {
            // The original's neutral selection surface fades as the lens lifts.
            ColorUtils.setAlphaComponent(if (dark) Color.WHITE else Color.BLACK, (25f * (1f - progress)).toInt())
        } else {
            ColorUtils.setAlphaComponent(surfaceColor, if (Build.VERSION.SDK_INT >= 31) 102 else 230)
        }
        canvas.drawRoundRect(bounds, height / 2f, height / 2f, tint)
        if (progress > 0.001f) {
            val x = if (isIndicator) width / 2f else lightPositionX.coerceIn(0f, width.toFloat())
            glow.shader = android.graphics.RadialGradient(
                x, height / 2f, height * 1.5f,
                intArrayOf(Color.argb((52f * progress).toInt(), 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawRoundRect(bounds, height / 2f, height / 2f, glow)
            if (isIndicator) {
                bounds.inset(innerShade.strokeWidth / 2f, innerShade.strokeWidth / 2f)
                innerShade.alpha = (35f * progress).toInt()
                canvas.drawRoundRect(bounds, bounds.height() / 2f, bounds.height() / 2f, innerShade)
                bounds.set(0f, 0f, width.toFloat(), height.toFloat())
            }
        }
        bounds.inset(rim.strokeWidth / 2f, rim.strokeWidth / 2f)
        rim.alpha = if (isIndicator) (180f * progress).toInt() else 128
        canvas.drawRoundRect(bounds, bounds.height() / 2f, bounds.height() / 2f, rim)
        super.onDrawForeground(canvas)
    }
}

/** Mutate uniforms on cached shaders; no shader recompilation on animation frames. */
@RequiresApi(33)
private object GlassEffects {
    fun install(image: View, width: Int, height: Int, density: Float, indicator: Boolean): (Float) -> Unit {
        val shader = RuntimeShader(
            if (indicator) RoundedRectRefractionWithDispersionShaderString else RoundedRectRefractionShaderString,
        ).apply {
            setFloatUniform("size", width.toFloat(), height.toFloat())
            setFloatUniform("offset", 0f, 0f)
            val radius = minOf(width, height) / 2f
            setFloatUniform("cornerRadii", radius, radius, radius, radius)
            setFloatUniform("depthEffect", 0f)
            setFloatUniform("refractionHeight", if (indicator) 0.01f else minOf(24f * density, radius))
            setFloatUniform("refractionAmount", if (indicator) 0f else -24f * density)
            if (indicator) setFloatUniform("chromaticAberration", 1f)
        }
        val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
        val effect = if (indicator) {
            // The sampled bar is already blurred. Keep the copied glyphs sharp.
            lens
        } else {
            val saturation = android.graphics.ColorMatrix().apply { setSaturation(1.5f) }
            val vibrant = RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(saturation))
            val blur = RenderEffect.createBlurEffect(8f * density, 8f * density, Shader.TileMode.CLAMP)
            RenderEffect.createChainEffect(lens, RenderEffect.createChainEffect(blur, vibrant))
        }
        image.setRenderEffect(effect)
        return { progress ->
            if (indicator) {
                shader.setFloatUniform("refractionHeight", (minOf(10f * density, height / 2f) * progress).coerceAtLeast(0.01f))
                shader.setFloatUniform("refractionAmount", -14f * density * progress)
                // Image filters snapshot uniforms. Rebind the filter while reusing
                // the compiled RuntimeShader, so pressure actually changes the lens.
                image.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"))
            }
        }
    }
}
