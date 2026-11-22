package lk.pulseping

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.max

class PingGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val samples = ArrayList<Int>()
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.border)
        strokeWidth = resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 5f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        alpha = 55
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f * resources.displayMetrics.density
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private var lineColor = ContextCompat.getColor(context, R.color.crimson)

    fun setSamples(values: List<Int>) {
        samples.clear()
        samples.addAll(values.takeLast(MAX_SAMPLES))
        invalidate()
    }

    fun setSignalColor(color: Int) {
        lineColor = color
        linePaint.color = color
        glowPaint.color = color
        glowPaint.alpha = 55
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val left = 2f * density
        val right = width - 2f * density
        val top = 5f * density
        val bottom = height - 5f * density
        if (right <= left || bottom <= top) return

        for (row in 1..3) {
            val y = top + (bottom - top) * row / 4f
            canvas.drawLine(left, y, right, y, gridPaint)
        }
        for (column in 1..5) {
            val x = left + (right - left) * column / 6f
            canvas.drawLine(x, top, x, bottom, gridPaint)
        }
        if (samples.isEmpty()) return

        val baseline = bottom - (bottom - top) * 0.16f
        val xStep = (right - left) / max(samples.size - 1, 1)
        path.reset()
        var hasPoint = false
        samples.forEachIndexed { index, sample ->
            val x = left + index * xStep
            if (sample < 0) {
                hasPoint = false
            } else {
                val magnitude = sample.coerceIn(0, MAX_PING_MS).toFloat() / MAX_PING_MS
                val y = bottom - magnitude * (bottom - top) * 0.82f
                if (!hasPoint) path.moveTo(x, y) else path.lineTo(x, y)
                hasPoint = true
            }
        }
        canvas.drawPath(path, glowPaint)
        canvas.drawPath(path, linePaint)
        canvas.drawLine(left, baseline, right, baseline, gridPaint)
    }

    companion object {
        private const val MAX_SAMPLES = 30
        private const val MAX_PING_MS = 400
    }
}
