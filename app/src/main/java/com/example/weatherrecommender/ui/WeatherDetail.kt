@file:Suppress("TooManyFunctions") // Hero/forecast/activity rows split from DetailContent.

package com.example.weatherrecommender.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.WeatherForecast
import com.example.weatherrecommender.ui.util.asUiText

private const val DAY_SWITCH_MS = 220

/**
 * Detail body inside the map bottom sheet. The sheet stays locked at 60%. The 16:9 hero
 * shrinks when leftover space cannot hold day chips plus four minimum activity rows; that
 * lower column then scrolls so rows cannot clip on a short device or large font.
 */
@Composable
internal fun DetailContent(
    uiState: WeatherUiState,
    onDaySelected: (Int) -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val imageUrl = uiState.selectedLocation?.imageUrl ?: uiState.forecast?.location?.imageUrl
    val showLoadingShimmer = uiState.isLoadingForecast && uiState.forecast == null
    val forecast = uiState.forecast
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("detail_sheet_body")
    ) {
        val heroHeight = with(density) {
            detailHeroHeightPx(
                sheetWidthPx = constraints.maxWidth.toFloat(),
                sheetHeightPx = constraints.maxHeight.toFloat(),
                minBodyPx = DetailLayout.MinScrollBodyHeight.toPx()
            ).toDp()
        }
        Column(Modifier.fillMaxSize()) {
            CityHeroOverlay(
                imageUrl = imageUrl,
                showShimmer = showLoadingShimmer && imageUrl == null,
                header = header,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
            )
            DetailBodyColumn(
                uiState = uiState,
                forecast = forecast,
                showLoadingShimmer = showLoadingShimmer,
                onDaySelected = onDaySelected
            )
        }
    }
}

/** Padded chips + activities (or shimmer) under the hero, including this lane's error. */
@Composable
private fun ColumnScope.DetailBodyColumn(
    uiState: WeatherUiState,
    forecast: WeatherForecast?,
    showLoadingShimmer: Boolean,
    onDaySelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(
                start = DetailLayout.SheetHorizontalPadding,
                end = DetailLayout.SheetHorizontalPadding,
                top = DetailLayout.BlockSpacing,
                bottom = DetailLayout.SheetBottomPadding
            ),
        verticalArrangement = Arrangement.spacedBy(DetailLayout.AfterDayRowSpacing)
    ) {
        DetailForecastLaneError(uiState)
        if (showLoadingShimmer) {
            PremiumShimmerLoadingState(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else if (forecast != null) {
            DetailForecastBody(
                forecast = forecast,
                selectedDayIndex = uiState.selectedDayIndex,
                rankedActivities = uiState.rankedActivities,
                onDaySelected = onDaySelected
            )
        }
    }
}

/** Forecast-lane failure: blocking copy with no cache, sync banner when days are on screen. */
@Composable
private fun DetailForecastLaneError(uiState: WeatherUiState) {
    val error = uiState.forecastFetch.errorOrNull() ?: return
    if (uiState.forecast == null) {
        Text(
            text = stringResource(R.string.error_prefix, error.asString()),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    } else {
        SyncErrorBanner(error.asString())
    }
}

/** Day chips plus ranked activities; scrolls when leftover height is below the minimum body. */
@Composable
private fun ColumnScope.DetailForecastBody(
    forecast: WeatherForecast,
    selectedDayIndex: Int,
    rankedActivities: List<RankedActivity>,
    onDaySelected: (Int) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        val overflow = maxHeight < DetailLayout.MinScrollBodyHeight
        Column(
            modifier = if (overflow) {
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("detail_sheet_scroll")
            } else {
                Modifier.fillMaxSize()
            },
            verticalArrangement = Arrangement.spacedBy(DetailLayout.AfterDayRowSpacing)
        ) {
            WeekSummarySection(
                forecast = forecast,
                selectedDayIndex = selectedDayIndex,
                onDaySelected = onDaySelected,
                modifier = Modifier.fillMaxWidth()
            )
            DayActivityList(
                selectedDayIndex = selectedDayIndex,
                rankedActivities = rankedActivities,
                fillRemaining = !overflow
            )
        }
    }
}

/** Crossfades the selected day's ranked rows; weighted when filling, min-height when scrolling. */
@Composable
private fun ColumnScope.DayActivityList(
    selectedDayIndex: Int,
    rankedActivities: List<RankedActivity>,
    fillRemaining: Boolean
) {
    Crossfade(
        targetState = selectedDayIndex to rankedActivities,
        animationSpec = tween(DAY_SWITCH_MS),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (fillRemaining) Modifier.weight(1f) else Modifier)
            .testTag("detail_activity_list"),
        label = "day_activities"
    ) { (_, activities) ->
        Column(modifier = if (fillRemaining) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
            activities.forEachIndexed { index, ranked ->
                ActivityItem(
                    rankedActivity = ranked,
                    isTopPick = index == 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (fillRemaining) {
                                Modifier.weight(1f)
                            } else {
                                Modifier.heightIn(min = DetailLayout.MinActivityRowHeight)
                            }
                        )
                )
            }
        }
    }
}

/**
 * Edge-to-edge photo (or placeholder/shimmer) flush to the sheet’s top. The sheet Surface
 * clips this to the rounded top corners — no inner card inset. Height is chosen by
 * [detailHeroHeightPx] (16:9 when the body fits, otherwise a shorter cap). [header] sits on a
 * short top scrim so chrome stays readable while the photo still meets the sheet edge.
 */
@Composable
private fun CityHeroOverlay(
    imageUrl: String?,
    showShimmer: Boolean,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.testTag("detail_hero")) {
        when {
            imageUrl != null -> {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            showShimmer -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .shimmerEffect()
                )
            }
            else -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.55f),
                            0.38f to Color.Black.copy(alpha = 0.18f),
                            0.62f to Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            header()
        }
    }
}

/** Error-container banner for background sync failures that left cached days on screen. */
@Composable
private fun SyncErrorBanner(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(
                R.string.sync_error_with_warning,
                stringResource(R.string.sync_error_prefix, message)
            ),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/** Compact ranked row: icon, score, title, and one-line reason. */
@Composable
private fun ActivityItem(
    rankedActivity: RankedActivity,
    isTopPick: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Icon(
            activityIcon(rankedActivity.activity),
            contentDescription = null,
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = rankedActivity.score.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (isTopPick) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isTopPick) colorScheme.primary else colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 28.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = rankedActivity.activity.asUiText().asString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = rankedActivity.reasonKey.asUiText(rankedActivity.reasonArgs).asString(),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
