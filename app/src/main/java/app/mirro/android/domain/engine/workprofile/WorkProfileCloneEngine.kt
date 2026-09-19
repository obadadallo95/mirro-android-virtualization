package app.mirro.android.domain.engine.workprofile

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import app.mirro.android.domain.engine.CloneEngine
import app.mirro.android.domain.engine.EngineAvailability
import app.mirro.android.domain.engine.EngineExecutionResult
import app.mirro.android.domain.model.CloneConfig
import app.mirro.android.domain.model.CloneEngineType
import app.mirro.android.domain.model.CloneInstance
import app.mirro.android.domain.model.InstalledApp
import app.mirro.android.domain.model.StorageMetrics

/**
 * Strategy implementation targeting Android Managed Profile / Work Profile.
 *
 * Exposes explicit status when under development and never pretends a profile was provisioned.
 */
class WorkProfileCloneEngine(
    private val context: Context
) : CloneEngine {

    override val engineType: CloneEngineType = CloneEngineType.WORK_PROFILE

    override fun checkAvailability(): EngineAvailability {
        val hasManagedUsersFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val isDeviceOwner = dpm?.isDeviceOwnerApp(context.packageName) ?: false
        val isProfileOwner = dpm?.isProfileOwnerApp(context.packageName) ?: false

        val missing = mutableListOf<String>()
        if (!hasManagedUsersFeature) missing.add("OS lacks PackageManager.FEATURE_MANAGED_USERS")
        if (!isDeviceOwner && !isProfileOwner) missing.add("App does not currently hold Device/Profile Owner privileges")

        return EngineAvailability(
            isAvailable = hasManagedUsersFeature && (isDeviceOwner || isProfileOwner),
            summary = "Android Enterprise / Work Profile engine. Requires Profile Owner provisioning.",
            missingRequirements = missing
        )
    }

    override suspend fun createClone(app: InstalledApp, config: CloneConfig): EngineExecutionResult<CloneInstance> {
        return EngineExecutionResult.NotImplemented(
            engineType = engineType,
            reason = "Work Profile provisioning requires DPM management or user enrollment flow. Scheduled for Milestone 2."
        )
    }

    override suspend fun launchInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        return EngineExecutionResult.NotImplemented(engineType, "Work Profile launcher bridge not attached.")
    }

    override suspend fun freezeInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        return EngineExecutionResult.NotImplemented(engineType, "Work Profile pause/suspend requires DPM.")
    }

    override suspend fun unfreezeInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        return EngineExecutionResult.NotImplemented(engineType, "Work Profile unpause requires DPM.")
    }

    override suspend fun deleteInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        return EngineExecutionResult.NotImplemented(engineType, "Work Profile removal requires DPM.")
    }

    override suspend fun getStorageUsage(instance: CloneInstance): StorageMetrics {
        return StorageMetrics(
            apkSizeBytes = null,
            dataSizeBytes = null,
            cacheSizeBytes = null
        )
    }

    override suspend fun createShortcut(instance: CloneInstance): EngineExecutionResult<Unit> {
        return EngineExecutionResult.NotImplemented(engineType, "Work Profile shortcut binding requires LauncherApps API.")
    }
}
