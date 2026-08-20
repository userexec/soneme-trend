package com.userexec.soneme.trend.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

enum class TimeBasis(val csvName: String, val singular: String, val perLabel: String, val rank: Int) {
    MINUTES("minutes", "minute", "per minute", 0),
    HOURS("hours", "hour", "per hour", 1),
    DAYS("days", "day", "per day", 2),
    WEEKS("weeks", "week", "per week", 3),
    MONTHS("months", "month", "per month", 4),
    YEARS("years", "year", "per year", 5);

    companion object {
        fun fromCsv(value: String): TimeBasis? = entries.firstOrNull { it.csvName == value.trim().lowercase() }
        fun finest(values: Iterable<TimeBasis>): TimeBasis = values.minBy { it.rank }
        fun coarsest(values: Iterable<TimeBasis>): TimeBasis = values.maxBy { it.rank }
    }
}

data class DatumDefinition(
    val uid: String,
    val name: String,
    val csvFilename: String,
    val order: Int
)

data class CorrelationDefinition(
    val uid: String,
    val name: String,
    val datumUids: List<String>,
    val order: Int
)

data class RegistryState(
    val datums: List<DatumDefinition> = emptyList(),
    val correlations: List<CorrelationDefinition> = emptyList()
)

data class RawRecord(
    val instant: Instant,
    val value: BigDecimal
)

data class TrendFile(
    val unit: String,
    val timeBasis: TimeBasis,
    val goal: BigDecimal?,
    val records: List<RawRecord>
)

data class BucketKey(
    val basis: TimeBasis,
    val localStart: LocalDateTime
)

data class LogicalPoint(
    val bucket: BucketKey,
    val persistedInstant: Instant,
    val value: BigDecimal,
    val rawMembers: List<RawRecord> = emptyList()
)

enum class ChartRange(val label: String) {
    ALL_TIME("All time"),
    LAST_YEAR("Last year"),
    LAST_MONTH("Last month"),
    LAST_WEEK("Last week"),
    LAST_DAY("Last day"),
    LAST_HOUR("Last hour")
}

data class ChartPoint(
    val x: Double,
    val value: Double,
    val logicalPoint: LogicalPoint,
    val inRange: Boolean = true
)

data class ChartSeries(
    val datumUid: String,
    val name: String,
    val unit: String,
    val timeBasis: TimeBasis,
    val lineColor: Int,
    val highlightColor: Int,
    val points: List<ChartPoint>,
    val goal: Double? = null
)

data class RegressionResult(val slope: Double, val intercept: Double, val origin: LocalDateTime)
