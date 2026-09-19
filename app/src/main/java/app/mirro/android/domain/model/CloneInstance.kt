package app.mirro.android.domain.model

/**
 * Represents an isolated or configured cloned instance of an Android application.
 */
data class CloneInstance(
    val id: String,
    val originalPackageName: String,
    val originalAppLabel: String,
    val customName: String,
    val badgeColorHex: String,
    val badgeSymbol: String,
    val engineType: CloneEngineType,
    val isFrozen: Boolean = false,
    val isLocked: Boolean = false,
    val storageSizeBytes: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLaunchedAt: Long? = null
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
    val engineType: CloneEngineType,
    val isLocked: Boolean = false
)
