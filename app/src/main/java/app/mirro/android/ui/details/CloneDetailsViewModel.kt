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
    val showLaunchDisclaimer: Boolean = false,
    val isDeleted: Boolean = false,
    val message: String? = null
)

class CloneDetailsViewModel(
    private val instanceId: String,
    private val context: Context,
    private val cloneRepository: CloneInstanceRepository,
    private val installedAppRepository: InstalledAppRepository,
    private val engine: CloneEngine,
    private val analyzer: CompatibilityAnalyzer = CompatibilityAnalyzer()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloneDetailsUiState())
    val uiState: StateFlow<CloneDetailsUiState> = _uiState.asStateFlow()

    init {
        loadInstance()
    }

    private fun loadInstance() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val instance = cloneRepository.getInstanceById(instanceId)
            if (instance != null) {
                val icon = try {
                    context.packageManager.getApplicationIcon(instance.originalPackageName)
                } catch (_: Exception) {
                    null
                }

                val storage = engine.getStorageUsage(instance)
                val baseApp = installedAppRepository.getAppByPackage(instance.originalPackageName)
                val compat = baseApp?.let {
                    try {
                        val pi = context.packageManager.getPackageInfo(it.packageName, 0)
                        analyzer.analyze(pi)
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

    fun requestLaunch() {
        // Honest architecture notice: since container process virtualization is under development,
        // we present the demonstration notice or execute launch
        _uiState.value = _uiState.value.copy(showLaunchDisclaimer = true)
    }

    fun proceedLaunch(onResult: (String) -> Unit) {
        val instance = _uiState.value.instance ?: return
        _uiState.value = _uiState.value.copy(showLaunchDisclaimer = false)

        viewModelScope.launch {
            val result = engine.launchInstance(instance)
            when (result) {
                is EngineExecutionResult.Success -> {
                    onResult(result.message ?: "Application launched")
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
        viewModelScope.launch {
            val result = engine.freezeInstance(instance)
            when (result) {
                is EngineExecutionResult.Success -> {
                    loadInstance()
                    onResult(result.message ?: "Freeze state updated")
                }
                is EngineExecutionResult.Failure -> {
                    onResult(result.userMessage)
                }
                else -> {}
            }
        }
    }

    fun createShortcut(onResult: (String) -> Unit) {
        val instance = _uiState.value.instance ?: return
        viewModelScope.launch {
            val result = engine.createShortcut(instance)
            when (result) {
                is EngineExecutionResult.Success -> {
                    onResult("Shortcut created on launcher")
                }
                is EngineExecutionResult.Failure -> {
                    onResult(result.userMessage)
                }
                else -> {}
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
            engine.deleteInstance(instance)
            _uiState.value = _uiState.value.copy(
                showDeleteDialog = false,
                isDeleted = true
            )
        }
    }
}
