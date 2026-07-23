package com.p59.windows

import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayDeque

private class ScriptedSerial {
    private val incoming = ArrayDeque<Byte>()
    private val command = StringBuilder()

    val input = object : InputStream() {
        override fun available(): Int = synchronized(incoming) { incoming.size }
        override fun read(): Int = synchronized(incoming) {
            if (incoming.isEmpty()) -1 else incoming.removeFirst().toInt() and 0xFF
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            synchronized(incoming) {
                if (incoming.isEmpty()) return -1
                var count = 0
                while (count < length && incoming.isNotEmpty()) {
                    buffer[offset + count] = incoming.removeFirst()
                    count++
                }
                count
            }
    }

    val output = object : OutputStream() {
        override fun write(value: Int) {
            val ch = value.toChar()
            if (ch == '\r') {
                respond(command.toString())
                command.setLength(0)
            } else if (ch != '\n') {
                command.append(ch)
            }
        }
    }

    private fun respond(raw: String) {
        val cmd = raw.trim().uppercase()
        val response = when (cmd) {
            "ATZ" -> "OBDX Pro GT v1.35\r>"
            "AT@1" -> "OBDX Pro GT\r>"
            "ATE0", "ATL0", "ATS0", "ATH1", "ATAL", "ATSP2", "ATAT1", "ATSH6C10F0" -> "OK\r>"
            "ATRV" -> "13.8V\r>"
            "010C" -> "6C F0 10 41 0C 0D 48\r>" // 850 RPM
            "010D" -> "6C F0 10 41 0D 64\r>" // 62 mph
            "010B" -> "6C F0 10 41 0B 28\r>" // 40 kPa
            "0111" -> "6C F0 10 41 11 80\r>"
            "0110" -> "6C F0 10 41 10 04 D2\r>" // 12.34 g/s
            "010E" -> "6C F0 10 41 0E A0\r>" // 16 degrees
            "0105" -> "6C F0 10 41 05 82\r>" // 194 F
            "010F" -> "6C F0 10 41 0F 50\r>" // 104 F
            "0106" -> "6C F0 10 41 06 80\r>"
            "0107" -> "6C F0 10 41 07 80\r>"
            "0144" -> "6C F0 10 41 44 80 00\r>"
            "221151" -> "6C F0 10 62 11 51 66\r>" // 2.0 V
            "03" -> "6C F0 10 43 01 71 00 00\r>"
            "04" -> "6C F0 10 44\r>"
            else -> "NO DATA\r>"
        }
        synchronized(incoming) {
            response.toByteArray().forEach(incoming::addLast)
        }
    }
}

class ElmLiveDataClientTest {
    @kotlin.test.Test
    fun simulatedElmSession() {
    val serial = ScriptedSerial()
    val client = ElmLiveDataClient(serial.input, serial.output, Any()) {}
    client.initialize()

    var snapshot = TelemetrySnapshot()
    repeat(4) { snapshot = client.pollSnapshot() }

    check(snapshot.rpm == 850) { "RPM ${snapshot.rpm}" }
    check(snapshot.mapKpa == 40f)
    check(snapshot.mafGps == 12.34f)
    check(snapshot.coolantF == 194)
    check(snapshot.acPressureVolts == 2f)
    check(kotlin.math.abs((snapshot.widebandAfr ?: 0f) - 13.37f) < 0.01f)
    check(snapshot.adapterVoltage == 13.8f)
    check(client.readActiveDtcs() == listOf("P0171"))
    check(client.clearDtcs())

    println("ELM_WINDOWS_SIMULATION_PASS")
}
}
