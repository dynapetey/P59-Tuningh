package com.example.data.database

import androidx.room.*
import com.example.data.model.CalFile
import com.example.data.model.LogDataPoint
import com.example.data.model.LogSession
import kotlinx.coroutines.flow.Flow

@Dao
interface TunerDao {

    // Calibrations
    @Query("SELECT * FROM calibration_files ORDER BY lastModified DESC")
    fun getAllCalibrations(): Flow<List<CalFile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalibration(cal: CalFile): Long

    @Query("SELECT * FROM calibration_files WHERE id = :id")
    suspend fun getCalibrationById(id: Int): CalFile?

    @Query("DELETE FROM calibration_files WHERE id = :id")
    suspend fun deleteCalibration(id: Int)

    // Log Sessions
    @Query("SELECT * FROM log_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<LogSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: LogSession): Long

    @Query("UPDATE log_sessions SET durationSeconds = :dur, avgRpm = :avg, maxRpm = :max, maxMph = :maxMph WHERE id = :id")
    suspend fun updateSessionStats(id: Int, dur: Int, avg: Int, max: Int, maxMph: Int)

    @Query("DELETE FROM log_sessions WHERE id = :id")
    suspend fun deleteSession(id: Int)

    // Log Points
    @Query("SELECT * FROM log_data_points WHERE sessionId = :sessionId ORDER BY timestampOffsetMs ASC")
    fun getPointsForSession(sessionId: Int): Flow<List<LogDataPoint>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<LogDataPoint>)
}
