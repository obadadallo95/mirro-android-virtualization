package app.mirro.android.domain.model

/**
 * Persisted runtime milestones. Activity hosting and verification are intentionally future
 * milestones; Application.onCreate() alone never represents a fully launched clone.
 */
enum class CloneRuntimeState(val label: String) {
    REGISTERED("Registered"),
    APK_LOADED("APK loaded"),
    APPLICATION_BOOTSTRAPPED("Application bootstrapped"),
    ACTIVITY_HOSTED("Activity hosted"),
    RUNTIME_VERIFIED("Runtime verified"),
    FAILED("Failed")
}
