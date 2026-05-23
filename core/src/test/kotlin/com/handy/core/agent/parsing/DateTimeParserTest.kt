package com.handy.core.agent.parsing

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.stream.Stream
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class DateTimeParserTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("parseCases")
    fun `parser covers supported natural language cases`(
        input: String,
        expected: ZonedDateTime,
    ) {
        val result = DateTimeParser.parse(input, now = NOW)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(expected.toInstant().toEpochMilli())
    }

    @Test fun `text without date time signal returns no time`() {
        val result = DateTimeParser.parse("team sync", now = NOW)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isNull()
    }

    @Test fun `unparseable time signal fails`() {
        val result = DateTimeParser.parse("next someday", now = NOW)

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(DateTimeParser.ParseFailure::class.java)
    }

    @Test fun `past time is refused`() {
        val result = DateTimeParser.parse("2026-05-17 09:00", now = NOW)

        assertThat(result.isFailure).isTrue()
        assertThat(DateTimeParser.isRefusal(result.exceptionOrNull()!!)).isTrue()
    }

    @Test fun `more than one year future is refused`() {
        val result = DateTimeParser.parse("2027-05-19 10:01", now = NOW)

        assertThat(result.isFailure).isTrue()
        assertThat(DateTimeParser.isRefusal(result.exceptionOrNull()!!)).isTrue()
    }

    companion object {
        private val ZONE: ZoneId = ZoneId.systemDefault()
        private val NOW: ZonedDateTime = ZonedDateTime.of(
            LocalDate.of(2026, 5, 18),
            LocalTime.of(10, 0),
            ZONE,
        )

        @JvmStatic
        fun parseCases(): Stream<Arguments> = Stream.of(
            Arguments.of(
                "tomorrow 3 pm",
                ZonedDateTime.of(2026, 5, 19, 15, 0, 0, 0, ZONE),
            ),
            Arguments.of(
                "next Monday 9 am",
                ZonedDateTime.of(2026, 5, 25, 9, 0, 0, 0, ZONE),
            ),
            Arguments.of(
                "this Friday 6:30 pm",
                ZonedDateTime.of(2026, 5, 22, 18, 30, 0, 0, ZONE),
            ),
            Arguments.of(
                "in 2 hours",
                NOW.plusHours(2),
            ),
            Arguments.of(
                "in 30 minutes",
                NOW.plusMinutes(30),
            ),
            Arguments.of(
                "at 6",
                ZonedDateTime.of(2026, 5, 18, 18, 0, 0, 0, ZONE),
            ),
            Arguments.of(
                "2026-06-01 14:00",
                ZonedDateTime.of(2026, 6, 1, 14, 0, 0, 0, ZONE),
            ),
            Arguments.of(
                "Saturday",
                ZonedDateTime.of(2026, 5, 23, 9, 0, 0, 0, ZONE),
            ),
        )
    }
}
