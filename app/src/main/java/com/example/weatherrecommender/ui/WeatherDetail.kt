package com.example.weatherrecommender.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.RankedActivity
import com.example.weatherrecommender.domain.model.ScoringThresholds
import com.example.weatherrecommender.ui.util.asUiText

private const val DAY_SWITCH_MS = 220

/**
 * Detail body inside the map bottom sheet: an edge-to-edge city hero (header overlaid),
 * a full-width row of tall day buttons, and a vertical column of ranked activities.
 * The sheet itself is locked at 60% of the screen; this column scrolls when content
 * overflows that height. Tapping a day re-ranks activities (handled by
 * [WeatherViewModel.onDaySelected]). Pull-to-refresh is home-only.
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
            .testTag("detail_sheet_body")
            .verticalScroll(rememberScrollState())
    ) {
        CityHeroOverlay(
            imageUrl = imageUrl,
            showShimmer = showLoadingShimmer && imageUrl == null,
            header = header,
            modifier = Modifier
                .fillMaxWidth()
                .height(DetailLayout.HeroHeight)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                PremiumShimmerLoadingState()
            } else if (forecast != null) {
                WeekSummarySection(
                    forecast = forecast,
                    selectedDayIndex = uiState.selectedDayIndex,
                    onDaySelected = onDaySelected,
                    compact = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DetailLayout.DayRowHeight)
                )
                Crossfade(
                    targetState = uiState.selectedDayIndex to uiState.rankedActivities,
                    animationSpec = tween(DAY_SWITCH_MS),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_activity_list"),
                    label = "day_activities"
                ) { (_, activities) ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(DetailLayout.ActivitySpacing)
                    ) {
                        activities.forEach { ranked ->
                            ActivityItem(
                                rankedActivity = ranked,
                                compact = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = DetailLayout.ActivityRowMinHeight)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Edge-to-edge photo (or placeholder/shimmer) flush to the sheet's top. The sheet Surface
 * clips this to the rounded top corners — no inner card inset. [header] sits on a top-down
 * scrim so white chrome stays readable even when [imageUrl] is missing.
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
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.72f),
                            0.55f to Color.Black.copy(alpha = 0.28f),
                            1f to Color.Transparent
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

@Composable
private fun ActivityItem(
    rankedActivity: RankedActivity,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = if (compact) 4.dp else 8.dp)
        ) {
            Icon(
                activityIcon(rankedActivity.activity),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(if (compact) 18.dp else 22.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = rankedActivity.activity.asUiText().asString(),
                        style = if (compact) {
                            MaterialTheme.typography.labelLarge
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = rankedActivity.score.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            rankedActivity.score > ScoringThresholds.SCORE_HIGH ->
                                MaterialTheme.colorScheme.primary
                            rankedActivity.score > ScoringThresholds.SCORE_MID ->
                                MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }
                if (!compact) {
                    Text(
                        text = rankedActivity.reasonKey.asUiText(rankedActivity.reasonArgs).asString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
