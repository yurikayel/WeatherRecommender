package com.example.weatherrecommender.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.weatherrecommender.R
import com.example.weatherrecommender.domain.model.DailyForecast
import com.example.weatherrecommender.domain.model.Location
import com.example.weatherrecommender.domain.model.RankedActivity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/**
 * Renders [ShareWeatherFlyer] into a [androidx.compose.ui.graphics.layer.GraphicsLayer],
 * captures a PNG bitmap, and launches the system share sheet.
 *
 * Kept in the UI layer so domain stays free of Android Intents.
 */
@Composable
internal fun ShareWeatherCapture(
    location: Location,
    days: List<DailyForecast>,
    selectedDayIndex: Int,
    rankedActivities: List<RankedActivity>,
    onComplete: (ShareWeatherOutcome) -> Unit,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO
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
            ShareWeatherFlyer(
                location = location,
                days = days,
                selectedDayIndex = selectedDayIndex,
                rankedActivities = rankedActivities
            )
        }
    }

    LaunchedEffect(laidOut) {
        if (laidOut == 0 || finished > 0) return@LaunchedEffect
        // Let Compose finish recording into the graphics layer.
        delay(64.milliseconds)
        val outcome = try {
            val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
            withContext(ioDispatcher) {
                shareWeatherBitmap(context, bitmap, location.name)
            }
        } catch (_: Exception) {
            ShareWeatherOutcome(shared = false)
        }
        finished = 1
        onComplete(outcome)
    }
}

/**
 * Writes [bitmap] to cache and opens [Intent.ACTION_SEND] with `image/png` via FileProvider.
 * Also copies the same image into Downloads when possible; a Downloads failure does not
 * prevent sharing.
 */
internal fun shareWeatherBitmap(context: Context, bitmap: Bitmap, cityName: String): ShareWeatherOutcome {
    return try {
        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val file = File(dir, "weather_share_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                return ShareWeatherOutcome(shared = false)
            }
        }

        val downloadsFileName = buildDownloadsFileName(cityName)
        val savedToDownloads = saveBitmapToDownloads(context, bitmap, downloadsFileName)

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
        ShareWeatherOutcome(shared = true, savedToDownloads = savedToDownloads)
    } catch (_: Exception) {
        ShareWeatherOutcome(shared = false)
    }
}

/**
 * Saves [bitmap] into the public Downloads folder.
 * Uses MediaStore on Android Q+ (no storage permission). On older APIs writes directly
 * when [Manifest.permission.WRITE_EXTERNAL_STORAGE] is already granted.
 */
internal fun saveBitmapToDownloads(context: Context, bitmap: Bitmap, fileName: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveBitmapToDownloadsMediaStore(context, bitmap, fileName)
        } else {
            saveBitmapToDownloadsLegacy(context, bitmap, fileName)
        }
    } catch (_: Exception) {
        false
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
@SuppressLint("NewApi")
private fun saveBitmapToDownloadsMediaStore(
    context: Context,
    bitmap: Bitmap,
    fileName: String
): Boolean {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
    val success = runCatching {
        val wrote = resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } == true
        if (wrote) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            resolver.delete(uri, null, null)
            false
        }
    }.getOrElse {
        runCatching { resolver.delete(uri, null, null) }
        false
    }
    return success
}

@Suppress("DEPRECATION")
private fun saveBitmapToDownloadsLegacy(
    context: Context,
    bitmap: Bitmap,
    fileName: String
): Boolean {
    val hasWrite = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) == PackageManager.PERMISSION_GRANTED
    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val ready = hasWrite && (downloads.exists() || downloads.mkdirs())
    if (!ready) return false

    val file = File(downloads, fileName)
    val wrote = FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    if (wrote) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(file.absolutePath),
            arrayOf("image/png"),
            null
        )
    }
    return wrote
}

internal fun buildDownloadsFileName(cityName: String, date: Date = Date()): String {
    val city = sanitizeCityForFilename(cityName)
    val datePart = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
    return "WeatherRecommender_${city}_$datePart.png"
}

internal fun sanitizeCityForFilename(cityName: String): String {
    val sanitized = cityName
        .replace(Regex("[^A-Za-z0-9]+"), "_")
        .trim('_')
        .take(40)
    return sanitized.ifEmpty { "City" }
}
