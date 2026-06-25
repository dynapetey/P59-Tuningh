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
    val injectorFlowRateLbHr: Double = 24.8, // injector flow rate scaling (lb/hr)
    val revLimitRpm: Int = 5900, // fuel/spark cut engine speed (RPM)
    val fan1OnTempF: Int = 205, // cooling fan 1 trigger temperature (deg F)
    val fan2OnTempF: Int = 215, // cooling fan 2 trigger temperature (deg F)
    val veMultiplierPercent: Int = 100, // Volumetric Efficiency (VE) table multiplier (%)
    val isChecksumValid: Boolean = true,
    val rawHexTrunc: String = "00FF1C2E03A0FF5B6C10", // truncated raw hex representing binary updates
    val lastModified: Long = System.currentTimeMillis()
)
