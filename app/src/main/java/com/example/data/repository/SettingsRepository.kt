package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class ViewMode {
    GRID,
    LIST
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val languageCode: String = "system",
    val biometricLockEnabled: Boolean = false,
    val viewMode: ViewMode = ViewMode.GRID
)

class SettingsRepository(
    private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("apptwin_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private fun loadSettings(): UserSettings {
        val themeStr = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val theme = try { ThemeMode.valueOf(themeStr) } catch (_: Exception) { ThemeMode.SYSTEM }
        val dynamicColor = prefs.getBoolean("dynamic_color", true)
        val languageCode = prefs.getString("language_code", "system") ?: "system"
        val biometricLock = prefs.getBoolean("biometric_lock", false)
        val viewModeStr = prefs.getString("view_mode", ViewMode.GRID.name) ?: ViewMode.GRID.name
        val viewMode = try { ViewMode.valueOf(viewModeStr) } catch (_: Exception) { ViewMode.GRID }

        return UserSettings(
            themeMode = theme,
            dynamicColor = dynamicColor,
            languageCode = languageCode,
            biometricLockEnabled = biometricLock,
            viewMode = viewMode
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean("dynamic_color", enabled).apply()
        _settings.value = _settings.value.copy(dynamicColor = enabled)
    }

    fun setLanguage(languageCode: String) {
        prefs.edit().putString("language_code", languageCode).apply()
        _settings.value = _settings.value.copy(languageCode = languageCode)
    }

    fun setBiometricLock(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_lock", enabled).apply()
        _settings.value = _settings.value.copy(biometricLockEnabled = enabled)
    }

    fun setViewMode(viewMode: ViewMode) {
        prefs.edit().putString("view_mode", viewMode.name).apply()
        _settings.value = _settings.value.copy(viewMode = viewMode)
    }
}
