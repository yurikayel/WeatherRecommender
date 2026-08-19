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

/**
 * Persisted light/dark preference.
 *
 * `null` on [ThemePreferences.themeMode] means first launch (no key written yet).
 * First-run settling writes [ThemeMode.LIGHT] or [ThemeMode.DARK] from day/night at the
 * device location. An explicit [ThemeMode.SYSTEM] is stored as `"system"` so it is distinct
 * from unset — later choosing System still follows the OS.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/** Resolves whether Compose should use the dark color scheme for a stored preference. */
fun ThemeMode.resolveDarkTheme(systemInDarkTheme: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemInDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Like [ThemeMode.resolveDarkTheme], with first-run unset falling back to [unsetIsNight]
 * (clock or solar) until [ThemePreferences] is written.
 */
fun ThemeMode?.resolveRenderedDarkTheme(
    systemInDarkTheme: Boolean,
    unsetIsNight: Boolean
): Boolean = when (this) {
    null -> unsetIsNight
    else -> resolveDarkTheme(systemInDarkTheme)
}

@Singleton
class ThemePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    val themeMode: Flow<ThemeMode?> = context.themeDataStore.data.map { prefs ->
        when (prefs[KEY_THEME_MODE]) {
            VALUE_LIGHT -> ThemeMode.LIGHT
            VALUE_DARK -> ThemeMode.DARK
            VALUE_SYSTEM -> ThemeMode.SYSTEM
            else -> null
        }
    }

    suspend fun currentMode(): ThemeMode? = themeMode.first()

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = when (mode) {
                ThemeMode.SYSTEM -> VALUE_SYSTEM
                ThemeMode.LIGHT -> VALUE_LIGHT
                ThemeMode.DARK -> VALUE_DARK
            }
        }
    }

    /**
     * Flips between light and dark based on the currently rendered theme.
     * After the first toggle, the explicit choice replaces first-run / system-follow.
     */
    suspend fun toggle(currentlyDark: Boolean) {
        setThemeMode(if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK)
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        const val VALUE_LIGHT = "light"
        const val VALUE_DARK = "dark"
        const val VALUE_SYSTEM = "system"
    }
}
