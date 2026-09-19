package app.mirro.android.domain.engine.workprofile

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log

/**
 * Manages the detection and execution of Android Managed Profile (Work Profile) provisioning.
 *
 * Utilizes official Android Enterprise APIs (DevicePolicyManager, UserManager, PackageManager)
 * to interact with the system provisioning flow without relying on hidden or deprecated APIs.
 */
class ProfileProvisioningManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "ProfileProvisioning"
    }

    private val packageManager: PackageManager = context.packageManager
    private val devicePolicyManager: DevicePolicyManager? =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    private val userManager: UserManager? =
        context.getSystemService(Context.USER_SERVICE) as? UserManager

    /**
     * Checks if the Android device hardware and OS build support managed profiles.
     */
    fun isManagedUsersSupported(): Boolean {
        return packageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)
    }

    /**
     * Checks if this instance of Mirro is currently a Profile Owner in the calling user profile.
     */
    fun isProfileOwner(): Boolean {
        return devicePolicyManager?.isProfileOwnerApp(context.packageName) ?: false
    }

    /**
     * Checks if Mirro is a Device Owner on this device.
     */
    fun isDeviceOwner(): Boolean {
        return devicePolicyManager?.isDeviceOwnerApp(context.packageName) ?: false
    }

    /**
     * Checks whether the current runtime process is executing inside the Work Profile.
     */
    fun isRunningInWorkProfile(): Boolean {
        val prefs = context.getSharedPreferences(
            MirroDeviceAdminReceiver.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        if (prefs.getBoolean(MirroDeviceAdminReceiver.KEY_IS_WORK_PROFILE_INSTANCE, false)) {
            return true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && userManager != null) {
            try {
                if (userManager.isManagedProfile) {
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "isManagedProfile check: ${e.message}")
            }
        }
        return isProfileOwner()
    }

    /**
     * Checks if the device permits provisioning a new managed profile right now.
     */
    fun isProvisioningAllowed(): Boolean {
        return if (devicePolicyManager != null) {
            try {
                devicePolicyManager.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
            } catch (e: Exception) {
                Log.e(TAG, "Error querying isProvisioningAllowed", e)
                false
            }
        } else {
            false
        }
    }

    /**
     * Inspects the device state and returns the current [ProvisioningStatus].
     */
    fun getProvisioningStatus(): ProvisioningStatus {
        if (!isManagedUsersSupported()) {
            return ProvisioningStatus.NotSupported(
                "This device OS or ROM does not support PackageManager.FEATURE_MANAGED_USERS."
            )
        }

        if (isProfileOwner()) {
            return ProvisioningStatus.Active(
                isCurrentProcessInProfile = isRunningInWorkProfile(),
                profileCount = getDetectedProfilesCount()
            )
        }

        val profilesCount = getDetectedProfilesCount()
        val provisioningAllowed = isProvisioningAllowed()

        if (provisioningAllowed) {
            return ProvisioningStatus.Available
        }

        // If not allowed and more than 1 profile is detected, an existing work profile exists from another manager
        if (profilesCount > 1) {
            return ProvisioningStatus.Conflict(
                "Another work profile or managed space already exists on this device."
            )
        }

        return ProvisioningStatus.NotSupported(
            "Managed profile provisioning is restricted by device policy or OS configuration."
        )
    }

    /**
     * Returns the number of user profiles associated with the current user.
     */
    fun getDetectedProfilesCount(): Int {
        return try {
            userManager?.userProfiles?.size ?: 1
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read userProfiles", e)
            1
        }
    }

    /**
     * Creates the official system Intent to initiate Managed Profile provisioning.
     *
     * Launches Android's native provisioning UI where the user explicitly grants
     * profile creation permissions.
     */
    fun createProvisioningIntent(): Intent {
        val adminComponent = MirroDeviceAdminReceiver.getComponentName(context)
        return Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, adminComponent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Allows provisioning to skip unneeded interactive screens when permitted by OS
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
            }
        }
    }

    /**
     * Evaluates the Activity result code after the system provisioning activity finishes.
     */
    fun handleActivityResult(resultCode: Int): ProvisioningStatus {
        return when (resultCode) {
            Activity.RESULT_OK -> {
                Log.i(TAG, "System provisioning returned RESULT_OK.")
                getProvisioningStatus()
            }
            Activity.RESULT_CANCELED -> {
                Log.w(TAG, "System provisioning was canceled by the user.")
                ProvisioningStatus.Failed("Provisioning was canceled or rejected by the user.")
            }
            else -> {
                Log.e(TAG, "System provisioning finished with code: $resultCode")
                ProvisioningStatus.Failed("Provisioning failed with result code $resultCode.")
            }
        }
    }
}
