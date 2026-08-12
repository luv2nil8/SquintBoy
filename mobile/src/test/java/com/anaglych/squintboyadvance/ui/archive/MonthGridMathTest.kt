package com.anaglych.squintboyadvance.ui.archive

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthGridMathTest {

    @Test
    fun `grid start is the Sunday on or before the 1st`() {
        // July 2026 starts on a Wednesday → grid starts Sun Jun 28.
        assertEquals(
            LocalDate.of(2026, 6, 28),
            MonthGridMath.gridStart(YearMonth.of(2026, 7)),
        )
        // Feb 2026 starts on a Sunday → grid starts on the 1st itself.
        assertEquals(
            LocalDate.of(2026, 2, 1),
            MonthGridMath.gridStart(YearMonth.of(2026, 2)),
        )
        assertEquals(DayOfWeek.SUNDAY, MonthGridMath.gridStart(YearMonth.of(2026, 8)).dayOfWeek)
    }

    @Test
    fun `six-row grid covers every day of a six-week month`() {
        // Aug 2026: 31 days starting Saturday — needs all 6 rows.
        val month = YearMonth.of(2026, 8)
        val inMonth = (0 until MonthGridMath.ROWS).flatMap { row ->
            (0 until MonthGridMath.COLS).map { col -> MonthGridMath.dateAt(month, row, col) }
        }.filter { YearMonth.from(it) == month }
        assertEquals(31, inMonth.size)
        assertEquals(LocalDate.of(2026, 8, 31), inMonth.last())
    }

    @Test
    fun `page math maps both directions`() {
        val oldest = YearMonth.of(2026, 3)
        val newest = YearMonth.of(2026, 7)
        assertEquals(5, MonthGridMath.pageCount(oldest, newest))
        assertEquals(oldest, MonthGridMath.monthForPage(oldest, 0))
        assertEquals(newest, MonthGridMath.monthForPage(oldest, 4))
    }

    @Test
    fun `single-month range has one page`() {
        val month = YearMonth.of(2026, 7)
        assertEquals(1, MonthGridMath.pageCount(month, month))
    }

    @Test
    fun `page count never drops below one`() {
        // Degenerate input (oldest after newest) still renders one page.
        assertEquals(1, MonthGridMath.pageCount(YearMonth.of(2026, 8), YearMonth.of(2026, 7)))
    }
}
