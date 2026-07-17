package com.example.weatherrecommender.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RecommendedActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Renders [ShareWeatherCard] into a [androidx.compose.ui.graphics.layer.GraphicsLayer],
 * captures a PNG bitmap, and launches the system share sheet.
 *
 * Kept in the UI layer so domain stays free of Android Intents.
 */
@Composable
internal fun ShareWeatherCapture(
    location: Location,
    days: List<DailyForecast>,
    tipActivity: RecommendedActivity?,
    onComplete: (success: Boolean) -> Unit
) {
    val context = LocalContext.current
    val graphicsLayer = rememberGraphicsLayer()
    var laidOut by remember { mutableIntStateOf(0) }
    var finished by remember { mutableIntStateOf(0) }

    // Transparent dialog gives the card a real window to measure/draw into before capture.
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .drawWithContent {
                    val contentDrawScope = this
                    graphicsLayer.record {
                        contentDrawScope.drawContent()
                    }
                    // Keep a faint draw so the layer is valid; dialog is brief and mostly unnoticed.
                    drawLayer(graphicsLayer)
                }
                .onGloballyPositioned { laidOut++ }
        ) {
            ShareWeatherCard(
                location = location,
                days = days,
                tipActivity = tipActivity
            )
        }
    }

    LaunchedEffect(laidOut) {
        if (laidOut == 0 || finished > 0) return@LaunchedEffect
        // Let Compose finish recording into the graphics layer.
        delay(64)
        val success = try {
            val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
            withContext(Dispatchers.IO) {
                shareWeatherBitmap(context, bitmap, location.name)
            }
        } catch (_: Exception) {
            false
        }
        finished = 1
        onComplete(success)
    }
}

/**
 * Writes [bitmap] to cache and opens [Intent.ACTION_SEND] with `image/png` via FileProvider.
 * @return true when the chooser was launched successfully.
 */
internal fun shareWeatherBitmap(context: Context, bitmap: Bitmap, cityName: String): Boolean {
    return try {
        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val file = File(dir, "weather_share_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                return false
            }
        }
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                context.getString(R.string.share_weather_subject, cityName)
            )
            putExtra(
                Intent.EXTRA_TEXT,
                context.getString(R.string.share_weather_text, cityName)
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(
                send,
                context.getString(R.string.share_weather_chooser)
            )
        )
        true
    } catch (_: Exception) {
        false
    }
}
