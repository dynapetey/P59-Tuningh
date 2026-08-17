package com.p59.windows

import java.io.File

data class PcmHammerResult(val exitCode: Int)

class PcmHammerBackend(private val log: (String) -> Unit) {
    private val executable: File? = locateExecutable()

    val isAvailable: Boolean
        get() = executable?.canExecute() == true

    fun testWrite(image: File, device: String): PcmHammerResult =
        run("--test-write", image, device)

    fun write(image: File, device: String): PcmHammerResult =
        run("--write", image, device)

    fun identify(device: String): PcmHammerResult =
        run("--identify-pcm", null, device)

    fun testRead(device: String): PcmHammerResult =
        run("--test-read", null, device)

    private fun run(operation: String, image: File?, device: String): PcmHammerResult {
        val cli = executable ?: error(
            "The official PCM Hammer CLI is not installed. Build the packaged Linux runtime " +
                "or set PCM_HAMMER_CLI to its absolute path."
        )

        val command = mutableListOf(cli.absolutePath, operation)
        image?.let { command += it.absolutePath }
        command += listOf("--device", device)

        val kernelDir = File(cli.parentFile, "kernels")
        if (kernelDir.isDirectory) {
            command += listOf("--kernel-dir", kernelDir.absolutePath)
        }

        log("[PCM HAMMER] Starting ${operation.removePrefix("--")} using ${cli.name}.")
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { log("[PCM HAMMER] $it") }
        }

        val exitCode = process.waitFor()
        log("[PCM HAMMER] Process exited with code $exitCode.")
        return PcmHammerResult(exitCode)
    }

    private fun locateExecutable(): File? {
        val configured = System.getenv("PCM_HAMMER_CLI")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
        if (configured?.isFile == true) return configured

        val javaHome = File(System.getProperty("java.home"))
        val packaged = javaHome.parentFile?.parentFile
            ?.resolve("lib/app/pcmhammer/pcmhammer-cli")
        if (packaged?.isFile == true) return packaged

        val executableName = if (platformName == "Windows") "pcmhammer-cli.exe" else "pcmhammer-cli"
        return System.getenv("PATH")
            ?.split(File.pathSeparator)
            ?.asSequence()
            ?.map { File(it, executableName) }
            ?.firstOrNull { it.isFile && it.canExecute() }
    }
}
