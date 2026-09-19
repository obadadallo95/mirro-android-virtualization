package app.mirro.android.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mirro.android.data.repository.InstalledAppRepository
import app.mirro.android.domain.analyzer.CompatibilityAnalyzer
import app.mirro.android.domain.engine.CloneEngine
import app.mirro.android.domain.engine.EngineExecutionResult
import app.mirro.android.domain.model.CloneConfig
import app.mirro.android.domain.model.CloneEngineType
import app.mirro.android.domain.model.CompatibilityReport
import app.mirro.android.domain.model.CompatibilityStatus
import app.mirro.android.domain.model.InstalledApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CloneSetupUiState(
    val app: InstalledApp? = null,
    val customName: String = "",
    val badgeColorHex: String = "#10B981", // Default Emerald
    val badgeSymbol: String = "2",
    val isLocked: Boolean = false,
    val compatibilityReport: CompatibilityReport? = null,
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val createdInstanceId: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class CloneSetupViewModel(
    private val packageName: String,
    private val installedAppRepository: InstalledAppRepository,
    private val cloneEngine: CloneEngine,
    private val analyzer: CompatibilityAnalyzer = CompatibilityAnalyzer()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloneSetupUiState())
    val uiState: StateFlow<CloneSetupUiState> = _uiState.asStateFlow()

    init {
        loadApp()
    }

    private fun loadApp() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val app = installedAppRepository.getAppByPackage(packageName)
            if (app != null) {
                val defaultName = if (app.packageName.equals("com.openai.chatgpt", ignoreCase = true)) {
                    "ChatGPT 2"
                } else {
                    "${app.label} 2"
                }

                val report = try {
                    val pkgInfo = installedAppRepository.getPackageInfo(app.packageName)
                    if (pkgInfo != null) {
                        analyzer.analyze(packageInfo = pkgInfo)
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                } ?: CompatibilityReport(
                    status = app.compatibilityStatus,
                    summary = when (app.compatibilityStatus) {
                        CompatibilityStatus.SUPPORTED -> "Application is ready to run in Mirro Container sandbox."
                        CompatibilityStatus.LIMITED -> "Application uses cloud push or keystore services with partial isolation."
                        CompatibilityStatus.PROTECTED -> "Application contains system or shared UID protection policies."
                        CompatibilityStatus.CONTAINER_LAUNCH_VERIFIED -> "Container runtime execution verified with isolated data."
                        CompatibilityStatus.UNKNOWN -> "Application is ready for isolated container startup test."
                    }
                )

                _uiState.value = _uiState.value.copy(
                    app = app,
                    customName = defaultName,
                    compatibilityReport = report,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Application not found on device"
                )
            }
        }
    }

    fun onCustomNameChange(name: String) {
        _uiState.value = _uiState.value.copy(customName = name)
    }

    fun onBadgeColorChange(hex: String) {
        _uiState.value = _uiState.value.copy(badgeColorHex = hex)
    }

    fun onBadgeSymbolChange(symbol: String) {
        _uiState.value = _uiState.value.copy(badgeSymbol = symbol)
    }

    fun onLockToggle(locked: Boolean) {
        _uiState.value = _uiState.value.copy(isLocked = locked)
    }

    fun onCreateClone() {
        val currentState = _uiState.value
        val app = currentState.app ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreating = true)
            val config = CloneConfig(
                originalPackageName = app.packageName,
                originalAppLabel = app.label,
                customName = currentState.customName.ifBlank { "${app.label} 2" },
                badgeColorHex = currentState.badgeColorHex,
                badgeSymbol = currentState.badgeSymbol.ifBlank { "2" },
                engineType = CloneEngineType.VIRTUALIZED_CONTAINER,
                isLocked = currentState.isLocked
            )

            val result = cloneEngine.createClone(app, config)
            when (result) {
                is EngineExecutionResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        createdInstanceId = result.data.id,
                        isCreating = false,
                        errorMessage = null
                    )
                }
                is EngineExecutionResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.userMessage,
                        isCreating = false
                    )
                }
                is EngineExecutionResult.Pending -> {
                    _uiState.value = _uiState.value.copy(
                        infoMessage = result.message,
                        isCreating = false
                    )
                }
                is EngineExecutionResult.NotImplemented -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.reason,
                        isCreating = false
                    )
                }
                is EngineExecutionResult.Unsupported -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.reason,
                        isCreating = false
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }
}
