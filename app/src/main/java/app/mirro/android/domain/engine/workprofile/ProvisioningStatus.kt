package app.mirro.android.domain.engine.workprofile

/**
 * Represents the current status of Android Managed Profile / Work Profile on the device.
 */
sealed class ProvisioningStatus {

    /**
     * The device hardware, ROM, or Android version does not support PackageManager.FEATURE_MANAGED_USERS.
     */
    data class NotSupported(val reason: String) : ProvisioningStatus()

    /**
     * The device supports Work Profiles and is ready for the user to initiate the provisioning flow.
     */
    object Available : ProvisioningStatus()

    /**
     * Provisioning is currently in progress via the Android system UI.
     */
    object Provisioning : ProvisioningStatus()

    /**
     * Mirro Space is active and Profile Owner privileges are established.
     */
    data class Active(
        val isCurrentProcessInProfile: Boolean,
        val profileCount: Int = 1
    ) : ProvisioningStatus()

    /**
     * An existing work profile (from another MDM or company profile) is already present on this device.
     */
    data class Conflict(val message: String) : ProvisioningStatus()

    /**
     * Provisioning flow failed or was canceled by the user.
     */
    data class Failed(val error: String) : ProvisioningStatus()
}
