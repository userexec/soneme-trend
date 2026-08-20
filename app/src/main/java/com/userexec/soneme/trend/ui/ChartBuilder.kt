package com.userexec.soneme.trend.ui

import com.userexec.soneme.trend.model.*
import com.userexec.soneme.trend.time.TimeMath
import java.time.LocalDateTime

object ChartBuilder {
    data class InputSeries(
        val datumUid: String,
        val name: String,
        val unit: String,
        val basis: TimeBasis,
        val points: List<LogicalPoint>,
        val goal: Double? = null,
        val lineColor: Int,
        val highlightColor: Int
    )

    data class BuildResult(
        val series: List<ChartSeries>,
        val xBasis: TimeBasis,
        val leftEdge: LocalDateTime,
        val rightEdge: LocalDateTime,
        val xSpan: Double,
        val recordsInRange: Int
    )

    fun build(inputs: List<InputSeries>, range: ChartRange, correlation: Boolean): BuildResult? {
        val usable = inputs.filter { it.points.isNotEmpty() }
        if (usable.isEmpty()) return null
        val right = usable.flatMap { it.points }.maxOf { it.bucket.localStart }
        val xBasis = if (correlation) TimeBasis.finest(usable.map { it.basis }) else usable.first().basis
        val cutoff = TimeMath.rangeCutoff(right, range)
        val candidates = usable.mapNotNull { input ->
            val inRange = if (cutoff == null) input.points else input.points.filter { it.bucket.localStart >= cutoff && it.bucket.localStart <= right }
            val included = if (cutoff != null) {
                val prior = input.points.lastOrNull { it.bucket.localStart < cutoff }
                listOfNotNull(prior) + inRange
            } else inRange
            if (included.size < 2 || inRange.isEmpty()) null else input to Pair(included, inRange.toSet())
        }
        if ((!correlation && candidates.isEmpty()) || (correlation && candidates.size < 2)) return null
        val left = cutoff ?: candidates.flatMap { it.second.first }.minOf { it.bucket.localStart }
        val chartSeries = candidates.map { (input, data) ->
            val (included, inSet) = data
            ChartSeries(
                input.datumUid, input.name, input.unit, input.basis, input.lineColor, input.highlightColor,
                included.map { p -> ChartPoint(TimeMath.unitsBetween(left, p.bucket.localStart, xBasis), p.value.toDouble(), p, p in inSet) },
                if (correlation) null else input.goal
            )
        }
        return BuildResult(
            chartSeries,
            xBasis,
            left,
            right,
            TimeMath.unitsBetween(left, right, xBasis).coerceAtLeast(1.0),
            chartSeries.sumOf { s -> s.points.count { it.inRange } }
        )
    }

    fun availableRanges(inputs: List<InputSeries>, correlation: Boolean): List<ChartRange> {
        if (inputs.isEmpty()) return listOf(ChartRange.ALL_TIME)
        val limitingBasis = if (correlation) TimeBasis.coarsest(inputs.map { it.basis }) else inputs.first().basis
        val candidates = listOf(ChartRange.ALL_TIME, ChartRange.LAST_YEAR, ChartRange.LAST_MONTH, ChartRange.LAST_WEEK, ChartRange.LAST_DAY, ChartRange.LAST_HOUR)
            .filter { TimeMath.rangeAllowedByBasis(it, limitingBasis) }
        return candidates.filter { range ->
            if (range == ChartRange.ALL_TIME) true
            else if (!correlation) build(inputs, range, false) != null
            else {
                val right = inputs.flatMap { it.points }.maxOfOrNull { it.bucket.localStart }
                right != null && inputs.any { hasRenderablePortion(it, range, right) }
            }
        }
    }

    private fun hasRenderablePortion(input: InputSeries, range: ChartRange, right: LocalDateTime): Boolean {
        if (input.points.isEmpty()) return false
        val cutoff = TimeMath.rangeCutoff(right, range) ?: return input.points.size >= 2
        val inRange = input.points.filter { it.bucket.localStart >= cutoff && it.bucket.localStart <= right }
        val prior = input.points.lastOrNull { it.bucket.localStart < cutoff }
        return inRange.isNotEmpty() && listOfNotNull(prior).size + inRange.size >= 2
    }
}
