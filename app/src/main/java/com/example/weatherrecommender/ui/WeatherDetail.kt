@file:Suppress("TooManyFunctions") // Hero/forecast/activity rows split from DetailContent.

package com.example.weatherrecommender.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Detail body inside the map bottom sheet. The sheet is locked at 60% and this column
 * fills that height — there is no [androidx.compose.foundation.verticalScroll]. The 16:9
 * hero takes its aspect-ratio height; day chips stay [DetailLayout.DayRowHeight]; ranked
 * activity rows share the remaining space via [Modifier.weight] so every row stays on
 * screen. Tapping a day only swaps the activity list via [WeatherViewModel.onDaySelected].
 * Pull-to-refresh is home-only.
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("detail_sheet_body")
    ) {
        CityHeroOverlay(
            imageUrl = imageUrl,
            showShimmer = showLoadingShimmer && imageUrl == null,
            header = header,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(DetailLayout.HeroAspectRatio)
        )

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
            uiState.error?.let { error ->
                Text(
                    text = stringResource(R.string.error_prefix, error.asString()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            uiState.syncError?.let { syncError ->
                SyncErrorBanner(syncError.asString())
            }

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
}

/** Day chips plus a weighted column of ranked activities for the selected day. */
@Composable
private fun ColumnScope.DetailForecastBody(
    forecast: WeatherForecast,
    selectedDayIndex: Int,
    rankedActivities: List<RankedActivity>,
    onDaySelected: (Int) -> Unit
) {
    WeekSummarySection(
        forecast = forecast,
        selectedDayIndex = selectedDayIndex,
        onDaySelected = onDaySelected,
        modifier = Modifier.fillMaxWidth()
    )
    Crossfade(
        targetState = selectedDayIndex to rankedActivities,
        animationSpec = tween(DAY_SWITCH_MS),
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag("detail_activity_list"),
        label = "day_activities"
    ) { (_, activities) ->
        Column(modifier = Modifier.fillMaxSize()) {
            activities.forEachIndexed { index, ranked ->
                ActivityItem(
                    rankedActivity = ranked,
                    isTopPick = index == 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

/**
 * Edge-to-edge photo (or placeholder/shimmer) flush to the sheet’s top. The sheet Surface
 * clips this to the rounded top corners — no inner card inset. Height comes from a 16:9
 * [aspectRatio] (placeholder uses the same box so layout does not jump). [header] sits on a
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
