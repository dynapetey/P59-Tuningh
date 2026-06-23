package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calibration_files")
data class CalFile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val operatingSystem: String = "12587603",
    val vatsEnabled: Boolean = true,
    val flexFuelEnabled: Boolean = false,
    val mapSensorBarType: Int = 1, // 1, 2, or 3 Bar
    val leanCruiseEnabled: Boolean = false,
    val sparkMaxAdvance: Int = 36, // spark max limits
    val targetIdleRpm: Int = 650, // idle RPM
    val isChecksumValid: Boolean = true,
    val rawHexTrunc: String = "00FF1C2E03A0FF5B6C10", // truncated raw hex representing binary updates
    val lastModified: Long = System.currentTimeMillis()
)
