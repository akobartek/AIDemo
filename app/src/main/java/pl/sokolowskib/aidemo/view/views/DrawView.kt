package pl.sokolowskib.aidemo.view.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import pl.sokolowskib.aidemo.data.DetectionResult

class DrawView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val rectPaint =
        Paint().apply {
            isAntiAlias = true
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }

    private val results: MutableList<DetectionResult> = ArrayList()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        synchronized(this) {
            for (entry in results) {
                var red = (255 * (entry.confidence * (-4) + 4)).toInt()
                if (red < 0) red = 0 else if (red > 255) red = 255
                var green = (255 * (entry.confidence * 4 - 2)).toInt()
                if (green < 0) green = 0 else if (green > 255) green = 255
                rectPaint.color = Color.rgb(red, green, 0)
                canvas.drawRect(entry.rectF, rectPaint)
            }
        }
    }

    fun setTargets(sources: List<DetectionResult>) {
        synchronized(this) {
            results.clear()
            results.addAll(sources)
            this.postInvalidate()
        }
    }
}