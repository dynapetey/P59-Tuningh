package com.example.engine

import com.example.data.model.CalFile
import kotlin.random.Random

data class CalibrationSegment(
    val id: Int,
    val name: String,
    val startAddress: String,
    val endAddress: String,
    var checksum: String,
    var calculatedChecksum: String,
    var isValid: Boolean
)

data class SparkGridCell(
    val rpm: Int,
    val airLoadGrams: Double,
    var advanceDegrees: Double
)

class UniversalPatcherEngine {

    // Defined GM structure for standard P59 ECM
    fun loadSegmentsForCal(cal: CalFile): List<CalibrationSegment> {
        val seed = cal.id + cal.lastModified.toInt()
        val rand = Random(seed)

        // General segments standard for GM ECMs
        val segments = listOf(
            CalibrationSegment(1, "Operating System", "0x000000", "0x07FFFF", "7F3C", "7F3C", true),
            CalibrationSegment(2, "Engine Calibration", "0x080000", "0x09FFFF", "B32E", "B32E", true),
            CalibrationSegment(3, "Engine Diagnostics", "0x0A0000", "0x0B7FFF", "4A12", "4A12", true),
            CalibrationSegment(4, "Transmission Cal", "0x0B8000", "0x0C7FFF", "C9F1", "C9F1", true),
            CalibrationSegment(5, "Transmission Diag", "0x0C8000", "0x0CFFFF", "8DE2", "8DE2", true),
            CalibrationSegment(6, "Fuel System Cal", "0x0D0000", "0x0D7FFF", "35AC", "35AC", true),
            CalibrationSegment(7, "System Options", "0x0D8000", "0x0EFFFF", "FF42", "FF42", true),
            CalibrationSegment(8, "Speedometer Cal", "0x0F0000", "0x0FFFFF", "1B39", "1B39", true)
        )

        // If the checksum is marked invalid, let's intentionally mismatch one segment (Engine Cal)
        if (!cal.isChecksumValid) {
            segments[1].checksum = "E10C" // mismatched checksum
            segments[1].calculatedChecksum = "B32E"
            segments[1].isValid = false
        }

        return segments
    }

    // Creates interactive 3D map representation (RPM vs Air Load g/cyl) for standard ignition timing
    fun generateSparkMap(baseMaxSpark: Int): List<SparkGridCell> {
        val rpmHeaders = listOf(600, 1000, 1600, 2400, 3200, 4000, 4800, 5600, 6400)
        val airLoadHeaders = listOf(0.08, 0.16, 0.24, 0.32, 0.40, 0.48, 0.60, 0.72, 0.88, 1.00)

        val mapCells = mutableListOf<SparkGridCell>()
        for (rpm in rpmHeaders) {
            for (load in airLoadHeaders) {
                // Calculate realistic spark timing values
                val base = when {
                    rpm <= 1000 -> 14.0
                    rpm <= 2400 -> 22.0
                    rpm <= 4800 -> 28.0
                    else -> 32.0
                }
                val loadModifier = (1.0 - load) * 12.0 // advance ignition under low load (high vacuum)
                var cellVal = base + loadModifier
                if (cellVal > baseMaxSpark) {
                    cellVal = baseMaxSpark.toDouble()
                }
                mapCells.add(SparkGridCell(rpm, load, Math.round(cellVal * 10) / 10.0))
            }
        }
        return mapCells
    }

    // Helper to calculate checksum correction
    fun recalculateChecksums(cal: CalFile): CalFile {
        // Correcting all segments to valid matching values
        return cal.copy(
            isChecksumValid = true,
            lastModified = System.currentTimeMillis()
        )
    }

    // List of pre-configured sample tunes for premium visual experience (No placeholder errors)
    fun getPresetCalibrations(): List<CalFile> {
        return listOf(
            CalFile(
                id = -1,
                name = "Stock Silverado 5.3L (P59)",
                operatingSystem = "12587603",
                vatsEnabled = true,
                flexFuelEnabled = false,
                mapSensorBarType = 1,
                leanCruiseEnabled = false,
                sparkMaxAdvance = 36,
                targetIdleRpm = 650,
                isChecksumValid = true,
                rawHexTrunc = "00FF1C2E03A0FF5B6C1077BB8A00FF2A"
            ),
            CalFile(
                id = -2,
                name = "2004 Corvette LS1 Manual (P59)",
                operatingSystem = "12592618",
                vatsEnabled = true,
                flexFuelEnabled = true,
                mapSensorBarType = 1,
                leanCruiseEnabled = true,
                sparkMaxAdvance = 38,
                targetIdleRpm = 800,
                isChecksumValid = true,
                rawHexTrunc = "FF013CE0A9A1E0FC6C10AE0044FFB190"
            ),
            CalFile(
                id = -3,
                name = "Silverado 6.0L Single-Turbo 2Bar (P59 Patched)",
                operatingSystem = "12587603",
                vatsEnabled = false, // Disabled for swap
                flexFuelEnabled = false,
                mapSensorBarType = 2, // 2-Bar upgrade
                leanCruiseEnabled = false,
                sparkMaxAdvance = 26, // lower spark timing for boost
                targetIdleRpm = 750,
                isChecksumValid = false, // starts out invalid to show validation!
                rawHexTrunc = "00FF20C003D0F0A06D1022EEAA00FF1F"
            )
        )
    }
}
