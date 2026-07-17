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
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "theme_preferences"
)

/**
 * Persisted light/dark preference.
 *
 * [ThemeMode.SYSTEM] is the default until the user toggles; after that the explicit
 * [ThemeMode.LIGHT] / [ThemeMode.DARK] choice is remembered across restarts.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/** Resolves whether Compose should use the dark color scheme. */
fun ThemeMode.resolveDarkTheme(systemInDarkTheme: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemInDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Singleton
class ThemePreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    val themeMode: Flow<ThemeMode> = context.themeDataStore.data.map { prefs ->
        when (prefs[KEY_THEME_MODE]) {
            VALUE_LIGHT -> ThemeMode.LIGHT
            VALUE_DARK -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.themeDataStore.edit { prefs ->
            when (mode) {
                ThemeMode.SYSTEM -> prefs.remove(KEY_THEME_MODE)
                ThemeMode.LIGHT -> prefs[KEY_THEME_MODE] = VALUE_LIGHT
                ThemeMode.DARK -> prefs[KEY_THEME_MODE] = VALUE_DARK
            }
        }
    }

    /**
     * Flips between light and dark based on the currently rendered theme.
     * After the first toggle, the explicit choice replaces system-follow.
     */
    suspend fun toggle(currentlyDark: Boolean) {
        setThemeMode(if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK)
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        const val VALUE_LIGHT = "light"
        const val VALUE_DARK = "dark"
    }
}
