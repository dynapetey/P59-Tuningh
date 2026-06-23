package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "log_data_points",
    foreignKeys = [
        ForeignKey(
            entity = LogSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class LogDataPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: Int,
    val timestampOffsetMs: Long, // relative to session start
    val rpm: Int,
    val mph: Int,
    val mapKpa: Float,
    val coolantTempF: Int,
    val sparkAdvance: Float,
    val shortTermFuelTrimPercent: Float,
    val widebandO2Afr: Float,
    val throttlePositionPercent: Int
)
