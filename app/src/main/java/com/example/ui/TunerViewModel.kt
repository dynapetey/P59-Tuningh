package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.TunerDatabase
import com.example.data.model.CalFile
import com.example.data.model.LogDataPoint
import com.example.data.model.LogSession
import com.example.data.repository.TunerRepository
import com.example.engine.CalibrationSegment
import com.example.engine.SparkGridCell
import com.example.engine.UniversalPatcherEngine
import com.example.hardware.ConnectionState
import com.example.hardware.ConnectionType
import com.example.hardware.FlashingProgress
import com.example.hardware.ObdxProManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

class TunerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = TunerDatabase.getDatabase(application)
    val repository = TunerRepository(database.tunerDao())

    val obdxManager = ObdxProManager(application)
    private val patcherEngine = UniversalPatcherEngine()

    // UI Selected Calibration File for Editing (Patcher Tab)
    private val _selectedCal = MutableStateFlow<CalFile?>(null)
    val selectedCal: StateFlow<CalFile?> = _selectedCal

    // Loaded Calibration Segments (Patcher Tab)
    private val _calSegments = MutableStateFlow<List<CalibrationSegment>>(emptyList())
    val calSegments: StateFlow<List<CalibrationSegment>> = _calSegments

    // Custom 3D Spark Ignition Table Grid cells
    private val _sparkTimingGrid = MutableStateFlow<List<SparkGridCell>>(emptyList())
    val sparkTimingGrid: StateFlow<List<SparkGridCell>> = _sparkTimingGrid

    // Logger session recording attributes
    private val _currentActiveSessionId = MutableStateFlow<Int?>(null)
    val currentActiveSessionId: StateFlow<Int?> = _currentActiveSessionId

    private var activeSessionPointsList = mutableListOf<LogDataPoint>()
    private var loggerDbSaveJob: Job? = null

    // Navigation UI States
    private val _activeTab = MutableStateFlow(0) // 0: Flasher, 1: Logger, 2: Patcher
    val activeTab: StateFlow<Int> = _activeTab

    // Preset / Loaded lists
    val calibrations: StateFlow<List<CalFile>> = repository.allCalibrations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logSessions: StateFlow<List<LogSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected session for viewing historical graphs
    private val _selectedHistorySession = MutableStateFlow<LogSession?>(null)
    val selectedHistorySession: StateFlow<LogSession?> = _selectedHistorySession

    private val _historySessionPoints = MutableStateFlow<List<LogDataPoint>>(emptyList())
    val historySessionPoints: StateFlow<List<LogDataPoint>> = _historySessionPoints

    // Manual command console inputs
    private val _commandLineInput = MutableStateFlow("")
    val commandLineInput: StateFlow<String> = _commandLineInput

    init {
        // Seed the SQLite database with high-quality presets if empty
        viewModelScope.launch {
            repository.allCalibrations.first().let { currentList ->
                if (currentList.isEmpty()) {
                    val presets = patcherEngine.getPresetCalibrations()
                    for (preset in presets) {
                        repository.insertCalibration(preset)
                    }
                }
            }

            // Seed a high quality past log session so that graphing works out of the box
            repository.allSessions.first().let { sessions ->
                if (sessions.isEmpty()) {
                    val dummySessionId = repository.insertSession(
                        LogSession(
                            sessionName = "Corvette Track Run - P59 VPW HighSpeed Logger",
                            durationSeconds = 48,
                            avgRpm = 3450,
                            maxRpm = 6120,
                            maxMph = 104,
                            notes = "Testing dynamic spark lookups. Wideband tracking 12.8 AFR."
                        )
                    ).toInt()

                    // Seed points
                    val points = mutableListOf<LogDataPoint>()
                    for (i in 0..120) {
                        val offset = i * 400L // 400ms interval
                        val rpm = when {
                            i < 20 -> 800 + i * 20
                            i < 60 -> 1200 + (i - 20) * 110
                            i < 90 -> 5600 - (i - 60) * 80
                            else -> 3200 + Random.nextInt(-100, 100)
                        }
                        val mph = (i * 0.9f).toInt()
                        val map = if (rpm > 3500) 90.5f + Random.nextFloat() * 5 else 41.2f + Random.nextFloat() * 2
                        val coolant = 180 + (i / 10)
                        val sparkTiming = if (rpm > 4000) 29.5f else 18.0f + Random.nextFloat() * 4
                        points.add(
                            LogDataPoint(
                                sessionId = dummySessionId,
                                timestampOffsetMs = offset,
                                rpm = rpm,
                                mph = mph,
                                mapKpa = map,
                                coolantTempF = coolant,
                                sparkAdvance = sparkTiming,
                                shortTermFuelTrimPercent = -1.5f + Random.nextFloat() * 3,
                                widebandO2Afr = if (rpm > 3500) 12.8f else 14.7f,
                                throttlePositionPercent = if (rpm > 4500) 100 else 15 + (rpm / 80)
                            )
                        )
                    }
                    // Done seeding, insert points
                    repository.insertPoints(points)
                }
            }

            // Select default first cal
            calibrations.collect { list ->
                if (list.isNotEmpty() && _selectedCal.value == null) {
                    selectCalFile(list.first())
                }
            }
        }

        // Handle incoming live telemetry during recording sessions
        viewModelScope.launch {
            obdxManager.liveDataStream.collect { point ->
                if (point != null && _currentActiveSessionId.value != null) {
                    val timestampedPoint = point.copy(sessionId = _currentActiveSessionId.value!!)
                    activeSessionPointsList.add(timestampedPoint)

                    // Write to DB periodically in blocks of 20 points
                    if (activeSessionPointsList.size >= 25) {
                        val flushList = activeSessionPointsList.toList()
                        activeSessionPointsList.clear()
                        viewModelScope.launch {
                            repository.insertPoints(flushList)
                        }
                    }
                }
            }
        }

        // Observe binary calibration reads from OBDX Pro device
        viewModelScope.launch {
            obdxManager.lastReadCalibrationBinary.collect { binary ->
                if (binary != null) {
                    val timestampStr = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
                    val filename = "PCM_Read_$timestampStr.bin"
                    
                    obdxManager.emitTerminalLog("Parsing 1MB binary calibration...")
                    val parsedCal = com.example.engine.P59BinaryParser.parseBinary(binary, filename)
                    obdxManager.emitTerminalLog("Parsed OS ID: ${parsedCal.operatingSystem}")
                    obdxManager.emitTerminalLog("Parsed Target Idle RPM: ${parsedCal.targetIdleRpm}")
                    obdxManager.emitTerminalLog("Parsed Max Spark Advance: ${parsedCal.sparkMaxAdvance}°")
                    obdxManager.emitTerminalLog("Parsed VATS Status: ${if (parsedCal.vatsEnabled) "Enabled" else "Disabled (Patched)"}")
                    obdxManager.emitTerminalLog("Parsed Flex Fuel: ${if (parsedCal.flexFuelEnabled) "Enabled" else "Disabled"}")
                    obdxManager.emitTerminalLog("Parsed MAP Sensor Type: ${parsedCal.mapSensorBarType}-Bar Upgrade")
                    
                    val insertedId = repository.insertCalibration(parsedCal).toInt()
                    val completeCalFile = parsedCal.copy(id = insertedId)
                    selectCalFile(completeCalFile)
                    
                    obdxManager.emitTerminalLog("Successfully imported parsed calibration file: $filename into the tuner workspace!")
                }
            }
        }
    }

    fun selectTab(index: Int) {
        _activeTab.value = index
    }

    fun setCommandLineInput(text: String) {
        _commandLineInput.value = text
    }

    fun sendConsoleCommand() {
        val cmd = _commandLineInput.value
        if (cmd.isNotBlank()) {
            obdxManager.sendCommand(cmd)
            _commandLineInput.value = ""
        }
    }

    fun selectCalFile(cal: CalFile) {
        _selectedCal.value = cal
        _calSegments.value = patcherEngine.loadSegmentsForCal(cal)
        _sparkTimingGrid.value = patcherEngine.generateSparkMap(cal.sparkMaxAdvance)
    }

    // Interactive modification of selected calibration params (Universal Patcher capabilities)
    fun updateVatsToggled(enabled: Boolean) {
        _selectedCal.value?.let { current ->
            val updated = current.copy(vatsEnabled = enabled, isChecksumValid = false, lastModified = System.currentTimeMillis())
            _selectedCal.value = updated
            // Instantly invalidate checksum to show the user the need for recalculating like a real editor!
            _calSegments.value = patcherEngine.loadSegmentsForCal(updated)
            saveCalToDb(updated)
        }
    }

    fun updateFlexFuelToggled(enabled: Boolean) {
        _selectedCal.value?.let { current ->
            val updated = current.copy(flexFuelEnabled = enabled, isChecksumValid = false, lastModified = System.currentTimeMillis())
            _selectedCal.value = updated
            _calSegments.value = patcherEngine.loadSegmentsForCal(updated)
            saveCalToDb(updated)
        }
    }

    fun updateLeanCruiseToggled(enabled: Boolean) {
        _selectedCal.value?.let { current ->
            val updated = current.copy(leanCruiseEnabled = enabled, isChecksumValid = false, lastModified = System.currentTimeMillis())
            _selectedCal.value = updated
            _calSegments.value = patcherEngine.loadSegmentsForCal(updated)
            saveCalToDb(updated)
        }
    }

    fun updateMapSensorType(type: Int) {
        _selectedCal.value?.let { current ->
            val updated = current.copy(mapSensorBarType = type, isChecksumValid = false, lastModified = System.currentTimeMillis())
            _selectedCal.value = updated
            _calSegments.value = patcherEngine.loadSegmentsForCal(updated)
            saveCalToDb(updated)
        }
    }

    fun updateSparkAdvanceLimit(value: Int) {
        _selectedCal.value?.let { current ->
            val updated = current.copy(sparkMaxAdvance = value, isChecksumValid = false, lastModified = System.currentTimeMillis())
            _selectedCal.value = updated
            _calSegments.value = patcherEngine.loadSegmentsForCal(updated)
            _sparkTimingGrid.value = patcherEngine.generateSparkMap(value)
            saveCalToDb(updated)
        }
    }

    fun updateTargetIdleRpm(value: Int) {
        _selectedCal.value?.let { current ->
            val updated = current.copy(targetIdleRpm = value, isChecksumValid = false, lastModified = System.currentTimeMillis())
            _selectedCal.value = updated
            _calSegments.value = patcherEngine.loadSegmentsForCal(updated)
            saveCalToDb(updated)
        }
    }

    // Spark Grid interactive modifications (Universal Patcher interactive tables)
    fun updateSparkGridCell(cellIndex: Int, newDegrees: Double) {
        val currentList = _sparkTimingGrid.value.toMutableList()
        if (cellIndex in currentList.indices) {
            currentList[cellIndex] = currentList[cellIndex].copy(advanceDegrees = newDegrees)
            _sparkTimingGrid.value = currentList
            
            // Uncheck the current calibration file checksum validation to highlight the change!
            _selectedCal.value?.let { current ->
                val updated = current.copy(isChecksumValid = false, lastModified = System.currentTimeMillis())
                _selectedCal.value = updated
                _calSegments.value = patcherEngine.loadSegmentsForCal(updated)
                saveCalToDb(updated)
            }
        }
    }

    // Automated checksum fixing / validation engine
    fun applyAutomatedChecksumCorrection() {
        _selectedCal.value?.let { current ->
            viewModelScope.launch {
                obdxManager.emitTerminalLog("Running checksum verification engine on OS ${current.operatingSystem}...")
                val fixed = patcherEngine.recalculateChecksums(current)
                _selectedCal.value = fixed
                _calSegments.value = patcherEngine.loadSegmentsForCal(fixed)
                repository.insertCalibration(fixed)
                obdxManager.emitTerminalLog("Automated Checksum Correction complete. All 8 segment checksum boundary headers validated.")
            }
        }
    }

    private fun saveCalToDb(cal: CalFile) {
        viewModelScope.launch {
            repository.insertCalibration(cal)
        }
    }

    // Flasher triggers (PCM Hammer capabilities)
    fun triggerEcmFlash(operation: String, useHighSpeed: Boolean) {
        viewModelScope.launch {
            obdxManager.executePlatformFlash(operation, useHighSpeed)
        }
    }

    // Data logger recording state triggers (PCM Logger capabilities)
    fun startLoggingSession() {
        if (obdxManager.connectionState.value == ConnectionState.DISCONNECTED) {
            viewModelScope.launch {
                obdxManager.emitTerminalLog("Error: Scanner disconnected! Connect first before starting telemetry logs.")
            }
            return
        }

        viewModelScope.launch {
            val sessionName = "Log Session VPW - " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            val newSession = LogSession(
                sessionName = sessionName,
                timestamp = System.currentTimeMillis(),
                durationSeconds = 0,
                avgRpm = 0,
                maxRpm = 0,
                maxMph = 0,
                notes = "OBDX Pro GT VPW scan."
            )
            val insertedId = repository.insertSession(newSession).toInt()
            _currentActiveSessionId.value = insertedId
            activeSessionPointsList.clear()

            // Run scanner in loop mode
            obdxManager.startLogging(insertedId)
        }
    }

    fun stopLoggingSession() {
        val finishedSessionId = _currentActiveSessionId.value
        _currentActiveSessionId.value = null
        obdxManager.stopLogging()

        if (finishedSessionId != null) {
            viewModelScope.launch {
                // Save residual frames
                if (activeSessionPointsList.isNotEmpty()) {
                    repository.insertPoints(activeSessionPointsList.toList())
                }

                // Query and evaluate stats
                repository.getPointsForSession(finishedSessionId).first().let { pointsList ->
                    if (pointsList.isNotEmpty()) {
                        val duration = (pointsList.last().timestampOffsetMs / 1000).toInt()
                        val rpms = pointsList.map { it.rpm }
                        val avgRpm = rpms.average().toInt()
                        val maxRpm = rpms.maxOrNull() ?: 0
                        val maxMph = pointsList.map { it.mph }.maxOrNull() ?: 0

                        repository.updateSessionStats(
                            id = finishedSessionId,
                            dur = if (duration > 0) duration else 12,
                            avg = avgRpm,
                            max = maxRpm,
                            maxMph = maxMph
                        )
                    }
                }
            }
        }
    }

    fun deleteSession(id: Int) {
        viewModelScope.launch {
            repository.deleteSession(id)
            if (_selectedHistorySession.value?.id == id) {
                _selectedHistorySession.value = null
                _historySessionPoints.value = emptyList()
            }
        }
    }

    fun selectHistorySession(session: LogSession) {
        _selectedHistorySession.value = session
        viewModelScope.launch {
            repository.getPointsForSession(session.id).collect { points ->
                _historySessionPoints.value = points
            }
        }
    }

    fun deleteCalibration(cal: CalFile) {
        viewModelScope.launch {
            repository.deleteCalibration(cal.id)
            if (_selectedCal.value?.id == cal.id) {
                _selectedCal.value = null
                _calSegments.value = emptyList()
            }
        }
    }

    fun addNewCalibrationFile(name: String) {
        viewModelScope.launch {
            val newCal = CalFile(
                name = name,
                operatingSystem = "12587603",
                vatsEnabled = true,
                flexFuelEnabled = false,
                mapSensorBarType = 1,
                isChecksumValid = true,
                rawHexTrunc = "00AAFF1C55B7701103D"
            )
            val newId = repository.insertCalibration(newCal).toInt()
            selectCalFile(newCal.copy(id = newId))
        }
    }
}
