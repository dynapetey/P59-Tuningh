package com.p59.windows

import com.fazecast.jSerialComm.SerialPort
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

data class SerialPortInfo(
    val systemName: String,
    val description: String
) {
    override fun toString(): String =
        if (description.isBlank() || description == systemName) systemName
        else "$systemName — $description"
}

class SerialConnection : Closeable {
    @Volatile
    private var port: SerialPort? = null

    val isOpen: Boolean
        get() = port?.isOpen == true

    val systemPortName: String?
        get() = port?.systemPortName

    fun listPorts(): List<SerialPortInfo> =
        SerialPort.getCommPorts()
            .map {
                SerialPortInfo(
                    systemName = it.systemPortName,
                    description = it.descriptivePortName ?: it.portDescription ?: ""
                )
            }
            .sortedBy { it.systemName }

    @Synchronized
    fun connect(portName: String, baudRate: Int = 115200) {
        close()

        val selected = SerialPort.getCommPort(portName)
        selected.setComPortParameters(
            baudRate,
            8,
            SerialPort.ONE_STOP_BIT,
            SerialPort.NO_PARITY
        )
        selected.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED)
        selected.setComPortTimeouts(
            SerialPort.TIMEOUT_NONBLOCKING,
            0,
            0
        )

        if (!selected.openPort(2500)) {
            throw IllegalStateException(
                "Unable to open $portName. Close other programs using the OBDX Pro and try again."
            )
        }

        port = selected
    }

    fun inputStream(): InputStream =
        port?.inputStream
            ?: error("Serial port is not connected")

    fun outputStream(): OutputStream =
        port?.outputStream
            ?: error("Serial port is not connected")

    @Synchronized
    override fun close() {
        val current = port
        port = null

        if (current != null) {
            try {
                current.inputStream?.close()
            } catch (_: Exception) {
            }

            try {
                current.outputStream?.close()
            } catch (_: Exception) {
            }

            if (current.isOpen) {
                current.closePort()
            }
        }
    }
}
