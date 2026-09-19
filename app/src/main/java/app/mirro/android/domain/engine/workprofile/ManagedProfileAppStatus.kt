package app.mirro.android.domain.engine.workprofile

import androidx.annotation.StringRes
import app.mirro.android.R

/**
 * Explicit state model describing the availability of an application inside Mirro Space (Work Profile).
 *
 * Core Engineering Principle:
 * Never simulate success or fake app presence.
 * Mirro must only consider an app available when verified via official LauncherApps APIs.
 */
sealed class ManagedProfileAppStatus {

    /**
     * Mirro Space (Work Profile) has not been provisioned or is disabled.
     */
    data class ProfileNotReady(
        val reason: String
    ) : ManagedProfileAppStatus()

    /**
     * Target application is not installed on the host device (personal profile).
     */
    data class AppUnavailable(
        val reason: String
    ) : ManagedProfileAppStatus()

    /**
     * Target application is blocked from cloning by system platform policies
     * (e.g., system package FLAG_SYSTEM, sharedUserId UID binding, or vendor policy).
     */
    data class BlockedByPlatform(
        val reason: String
    ) : ManagedProfileAppStatus()

    /**
     * Mirro Space is active, but the target application is not yet installed inside the profile.
     */
    data class AppNotInstalledInProfile(
        val packageName: String,
        val appLabel: String,
        val userSerialNumber: Long
    ) : ManagedProfileAppStatus()

    /**
     * Action is required by the user or system to make the package available in the profile
     * (e.g. Prompting user to install from Work Profile Google Play Store).
     */
    data class InstallActionRequired(
        val packageName: String,
        val appLabel: String,
        val userSerialNumber: Long,
        val canAttemptDirectInstall: Boolean,
        val instructions: String
    ) : ManagedProfileAppStatus()

    /**
     * The application package is verified and present inside Mirro Space (Work Profile).
     * Ready to configure and register an isolated clone instance.
     */
    data class AppAvailableInProfile(
        val packageName: String,
        val appLabel: String,
        val userSerialNumber: Long,
        val isLaunchable: Boolean = true
    ) : ManagedProfileAppStatus()

    val isReadyForCloneCreation: Boolean
        get() = this is AppAvailableInProfile
}
