package com.example.ui.settings

import androidx.lifecycle.ViewModel
import com.example.data.repository.SettingsRepository
import com.example.data.repository.ThemeMode
import com.example.data.repository.UserSettings
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settings

    fun setThemeMode(mode: ThemeMode) {
        settingsRepository.setThemeMode(mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        settingsRepository.setDynamicColor(enabled)
    }

    fun setLanguage(languageCode: String) {
        settingsRepository.setLanguage(languageCode)
    }

    fun setBiometricLock(enabled: Boolean) {
        settingsRepository.setBiometricLock(enabled)
    }
}
