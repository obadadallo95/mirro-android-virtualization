package app.mirro.android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.mirro.android.data.local.dao.CloneInstanceDao
import app.mirro.android.data.local.entity.CloneInstanceEntity

@Database(entities = [CloneInstanceEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cloneInstanceDao(): CloneInstanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mirro_database"
                )
                    .addMigrations(MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE clone_instances ADD COLUMN runtimeState TEXT NOT NULL DEFAULT 'REGISTERED'"
                )
                database.execSQL(
                    "ALTER TABLE clone_instances ADD COLUMN latestFailureStage TEXT"
                )
                database.execSQL(
                    "ALTER TABLE clone_instances ADD COLUMN latestFailureReason TEXT"
                )
                database.execSQL(
                    "UPDATE clone_instances SET runtimeState = 'REGISTERED', isRuntimeVerified = 0, latestFailureStage = NULL, latestFailureReason = NULL"
                )
            }
        }
    }
}
