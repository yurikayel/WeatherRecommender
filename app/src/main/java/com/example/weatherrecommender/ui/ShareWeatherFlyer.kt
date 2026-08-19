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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.ScoringThresholds
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
import com.example.weatherrecommender.ui.util.isoDateToShortWeekday
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

private const val MAX_FLYER_ACTIVITIES = 3
private const val MAX_FLYER_DAYS = 7

/**
 * Flyer type scale — explicit sizes/weights/lineHeights (no Material default LH bloat).
 * Font family stays system/default so share PNGs stay deterministic across devices.
 */
private object FlyerType {
    val brand = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.4.sp
    )
    val city = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 36.sp
    )
    val place = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 17.sp
    )
    val meta = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.3.sp
    )
    val heroDay = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 14.sp
    )
    val heroTemp = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 46.sp
    )
    val heroConditions = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 13.sp
    )
    val section = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
    val dayLabel = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 12.sp
    )
    val dayHigh = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 13.sp
    )
    val dayLow = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 12.sp
    )
    val activityName = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp
    )
    val activityReason = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 12.sp
    )
    val score = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 19.sp
    )
    val footer = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        lineHeight = 11.sp
    )
}

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
    val hasImage = location.imageUrl != null

    Box(
        modifier = modifier
            .width(FlyerWidth)
            .height(FlyerHeight)
            .background(
                Brush.verticalGradient(
                    colors = listOf(PremiumPrimaryContainerLight, FlyerSurface, FlyerSurface)
                )
            )
    ) {
        if (hasImage) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(location.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            FlyerHeader(location = location, days = forecastDays, hasImage = hasImage)

            Spacer(Modifier.weight(1f))
            selectedDay?.let { FlyerHero(day = it, hasImage = hasImage) }

            Spacer(Modifier.weight(1f))
            FlyerForecastStrip(days = forecastDays, hasImage = hasImage)

            if (rankedActivities.isNotEmpty() && selectedDay != null) {
                Spacer(Modifier.weight(1f))
                FlyerActivities(day = selectedDay, activities = rankedActivities, hasImage = hasImage)
            }

            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.share_card_footer),
                style = FlyerType.footer,
                color = if (hasImage) Color.White.copy(alpha = 0.85f) else FlyerMuted.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FlyerHeader(location: Location, days: List<DailyForecast>, hasImage: Boolean) {
    val textPrimary = if (hasImage) Color.White else FlyerInk
    val textMuted = if (hasImage) Color.White.copy(alpha = 0.85f) else FlyerMuted
    val brandAccent = if (hasImage) PremiumPrimary else FlyerAccent

    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_weather_mark),
            contentDescription = null,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.app_title),
            style = FlyerType.brand,
            color = brandAccent
        )
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = location.name,
        style = FlyerType.city,
        color = textPrimary,
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
            style = FlyerType.place,
            color = textMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    val first = days.firstOrNull()
    val last = days.lastOrNull()
    if (first != null && last != null) {
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(
                R.string.share_card_date_range,
                isoDateToShortDate(first.date),
                isoDateToShortDate(last.date)
            ),
            style = FlyerType.meta,
            color = textMuted
        )
    }
}

@Composable
private fun FlyerHero(day: DailyForecast, hasImage: Boolean) {
    val textPrimary = if (hasImage) Color.White else FlyerInk
    val textMuted = if (hasImage) Color.White.copy(alpha = 0.85f) else FlyerMuted

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(flyerPastelFor(day.weatherCode)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = weatherCodeIcon(day.weatherCode),
                contentDescription = weatherCodeDescription(day.weatherCode),
                tint = FlyerInk,
                modifier = Modifier.size(38.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = stringResource(
                    R.string.share_card_hero_day,
                    isoDateToWeekday(day.date),
                    isoDateToShortDate(day.date)
                ),
                style = FlyerType.heroDay,
                color = textMuted
            )
            Text(
                text = stringResource(R.string.temp_degrees, day.maxTemp.roundToInt()),
                style = FlyerType.heroTemp,
                color = textPrimary
            )
            Text(
                text = stringResource(
                    R.string.share_card_hero_conditions,
                    weatherCodeDescription(day.weatherCode),
                    day.minTemp.roundToInt()
                ),
                style = FlyerType.heroConditions,
                color = textMuted
            )
        }
    }
}

@Composable
private fun FlyerForecastStrip(days: List<DailyForecast>, hasImage: Boolean) {
    FlyerSectionLabel(
        text = stringResource(R.string.share_card_forecast_label),
        hasImage = hasImage
    )
    Spacer(Modifier.height(4.dp))
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FlyerSurface.copy(alpha = 0.95f))
            .padding(horizontal = 6.dp, vertical = 6.dp)
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
        modifier = modifier.padding(horizontal = 1.dp)
    ) {
        Text(
            text = isoDateToShortWeekday(day.date),
            style = FlyerType.dayLabel,
            color = FlyerMuted,
            maxLines = 1
        )
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(flyerPastelFor(day.weatherCode)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = weatherCodeIcon(day.weatherCode),
                contentDescription = weatherCodeDescription(day.weatherCode),
                tint = FlyerInk,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = stringResource(R.string.temp_degrees, day.maxTemp.roundToInt()),
            style = FlyerType.dayHigh,
            color = FlyerInk,
            maxLines = 1
        )
        Text(
            text = stringResource(R.string.temp_degrees, day.minTemp.roundToInt()),
            style = FlyerType.dayLow,
            color = FlyerMuted,
            maxLines = 1
        )
    }
}

@Composable
private fun FlyerActivities(day: DailyForecast, activities: List<RankedActivity>, hasImage: Boolean) {
    FlyerSectionLabel(
        text = stringResource(R.string.detail_activities_for_day, isoDateToWeekday(day.date)),
        hasImage = hasImage
    )
    Spacer(Modifier.height(6.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        activities.sortedByDescending { it.score }.take(MAX_FLYER_ACTIVITIES).forEach { ranked ->
            FlyerActivityItem(ranked = ranked, hasImage = hasImage)
        }
    }
}

@Composable
private fun FlyerActivityItem(ranked: RankedActivity, hasImage: Boolean) {
    val textPrimary = if (hasImage) Color.White else FlyerInk

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = activityIcon(ranked.activity),
            contentDescription = null,
            tint = textPrimary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = ranked.activity.asUiText().asString(),
            style = FlyerType.activityName,
            color = textPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = ranked.score.toString(),
            style = FlyerType.activityName,
            fontWeight = FontWeight.Bold,
            softWrap = false,
            color = flyerScoreColor(ranked.score)
        )
    }
}

@Composable
private fun FlyerSectionLabel(text: String, hasImage: Boolean) {
    val textColor = if (hasImage) Color.White.copy(alpha = 0.9f) else FlyerMuted
    Text(
        text = text,
        style = FlyerType.section,
        color = textColor,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Mirrors the detail-screen score ring thresholds using fixed light colors. */
private fun flyerScoreColor(score: Int): Color = when {
    score > ScoringThresholds.SCORE_HIGH -> FlyerAccent
    score > ScoringThresholds.SCORE_MID -> PremiumAccent
    else -> PremiumErrorLight
}

private fun flyerPastelFor(weatherCode: Int): Color = when (weatherUiCategory(weatherCode)) {
    WeatherUiCategory.CLEAR -> PastelSunnyLight
    WeatherUiCategory.RAIN -> PastelRainLight
    WeatherUiCategory.SNOW -> PastelSnowLight
    WeatherUiCategory.THUNDERSTORM -> PastelThunderLight
    WeatherUiCategory.CLOUDY -> FlyerChip
}
