/*
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Read-only P59 protocol port based on PCM Hammer:
 * https://github.com/PcmHammer/PcmHammer
 *
 * P59 configuration:
 *   kernel: Kernel-P01.bin
 *   kernel address: 0xFF8000
 *   image size: 1 MiB
 *   seed/key algorithm: 40
 *   maximum read block: 4096 bytes
 */
package com.p59.windows.protocol

import java.io.IOException
import java.security.MessageDigest

internal class P59KernelReader(
    private val kernelProvider: () -> ByteArray,
    private val transport: ObdxDviTransport,
    private val log: (String) -> Unit,
    private val onProgress: (bytesRead: Int, totalBytes: Int, blockAddress: Int) -> Unit
) {
    companion object {
        const val IMAGE_SIZE = 1024 * 1024
        private const val KERNEL_BASE_ADDRESS = 0xFF8000
        private const val MAX_KERNEL_END_ADDRESS = 0xFFCDFF
        private const val READ_BLOCK_SIZE = 4096
        private const val KERNEL_UPLOAD_CHUNK_SIZE = 1024
        private const val SECURITY_DELAY_MS = 11_000L

        private const val PRIORITY_PHYSICAL = 0x6C
        private const val PRIORITY_PHYSICAL_HIGH = 0x8C
        private const val PRIORITY_BLOCK = 0x6D

        private const val PCM_ID = 0x10
        private const val TOOL_ID = 0xF0
        private const val BROADCAST_ID = 0xFE

        // One-megabyte chips supported by the P01/P59 PCM Hammer kernel.
        private val SUPPORTED_ONE_MIB_FLASH_IDS = mapOf(
            0x0089889DL to "Intel 28F800B",
            0x008988F2L to "Intel 28F800F3",
            0x00012258L to "AMD AM29F800BB",
            0x00012281L to "AMD AM29BL802C"
        )
    }

    private var lastToolPresentAt = 0L
    private var kernelStarted = false

    fun readEntirePcm(useHighSpeedRequested: Boolean): ByteArray {
        if (useHighSpeedRequested) {
            log(
                "[P59 READ] VPW 4X was requested. This verified Windows read path " +
                    "uses VPW 1X until PCM Hammer's complete multi-module 4X " +
                    "permission exchange is ported."
            )
        }

        log("==============================================")
        log("[P59 READ] Starting checked 1 MiB P01/P59-family PCM read.")

        val factoryOsId = queryFactoryOperatingSystemId()
        log("[P59 READ] Factory OS ID: $factoryOsId (0x%08X).".format(factoryOsId))

        log("[P59 READ] Suppressing normal VPW chatter.")
        sendToolPresent(force = true)
        transport.sendNetworkFrame(
            bytes(
                PRIORITY_PHYSICAL,
                BROADCAST_ID,
                TOOL_ID,
                0x28,
                0x00
            )
        )
        Thread.sleep(300)
        transport.clearPendingNetworkFrames()

        unlockPcm()
        val kernel = loadKernel()
        uploadAndExecuteKernel(kernel)

        val kernelVersion = queryKernelVersion()
        kernelStarted = true
        log("[P59 READ] Kernel responded. Version token: 0x%016X.".format(kernelVersion))

        val flashId = queryFlashChipId()
        val flashDescription = SUPPORTED_ONE_MIB_FLASH_IDS[flashId]
            ?: throw IOException(
                "Connected PCM flash ID 0x%08X is not a supported 1 MiB P59 chip; "
                    .format(flashId) +
                    "the read was stopped before selecting an image size."
            )

        log(
            "[P59 READ] Flash chip: $flashDescription, ID 0x%08X, 1 MiB."
                .format(flashId)
        )
        log("[P59 READ] Reading PCM memory in 4096-byte blocks.")

        val image = ByteArray(IMAGE_SIZE)
        var address = 0

        while (address < IMAGE_SIZE) {
            val blockAddress = address
            val length = minOf(READ_BLOCK_SIZE, IMAGE_SIZE - blockAddress)
            val data = readBlockWithRetries(blockAddress, length)
            System.arraycopy(data, 0, image, blockAddress, data.size)

            address += data.size
            onProgress(address, IMAGE_SIZE, blockAddress)

            if ((address / READ_BLOCK_SIZE) % 16 == 0 || address == IMAGE_SIZE) {
                val percentage = address * 100 / IMAGE_SIZE
                log(
                    "[P59 READ] $percentage% complete — " +
                        "0x%06X / 0x%06X".format(address, IMAGE_SIZE)
                )
            }
        }

        log("[P59 READ] Transfer complete. Starting independent PCM-side CRC32 verification.")
        val localCrc = calculatePcmHammerCrc32(image)
        val pcmCrc = queryPcmCrc(address = 0, size = IMAGE_SIZE)

        if (localCrc != pcmCrc) {
            throw IOException(
                "Full-image CRC mismatch: Windows image %08X, PCM %08X. "
                    .format(localCrc, pcmCrc) +
                    "The file was not published."
            )
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(image)
            .joinToString("") { "%02x".format(it.u8()) }

        log(
            "[P59 READ] Verified: every transfer block passed its 16-bit checksum " +
                "and the full-image PCM CRC32 matched 0x%08X.".format(localCrc)
        )
        log("[P59 READ] Image SHA-256: $digest")
        log("==============================================")

        return image
    }

    fun exitKernel() {
        if (!kernelStarted) return

        try {
            transport.sendNetworkFrame(
                bytes(PRIORITY_PHYSICAL, PCM_ID, TOOL_ID, 0x20),
                ackTimeoutMs = 1800
            )
            Thread.sleep(300)
        } catch (error: Exception) {
            log(
                "[P59 READ] Kernel-exit message was not acknowledged: " +
                    (error.message ?: error.javaClass.simpleName)
            )
        } finally {
            kernelStarted = false
        }
    }

    private fun queryFactoryOperatingSystemId(): Long {
        sendToolPresent(force = true)

        val response = transport.transact(
            request = bytes(
                PRIORITY_PHYSICAL,
                PCM_ID,
                TOOL_ID,
                0x3C,
                0x0A
            ),
            timeoutMs = 2500,
            attempts = 3
        ) { frame ->
            isPcmReply(frame) &&
                frame.getOrNull(3).u8() == 0x7C &&
                frame.getOrNull(4).u8() == 0x0A
        }

        if (response.size < 8) {
            throw IOException(
                "PCM OS-ID response was truncated: ${response.toHexString()}"
            )
        }

        val value = if (response.size == 8) {
            ((response[5].u8().toLong() shl 16) or
                (response[6].u8().toLong() shl 8) or
                response[7].u8().toLong())
        } else {
            ((response[5].u8().toLong() shl 24) or
                (response[6].u8().toLong() shl 16) or
                (response[7].u8().toLong() shl 8) or
                response[8].u8().toLong())
        }

        if (value == 0L || value == 0x00FFFFFFL || value == 0xFFFFFFFFL) {
            throw IOException(
                "PCM returned an invalid operating-system ID: 0x%08X"
                    .format(value)
            )
        }

        return value
    }

    private fun unlockPcm() {
        log("[P59 READ] Requesting PCM security seed (algorithm 40).")
        var keyAttempted = false

        repeat(3) { unlockAttempt ->
            sendToolPresent(force = true)

            val seedResponse = transport.transact(
                request = bytes(
                    PRIORITY_PHYSICAL,
                    PCM_ID,
                    TOOL_ID,
                    0x27,
                    0x01
                ),
                timeoutMs = 2200,
                attempts = 3
            ) { frame ->
                isPcmReply(frame) &&
                    (
                        frame.getOrNull(3).u8() == 0x67 ||
                            frame.getOrNull(3).u8() == 0x7F
                    )
            }

            if (seedResponse.getOrNull(3).u8() == 0x7F) {
                throw IOException(
                    "PCM rejected seed request: ${seedResponse.toHexString()}"
                )
            }

            // The six-byte 67 01 37 reply is ambiguous in GM's protocol. PCM
            // Hammer treats it as "already unlocked" before any key has been
            // attempted; after a rejected key it means the security delay is
            // still active.
            if (
                seedResponse.size == 6 &&
                seedResponse.getOrNull(4).u8() == 0x01 &&
                seedResponse.getOrNull(5).u8() == 0x37
            ) {
                if (!keyAttempted) {
                    log("[P59 READ] PCM reports that security access is already unlocked.")
                    return
                }

                if (unlockAttempt == 2) {
                    throw IOException("PCM remained in security-delay lockout")
                }

                log("[P59 READ] PCM security delay active; waiting 11 seconds.")
                Thread.sleep(SECURITY_DELAY_MS)
                return@repeat
            }

            if (
                seedResponse.size < 7 ||
                seedResponse.getOrNull(4).u8() != 0x01
            ) {
                throw IOException(
                    "Unexpected PCM seed response: ${seedResponse.toHexString()}"
                )
            }

            val seed = (seedResponse[5].u8() shl 8) or seedResponse[6].u8()

            if (seed == 0x0000) {
                log("[P59 READ] PCM returned seed 0x0000; unlock is not required.")
                return
            }

            val key = if (seed == 0xFFFF) {
                0xFFFF
            } else {
                calculateAlgorithm40Key(seed)
            }

            log(
                "[P59 READ] Seed 0x%04X received; sending algorithm-40 key."
                    .format(seed)
            )
            keyAttempted = true

            val unlock = transport.transact(
                request = bytes(
                    PRIORITY_PHYSICAL,
                    PCM_ID,
                    TOOL_ID,
                    0x27,
                    0x02,
                    (key ushr 8) and 0xFF,
                    key and 0xFF
                ),
                timeoutMs = 2500,
                attempts = 3
            ) { frame ->
                isPcmReply(frame) &&
                    (
                        frame.getOrNull(3).u8() == 0x67 ||
                            frame.getOrNull(3).u8() == 0x7F
                    )
            }

            if (unlock.getOrNull(3).u8() == 0x7F) {
                throw IOException(
                    "PCM rejected the unlock request: ${unlock.toHexString()}"
                )
            }

            if (
                unlock.size < 6 ||
                unlock.getOrNull(4).u8() != 0x02
            ) {
                throw IOException(
                    "Unexpected PCM unlock response: ${unlock.toHexString()}"
                )
            }

            when (unlock[5].u8()) {
                0x34 -> {
                    log("[P59 READ] PCM security access granted.")
                    return
                }
                0x33 -> throw IOException("PCM refused security access")
                0x35 -> throw IOException("PCM reported an invalid security key")
                0x36, 0x37 -> {
                    if (unlockAttempt == 2) {
                        throw IOException("PCM security-delay timer did not clear")
                    }
                    log("[P59 READ] PCM is enforcing a security delay; waiting 11 seconds.")
                    Thread.sleep(SECURITY_DELAY_MS)
                }
                else -> throw IOException(
                    "Unknown PCM unlock response: ${unlock.toHexString()}"
                )
            }
        }

        throw IOException("Unable to unlock PCM after three security exchanges")
    }

    /**
     * Exact VPW algorithm-40 operation sequence from PCM Hammer's GPL
     * KeyAlgorithm table:
     *
     *   add 0x5201
     *   swap bytes, then add 0x9738
     *   one's complement
     *   subtract 0xD428
     */
    private fun calculateAlgorithm40Key(seed: Int): Int {
        var value = seed and 0xFFFF
        value = (value + 0x5201) and 0xFFFF

        val swapped =
            ((value and 0xFF) shl 8) or
                ((value ushr 8) and 0xFF)

        value = (swapped + 0x9738) and 0xFFFF
        value = value.inv() and 0xFFFF
        value = (value - 0xD428) and 0xFFFF

        return value
    }

    private fun loadKernel(): ByteArray {
        val kernel = kernelProvider().copyOf()

        if (kernel.isEmpty()) {
            throw IOException("Kernel-P01.bin is empty")
        }

        if (KERNEL_BASE_ADDRESS + kernel.size > MAX_KERNEL_END_ADDRESS) {
            throw IOException(
                "Kernel is too large for P01/P59 RAM: ${kernel.size} bytes"
            )
        }

        return kernel
    }

    private fun uploadAndExecuteKernel(kernel: ByteArray) {
        val claimedSize = minOf(4096, kernel.size)

        log(
            "[P59 READ] Requesting kernel upload permission: " +
                "${kernel.size} bytes at 0x%06X.".format(KERNEL_BASE_ADDRESS)
        )

        sendToolPresent(force = true)

        val permission = transport.transact(
            request = bytes(
                PRIORITY_PHYSICAL,
                PCM_ID,
                TOOL_ID,
                0x34,
                0x00,
                (claimedSize ushr 8) and 0xFF,
                claimedSize and 0xFF,
                (KERNEL_BASE_ADDRESS ushr 16) and 0xFF,
                (KERNEL_BASE_ADDRESS ushr 8) and 0xFF,
                KERNEL_BASE_ADDRESS and 0xFF
            ),
            timeoutMs = 3000,
            attempts = 3
        ) { frame ->
            isPcmReply(frame) &&
                (
                    frame.getOrNull(3).u8() == 0x74 ||
                        frame.getOrNull(3).u8() == 0x7F
                )
        }

        if (permission.getOrNull(3).u8() == 0x7F) {
            throw IOException(
                "PCM denied kernel upload permission: ${permission.toHexString()}"
            )
        }

        log("[P59 READ] Kernel upload permission granted.")

        var upperBound = kernel.size
        var uploaded = 0

        while (upperBound > 0) {
            val length = minOf(KERNEL_UPLOAD_CHUNK_SIZE, upperBound)
            val offset = upperBound - length
            val address = KERNEL_BASE_ADDRESS + offset
            val execute = offset == 0

            sendToolPresent()

            val block = createKernelUploadBlock(
                kernel = kernel,
                offset = offset,
                length = length,
                address = address,
                execute = execute
            )

            val response = transport.transact(
                request = block,
                timeoutMs = 5500,
                attempts = 4
            ) { frame ->
                isPcmReply(frame) &&
                    (
                        frame.getOrNull(3).u8() == 0x76 ||
                            frame.getOrNull(3).u8() == 0x7F
                    )
            }

            if (response.getOrNull(3).u8() == 0x7F) {
                throw IOException(
                    "PCM rejected kernel block at 0x%06X: %s"
                        .format(address, response.toHexString())
                )
            }

            upperBound = offset
            uploaded += length

            val percentage = uploaded * 100 / kernel.size
            log(
                "[P59 READ] Kernel upload ${percentage}% — block ${"0x%06X".format(address)}."
            )
        }

        Thread.sleep(400)
        log("[P59 READ] Kernel upload completed and execution requested.")
    }

    private fun queryKernelVersion(): Long {
        val response = transport.transact(
            request = bytes(
                PRIORITY_PHYSICAL,
                PCM_ID,
                TOOL_ID,
                0x3D,
                0x00
            ),
            timeoutMs = 3000,
            attempts = 5
        ) { frame ->
            isPcmReply(frame) &&
                frame.getOrNull(3).u8() == 0x7D &&
                frame.getOrNull(4).u8() == 0x00
        }

        if (response.size < 9) {
            throw IOException(
                "Kernel version response was truncated: ${response.toHexString()}"
            )
        }

        val epoch =
            (response[5].u8().toLong() shl 24) or
                (response[6].u8().toLong() shl 16) or
                (response[7].u8().toLong() shl 8) or
                response[8].u8().toLong()
        val pcmType = response.getOrNull(9).u8().coerceAtLeast(0)
        val version = (epoch shl 8) or pcmType.toLong()

        if (epoch == 0L) {
            throw IOException("Uploaded kernel did not return a valid version")
        }

        return version
    }

    private fun queryFlashChipId(): Long {
        sendToolPresent(force = true)

        val response = transport.transact(
            request = bytes(
                PRIORITY_PHYSICAL,
                PCM_ID,
                TOOL_ID,
                0x3D,
                0x01
            ),
            timeoutMs = 3500,
            attempts = 3
        ) { frame ->
            isPcmReply(frame) &&
                frame.getOrNull(3).u8() == 0x7D &&
                frame.getOrNull(4).u8() == 0x01
        }

        if (response.size < 9) {
            throw IOException(
                "Flash-ID response was truncated: ${response.toHexString()}"
            )
        }

        return (response[5].u8().toLong() shl 24) or
            (response[6].u8().toLong() shl 16) or
            (response[7].u8().toLong() shl 8) or
            response[8].u8().toLong()
    }

    private fun createKernelUploadBlock(
        kernel: ByteArray,
        offset: Int,
        length: Int,
        address: Int,
        execute: Boolean
    ): ByteArray {
        val frame = ByteArray(10 + length + 2)

        frame[0] = PRIORITY_BLOCK.toByte()
        frame[1] = PCM_ID.toByte()
        frame[2] = TOOL_ID.toByte()
        frame[3] = 0x36
        frame[4] = if (execute) 0x80.toByte() else 0x00
        frame[5] = ((length ushr 8) and 0xFF).toByte()
        frame[6] = (length and 0xFF).toByte()
        frame[7] = ((address ushr 16) and 0xFF).toByte()
        frame[8] = ((address ushr 8) and 0xFF).toByte()
        frame[9] = (address and 0xFF).toByte()

        System.arraycopy(kernel, offset, frame, 10, length)
        writeBlockChecksum(frame)

        return frame
    }

    private fun readBlockWithRetries(
        address: Int,
        length: Int
    ): ByteArray {
        var lastError: Throwable? = null

        repeat(10) { retry ->
            try {
                sendToolPresent(force = true)

                val response = transport.transact(
                    request = bytes(
                        PRIORITY_BLOCK,
                        PCM_ID,
                        TOOL_ID,
                        0x35,
                        0x01,
                        (length ushr 8) and 0xFF,
                        length and 0xFF,
                        (address ushr 16) and 0xFF,
                        (address ushr 8) and 0xFF,
                        address and 0xFF
                    ),
                    // At VPW 1X, a 4096-byte response needs roughly four
                    // seconds on the vehicle bus before DVI can deliver it.
                    timeoutMs = 10_000,
                    attempts = 1
                ) { frame ->
                    if (!isPcmReply(frame)) {
                        false
                    } else if (frame.getOrNull(3).u8() == 0x7F) {
                        true
                    } else {
                        frame.getOrNull(3).u8() == 0x76 &&
                            frame.size >= 10 &&
                            parseAddress(frame) == address
                    }
                }

                if (response.getOrNull(3).u8() == 0x7F) {
                    throw IOException(
                        "PCM negative response for address 0x%06X: %s"
                            .format(address, response.toHexString())
                    )
                }

                return parseReadResponse(response, address, length)
            } catch (error: Throwable) {
                lastError = error
                log(
                    "[P59 READ] Block 0x%06X retry %d/10: %s"
                        .format(
                            address,
                            retry + 1,
                            error.message ?: error.javaClass.simpleName
                        )
                )
                transport.clearPendingNetworkFrames()
                Thread.sleep(150)
            }
        }

        throw IOException(
            "Unable to read block at 0x%06X after retries".format(address),
            lastError
        )
    }

    private fun parseReadResponse(
        frame: ByteArray,
        expectedAddress: Int,
        expectedLength: Int
    ): ByteArray {
        if (frame.size < 12) {
            throw IOException("Truncated kernel response: ${frame.toHexString()}")
        }

        if (
            frame[0].u8() != PRIORITY_BLOCK ||
            frame[1].u8() != TOOL_ID ||
            frame[2].u8() != PCM_ID ||
            frame[3].u8() != 0x76
        ) {
            throw IOException(
                "Unexpected kernel response header: ${frame.toHexString()}"
            )
        }

        val encoding = frame[4].u8()
        val dataLength = (frame[5].u8() shl 8) or frame[6].u8()
        val actualAddress = parseAddress(frame)

        if (actualAddress != expectedAddress) {
            throw IOException(
                "Kernel block address mismatch: expected 0x%06X, received 0x%06X"
                    .format(expectedAddress, actualAddress)
            )
        }

        if (dataLength != expectedLength) {
            throw IOException(
                "Kernel block length mismatch at 0x%06X: expected %d, received %d"
                    .format(expectedAddress, expectedLength, dataLength)
            )
        }

        if (encoding != 0x01) {
            throw IOException(
                "Unsupported kernel block encoding $encoding at 0x%06X"
                    .format(expectedAddress)
            )
        }

        val requiredSize = 10 + dataLength + 2

        if (frame.size != requiredSize) {
            throw IOException(
                "Kernel data frame size mismatch at 0x%06X: expected %d, received %d"
                    .format(expectedAddress, requiredSize, frame.size)
            )
        }

        var calculated = 0

        for (index in 4 until 10 + dataLength) {
            calculated = (calculated + frame[index].u8()) and 0xFFFF
        }

        val reported =
            (frame[10 + dataLength].u8() shl 8) or
                frame[11 + dataLength].u8()

        if (reported != calculated) {
            throw IOException(
                "Kernel block checksum mismatch at 0x%06X: expected %04X, received %04X"
                    .format(expectedAddress, calculated, reported)
            )
        }

        return frame.copyOfRange(10, 10 + dataLength)
    }

    private fun queryPcmCrc(address: Int, size: Int): Long {
        require(address in 0..0xFFFFFF)
        require(size in 1..0xFFFFFF)

        val request = bytes(
            PRIORITY_PHYSICAL,
            PCM_ID,
            TOOL_ID,
            0x3D,
            0x02,
            (size ushr 16) and 0xFF,
            (size ushr 8) and 0xFF,
            size and 0xFF,
            (address ushr 16) and 0xFF,
            (address ushr 8) and 0xFF,
            address and 0xFF
        )

        sendToolPresent(force = true)
        val response = transport.transact(
            request = request,
            // The assembly kernel computes the requested range synchronously.
            // One MiB can take several seconds on the PCM CPU.
            timeoutMs = 30_000,
            attempts = 2
        ) { frame ->
            isPcmReply(frame) &&
                frame.size >= 15 &&
                frame.getOrNull(3).u8() == 0x7D &&
                frame.getOrNull(4).u8() == 0x02 &&
                frame.sliceArray(5..10).contentEquals(request.sliceArray(5..10))
        }

        if (response.size < 15) {
            throw IOException(
                "PCM CRC response was truncated: ${response.toHexString()}"
            )
        }

        return (response[11].u8().toLong() shl 24) or
            (response[12].u8().toLong() shl 16) or
            (response[13].u8().toLong() shl 8) or
            response[14].u8().toLong()
    }

    /** PCM Hammer CRC32: non-reflected 0x04C11DB7, initial remainder zero. */
    private fun calculatePcmHammerCrc32(data: ByteArray): Long {
        var remainder = 0L
        val polynomial = 0x04C11DB7L
        val topBit = 0x80000000L

        for (value in data) {
            remainder = remainder xor (value.u8().toLong() shl 24)

            repeat(8) {
                remainder = if ((remainder and topBit) != 0L) {
                    ((remainder shl 1) xor polynomial) and 0xFFFFFFFFL
                } else {
                    (remainder shl 1) and 0xFFFFFFFFL
                }
            }
        }

        return remainder
    }

    private fun sendToolPresent(force: Boolean = false) {
        val now = System.currentTimeMillis()

        if (!force && now - lastToolPresentAt < 1800) {
            return
        }

        transport.sendNetworkFrame(
            bytes(
                PRIORITY_PHYSICAL_HIGH,
                BROADCAST_ID,
                TOOL_ID,
                0x3F
            ),
            ackTimeoutMs = 1800
        )

        lastToolPresentAt = now
    }

    private fun writeBlockChecksum(frame: ByteArray) {
        var sum = 0

        for (index in 4 until frame.size - 2) {
            sum = (sum + frame[index].u8()) and 0xFFFF
        }

        frame[frame.lastIndex - 1] = ((sum ushr 8) and 0xFF).toByte()
        frame[frame.lastIndex] = (sum and 0xFF).toByte()
    }

    private fun isPcmReply(frame: ByteArray): Boolean =
        frame.size >= 4 &&
            frame[1].u8() == TOOL_ID &&
            frame[2].u8() == PCM_ID

    private fun parseAddress(frame: ByteArray): Int {
        if (frame.size < 10) return -1

        return (frame[7].u8() shl 16) or
            (frame[8].u8() shl 8) or
            frame[9].u8()
    }

    private fun bytes(vararg values: Int): ByteArray =
        ByteArray(values.size) { index -> values[index].toByte() }

    private fun Byte?.u8(): Int = this?.toInt()?.and(0xFF) ?: -1
    private fun Byte.u8(): Int = toInt() and 0xFF

    private fun ByteArray.toHexString(): String =
        joinToString(" ") { "%02X".format(it.u8()) }
}
