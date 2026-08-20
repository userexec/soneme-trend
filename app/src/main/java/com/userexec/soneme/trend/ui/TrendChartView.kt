package com.userexec.soneme.trend.ui

import android.content.Context
import android.graphics.*
import android.view.View
import com.userexec.soneme.trend.model.ChartRange
import com.userexec.soneme.trend.model.ChartSeries
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

class TrendChartView(context: Context) : View(context) {
    data class Model(
        val series: List<ChartSeries>,
        val selectedSeries: Int = 0,
        val selectedPoint: Int? = null,
        val correlation: Boolean = false,
        val xMin: Double = 0.0,
        val xMax: Double = 1.0,
        val range: ChartRange? = null
    )

    var model: Model? = null
        set(value) { field = value; invalidate() }

    private val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        strokeWidth = 1.5f
        textSize = 13.5f * context.resources.displayMetrics.scaledDensity
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.4f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val point = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0,255,0); strokeWidth = 1.5f }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val m = model ?: return
        if (m.series.isEmpty()) return
        val density = resources.displayMetrics.density
        val left = 40f * density; val right = width - 6f * density; val top = 7f * density; val bottom = height - 20f * density
        if (right <= left || bottom <= top) return
        val minX = m.xMin
        var maxX = m.xMax
        if (maxX <= minX) maxX = minX + 1.0
        val scales = m.series.map { s -> yRange(s) }
        val selected = m.selectedSeries.coerceIn(0, m.series.lastIndex)
        val selectedScale = scales[selected]

        fun xp(x: Double) = left + ((x - minX) / (maxX - minX) * (right - left)).toFloat()
        fun yp(y: Double, scale: Pair<Double,Double>): Float {
            val (lo, hi) = scale
            return bottom - ((y - lo) / (hi - lo) * (bottom - top)).toFloat()
        }

        canvas.drawLine(left, top, left, bottom, axis); canvas.drawLine(left, bottom, right, bottom, axis)
        if (isFocused && !m.correlation) {
            val glowWidth = (right - left) * 0.05f
            val blue = UiFactory.BLUE
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    left, 0f, left + glowWidth, 0f,
                    Color.argb(128, Color.red(blue), Color.green(blue), Color.blue(blue)),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(left, top, left + glowWidth, bottom, glow)
        }
        val tickDivisions = when (m.range) {
            ChartRange.LAST_YEAR -> 12
            ChartRange.LAST_MONTH -> 4
            ChartRange.LAST_WEEK -> 7
            ChartRange.LAST_DAY -> 4
            ChartRange.LAST_HOUR -> 6
            else -> 0
        }
        if (tickDivisions > 0) {
            for (i in 1 until tickDivisions) {
                val x = left + (right - left) * i / tickDivisions.toFloat()
                canvas.drawLine(x, bottom, x, bottom - 4f, axis)
            }
        }
        val (lo, hi) = selectedScale
        for (i in 0..3) {
            val v = lo + (hi - lo) * i / 3.0
            val y = yp(v, selectedScale)
            canvas.drawLine(left - 3, y, left, y, axis)
            canvas.drawText(formatAxis(v), 1f * density, y + 4f * density, axis)
        }

        val save = canvas.save()
        canvas.clipRect(left, top, right, bottom)
        val drawOrder = m.series.indices.sortedBy { if (it == selected) 1 else 0 }
        drawOrder.forEach { si ->
            val s = m.series[si]; val scale = scales[si]
            if (s.points.size < 2) return@forEach
            val path = Path()
            s.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(xp(p.x), yp(p.value, scale)) else path.lineTo(xp(p.x), yp(p.value, scale)) }
            if (!m.correlation) {
                val fillPath = Path(path)
                fillPath.lineTo(xp(s.points.last().x), bottom); fillPath.lineTo(xp(s.points.first().x), bottom); fillPath.close()
                fill.color = Color.argb(102, Color.red(s.lineColor), Color.green(s.lineColor), Color.blue(s.lineColor)); canvas.drawPath(fillPath, fill)
            }
            line.color = s.lineColor; line.strokeWidth = if (si == selected && m.correlation) 3.2f else 2.4f; canvas.drawPath(path, line)
            if (!m.correlation && s.goal != null) canvas.drawLine(left, yp(s.goal, scale), right, yp(s.goal, scale), goalPaint)
            s.points.forEachIndexed { pi, p ->
                val focused = si == selected && m.selectedPoint == pi
                point.color = if (focused) s.highlightColor else s.lineColor
                canvas.drawCircle(xp(p.x), yp(p.value, scale), if (focused) 6f else 3f, point)
            }
        }
        canvas.restoreToCount(save)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    private fun yRange(series: ChartSeries): Pair<Double,Double> {
        val vals = series.points.map { it.value }.toMutableList(); series.goal?.let { vals += it }
        var hi = vals.maxOrNull() ?: 1.0; var lo = vals.minOrNull() ?: -1.0
        if (hi == lo) return (lo - 1.0) to (hi + 1.0)
        hi = padded(hi, top = true); lo = padded(lo, top = false)
        if (hi <= lo) { val mid = (hi + lo) / 2.0; return (mid - 1.0) to (mid + 1.0) }
        return lo to hi
    }

    private fun padded(v: Double, top: Boolean): Double {
        if (v == 0.0) return if (top) 1.0 else -1.0
        val power = floor(log10(abs(v))) - 1.0
        val step = 10.0.pow(power)
        val towardZeroTwoSig = if (v > 0) floor(v / step) * step else -floor(abs(v) / step) * step
        return if (top) {
            if (v > 0) towardZeroTwoSig + 2 * step else towardZeroTwoSig + 2 * step
        } else {
            if (v > 0) towardZeroTwoSig - 2 * step else towardZeroTwoSig - 2 * step
        }
    }

    private fun formatAxis(v: Double): String = when {
        abs(v) >= 1000 -> String.format("%.0f", v)
        abs(v) >= 10 -> String.format("%.1f", v).trimEnd('0').trimEnd('.')
        else -> String.format("%.3f", v).trimEnd('0').trimEnd('.')
    }
}
