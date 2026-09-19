package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.CloneInstanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CloneInstanceDao {

    @Query("SELECT * FROM clone_instances ORDER BY createdAt DESC")
    fun getAllInstances(): Flow<List<CloneInstanceEntity>>

    @Query("SELECT * FROM clone_instances WHERE id = :id LIMIT 1")
    suspend fun getInstanceById(id: String): CloneInstanceEntity?

    @Query("SELECT * FROM clone_instances WHERE originalPackageName = :packageName")
    fun getInstancesForPackage(packageName: String): Flow<List<CloneInstanceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstance(instance: CloneInstanceEntity)

    @Update
    suspend fun updateInstance(instance: CloneInstanceEntity)

    @Query("DELETE FROM clone_instances WHERE id = :id")
    suspend fun deleteInstanceById(id: String)

    @Query("UPDATE clone_instances SET isFrozen = :isFrozen WHERE id = :id")
    suspend fun setFrozenState(id: String, isFrozen: Boolean)

    @Query("UPDATE clone_instances SET customName = :newName WHERE id = :id")
    suspend fun updateCustomName(id: String, newName: String)

    @Query("UPDATE clone_instances SET lastLaunchedAt = :timestamp WHERE id = :id")
    suspend fun updateLastLaunched(id: String, timestamp: Long)
}
