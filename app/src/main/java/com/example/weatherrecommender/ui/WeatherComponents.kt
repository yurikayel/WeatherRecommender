package com.example.weatherrecommender.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

/** Skeleton placeholder shown while a city's forecast loads. Mirrors the loaded detail body. */
@Composable
internal fun PremiumShimmerLoadingState() {
    Column {
        Box(Modifier.width(120.dp).height(28.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            listOf(108.dp, 96.dp, 58.dp, 48.dp).forEach { nameWidth ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.size(24.dp).clip(CircleShape).shimmerEffect())
                    Box(
                        Modifier.width(nameWidth).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect()
                    )
                    Box(Modifier.width(24.dp).height(18.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Box(Modifier.width(72.dp).height(28.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(7) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
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
 * First row inside the scrolling sheet: city label + theme toggle on home; back, city, share, and
 * theme on detail. No chrome is drawn over the map itself.
 */
@Composable
internal fun WeatherSheetHeader(
    title: String,
    inDetail: Boolean,
    canShare: Boolean,
    shareInProgress: Boolean,
    isDarkTheme: Boolean,
    mapFullyCollapsed: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    searchQuery: String = "",
    isSearching: Boolean = false,
    onQueryChange: (String) -> Unit = {},
    onInfoClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.then(
            if (mapFullyCollapsed) Modifier.statusBarsPadding() else Modifier
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
            if (inDetail && onInfoClick != null) {
                IconButton(onClick = onInfoClick) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = stringResource(R.string.detail_info)
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
