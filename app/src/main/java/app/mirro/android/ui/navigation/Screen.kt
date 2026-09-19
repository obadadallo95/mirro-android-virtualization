package app.mirro.android.ui.navigation

sealed class Screen {
    data object Home : Screen()
    data object AppPicker : Screen()
    data class CloneSetup(val packageName: String) : Screen()
    data class CloneDetails(val instanceId: String) : Screen()
    data object Settings : Screen()
    data class ContainerDiagnostics(val cloneId: String? = null) : Screen()
    data object BrandIdentity : Screen()
}

