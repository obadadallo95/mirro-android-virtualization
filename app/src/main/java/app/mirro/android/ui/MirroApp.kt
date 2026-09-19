package app.mirro.android.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
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
import app.mirro.android.data.local.AppDatabase
import app.mirro.android.data.repository.CloneInstanceRepository
import app.mirro.android.data.repository.InstalledAppRepository
import app.mirro.android.data.repository.SettingsRepository
import app.mirro.android.data.repository.ShortcutRepository
import app.mirro.android.data.repository.ThemeMode
import app.mirro.android.domain.analyzer.CompatibilityAnalyzer
import app.mirro.android.domain.engine.container.ContainerCloneEngine
import app.mirro.android.ui.details.CloneDetailsScreen
import app.mirro.android.ui.details.CloneDetailsViewModel
import app.mirro.android.ui.home.HomeScreen
import app.mirro.android.ui.home.HomeViewModel
import app.mirro.android.ui.navigation.Screen
import app.mirro.android.ui.picker.AppPickerScreen
import app.mirro.android.ui.picker.AppPickerViewModel
import app.mirro.android.ui.settings.BrandIdentityScreen
import app.mirro.android.ui.settings.ContainerDiagnosticsScreen
import app.mirro.android.ui.settings.SettingsScreen
import app.mirro.android.ui.settings.SettingsViewModel
import app.mirro.android.ui.setup.CloneSetupScreen
import app.mirro.android.ui.setup.CloneSetupViewModel
import app.mirro.android.ui.theme.MirroTheme

@Composable
fun MirroApp() {
    val context = LocalContext.current

    // Initialize core architectural infrastructure
    val database = remember { AppDatabase.getInstance(context) }
    val analyzer = remember { CompatibilityAnalyzer() }
    val installedAppRepo = remember { InstalledAppRepository(context, analyzer) }
    val cloneRepo = remember { CloneInstanceRepository(database.cloneInstanceDao()) }
    val shortcutRepo = remember { ShortcutRepository(context) }
    val settingsRepo = remember { SettingsRepository(context) }

    val containerEngine = remember {
        ContainerCloneEngine(
            context = context,
            cloneRepository = cloneRepo,
            shortcutRepository = shortcutRepo
        )
    }

    // ViewModels with constructor-based state management
    val homeViewModel = remember {
        HomeViewModel(
            context = context,
            cloneRepository = cloneRepo,
            settingsRepository = settingsRepo,
            cloneEngine = containerEngine
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

    val activityResultOwner = LocalActivityResultRegistryOwner.current ?: (context as? ActivityResultRegistryOwner)

    val locals = buildList {
        add(LocalConfiguration provides localizedConfig)
        add(LocalContext provides localizedContext)
        add(LocalLayoutDirection provides layoutDirection)
        if (activityResultOwner != null) {
            add(LocalActivityResultRegistryOwner provides activityResultOwner)
        }
    }

    CompositionLocalProvider(*locals.toTypedArray()) {
        MirroTheme(
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
                                    cloneEngine = containerEngine,
                                    analyzer = analyzer
                                )
                            }
                            CloneSetupScreen(
                                viewModel = setupViewModel,
                                onNavigateBack = { navigateBack() },
                                onCloneCreated = { id ->
                                    backStack.clear()
                                    backStack.add(Screen.Home)
                                    backStack.add(Screen.CloneDetails(id))
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
                                    cloneEngine = containerEngine,
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
                                onNavigateToDiagnostics = { navigateTo(Screen.ContainerDiagnostics()) },
                                onNavigateToBrandIdentity = { navigateTo(Screen.BrandIdentity) }
                            )
                        }

                        is Screen.ContainerDiagnostics -> {
                            ContainerDiagnosticsScreen(
                                cloneId = screen.cloneId,
                                onNavigateBack = { navigateBack() }
                            )
                        }

                        is Screen.BrandIdentity -> {
                            BrandIdentityScreen(
                                onNavigateBack = { navigateBack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
