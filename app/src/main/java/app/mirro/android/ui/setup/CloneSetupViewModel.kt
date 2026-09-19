package app.mirro.android.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mirro.android.data.repository.InstalledAppRepository
import app.mirro.android.domain.analyzer.CompatibilityAnalyzer
import app.mirro.android.domain.engine.CloneEngine
import app.mirro.android.domain.engine.EngineExecutionResult
import app.mirro.android.domain.engine.EngineRegistry
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
    val selectedEngine: CloneEngineType = CloneEngineType.BLUEPRINT_STAGING,
    val isLocked: Boolean = false,
    val compatibilityReport: CompatibilityReport? = null,
    val isLoading: Boolean = true,
    val showHonestNotice: Boolean = false,
    val createdInstanceId: String? = null,
    val errorMessage: String? = null
)

class CloneSetupViewModel(
    private val packageName: String,
    private val installedAppRepository: InstalledAppRepository,
    private val engineRegistry: EngineRegistry,
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
                // Generate default clone name e.g. "ChatGPT Work" or "$label (Twin)"
                val defaultName = "${app.label} (Twin)"
                _uiState.value = _uiState.value.copy(
                    app = app,
                    customName = defaultName,
                    compatibilityReport = CompatibilityReport(
                        status = app.compatibilityStatus,
                        summary = when (app.compatibilityStatus) {
                            CompatibilityStatus.SUPPORTED -> "Application structure is suitable for profile and container sandboxing."
                            CompatibilityStatus.LIMITED -> "Application uses services that may experience partial push or keystore isolation constraints."
                            CompatibilityStatus.PROTECTED -> "Application contains system or shared UID protection policies."
                            CompatibilityStatus.UNKNOWN -> "Package parameters require runtime validation."
                        }
                    ),
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

    fun onEngineChange(engineType: CloneEngineType) {
        _uiState.value = _uiState.value.copy(selectedEngine = engineType)
    }

    fun onLockToggle(locked: Boolean) {
        _uiState.value = _uiState.value.copy(isLocked = locked)
    }

    fun onCreateCloneClicked() {
        val currentState = _uiState.value
        val app = currentState.app ?: return

        // If the selected engine is not implemented (e.g. Work Profile or Container),
        // we DO NOT pretend success. We display the honest notice.
        if (!currentState.selectedEngine.isImplemented) {
            _uiState.value = currentState.copy(showHonestNotice = true)
            return
        }

        // Otherwise execute real blueprint creation
        executeBlueprintCreation()
    }

    fun executeBlueprintCreation() {
        val currentState = _uiState.value
        val app = currentState.app ?: return

        viewModelScope.launch {
            val engine = engineRegistry.getEngine(CloneEngineType.BLUEPRINT_STAGING)
            val config = CloneConfig(
                originalPackageName = app.packageName,
                originalAppLabel = app.label,
                customName = currentState.customName.ifBlank { "${app.label} (Twin)" },
                badgeColorHex = currentState.badgeColorHex,
                badgeSymbol = currentState.badgeSymbol,
                engineType = currentState.selectedEngine,
                isLocked = currentState.isLocked
            )

            val result = engine.createClone(app, config)
            when (result) {
                is EngineExecutionResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        createdInstanceId = result.data.id,
                        showHonestNotice = false
                    )
                }
                is EngineExecutionResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = result.userMessage,
                        showHonestNotice = false
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(showHonestNotice = false)
                }
            }
        }
    }

    fun dismissHonestNotice() {
        _uiState.value = _uiState.value.copy(showHonestNotice = false)
    }
}
