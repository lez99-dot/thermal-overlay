package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "thermal_readings")
data class ThermalReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val cpuTemp: Float,
    val gpuTemp: Float
)

@Entity(tableName = "thermal_config")
data class ThermalConfig(
    @PrimaryKey val key: String,
    val value: String
)

@Dao
interface ThermalDao {
    @Query("SELECT * FROM thermal_readings ORDER BY timestamp DESC LIMIT 60")
    fun getRecentReadingsFlow(): Flow<List<ThermalReading>>

    @Query("SELECT * FROM thermal_readings ORDER BY timestamp DESC")
    suspend fun getRecentReadings(): List<ThermalReading>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReading(reading: ThermalReading)

    @Query("DELETE FROM thermal_readings WHERE timestamp < :cutoff")
    suspend fun clearOldReadings(cutoff: Long)

    @Query("DELETE FROM thermal_readings")
    suspend fun clearAllReadings()

    @Query("SELECT * FROM thermal_config WHERE `key` = :key")
    suspend fun getConfig(key: String): ThermalConfig?

    @Query("SELECT * FROM thermal_config")
    fun getAllConfigFlow(): Flow<List<ThermalConfig>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: ThermalConfig)
}

@Database(entities = [ThermalReading::class, ThermalConfig::class], version = 1, exportSchema = false)
abstract class ThermalDatabase : RoomDatabase() {
    abstract fun dao(): ThermalDao

    companion object {
        @Volatile
        private var INSTANCE: ThermalDatabase? = null

        fun getInstance(context: Context): ThermalDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ThermalDatabase::class.java,
                    "thermal_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
