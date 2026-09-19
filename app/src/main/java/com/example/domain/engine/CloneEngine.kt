package com.example.domain.engine

import com.example.domain.model.CloneConfig
import com.example.domain.model.CloneEngineType
import com.example.domain.model.CloneInstance
import com.example.domain.model.InstalledApp
import com.example.domain.model.StorageMetrics

/**
 * Result wrapper for engine lifecycle operations.
 */
sealed class EngineExecutionResult<out T> {
    data class Success<out T>(val data: T, val message: String? = null) : EngineExecutionResult<T>()
    data class NotImplemented(val engineType: CloneEngineType, val reason: String) : EngineExecutionResult<Nothing>()
    data class Unsupported(val reason: String) : EngineExecutionResult<Nothing>()
    data class Failure(val error: Throwable, val userMessage: String) : EngineExecutionResult<Nothing>()
}

/**
 * Engine availability diagnostic report.
 */
data class EngineAvailability(
    val isAvailable: Boolean,
    val summary: String,
    val missingRequirements: List<String> = emptyList()
)

/**
 * Clean architectural abstraction for app cloning and process isolation.
 *
 * Designed to support multiple coexisting isolation strategies (e.g., Android Work Profile /
 * Managed Profile, Virtual Container Hooking, or Blueprint Staging).
 */
interface CloneEngine {
    val engineType: CloneEngineType

    /**
     * Checks if the host Android device meets this engine's hardware/OS requirements.
     */
    fun checkAvailability(): EngineAvailability

    /**
     * Creates or registers an isolated instance for the target application.
     */
    suspend fun createClone(app: InstalledApp, config: CloneConfig): EngineExecutionResult<CloneInstance>

    /**
     * Launches the isolated instance process with independent sandbox data.
     */
    suspend fun launchInstance(instance: CloneInstance): EngineExecutionResult<Unit>

    /**
     * Suspends/freezes background processes associated with the instance.
     */
    suspend fun freezeInstance(instance: CloneInstance): EngineExecutionResult<Unit>

    /**
     * Unfreezes/restores the instance.
     */
    suspend fun unfreezeInstance(instance: CloneInstance): EngineExecutionResult<Unit>

    /**
     * Permanently deletes the instance and wipes its isolated sandboxed storage.
     */
    suspend fun deleteInstance(instance: CloneInstance): EngineExecutionResult<Unit>

    /**
     * Queries storage consumed by this specific isolated instance.
     */
    suspend fun getStorageUsage(instance: CloneInstance): StorageMetrics

    /**
     * Adds an Android home-screen shortcut directly bound to this clone instance.
     */
    suspend fun createShortcut(instance: CloneInstance): EngineExecutionResult<Unit>
}
