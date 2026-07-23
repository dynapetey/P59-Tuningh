package com.p59.windows

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToInt

data class TelemetrySnapshot(
    val timestampMs: Long = System.currentTimeMillis(),
    val rpm: Int? = null,
    val mph: Int? = null,
    val mapKpa: Float? = null,
    val coolantF: Int? = null,
    val throttlePercent: Int? = null,
    val mafGps: Float? = null,
    val sparkDegrees: Float? = null,
    val stftPercent: Float? = null,
    val ltftPercent: Float? = null,
    val commandedEq: Float? = null,
    val intakeAirF: Int? = null,
    val acPressureVolts: Float? = null,
    val widebandAfr: Float? = null,
    val adapterVoltage: Float? = null
)

class ElmLiveDataClient(
    private val input: InputStream,
    private val output: OutputStream,
    private val ioLock: Any,
    private val log: (String) -> Unit
) {
    private var cache = TelemetrySnapshot()
    private var pollCounter = 0

    fun initialize() {
        synchronized(ioLock) {
            drainInput()

            val reset = sendCommandLocked("ATZ", 2800)
            log("[ELM] Reset: ${clean(reset)}")

            val identity = sendCommandLocked("AT@1", 1800)
            if (!identity.contains("OBDX", ignoreCase = true)) {
                throw IOException(
                    "The selected COM port did not identify as an OBDX Pro: ${clean(identity)}"
                )
            }
            log("[ELM] Adapter: ${clean(identity)}")

            listOf(
                "ATE0",
                "ATL0",
                "ATS0",
                "ATH1",
                "ATAL",
                "ATSP2",
                "ATAT1",
                // Direct physical header: PCM 0x10, scan tool 0xF0.
                "ATSH6C10F0"
            ).forEach { command ->
                val response = sendCommandLocked(command, 1600)
                if (
                    !response.contains("OK", ignoreCase = true) &&
                    !response.contains(command, ignoreCase = true)
                ) {
                    throw IOException(
                        "Adapter rejected $command: ${clean(response)}"
                    )
                }
            }

            cache = cache.copy(adapterVoltage = readVoltageLocked())
            log("[ELM] SAE J1850 VPW diagnostic mode ready.")
        }
    }

    fun readVoltage(): Float? =
        synchronized(ioLock) { readVoltageLocked() }

    fun pollSnapshot(): TelemetrySnapshot {
        synchronized(ioLock) {
            pollCounter++

            val rpm = queryDataLocked("010C", intArrayOf(0x41, 0x0C), 2)
                ?.let { (((it[0].u8() * 256) + it[1].u8()) / 4) }

            val mph = queryDataLocked("010D", intArrayOf(0x41, 0x0D), 1)
                ?.let { (it[0].u8() * 0.621371f).roundToInt() }

            val map = queryDataLocked("010B", intArrayOf(0x41, 0x0B), 1)
                ?.let { it[0].u8().toFloat() }

            val throttle = queryDataLocked("0111", intArrayOf(0x41, 0x11), 1)
                ?.let { (it[0].u8() * 100f / 255f).roundToInt() }

            if (rpm != null) cache = cache.copy(rpm = rpm)
            if (mph != null) cache = cache.copy(mph = mph)
            if (map != null) cache = cache.copy(mapKpa = map)
            if (throttle != null) cache = cache.copy(throttlePercent = throttle)

            if (pollCounter % 2 == 0) {
                val maf = queryDataLocked("0110", intArrayOf(0x41, 0x10), 2)
                    ?.let { ((it[0].u8() * 256) + it[1].u8()) / 100f }

                val spark = queryDataLocked("010E", intArrayOf(0x41, 0x0E), 1)
                    ?.let { (it[0].u8() / 2f) - 64f }

                if (maf != null) cache = cache.copy(mafGps = maf)
                if (spark != null) cache = cache.copy(sparkDegrees = spark)
            }

            if (pollCounter % 4 == 0) {
                val ect = queryDataLocked("0105", intArrayOf(0x41, 0x05), 1)
                    ?.let { celsiusToFahrenheit(it[0].u8() - 40) }

                val iat = queryDataLocked("010F", intArrayOf(0x41, 0x0F), 1)
                    ?.let { celsiusToFahrenheit(it[0].u8() - 40) }

                val stft = queryDataLocked("0106", intArrayOf(0x41, 0x06), 1)
                    ?.let { (it[0].u8() - 128) * 100f / 128f }

                val ltft = queryDataLocked("0107", intArrayOf(0x41, 0x07), 1)
                    ?.let { (it[0].u8() - 128) * 100f / 128f }

                val eq = queryDataLocked("0144", intArrayOf(0x41, 0x44), 2)
                    ?.let { ((it[0].u8() * 256) + it[1].u8()) / 32768f }

                val acVolts = queryDataLocked(
                    "221151",
                    intArrayOf(0x62, 0x11, 0x51),
                    1
                )?.let { it[0].u8() / 51f }

                val widebandAfr = acVolts
                    ?.takeIf { it in 0f..5.2f }
                    ?.let { (it / 0.5f) + 9.37f }

                val voltage = readVoltageLocked()

                cache = cache.copy(
                    coolantF = ect ?: cache.coolantF,
                    intakeAirF = iat ?: cache.intakeAirF,
                    stftPercent = stft ?: cache.stftPercent,
                    ltftPercent = ltft ?: cache.ltftPercent,
                    commandedEq = eq ?: cache.commandedEq,
                    acPressureVolts = acVolts ?: cache.acPressureVolts,
                    widebandAfr = widebandAfr ?: cache.widebandAfr,
                    adapterVoltage = voltage ?: cache.adapterVoltage
                )
            }

            cache = cache.copy(timestampMs = System.currentTimeMillis())
            return cache
        }
    }

    fun readActiveDtcs(): List<String> =
        synchronized(ioLock) {
            val response = sendCommandLocked("03", 2500)
            val frames = parseAllHexLines(response)

            frames.flatMap { frame ->
                val modeIndex = frame.indexOf(0x43)
                if (modeIndex < 0) {
                    emptyList()
                } else {
                    val data = frame.drop(modeIndex + 1)
                    data.chunked(2)
                        .filter { it.size == 2 && (it[0] != 0 || it[1] != 0) }
                        .map { decodeDtc(it[0], it[1]) }
                }
            }.distinct()
        }

    fun clearDtcs(): Boolean =
        synchronized(ioLock) {
            val response = sendCommandLocked("04", 2500)
            val frames = parseAllHexLines(response)
            frames.any { it.contains(0x44) } ||
                response.contains("OK", ignoreCase = true)
        }

    private fun readVoltageLocked(): Float? {
        val response = sendCommandLocked("ATRV", 1400)
        return Regex("""(\d+(?:\.\d+)?)\s*V""", RegexOption.IGNORE_CASE)
            .find(response)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFloatOrNull()
            ?.takeIf { it in 5f..20f }
    }

    private fun queryDataLocked(
        request: String,
        signature: IntArray,
        expectedLength: Int
    ): ByteArray? {
        val response = sendCommandLocked(request, 2200)
        val requestCompact = request.filter(Char::isLetterOrDigit).uppercase()

        for (frame in parseAllHexLines(response, requestCompact)) {
            val start = indexOfSequence(frame, signature)
            if (start < 0) continue

            val dataStart = start + signature.size
            if (dataStart + expectedLength > frame.size) continue

            return ByteArray(expectedLength) { offset ->
                frame[dataStart + offset].toByte()
            }
        }

        return null
    }

    private fun sendCommandLocked(command: String, timeoutMs: Int): String {
        drainInput()
        output.write((command.trim() + "\r").toByteArray(Charsets.US_ASCII))
        output.flush()
        return readUntilPrompt(timeoutMs)
    }

    private fun readUntilPrompt(
        firstByteTimeoutMs: Int,
        idleTimeoutMs: Int = 250
    ): String {
        val result = StringBuilder()
        val firstDeadline = System.currentTimeMillis() + firstByteTimeoutMs
        var lastProgressAt = 0L
        var receivedAny = false

        while (true) {
            val now = System.currentTimeMillis()

            if (!receivedAny && now >= firstDeadline) {
                break
            }

            if (receivedAny && now - lastProgressAt >= idleTimeoutMs) {
                break
            }

            val available = input.available()
            if (available <= 0) {
                Thread.sleep(2)
                continue
            }

            val buffer = ByteArray(minOf(available, 1024))
            val count = input.read(buffer)
            if (count <= 0) {
                Thread.sleep(2)
                continue
            }

            receivedAny = true
            lastProgressAt = System.currentTimeMillis()
            val chunk = String(buffer, 0, count, Charsets.US_ASCII)
            result.append(chunk)

            if (result.indexOf(">") >= 0) {
                break
            }
        }

        return result.toString()
    }

    private fun parseAllHexLines(
        response: String,
        echoedRequest: String? = null
    ): List<List<Int>> =
        response
            .uppercase()
            .replace("SEARCHING...", "\n")
            .replace('\r', '\n')
            .replace(">", "\n")
            .split('\n')
            .mapNotNull { raw ->
                val compact = raw.filter {
                    it in '0'..'9' || it in 'A'..'F'
                }

                if (
                    compact.length < 2 ||
                    compact.length % 2 != 0 ||
                    compact == echoedRequest
                ) {
                    null
                } else {
                    try {
                        compact.chunked(2).map { it.toInt(16) }
                    } catch (_: NumberFormatException) {
                        null
                    }
                }
            }

    private fun indexOfSequence(
        frame: List<Int>,
        signature: IntArray
    ): Int {
        if (signature.isEmpty() || frame.size < signature.size) return -1

        for (start in 0..frame.size - signature.size) {
            var matches = true

            for (offset in signature.indices) {
                if (frame[start + offset] != signature[offset]) {
                    matches = false
                    break
                }
            }

            if (matches) return start
        }

        return -1
    }

    private fun drainInput() {
        val buffer = ByteArray(1024)

        while (input.available() > 0) {
            val count = input.read(
                buffer,
                0,
                minOf(buffer.size, input.available())
            )
            if (count <= 0) break
        }
    }

    private fun clean(value: String): String =
        value
            .replace("\r", " ")
            .replace("\n", " ")
            .replace(">", " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun parseAllHexLines(response: String): List<List<Int>> =
        parseAllHexLines(response, null)

    private fun decodeDtc(first: Int, second: Int): String {
        val family = when ((first ushr 6) and 0x03) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            else -> 'U'
        }

        return "%c%d%X%X%X".format(
            family,
            (first ushr 4) and 0x03,
            first and 0x0F,
            (second ushr 4) and 0x0F,
            second and 0x0F
        )
    }

    private fun celsiusToFahrenheit(celsius: Int): Int =
        (celsius * 9f / 5f + 32f).roundToInt()

    private fun Byte.u8(): Int = toInt() and 0xFF
}
