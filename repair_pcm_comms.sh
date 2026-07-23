#!/usr/bin/env bash
set -Eeuo pipefail

# Repair the P59-Tuningh Android communication layer.
#
# What this changes:
#   - Uses one serialized ELM-compatible request/response path for live data.
#   - Reads until the ELM prompt instead of consuming only the first available bytes.
#   - Prevents overlapping PID jobs and stale-response misalignment.
#   - Removes fake default telemetry values.
#   - Uses the user's A/C-pressure wideband formula: AFR = volts / 0.5 + 9.37.
#   - Stores converted wideband AFR in widebandO2Afr.
#   - Stops sending raw binary tester-present frames while the adapter is in ELM mode.
#   - DISABLES the unfinished flash/read routine because it does not contain a real
#     P59 kernel and cannot safely read or write a PCM.
#
# Run from the repository root:
#   chmod +x repair_pcm_comms.sh
#   ./repair_pcm_comms.sh
#
# Skip the Gradle build:
#   SKIP_BUILD=1 ./repair_pcm_comms.sh

ROOT="${1:-$PWD}"
cd "$ROOT"

TARGET="app/src/main/java/com/example/hardware/ObdxProManager.kt"

if [[ ! -f "$TARGET" ]]; then
    echo "ERROR: $TARGET was not found."
    echo "Run this script from the P59-Tuningh repository root."
    exit 1
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP="${TARGET}.backup-${STAMP}"
cp "$TARGET" "$BACKUP"
echo "Backup created: $BACKUP"

python3 <<'PY'
from pathlib import Path
import sys

path = Path("app/src/main/java/com/example/hardware/ObdxProManager.kt")
text = path.read_text(encoding="utf-8")


def replace_function(source: str, marker: str, replacement: str) -> str:
    start = source.find(marker)
    if start < 0:
        raise RuntimeError(f"Could not find function marker: {marker}")

    brace = source.find("{", start)
    if brace < 0:
        raise RuntimeError(f"Could not find opening brace for: {marker}")

    depth = 0
    end = None

    for index in range(brace, len(source)):
        char = source[index]

        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                end = index + 1
                break

    if end is None:
        raise RuntimeError(f"Could not find closing brace for: {marker}")

    return source[:start] + replacement.rstrip() + source[end:]


new_monitor = r'''
    private fun startConnectionMonitor() {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = scope.launch(Dispatchers.IO) {
            emitTerminalLog("Connection monitor started (ELM-safe voltage polling).")

            while (isActive && activeInputStream != null) {
                if (_connectionState.value == ConnectionState.CONNECTED_READY) {
                    socketMutex.withLock {
                        try {
                            val inStream = activeInputStream ?: return@withLock
                            drainAvailableInput(inStream)

                            if (writeRaw("ATRV\r".toByteArray()) > 0) {
                                val response = readElmResponse(800)
                                val match = Regex("(\\d+(?:\\.\\d+)?)\\s*V")
                                    .find(response.uppercase())

                                val volts = match?.groupValues?.getOrNull(1)?.toFloatOrNull()
                                if (volts != null && volts in 5.0f..20.0f) {
                                    _voltage.value = volts
                                }
                            }
                        } catch (_: Exception) {
                            // The next normal transaction will report a hard connection failure.
                        }
                    }
                }

                delay(3000)
            }

            emitTerminalLog("Connection monitor stopped.")
        }
    }
'''

new_query = r'''
    private suspend fun queryRawClass2PayloadDirect(payload: ByteArray): ByteArray? {
        val inStream = activeInputStream ?: return null
        activeOutputStream ?: return null

        if (payload.isEmpty()) return null

        return socketMutex.withLock {
            try {
                drainAvailableInput(inStream)

                // OBDX Pro GT supports the legacy ELM327 command set over serial,
                // Bluetooth and Wi-Fi. Keep diagnostic logging in that mode.
                val command = payload.joinToString("") {
                    String.format("%02X", it.toInt() and 0xFF)
                } + "\r"

                if (writeRaw(command.toByteArray()) <= 0) {
                    return@withLock null
                }

                val response = readElmResponse(1400)
                parseElmResponsePayload(response, payload)
            } catch (e: Exception) {
                emitTerminalLog(
                    "[PID ERROR] ${payload.joinToString(" ") { String.format("%02X", it.toInt() and 0xFF) }}: " +
                        (e.localizedMessage ?: e.javaClass.simpleName)
                )
                null
            }
        }
    }

    private fun drainAvailableInput(inStream: InputStream) {
        val drainBuffer = ByteArray(512)

        while (inStream.available() > 0) {
            val count = inStream.read(
                drainBuffer,
                0,
                inStream.available().coerceAtMost(drainBuffer.size)
            )

            if (count <= 0) break
        }
    }

    private suspend fun readElmResponse(timeoutMs: Int): String {
        val output = StringBuilder()
        val buffer = ByteArray(512)
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis())
                .coerceAtLeast(1L)
                .coerceAtMost(150L)
                .toInt()

            val count = readRaw(buffer, remaining)

            if (count > 0) {
                val chunk = String(buffer, 0, count)
                output.append(chunk)

                if (output.indexOf(">") >= 0) {
                    break
                }
            } else {
                delay(5)
            }
        }

        return output.toString()
    }

    private fun parseElmResponsePayload(
        response: String,
        request: ByteArray
    ): ByteArray? {
        if (response.isBlank() || request.isEmpty()) return null

        val upper = response.uppercase()

        if (
            upper.contains("NO DATA") ||
            upper.contains("UNABLE TO CONNECT") ||
            upper.contains("BUS ERROR") ||
            upper.contains("STOPPED") ||
            upper.contains("?")
        ) {
            return null
        }

        val requestHex = request.joinToString("") {
            String.format("%02X", it.toInt() and 0xFF)
        }

        val expectedMode = ((request[0].toInt() and 0xFF) + 0x40) and 0xFF
        val signature = IntArray(request.size)
        signature[0] = expectedMode

        for (index in 1 until request.size) {
            signature[index] = request[index].toInt() and 0xFF
        }

        val lines = upper
            .replace('\r', '\n')
            .replace("SEARCHING...", "\n")
            .replace(">", "\n")
            .split('\n')

        for (rawLine in lines) {
            val line = rawLine.trim()

            if (line.isEmpty()) continue

            val hex = line.filter { character ->
                character in '0'..'9' || character in 'A'..'F'
            }

            if (hex.isEmpty() || hex == requestHex || hex.length < signature.size * 2) {
                continue
            }

            val evenHex = if (hex.length % 2 == 0) hex else hex.dropLast(1)
            if (evenHex.length < signature.size * 2) continue

            val frame = IntArray(evenHex.length / 2)

            try {
                for (index in frame.indices) {
                    frame[index] = evenHex
                        .substring(index * 2, index * 2 + 2)
                        .toInt(16)
                }
            } catch (_: Exception) {
                continue
            }

            val signatureIndex = indexOfSequence(frame, signature)
            if (signatureIndex < 0) continue

            val dataStart = signatureIndex + signature.size
            if (dataStart >= frame.size) continue

            val expectedSize = expectedPayloadDataSize(request)
            val available = frame.size - dataStart
            val dataSize = if (expectedSize > 0) {
                expectedSize.coerceAtMost(available)
            } else {
                available
            }

            if (dataSize <= 0) continue

            return ByteArray(dataSize) { offset ->
                frame[dataStart + offset].toByte()
            }
        }

        return null
    }

    private fun indexOfSequence(haystack: IntArray, needle: IntArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1

        for (start in 0..haystack.size - needle.size) {
            var matches = true

            for (offset in needle.indices) {
                if (haystack[start + offset] != needle[offset]) {
                    matches = false
                    break
                }
            }

            if (matches) return start
        }

        return -1
    }

    private fun expectedPayloadDataSize(request: ByteArray): Int {
        if (request.isEmpty()) return -1

        val mode = request[0].toInt() and 0xFF
        val pid1 = request.getOrNull(1)?.toInt()?.and(0xFF)
        val pid2 = request.getOrNull(2)?.toInt()?.and(0xFF)

        return when {
            mode == 0x01 && pid1 == 0x00 -> 4
            mode == 0x01 && pid1 == 0x0C -> 2
            mode == 0x01 && pid1 == 0x0D -> 1
            mode == 0x01 && pid1 == 0x0B -> 1
            mode == 0x01 && pid1 == 0x05 -> 1
            mode == 0x01 && pid1 == 0x11 -> 1
            mode == 0x01 && pid1 == 0x10 -> 2
            mode == 0x01 && pid1 == 0x0E -> 1
            mode == 0x01 && pid1 == 0x06 -> 1
            mode == 0x01 && pid1 == 0x07 -> 1
            mode == 0x01 && pid1 == 0x44 -> 2
            mode == 0x01 && pid1 == 0x0F -> 1

            mode == 0x22 && pid1 == 0x11 && pid2 == 0x51 -> 1
            mode == 0x22 && pid1 == 0x11 && pid2 == 0xA6 -> 1
            mode == 0x22 && pid1 == 0x11 && pid2 == 0xA7 -> 2

            else -> -1
        }
    }
'''

new_flash = r'''
    fun executePlatformFlash(
        operation: String,
        useHighSpeed: Boolean,
        binaryData: ByteArray? = null
    ) {
        scope.launch {
            _lastReadCalibrationBinary.value = null
            _flashProgress.value = FlashingProgress(
                operation = operation,
                isComplete = false,
                progress = 0f,
                speedKbps = if (useHighSpeed) 41.6f else 10.4f,
                isError = true,
                logMessage = "Safety block: real P59 flash kernel is not implemented."
            )

            emitTerminalLog("==============================================")
            emitTerminalLog("[SAFETY BLOCK] $operation was not started.")
            emitTerminalLog(
                "The previous flasher uploaded placeholder 0x90 bytes instead of a real P59 kernel " +
                    "and could not produce a valid 1 MB PCM image."
            )
            emitTerminalLog(
                "PCM read/write remains disabled until a verified PCM Hammer-compatible P59 kernel, " +
                    "OBDX DVI transport, block framing, retries, CRC checks and recovery handling are implemented."
            )
            emitTerminalLog("Do not use this application to write the PCM in its current state.")
            emitTerminalLog("==============================================")
        }
    }
'''

new_logging = r'''
    fun startLogging(sessionId: Int) {
        if (
            _connectionState.value == ConnectionState.DISCONNECTED ||
            activeInputStream == null ||
            activeOutputStream == null
        ) {
            scope.launch {
                emitTerminalLog(
                    "Error: Physical device not connected. Cannot start live telemetry logging."
                )
            }
            return
        }

        loggingJob?.cancel()
        loggingJob = scope.launch(Dispatchers.IO) {
            _connectionState.value = ConnectionState.LOGGING
            emitTerminalLog(
                "Starting serialized ELM/J1850 live-data logger on Session #$sessionId..."
            )

            // Zero values are intentional. The old implementation displayed believable
            // fake values before the PCM had returned any data.
            val telemetryCache = java.util.concurrent.ConcurrentHashMap<String, Any>().apply {
                put("RPM", 0)
                put("MPH", 0)
                put("MAP", 0f)
                put("ECT", 0)
                put("TPS", 0)
                put("MAF", 0f)
                put("SPARK", 0f)
                put("STFT", 0f)
                put("LTFT", 0f)
                put("EQ_RATIO", 1f)
                put("IAT", 0)
                put("KNOCK", 0f)
                put("KNK_CNT", 0)
                put("AC_VOLTS", 0f)
            }

            val pollingTasks = listOf(
                PidPollTask("RPM", byteArrayOf(0x01, 0x0C), 150L),
                PidPollTask("TPS", byteArrayOf(0x01, 0x11), 150L),
                PidPollTask("MAP", byteArrayOf(0x01, 0x0B), 200L),
                PidPollTask("MPH", byteArrayOf(0x01, 0x0D), 250L),

                PidPollTask("SPARK", byteArrayOf(0x01, 0x0E), 300L),
                PidPollTask("STFT", byteArrayOf(0x01, 0x06), 350L),
                PidPollTask("LTFT", byteArrayOf(0x01, 0x07), 350L),

                PidPollTask("ECT", byteArrayOf(0x01, 0x05), 600L),
                PidPollTask("IAT", byteArrayOf(0x01, 0x0F), 600L),
                PidPollTask("MAF", byteArrayOf(0x01, 0x10), 500L),
                PidPollTask("EQ_RATIO", byteArrayOf(0x01, 0x44), 500L),

                PidPollTask(
                    "AC_VOLTS",
                    byteArrayOf(0x22, 0x11, 0x51),
                    500L
                ),

                PidPollTask(
                    "KNOCK",
                    byteArrayOf(0x22, 0x11, 0xA6.toByte()),
                    800L
                ),

                PidPollTask(
                    "KNK_CNT",
                    byteArrayOf(0x22, 0x11, 0xA7.toByte()),
                    800L
                )
            )

            val sessionStart = System.currentTimeMillis()
            var lastEmit = 0L
            var receivedAnyPcmData = false
            var consecutiveFailures = 0

            try {
                while (isActive) {
                    val loopNow = System.currentTimeMillis()
                    var performedPoll = false

                    // Poll one request at a time. Parallel request jobs were allowing
                    // delayed responses to be associated with the next PID.
                    for (task in pollingTasks) {
                        if (loopNow - task.lastPolledMs < task.intervalMs) continue

                        task.lastPolledMs = System.currentTimeMillis()
                        performedPoll = true

                        val response = queryRawClass2Payload(task.payload)

                        if (response != null) {
                            parseAndCachePid(task.pidId, response, telemetryCache)
                            receivedAnyPcmData = true
                            consecutiveFailures = 0
                        } else {
                            consecutiveFailures++
                        }

                        delay(10)
                    }

                    val now = System.currentTimeMillis()

                    if (receivedAnyPcmData && now - lastEmit >= 200L) {
                        val rpm = telemetryCache["RPM"] as? Int ?: 0
                        val mph = telemetryCache["MPH"] as? Int ?: 0
                        val map = telemetryCache["MAP"] as? Float ?: 0f
                        val ect = telemetryCache["ECT"] as? Int ?: 0
                        val tps = telemetryCache["TPS"] as? Int ?: 0
                        val maf = telemetryCache["MAF"] as? Float ?: 0f
                        val spark = telemetryCache["SPARK"] as? Float ?: 0f
                        val stft = telemetryCache["STFT"] as? Float ?: 0f
                        val ltft = telemetryCache["LTFT"] as? Float ?: 0f
                        val commandedEq =
                            telemetryCache["EQ_RATIO"] as? Float ?: 1f
                        val iat = telemetryCache["IAT"] as? Int ?: 0
                        val knock = telemetryCache["KNOCK"] as? Float ?: 0f
                        val knockCount = telemetryCache["KNK_CNT"] as? Int ?: 0
                        val acVolts = telemetryCache["AC_VOLTS"] as? Float ?: 0f

                        // User-specified AEM conversion through the A/C-pressure input:
                        // AFR = (voltage / 0.5) + 9.37
                        val convertedAfr = if (acVolts > 0f) {
                            (acVolts / 0.5f) + 9.37f
                        } else {
                            0f
                        }

                        val convertedLambda = if (convertedAfr > 0f) {
                            convertedAfr / 14.7f
                        } else {
                            0f
                        }

                        val convertedEq = if (convertedLambda > 0f) {
                            1f / convertedLambda
                        } else {
                            0f
                        }

                        _liveDataStream.value = LogDataPoint(
                            sessionId = sessionId,
                            timestampOffsetMs = now - sessionStart,
                            rpm = rpm,
                            mph = mph,
                            mapKpa = map,
                            coolantTempF = ect,
                            sparkAdvance = spark,
                            shortTermFuelTrimPercent = stft,

                            // The old code put commanded AFR here. This field now carries
                            // the measured/converted wideband value.
                            widebandO2Afr = convertedAfr,

                            throttlePositionPercent = tps,
                            massAirFlowGps = maf,
                            manifoldAirTempF = iat,

                            // No real P59 request is currently implemented for these fields.
                            desiredIdleRpm = 0,
                            iacPositionSteps = 0,
                            dwellTimeMs = 0f,

                            knockRetardDegrees = knock,
                            knockCount = knockCount,
                            longTermFuelTrimPercent = ltft,
                            commandedEquivalenceRatio = commandedEq,

                            acPressureVoltage = acVolts,
                            convertedWidebandAfr = convertedAfr,
                            convertedLambda = convertedLambda,
                            convertedEq = convertedEq
                        )

                        lastEmit = now
                    }

                    if (consecutiveFailures >= 40) {
                        emitTerminalLog(
                            "Live-data logger stopped after repeated PCM response timeouts."
                        )
                        break
                    }

                    if (!performedPoll) {
                        delay(15)
                    }
                }
            } finally {
                if (_connectionState.value == ConnectionState.LOGGING) {
                    _connectionState.value = ConnectionState.CONNECTED_READY
                }
            }
        }
    }
'''

try:
    text = replace_function(
        text,
        "private fun startConnectionMonitor()",
        new_monitor,
    )

    text = replace_function(
        text,
        "private suspend fun queryRawClass2PayloadDirect(payload: ByteArray)",
        new_query,
    )

    text = replace_function(
        text,
        "fun executePlatformFlash(operation: String, useHighSpeed: Boolean, binaryData: ByteArray? = null)",
        new_flash,
    )

    text = replace_function(
        text,
        "fun startLogging(sessionId: Int)",
        new_logging,
    )
except RuntimeError as error:
    sys.exit(f"PATCH ERROR: {error}")

text = text.replace(
    'emitTerminalLog("[BLUETOOTH TX] AT I7") // GM VPW Protocol on OBDX Pro',
    'emitTerminalLog("[ADAPTER TX] ATSP2 // SAE J1850 VPW")',
)

text = text.replace(
    'writeRaw("AT I7\\r\\n".toByteArray())',
    'writeRaw("ATSP2\\r".toByteArray())',
)

text = text.replace(
    'emitTerminalLog("[BLUETOOTH TX] OBDX_SPEED_1X")',
    'emitTerminalLog("[ADAPTER TX] ATAT1 // Adaptive timing")',
)

text = text.replace(
    'writeRaw("OBDX_SPEED_1X\\r\\n".toByteArray())',
    'writeRaw("ATAT1\\r".toByteArray())',
)

text = text.replace(
    '        _voltage.value = 13.8f\n',
    '',
)

text = text.replace(
    '        _voltage.value = 14.1f\n',
    '',
)

path.write_text(text, encoding="utf-8")
print(f"Patched {path}")
PY

if [[ "${SKIP_BUILD:-0}" == "1" ]]; then
    echo "Patch complete. Gradle build skipped."
    exit 0
fi

chmod +x gradlew

echo
echo "Compiling debug APK..."
./gradlew --no-daemon clean assembleDebug

APK="app/build/outputs/apk/debug/app-debug.apk"

echo
echo "SUCCESS"
echo "APK: $APK"
echo
echo "Important:"
echo "  - Live data is enabled."
echo "  - PCM full read/write is intentionally safety-blocked."
echo "  - Do not use the current app for PCM writing."
