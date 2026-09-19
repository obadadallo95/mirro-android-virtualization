package com.example.ui.home

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.CloneInstanceRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.ViewMode
import com.example.domain.engine.CloneEngine
import com.example.domain.model.CloneInstance
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
    private val engine: CloneEngine
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
            viewMode = settings.viewMode
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
            val result = engine.launchInstance(instance)
            when (result) {
                is com.example.domain.engine.EngineExecutionResult.Success -> {
                    onResult(result.message ?: "Launched")
                }
                is com.example.domain.engine.EngineExecutionResult.Failure -> {
                    onResult(result.userMessage)
                }
                is com.example.domain.engine.EngineExecutionResult.NotImplemented -> {
                    onResult(result.reason)
                }
                is com.example.domain.engine.EngineExecutionResult.Unsupported -> {
                    onResult(result.reason)
                }
            }
        }
    }

    fun toggleFreeze(instance: CloneInstance) {
        viewModelScope.launch {
            engine.freezeInstance(instance)
        }
    }

    fun deleteInstance(instance: CloneInstance) {
        viewModelScope.launch {
            engine.deleteInstance(instance)
        }
    }
}
