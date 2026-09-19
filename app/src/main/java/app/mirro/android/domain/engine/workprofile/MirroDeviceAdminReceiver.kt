package app.mirro.android.domain.engine.workprofile

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Official DeviceAdminReceiver for Mirro's Work Profile isolation engine.
 *
 * Registered in AndroidManifest.xml with BIND_DEVICE_ADMIN permission.
 * Responsible for handling the managed profile provisioning lifecycle, setting
 * the profile name, and enabling the profile upon completion.
 */
class MirroDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "MirroDeviceAdmin"
        const val PREFS_NAME = "mirro_work_profile_prefs"
        const val KEY_IS_WORK_PROFILE_INSTANCE = "is_work_profile_instance"
        const val KEY_PROVISIONED_TIMESTAMP = "provisioned_timestamp"
        const val PROFILE_NAME = "Mirro"

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, MirroDeviceAdminReceiver::class.java)
        }
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.i(TAG, "Managed profile provisioning completed for Mirro.")

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = getComponentName(context)

        if (dpm != null && dpm.isProfileOwnerApp(context.packageName)) {
            try {
                // Set the profile display label in Android system settings & launcher
                dpm.setProfileName(adminComponent, PROFILE_NAME)
                // Enable the profile so it becomes active and discoverable by LauncherApps
                dpm.setProfileEnabled(adminComponent)
                Log.i(TAG, "Mirro profile name configured and profile enabled successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to configure profile owner after provisioning", e)
            }
        } else {
            Log.w(TAG, "onProfileProvisioningComplete called but app is not recognized as profile owner.")
        }

        // Persist minimal local flag marking this runtime instance as the Work Profile process
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_WORK_PROFILE_INSTANCE, true)
            .putLong(KEY_PROVISIONED_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Mirro DeviceAdmin enabled.")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Mirro DeviceAdmin disabled.")
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_IS_WORK_PROFILE_INSTANCE)
            .apply()
    }
}
