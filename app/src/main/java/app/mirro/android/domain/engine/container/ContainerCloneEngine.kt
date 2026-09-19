package app.mirro.android.domain.engine.container

import app.mirro.android.domain.engine.CloneEngine
import app.mirro.android.domain.engine.EngineAvailability
import app.mirro.android.domain.engine.EngineExecutionResult
import app.mirro.android.domain.model.CloneConfig
import app.mirro.android.domain.model.CloneEngineType
import app.mirro.android.domain.model.CloneInstance
import app.mirro.android.domain.model.InstalledApp
import app.mirro.android.domain.model.StorageMetrics

/**
 * Strategy implementation targeting a virtualized user-space container.
 *
 * In Milestone 1, explicitly reports its architectural status as under construction.
 */
class ContainerCloneEngine : CloneEngine {

    override val engineType: CloneEngineType = CloneEngineType.VIRTUALIZED_CONTAINER

    override fun checkAvailability(): EngineAvailability {
        return EngineAvailability(
            isAvailable = false,
            summary = "Virtual container engine under active research and engineering.",
            missingRequirements = listOf(
                "Dynamic DexClassLoader & binder proxy virtualization hooks under construction",
                "Android 14+ private data directory hardening compatibility pending"
            )
        )
    }

    override suspend fun createClone(app: InstalledApp, config: CloneConfig): EngineExecutionResult<CloneInstance> {
        return EngineExecutionResult.NotImplemented(
            engineType = engineType,
            reason = "Container process hooking engine is currently under development. Please select Architecture Blueprint for testing."
        )
    }

    override suspend fun launchInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        return EngineExecutionResult.NotImplemented(engineType, "Container sandbox runtime not yet bound.")
    }

    override suspend fun freezeInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        return EngineExecutionResult.NotImplemented(engineType, "Container process signal freezer under development.")
    }

    override suspend fun unfreezeInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        return EngineExecutionResult.NotImplemented(engineType, "Container process signal freezer under development.")
    }

    override suspend fun deleteInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        return EngineExecutionResult.NotImplemented(engineType, "Container storage unmount under development.")
    }

    override suspend fun getStorageUsage(instance: CloneInstance): StorageMetrics {
        return StorageMetrics(
            apkSizeBytes = null,
            dataSizeBytes = null,
            cacheSizeBytes = null
        )
    }

    override suspend fun createShortcut(instance: CloneInstance): EngineExecutionResult<Unit> {
        return EngineExecutionResult.NotImplemented(engineType, "Container shortcut dispatcher under development.")
    }
}
