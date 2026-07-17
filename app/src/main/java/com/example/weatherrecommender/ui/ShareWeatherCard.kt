package com.example.weatherrecommender.ui

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RecommendedActivity
import com.example.weatherrecommender.theme.PastelRainLight
import com.example.weatherrecommender.theme.PastelSnowLight
import com.example.weatherrecommender.theme.PastelSunnyLight
import com.example.weatherrecommender.theme.PastelThunderLight
import com.example.weatherrecommender.theme.PremiumOnLightText
import com.example.weatherrecommender.theme.PremiumOnSurfaceVariantLight
import com.example.weatherrecommender.theme.PremiumPrimary
import com.example.weatherrecommender.theme.PremiumPrimaryContainerLight
import com.example.weatherrecommender.theme.PremiumPrimaryDark
import com.example.weatherrecommender.theme.PremiumSurfaceLight
import com.example.weatherrecommender.theme.PremiumSurfaceVariantLight
import com.example.weatherrecommender.ui.util.WeatherUiCategory
import com.example.weatherrecommender.ui.util.asUiText
import com.example.weatherrecommender.ui.util.isoDateToWeekday
import com.example.weatherrecommender.ui.util.weatherCodeDescription
import com.example.weatherrecommender.ui.util.weatherCodeIcon
import com.example.weatherrecommender.ui.util.weatherUiCategory
import kotlin.math.roundToInt

/** Fixed light palette so shared images stay consistent across themes and apps. */
private val ShareCardInk = PremiumOnLightText
private val ShareCardMuted = PremiumOnSurfaceVariantLight
private val ShareCardSurface = PremiumSurfaceLight
private val ShareCardChip = PremiumSurfaceVariantLight
private val ShareCardAccent = PremiumPrimaryDark

/**
 * Polished 7-day weather summary card rendered for image export / Paparazzi.
 * Always uses a light branded look so shares look consistent in other apps.
 */
@Composable
internal fun ShareWeatherCard(
    location: Location,
    days: List<DailyForecast>,
    tipActivity: RecommendedActivity? = null,
    modifier: Modifier = Modifier
) {
    val forecastDays = days.take(7)
    Column(
        modifier = modifier
            .width(360.dp)
            .shadow(12.dp, RoundedCornerShape(28.dp), clip = false)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(PremiumPrimaryContainerLight, ShareCardSurface)
                )
            )
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Text(
            text = stringResource(R.string.app_title),
            color = ShareCardAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = location.name,
            color = ShareCardInk,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        location.country?.takeIf { it.isNotBlank() }?.let { country ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = country,
                color = ShareCardMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.share_card_forecast_label),
            color = ShareCardMuted,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            forecastDays.forEach { day ->
                ShareDayColumn(day = day, modifier = Modifier.weight(1f))
            }
        }

        tipActivity?.let { activity ->
            Spacer(Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ShareCardSurface.copy(alpha = 0.85f))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PremiumPrimary.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = activityIcon(activity),
                        contentDescription = null,
                        tint = ShareCardAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.share_card_tip_label),
                        color = ShareCardMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = activity.asUiText().asString(),
                        color = ShareCardInk,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.share_card_footer),
            color = ShareCardMuted.copy(alpha = 0.85f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ShareDayColumn(
    day: DailyForecast,
    modifier: Modifier = Modifier
) {
    val pastel = sharePastelFor(day.weatherCode)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 2.dp)
    ) {
        Text(
            text = isoDateToWeekday(day.date),
            color = ShareCardMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(pastel),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = weatherCodeIcon(day.weatherCode),
                contentDescription = weatherCodeDescription(day.weatherCode),
                tint = ShareCardInk,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.temp_degrees, day.maxTemp.roundToInt()),
            color = ShareCardInk,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = stringResource(R.string.temp_degrees, day.minTemp.roundToInt()),
            color = ShareCardMuted,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

private fun sharePastelFor(weatherCode: Int): Color = when (weatherUiCategory(weatherCode)) {
    WeatherUiCategory.CLEAR -> PastelSunnyLight
    WeatherUiCategory.RAIN -> PastelRainLight
    WeatherUiCategory.SNOW -> PastelSnowLight
    WeatherUiCategory.THUNDERSTORM -> PastelThunderLight
    WeatherUiCategory.CLOUDY -> ShareCardChip
}
