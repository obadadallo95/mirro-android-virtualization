package app.mirro.android.domain.engine.workprofile

import android.content.Context
import android.content.pm.CrossProfileApps
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import app.mirro.android.data.repository.CloneInstanceRepository
import app.mirro.android.data.repository.ShortcutRepository
import app.mirro.android.domain.engine.CloneEngine
import app.mirro.android.domain.engine.EngineAvailability
import app.mirro.android.domain.engine.EngineExecutionResult
import app.mirro.android.domain.model.CloneConfig
import app.mirro.android.domain.model.CloneEngineType
import app.mirro.android.domain.model.CloneInstance
import app.mirro.android.domain.model.InstalledApp
import app.mirro.android.domain.model.ProfileType
import app.mirro.android.domain.model.StorageMetrics
import java.util.UUID

/**
 * Strategy implementation targeting Android Managed Profile / Work Profile.
 *
 * Provides real cross-profile application launching targeting the isolated
 * Mirro Space [UserHandle] via official [LauncherApps] and [CrossProfileApps] APIs.
 *
 * CRITICAL: Never falls back to [android.content.pm.PackageManager.getLaunchIntentForPackage]
 * which would launch the personal profile instance.
 */
class WorkProfileCloneEngine(
    private val context: Context,
    private val provisioningManager: ProfileProvisioningManager = ProfileProvisioningManager(context),
    private val appDiscoveryManager: ProfileAppDiscoveryManager = ProfileAppDiscoveryManager(context, provisioningManager),
    private val cloneRepository: CloneInstanceRepository? = null,
    private val shortcutRepository: ShortcutRepository = ShortcutRepository(context)
) : CloneEngine {

    companion object {
        private const val TAG = "WorkProfileEngine"
    }

    override val engineType: CloneEngineType = CloneEngineType.WORK_PROFILE

    private val launcherApps: LauncherApps? =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
    private val userManager: UserManager? =
        context.getSystemService(Context.USER_SERVICE) as? UserManager

    override fun checkAvailability(): EngineAvailability {
        val status = provisioningManager.getProvisioningStatus()
        val missing = mutableListOf<String>()

        return when (status) {
            is ProvisioningStatus.Active -> {
                EngineAvailability(
                    isAvailable = true,
                    summary = "Mirro Space is active and Profile Owner privileges are established.",
                    missingRequirements = emptyList()
                )
            }
            is ProvisioningStatus.Available -> {
                missing.add("Mirro Space has not been provisioned yet. User setup required.")
                EngineAvailability(
                    isAvailable = false,
                    summary = "Work Profile engine supported on this device. Ready to provision Mirro Space.",
                    missingRequirements = missing
                )
            }
            is ProvisioningStatus.Conflict -> {
                missing.add(status.message)
                EngineAvailability(
                    isAvailable = false,
                    summary = "Cannot provision Mirro Space due to existing managed profile conflict.",
                    missingRequirements = missing
                )
            }
            is ProvisioningStatus.NotSupported -> {
                missing.add(status.reason)
                EngineAvailability(
                    isAvailable = false,
                    summary = "Work Profile engine is not supported on this device architecture.",
                    missingRequirements = missing
                )
            }
            is ProvisioningStatus.Provisioning -> {
                missing.add("Provisioning is currently in progress.")
                EngineAvailability(
                    isAvailable = false,
                    summary = "Provisioning in progress.",
                    missingRequirements = missing
                )
            }
            is ProvisioningStatus.Failed -> {
                missing.add(status.error)
                EngineAvailability(
                    isAvailable = false,
                    summary = "Previous provisioning attempt failed or was canceled.",
                    missingRequirements = missing
                )
            }
        }
    }

    /**
     * Inspects whether a specific app is present inside the Mirro Space.
     */
    fun checkAppAvailability(packageName: String, appLabel: String): ManagedProfileAppStatus {
        return appDiscoveryManager.checkAppAvailability(packageName, appLabel)
    }

    /**
     * Resolves the exact target [UserHandle] for a given [CloneInstance].
     */
    fun resolveTargetUserHandle(instance: CloneInstance): UserHandle? {
        if (instance.userSerialNumber != 0L && userManager != null) {
            try {
                val user = userManager.getUserForSerialNumber(instance.userSerialNumber)
                if (user != null) return user
            } catch (e: Exception) {
                Log.w(TAG, "Failed resolving UserHandle from serial ${instance.userSerialNumber}", e)
            }
        }
        return appDiscoveryManager.getWorkProfileUserHandle()
    }

    override suspend fun createClone(app: InstalledApp, config: CloneConfig): EngineExecutionResult<CloneInstance> {
        val availability = checkAppAvailability(app.packageName, app.label)

        when (availability) {
            is ManagedProfileAppStatus.ProfileNotReady -> {
                return EngineExecutionResult.Failure(
                    error = IllegalStateException("Mirro Space not ready: ${availability.reason}"),
                    userMessage = "Mirro Space is not active. Please create your Mirro Space first."
                )
            }
            is ManagedProfileAppStatus.AppUnavailable -> {
                return EngineExecutionResult.Failure(
                    error = IllegalStateException(availability.reason),
                    userMessage = "Source application is not installed on the primary device profile."
                )
            }
            is ManagedProfileAppStatus.BlockedByPlatform -> {
                return EngineExecutionResult.Failure(
                    error = IllegalStateException(availability.reason),
                    userMessage = availability.reason
                )
            }
            is ManagedProfileAppStatus.AppNotInstalledInProfile,
            is ManagedProfileAppStatus.InstallActionRequired -> {
                return EngineExecutionResult.Failure(
                    error = IllegalStateException("Package not installed in Work Profile"),
                    userMessage = "Install ${app.label} in Mirro Space to continue."
                )
            }
            is ManagedProfileAppStatus.AppAvailableInProfile -> {
                val instance = CloneInstance(
                    id = UUID.randomUUID().toString(),
                    originalPackageName = app.packageName,
                    originalAppLabel = app.label,
                    customName = config.customName.ifBlank { "${app.label} (Mirro)" },
                    badgeColorHex = config.badgeColorHex,
                    badgeSymbol = config.badgeSymbol,
                    engineType = CloneEngineType.WORK_PROFILE,
                    profileType = ProfileType.MIRRO_MANAGED,
                    userSerialNumber = availability.userSerialNumber,
                    isLocked = config.isLocked,
                    createdAt = System.currentTimeMillis()
                )

                cloneRepository?.saveInstance(instance)

                return EngineExecutionResult.Success(
                    data = instance,
                    message = "Successfully created isolated second instance in Mirro Space."
                )
            }
        }
    }

    /**
     * Executes real cross-profile launch targeting the managed profile user.
     *
     * Returns structured [WorkProfileLaunchResult] indicating success or specific root cause.
     */
    fun executeProfileLaunch(instance: CloneInstance): WorkProfileLaunchResult {
        val targetUser = resolveTargetUserHandle(instance)
            ?: return WorkProfileLaunchResult.Failure(
                status = WorkProfileLaunchStatus.PROFILE_UNAVAILABLE,
                reason = "Mirro Space is not active or its profile user handle could not be resolved on this device.",
                userActionRequired = "Please ensure Mirro Space is set up in Settings."
            )

        // Check if Quiet Mode (Work Profile paused) is currently enabled
        val isPaused = try {
            userManager?.isQuietModeEnabled(targetUser) == true
        } catch (e: Exception) {
            Log.d(TAG, "isQuietModeEnabled query error: ${e.message}")
            false
        }

        if (isPaused) {
            return WorkProfileLaunchResult.Failure(
                status = WorkProfileLaunchStatus.PROFILE_PAUSED,
                reason = "Mirro Space is currently paused (Quiet Mode enabled).",
                userActionRequired = "Unpause your Work Profile from Quick Settings or Settings to launch."
            )
        }

        val launcher = launcherApps
            ?: return WorkProfileLaunchResult.Failure(
                status = WorkProfileLaunchStatus.PROFILE_UNAVAILABLE,
                reason = "LauncherApps system service is unavailable."
            )

        val activities = try {
            launcher.getActivityList(instance.originalPackageName, targetUser)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query launcher activities for ${instance.originalPackageName}: ${e.message}")
            null
        }

        if (activities.isNullOrEmpty()) {
            val isInstalled = appDiscoveryManager.isAppInstalledInWorkProfile(instance.originalPackageName)
            return if (!isInstalled) {
                WorkProfileLaunchResult.Failure(
                    status = WorkProfileLaunchStatus.APP_NOT_INSTALLED,
                    reason = "${instance.originalAppLabel} is not installed inside Mirro Space.",
                    userActionRequired = "Open Google Play Store in Mirro Space to add ${instance.originalAppLabel}."
                )
            } else {
                WorkProfileLaunchResult.Failure(
                    status = WorkProfileLaunchStatus.NO_LAUNCHABLE_ACTIVITY,
                    reason = "${instance.originalAppLabel} does not expose a launchable main activity in Mirro Space."
                )
            }
        }

        val launchActivity = activities.first()
        val component = launchActivity.componentName

        var success = false
        var launchError: Exception? = null

        // Primary mechanism: LauncherApps.startMainActivity
        try {
            launcher.startMainActivity(component, targetUser, null, null)
            success = true
        } catch (e: Exception) {
            Log.w(TAG, "LauncherApps.startMainActivity failed: ${e.message}, attempting CrossProfileApps bridge...")
            launchError = e

            // Secondary mechanism: CrossProfileApps (Android 9+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val crossProfileApps = context.getSystemService(Context.CROSS_PROFILE_APPS_SERVICE) as? CrossProfileApps
                if (crossProfileApps != null) {
                    try {
                        crossProfileApps.startMainActivity(component, targetUser)
                        success = true
                        launchError = null
                    } catch (cpe: Exception) {
                        Log.e(TAG, "CrossProfileApps.startMainActivity failed: ${cpe.message}")
                        launchError = cpe
                    }
                }
            }
        }

        return if (success) {
            WorkProfileLaunchResult.Success(
                componentName = component.flattenToShortString(),
                message = "Launched ${instance.customName} in Mirro Space."
            )
        } else {
            WorkProfileLaunchResult.Failure(
                status = WorkProfileLaunchStatus.CROSS_PROFILE_LAUNCH_BLOCKED,
                reason = "Cross-profile launch blocked by platform security policy: ${launchError?.message ?: "Security restriction"}",
                userActionRequired = "Check that Mirro has launch permissions and target app is not restricted."
            )
        }
    }

    override suspend fun launchInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        val result = executeProfileLaunch(instance)
        return when (result) {
            is WorkProfileLaunchResult.Success -> {
                cloneRepository?.updateLaunchSuccess(instance.id, System.currentTimeMillis())
                EngineExecutionResult.Success(Unit, result.message)
            }
            is WorkProfileLaunchResult.Failure -> {
                val message = result.userActionRequired ?: result.reason
                EngineExecutionResult.Failure(
                    error = IllegalStateException(result.reason),
                    userMessage = message
                )
            }
        }
    }

    override suspend fun freezeInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        // Honest Android architecture: Android restricts quiet mode toggles from personal profile
        // apps to the native Quick Settings / Settings tile. We do NOT fake freeze with a DB boolean.
        return EngineExecutionResult.Unsupported(
            reason = "Direct programmatic pausing is restricted by Android security. Use the native 'Work Profile' toggle in device Quick Settings to pause or resume Mirro Space."
        )
    }

    override suspend fun unfreezeInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        return EngineExecutionResult.Unsupported(
            reason = "Direct programmatic unpausing is restricted by Android security. Use the native 'Work Profile' toggle in device Quick Settings to pause or resume Mirro Space."
        )
    }

    override suspend fun deleteInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        return if (cloneRepository != null) {
            cloneRepository.deleteInstance(instance.id)
            EngineExecutionResult.Success(Unit, "Removed clone instance registration from Mirro.")
        } else {
            EngineExecutionResult.Success(Unit)
        }
    }

    override suspend fun getStorageUsage(instance: CloneInstance): StorageMetrics {
        return StorageMetrics(
            apkSizeBytes = null,
            dataSizeBytes = null,
            cacheSizeBytes = null
        )
    }

    override suspend fun createShortcut(instance: CloneInstance): EngineExecutionResult<Unit> {
        val supported = shortcutRepository.isRequestPinShortcutSupported()
        if (!supported) {
            return EngineExecutionResult.Unsupported(
                reason = "Your default home launcher does not support pinned desktop shortcuts."
            )
        }

        val requested = shortcutRepository.requestPinShortcut(instance)
        return if (requested) {
            EngineExecutionResult.Success(
                data = Unit,
                message = "Pinned desktop shortcut created for ${instance.customName} (Mirro Space)."
            )
        } else {
            EngineExecutionResult.Failure(
                error = IllegalStateException("Failed to pin launcher shortcut"),
                userMessage = "Could not create pinned shortcut. Please check launcher permissions."
            )
        }
    }
}

