package com.example.weatherrecommender.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.ui.util.isoDateToDayOfMonth
import com.example.weatherrecommender.ui.util.isoDateToShortWeekday
import com.example.weatherrecommender.ui.util.isoDateToWeekday
import com.example.weatherrecommender.ui.util.weatherCodeDescription
import com.example.weatherrecommender.ui.util.weatherCodeIcon
import kotlin.math.roundToInt

/**
 * Full-width row of tall day-of-week buttons. Today is index 0 (selected by default in UI state).
 * Each button shows weekday + date stacked, then weather icon, temp range, and precipitation —
 * no per-day activity name/score. Tapping updates [selectedDayIndex] via [onDaySelected].
 */
@Composable
internal fun WeekSummarySection(
    forecast: WeatherForecast,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DetailLayout.DayButtonSpacing)
    ) {
        forecast.dailyForecasts.forEachIndexed { index, day ->
            WeekDayButton(
                day = day,
                dayIndex = index,
                selected = index == selectedDayIndex,
                compact = compact,
                onClick = { onDaySelected(index) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun WeekDayButton(
    day: DailyForecast,
    dayIndex: Int,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(250),
        label = "week_day_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(250),
        label = "week_day_content"
    )

    val weekday = isoDateToWeekday(day.date)
    val shortWeekday = isoDateToShortWeekday(day.date)
    val dayOfMonth = isoDateToDayOfMonth(day.date)
    val weatherDesc = weatherCodeDescription(day.weatherCode)
    val precipMm = day.precipitationSum.roundToInt()
    val rowLabel = stringResource(
        R.string.week_summary_row_cd,
        weekday,
        dayOfMonth,
        day.maxTemp.roundToInt(),
        day.minTemp.roundToInt(),
        precipMm
    )
    val corner = RoundedCornerShape(DetailLayout.DayButtonCorner)
    val iconSize = if (compact) 12.dp else 16.dp
    val weekdaySize = if (compact) 8.sp else 9.sp
    val dateSize = if (compact) 12.sp else 14.sp
    val metricSize = if (compact) 8.sp else 9.sp

    Column(
        modifier = modifier
            .clip(corner)
            .background(background)
            .then(
                if (selected) Modifier
                else Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    corner
                )
            )
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = rowLabel
                testTag = "week_summary_day_$dayIndex"
            }
            .padding(
                horizontal = 2.dp,
                vertical = if (compact) 4.dp else 6.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Text(
            text = shortWeekday,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = weekdaySize,
                lineHeight = weekdaySize * 1.1f
            ),
            color = contentColor.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            softWrap = false
        )
        Text(
            text = dayOfMonth,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = dateSize,
                lineHeight = dateSize * 1.1f
            ),
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        Icon(
            imageVector = weatherCodeIcon(day.weatherCode),
            contentDescription = weatherDesc,
            tint = contentColor,
            modifier = Modifier.size(iconSize)
        )
        Text(
            text = stringResource(
                R.string.week_summary_temp_range_compact,
                day.maxTemp.roundToInt(),
                day.minTemp.roundToInt()
            ),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = metricSize,
                lineHeight = metricSize * 1.1f
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            softWrap = false
        )
        Text(
            text = stringResource(R.string.week_summary_precip, precipMm),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = metricSize,
                lineHeight = metricSize * 1.1f
            ),
            color = contentColor.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            softWrap = false
        )
    }
}
