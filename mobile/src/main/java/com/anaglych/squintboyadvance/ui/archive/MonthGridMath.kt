package com.anaglych.squintboyadvance.ui.archive

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Pure date math for the month-grid calendar (Sunday-first, fixed 6 rows). */
object MonthGridMath {

    const val ROWS = 6
    const val COLS = 7

    /** The grid's top-left date: the Sunday on or before the 1st of the month. */
    fun gridStart(month: YearMonth): LocalDate =
        month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

    /** Date at a grid position. May fall outside [month] (rendered blank). */
    fun dateAt(month: YearMonth, row: Int, col: Int): LocalDate =
        gridStart(month).plusDays((row * COLS + col).toLong())

    /** Number of pager pages spanning [oldest]..[newest] inclusive. */
    fun pageCount(oldest: YearMonth, newest: YearMonth): Int =
        (ChronoUnit.MONTHS.between(oldest, newest).toInt() + 1).coerceAtLeast(1)

    fun monthForPage(oldest: YearMonth, page: Int): YearMonth = oldest.plusMonths(page.toLong())
}
