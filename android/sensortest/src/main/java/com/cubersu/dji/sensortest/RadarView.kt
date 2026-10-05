package com.cubersu.dji.sensortest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Yatay engel mesafelerini kuşbakışı çizer.
 *
 * VARSAYIM (test ile doğrulanacak): 0. sektör drone'un önü, sektörler saat yönünde ilerler.
 * Ekranın üstü drone'un önüdür. Bu varsayımı doğrulamak test prosedürünün bir adımıdır.
 */
class RadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var snapshot: ObstacleMonitor.Snapshot? = null
        set(value) {
            field = value
            invalidate()
        }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.GRAY
    }
    private val sectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }
    private val dronePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val oval = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - 32f
        if (radius <= 0f) return

        val data = snapshot
        if (data != null && data.horizontalMm.isNotEmpty()) {
            val count = data.horizontalMm.size
            val sweep = 360f / count
            data.horizontalMm.forEachIndexed { index, mm ->
                val meters = mm / 1000f
                sectorPaint.color = colorFor(mm)
                // Dilim, engelin bulunduğu mesafeye kadar uzanır. Veri yoksa tam halka, soluk gri.
                val r = if (isValid(mm)) radius * (meters / MAX_RANGE_M).coerceIn(0.03f, 1f) else radius
                oval.set(cx - r, cy - r, cx + r, cy + r)
                // Canvas'ta 0° sağ taraf, -90° üst taraf. 0. sektörü üstte ortalıyoruz.
                val start = -90f - sweep / 2f + index * sweep
                canvas.drawArc(oval, start, sweep, true, sectorPaint)
            }
        }

        // 2 m ve 5 m halkaları, dış çember 10 m
        for (ringM in RINGS_M) {
            val r = radius * ringM / MAX_RANGE_M
            canvas.drawCircle(cx, cy, r, ringPaint)
        }
        canvas.drawCircle(cx, cy, radius, ringPaint)
        canvas.drawText("ÖN", cx, cy - radius - 4f, textPaint)
        canvas.drawCircle(cx, cy, 10f, dronePaint)
    }

    private fun colorFor(mm: Int): Int = when {
        !isValid(mm) -> Color.argb(80, 128, 128, 128)
        mm < 2_000 -> Color.argb(200, 220, 50, 50)
        mm < 5_000 -> Color.argb(200, 240, 150, 30)
        mm < 10_000 -> Color.argb(200, 230, 210, 40)
        else -> Color.argb(120, 60, 180, 90)
    }

    companion object {
        private const val MAX_RANGE_M = 10f
        private val RINGS_M = floatArrayOf(2f, 5f)

        /** 0 ve negatif değerler ile çok büyük değerler "veri yok / engel yok" kabul edilir. */
        fun isValid(mm: Int): Boolean = mm in 1 until 60_000
    }
}
