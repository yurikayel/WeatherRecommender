package com.example.weatherrecommender.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "theme_preferences"
)

private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
private val KEY_THEME_SOURCE = stringPreferencesKey("theme_source")

/**
 * Persisted appearance preference.
 *
 * `null` on [ThemePreferences.themeMode] means first launch (no key written yet).
 * First-run settling writes [ThemeMode.CYCLE] so cold start is not unset. Cycle follows
 * day/night at the device; Light and Dark are sticky user locks. Legacy `"system"` rows
 * map to [ThemeMode.CYCLE] (day/night, not OS follow).
 */
enum class ThemeMode {
    CYCLE,
    LIGHT,
    DARK
}

/**
 * Who last wrote [ThemeMode]. [ThemeSource.USER] is a Light/Dark lock that GPS must not
 * overwrite. [ThemeSource.CLOCK] marks automatic Cycle (first-run or the Cycle toggle).
 * [ThemeSource.GPS] remains for legacy first-run rows.
 */
enum class ThemeSource {
    CLOCK,
    GPS,
    USER
}

/** Resolves whether Compose should use the dark color scheme for a stored preference. */
fun ThemeMode.resolveDarkTheme(isNight: Boolean): Boolean = when (this) {
    ThemeMode.CYCLE -> isNight
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Like [ThemeMode.resolveDarkTheme], with first-run unset treated as Cycle until
 * [ThemePreferences] is written.
 */
fun ThemeMode?.resolveRenderedDarkTheme(isNight: Boolean): Boolean =
    (this ?: ThemeMode.CYCLE).resolveDarkTheme(isNight)

/** Light → Dark → Cycle → Light. Unset is treated as Cycle. */
fun ThemeMode?.nextToggleMode(): ThemeMode = when (this) {
    ThemeMode.LIGHT -> ThemeMode.DARK
    ThemeMode.DARK -> ThemeMode.CYCLE
    ThemeMode.CYCLE, null -> ThemeMode.LIGHT
}

/** Cycle is automatic (not a lock); Light/Dark from the toggle are user locks. */
fun ThemeMode.sourceWhenSelected(): ThemeSource =
    if (this == ThemeMode.CYCLE) ThemeSource.CLOCK else ThemeSource.USER

@Singleton
class ThemePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    val themeMode: Flow<ThemeMode?> = context.themeDataStore.data.map { prefs ->
        prefs.storedThemeMode()
    }

    /** Current stored mode, or null on first launch. */
    suspend fun currentMode(): ThemeMode? = themeMode.first()

    /**
     * Who wrote the stored mode. Missing source with an existing mode is [ThemeSource.USER]
     * so upgrades do not re-open first-run GPS override of a Light/Dark lock.
     */
    suspend fun currentSource(): ThemeSource? {
        val prefs = context.themeDataStore.data.first()
        return prefs.storedThemeSource()
    }

    /**
     * Writes Cycle, Light, or Dark. [source] defaults to [ThemeSource.USER] so a Light/Dark
     * lock cannot be overwritten by a later GPS fix.
     */
    suspend fun setThemeMode(mode: ThemeMode, source: ThemeSource = ThemeSource.USER) {
        context.themeDataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.storageValue()
            prefs[KEY_THEME_SOURCE] = source.storageValue()
        }
    }

    /** Advances Light → Dark → Cycle → Light, persisting Cycle as automatic. */
    suspend fun advanceThemeMode(current: ThemeMode?) {
        val next = current.nextToggleMode()
        setThemeMode(next, next.sourceWhenSelected())
    }
}

/** Storage token for a [ThemeMode]. */
internal fun ThemeMode.storageValue(): String = when (this) {
    ThemeMode.CYCLE -> "cycle"
    ThemeMode.LIGHT -> "light"
    ThemeMode.DARK -> "dark"
}

/** Storage token for a [ThemeSource]. */
internal fun ThemeSource.storageValue(): String = when (this) {
    ThemeSource.CLOCK -> "clock"
    ThemeSource.GPS -> "gps"
    ThemeSource.USER -> "user"
}

/** Reads the stored theme enum, or null when the key has never been written. */
internal fun Preferences.storedThemeMode(): ThemeMode? = when (this[KEY_THEME_MODE]) {
    "light" -> ThemeMode.LIGHT
    "dark" -> ThemeMode.DARK
    "cycle", "system" -> ThemeMode.CYCLE
    else -> null
}

/**
 * Reads who wrote the theme. A legacy row with a mode but no source is treated as user-set
 * so a later GPS fix cannot flip an already-chosen appearance.
 */
internal fun Preferences.storedThemeSource(): ThemeSource? {
    val source = when (this[KEY_THEME_SOURCE]) {
        "clock" -> ThemeSource.CLOCK
        "gps" -> ThemeSource.GPS
        "user" -> ThemeSource.USER
        else -> null
    }
    if (source != null) return source
    return if (storedThemeMode() != null) ThemeSource.USER else null
}
