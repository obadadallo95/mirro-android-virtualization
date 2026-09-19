package com.example.domain.engine.blueprint

import android.content.Context
import android.content.Intent
import com.example.data.repository.CloneInstanceRepository
import com.example.data.repository.ShortcutRepository
import com.example.data.repository.StorageRepository
import com.example.domain.engine.CloneEngine
import com.example.domain.engine.EngineAvailability
import com.example.domain.engine.EngineExecutionResult
import com.example.domain.model.CloneConfig
import com.example.domain.model.CloneEngineType
import com.example.domain.model.CloneInstance
import com.example.domain.model.InstalledApp
import com.example.domain.model.StorageMetrics
import java.util.UUID

/**
 * The default foundation engine for AppTwin Milestone 1.
 *
 * This engine manages the real Android architectural foundation:
 * persistence, UI state, shortcut dispatch, and storage analysis.
 *
 * In accordance with our core engineering principles, it NEVER fakes
 * sandboxed process virtualization. Any attempt to execute low-level
 * operations discloses their architectural staging status honestly.
 */
class BlueprintCloneEngine(
    private val context: Context,
    private val cloneInstanceRepository: CloneInstanceRepository,
    private val storageRepository: StorageRepository,
    private val shortcutRepository: ShortcutRepository
) : CloneEngine {

    override val engineType: CloneEngineType = CloneEngineType.BLUEPRINT_STAGING

    override fun checkAvailability(): EngineAvailability {
        return EngineAvailability(
            isAvailable = true,
            summary = "Production architectural foundation is ready. Local Room persistence and shortcut services active."
        )
    }

    override suspend fun createClone(app: InstalledApp, config: CloneConfig): EngineExecutionResult<CloneInstance> {
        val instance = CloneInstance(
            id = UUID.randomUUID().toString(),
            originalPackageName = config.originalPackageName,
            originalAppLabel = config.originalAppLabel,
            customName = config.customName.ifBlank { "${config.originalAppLabel} (Twin)" },
            badgeColorHex = config.badgeColorHex,
            badgeSymbol = config.badgeSymbol,
            engineType = config.engineType,
            isFrozen = false,
            isLocked = config.isLocked,
            storageSizeBytes = 16L * 1024 * 1024, // Baseline profile reservation
            createdAt = System.currentTimeMillis(),
            lastLaunchedAt = null
        )

        cloneInstanceRepository.saveInstance(instance)
        return EngineExecutionResult.Success(
            data = instance,
            message = "Clone blueprint configuration saved in local database."
        )
    }

    override suspend fun launchInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        cloneInstanceRepository.updateLastLaunched(instance.id)

        // Attempt to launch the original host package for demonstration purposes
        val launchIntent = context.packageManager.getLaunchIntentForPackage(instance.originalPackageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            EngineExecutionResult.Success(
                data = Unit,
                message = "Base app launched for demonstration. Sandboxed virtualization engine is currently in development."
            )
        } else {
            EngineExecutionResult.Failure(
                error = IllegalStateException("Package not launchable"),
                userMessage = "Could not find launch intent for ${instance.originalPackageName}."
            )
        }
    }

    override suspend fun freezeInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        val newFrozenState = !instance.isFrozen
        cloneInstanceRepository.setFrozenState(instance.id, newFrozenState)
        return EngineExecutionResult.Success(
            data = Unit,
            message = if (newFrozenState) "Instance marked as frozen" else "Instance marked as ready"
        )
    }

    override suspend fun unfreezeInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        cloneInstanceRepository.setFrozenState(instance.id, false)
        return EngineExecutionResult.Success(data = Unit, message = "Instance marked as ready")
    }

    override suspend fun deleteInstance(instance: CloneInstance): EngineExecutionResult<Unit> {
        cloneInstanceRepository.deleteInstance(instance.id)
        return EngineExecutionResult.Success(data = Unit, message = "Clone instance deleted.")
    }

    override suspend fun getStorageUsage(instance: CloneInstance): StorageMetrics {
        return storageRepository.getStorageMetrics(instance)
    }

    override suspend fun createShortcut(instance: CloneInstance): EngineExecutionResult<Unit> {
        val success = shortcutRepository.requestPinShortcut(instance)
        return if (success) {
            EngineExecutionResult.Success(Unit, "Shortcut pinned to launcher")
        } else {
            EngineExecutionResult.Failure(
                error = UnsupportedOperationException("Pin shortcut not supported"),
                userMessage = "Launcher shortcut pinning is not supported on this launcher."
            )
        }
    }
}
