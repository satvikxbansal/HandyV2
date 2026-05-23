package com.handy.core.agent.parsing

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Bounded parser for the small set of natural-language date/time phrases used
 * by deterministic recipes. It uses the system default [ZoneId], refuses past
 * times, and refuses times more than one year in the future.
 */
object DateTimeParser {
    private val systemZone: ZoneId
        get() = ZoneId.systemDefault()

    fun parse(
        text: String,
        now: ZonedDateTime = ZonedDateTime.now(systemZone),
    ): Result<Long?> {
        val raw = text.trim()
        if (raw.isBlank()) return Result.success(null)

        val reference = now.withZoneSameInstant(systemZone)
        val parsed = parseIso(raw, reference.zone)
            ?: parseRelative(raw, reference)
            ?: parseTomorrow(raw, reference)
            ?: parseWeekday(raw, reference)
            ?: parseTimeOnly(raw, reference)
            ?: parseYearOnly(raw, reference.zone)

        return if (parsed != null) {
            validate(parsed, reference)
        } else if (raw.hasDateTimeSignal()) {
            Result.failure(ParseFailure("unparseable-time"))
        } else {
            Result.success(null)
        }
    }

    class ParseFailure(reason: String) : IllegalArgumentException(reason)
    class Refused(reason: String) : IllegalArgumentException(reason)

    fun isRefusal(error: Throwable): Boolean = error is Refused

    private fun parseIso(text: String, zone: ZoneId): ZonedDateTime? {
        val match = ISO_DATE_TIME.find(text) ?: return null
        val normalized = "${match.groupValues[1]}T${match.groupValues[2].padStart(2, '0')}:${match.groupValues[3]}"
        return runCatching {
            LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(zone)
        }.getOrNull()
    }

    private fun parseRelative(text: String, now: ZonedDateTime): ZonedDateTime? {
        val match = RELATIVE_DURATION.find(text) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        return when (match.groupValues[2].lowercase(Locale.US)) {
            "hour", "hours", "hr", "hrs", "h" -> now.plusHours(amount)
            "minute", "minutes", "min", "mins", "m" -> now.plusMinutes(amount)
            else -> null
        }
    }

    private fun parseTomorrow(text: String, now: ZonedDateTime): ZonedDateTime? {
        val match = TOMORROW.find(text) ?: return null
        val time = parseTimeOfDay(text.substring(match.range.last + 1)) ?: DEFAULT_TIME
        return now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
    }

    private fun parseWeekday(text: String, now: ZonedDateTime): ZonedDateTime? {
        val match = WEEKDAY.find(text) ?: return null
        val qualifier = match.groupValues.getOrNull(1)?.lowercase(Locale.US).orEmpty()
        val targetDay = match.groupValues.getOrNull(2)?.toDayOfWeek() ?: return null
        val tail = text.substring(match.range.last + 1)
        val time = parseTimeOfDay(tail) ?: DEFAULT_TIME

        var daysUntil = (targetDay.value - now.dayOfWeek.value + 7) % 7
        if (qualifier == "next" && daysUntil == 0) {
            daysUntil = 7
        }

        var date = now.toLocalDate().plusDays(daysUntil.toLong())
        if (qualifier.isEmpty()) {
            val candidate = date.atTime(time).atZone(now.zone)
            if (!candidate.isAfter(now)) {
                date = date.plusDays(7)
            }
        }
        return date.atTime(time).atZone(now.zone)
    }

    private fun parseTimeOnly(text: String, now: ZonedDateTime): ZonedDateTime? {
        parseTimeOfDay(text)?.let { time ->
            val today = now.toLocalDate().atTime(time).atZone(now.zone)
            return if (today.isAfter(now)) today else today.plusDays(1)
        }

        val match = AT_BARE_HOUR.find(text) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val candidates = listOf(hour % 12, (hour % 12) + 12)
            .distinct()
            .flatMap { resolvedHour ->
                val candidate = now.toLocalDate()
                    .atTime(LocalTime.of(resolvedHour, 0))
                    .atZone(now.zone)
                listOf(candidate, candidate.plusDays(1))
            }
            .filter { it.isAfter(now) }
            .sorted()

        return candidates.firstOrNull { !it.isAfter(now.plusHours(12)) }
    }

    private fun parseYearOnly(text: String, zone: ZoneId): ZonedDateTime? {
        val match = YEAR_ONLY.find(text) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        return LocalDate.of(year, 1, 1).atTime(DEFAULT_TIME).atZone(zone)
    }

    private fun parseTimeOfDay(text: String): LocalTime? {
        TIME_WITH_MERIDIEM.find(text)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
            val period = match.groupValues[3].lowercase(Locale.US)
            val resolvedHour = if (period.startsWith("p")) {
                (hour % 12) + 12
            } else {
                hour % 12
            }
            return LocalTime.of(resolvedHour, minute)
        }

        TIME_24_HOUR.find(text)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return null
            val minute = match.groupValues[2].toIntOrNull() ?: return null
            return LocalTime.of(hour, minute)
        }

        return null
    }

    private fun validate(
        parsed: ZonedDateTime,
        now: ZonedDateTime,
    ): Result<Long?> = when {
        parsed.isBefore(now) -> Result.failure(Refused("time-in-past"))
        parsed.isAfter(now.plusYears(1)) -> Result.failure(Refused("time-too-far"))
        else -> Result.success(parsed.toInstant().toEpochMilli())
    }

    private fun String.hasDateTimeSignal(): Boolean {
        val normalized = lowercase(Locale.US)
        return normalized.contains("tomorrow") ||
            WEEKDAY.containsMatchIn(this) ||
            RELATIVE_DURATION.containsMatchIn(this) ||
            AT_BARE_HOUR.containsMatchIn(this) ||
            TIME_WITH_MERIDIEM.containsMatchIn(this) ||
            ISO_DATE_HINT.containsMatchIn(this) ||
            YEAR_ONLY.containsMatchIn(this) ||
            Regex("""\b(next|this)\b""", RegexOption.IGNORE_CASE).containsMatchIn(this)
    }

    private fun String.toDayOfWeek(): DayOfWeek? = when (lowercase(Locale.US)) {
        "monday" -> DayOfWeek.MONDAY
        "tuesday" -> DayOfWeek.TUESDAY
        "wednesday" -> DayOfWeek.WEDNESDAY
        "thursday" -> DayOfWeek.THURSDAY
        "friday" -> DayOfWeek.FRIDAY
        "saturday" -> DayOfWeek.SATURDAY
        "sunday" -> DayOfWeek.SUNDAY
        else -> null
    }

    private val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

    private val ISO_DATE_TIME = Regex("""\b(\d{4}-\d{2}-\d{2})[ T](\d{1,2}):([0-5]\d)\b""")
    private val ISO_DATE_HINT = Regex("""\b\d{4}-\d{2}-\d{2}\b""")
    private val YEAR_ONLY = Regex("""\b(19\d{2}|20\d{2}|21\d{2})\b""")
    private val RELATIVE_DURATION = Regex(
        pattern = """\bin\s+(\d+)\s*(hours?|hrs?|h|minutes?|mins?|m)\b""",
        options = setOf(RegexOption.IGNORE_CASE),
    )
    private val TOMORROW = Regex("""\btomorrow\b""", RegexOption.IGNORE_CASE)
    private val WEEKDAY = Regex(
        pattern = """\b(?:(this|next)\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""",
        options = setOf(RegexOption.IGNORE_CASE),
    )
    private val TIME_WITH_MERIDIEM = Regex(
        pattern = """\b(1[0-2]|0?[1-9])(?:[:.]([0-5]\d))?\s*(a\.?m\.?|p\.?m\.?)\b""",
        options = setOf(RegexOption.IGNORE_CASE),
    )
    private val TIME_24_HOUR = Regex("""\b([01]?\d|2[0-3])[:.]([0-5]\d)\b""")
    private val AT_BARE_HOUR = Regex(
        pattern = """\bat\s+(1[0-2]|0?[1-9])(?:\s*o[' ]?clock)?\b""",
        options = setOf(RegexOption.IGNORE_CASE),
    )
}
