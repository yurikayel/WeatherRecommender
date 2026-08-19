@file:Suppress("TooManyFunctions") // Chip color/copy extracted from WeekDayButton.

package com.example.weatherrecommender.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * Equal-width row of day chips filling the parent. Today is index 0 (selected by default).
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
        modifier = modifier
            .fillMaxWidth()
            .height(DetailLayout.DayRowHeight),
        horizontalArrangement = Arrangement.spacedBy(DetailLayout.DayButtonSpacing)
    ) {
        forecast.dailyForecasts.forEachIndexed { index, day ->
            WeekDayButton(
                day = day,
                dayIndex = index,
                selected = index == selectedDayIndex,
                onClick = { onDaySelected(index) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

/** One tall chip: weekday, date, weather icon, temp range, and precip. */
@Composable
private fun WeekDayButton(
    day: DailyForecast,
    dayIndex: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = rememberWeekDayChipColors(selected)
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
            .background(colors.background)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = rowLabel
                testTag = "week_summary_day_$dayIndex"
            }
            .padding(horizontal = 2.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        WeekDayChipCopy(
            shortWeekday = shortWeekday,
            dayOfMonth = dayOfMonth,
            weatherDesc = weatherDesc,
            weatherCode = day.weatherCode,
            maxTemp = day.maxTemp.roundToInt(),
            minTemp = day.minTemp.roundToInt(),
            precipMm = precipMm,
            colors = colors
        )
    }
}

/** Selected = filled primary; unselected = tonal container. */
@Composable
private fun rememberWeekDayChipColors(selected: Boolean): WeekDayChipColors {
    val colorScheme = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (selected) colorScheme.primary else colorScheme.surfaceContainerHighest,
        animationSpec = tween(250),
        label = "week_day_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colorScheme.onPrimary else colorScheme.onSurface,
        animationSpec = tween(250),
        label = "week_day_content"
    )
    val secondaryColor by animateColorAsState(
        targetValue = if (selected) {
            colorScheme.onPrimary.copy(alpha = 0.80f)
        } else {
            colorScheme.onSurfaceVariant
        },
        animationSpec = tween(250),
        label = "week_day_secondary"
    )
    return WeekDayChipColors(background, contentColor, secondaryColor)
}

private data class WeekDayChipColors(
    val background: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color,
    val secondary: androidx.compose.ui.graphics.Color
)

/** Stacked weekday, date number, icon, temps, and precip inside a day chip. */
@Composable
private fun WeekDayChipCopy(
    shortWeekday: String,
    dayOfMonth: String,
    weatherDesc: String,
    weatherCode: Int,
    maxTemp: Int,
    minTemp: Int,
    precipMm: Int,
    colors: WeekDayChipColors
) {
    Text(
        text = shortWeekday,
        style = MaterialTheme.typography.labelLarge,
        color = colors.secondary,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center,
        softWrap = false
    )
    Text(
        text = dayOfMonth,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = colors.content,
        maxLines = 1,
        textAlign = TextAlign.Center
    )
    Icon(
        imageVector = weatherCodeIcon(weatherCode),
        contentDescription = weatherDesc,
        tint = colors.content,
        modifier = Modifier.size(18.dp)
    )
    Text(
        text = stringResource(R.string.week_summary_temp_range_compact, maxTemp, minTemp),
        style = MaterialTheme.typography.labelSmall,
        color = colors.content,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center,
        softWrap = false
    )
    Text(
        text = stringResource(R.string.week_summary_precip, precipMm),
        style = MaterialTheme.typography.labelSmall,
        color = colors.secondary,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center,
        softWrap = false
    )
}
