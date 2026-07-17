package com.example.weatherrecommender.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.weatherrecommender.R

private val StampWidth = 112.dp
private val StampHeight = 140.dp
private const val STAMP_ROTATION_DEGREES = -2.2f

/**
 * Compact postage-stamp city photo for the detail sheet.
 * Call only when [imageUrl] is non-blank; Coil load failures leave an empty frame
 * without affecting forecast layout.
 */
@Composable
internal fun CityPostageStamp(
    imageUrl: String,
    cityName: String,
    attribution: String?
) {
    val stampCd = stringResource(R.string.detail_city_stamp_cd, cityName)
    val frameColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val paper = MaterialTheme.colorScheme.surface
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f), 0f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer { rotationZ = STAMP_ROTATION_DEGREES }
                .shadow(6.dp, RoundedCornerShape(2.dp), clip = false)
                .semantics { contentDescription = stampCd }
        ) {
            Box(
                modifier = Modifier
                    .size(StampWidth, StampHeight)
                    .background(paper, RoundedCornerShape(2.dp))
                    .drawBehind {
                        val inset = 5.dp.toPx()
                        val stroke = Stroke(width = 1.5.dp.toPx(), pathEffect = dashEffect)
                        val path = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = inset,
                                    top = inset,
                                    right = size.width - inset,
                                    bottom = size.height - inset,
                                    cornerRadius = CornerRadius(2.dp.toPx())
                                )
                            )
                        }
                        drawPath(path = path, color = frameColor, style = stroke)
                    }
                    .border(2.dp, frameColor, RoundedCornerShape(2.dp))
                    .padding(10.dp)
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(1.dp))
                )
            }
        }
        attribution?.takeIf { it.isNotBlank() }?.let { credit ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.detail_wikipedia_attribution, credit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
    }
}

@Composable
internal fun CityDescriptionSection(
    cityName: String,
    description: String,
    attribution: String?,
    showAttribution: Boolean = true
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.detail_about_city, cityName),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )
        if (showAttribution) {
            attribution?.takeIf { it.isNotBlank() }?.let { credit ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.detail_wikipedia_attribution, credit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }
        }
    }
}
