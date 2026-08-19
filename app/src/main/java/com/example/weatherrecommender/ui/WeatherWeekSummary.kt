package com.example.weatherrecommender.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
 * Compact equal-width row of day chips. Today is index 0 (selected by default in UI state).
 * Each chip shows weekday + date, weather icon, temp range, and precipitation — no per-day
 * activity name/score and no section title. Tapping updates [selectedDayIndex] via [onDaySelected].
 */
@Composable
internal fun WeekSummarySection(
    forecast: WeatherForecast,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit,
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
                onClick = { onDaySelected(index) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WeekDayButton(
    day: DailyForecast,
    dayIndex: Int,
    selected: Boolean,
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
            .padding(horizontal = 2.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = shortWeekday,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 11.sp
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
                fontSize = 13.sp,
                lineHeight = 14.sp
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
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = stringResource(
                R.string.week_summary_temp_range_compact,
                day.maxTemp.roundToInt(),
                day.minTemp.roundToInt()
            ),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                lineHeight = 10.sp
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
                fontSize = 9.sp,
                lineHeight = 10.sp
            ),
            color = contentColor.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            softWrap = false
        )
    }
}
