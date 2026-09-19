package app.mirro.android.ui.home

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mirro.android.data.repository.CloneInstanceRepository
import app.mirro.android.data.repository.SettingsRepository
import app.mirro.android.data.repository.ViewMode
import app.mirro.android.domain.engine.CloneEngine
import app.mirro.android.domain.engine.EngineExecutionResult
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
    val isLoading: Boolean = false,
    val message: String? = null
)

class HomeViewModel(
    private val context: Context,
    private val cloneRepository: CloneInstanceRepository,
    private val settingsRepository: SettingsRepository,
    private val cloneEngine: CloneEngine
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    val uiState: StateFlow<HomeUiState> = combine(
        cloneRepository.allInstances,
        _searchQuery,
        settingsRepository.settings
    ) { instances, query, settings ->
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
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun toggleViewMode() {
        val current = uiState.value.viewMode
        val next = if (current == ViewMode.GRID) ViewMode.LIST else ViewMode.GRID
        settingsRepository.setViewMode(next)
    }

    fun launchInstance(instance: CloneInstance, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = cloneEngine.launchInstance(instance)
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
        viewModelScope.launch {
            if (instance.isFrozen) {
                cloneEngine.unfreezeInstance(instance)
            } else {
                cloneEngine.freezeInstance(instance)
            }
        }
    }

    fun deleteInstance(instance: CloneInstance) {
        viewModelScope.launch {
            cloneEngine.deleteInstance(instance)
        }
    }
}

