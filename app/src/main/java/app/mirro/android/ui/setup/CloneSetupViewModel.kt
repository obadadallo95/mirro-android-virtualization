package app.mirro.android.ui.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mirro.android.data.repository.InstalledAppRepository
import app.mirro.android.domain.analyzer.CompatibilityAnalyzer
import app.mirro.android.domain.engine.CloneEngine
import app.mirro.android.domain.engine.EngineExecutionResult
import app.mirro.android.domain.engine.EngineRegistry
import app.mirro.android.domain.engine.workprofile.ManagedProfileAppStatus
import app.mirro.android.domain.engine.workprofile.WorkProfileCloneEngine
import app.mirro.android.domain.model.CloneConfig
import app.mirro.android.domain.model.CloneEngineType
import app.mirro.android.domain.model.CompatibilityReport
import app.mirro.android.domain.model.CompatibilityStatus
import app.mirro.android.domain.model.InstalledApp
import app.mirro.android.domain.model.ProfileType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CloneSetupUiState(
    val app: InstalledApp? = null,
    val customName: String = "",
    val badgeColorHex: String = "#10B981", // Default Emerald
    val badgeSymbol: String = "2",
    val selectedEngine: CloneEngineType = CloneEngineType.WORK_PROFILE,
    val isLocked: Boolean = false,
    val compatibilityReport: CompatibilityReport? = null,
    val managedProfileStatus: ManagedProfileAppStatus? = null,
    val isLoading: Boolean = true,
    val showHonestNotice: Boolean = false,
    val createdInstanceId: String? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class CloneSetupViewModel(
    private val packageName: String,
    private val installedAppRepository: InstalledAppRepository,
    private val engineRegistry: EngineRegistry,
    private val workProfileEngine: WorkProfileCloneEngine? = null,
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
                val defaultName = if (app.packageName.equals("com.openai.chatgpt", ignoreCase = true)) {
                    "ChatGPT Work"
                } else {
                    "${app.label} (Twin)"
                }

                val wpStatus = workProfileEngine?.checkAppAvailability(app.packageName, app.label)
                val isInstalledInWp = wpStatus is ManagedProfileAppStatus.AppAvailableInProfile

                val report = try {
                    val pkgInfo = installedAppRepository.getPackageInfo(app.packageName)
                    if (pkgInfo != null) {
                        analyzer.analyze(
                            packageInfo = pkgInfo,
                            engineType = _uiState.value.selectedEngine,
                            isInstalledInWorkProfile = isInstalledInWp
                        )
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                } ?: CompatibilityReport(
                    status = app.compatibilityStatus,
                    summary = when (app.compatibilityStatus) {
                        CompatibilityStatus.SUPPORTED -> "Application structure is suitable for profile and container sandboxing."
                        CompatibilityStatus.LIMITED -> "Application uses services that may experience partial push or keystore isolation constraints."
                        CompatibilityStatus.PROTECTED -> "Application contains system or shared UID protection policies."
                        CompatibilityStatus.WORK_PROFILE_AVAILABLE -> "Package is present inside Mirro Space and ready for isolation."
                        CompatibilityStatus.WORK_PROFILE_INSTALL_REQUIRED -> "Application must be added to Mirro Space to establish clone."
                        CompatibilityStatus.VERIFIED_WORK_PROFILE -> "Verified runtime multi-account isolation in Mirro Space."
                        CompatibilityStatus.UNKNOWN -> "Package parameters require runtime validation."
                    }
                )

                _uiState.value = _uiState.value.copy(
                    app = app,
                    customName = defaultName,
                    compatibilityReport = report,
                    managedProfileStatus = wpStatus,
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
        refreshAvailability()
    }

    fun refreshAvailability() {
        val app = _uiState.value.app ?: return
        val wpStatus = workProfileEngine?.checkAppAvailability(app.packageName, app.label)
        val isInstalledInWp = wpStatus is ManagedProfileAppStatus.AppAvailableInProfile

        val report = try {
            val pkgInfo = installedAppRepository.getPackageInfo(app.packageName)
            if (pkgInfo != null) {
                analyzer.analyze(
                    packageInfo = pkgInfo,
                    engineType = _uiState.value.selectedEngine,
                    isInstalledInWorkProfile = isInstalledInWp
                )
            } else null
        } catch (_: Exception) {
            null
        }

        _uiState.value = _uiState.value.copy(
            managedProfileStatus = wpStatus,
            compatibilityReport = report ?: _uiState.value.compatibilityReport
        )
    }

    fun onLockToggle(locked: Boolean) {
        _uiState.value = _uiState.value.copy(isLocked = locked)
    }

    fun onAddAppToWorkProfile(context: Context) {
        val app = _uiState.value.app ?: return
        if (workProfileEngine == null) return

        // 1. Attempt direct package enabling if Device Admin
        val enabledDirectly = workProfileEngine.checkAppAvailability(app.packageName, app.label)
        if (enabledDirectly is ManagedProfileAppStatus.AppAvailableInProfile) {
            _uiState.value = _uiState.value.copy(
                infoMessage = "${app.label} is already available in Mirro Space.",
                managedProfileStatus = enabledDirectly
            )
            return
        }

        // 2. Open Google Play Store for package
        try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("market://details?id=${app.packageName}")
            ).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val webIntent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}")
                ).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            } catch (err: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Unable to launch Play Store: ${err.message}"
                )
            }
        }
    }

    fun onCreateCloneClicked() {
        val currentState = _uiState.value
        val app = currentState.app ?: return

        // If the selected engine is not implemented, display the honest notice.
        if (!currentState.selectedEngine.isImplemented) {
            _uiState.value = currentState.copy(showHonestNotice = true)
            return
        }

        // Work profile isolation creation
        if (currentState.selectedEngine == CloneEngineType.WORK_PROFILE && workProfileEngine != null) {
            viewModelScope.launch {
                val config = CloneConfig(
                    originalPackageName = app.packageName,
                    originalAppLabel = app.label,
                    customName = currentState.customName.ifBlank { "${app.label} (Mirro)" },
                    badgeColorHex = currentState.badgeColorHex,
                    badgeSymbol = currentState.badgeSymbol,
                    engineType = CloneEngineType.WORK_PROFILE,
                    profileType = ProfileType.MIRRO_MANAGED,
                    isLocked = currentState.isLocked
                )

                val result = workProfileEngine.createClone(app, config)
                when (result) {
                    is EngineExecutionResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            createdInstanceId = result.data.id,
                            errorMessage = null
                        )
                    }
                    is EngineExecutionResult.Failure -> {
                        _uiState.value = _uiState.value.copy(
                            errorMessage = result.userMessage
                        )
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Unable to create clone with Work Profile engine."
                        )
                    }
                }
            }
            return
        }

        // Otherwise execute blueprint creation
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

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, infoMessage = null)
    }
}
