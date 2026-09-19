package app.mirro.android.ui.settings

import androidx.lifecycle.ViewModel
import app.mirro.android.data.repository.SettingsRepository
import app.mirro.android.data.repository.ThemeMode
import app.mirro.android.data.repository.UserSettings
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
