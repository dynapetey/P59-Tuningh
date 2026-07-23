package com.p59.windows

import com.p59.windows.protocol.ObdxDviTransport
import com.p59.windows.protocol.P59KernelReader
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

data class PcmReadResult(
    val file: File,
    val bytes: Int,
    val sha256: String
)

class P59DesktopReadService(
    private val input: InputStream,
    private val output: OutputStream,
    private val ioLock: Any,
    private val log: (String) -> Unit,
    private val progress: (bytesRead: Int, totalBytes: Int, blockAddress: Int) -> Unit
) {
    fun readTo(destination: File): PcmReadResult =
        synchronized(ioLock) {
            val transport = ObdxDviTransport(input, output, log)
            val reader = P59KernelReader(
                kernelProvider = {
                    javaClass.getResourceAsStream("/kernel_p01.bin")
                        ?.use { it.readBytes() }
                        ?: error("kernel_p01.bin is missing from the Windows application resources")
                },
                transport = transport,
                log = log,
                onProgress = progress
            )

            var enteredDvi = false

            try {
                transport.enterDviMode()
                enteredDvi = true

                val image = reader.readEntirePcm(useHighSpeedRequested = false)
                require(image.size == P59KernelReader.IMAGE_SIZE) {
                    "Expected a 1 MiB P59 image; received ${image.size} bytes"
                }

                destination.parentFile?.mkdirs()
                val temp = File(
                    destination.parentFile ?: File("."),
                    destination.name + ".partial"
                )

                temp.outputStream().use { stream ->
                    stream.write(image)
                    stream.fdSyncIfPossible()
                }

                try {
                    Files.move(
                        temp.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(
                        temp.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }

                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(image)
                    .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

                PcmReadResult(
                    file = destination,
                    bytes = image.size,
                    sha256 = digest
                )
            } finally {
                try {
                    reader.exitKernel()
                } catch (error: Exception) {
                    log(
                        "[WINDOWS] Kernel exit warning: " +
                            (error.message ?: error.javaClass.simpleName)
                    )
                }

                if (enteredDvi) {
                    try {
                        transport.returnToElmMode()
                    } catch (error: Exception) {
                        log(
                            "[WINDOWS] Adapter recovery warning: " +
                                (error.message ?: error.javaClass.simpleName)
                        )
                    }
                }
            }
        }

    private fun java.io.OutputStream.fdSyncIfPossible() {
        if (this is java.io.FileOutputStream) {
            fd.sync()
        }
    }
}
