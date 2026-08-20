package com.userexec.soneme.trend.time

import com.userexec.soneme.trend.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

object TimeMath {
    fun bucketFor(instant: Instant, basis: TimeBasis, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): BucketKey {
        return BucketKey(basis, floor(instant.atZone(zone).toLocalDateTime(), basis, locale))
    }

    fun floor(local: LocalDateTime, basis: TimeBasis, locale: Locale = Locale.getDefault()): LocalDateTime = when (basis) {
        TimeBasis.MINUTES -> local.withSecond(0).withNano(0)
        TimeBasis.HOURS -> local.withMinute(0).withSecond(0).withNano(0)
        TimeBasis.DAYS -> local.toLocalDate().atStartOfDay()
        TimeBasis.WEEKS -> {
            val first = WeekFields.of(locale).firstDayOfWeek
            local.toLocalDate().with(TemporalAdjusters.previousOrSame(first)).atStartOfDay()
        }
        TimeBasis.MONTHS -> LocalDate.of(local.year, local.month, 1).atStartOfDay()
        TimeBasis.YEARS -> LocalDate.of(local.year, 1, 1).atStartOfDay()
    }

    fun logicalPoints(file: TrendFile, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): List<LogicalPoint> {
        return file.records.groupBy { bucketFor(it.instant, file.timeBasis, zone, locale) }
            .map { (bucket, members) ->
                val newest = members.maxBy { it.instant }
                LogicalPoint(bucket, newest.instant, newest.value, members.sortedBy { it.instant })
            }
            .sortedBy { it.bucket.localStart }
    }

    /** X distance in discrete units of the requested basis, never epoch milliseconds. */
    fun unitsBetween(start: LocalDateTime, end: LocalDateTime, basis: TimeBasis): Double = when (basis) {
        TimeBasis.MINUTES -> ChronoUnit.MINUTES.between(start, end).toDouble()
        TimeBasis.HOURS -> ChronoUnit.HOURS.between(start, end).toDouble()
        TimeBasis.DAYS -> ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()).toDouble()
        TimeBasis.WEEKS -> ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()).toDouble() / 7.0
        TimeBasis.MONTHS -> ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(end)).toDouble()
        TimeBasis.YEARS -> ChronoUnit.YEARS.between(Year.from(start), Year.from(end)).toDouble()
    }

    fun rangeCutoff(rightEdge: LocalDateTime, range: ChartRange): LocalDateTime? = when (range) {
        ChartRange.ALL_TIME -> null
        ChartRange.LAST_YEAR -> rightEdge.minusDays(365)
        ChartRange.LAST_MONTH -> rightEdge.minusDays(30)
        ChartRange.LAST_WEEK -> rightEdge.minusDays(7)
        ChartRange.LAST_DAY -> rightEdge.minusHours(24)
        ChartRange.LAST_HOUR -> rightEdge.minusMinutes(60)
    }

    fun rangeAllowedByBasis(range: ChartRange, basis: TimeBasis): Boolean = when (range) {
        ChartRange.ALL_TIME -> true
        ChartRange.LAST_YEAR -> basis.rank < TimeBasis.YEARS.rank
        ChartRange.LAST_MONTH -> basis.rank < TimeBasis.MONTHS.rank
        ChartRange.LAST_WEEK -> basis.rank < TimeBasis.WEEKS.rank
        ChartRange.LAST_DAY -> basis.rank < TimeBasis.DAYS.rank
        ChartRange.LAST_HOUR -> basis.rank < TimeBasis.HOURS.rank
    }

    fun lookbehindRangesAbove(basis: TimeBasis): List<ChartRange> = listOf(
        ChartRange.LAST_YEAR,
        ChartRange.LAST_MONTH,
        ChartRange.LAST_WEEK,
        ChartRange.LAST_DAY,
        ChartRange.LAST_HOUR
    ).filter { rangeAllowedByBasis(it, basis) }

    fun pretty(bucket: BucketKey, locale: Locale = Locale.getDefault()): String {
        val v = bucket.localStart
        return when (bucket.basis) {
            TimeBasis.MINUTES -> v.format(DateTimeFormatter.ofPattern("MMMM d, yyyy, h:mm a", locale))
            TimeBasis.HOURS -> v.format(DateTimeFormatter.ofPattern("MMMM d, yyyy, h a", locale))
            TimeBasis.DAYS -> v.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", locale))
            TimeBasis.WEEKS -> {
                val end = v.toLocalDate().plusDays(6)
                if (end.year == v.year && end.month == v.month) {
                    "${v.format(DateTimeFormatter.ofPattern("MMMM d", locale))}-${end.dayOfMonth}, ${v.year}"
                } else {
                    "${v.toLocalDate().format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))}-${end.format(DateTimeFormatter.ofPattern("MMM d, yyyy", locale))}"
                }
            }
            TimeBasis.MONTHS -> v.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
            TimeBasis.YEARS -> v.year.toString()
        }
    }

    fun prettyLocalDateTime(value: LocalDateTime, locale: Locale = Locale.getDefault()): String =
        value.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))

    fun formatNumber(value: java.math.BigDecimal): String = value.stripTrailingZeros().toPlainString()

    fun projectionUnits(basis: TimeBasis): List<Pair<String, Double>> = buildList {
        if (basis == TimeBasis.MINUTES) add("one hour" to 60.0)
        if (basis.rank <= TimeBasis.HOURS.rank) add("one day" to when (basis) {
            TimeBasis.MINUTES -> 1440.0
            TimeBasis.HOURS -> 24.0
            else -> 0.0
        })
        if (basis.rank <= TimeBasis.DAYS.rank) add("one week" to when (basis) {
            TimeBasis.MINUTES -> 10080.0
            TimeBasis.HOURS -> 168.0
            TimeBasis.DAYS -> 7.0
            else -> 0.0
        })
        if (basis.rank <= TimeBasis.WEEKS.rank) add("one month" to when (basis) {
            TimeBasis.MINUTES -> 43200.0
            TimeBasis.HOURS -> 720.0
            TimeBasis.DAYS -> 30.0
            TimeBasis.WEEKS -> 30.0 / 7.0
            else -> 0.0
        })
        if (basis.rank <= TimeBasis.MONTHS.rank) add("one year" to when (basis) {
            TimeBasis.MINUTES -> 525600.0
            TimeBasis.HOURS -> 8760.0
            TimeBasis.DAYS -> 365.0
            TimeBasis.WEEKS -> 365.0 / 7.0
            TimeBasis.MONTHS -> 12.0
            else -> 0.0
        })
    }

    fun durationForRange(range: ChartRange): Duration? = when (range) {
        ChartRange.ALL_TIME -> null
        ChartRange.LAST_YEAR -> Duration.ofDays(365)
        ChartRange.LAST_MONTH -> Duration.ofDays(30)
        ChartRange.LAST_WEEK -> Duration.ofDays(7)
        ChartRange.LAST_DAY -> Duration.ofHours(24)
        ChartRange.LAST_HOUR -> Duration.ofHours(1)
    }

    fun interpolationFraction(before: LocalDateTime, at: LocalDateTime, after: LocalDateTime): Double {
        val total = Duration.between(before, after).toMillis().toDouble()
        if (abs(total) < 0.5) return 0.0
        return Duration.between(before, at).toMillis().toDouble() / total
    }
}
