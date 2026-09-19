package app.mirro.android.ui.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mirro.android.data.repository.InstalledAppRepository
import app.mirro.android.domain.model.CompatibilityStatus
import app.mirro.android.domain.model.InstalledApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppFilter {
    ALL,
    USER_ONLY,
    SYSTEM_ONLY
}

data class AppPickerUiState(
    val apps: List<InstalledApp> = emptyList(),
    val searchQuery: String = "",
    val activeFilter: AppFilter = AppFilter.ALL,
    val selectedCompatibility: CompatibilityStatus? = null,
    val isLoading: Boolean = false
)

class AppPickerViewModel(
    private val installedAppRepository: InstalledAppRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _activeFilter = MutableStateFlow(AppFilter.ALL)
    private val _selectedCompatibility = MutableStateFlow<CompatibilityStatus?>(null)

    val uiState: StateFlow<AppPickerUiState> = combine(
        installedAppRepository.installedApps,
        installedAppRepository.isLoading,
        _searchQuery,
        _activeFilter,
        _selectedCompatibility
    ) { apps, loading, query, filter, compatFilter ->
        val filtered = apps.filter { app ->
            val matchesQuery = query.isBlank() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)

            val matchesType = when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER_ONLY -> !app.isSystemApp
                AppFilter.SYSTEM_ONLY -> app.isSystemApp
            }

            val matchesCompat = compatFilter == null || app.compatibilityStatus == compatFilter

            matchesQuery && matchesType && matchesCompat
        }

        AppPickerUiState(
            apps = filtered,
            searchQuery = query,
            activeFilter = filter,
            selectedCompatibility = compatFilter,
            isLoading = loading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppPickerUiState(isLoading = true)
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            installedAppRepository.refreshInstalledApps()
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: AppFilter) {
        _activeFilter.value = filter
    }

    fun toggleCompatibilityFilter(status: CompatibilityStatus) {
        _selectedCompatibility.value = if (_selectedCompatibility.value == status) null else status
    }
}
