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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/** Heat ramp buckets: 0 none … 5 hottest. Absolute, so equal green means equal saves. */
fun intensityLevel(count: Int): Int = when {
    count <= 0 -> 0
    count == 1 -> 1
    count <= 3 -> 2
    count <= 6 -> 3
    count <= 9 -> 4
    else -> 5
}

// Dark green → crimson heat ramp, stepped through the brand palette:
// forest → GB green → lime → burnt orange → crimson.
private val HeatRamp = listOf(
    Color(0xFF306230),
    Color(0xFF9BBC0F),
    Color(0xFFC4D428),
    Color(0xFFD87340),
    Color(0xFFEC1358),
)

private val monthTitleFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val dowLabels = listOf("S", "M", "T", "W", "T", "F", "S")

/**
 * One-month-at-a-time heatmap calendar: fixed 6×7 grid, swipe (or chevrons) to
 * move between months, bounded by the oldest archived save and the current
 * month. Days outside the month render blank so the height never jumps.
 */
@Composable
fun MonthHeatmap(
    dayCounts: Map<LocalDate, Int>,
    pinnedDays: Set<LocalDate>,
    selectedDay: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
    oldestMonth: YearMonth?,
    today: LocalDate = LocalDate.now(),
) {
    val currentMonth = YearMonth.from(today)
    val firstMonth = oldestMonth?.takeIf { it <= currentMonth } ?: currentMonth
    val pageCount = MonthGridMath.pageCount(firstMonth, currentMonth)

    val pagerState = rememberPagerState(initialPage = pageCount - 1) { pageCount }
    val scope = rememberCoroutineScope()

    // A newly archived older save can extend the range; keep the pager pinned
    // to the same month rather than the same index.
    LaunchedEffect(pageCount) {
        if (pagerState.currentPage >= pageCount) pagerState.scrollToPage(pageCount - 1)
    }

    val emptyCell = MaterialTheme.colorScheme.surfaceVariant
    val levelColors = remember(emptyCell) { listOf(emptyCell) + HeatRamp }

    Column {
        // ── Month header with chevrons ───────────────────────────────
        val shownMonth = MonthGridMath.monthForPage(firstMonth, pagerState.currentPage)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                enabled = pagerState.currentPage > 0,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous month",
                )
            }
            Text(
                monthTitleFormat.format(shownMonth),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                enabled = pagerState.currentPage < pageCount - 1,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next month",
                )
            }
        }

        // ── Day-of-week labels ───────────────────────────────────────
        Row(Modifier.fillMaxWidth()) {
            for (label in dowLabels) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ── Month pages ──────────────────────────────────────────────
        HorizontalPager(state = pagerState) { page ->
            MonthGrid(
                month = MonthGridMath.monthForPage(firstMonth, page),
                dayCounts = dayCounts,
                pinnedDays = pinnedDays,
                selectedDay = selectedDay,
                today = today,
                levelColors = levelColors,
                onDayClick = onDayClick,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    dayCounts: Map<LocalDate, Int>,
    pinnedDays: Set<LocalDate>,
    selectedDay: LocalDate?,
    today: LocalDate,
    levelColors: List<Color>,
    onDayClick: (LocalDate) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        for (row in 0 until MonthGridMath.ROWS) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until MonthGridMath.COLS) {
                    val date = MonthGridMath.dateAt(month, row, col)
                    Box(Modifier.weight(1f)) {
                        if (YearMonth.from(date) == month) {
                            DayCell(
                                date = date,
                                count = dayCounts[date] ?: 0,
                                pinned = date in pinnedDays,
                                selected = date == selectedDay,
                                isToday = date == today,
                                isFuture = date.isAfter(today),
                                levelColors = levelColors,
                                onClick = { onDayClick(date) },
                            )
                        } else {
                            // Adjacent-month spillover: blank, but keeps cell size.
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(3.dp)
                                    .aspectRatio(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    count: Int,
    pinned: Boolean,
    selected: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    levelColors: List<Color>,
    onClick: () -> Unit,
) {
    val level = intensityLevel(count)

    // Pulse outward from the tapped cell so the day-list reads as "opening".
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            scale.animateTo(1.2f, animationSpec = tween(120))
            scale.animateTo(1f, animationSpec = spring())
        }
    }

    val border = when {
        selected -> Modifier.border(
            1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)
        )
        isToday -> Modifier.border(
            1.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(8.dp)
        )
        else -> Modifier
    }
    val clickable = if (count > 0) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else Modifier

    val numberColor = when {
        isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        level >= 4 -> Color.White                            // orange/crimson cells
        level >= 2 -> MaterialTheme.colorScheme.onPrimary    // bright green/lime cells
        level == 1 -> MaterialTheme.colorScheme.onSurface    // dark green cell
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(3.dp)
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(RoundedCornerShape(8.dp))
            .background(levelColors[level])
            .then(border)
            .then(clickable),
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = numberColor,
        )
        if (pinned) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            level >= 4 -> Color.White
                            level >= 2 -> MaterialTheme.colorScheme.onPrimary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    ),
            )
        }
    }
}
