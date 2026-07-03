package com.anaglych.squintboyadvance.ui.archive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** GitHub-contribution-style intensity ramp: 0 none … 4 hottest. */
fun intensityLevel(count: Int): Int = when {
    count <= 0 -> 0
    count == 1 -> 1
    count <= 3 -> 2
    count <= 6 -> 3
    else -> 4
}

private val CELL = 14.dp
private val GAP = 3.dp

/**
 * Week-per-column heatmap of save activity, newest week at the right edge.
 * Tapping a day pulses the cell and reports it to the caller, which expands
 * the day's save list beneath.
 */
@Composable
fun CalendarHeatmap(
    dayCounts: Map<LocalDate, Int>,
    selectedDay: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
    weeks: Int = 53,
    today: LocalDate = LocalDate.now(),
) {
    // Columns run Sunday..Saturday; the last column is the week containing today.
    val endOfCurrentWeek = today.with(DayOfWeek.SATURDAY).let {
        if (it.isBefore(today)) it.plusWeeks(1) else it
    }
    val start = endOfCurrentWeek.minusDays((weeks * 7 - 1).toLong())

    val listState: LazyListState = rememberLazyListState()
    LaunchedEffect(weeks) {
        listState.scrollToItem(weeks - 1) // open at the newest week
    }

    val primary = MaterialTheme.colorScheme.primary
    val emptyCell = MaterialTheme.colorScheme.surfaceVariant
    val levelColors = remember(primary, emptyCell) {
        listOf(
            emptyCell,
            primary.copy(alpha = 0.25f),
            primary.copy(alpha = 0.50f),
            primary.copy(alpha = 0.75f),
            primary,
        )
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(GAP),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
    ) {
        items(count = weeks, key = { it }) { weekIndex ->
            val weekStart = start.plusWeeks(weekIndex.toLong())
            Column {
                MonthLabel(weekStart, isFirstColumn = weekIndex == 0)
                Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                    for (dayOfWeek in 0 until 7) {
                        val date = weekStart.plusDays(dayOfWeek.toLong())
                        if (date.isAfter(today)) {
                            Spacer(Modifier.size(CELL))
                        } else {
                            DayCell(
                                date = date,
                                count = dayCounts[date] ?: 0,
                                selected = date == selectedDay,
                                levelColors = levelColors,
                                onClick = { onDayClick(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Shows the month name above the first week that enters a new month. */
@Composable
private fun MonthLabel(weekStart: LocalDate, isFirstColumn: Boolean) {
    val previousWeekStart = weekStart.minusWeeks(1)
    val entersNewMonth = weekStart.month != previousWeekStart.month
    Box(Modifier.height(18.dp)) {
        if (entersNewMonth && !isFirstColumn) {
            Text(
                weekStart.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    count: Int,
    selected: Boolean,
    levelColors: List<Color>,
    onClick: () -> Unit,
) {
    // Pulse outward from the tapped cell so the expansion reads as "opening the day".
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            scale.animateTo(1.35f, animationSpec = tween(120))
            scale.animateTo(1f, animationSpec = spring())
        }
    }

    val border = if (selected) {
        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
    } else Modifier

    Box(
        modifier = Modifier
            .size(CELL)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(RoundedCornerShape(3.dp))
            .background(levelColors[intensityLevel(count)])
            .then(border)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}
