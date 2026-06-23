package com.example

import com.example.engine.P59BinaryParser
import org.junit.Assert.*
import org.junit.Test

class P59BinaryParserTest {

    @Test
    fun testP59BinaryGenerationAndParsing() {
        val targetOs = "12592618"
        val targetVats = false
        val targetFlexFuel = true
        val targetMap = 2
        val targetIdle = 750
        val targetSpark = 32
        val targetLeanCruise = true

        // Generate binary using standard structural offsets
        val binary = P59BinaryParser.createStandardP59Binary(
            osId = targetOs,
            vatsEnabled = targetVats,
            flexFuelEnabled = targetFlexFuel,
            mapSensorBarType = targetMap,
            targetIdleRpm = targetIdle,
            sparkMaxAdvance = targetSpark,
            leanCruiseEnabled = targetLeanCruise
        )

        // Ensure 1MB size
        assertEquals(1048576, binary.size)

        // Parse generated binary
        val parsed = P59BinaryParser.parseBinary(binary, "Test_Read_Calibration.bin")

        // Verify all fields are parsed correctly according to physical offsets
        assertEquals("Test_Read_Calibration.bin", parsed.name)
        assertEquals(targetOs, parsed.operatingSystem)
        assertEquals(targetVats, parsed.vatsEnabled)
        assertEquals(targetFlexFuel, parsed.flexFuelEnabled)
        assertEquals(targetMap, parsed.mapSensorBarType)
        assertEquals(targetIdle, parsed.targetIdleRpm)
        assertEquals(targetSpark, parsed.sparkMaxAdvance)
        assertEquals(targetLeanCruise, parsed.leanCruiseEnabled)
        assertTrue(parsed.isChecksumValid)
    }

    @Test
    fun testDefaultFallbackParsing() {
        // Feed an empty or corrupted binary block to verify safe defaults
        val emptyBinary = ByteArray(1024)
        val parsed = P59BinaryParser.parseBinary(emptyBinary, "Corrupt_P59.bin")

        // Check fallback safety properties
        assertEquals("12587603", parsed.operatingSystem)
        assertTrue(parsed.vatsEnabled)
        assertFalse(parsed.flexFuelEnabled)
        assertEquals(1, parsed.mapSensorBarType)
        assertEquals(650, parsed.targetIdleRpm)
        assertEquals(36, parsed.sparkMaxAdvance)
        assertFalse(parsed.leanCruiseEnabled)
    }
}
