package io.github.alice060404.wereadlyrics

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Typeface
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView

/** A transparent two-line viewport over the complete sentence. */
internal class ScrollingSubtitleView(context: Context) : FrameLayout(context) {
    private val subtitle = TextView(context).apply {
        setTextColor(Color.rgb(245, 247, 250))
        textSize = 16f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        includeFontPadding = false
        setLineSpacing(3.dp.toFloat(), 1f)
        setShadowLayer(2.dp.toFloat(), 0f, 1.dp.toFloat(), Color.argb(240, 0, 0, 0))
        setPadding(10.dp, 0, 10.dp, 0)
        // Do not cap the text layout or ellipsize: the viewport supplies the height limit.
    }
    private var characterOffset = 0
    private var playing = true
    private var scrollAnimation: ValueAnimator? = null
    private var verticalOffset = 0

    val text: CharSequence get() = subtitle.text

    init {
        setPadding(0, 10.dp, 0, 10.dp)
        clipToPadding = false
        clipChildren = false
        addView(subtitle, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun update(text: String, offset: Int, playing: Boolean): Boolean {
        val textChanged = subtitle.text.toString() != text
        val nextOffset = offset.coerceIn(0, (text.length - 1).coerceAtLeast(0))
        val changed = textChanged || characterOffset != nextOffset || this.playing != playing
        if (!changed) return false
        scrollAnimation?.cancel()
        this.playing = playing
        characterOffset = nextOffset
        if (textChanged) {
            subtitle.text = text
            setOffset(0)
        } else {
            followPosition(animate = playing)
        }
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val screenLimit = (resources.displayMetrics.widthPixels * 0.88f).toInt()
        val widthLimit = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            screenLimit
        } else {
            minOf(screenLimit, MeasureSpec.getSize(widthMeasureSpec))
        }
        subtitle.minWidth = minOf(96.dp, widthLimit)
        subtitle.measure(
            MeasureSpec.makeMeasureSpec(widthLimit, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        val layout = subtitle.layout ?: return
        // Use actual line metrics (including fallback fonts), not an estimated font height.
        val contentHeight = layout.getLineTop(minOf(2, layout.lineCount))
        val desiredHeight = contentHeight + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(subtitle.measuredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        subtitle.layout(0, paddingTop, subtitle.measuredWidth, paddingTop + subtitle.measuredHeight)
        followPosition(animate = false)
    }

    override fun dispatchDraw(canvas: Canvas) {
        val checkpoint = canvas.save()
        canvas.clipRect(0, paddingTop, width, height - paddingBottom)
        canvas.translate(0f, -verticalOffset.toFloat())
        super.dispatchDraw(canvas)
        canvas.restoreToCount(checkpoint)
    }

    private fun setOffset(offset: Int) {
        verticalOffset = offset
        invalidate()
    }

    private fun followPosition(animate: Boolean) {
        val layout = subtitle.layout ?: return
        val line = layout.getLineForOffset(characterOffset.coerceAtMost(subtitle.text.length))
        // Keep the final two lines together and align to a complete line boundary.
        val target = layout.getLineTop(minOf(line, (layout.lineCount - 2).coerceAtLeast(0)))
        scrollAnimation?.cancel()
        if (!animate || target == verticalOffset) {
            setOffset(target)
            return
        }
        scrollAnimation = ValueAnimator.ofInt(verticalOffset, target).apply {
            duration = 160L
            interpolator = DecelerateInterpolator()
            addUpdateListener { setOffset(it.animatedValue as Int) }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        scrollAnimation?.cancel()
        super.onDetachedFromWindow()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
