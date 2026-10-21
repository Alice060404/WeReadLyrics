/*
 * Motion design adapted from AndroidLiquidGlass LiquidBottomTabs,
 * DampedDragAnimation and InteractiveHighlight. Copyright 2025 Kyant.
 * SPDX-License-Identifier: Apache-2.0
 * Modified for WeReadLyrics: native View layers and AndroidX springs.
 */
package io.github.alice060404.wereadlyrics.ui

import android.content.Context
import android.graphics.Rect
import android.graphics.Outline
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.util.TypedValue
import android.text.TextUtils
import android.view.Gravity
import android.view.MenuInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.color.MaterialColors
import com.google.android.material.textview.MaterialTextView
import io.github.alice060404.wereadlyrics.R
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * View adaptation of AndroidLiquidGlass' LiquidBottomTabs: a refractive glass
 * capsule, a spring-driven selection lens, and a draggable three-tab control.
 */
class LiquidGlassNavigationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private var settings = GlassSettings()
    private val surfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface)
    private val onSurfaceColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
    private val accentColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary)
    private val menu = PopupMenu(context, this).menu.apply {
        MenuInflater(context).inflate(R.menu.bottom_navigation, this)
    }
    private val items = (0 until menu.size()).map(menu::getItem)
    private val glass = GlassSurfaceView(context, surfaceColor, false)
    private val indicator = GlassSurfaceView(context, surfaceColor, true)
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(4.dp, 4.dp, 4.dp, 4.dp)
    }
    private val tabs = items.mapIndexed { index, item ->
        MaterialTextView(context).apply {
            id = item.itemId
            text = item.title
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            includeFontPadding = false
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            isFocusable = true
            compoundDrawablePadding = 2.dp
            item.icon?.mutate()?.let { icon ->
                icon.setBounds(0, 0, 24.dp, 24.dp)
                setCompoundDrawables(null, icon, null, null)
            }
            // Feedback is drawn by the glass, never by a Material pressed state.
            background = null
            foreground = null
            stateListAnimator = null
            defaultFocusHighlightEnabled = false
            var clickArmed = false
            setOnTouchListener { view, event ->
                val inside = event.x >= -touchSlop && event.x <= view.width + touchSlop &&
                    event.y >= -touchSlop && event.y <= view.height + touchSlop
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> clickArmed = true
                    MotionEvent.ACTION_MOVE -> if (!inside) clickArmed = false
                    MotionEvent.ACTION_CANCEL -> clickArmed = false
                    MotionEvent.ACTION_UP -> {
                        // Synchronous click: no deferred native pressed-state frame
                        // between releasing the finger and starting lens travel.
                        if (clickArmed && inside && !gestureCanceled) view.performClick()
                        clickArmed = false
                    }
                }
                true
            }
            setOnClickListener {
                if (!gestureCanceled) select(index, notify = true, animate = true)
            }
            setOnFocusChangeListener { _, focused ->
                focusedIndex = if (focused) index else if (focusedIndex == index) -1 else focusedIndex
                invalidate()
            }
            ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.RadioButton"
                    info.isCheckable = true
                    info.isChecked = index == selectedIndex
                    info.setCollectionItemInfo(
                        AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(
                            0, 1, index, 1, false, index == selectedIndex,
                        ),
                    )
                }
            })
        }
    }
    // An offscreen, accent-colored copy is refracted by the front selection lens.
    // It is never attached to the accessibility or touch hierarchy.
    private val lensRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(4.dp, 4.dp, 4.dp, 4.dp)
        items.forEach { item ->
            addView(MaterialTextView(context).apply {
                text = item.title
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                includeFontPadding = false
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                compoundDrawablePadding = 2.dp
                setTextColor(accentColor)
                item.icon?.constantState?.newDrawable()?.mutate()?.let { icon ->
                    icon.setBounds(0, 0, 24.dp, 24.dp)
                    icon.setTint(accentColor)
                    setCompoundDrawables(null, icon, null, null)
                }
                background = null
                stateListAnimator = null
            }, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
    }
    private val lensContent = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            glass.draw(canvas)
            lensRow.draw(canvas)
        }
    }
    private val position = FloatValueHolder(0f)
    private val pressure = FloatValueHolder(0f)
    private val panelOffset = FloatValueHolder(0f)
    private val positionSpring = SpringAnimation(position).apply {
        spring = SpringForce().setDampingRatio(1f).setStiffness(1000f)
        setMinimumVisibleChange(0.1f)
        addUpdateListener { _, _, velocity ->
            motionVelocity = velocity
            updateGlassMotion()
        }
        addEndListener { _, canceled, _, _ ->
            motionVelocity = 0f
            updateGlassMotion()
            if (!canceled) releaseWhenSettled()
        }
    }
    private val pressureSpring = SpringAnimation(pressure).apply {
        spring = SpringForce().setDampingRatio(1f).setStiffness(1000f)
        setMinimumVisibleChange(0.001f)
        addUpdateListener { _, _, _ -> updateGlassMotion() }
    }
    private val panelSpring = SpringAnimation(panelOffset).apply {
        spring = SpringForce().setDampingRatio(1f).setStiffness(300f)
        setMinimumVisibleChange(0.1f)
        addUpdateListener { _, _, _ -> updateGlassMotion() }
    }
    private val scaleXSpring = spring(indicator, DynamicAnimation.SCALE_X, 0.6f, 250f)
    private val scaleYSpring = spring(indicator, DynamicAnimation.SCALE_Y, 0.7f, 250f)
    private var motionVelocity = 0f
    private var fingerDown = false
    private var gestureCanceled = false
    private var pressStartedAt = 0L
    private var gestureStartPosition = 0f
    private var dragTarget = 0f
    private var renderingActive = false
    private var focusedIndex = -1
    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.dp.toFloat()
        color = accentColor
    }
    private val focusBounds = RectF()
    private val releasePress = Runnable { releaseWhenSettled() }
    private var selectedIndex = 0
    private var itemSelected: ((Int) -> Unit)? = null
    private var cellWidth = 0f
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var backdrop: GlassBackdrop? = null
    private var lensBackdrop: GlassBackdrop? = null
    private var pendingLensCapture: OneShotPreDrawListener? = null
    private val lensBounds = Rect()
    private var source: View? = null
    private var sourceObserver: ViewTreeObserver? = null
    private var pendingCapture: OneShotPreDrawListener? = null
    private val sourceLayoutListener = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        requestBackdropUpdate()
    }
    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { requestBackdropUpdate() }
    private val sourceLocation = IntArray(2)
    private val navigationLocation = IntArray(2)
    private val captureBounds = Rect()

    var selectedItemId: Int
        get() = items[selectedIndex].itemId
        set(value) {
            val index = items.indexOfFirst { it.itemId == value }
            if (index >= 0) select(index, notify = true, animate = isLaidOut)
        }

    init {
        clipChildren = false
        clipToPadding = false
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
            }
        }
        addView(glass, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(indicator, LayoutParams(0, 0).apply {
            // Translation is in physical coordinates, including in RTL layouts.
            gravity = Gravity.TOP or Gravity.LEFT
            leftMargin = 4.dp
            topMargin = 4.dp
        })
        tabs.forEach { row.addView(it, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)) }
        updateTabAppearance()
        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.setCollectionInfo(
                    AccessibilityNodeInfoCompat.CollectionInfoCompat.obtain(
                        1, items.size, false, AccessibilityNodeInfoCompat.CollectionInfoCompat.SELECTION_MODE_SINGLE,
                    ),
                )
            }
        })
    }

    internal fun applySettings(value: GlassSettings) {
        val safe = value.normalized()
        if (settings == safe) return
        settings = safe
        glass.applySettings(safe)
        indicator.applySettings(safe)
        requestLayout()
        requestBackdropUpdate()
    }

    fun setOnItemSelectedListener(listener: (Int) -> Unit) {
        itemSelected = listener
    }

    fun setBackdropSource(view: View) {
        removeSourceListeners()
        source = view
        if (isAttachedToWindow) addSourceListeners()
        requestBackdropUpdate()
    }

    /** Coalesced, on-demand captures; idle pages and spring frames aren't recaptured. */
    fun requestBackdropUpdate() {
        if (!renderingActive || width == 0 || height == 0 || pendingCapture != null) return
        pendingCapture = OneShotPreDrawListener.add(this) {
            pendingCapture = null
            captureBackdrop()
        }
        postInvalidateOnAnimation()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val navigationWidth = minOf(availableWidth, 420.dp)
        val textWidth = ((navigationWidth - 8.dp).toFloat() / items.size).coerceAtLeast(1f)
        val labelHeight = tabs.maxOf { tab ->
            val lines = ceil(tab.paint.measureText(tab.text.toString()) / textWidth).toInt().coerceIn(1, 2)
            val metrics = tab.paint.fontMetrics
            ceil((metrics.descent - metrics.ascent) * lines).toInt()
        }
        val navigationHeight = maxOf(settings.height.dp, 24.dp + 2.dp + labelHeight + 16.dp)
        val lensWidth = ((navigationWidth - 8.dp).toFloat() / items.size).roundToInt()
        val lensHeight = navigationHeight - 8.dp
        if (indicator.layoutParams.width != lensWidth || indicator.layoutParams.height != lensHeight) {
            indicator.layoutParams = (indicator.layoutParams as LayoutParams).apply {
                width = lensWidth
                height = lensHeight
            }
        }
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(navigationWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(navigationHeight, MeasureSpec.EXACTLY),
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val newCellWidth = (right - left - 8.dp).toFloat() / items.size
        val resized = newCellWidth != cellWidth || changed
        cellWidth = newCellWidth
        super.onLayout(changed, left, top, right, bottom)
        if (resized) {
            positionSpring.cancel()
            position.value = physicalIndex(selectedIndex) * cellWidth
        }
        lensRow.layoutDirection = layoutDirection
        lensRow.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        lensRow.layout(0, 0, width, height)
        lensContent.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        lensContent.layout(0, 0, width, height)
        updateGlassMotion()
        requestBackdropUpdate()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            gestureCanceled = false
            fingerDown = true
            pressStartedAt = SystemClock.uptimeMillis()
            removeCallbacks(releasePress)
            pressureSpring.animateToFinalPosition(1f)
        } else if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            gestureCanceled = true
            cancelGesture()
        }
        // Deliver UP first: the child's click starts the travel before release is considered.
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            fingerDown = false
            releaseWhenSettled()
        } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            cancelGesture()
        }
        return handled
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (gestureCanceled) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                gestureStartPosition = position.value
                dragTarget = gestureStartPosition
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > touchSlop && abs(event.x - downX) > abs(event.y - downY)) {
                    dragging = true
                    parent.requestDisallowInterceptTouchEvent(true)
                    updateDrag(event.x)
                }
            }
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gestureCanceled) return true
        if (!dragging || cellWidth <= 0f) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                updateDrag(event.x)
            }
            MotionEvent.ACTION_UP -> {
                // Commit the finger's target, not the spring's lagging position.
                updateDrag(event.x)
                performClick()
                dragging = false
                panelSpring.animateToFinalPosition(0f)
                parent.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        val target = if (dragging) dragTarget else position.value
        val physical = if (cellWidth > 0f) {
            (target / cellWidth).roundToInt().coerceIn(items.indices)
        } else {
            physicalIndex(selectedIndex)
        }
        select(logicalIndex(physical), notify = true, animate = true)
        return true
    }

    private fun cancelGesture() {
        fingerDown = false
        dragging = false
        positionSpring.animateToFinalPosition(physicalIndex(selectedIndex) * cellWidth)
        panelSpring.animateToFinalPosition(0f)
        parent?.requestDisallowInterceptTouchEvent(false)
        releaseWhenSettled()
    }

    private fun updateDrag(x: Float) {
        if (cellWidth <= 0f || width == 0) return
        val distance = x - downX
        // Preserve the grab offset, including when a previous travel is interrupted.
        dragTarget = (gestureStartPosition + distance).coerceIn(0f, cellWidth * items.lastIndex)
        positionSpring.animateToFinalPosition(dragTarget)
        val fraction = (distance / width).coerceIn(-1f, 1f)
        val eased = 1f - (1f - abs(fraction)) * (1f - abs(fraction))
        panelSpring.animateToFinalPosition(4.dp * kotlin.math.sign(fraction) * eased)
    }

    private fun releaseWhenSettled() {
        removeCallbacks(releasePress)
        if (fingerDown || positionSpring.isRunning) return
        val remaining = 120L - (SystemClock.uptimeMillis() - pressStartedAt)
        if (remaining > 0L) postDelayed(releasePress, remaining) else pressureSpring.animateToFinalPosition(0f)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (focusedIndex >= 0 && !isInTouchMode && cellWidth > 0f) {
            val x = 4.dp + physicalIndex(focusedIndex) * cellWidth
            focusBounds.set(x + 1.dp, 5.dp.toFloat(), x + cellWidth - 1.dp, height - 5.dp.toFloat())
            canvas.drawRoundRect(focusBounds, focusBounds.height() / 2f, focusBounds.height() / 2f, focusPaint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        renderingActive = true
        addSourceListeners()
        updateGlassMotion()
        requestBackdropUpdate()
    }

    override fun onDetachedFromWindow() {
        renderingActive = false
        pendingCapture?.removeListener()
        pendingCapture = null
        removeSourceListeners()
        pendingLensCapture?.removeListener()
        pendingLensCapture = null
        removeCallbacks(releasePress)
        positionSpring.cancel()
        pressureSpring.cancel()
        panelSpring.cancel()
        scaleXSpring.cancel()
        scaleYSpring.cancel()
        fingerDown = false
        dragging = false
        pressure.value = 0f
        panelOffset.value = 0f
        position.value = physicalIndex(selectedIndex) * cellWidth
        indicator.scaleX = 1f
        indicator.scaleY = 1f
        glass.backdrop = null
        indicator.backdrop = null
        backdrop?.release()
        backdrop = null
        lensBackdrop?.release()
        lensBackdrop = null
        super.onDetachedFromWindow()
    }

    private fun select(index: Int, notify: Boolean, animate: Boolean) {
        selectedIndex = index
        updateTabAppearance()
        val target = physicalIndex(index) * cellWidth
        if (animate) {
            removeCallbacks(releasePress)
            pressStartedAt = SystemClock.uptimeMillis()
            pressureSpring.animateToFinalPosition(1f)
            positionSpring.animateToFinalPosition(target)
        } else {
            positionSpring.cancel()
            position.value = target
            updateGlassMotion()
        }
        if (notify) itemSelected?.invoke(items[index].itemId)
        requestBackdropUpdate()
    }

    private fun updateGlassMotion() {
        if (!renderingActive || cellWidth <= 0f) return
        val progress = pressure.value.coerceIn(0f, 1f)
        val normalizedVelocity = if (cellWidth > 0f) motionVelocity / (cellWidth * items.lastIndex * 10f) else 0f
        val velocity = normalizedVelocity.coerceIn(-0.26f, 0.26f)
        val liftedScale = 1f + (78f / 56f - 1f) * progress
        scaleXSpring.animateToFinalPosition(liftedScale / (1f - velocity * 0.75f))
        scaleYSpring.animateToFinalPosition(liftedScale * (1f - velocity * 0.25f))
        val panelScale = 1f + 16.dp.toFloat() / width * progress
        glass.scaleX = panelScale
        glass.scaleY = panelScale
        row.scaleX = panelScale
        row.scaleY = panelScale
        glass.translationX = panelOffset.value
        row.translationX = panelOffset.value
        indicator.translationX = position.value + panelOffset.value
        indicator.sourceOffsetX = 4.dp + position.value
        indicator.sourceOffsetY = 4.dp.toFloat()
        glass.lightPositionX = 4.dp + position.value + cellWidth / 2f
        glass.interactionProgress = progress
        indicator.interactionProgress = progress
        indicator.invalidateBackdrop()
        for (index in 0 until lensRow.childCount) {
            lensRow.getChildAt(index).apply {
                scaleX = 1f + 0.2f * progress
                scaleY = 1f + 0.2f * progress
            }
        }
        requestLensUpdate()
    }

    private fun requestLensUpdate() {
        if (!renderingActive || width == 0 || height == 0 || pendingLensCapture != null) return
        pendingLensCapture = OneShotPreDrawListener.add(this) {
            pendingLensCapture = null
            captureLens()
        }
        postInvalidateOnAnimation()
    }

    private fun captureLens() {
        if (width == 0 || height == 0) return
        val snapshot = lensBackdrop ?: GlassBackdrop.create(isHardwareAccelerated).also { lensBackdrop = it }
        lensBounds.set(0, 0, width, height)
        snapshot.capture(lensContent, lensBounds, surfaceColor)
        indicator.backdrop = snapshot
    }

    private fun updateTabAppearance() {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            tab.isSelected = selected
            // Accent comes from the moving lens, not a tint that jumps ahead of it.
            tab.setTextColor(onSurfaceColor)
            tab.compoundDrawables[1]?.setTint(onSurfaceColor)
        }
    }

    private fun captureBackdrop() {
        val page = source ?: return
        if (page.width == 0 || page.height == 0 || !page.isAttachedToWindow) return
        page.getLocationInWindow(sourceLocation)
        getLocationInWindow(navigationLocation)
        val x = navigationLocation[0] - sourceLocation[0]
        val y = navigationLocation[1] - sourceLocation[1]
        captureBounds.set(x, y, x + width, y + height)
        val snapshot = backdrop ?: GlassBackdrop.create(isHardwareAccelerated).also { backdrop = it }
        snapshot.capture(page, captureBounds, surfaceColor)
        glass.backdrop = snapshot
        captureLens()
    }

    private fun addSourceListeners() {
        source?.let { page ->
            page.addOnLayoutChangeListener(sourceLayoutListener)
            sourceObserver = page.viewTreeObserver.also { it.addOnScrollChangedListener(scrollListener) }
        }
    }

    private fun removeSourceListeners() {
        source?.removeOnLayoutChangeListener(sourceLayoutListener)
        sourceObserver?.takeIf { it.isAlive }?.removeOnScrollChangedListener(scrollListener)
        sourceObserver = null
    }

    private fun physicalIndex(index: Int) = if (layoutDirection == LAYOUT_DIRECTION_RTL) items.lastIndex - index else index
    private fun logicalIndex(index: Int) = physicalIndex(index)

    private fun spring(view: View, property: DynamicAnimation.ViewProperty, damping: Float, stiffness: Float) =
        SpringAnimation(view, property).apply {
            spring = SpringForce().setDampingRatio(damping).setStiffness(stiffness)
        }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()
}
