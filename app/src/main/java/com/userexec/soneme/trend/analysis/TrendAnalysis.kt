package com.userexec.soneme.trend.analysis

import com.userexec.soneme.trend.model.*
import com.userexec.soneme.trend.time.TimeMath
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.ceil

object TrendAnalysis {
    data class ChangeStat(val label: String, val change: Double, val percent: Double?)
    data class GoalProjection(val text: String)
    data class Estimate(val label: String, val value: Double)
    data class Report(
        val overallChange: Double,
        val overallPercent: Double?,
        val changes: List<ChangeStat>,
        val regression: RegressionResult?,
        val goalProjection: GoalProjection?,
        val estimates: List<Estimate>
    )

    fun build(file: TrendFile, points: List<LogicalPoint>, now: LocalDateTime = LocalDateTime.now()): Report? {
        if (points.size < 2) return null
        val first = points.first(); val last = points.last()
        val overall = last.value.toDouble() - first.value.toDouble()
        val overallPct = percent(first.value.toDouble(), last.value.toDouble())
        val currentBucket = TimeMath.floor(now, file.timeBasis)
        val changes = TimeMath.lookbehindRangesAbove(file.timeBasis).mapNotNull { range ->
            val duration = TimeMath.durationForRange(range) ?: return@mapNotNull null
            val target = currentBucket.minus(duration)
            val baseline = valueAt(points, target) ?: return@mapNotNull null
            val current = last.value.toDouble() // carry latest measurement forward to now
            ChangeStat(range.label.lowercase().replaceFirstChar { it.uppercase() }, current - baseline, percent(baseline, current))
        }
        val regression = regression(points, file.timeBasis)
        val goal = file.goal?.let { goalProjection(it.toDouble(), points, regression, file.timeBasis) }
        val estimates = regression?.let { r ->
            val latestX = TimeMath.unitsBetween(r.origin, last.bucket.localStart, file.timeBasis)
            TimeMath.projectionUnits(file.timeBasis).map { (label, units) -> Estimate(label, r.intercept + r.slope * (latestX + units)) }
        } ?: emptyList()
        return Report(overall, overallPct, changes, regression, goal, estimates)
    }

    private fun valueAt(points: List<LogicalPoint>, target: LocalDateTime): Double? {
        val exact = points.firstOrNull { it.bucket.localStart == target }
        if (exact != null) return exact.value.toDouble()
        val before = points.lastOrNull { it.bucket.localStart < target } ?: return null
        val after = points.firstOrNull { it.bucket.localStart > target }
        if (after == null) return before.value.toDouble()
        val f = TimeMath.interpolationFraction(before.bucket.localStart, target, after.bucket.localStart)
        return before.value.toDouble() + (after.value.toDouble() - before.value.toDouble()) * f
    }

    private fun percent(baseline: Double, current: Double): Double? = if (baseline == 0.0) null else ((current - baseline) / baseline) * 100.0

    private fun regression(points: List<LogicalPoint>, basis: TimeBasis): RegressionResult? {
        if (points.size < 2) return null
        val origin = points.first().bucket.localStart
        val xs = points.map { TimeMath.unitsBetween(origin, it.bucket.localStart, basis) }
        val ys = points.map { it.value.toDouble() }
        val meanX = xs.average(); val meanY = ys.average()
        val denom = xs.sumOf { (it - meanX) * (it - meanX) }
        if (abs(denom) < 1e-12) return null
        val slope = xs.indices.sumOf { (xs[it] - meanX) * (ys[it] - meanY) } / denom
        val intercept = meanY - slope * meanX
        return RegressionResult(slope, intercept, origin)
    }

    private fun goalProjection(goal: Double, points: List<LogicalPoint>, regression: RegressionResult?, basis: TimeBasis): GoalProjection? {
        val oldest = points.first().value.toDouble(); val latest = points.last().value.toDouble()
        if (oldest == goal) return null
        val reached = (oldest < goal && latest >= goal) || (oldest > goal && latest <= goal)
        if (reached) return GoalProjection("Goal reached.")
        val r = regression ?: return GoalProjection("Current values suggest goal will not be reached without changes.")
        if (abs(r.slope) < 1e-12) return GoalProjection("Current values suggest goal will not be reached without changes.")
        val goalX = (goal - r.intercept) / r.slope
        val latestX = TimeMath.unitsBetween(r.origin, points.last().bucket.localStart, basis)
        val headingToward = (goal > latest && r.slope > 0) || (goal < latest && r.slope < 0)
        if (!headingToward) return GoalProjection("Current values suggest goal will not be reached without changes.")
        val remaining = if (goalX <= latestX) 0.0 else goalX - latestX
        val whole = ceil(remaining).toLong()
        return GoalProjection("Estimated time to goal: $whole ${if (whole == 1L) basis.singular else basis.csvName}")
    }

    fun formatChange(value: Double): String {
        val a = abs(value)
        val scale = when { a < 1 -> 2; a < 100 -> 1; else -> 0 }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    fun formatPercent(value: Double): String = BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).toPlainString() + "%"

    fun formatEstimate(value: Double): String {
        val a = abs(value); val scale = when { a < 1 -> 2; a < 100 -> 1; else -> 0 }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}
