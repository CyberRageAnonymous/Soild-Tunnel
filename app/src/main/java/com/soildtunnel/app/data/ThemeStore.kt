package com.soildtunnel.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.soildtunnel.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore by preferencesDataStore(name = "soildtunnel_theme")

/** Persists the [ThemeMode]. Own tiny DataStore so a settings reset never wipes it. */
class ThemeStore(private val context: Context) {
    private val modeKey = stringPreferencesKey("theme_mode")

    val mode: Flow<ThemeMode> =
        context.themeDataStore.data.map {
            runCatching { ThemeMode.valueOf(it[modeKey] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
        }

    suspend fun setMode(mode: ThemeMode) {
        context.themeDataStore.edit { it[modeKey] = mode.name }
    }
}
