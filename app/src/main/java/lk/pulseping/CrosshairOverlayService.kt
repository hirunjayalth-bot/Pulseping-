package lk.pulseping

import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class CrosshairOverlayService : Service() {
    private var overlay: CrosshairView? = null
    private var windowManager: WindowManager? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val style = intent?.getStringExtra(EXTRA_STYLE) ?: STYLE_CROSS
        val color = intent?.getIntExtra(EXTRA_COLOR, 0xFFFF334F.toInt()) ?: 0xFFFF334F.toInt()
        val size = intent?.getIntExtra(EXTRA_SIZE, 28) ?: 28
        if (overlay == null) {
            windowManager = getSystemService(WindowManager::class.java)
            overlay = CrosshairView(this)
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            windowManager?.addView(overlay, layoutParams)
        }
        overlay?.configure(style, color, size)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        overlay?.let { windowManager?.removeView(it) }
        overlay = null
        windowManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private class CrosshairView(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private var style = CrosshairOverlayService.STYLE_CROSS
        private var color = 0xFFFF334F.toInt()
        private var sizeDp = 28

        fun configure(style: String, color: Int, sizeDp: Int) {
            this.style = style
            this.color = color
            this.sizeDp = sizeDp.coerceIn(12, 72)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val density = resources.displayMetrics.density
            val radius = sizeDp * density / 2f
            val cx = width / 2f
            val cy = height / 2f
            paint.color = color
            paint.strokeWidth = 2f * density
            paint.alpha = 230
            when (style) {
                CrosshairOverlayService.STYLE_DOT -> {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy, 2.5f * density, paint)
                }
                CrosshairOverlayService.STYLE_CIRCLE -> {
                    paint.style = Paint.Style.STROKE
                    canvas.drawCircle(cx, cy, radius * 0.6f, paint)
                }
                else -> {
                    paint.style = Paint.Style.STROKE
                    val gap = 4f * density
                    canvas.drawLine(cx - radius, cy, cx - gap, cy, paint)
                    canvas.drawLine(cx + gap, cy, cx + radius, cy, paint)
                    canvas.drawLine(cx, cy - radius, cx, cy - gap, paint)
                    canvas.drawLine(cx, cy + gap, cx, cy + radius, paint)
                }
            }
        }
    }

    companion object {
        const val ACTION_STOP = "lk.pulseping.crosshair.STOP"
        const val EXTRA_STYLE = "style"
        const val EXTRA_COLOR = "color"
        const val EXTRA_SIZE = "size"
        const val STYLE_DOT = "Dot"
        const val STYLE_CROSS = "Cross"
        const val STYLE_CIRCLE = "Circle"
    }
}
