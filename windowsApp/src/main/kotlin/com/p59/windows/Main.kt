package com.p59.windows

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.text.DecimalFormat
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.WindowConstants
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.table.DefaultTableModel

private val Background = Color(0x0F, 0x11, 0x15)
private val Surface = Color(0x1B, 0x1E, 0x24)
private val Border = Color(0x2C, 0x31, 0x3C)
private val TextPrimary = Color(0xF2, 0xF5, 0xF7)
private val TextSecondary = Color(0xAC, 0xB3, 0xBF)
private val Accent = Color(0x00, 0xD1, 0xFF)
private val Success = Color(0x00, 0xD8, 0x7A)
private val Warning = Color(0xFF, 0xA3, 0x1A)
private val ErrorColor = Color(0xFF, 0x55, 0x66)
private val platformName =
    if (System.getProperty("os.name").startsWith("Linux", ignoreCase = true)) "Linux" else "Windows"

fun main() {
    SwingUtilities.invokeLater {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName())
        } catch (_: Exception) {
        }

        P59WindowsApp().show()
    }
}

private class P59WindowsApp {
    private val frame = JFrame("OBDX Pro P59 Tuner — $platformName")
    private val executor = Executors.newCachedThreadPool()
    private val serial = SerialConnection()
    private val ioLock = Any()
    private val liveRunning = AtomicBoolean(false)

    @Volatile
    private var elmClient: ElmLiveDataClient? = null

    @Volatile
    private var liveFuture: Future<*>? = null

    private val portCombo = JComboBox<SerialPortInfo>()
    private val baudField = JTextField("115200", 8)
    private val connectButton = JButton("Connect")
    private val disconnectButton = JButton("Disconnect")
    private val refreshPortsButton = JButton("Refresh ports")
    private val connectionStatus = JLabel("Disconnected")
    private val voltageStatus = JLabel("— V")

    private val startLoggingButton = JButton("Start live data")
    private val stopLoggingButton = JButton("Stop")
    private val readDtcButton = JButton("Read DTCs")
    private val clearDtcButton = JButton("Clear DTCs")
    private val dtcArea = JTextArea()

    private val readPcmButton = JButton("Read full 1 MiB PCM")
    private val writeCalibrationButton = JButton("Write calibration")
    private val writeFullButton = JButton("Write full PCM")
    private val readProgress = JProgressBar(0, 100)
    private val readStatus = JLabel("Ready")
    private val readAddress = JLabel("0x000000")

    private val terminal = JTextArea()
    private val telemetryModel = DefaultTableModel(
        arrayOf("Channel", "Value", "Unit"),
        0
    ).apply {
        addRow(arrayOf("Engine speed", "—", "RPM"))
        addRow(arrayOf("Vehicle speed", "—", "MPH"))
        addRow(arrayOf("MAP", "—", "kPa"))
        addRow(arrayOf("Coolant", "—", "°F"))
        addRow(arrayOf("Throttle", "—", "%"))
        addRow(arrayOf("MAF", "—", "g/s"))
        addRow(arrayOf("Spark", "—", "°"))
        addRow(arrayOf("STFT", "—", "%"))
        addRow(arrayOf("LTFT", "—", "%"))
        addRow(arrayOf("Commanded EQ", "—", "EQ"))
        addRow(arrayOf("Intake air", "—", "°F"))
        addRow(arrayOf("A/C input", "—", "V"))
        addRow(arrayOf("Wideband", "—", "AFR"))
        addRow(arrayOf("Adapter voltage", "—", "V"))
    }

    private val telemetryTable = object : JTable(telemetryModel) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }

    fun show() {
        configureFrame()
        refreshPorts()
        updateConnectedUi(false)
        log("$platformName runtime initialized.")
        log("PCM write operations are safety-locked; verified read and live data are available.")
        frame.isVisible = true
    }

    private fun configureFrame() {
        frame.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        frame.minimumSize = Dimension(1050, 720)
        frame.size = Dimension(1280, 820)
        frame.setLocationRelativeTo(null)
        frame.contentPane.background = Background
        frame.layout = BorderLayout(8, 8)

        val header = JPanel(BorderLayout()).apply {
            background = Surface
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Border),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
            )
            add(
                JLabel("OBDX PRO P59 TUNER").apply {
                    foreground = TextPrimary
                    font = Font(Font.SANS_SERIF, Font.BOLD, 20)
                },
                BorderLayout.WEST
            )
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 14, 0)).apply {
                    background = Surface
                    add(connectionStatus)
                    add(voltageStatus)
                },
                BorderLayout.EAST
            )
        }

        val tabs = JTabbedPane().apply {
            background = Background
            foreground = TextPrimary
            addTab("Connection", createConnectionPanel())
            addTab("Live Data", createLiveDataPanel())
            addTab("Flasher", createFlasherPanel())
            addTab("Diagnostics", createDiagnosticsPanel())
            addTab("About", createAboutPanel())
        }

        terminal.apply {
            isEditable = false
            background = Color(0x08, 0x0A, 0x0D)
            foreground = Color(0x8D, 0xFF, 0xBC)
            caretColor = Accent
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            lineWrap = true
            wrapStyleWord = true
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        }

        val split = JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            tabs,
            JScrollPane(terminal)
        ).apply {
            resizeWeight = 0.72
            dividerLocation = 560
            border = BorderFactory.createEmptyBorder()
            background = Background
        }

        frame.add(header, BorderLayout.NORTH)
        frame.add(split, BorderLayout.CENTER)

        styleStatusLabel(connectionStatus, ErrorColor)
        styleStatusLabel(voltageStatus, TextSecondary)
        styleButton(connectButton, Accent)
        styleButton(disconnectButton, ErrorColor)
        styleButton(refreshPortsButton, TextSecondary)
        styleButton(startLoggingButton, Success)
        styleButton(stopLoggingButton, Warning)
        styleButton(readDtcButton, Accent)
        styleButton(clearDtcButton, Warning)
        styleButton(readPcmButton, Accent)
        styleButton(writeCalibrationButton, ErrorColor)
        styleButton(writeFullButton, ErrorColor)

        connectButton.addActionListener { connect() }
        disconnectButton.addActionListener { disconnect() }
        refreshPortsButton.addActionListener { refreshPorts() }
        startLoggingButton.addActionListener { startLiveLogging() }
        stopLoggingButton.addActionListener { stopLiveLogging() }
        readDtcButton.addActionListener { readDtcs() }
        clearDtcButton.addActionListener { clearDtcs() }
        readPcmButton.addActionListener { readPcm() }

        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(event: WindowEvent?) {
                stopLiveLogging()
                serial.close()
                executor.shutdownNow()
                frame.dispose()
            }
        })
    }

    private fun createConnectionPanel(): JPanel =
        panelWithPadding().apply {
            layout = GridBagLayout()
            val constraints = GridBagConstraints().apply {
                insets = Insets(8, 8, 8, 8)
                anchor = GridBagConstraints.WEST
                fill = GridBagConstraints.HORIZONTAL
            }

            addFormRow(this, constraints, 0, "Serial port", portCombo)
            addFormRow(this, constraints, 1, "Baud rate", baudField)

            constraints.gridx = 0
            constraints.gridy = 2
            constraints.gridwidth = 2
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    background = Background
                    add(refreshPortsButton)
                    add(connectButton)
                    add(disconnectButton)
                },
                constraints
            )

            constraints.gridy = 3
            constraints.weighty = 1.0
            constraints.anchor = GridBagConstraints.NORTHWEST
            add(
                infoCard(
                    "$platformName connection path",
                    "Use the OBDX Pro USB connection or a Bluetooth virtual serial port. " +
                        "The desktop runtime talks to the adapter through its serial interface. " +
                        "Close PCM Hammer, terminal programs, and other software that may already have the port open."
                ),
                constraints
            )
        }

    private fun createLiveDataPanel(): JPanel =
        panelWithPadding().apply {
            layout = BorderLayout(8, 8)

            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    background = Background
                    add(startLoggingButton)
                    add(stopLoggingButton)
                },
                BorderLayout.NORTH
            )

            telemetryTable.apply {
                background = Surface
                foreground = TextPrimary
                gridColor = Border
                selectionBackground = Color(0x14, 0x4D, 0x63)
                selectionForeground = TextPrimary
                rowHeight = 28
                tableHeader.background = Color(0x22, 0x27, 0x30)
                tableHeader.foreground = TextPrimary
            }

            add(JScrollPane(telemetryTable), BorderLayout.CENTER)
        }

    private fun createFlasherPanel(): JPanel =
        panelWithPadding().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)

            add(
                infoCard(
                    "P59 full PCM reader",
                    "Reads the complete 1 MiB image with the verified OBDX DVI/P01 kernel path. " +
                        "Each 4096-byte block is checked, the flash chip is validated, and the final image is compared against a PCM-side CRC32 before it is saved."
                )
            )
            add(Box.createVerticalStrut(12))

            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    background = Background
                    add(readPcmButton)
                    add(writeCalibrationButton)
                    add(writeFullButton)
                }
            )

            writeCalibrationButton.isEnabled = false
            writeFullButton.isEnabled = false
            writeCalibrationButton.toolTipText =
                "Disabled until the complete erase/program/recovery verifier is hardware-certified."
            writeFullButton.toolTipText =
                "Disabled until the complete erase/program/recovery verifier is hardware-certified."

            add(Box.createVerticalStrut(16))
            add(readProgress.apply {
                isStringPainted = true
                foreground = Accent
                background = Surface
                maximumSize = Dimension(Int.MAX_VALUE, 28)
            })
            add(Box.createVerticalStrut(8))
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 20, 0)).apply {
                    background = Background
                    add(readStatus)
                    add(readAddress)
                }
            )
            add(Box.createVerticalStrut(14))
            add(
                infoCard(
                    "Write safety lock",
                    "$platformName compatibility does not enable PCM writing. A native desktop package is not evidence that erase, programming, voltage interlocks, retries, and recovery behavior are safe on physical hardware."
                )
            )
        }

    private fun createDiagnosticsPanel(): JPanel =
        panelWithPadding().apply {
            layout = BorderLayout(8, 8)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                    background = Background
                    add(readDtcButton)
                    add(clearDtcButton)
                },
                BorderLayout.NORTH
            )

            dtcArea.apply {
                isEditable = false
                background = Surface
                foreground = TextPrimary
                font = Font(Font.MONOSPACED, Font.PLAIN, 14)
                border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            }
            add(JScrollPane(dtcArea), BorderLayout.CENTER)
        }

    private fun createAboutPanel(): JPanel =
        panelWithPadding().apply {
            layout = BorderLayout()
            add(
                infoCard(
                    "$platformName runtime",
                    "This desktop module is a JVM application packaged with its own Java runtime. " +
                        "It supports Windows 10/11 and Linux x64 serial ports, live OBD-II data, DTC operations, and verified read-only P59 extraction. " +
                        "The Android APK remains a separate build and is unchanged."
                ),
                BorderLayout.NORTH
            )
        }

    private fun connect() {
        val selected = portCombo.selectedItem as? SerialPortInfo
        if (selected == null) {
            showError("No serial port is selected.")
            return
        }

        val baud = baudField.text.trim().toIntOrNull()
        if (baud == null || baud !in 9600..3_000_000) {
            showError("Enter a valid baud rate.")
            return
        }

        updateBusy("Connecting to ${selected.systemName}...")

        executor.submit {
            try {
                serial.connect(selected.systemName, baud)

                val client = ElmLiveDataClient(
                    input = serial.inputStream(),
                    output = serial.outputStream(),
                    ioLock = ioLock,
                    log = ::log
                )
                client.initialize()
                elmClient = client

                val volts = client.readVoltage()
                onUi {
                    connectionStatus.text = "Connected: ${selected.systemName}"
                    connectionStatus.foreground = Success
                    voltageStatus.text = volts?.let { "${format(it)} V" } ?: "— V"
                    updateConnectedUi(true)
                    readStatus.text = "Ready"
                }
            } catch (error: Throwable) {
                elmClient = null
                serial.close()
                log("[ERROR] ${error.message ?: error.javaClass.simpleName}")
                onUi {
                    connectionStatus.text = "Disconnected"
                    connectionStatus.foreground = ErrorColor
                    voltageStatus.text = "— V"
                    updateConnectedUi(false)
                    showError(error.message ?: "Connection failed.")
                }
            }
        }
    }

    private fun disconnect() {
        stopLiveLogging()
        elmClient = null
        serial.close()
        connectionStatus.text = "Disconnected"
        connectionStatus.foreground = ErrorColor
        voltageStatus.text = "— V"
        updateConnectedUi(false)
        log("Serial connection closed.")
    }

    private fun refreshPorts() {
        val previous = (portCombo.selectedItem as? SerialPortInfo)?.systemName
        val ports = serial.listPorts()

        portCombo.removeAllItems()
        ports.forEach(portCombo::addItem)

        if (previous != null) {
            for (index in 0 until portCombo.itemCount) {
                if (portCombo.getItemAt(index).systemName == previous) {
                    portCombo.selectedIndex = index
                    break
                }
            }
        }

        if (ports.isEmpty()) {
            log("No serial ports were detected.")
        }
    }

    private fun startLiveLogging() {
        val client = elmClient
        if (client == null || !serial.isOpen) {
            showError("Connect to the OBDX Pro first.")
            return
        }

        if (!liveRunning.compareAndSet(false, true)) return

        startLoggingButton.isEnabled = false
        stopLoggingButton.isEnabled = true
        log("Live-data polling started.")

        liveFuture = executor.submit {
            var consecutiveFailures = 0

            while (liveRunning.get() && serial.isOpen) {
                try {
                    val snapshot = client.pollSnapshot()
                    consecutiveFailures = 0
                    onUi { updateTelemetry(snapshot) }
                } catch (error: Throwable) {
                    consecutiveFailures++
                    log(
                        "[LIVE ERROR $consecutiveFailures] " +
                            (error.message ?: error.javaClass.simpleName)
                    )

                    if (consecutiveFailures >= 8) {
                        liveRunning.set(false)
                        onUi {
                            showError(
                                "Live data stopped after repeated communication failures."
                            )
                        }
                        break
                    }
                }

                Thread.sleep(80)
            }

            onUi {
                startLoggingButton.isEnabled = serial.isOpen
                stopLoggingButton.isEnabled = false
            }
            log("Live-data polling stopped.")
        }
    }

    private fun stopLiveLogging() {
        liveRunning.set(false)
        liveFuture?.cancel(false)
        liveFuture = null
        startLoggingButton.isEnabled = serial.isOpen
        stopLoggingButton.isEnabled = false
    }

    private fun readPcm() {
        if (!serial.isOpen) {
            showError("Connect to the OBDX Pro first.")
            return
        }

        val chooser = JFileChooser().apply {
            dialogTitle = "Save verified P59 PCM image"
            fileFilter = FileNameExtensionFilter("P59 binary image (*.bin)", "bin")
            selectedFile = File(
                System.getProperty("user.home"),
                "P59_PCM_Read_${System.currentTimeMillis()}.bin"
            )
        }

        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return

        var destination = chooser.selectedFile
        if (!destination.name.endsWith(".bin", ignoreCase = true)) {
            destination = File(destination.parentFile, destination.name + ".bin")
        }

        stopLiveLogging()
        readPcmButton.isEnabled = false
        updateBusy("Preparing verified P59 read...")
        readProgress.value = 0
        readProgress.string = "0%"
        val saveFile = destination

        executor.submit {
            try {
                val service = P59DesktopReadService(
                    input = serial.inputStream(),
                    output = serial.outputStream(),
                    ioLock = ioLock,
                    log = ::log,
                    progress = { bytes, total, address ->
                        val percent = ((bytes.toDouble() / total) * 100).toInt()
                        onUi {
                            readProgress.value = percent
                            readProgress.string = "$percent%"
                            readAddress.text = "0x%06X".format(address)
                            readStatus.text =
                                "Reading ${bytes / 1024} / ${total / 1024} KiB"
                        }
                    }
                )

                val result = service.readTo(saveFile)

                // Restore normal ELM diagnostics after DVI mode.
                val restoredClient = ElmLiveDataClient(
                    serial.inputStream(),
                    serial.outputStream(),
                    ioLock,
                    ::log
                )
                restoredClient.initialize()
                elmClient = restoredClient

                onUi {
                    readProgress.value = 100
                    readProgress.string = "Verified"
                    readStatus.text = "Saved ${result.bytes / 1024} KiB"
                    readAddress.text = result.file.absolutePath
                    readPcmButton.isEnabled = true
                    updateConnectedUi(true)
                    JOptionPane.showMessageDialog(
                        frame,
                        "Verified P59 read saved.\n\n" +
                            "${result.file.absolutePath}\n\n" +
                            "SHA-256:\n${result.sha256}",
                        "PCM read complete",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            } catch (error: Throwable) {
                log("[P59 READ ERROR] ${error.message ?: error.javaClass.simpleName}")
                onUi {
                    readStatus.text = "Read failed"
                    readProgress.string = "Failed"
                    readPcmButton.isEnabled = serial.isOpen
                    showError(error.message ?: "PCM read failed.")
                }

                try {
                    val restoredClient = ElmLiveDataClient(
                        serial.inputStream(),
                        serial.outputStream(),
                        ioLock,
                        ::log
                    )
                    restoredClient.initialize()
                    elmClient = restoredClient
                } catch (_: Throwable) {
                    elmClient = null
                    onUi { updateConnectedUi(false) }
                }
            }
        }
    }

    private fun readDtcs() {
        val client = elmClient ?: run {
            showError("Connect to the OBDX Pro first.")
            return
        }

        executor.submit {
            try {
                val codes = client.readActiveDtcs()
                onUi {
                    dtcArea.text = if (codes.isEmpty()) {
                        "No active diagnostic trouble codes reported."
                    } else {
                        codes.joinToString("\n")
                    }
                }
            } catch (error: Throwable) {
                onUi { showError(error.message ?: "DTC read failed.") }
            }
        }
    }

    private fun clearDtcs() {
        val client = elmClient ?: run {
            showError("Connect to the OBDX Pro first.")
            return
        }

        val answer = JOptionPane.showConfirmDialog(
            frame,
            "Clear diagnostic trouble codes and related stored information?",
            "Confirm DTC clear",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        )

        if (answer != JOptionPane.YES_OPTION) return

        executor.submit {
            try {
                val cleared = client.clearDtcs()
                onUi {
                    if (cleared) {
                        dtcArea.text = "DTC clear acknowledged."
                    } else {
                        showError("The PCM did not acknowledge the DTC clear command.")
                    }
                }
            } catch (error: Throwable) {
                onUi { showError(error.message ?: "DTC clear failed.") }
            }
        }
    }

    private fun updateTelemetry(value: TelemetrySnapshot) {
        setValue(0, value.rpm?.toString())
        setValue(1, value.mph?.toString())
        setValue(2, value.mapKpa?.let(::format))
        setValue(3, value.coolantF?.toString())
        setValue(4, value.throttlePercent?.toString())
        setValue(5, value.mafGps?.let(::format))
        setValue(6, value.sparkDegrees?.let(::format))
        setValue(7, value.stftPercent?.let(::format))
        setValue(8, value.ltftPercent?.let(::format))
        setValue(9, value.commandedEq?.let(::format))
        setValue(10, value.intakeAirF?.toString())
        setValue(11, value.acPressureVolts?.let(::format))
        setValue(12, value.widebandAfr?.let(::format))
        setValue(13, value.adapterVoltage?.let(::format))

        value.adapterVoltage?.let {
            voltageStatus.text = "${format(it)} V"
            voltageStatus.foreground =
                if (it >= 11.8f) Success else Warning
        }
    }

    private fun setValue(row: Int, value: String?) {
        if (value != null) {
            telemetryModel.setValueAt(value, row, 1)
        }
    }

    private fun updateConnectedUi(connected: Boolean) {
        connectButton.isEnabled = !connected
        disconnectButton.isEnabled = connected
        portCombo.isEnabled = !connected
        baudField.isEnabled = !connected
        refreshPortsButton.isEnabled = !connected
        startLoggingButton.isEnabled = connected && !liveRunning.get()
        stopLoggingButton.isEnabled = connected && liveRunning.get()
        readPcmButton.isEnabled = connected
        readDtcButton.isEnabled = connected
        clearDtcButton.isEnabled = connected
    }

    private fun updateBusy(message: String) {
        readStatus.text = message
        log(message)
    }

    private fun showError(message: String) {
        JOptionPane.showMessageDialog(
            frame,
            message,
            "OBDX Pro P59 Tuner",
            JOptionPane.ERROR_MESSAGE
        )
    }

    private fun log(message: String) {
        onUi {
            terminal.append("[${System.currentTimeMillis() % 100000}] $message\n")
            terminal.caretPosition = terminal.document.length
        }
    }

    private fun onUi(action: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            action()
        } else {
            SwingUtilities.invokeLater(action)
        }
    }

    private fun panelWithPadding(): JPanel =
        JPanel().apply {
            background = Background
            border = BorderFactory.createEmptyBorder(14, 14, 14, 14)
        }

    private fun infoCard(title: String, body: String): JPanel =
        JPanel().apply {
            background = Surface
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Border),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)
            )
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(
                JLabel(title).apply {
                    foreground = TextPrimary
                    font = Font(Font.SANS_SERIF, Font.BOLD, 16)
                    alignmentX = 0f
                }
            )
            add(Box.createVerticalStrut(8))
            add(
                JTextArea(body).apply {
                    isEditable = false
                    isOpaque = false
                    foreground = TextSecondary
                    font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
                    lineWrap = true
                    wrapStyleWord = true
                    alignmentX = 0f
                    maximumSize = Dimension(Int.MAX_VALUE, 100)
                }
            )
        }

    private fun addFormRow(
        panel: JPanel,
        constraints: GridBagConstraints,
        row: Int,
        title: String,
        component: java.awt.Component
    ) {
        constraints.gridy = row
        constraints.gridwidth = 1
        constraints.weightx = 0.0
        constraints.gridx = 0
        panel.add(
            JLabel(title).apply {
                foreground = TextSecondary
                font = Font(Font.SANS_SERIF, Font.BOLD, 13)
            },
            constraints
        )

        constraints.gridx = 1
        constraints.weightx = 1.0
        panel.add(component, constraints)
    }

    private fun styleButton(button: JButton, accent: Color) {
        button.background = Surface
        button.foreground = accent
        button.isFocusPainted = false
        button.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(accent),
            BorderFactory.createEmptyBorder(7, 12, 7, 12)
        )
    }

    private fun styleStatusLabel(label: JLabel, color: Color) {
        label.foreground = color
        label.font = Font(Font.MONOSPACED, Font.BOLD, 12)
    }

    private fun format(value: Float): String =
        DecimalFormat("0.00").format(value)
}
