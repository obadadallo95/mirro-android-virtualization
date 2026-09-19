package app.mirro.android.ui.details

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mirro.android.data.repository.CloneInstanceRepository
import app.mirro.android.data.repository.InstalledAppRepository
import app.mirro.android.domain.analyzer.CompatibilityAnalyzer
import app.mirro.android.domain.engine.EngineExecutionResult
import app.mirro.android.domain.engine.EngineRegistry
import app.mirro.android.domain.engine.workprofile.ProfileAppDiscoveryManager
import app.mirro.android.domain.engine.workprofile.ProfileProvisioningManager
import app.mirro.android.domain.model.CloneEngineType
import app.mirro.android.domain.model.CloneInstance
import app.mirro.android.domain.model.CompatibilityReport
import app.mirro.android.domain.model.StorageMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CloneDetailsUiState(
    val instance: CloneInstance? = null,
    val baseAppIcon: Drawable? = null,
    val storageMetrics: StorageMetrics? = null,
    val compatibilityReport: CompatibilityReport? = null,
    val isLoading: Boolean = true,
    val showDeleteDialog: Boolean = false,
    val showRenameDialog: Boolean = false,
    val showLaunchDisclaimer: Boolean = false,
    val showFreezeNoticeDialog: Boolean = false,
    val freezeNoticeMessage: String? = null,
    val isDeleted: Boolean = false,
    val message: String? = null
)

class CloneDetailsViewModel(
    private val instanceId: String,
    private val context: Context,
    private val cloneRepository: CloneInstanceRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val engineRegistry: EngineRegistry,
    private val analyzer: CompatibilityAnalyzer = CompatibilityAnalyzer(),
    private val discoveryManager: ProfileAppDiscoveryManager = ProfileAppDiscoveryManager(
        context,
        ProfileProvisioningManager(context)
    )
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloneDetailsUiState())
    val uiState: StateFlow<CloneDetailsUiState> = _uiState.asStateFlow()

    init {
        loadInstance()
    }

    fun loadInstance() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val instance = cloneRepository.getInstanceById(instanceId)
            if (instance != null) {
                val icon = try {
                    context.packageManager.getApplicationIcon(instance.originalPackageName)
                } catch (_: Exception) {
                    null
                }

                val engine = engineRegistry.getEngine(instance.engineType)
                val storage = engine?.getStorageUsage(instance) ?: StorageMetrics(apkSizeBytes = null, dataSizeBytes = null, cacheSizeBytes = null)
                val baseApp = installedAppRepository.getAppByPackage(instance.originalPackageName)

                val inWorkProfile = if (instance.engineType == CloneEngineType.WORK_PROFILE) {
                    discoveryManager.isAppInstalledInWorkProfile(instance.originalPackageName)
                } else null

                val compat = baseApp?.let {
                    try {
                        val pi = context.packageManager.getPackageInfo(it.packageName, 0)
                        analyzer.analyze(
                            packageInfo = pi,
                            engineType = instance.engineType,
                            isInstalledInWorkProfile = inWorkProfile,
                            isRuntimeLaunchVerified = instance.isRuntimeVerified
                        )
                    } catch (_: Exception) {
                        null
                    }
                }

                _uiState.value = _uiState.value.copy(
                    instance = instance,
                    baseAppIcon = icon,
                    storageMetrics = storage,
                    compatibilityReport = compat,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun requestLaunch(onResult: (String) -> Unit) {
        val instance = _uiState.value.instance ?: return
        if (instance.engineType == CloneEngineType.WORK_PROFILE) {
            // Real Work Profile launch executes directly
            proceedLaunch(onResult)
        } else {
            // Blueprint staging shows demonstration notice
            _uiState.value = _uiState.value.copy(showLaunchDisclaimer = true)
        }
    }

    fun proceedLaunch(onResult: (String) -> Unit) {
        val instance = _uiState.value.instance ?: return
        _uiState.value = _uiState.value.copy(showLaunchDisclaimer = false)

        val engine = engineRegistry.getEngine(instance.engineType)
        if (engine == null) {
            onResult("Engine not registered")
            return
        }

        viewModelScope.launch {
            val result = engine.launchInstance(instance)
            when (result) {
                is EngineExecutionResult.Success -> {
                    loadInstance()
                    onResult(result.message ?: "Application launched in Mirro Space")
                }
                is EngineExecutionResult.Failure -> {
                    onResult(result.userMessage)
                }
                is EngineExecutionResult.NotImplemented -> {
                    onResult(result.reason)
                }
                is EngineExecutionResult.Unsupported -> {
                    onResult(result.reason)
                }
            }
        }
    }

    fun dismissLaunchDisclaimer() {
        _uiState.value = _uiState.value.copy(showLaunchDisclaimer = false)
    }

    fun toggleFreeze(onResult: (String) -> Unit) {
        val instance = _uiState.value.instance ?: return
        val engine = engineRegistry.getEngine(instance.engineType) ?: return

        viewModelScope.launch {
            val result = if (instance.isFrozen) {
                engine.unfreezeInstance(instance)
            } else {
                engine.freezeInstance(instance)
            }

            when (result) {
                is EngineExecutionResult.Success -> {
                    loadInstance()
                    onResult(result.message ?: "Status updated")
                }
                is EngineExecutionResult.Unsupported -> {
                    _uiState.value = _uiState.value.copy(
                        showFreezeNoticeDialog = true,
                        freezeNoticeMessage = result.reason
                    )
                }
                is EngineExecutionResult.Failure -> {
                    onResult(result.userMessage)
                }
                is EngineExecutionResult.NotImplemented -> {
                    onResult(result.reason)
                }
            }
        }
    }

    fun dismissFreezeNotice() {
        _uiState.value = _uiState.value.copy(showFreezeNoticeDialog = false, freezeNoticeMessage = null)
    }

    fun createShortcut(onResult: (String) -> Unit) {
        val instance = _uiState.value.instance ?: return
        val engine = engineRegistry.getEngine(instance.engineType) ?: return

        viewModelScope.launch {
            val result = engine.createShortcut(instance)
            when (result) {
                is EngineExecutionResult.Success -> {
                    onResult(result.message ?: "Shortcut added to home screen")
                }
                is EngineExecutionResult.Failure -> {
                    onResult(result.userMessage)
                }
                is EngineExecutionResult.Unsupported -> {
                    onResult(result.reason)
                }
                is EngineExecutionResult.NotImplemented -> {
                    onResult(result.reason)
                }
            }
        }
    }

    fun openRenameDialog() {
        _uiState.value = _uiState.value.copy(showRenameDialog = true)
    }

    fun closeRenameDialog() {
        _uiState.value = _uiState.value.copy(showRenameDialog = false)
    }

    fun submitRename(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            cloneRepository.updateCustomName(instanceId, newName)
            closeRenameDialog()
            loadInstance()
        }
    }

    fun openDeleteDialog() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = true)
    }

    fun closeDeleteDialog() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = false)
    }

    fun confirmDelete() {
        val instance = _uiState.value.instance ?: return
        val engine = engineRegistry.getEngine(instance.engineType) ?: return

        viewModelScope.launch {
            engine.deleteInstance(instance)
            _uiState.value = _uiState.value.copy(
                showDeleteDialog = false,
                isDeleted = true
            )
        }
    }
}

