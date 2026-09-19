package app.mirro.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.mirro.android.domain.model.CloneEngineType
import app.mirro.android.domain.model.CloneInstance

@Entity(tableName = "clone_instances")
data class CloneInstanceEntity(
    @PrimaryKey val id: String,
    val originalPackageName: String,
    val originalAppLabel: String,
    val customName: String,
    val badgeColorHex: String,
    val badgeSymbol: String,
    val engineType: String = CloneEngineType.VIRTUALIZED_CONTAINER.name,
    val isFrozen: Boolean,
    val isLocked: Boolean,
    val storageSizeBytes: Long,
    val createdAt: Long,
    val lastLaunchedAt: Long?,
    val isRuntimeVerified: Boolean = false
) {
    fun toDomainModel(): CloneInstance {
        val engine = try {
            CloneEngineType.valueOf(engineType)
        } catch (_: Exception) {
            CloneEngineType.VIRTUALIZED_CONTAINER
        }
        return CloneInstance(
            id = id,
            originalPackageName = originalPackageName,
            originalAppLabel = originalAppLabel,
            customName = customName,
            badgeColorHex = badgeColorHex,
            badgeSymbol = badgeSymbol,
            engineType = engine,
            isFrozen = isFrozen,
            isLocked = isLocked,
            storageSizeBytes = storageSizeBytes,
            createdAt = createdAt,
            lastLaunchedAt = lastLaunchedAt,
            isRuntimeVerified = isRuntimeVerified
        )
    }

    companion object {
        fun fromDomainModel(instance: CloneInstance): CloneInstanceEntity {
            return CloneInstanceEntity(
                id = instance.id,
                originalPackageName = instance.originalPackageName,
                originalAppLabel = instance.originalAppLabel,
                customName = instance.customName,
                badgeColorHex = instance.badgeColorHex,
                badgeSymbol = instance.badgeSymbol,
                engineType = instance.engineType.name,
                isFrozen = instance.isFrozen,
                isLocked = instance.isLocked,
                storageSizeBytes = instance.storageSizeBytes,
                createdAt = instance.createdAt,
                lastLaunchedAt = instance.lastLaunchedAt,
                isRuntimeVerified = instance.isRuntimeVerified
            )
        }
    }
}

