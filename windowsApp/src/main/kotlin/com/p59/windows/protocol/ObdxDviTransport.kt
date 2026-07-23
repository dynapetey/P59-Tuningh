/*
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * OBDX Pro DVI transport behavior ported from:
 * https://github.com/PcmHammer/PcmHammer
 *
 * This file implements only the VPW functionality needed for a verified,
 * read-only P01/P59 kernel session.
 */
package com.p59.windows.protocol

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayDeque

internal class ObdxDviTransport(
    private val input: InputStream,
    private val output: OutputStream,
    private val log: (String) -> Unit
) {
    private data class DviPacket(
        val command: Int,
        val payload: ByteArray,
        val raw: ByteArray
    )

    private val pendingNetworkFrames = ArrayDeque<ByteArray>()

    /**
     * Recover the adapter to ELM mode first, then enter OBDX DVI mode and
     * explicitly establish the VPW receive configuration used by PCM Hammer.
     */
    fun enterDviMode() {
        log("[DVI] Resetting OBDX interface and entering Direct Vehicle Interface mode...")

        // A previous interrupted flash can leave the adapter in DVI mode. The
        // binary DVI reset is harmless garbage to an ELM parser, while ATZ
        // recovers an adapter that is already in ELM mode. PCM Hammer sends
        // ATZ twice because some firmware misses the first reset while changing
        // parser state.
        try {
            writeRaw(buildDeviceCommand(0x25, byteArrayOf()))
            Thread.sleep(250)
        } catch (_: Exception) {
        }

        drainInput()
        writeAscii("ATZ\r")
        Thread.sleep(50)
        writeAscii("ATZ\r")
        Thread.sleep(400)
        readElmResponse(2000)

        drainInput()
        writeAscii("AT@1\r")
        val identity = readElmResponse(1800)
        if (!identity.contains("OBDX", ignoreCase = true)) {
            throw IOException(
                "Connected adapter did not identify itself as an OBDX Pro: " +
                    identity.cleanedElmLine()
            )
        }
        log("[DVI] Adapter identity: ${identity.cleanedElmLine()}")

        drainInput()
        writeAscii("DXDP1\r")
        val switchReply = readElmResponse(1800)
        if (!switchReply.contains("OK", ignoreCase = true)) {
            throw IOException(
                "OBDX Pro refused DVI mode: ${switchReply.cleanedElmLine()}"
            )
        }

        // DVI 0x24, sub-command 0x03: disable RX timestamps. This keeps
        // network packet framing deterministic even if a prior session enabled
        // them.
        sendDeviceCommand(0x24, bytes(0x03, 0x00), 1800)

        // DVI 0x31, sub-command 0x01: select OBD protocol; 1 = VPW.
        sendDeviceCommand(0x31, bytes(0x01, 0x01), 1800)

        // DVI 0x33, sub-command 0x08: do not append the physical VPW CRC to
        // received network frames. PCM Hammer's Message layer expects the
        // protocol frame without that transport CRC byte.
        sendDeviceCommand(0x33, bytes(0x08, 0x00), 1800)

        // DVI 0x33, sub-command 0x00: accept messages addressed to tool ID F0.
        sendDeviceCommand(0x33, bytes(0x00, 0xF0, 0x01), 1800)

        // DVI 0x31, sub-command 0x02: enable network RX/TX.
        sendDeviceCommand(0x31, bytes(0x02, 0x01), 1800)

        pendingNetworkFrames.clear()
        drainInput()
        log("[DVI] VPW network enabled; tool-address filter F0 active.")
    }

    fun returnToElmMode() {
        log("[DVI] Returning adapter to ELM-compatible mode...")

        try {
            writeRaw(buildDeviceCommand(0x25, byteArrayOf()))
            Thread.sleep(350)
        } catch (_: Exception) {
            // Continue with the ASCII reset. A disconnected adapter is handled
            // by the caller's connection recovery path.
        }

        drainInput()
        writeAscii("ATZ\r")
        Thread.sleep(50)
        writeAscii("ATZ\r")
        Thread.sleep(400)
        readElmResponse(2000)
        drainInput()
        pendingNetworkFrames.clear()
    }

    fun sendNetworkFrame(frame: ByteArray, ackTimeoutMs: Int = 2500) {
        require(frame.isNotEmpty()) { "VPW frame is empty" }
        require(frame.size <= 8192) { "VPW frame is too large: ${frame.size}" }

        val packet = if (frame.size <= 0xFF) {
            ByteArray(frame.size + 3).also { out ->
                out[0] = 0x10
                out[1] = frame.size.toByte()
                System.arraycopy(frame, 0, out, 2, frame.size)
                out[out.lastIndex] = dviChecksum(out)
            }
        } else {
            ByteArray(frame.size + 4).also { out ->
                out[0] = 0x11
                out[1] = ((frame.size ushr 8) and 0xFF).toByte()
                out[2] = (frame.size and 0xFF).toByte()
                System.arraycopy(frame, 0, out, 3, frame.size)
                out[out.lastIndex] = dviChecksum(out)
            }
        }

        writeRaw(packet)

        val deadline = System.currentTimeMillis() + ackTimeoutMs
        var timeoutCount = 0

        while (System.currentTimeMillis() < deadline && timeoutCount < 5) {
            val remaining = (deadline - System.currentTimeMillis())
                .coerceAtLeast(1)
                .toInt()

            val reply = readDviPacket(remaining)

            if (reply == null) {
                timeoutCount++
                continue
            }

            when (reply.command) {
                0x08, 0x09 -> pendingNetworkFrames.addLast(reply.payload)

                0x20, 0x21 -> {
                    val status = reply.payload.firstOrNull()?.u8() ?: 0
                    if (status != 0) {
                        throw IOException(
                            "OBDX DVI transmit acknowledgment reported status 0x%02X"
                                .format(status)
                        )
                    }
                    return
                }

                0x7F -> throw IOException(
                    "OBDX DVI device error: ${reply.raw.toHexString()}"
                )
            }
        }

        throw IOException("Timed out waiting for OBDX DVI transmit acknowledgment")
    }

    fun receiveNetworkFrame(timeoutMs: Int): ByteArray? {
        if (pendingNetworkFrames.isNotEmpty()) {
            return pendingNetworkFrames.removeFirst()
        }

        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis())
                .coerceAtLeast(1)
                .toInt()

            val packet = readDviPacket(remaining) ?: return null

            when (packet.command) {
                0x08, 0x09 -> return packet.payload
                0x7F -> throw IOException(
                    "OBDX DVI device error while receiving: ${packet.raw.toHexString()}"
                )
            }
        }

        return null
    }

    fun transact(
        request: ByteArray,
        timeoutMs: Int,
        attempts: Int = 3,
        matcher: (ByteArray) -> Boolean
    ): ByteArray {
        var lastResponse: ByteArray? = null

        repeat(attempts) { attempt ->
            val response = transactOrNull(request, timeoutMs, matcher)
            if (response != null) {
                return response
            }

            lastResponse = pendingNetworkFrames.lastOrNull()
            if (attempt + 1 < attempts) {
                log(
                    "[DVI] Request retry ${attempt + 2}/$attempts: " +
                        request.toHexString()
                )
            }
        }

        throw IOException(
            "No matching VPW response. Last frame: " +
                (lastResponse?.toHexString() ?: "<none>")
        )
    }

    fun transactOrNull(
        request: ByteArray,
        timeoutMs: Int,
        matcher: (ByteArray) -> Boolean
    ): ByteArray? {
        clearPendingNetworkFrames()
        sendNetworkFrame(request)

        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis())
                .coerceAtLeast(1)
                .toInt()

            val response = receiveNetworkFrame(remaining) ?: return null
            if (matcher(response)) {
                return response
            }
        }

        return null
    }

    fun clearPendingNetworkFrames() {
        pendingNetworkFrames.clear()
        drainInput()
    }

    private fun sendDeviceCommand(
        command: Int,
        payload: ByteArray,
        timeoutMs: Int
    ) {
        val request = buildDeviceCommand(command, payload)
        writeRaw(request)

        val expectedCommand = (command + 0x10) and 0xFF
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis())
                .coerceAtLeast(1)
                .toInt()

            val response = readDviPacket(remaining) ?: break

            when (response.command) {
                0x08, 0x09 -> pendingNetworkFrames.addLast(response.payload)
                0x7F -> throw IOException(
                    "OBDX rejected DVI command 0x%02X: %s"
                        .format(command, response.raw.toHexString())
                )
                expectedCommand -> {
                    if (!response.payload.contentEquals(payload)) {
                        throw IOException(
                            "OBDX DVI command 0x%02X returned an unexpected echo: %s"
                                .format(command, response.raw.toHexString())
                        )
                    }
                    return
                }
            }
        }

        throw IOException(
            "Timed out waiting for DVI command 0x%02X response".format(command)
        )
    }

    private fun buildDeviceCommand(command: Int, payload: ByteArray): ByteArray {
        require(payload.size <= 0xFF)

        return ByteArray(payload.size + 3).also { packet ->
            packet[0] = command.toByte()
            packet[1] = payload.size.toByte()
            System.arraycopy(payload, 0, packet, 2, payload.size)
            packet[packet.lastIndex] = dviChecksum(packet)
        }
    }

    /**
     * Read one complete DVI packet. The initial timeout only applies while
     * waiting for the first byte. After a packet starts, timeout is measured as
     * *lack of progress*, not total elapsed time. This is essential for large
     * 4 KiB network frames: abandoning a partially consumed frame would leave
     * the serial stream permanently misaligned.
     */
    private fun readDviPacket(initialTimeoutMs: Int): DviPacket? {
        val command = readFirstByte(initialTimeoutMs) ?: return null
        val lengthBytes = if (command == 0x09) 2 else 1

        val lengthHeader = readExactWithProgressTimeout(
            length = lengthBytes,
            inactivityTimeoutMs = 1500,
            absoluteTimeoutMs = 4000
        ) ?: throw IOException(
            "OBDX DVI packet ended while reading its length header"
        )

        val length = if (lengthBytes == 1) {
            lengthHeader[0].u8()
        } else {
            (lengthHeader[0].u8() shl 8) or lengthHeader[1].u8()
        }

        if (length > 8192) {
            throw IOException(
                "Invalid OBDX DVI payload length $length for command 0x%02X"
                    .format(command)
            )
        }

        val payloadAndChecksum = readExactWithProgressTimeout(
            length = length + 1,
            inactivityTimeoutMs = 2500,
            absoluteTimeoutMs = maxOf(5000, length * 5)
        ) ?: throw IOException(
            "OBDX DVI packet 0x%02X was truncated after its header (payload length %d)"
                .format(command, length)
        )

        val payload = payloadAndChecksum.copyOfRange(0, length)
        val receivedChecksum = payloadAndChecksum.last()

        val rawWithoutChecksum = ByteArray(1 + lengthBytes + length)
        rawWithoutChecksum[0] = command.toByte()
        System.arraycopy(lengthHeader, 0, rawWithoutChecksum, 1, lengthBytes)
        System.arraycopy(payload, 0, rawWithoutChecksum, 1 + lengthBytes, length)

        val completeRaw = rawWithoutChecksum + receivedChecksum
        val expectedChecksum = complementOfSum(rawWithoutChecksum)

        if (receivedChecksum != expectedChecksum) {
            throw IOException(
                "OBDX DVI serial checksum mismatch: expected %02X, received %02X"
                    .format(expectedChecksum.u8(), receivedChecksum.u8())
            )
        }

        return DviPacket(command, payload, completeRaw)
    }

    private fun dviChecksum(packetWithPlaceholder: ByteArray): Byte {
        var sum = 0

        for (index in 0 until packetWithPlaceholder.lastIndex) {
            sum = (sum + packetWithPlaceholder[index].u8()) and 0xFF
        }

        return sum.inv().toByte()
    }

    private fun complementOfSum(bytes: ByteArray): Byte {
        var sum = 0

        for (value in bytes) {
            sum = (sum + value.u8()) and 0xFF
        }

        return sum.inv().toByte()
    }

    private fun readElmResponse(timeoutMs: Int): String {
        val result = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis())
                .coerceAtLeast(1)
                .toInt()
            val next = readFirstByte(remaining) ?: break
            val character = next.toChar()

            if (character == '>') {
                break
            }

            result.append(character)
        }

        return result.toString()
    }

    private fun writeAscii(value: String) {
        writeRaw(value.toByteArray(Charsets.US_ASCII))
    }

    private fun writeRaw(bytes: ByteArray) {
        output.write(bytes)
        output.flush()
    }

    private fun drainInput() {
        val buffer = ByteArray(512)

        while (input.available() > 0) {
            val count = input.read(
                buffer,
                0,
                input.available().coerceAtMost(buffer.size)
            )

            if (count <= 0) break
        }
    }

    private fun readFirstByte(timeoutMs: Int): Int? {
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            if (input.available() > 0) {
                val value = input.read()
                return value.takeIf { it >= 0 }
            }

            Thread.sleep(1)
        }

        return null
    }

    private fun readExactWithProgressTimeout(
        length: Int,
        inactivityTimeoutMs: Int,
        absoluteTimeoutMs: Int
    ): ByteArray? {
        if (length == 0) return byteArrayOf()

        val result = ByteArray(length)
        var offset = 0
        val absoluteDeadline = System.currentTimeMillis() + absoluteTimeoutMs
        var inactivityDeadline = System.currentTimeMillis() + inactivityTimeoutMs

        while (
            offset < length &&
            System.currentTimeMillis() < absoluteDeadline &&
            System.currentTimeMillis() < inactivityDeadline
        ) {
            val available = input.available()

            if (available <= 0) {
                Thread.sleep(1)
                continue
            }

            val count = input.read(
                result,
                offset,
                minOf(length - offset, available)
            )

            if (count < 0) return null
            if (count == 0) {
                Thread.sleep(1)
                continue
            }

            offset += count
            inactivityDeadline = System.currentTimeMillis() + inactivityTimeoutMs
        }

        return if (offset == length) result else null
    }

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { index -> values[index].toByte() }

    private fun String.cleanedElmLine(): String =
        replace("\r", " ")
            .replace("\n", " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun Byte.u8(): Int = toInt() and 0xFF

    private fun ByteArray.toHexString(): String =
        joinToString(" ") { "%02X".format(it.u8()) }
}
