package com.example.weatherrecommender.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.theme.PastelRainLight
import com.example.weatherrecommender.theme.PastelSnowLight
import com.example.weatherrecommender.theme.PastelSunnyLight
import com.example.weatherrecommender.theme.PastelThunderLight
import com.example.weatherrecommender.theme.PremiumAccent
import com.example.weatherrecommender.theme.PremiumErrorLight
import com.example.weatherrecommender.theme.PremiumOnLightText
import com.example.weatherrecommender.theme.PremiumOnSurfaceVariantLight
import com.example.weatherrecommender.theme.PremiumPrimary
import com.example.weatherrecommender.theme.PremiumPrimaryContainerLight
import com.example.weatherrecommender.theme.PremiumPrimaryDark
import com.example.weatherrecommender.theme.PremiumSurfaceLight
import com.example.weatherrecommender.theme.PremiumSurfaceVariantLight
import com.example.weatherrecommender.ui.util.WeatherUiCategory
import com.example.weatherrecommender.ui.util.asUiText
import com.example.weatherrecommender.ui.util.isoDateToShortDate
import com.example.weatherrecommender.ui.util.isoDateToWeekday
import com.example.weatherrecommender.ui.util.weatherCodeDescription
import com.example.weatherrecommender.ui.util.weatherCodeIcon
import com.example.weatherrecommender.ui.util.weatherUiCategory
import kotlin.math.roundToInt

/** Fixed light palette so shared images stay consistent across themes and apps. */
private val FlyerInk = PremiumOnLightText
private val FlyerMuted = PremiumOnSurfaceVariantLight
private val FlyerSurface = PremiumSurfaceLight
private val FlyerChip = PremiumSurfaceVariantLight
private val FlyerAccent = PremiumPrimaryDark

/** 9:16 portrait (1080x1920 px at 3x) so the export drops straight into stories/status shares. */
private val FlyerWidth = 360.dp
private val FlyerHeight = 640.dp

private const val SCORE_HIGH = 75
private const val SCORE_MID = 40
private const val MAX_FLYER_ACTIVITIES = 4
private const val MAX_FLYER_DAYS = 7

/**
 * Vertical "weather and activities flyer" rendered for image export / Paparazzi.
 * Mirrors everything on the detail screen — location, 7-day forecast, and the ranked
 * activities for the selected day — re-laid-out as a portrait 9:16 poster.
 * Always uses a light branded look so shares look consistent in other apps.
 */
@Composable
internal fun ShareWeatherFlyer(
    location: Location,
    days: List<DailyForecast>,
    selectedDayIndex: Int = 0,
    rankedActivities: List<RankedActivity> = emptyList(),
    modifier: Modifier = Modifier
) {
    val forecastDays = days.take(MAX_FLYER_DAYS)
    val selectedDay = forecastDays.getOrNull(selectedDayIndex) ?: forecastDays.firstOrNull()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(FlyerWidth)
            .height(FlyerHeight)
            .background(
                Brush.verticalGradient(
                    colors = listOf(PremiumPrimaryContainerLight, FlyerSurface, FlyerSurface)
                )
            )
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        FlyerHeader(location = location, days = forecastDays)

        Spacer(Modifier.weight(1f))
        selectedDay?.let { FlyerHero(day = it) }

        Spacer(Modifier.weight(1f))
        FlyerForecastStrip(days = forecastDays)

        if (rankedActivities.isNotEmpty() && selectedDay != null) {
            Spacer(Modifier.weight(1f))
            FlyerActivities(day = selectedDay, activities = rankedActivities)
        }

        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.share_card_footer),
            color = FlyerMuted.copy(alpha = 0.85f),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FlyerHeader(location: Location, days: List<DailyForecast>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_weather_mark),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.app_title),
            color = FlyerAccent,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = location.name,
        color = FlyerInk,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    val subtitle = listOfNotNull(location.admin1, location.country)
        .filter { it.isNotBlank() }
        .joinToString(", ")
    if (subtitle.isNotEmpty()) {
        Text(
            text = subtitle,
            color = FlyerMuted,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    val first = days.firstOrNull()
    val last = days.lastOrNull()
    if (first != null && last != null) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.share_card_date_range,
                isoDateToShortDate(first.date),
                isoDateToShortDate(last.date)
            ),
            color = FlyerMuted,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.4.sp
        )
    }
}

/** Big selected-day highlight: pastel icon tile, hero temperature, and condition. */
@Composable
private fun FlyerHero(day: DailyForecast) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(flyerPastelFor(day.weatherCode)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = weatherCodeIcon(day.weatherCode),
                contentDescription = weatherCodeDescription(day.weatherCode),
                tint = FlyerInk,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = stringResource(
                    R.string.share_card_hero_day,
                    isoDateToWeekday(day.date),
                    isoDateToShortDate(day.date)
                ),
                color = FlyerMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(R.string.temp_degrees, day.maxTemp.roundToInt()),
                color = FlyerInk,
                fontSize = 40.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(
                    R.string.share_card_hero_conditions,
                    weatherCodeDescription(day.weatherCode),
                    day.minTemp.roundToInt()
                ),
                color = FlyerMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun FlyerForecastStrip(days: List<DailyForecast>) {
    FlyerSectionLabel(stringResource(R.string.share_card_forecast_label))
    Spacer(Modifier.height(6.dp))
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FlyerSurface.copy(alpha = 0.9f))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        days.forEach { day ->
            FlyerDayColumn(day = day, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun FlyerDayColumn(
    day: DailyForecast,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 2.dp)
    ) {
        Text(
            text = isoDateToWeekday(day.date),
            color = FlyerMuted,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(flyerPastelFor(day.weatherCode)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = weatherCodeIcon(day.weatherCode),
                contentDescription = weatherCodeDescription(day.weatherCode),
                tint = FlyerInk,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.temp_degrees, day.maxTemp.roundToInt()),
            color = FlyerInk,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = stringResource(R.string.temp_degrees, day.minTemp.roundToInt()),
            color = FlyerMuted,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun FlyerActivities(day: DailyForecast, activities: List<RankedActivity>) {
    FlyerSectionLabel(
        stringResource(R.string.detail_activities_for_day, isoDateToWeekday(day.date))
    )
    Spacer(Modifier.height(6.dp))
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        activities.take(MAX_FLYER_ACTIVITIES).forEach { ranked ->
            FlyerActivityRow(ranked)
        }
    }
}

@Composable
private fun FlyerActivityRow(ranked: RankedActivity) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FlyerSurface.copy(alpha = 0.9f))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(PremiumPrimary.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = activityIcon(ranked.activity),
                contentDescription = null,
                tint = FlyerAccent,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ranked.activity.asUiText().asString(),
                color = FlyerInk,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = ranked.reasonKey.asUiText(ranked.reasonArgs).asString(),
                color = FlyerMuted,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(FlyerChip)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ranked.score.coerceIn(0, 100) / 100f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(flyerScoreColor(ranked.score))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = ranked.score.toString(),
            color = flyerScoreColor(ranked.score),
            fontSize = 15.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FlyerSectionLabel(text: String) {
    Text(
        text = text,
        color = FlyerMuted,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Mirrors the detail-screen score ring thresholds using fixed light colors. */
private fun flyerScoreColor(score: Int): Color = when {
    score > SCORE_HIGH -> FlyerAccent
    score > SCORE_MID -> PremiumAccent
    else -> PremiumErrorLight
}

private fun flyerPastelFor(weatherCode: Int): Color = when (weatherUiCategory(weatherCode)) {
    WeatherUiCategory.CLEAR -> PastelSunnyLight
    WeatherUiCategory.RAIN -> PastelRainLight
    WeatherUiCategory.SNOW -> PastelSnowLight
    WeatherUiCategory.THUNDERSTORM -> PastelThunderLight
    WeatherUiCategory.CLOUDY -> FlyerChip
}
