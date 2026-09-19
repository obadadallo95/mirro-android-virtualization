package app.mirro.android.domain.engine.workprofile

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import app.mirro.android.domain.model.ProfileAppIdentity
import app.mirro.android.domain.model.ProfileType

/**
 * Discovers and validates Android application presence across user profiles.
 *
 * Utilizes official Android Enterprise APIs:
 * - [LauncherApps] for cross-profile activity and package discovery
 * - [UserManager] for user handle enumeration and stable serial number resolution
 * - [DevicePolicyManager] for managed profile state verification and package installation
 */
class ProfileAppDiscoveryManager(
    private val context: Context,
    private val provisioningManager: ProfileProvisioningManager = ProfileProvisioningManager(context)
) {
    companion object {
        private const val TAG = "ProfileAppDiscovery"
    }

    private val packageManager: PackageManager = context.packageManager
    private val launcherApps: LauncherApps? =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
    private val userManager: UserManager? =
        context.getSystemService(Context.USER_SERVICE) as? UserManager
    private val devicePolicyManager: DevicePolicyManager? =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager

    /**
     * Resolves the UserHandle of the Mirro Managed Work Profile if one exists.
     */
    fun getWorkProfileUserHandle(): UserHandle? {
        val myUser = Process.myUserHandle()
        val profiles = try {
            launcherApps?.profiles ?: userManager?.userProfiles ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve user profiles", e)
            emptyList()
        }

        for (user in profiles) {
            if (user != myUser) {
                // Secondary profile found (Managed Profile)
                return user
            }
        }
        return null
    }

    /**
     * Returns the stable serial number for the Mirro Managed Work Profile user.
     * Android UserHandle IDs are not stable across reboots, but user serial numbers are.
     */
    fun getWorkProfileSerialNumber(): Long? {
        val workUser = getWorkProfileUserHandle() ?: return null
        return try {
            userManager?.getSerialNumberForUser(workUser)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve serial number for user", e)
            null
        }
    }

    /**
     * Checks if a package is installed in the primary personal profile.
     */
    fun isAppInstalledInPersonal(packageName: String): Boolean {
        return try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PackageManager.PackageInfoFlags.of(0L)
            } else {
                0
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, flags as PackageManager.PackageInfoFlags)
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, flags as Int)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if a package is installed and launchable inside Mirro Space (Work Profile).
     *
     * Uses official LauncherApps.getActivityList(packageName, workProfileUser).
     */
    fun isAppInstalledInWorkProfile(packageName: String): Boolean {
        val workUser = getWorkProfileUserHandle() ?: return false
        val launcher = launcherApps ?: return false

        return try {
            val activities = launcher.getActivityList(packageName, workUser)
            activities != null && activities.isNotEmpty()
        } catch (e: Exception) {
            Log.d(TAG, "Error checking work profile activities for $packageName: ${e.message}")
            false
        }
    }

    /**
     * Evaluates comprehensive availability status for a target app in Mirro Space.
     */
    fun checkAppAvailability(packageName: String, appLabel: String): ManagedProfileAppStatus {
        val provisioningStatus = provisioningManager.getProvisioningStatus()
        if (provisioningStatus !is ProvisioningStatus.Active) {
            return ManagedProfileAppStatus.ProfileNotReady(
                reason = when (provisioningStatus) {
                    is ProvisioningStatus.Available -> "Mirro Space has not been provisioned on this device."
                    is ProvisioningStatus.Conflict -> provisioningStatus.message
                    is ProvisioningStatus.NotSupported -> provisioningStatus.reason
                    is ProvisioningStatus.Provisioning -> "Mirro Space provisioning is in progress."
                    is ProvisioningStatus.Failed -> provisioningStatus.error
                    else -> "Mirro Space is unavailable."
                }
            )
        }

        if (!isAppInstalledInPersonal(packageName)) {
            return ManagedProfileAppStatus.AppUnavailable(
                reason = "Application is not installed in the personal user profile."
            )
        }

        // Check platform blockers (system app, sharedUserId)
        try {
            val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }

            if (!pkgInfo.sharedUserId.isNullOrBlank()) {
                return ManagedProfileAppStatus.BlockedByPlatform(
                    reason = "Application uses android:sharedUserId=\"${pkgInfo.sharedUserId}\" and cannot be isolated."
                )
            }

            val appInfo = pkgInfo.applicationInfo
            val isSystem = if (appInfo != null) {
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            } else false

            if (isSystem) {
                return ManagedProfileAppStatus.BlockedByPlatform(
                    reason = "System applications cannot be duplicated across profile sandboxes."
                )
            }
        } catch (_: Exception) {
            // If we cannot inspect, proceed to check work profile presence
        }

        val workSerial = getWorkProfileSerialNumber() ?: 0L
        val isPresentInWorkProfile = isAppInstalledInWorkProfile(packageName)

        return if (isPresentInWorkProfile) {
            ManagedProfileAppStatus.AppAvailableInProfile(
                packageName = packageName,
                appLabel = appLabel,
                userSerialNumber = workSerial,
                isLaunchable = true
            )
        } else {
            ManagedProfileAppStatus.InstallActionRequired(
                packageName = packageName,
                appLabel = appLabel,
                userSerialNumber = workSerial,
                canAttemptDirectInstall = provisioningManager.isProfileOwner(),
                instructions = "Install $appLabel in Mirro Space to continue."
            )
        }
    }

    /**
     * Creates a profile-aware identity for the specified package and profile.
     */
    fun createProfileIdentity(packageName: String, profileType: ProfileType): ProfileAppIdentity {
        val serial = when (profileType) {
            ProfileType.PERSONAL -> 0L
            ProfileType.MIRRO_MANAGED -> getWorkProfileSerialNumber() ?: 0L
        }
        return ProfileAppIdentity(
            packageName = packageName,
            profileType = profileType,
            userSerialNumber = serial
        )
    }

    /**
     * Attempts to enable or install the existing package inside the managed profile
     * using the official [DevicePolicyManager.installExistingPackage] API if Profile Owner.
     *
     * Returns true if successfully enabled by the system, false if user action is required.
     */
    fun attemptEnableExistingPackage(packageName: String): Boolean {
        val dpm = devicePolicyManager ?: return false
        val admin = MirroDeviceAdminReceiver.getComponentName(context)

        return try {
            if (dpm.isProfileOwnerApp(context.packageName)) {
                dpm.installExistingPackage(admin, packageName)
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "installExistingPackage failed or not permitted on current user: ${e.message}")
            false
        }
    }

    /**
     * Creates an Intent to install the application inside the managed profile via Google Play Store.
     *
     * Allows user to install the exact application into Mirro Space without downloading APKs or sideloading.
     */
    fun createInstallInWorkProfileIntent(packageName: String): Intent {
        val uri = Uri.parse("market://details?id=$packageName")
        return Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
