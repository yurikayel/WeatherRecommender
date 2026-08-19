package com.example.weatherrecommender.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.DownhillSkiing
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Surfing
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.RecommendedActivity

/** Maps an activity to its representative Material icon. Shared across home and detail. */
internal fun activityIcon(activity: RecommendedActivity): ImageVector = when (activity) {
    RecommendedActivity.OUTDOOR_SIGHTSEEING -> Icons.Outlined.Park
    RecommendedActivity.INDOOR_SIGHTSEEING -> Icons.Outlined.Museum
    RecommendedActivity.SURFING -> Icons.Outlined.Surfing
    RecommendedActivity.SKIING -> Icons.Outlined.DownhillSkiing
}

/**
 * Weight/size metrics shared by [DetailContent] and [PremiumShimmerLoadingState] so loading
 * never flashes a different geometry (overlay hero + 7 tall day buttons + activity rows).
 * Heights are fixed so the locked 60% detail sheet can inner-scroll; the hero (and usually
 * the day row) is visible at that height, and scrolling reveals activities.
 */
internal object DetailLayout {
    const val ForecastDays = 7
    const val ActivitySlots = 4
    val HeroHeight = 168.dp
    val DayRowHeight = 140.dp
    val ActivityRowMinHeight = 64.dp
    val BlockSpacing = 8.dp
    val AfterDayRowSpacing = 12.dp
    val ActivitySpacing = 6.dp
    val DayButtonSpacing = 4.dp
    val DayButtonCorner = 12.dp
    val SheetHorizontalPadding = 8.dp
    val SheetBottomPadding = 16.dp
}

/**
 * Skeleton for the day-row + activity column under the overlay hero. Geometry matches
 * [DetailContent]'s loaded body. The hero (including header overlay) is drawn by
 * [DetailContent] itself so loading never drops the overlay chrome.
 */
@Composable
internal fun PremiumShimmerLoadingState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DetailLayout.AfterDayRowSpacing)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(DetailLayout.DayButtonSpacing),
            modifier = Modifier
                .fillMaxWidth()
                .height(DetailLayout.DayRowHeight)
        ) {
            repeat(DetailLayout.ForecastDays) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(DetailLayout.DayButtonCorner))
                        .shimmerEffect()
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(DetailLayout.ActivitySpacing),
            modifier = Modifier.fillMaxWidth()
        ) {
            repeat(DetailLayout.ActivitySlots) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(DetailLayout.ActivityRowMinHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .shimmerEffect()
                )
            }
        }
    }
}

/** A subtle pulsing background used for skeleton placeholders. */
@Composable
internal fun Modifier.shimmerEffect(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    return this.background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
}

/** Rounded search field used on the home screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = {
            Text(
                stringResource(R.string.search_hint),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(R.string.search),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingIcon = {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.clear_search),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        )
    )
}



/**
 * First row of sheet chrome: city label + theme toggle on home; back, city, Wikipedia,
 * share, and theme on detail. Home places this below the sheet edge. Detail overlays it on
 * the city hero ([overlayOnHero]) so it does not consume a separate vertical block.
 */
@Composable
internal fun WeatherSheetHeader(
    title: String,
    inDetail: Boolean,
    canShare: Boolean,
    shareInProgress: Boolean,
    isDarkTheme: Boolean,
    sheetFullyExpanded: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    searchQuery: String = "",
    isSearching: Boolean = false,
    onQueryChange: (String) -> Unit = {},
    wikipediaUrl: String? = null,
    onOpenWikipedia: (String) -> Unit = {},
    overlayOnHero: Boolean = false,
    modifier: Modifier = Modifier
) {
    val contentColor = if (overlayOnHero) Color.White else LocalContentColor.current
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = modifier.then(
                if (sheetFullyExpanded) Modifier.statusBarsPadding() else Modifier
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (inDetail) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                } else {
                    CustomSearchBar(
                        query = searchQuery,
                        onQueryChange = onQueryChange,
                        isSearching = isSearching,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 8.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (inDetail) {
                    IconButton(
                        onClick = { wikipediaUrl?.let(onOpenWikipedia) },
                        enabled = wikipediaUrl != null
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_wikipedia),
                            contentDescription = stringResource(R.string.detail_wikipedia)
                        )
                    }
                }
                if (canShare) {
                    IconButton(onClick = onShare, enabled = !shareInProgress) {
                        Icon(
                            Icons.Filled.Share,
                            contentDescription = stringResource(R.string.share_weather)
                        )
                    }
                }
                ThemeToggleIcon(isDarkTheme = isDarkTheme, onToggleTheme = onToggleTheme)
            }
        }
    }
}

@Composable
private fun ThemeToggleIcon(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    IconButton(onClick = onToggleTheme) {
        Icon(
            imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
            contentDescription = stringResource(
                if (isDarkTheme) {
                    R.string.theme_switch_to_light
                } else {
                    R.string.theme_switch_to_dark
                }
            )
        )
    }
}
