package com.example.weatherrecommender.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.ui.util.asUiText
import com.example.weatherrecommender.ui.util.isoDateToDayOfMonth
import com.example.weatherrecommender.ui.util.isoDateToWeekday
import com.example.weatherrecommender.ui.util.weatherCodeDescription
import com.example.weatherrecommender.ui.util.weatherCodeIcon
import kotlin.math.roundToInt

/**
 * Consolidated 7-day dashboard: weather, temps, precipitation, and top activity per day.
 * Rows are tappable shortcuts that sync with the compact day chips below.
 */
@Composable
internal fun WeekSummarySection(
    forecast: WeatherForecast,
    weekTopActivities: List<RankedActivity?>,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.detail_week_summary_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.size(12.dp))
        forecast.dailyForecasts.forEachIndexed { index, day ->
            val topActivity = weekTopActivities.getOrNull(index)
            WeekSummaryRow(
                day = day,
                dayIndex = index,
                topActivity = topActivity,
                selected = index == selectedDayIndex,
                onClick = { onDaySelected(index) }
            )
            if (index < forecast.dailyForecasts.lastIndex) {
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}

@Composable
private fun WeekSummaryRow(
    day: DailyForecast,
    dayIndex: Int,
    topActivity: RankedActivity?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(250),
        label = "week_row_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(250),
        label = "week_row_content"
    )

    val weekday = isoDateToWeekday(day.date)
    val dayOfMonth = isoDateToDayOfMonth(day.date)
    val weatherDesc = weatherCodeDescription(day.weatherCode)
    val rowLabel = stringResource(
        R.string.week_summary_row_cd,
        weekday,
        dayOfMonth,
        day.maxTemp.roundToInt(),
        day.minTemp.roundToInt(),
        topActivity?.activity?.asUiText()?.asString().orEmpty(),
        topActivity?.score ?: 0
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .then(
                if (selected) Modifier
                else Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    RoundedCornerShape(14.dp)
                )
            )
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = rowLabel
                testTag = "week_summary_day_$dayIndex"
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.width(80.dp)) {
                Text(
                    text = weekday,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dayOfMonth,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1
                )
            }
            Icon(
                imageVector = weatherCodeIcon(day.weatherCode),
                contentDescription = weatherDesc,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(
                    R.string.week_summary_temp_range,
                    day.maxTemp.roundToInt(),
                    day.minTemp.roundToInt()
                ),
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(
                    R.string.week_summary_precip,
                    day.precipitationSum.roundToInt()
                ),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.85f),
                maxLines = 1
            )
        }
        topActivity?.let { ranked ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = activityIcon(ranked.activity),
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = ranked.activity.asUiText().asString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 96.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = ranked.score.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
            }
        }
    }
}
