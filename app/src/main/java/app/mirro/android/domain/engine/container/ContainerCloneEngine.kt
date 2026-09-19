package app.mirro.android.domain.engine.container

import android.content.Context
import android.content.Intent
import android.os.Build
import app.mirro.android.data.repository.CloneInstanceRepository
import app.mirro.android.data.repository.ShortcutRepository
import app.mirro.android.domain.engine.CloneEngine
import app.mirro.android.domain.engine.EngineAvailability
import app.mirro.android.domain.engine.EngineExecutionResult
import app.mirro.android.domain.engine.container.inspector.ApkInspector
import app.mirro.android.domain.engine.container.runtime.ContainerRuntime
import app.mirro.android.domain.engine.container.runtime.VirtualProcessController
import app.mirro.android.domain.engine.container.storage.VirtualFileSystem
import app.mirro.android.domain.model.CloneConfig
import app.mirro.android.domain.model.CloneEngineType
import app.mirro.android.domain.model.CloneInstance
import app.mirro.android.domain.model.InstalledApp
import app.mirro.android.domain.model.StorageMetrics
import app.mirro.android.ui.container.ContainerHostActivity
import java.util.UUID

/**
 * Primary user-space virtualization and container runtime engine for Mirro.
 *
 * Implements in-app process sandboxing, isolated DEX/Asset loading, per-clone storage
 * redirection, and dedicated WebView data directory partitions without requiring
 * Work Profiles or device management.
 */
class ContainerCloneEngine(
    private val context: Context,
    val virtualFileSystem: VirtualFileSystem = VirtualFileSystem(context),
    val apkInspector: ApkInspector = ApkInspector(context),
    val containerRuntime: ContainerRuntime = ContainerRuntime(context, virtualFileSystem, apkInspector),
    val virtualProcessController: VirtualProcessController = VirtualProcessController(context),
    private val cloneRepository: CloneInstanceRepository? = null,
    private val shortcutRepository: ShortcutRepository = ShortcutRepository(context)
) : CloneEngine {

    override val engineType: CloneEngineType = CloneEngineType.VIRTUALIZED_CONTAINER

    override fun checkAvailability(): EngineAvailability {
        val missing = mutableListOf<String>()
        val isSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

        if (!isSupported) {
            missing.add("Container engine requires Android 7.0 (API 24) or higher.")
        }

        return EngineAvailability(
            isAvailable = isSupported,
            summary = if (isSupported) {
                "Mirro Container user-space sandbox engine is active and ready."
            } else {
                "Android version below minimum container requirement."
            },
            missingRequirements = missing
        )
    }

    override suspend fun createClone(app: InstalledApp, config: CloneConfig): EngineExecutionResult<CloneInstance> {
        return try {
            val cloneId = UUID.randomUUID().toString()

            // Verify target APK exists
            val descriptor = apkInspector.inspect(app.packageName)
            if (descriptor == null) {
                return EngineExecutionResult.Failure(
                    error = IllegalStateException("APK not found"),
                    userMessage = "Target application '${app.label}' is not installed on this device."
                )
            }

            // Create private isolated filesystem sandbox
            val runtimeIdentity = virtualFileSystem.createSandbox(
                cloneId = cloneId,
                packageName = app.packageName
            )

            val instance = CloneInstance(
                id = cloneId,
                originalPackageName = app.packageName,
                originalAppLabel = app.label,
                customName = config.customName.ifBlank { "${app.label} (Clone)" },
                badgeColorHex = config.badgeColorHex,
                badgeSymbol = config.badgeSymbol.ifBlank { "2" },
                engineType = CloneEngineType.VIRTUALIZED_CONTAINER,
                isRuntimeVerified = false,
                createdAt = System.currentTimeMillis(),
                lastLaunchedAt = null
            )

            cloneRepository?.saveInstance(instance)

            EngineExecutionResult.Success(
                data = instance,
                message = "Container sandbox initialized for ${instance.customName}"
            )
        } catch (e: Throwable) {
            EngineExecutionResult.Failure(
                error = e,
                userMessage = "Failed to create container clone: ${e.localizedMessage}"
            )
        }
    }

    override suspend fun launchInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        return try {
            val launchIntent = Intent(context, ContainerHostActivity::class.java).apply {
                putExtra(ContainerHostActivity.EXTRA_CLONE_ID, instance.id)
                putExtra(ContainerHostActivity.EXTRA_PACKAGE_NAME, instance.originalPackageName)
                putExtra(ContainerHostActivity.EXTRA_CUSTOM_NAME, instance.customName)
                putExtra(ContainerHostActivity.EXTRA_BADGE_COLOR, instance.badgeColorHex)
                putExtra(ContainerHostActivity.EXTRA_BADGE_SYMBOL, instance.badgeSymbol)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            context.startActivity(launchIntent)
            cloneRepository?.updateLaunchSuccess(instance.id)

            EngineExecutionResult.Success(
                data = Unit,
                message = "Launching ${instance.customName} in Mirro Container..."
            )
        } catch (e: Throwable) {
            EngineExecutionResult.Failure(
                error = e,
                userMessage = "Failed to launch container instance: ${e.localizedMessage}"
            )
        }
    }

    override suspend fun freezeInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        val result = virtualProcessController.freezeInstance(instance)
        if (result is EngineExecutionResult.Success) {
            cloneRepository?.setFrozenState(instance.id, true)
        }
        return result
    }

    override suspend fun unfreezeInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        val result = virtualProcessController.unfreezeInstance(instance)
        if (result is EngineExecutionResult.Success) {
            cloneRepository?.setFrozenState(instance.id, false)
        }
        return result
    }

    override suspend fun deleteInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        return try {
            virtualFileSystem.deleteSandbox(instance.id)
            cloneRepository?.deleteInstance(instance.id)
            EngineExecutionResult.Success(
                data = Unit,
                message = "Instance '${instance.customName}' and its sandbox storage were permanently deleted."
            )
        } catch (e: Throwable) {
            EngineExecutionResult.Failure(
                error = e,
                userMessage = "Failed to delete sandbox: ${e.localizedMessage}"
            )
        }
    }

    override suspend fun getStorageUsage(instance: CloneInstance): StorageMetrics {
        return virtualFileSystem.calculateStorageUsage(instance.id)
    }

    override suspend fun createShortcut(instance: CloneInstance): EngineExecutionResult<Unit> {
        if (!shortcutRepository.isRequestPinShortcutSupported()) {
            return EngineExecutionResult.Unsupported(
                reason = "Your default home launcher does not support pinned desktop shortcuts."
            )
        }

        val success = shortcutRepository.requestPinShortcut(instance)
        return if (success) {
            EngineExecutionResult.Success(
                data = Unit,
                message = "Shortcut for '${instance.customName}' created on home screen."
            )
        } else {
            EngineExecutionResult.Failure(
                error = IllegalStateException("Shortcut pin request denied by system"),
                userMessage = "Failed to create home screen shortcut."
            )
        }
    }
}
