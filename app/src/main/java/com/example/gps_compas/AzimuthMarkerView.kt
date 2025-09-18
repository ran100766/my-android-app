package com.example.gps_compas

import android.R
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

data class Marker(
    val azimuth: Float = 0f,
    val color: Int = Color.MAGENTA,
    val radius: Float = 30f,
    val drawAtCenter: Boolean = false,
    val distance :Int = 0
)

class AzimuthMarkerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val markers = mutableListOf<Marker>()

    fun setMarkers(list: List<Marker>) {
        markers.clear()
        markers.addAll(list)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (width.coerceAtMost(height) / 2f) - 40f

        for (m in markers) {
            val paint = Paint().apply {
                color = m.color
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            val (x, y) = if (m.drawAtCenter) {
                centerX to centerY
            } else {
                val rad = Math.toRadians(m.azimuth.toDouble())
                centerX + radius * sin(rad).toFloat() to
                        centerY - radius * cos(rad).toFloat()
            }

            canvas.drawCircle(x, y, m.radius, paint)





            // ✅ Draw the distance text inside
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 40f  // adjust size as needed

            val distanceText = String.format("%d m", m.distance) // example: "49.8 m"
            canvas.drawText(distanceText, x, y + (paint.textSize / 3), paint)

        }
    }
}
