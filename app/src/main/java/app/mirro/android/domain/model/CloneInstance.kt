package app.mirro.android.domain.model

/**
 * Represents an isolated or configured cloned instance of an Android application in Mirro.
 */
data class CloneInstance(
    val id: String,
    val originalPackageName: String,
    val originalAppLabel: String,
    val customName: String,
    val badgeColorHex: String,
    val badgeSymbol: String,
    val engineType: CloneEngineType = CloneEngineType.VIRTUALIZED_CONTAINER,
    val isFrozen: Boolean = false,
    val isLocked: Boolean = false,
    val storageSizeBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLaunchedAt: Long? = null,
    val isRuntimeVerified: Boolean = false,
    val runtimeState: CloneRuntimeState = CloneRuntimeState.REGISTERED,
    val latestFailureStage: String? = null,
    val latestFailureReason: String? = null
)

/**
 * Configuration payload used when initiating a new clone.
 */
data class CloneConfig(
    val originalPackageName: String,
    val originalAppLabel: String,
    val customName: String,
    val badgeColorHex: String,
    val badgeSymbol: String,
    val engineType: CloneEngineType = CloneEngineType.VIRTUALIZED_CONTAINER,
    val isLocked: Boolean = false
)
