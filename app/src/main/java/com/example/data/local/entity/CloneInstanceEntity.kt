package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.CloneEngineType
import com.example.domain.model.CloneInstance

@Entity(tableName = "clone_instances")
data class CloneInstanceEntity(
    @PrimaryKey val id: String,
    val originalPackageName: String,
    val originalAppLabel: String,
    val customName: String,
    val badgeColorHex: String,
    val badgeSymbol: String,
    val engineType: String,
    val isFrozen: Boolean,
    val isLocked: Boolean,
    val storageSizeBytes: Long,
    val createdAt: Long,
    val lastLaunchedAt: Long?
) {
    fun toDomainModel(): CloneInstance {
        val engine = try {
            CloneEngineType.valueOf(engineType)
        } catch (_: Exception) {
            CloneEngineType.BLUEPRINT_STAGING
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
            lastLaunchedAt = lastLaunchedAt
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
                lastLaunchedAt = instance.lastLaunchedAt
            )
        }
    }
}
