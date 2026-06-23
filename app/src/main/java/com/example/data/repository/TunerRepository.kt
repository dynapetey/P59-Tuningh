package com.example.data.repository

import com.example.data.database.TunerDao
import com.example.data.model.CalFile
import com.example.data.model.LogDataPoint
import com.example.data.model.LogSession
import kotlinx.coroutines.flow.Flow

class TunerRepository(private val tunerDao: TunerDao) {

    val allCalibrations: Flow<List<CalFile>> = tunerDao.getAllCalibrations()
    val allSessions: Flow<List<LogSession>> = tunerDao.getAllSessions()

    suspend fun getCalibrationById(id: Int): CalFile? {
        return tunerDao.getCalibrationById(id)
    }

    suspend fun insertCalibration(cal: CalFile): Long {
        return tunerDao.insertCalibration(cal)
    }

    suspend fun deleteCalibration(id: Int) {
        tunerDao.deleteCalibration(id)
    }

    suspend fun insertSession(session: LogSession): Long {
        return tunerDao.insertSession(session)
    }

    suspend fun updateSessionStats(id: Int, dur: Int, avg: Int, max: Int, maxMph: Int) {
        tunerDao.updateSessionStats(id, dur, avg, max, maxMph)
    }

    suspend fun deleteSession(id: Int) {
        tunerDao.deleteSession(id)
    }

    fun getPointsForSession(sessionId: Int): Flow<List<LogDataPoint>> {
        return tunerDao.getPointsForSession(sessionId)
    }

    suspend fun insertPoints(points: List<LogDataPoint>) {
        tunerDao.insertPoints(points)
    }
}
