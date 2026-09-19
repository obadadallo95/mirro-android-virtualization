package app.mirro.android.ui.details

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mirro.android.data.repository.CloneInstanceRepository
import app.mirro.android.data.repository.InstalledAppRepository
import app.mirro.android.domain.analyzer.CompatibilityAnalyzer
import app.mirro.android.domain.engine.CloneEngine
import app.mirro.android.domain.engine.EngineExecutionResult
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
    private val cloneEngine: CloneEngine,
    private val analyzer: CompatibilityAnalyzer = CompatibilityAnalyzer()
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

                val storage = cloneEngine.getStorageUsage(instance)
                val baseApp = installedAppRepository.getAppByPackage(instance.originalPackageName)

                val compat = baseApp?.let {
                    try {
                        val pi = context.packageManager.getPackageInfo(it.packageName, 0)
                        analyzer.analyze(
                            packageInfo = pi,
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

    fun launchClone(onResult: (String) -> Unit) {
        val instance = _uiState.value.instance ?: return

        viewModelScope.launch {
            val result = cloneEngine.launchInstance(instance)
            when (result) {
                is EngineExecutionResult.Success -> {
                    loadInstance()
                    onResult(result.message ?: "Application launched in Mirro Container")
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

    fun toggleFreeze(onResult: (String) -> Unit) {
        val instance = _uiState.value.instance ?: return

        viewModelScope.launch {
            val result = if (instance.isFrozen) {
                cloneEngine.unfreezeInstance(instance)
            } else {
                cloneEngine.freezeInstance(instance)
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

        viewModelScope.launch {
            val result = cloneEngine.createShortcut(instance)
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

        viewModelScope.launch {
            cloneEngine.deleteInstance(instance)
            _uiState.value = _uiState.value.copy(
                showDeleteDialog = false,
                isDeleted = true
            )
        }
    }
}
