package com.example.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale
import com.example.data.local.AppDatabase
import com.example.data.repository.CloneInstanceRepository
import com.example.data.repository.InstalledAppRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.ShortcutRepository
import com.example.data.repository.StorageRepository
import com.example.data.repository.ThemeMode
import com.example.domain.analyzer.CompatibilityAnalyzer
import com.example.domain.engine.EngineRegistry
import com.example.domain.engine.blueprint.BlueprintCloneEngine
import com.example.domain.engine.container.ContainerCloneEngine
import com.example.domain.engine.workprofile.WorkProfileCloneEngine
import com.example.domain.model.CloneEngineType
import com.example.ui.details.CloneDetailsScreen
import com.example.ui.details.CloneDetailsViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.navigation.Screen
import com.example.ui.picker.AppPickerScreen
import com.example.ui.picker.AppPickerViewModel
import com.example.ui.settings.ArchitectureDocsScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.setup.CloneSetupScreen
import com.example.ui.setup.CloneSetupViewModel
import com.example.ui.theme.AppTwinTheme

@Composable
fun AppTwinApp() {
    val context = LocalContext.current

    // Initialize core architectural infrastructure
    val database = remember { AppDatabase.getInstance(context) }
    val analyzer = remember { CompatibilityAnalyzer() }
    val installedAppRepo = remember { InstalledAppRepository(context, analyzer) }
    val cloneRepo = remember { CloneInstanceRepository(database.cloneInstanceDao()) }
    val storageRepo = remember { StorageRepository(context) }
    val shortcutRepo = remember { ShortcutRepository(context) }
    val settingsRepo = remember { SettingsRepository(context) }

    val blueprintEngine = remember {
        BlueprintCloneEngine(
            context = context,
            cloneInstanceRepository = cloneRepo,
            storageRepository = storageRepo,
            shortcutRepository = shortcutRepo
        )
    }
    val workProfileEngine = remember { WorkProfileCloneEngine(context) }
    val containerEngine = remember { ContainerCloneEngine() }

    val engineRegistry = remember {
        EngineRegistry(
            listOf(blueprintEngine, workProfileEngine, containerEngine)
        )
    }

    // ViewModels with constructor-based state management
    val homeViewModel = remember {
        HomeViewModel(
            context = context,
            cloneRepository = cloneRepo,
            settingsRepository = settingsRepo,
            engine = blueprintEngine
        )
    }

    val appPickerViewModel = remember {
        AppPickerViewModel(installedAppRepository = installedAppRepo)
    }

    val settingsViewModel = remember {
        SettingsViewModel(settingsRepository = settingsRepo)
    }

    // Settings & Theme
    val userSettings by settingsRepo.settings.collectAsState()
    val isDarkTheme = when (userSettings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Navigation BackStack
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val currentScreen = backStack.lastOrNull() ?: Screen.Home

    fun navigateTo(screen: Screen) {
        backStack.add(screen)
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
        }
    }

    BackHandler(enabled = backStack.size > 1) {
        navigateBack()
    }

    // Dynamic Language & RTL Localization
    val currentLocale = remember(userSettings.languageCode) {
        when (userSettings.languageCode) {
            "ar" -> Locale("ar")
            "en" -> Locale("en")
            else -> Locale.getDefault()
        }
    }
    val isRtl = currentLocale.language == "ar"
    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    val currentConfig = LocalConfiguration.current
    val localizedConfig = remember(currentConfig, currentLocale) {
        val config = Configuration(currentConfig)
        config.setLocale(currentLocale)
        config.setLayoutDirection(currentLocale)
        config
    }
    val localizedContext = remember(context, currentLocale) {
        context.createConfigurationContext(localizedConfig)
    }

    CompositionLocalProvider(
        LocalConfiguration provides localizedConfig,
        LocalContext provides localizedContext,
        LocalLayoutDirection provides layoutDirection
    ) {
        AppTwinTheme(
            darkTheme = isDarkTheme,
            dynamicColor = userSettings.dynamicColor
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "ScreenTransition"
                ) { screen ->
                    when (screen) {
                        is Screen.Home -> {
                            HomeScreen(
                                viewModel = homeViewModel,
                                onNavigateToPicker = { navigateTo(Screen.AppPicker) },
                                onNavigateToDetails = { id -> navigateTo(Screen.CloneDetails(id)) },
                                onNavigateToSettings = { navigateTo(Screen.Settings) }
                            )
                        }

                        is Screen.AppPicker -> {
                            AppPickerScreen(
                                viewModel = appPickerViewModel,
                                onNavigateBack = { navigateBack() },
                                onAppSelected = { pkg -> navigateTo(Screen.CloneSetup(pkg)) }
                            )
                        }

                        is Screen.CloneSetup -> {
                            val setupViewModel = remember(screen.packageName) {
                                CloneSetupViewModel(
                                    packageName = screen.packageName,
                                    installedAppRepository = installedAppRepo,
                                    engineRegistry = engineRegistry,
                                    analyzer = analyzer
                                )
                            }
                            CloneSetupScreen(
                                viewModel = setupViewModel,
                                onNavigateBack = { navigateBack() },
                                onCloneCreated = { id ->
                                    // Pop back to Home then navigate to clone details
                                    backStack.clear()
                                    backStack.add(Screen.Home)
                                    backStack.add(Screen.CloneDetails(id))
                                },
                                onViewArchitecture = {
                                    navigateTo(Screen.ArchitectureDocs)
                                }
                            )
                        }

                        is Screen.CloneDetails -> {
                            val detailsViewModel = remember(screen.instanceId) {
                                CloneDetailsViewModel(
                                    instanceId = screen.instanceId,
                                    context = context,
                                    cloneRepository = cloneRepo,
                                    installedAppRepository = installedAppRepo,
                                    engine = blueprintEngine,
                                    analyzer = analyzer
                                )
                            }
                            CloneDetailsScreen(
                                viewModel = detailsViewModel,
                                onNavigateBack = { navigateBack() }
                            )
                        }

                        is Screen.Settings -> {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateBack = { navigateBack() },
                                onNavigateToArchitectureDocs = { navigateTo(Screen.ArchitectureDocs) },
                                onNavigateToBrandIdentity = { navigateTo(Screen.BrandIdentity) }
                            )
                        }

                        is Screen.ArchitectureDocs -> {
                            ArchitectureDocsScreen(
                                onNavigateBack = { navigateBack() }
                            )
                        }

                        is Screen.BrandIdentity -> {
                            com.example.ui.settings.BrandIdentityScreen(
                                onNavigateBack = { navigateBack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
