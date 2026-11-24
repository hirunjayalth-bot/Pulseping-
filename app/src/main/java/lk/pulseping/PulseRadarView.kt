package lk.pulseping

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat

class PulseRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val bounds = RectF()
    private var signalColor = ContextCompat.getColor(context, R.color.neon_green)
    private var active = false
    private var phase = 0f
    private var animator: ValueAnimator? = null

    fun setSignalColor(color: Int) {
        signalColor = color
        invalidate()
    }

    fun setActive(isActive: Boolean) {
        if (active == isActive) return
        active = isActive
        animator?.cancel()
        animator = if (isActive && isAttachedToWindow) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 2400L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            null
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (active) setActive(false).also { setActive(true) }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = minOf(width, height) / 2f - 3f * density
        if (maxRadius <= 0f) return

        paint.color = signalColor
        paint.strokeWidth = 1.4f * density
        paint.alpha = if (active) 90 else 35
        canvas.drawCircle(centerX, centerY, maxRadius * 0.83f, paint)

        if (active) {
            for (wave in 0..1) {
                val progress = (phase + wave * 0.5f) % 1f
                paint.alpha = ((1f - progress) * 90).toInt()
                paint.strokeWidth = (2f - progress) * density
                canvas.drawCircle(centerX, centerY, maxRadius * (0.25f + progress * 0.7f), paint)
            }
            paint.alpha = 230
            paint.strokeWidth = 2f * density
            bounds.set(centerX - maxRadius, centerY - maxRadius, centerX + maxRadius, centerY + maxRadius)
            canvas.drawArc(bounds, phase * 360f, 105f, false, paint)
        }
    }
}
