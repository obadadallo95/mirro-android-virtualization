package app.mirro.android.ui.home

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mirro.android.data.repository.CloneInstanceRepository
import app.mirro.android.data.repository.SettingsRepository
import app.mirro.android.data.repository.ViewMode
import app.mirro.android.domain.engine.EngineExecutionResult
import app.mirro.android.domain.engine.EngineRegistry
import app.mirro.android.domain.engine.workprofile.ProfileProvisioningManager
import app.mirro.android.domain.engine.workprofile.ProvisioningStatus
import app.mirro.android.domain.model.CloneInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CloneUiItem(
    val instance: CloneInstance,
    val baseAppIcon: Drawable?
)

data class HomeUiState(
    val items: List<CloneUiItem> = emptyList(),
    val searchQuery: String = "",
    val viewMode: ViewMode = ViewMode.GRID,
    val provisioningStatus: ProvisioningStatus = ProvisioningStatus.Available,
    val isLoading: Boolean = false,
    val message: String? = null
)

class HomeViewModel(
    private val context: Context,
    private val cloneRepository: CloneInstanceRepository,
    private val settingsRepository: SettingsRepository,
    private val engineRegistry: EngineRegistry,
    val provisioningManager: ProfileProvisioningManager = ProfileProvisioningManager(context)
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _provisioningStatus = MutableStateFlow(provisioningManager.getProvisioningStatus())

    val uiState: StateFlow<HomeUiState> = combine(
        cloneRepository.allInstances,
        _searchQuery,
        settingsRepository.settings,
        _provisioningStatus
    ) { instances, query, settings, provStatus ->
        val filtered = if (query.isBlank()) {
            instances
        } else {
            instances.filter {
                it.customName.contains(query, ignoreCase = true) ||
                        it.originalAppLabel.contains(query, ignoreCase = true) ||
                        it.originalPackageName.contains(query, ignoreCase = true)
            }
        }

        val uiItems = filtered.map { instance ->
            val icon = try {
                context.packageManager.getApplicationIcon(instance.originalPackageName)
            } catch (_: Exception) {
                null
            }
            CloneUiItem(instance = instance, baseAppIcon = icon)
        }

        HomeUiState(
            items = uiItems,
            searchQuery = query,
            viewMode = settings.viewMode,
            provisioningStatus = provStatus,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(
            isLoading = true,
            provisioningStatus = provisioningManager.getProvisioningStatus()
        )
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleViewMode() {
        val current = uiState.value.viewMode
        val next = if (current == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
        settingsRepository.setViewMode(next)
    }

    fun getProvisioningIntent(): Intent {
        _provisioningStatus.value = ProvisioningStatus.Provisioning
        return provisioningManager.createProvisioningIntent()
    }

    fun handleProvisioningResult(resultCode: Int) {
        val newStatus = provisioningManager.handleActivityResult(resultCode)
        _provisioningStatus.value = newStatus
    }

    fun refreshProvisioningStatus() {
        _provisioningStatus.value = provisioningManager.getProvisioningStatus()
    }

    fun launchInstance(instance: CloneInstance, onResult: (String) -> Unit) {
        val engine = engineRegistry.getEngine(instance.engineType)
        viewModelScope.launch {
            val result = engine.launchInstance(instance)
            when (result) {
                is EngineExecutionResult.Success -> {
                    onResult(result.message ?: "Launched")
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

    fun toggleFreeze(instance: CloneInstance) {
        val engine = engineRegistry.getEngine(instance.engineType)
        viewModelScope.launch {
            if (instance.isFrozen) {
                engine.unfreezeInstance(instance)
            } else {
                engine.freezeInstance(instance)
            }
        }
    }

    fun deleteInstance(instance: CloneInstance) {
        val engine = engineRegistry.getEngine(instance.engineType)
        viewModelScope.launch {
            engine.deleteInstance(instance)
        }
    }
}
