package app.mirro.android.data.repository

import app.mirro.android.data.local.dao.CloneInstanceDao
import app.mirro.android.data.local.entity.CloneInstanceEntity
import app.mirro.android.domain.model.CloneInstance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local Room-backed repository for managing clone instance models.
 */
class CloneInstanceRepository(
    private val cloneInstanceDao: CloneInstanceDao
) {
    val allInstances: Flow<List<CloneInstance>> = cloneInstanceDao.getAllInstances().map { entities ->
        entities.map { it.toDomainModel() }
    }

    suspend fun getInstanceById(id: String): CloneInstance? {
        return cloneInstanceDao.getInstanceById(id)?.toDomainModel()
    }

    suspend fun saveInstance(instance: CloneInstance) {
        cloneInstanceDao.insertInstance(CloneInstanceEntity.fromDomainModel(instance))
    }

    suspend fun updateInstance(instance: CloneInstance) {
        cloneInstanceDao.updateInstance(CloneInstanceEntity.fromDomainModel(instance))
    }

    suspend fun deleteInstance(id: String) {
        cloneInstanceDao.deleteInstanceById(id)
    }

    suspend fun setFrozenState(id: String, isFrozen: Boolean) {
        cloneInstanceDao.setFrozenState(id, isFrozen)
    }

    suspend fun updateCustomName(id: String, newName: String) {
        cloneInstanceDao.updateCustomName(id, newName)
    }

    suspend fun updateLastLaunched(id: String, timestamp: Long = System.currentTimeMillis()) {
        cloneInstanceDao.updateLastLaunched(id, timestamp)
    }
}
