package app.mirro.android.domain.engine.workprofile

/**
 * Structured runtime results for cross-profile application launches.
 */
enum class WorkProfileLaunchStatus {
    LAUNCH_SUCCESS,
    PROFILE_PAUSED,
    PROFILE_UNAVAILABLE,
    APP_NOT_INSTALLED,
    NO_LAUNCHABLE_ACTIVITY,
    CROSS_PROFILE_LAUNCH_BLOCKED
}

/**
 * Detailed outcome of a managed profile launch attempt.
 */
sealed class WorkProfileLaunchResult {
    data class Success(
        val componentName: String,
        val message: String
    ) : WorkProfileLaunchResult()

    data class Failure(
        val status: WorkProfileLaunchStatus,
        val reason: String,
        val userActionRequired: String? = null
    ) : WorkProfileLaunchResult()
}
