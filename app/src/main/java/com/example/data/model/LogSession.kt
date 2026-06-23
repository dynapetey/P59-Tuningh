package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "log_sessions")
data class LogSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val avgRpm: Int = 0,
    val maxRpm: Int = 0,
    val maxMph: Int = 0,
    val notes: String = ""
)
