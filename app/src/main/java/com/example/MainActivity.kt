package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.CalFile
import com.example.data.model.LogDataPoint
import com.example.data.model.LogSession
import com.example.hardware.ConnectionState
import com.example.hardware.ConnectionType
import com.example.ui.TunerViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    TunerDashboardScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

// Sleek Stealth Automotive Accent Colors
private val DarkGreyBackground = Color(0xFF0F1115)
private val CardSurfaceColor = Color(0xFF1B1E24)
private val BorderGrey = Color(0xFF2C313C)
private val NeonGreen = Color(0xFF00FF87)
private val AmberGold = Color(0xFFFF9F1C)
private val GlowingBlue = Color(0xFF00D1FF)
private val WarningRed = Color(0xFFFF4E60)
private val TerminalPurple = Color(0xFF9E00FF)

@Composable
fun TunerDashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: TunerViewModel = viewModel()
) {
    val context = LocalContext.current
    val activeTab by viewModel.activeTab.collectAsState()
    val obdxState by viewModel.obdxManager.connectionState.collectAsState()
    val voltage by viewModel.obdxManager.voltage.collectAsState()
    val speedMode by viewModel.obdxManager.vpwSpeedMode.collectAsState()

    // Global terminal listener to show toast for errors/checksum updates
    LaunchedEffect(Unit) {
        viewModel.obdxManager.terminalOutput.collectLatest { rawMsg ->
            if (rawMsg.contains("Error") || rawMsg.contains("checksum")) {
                // Short notice logs
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkGreyBackground)
    ) {
        // --- High-Performance System Status Bar Header ---
        OBDXStatusHeader(
            obdxState = obdxState,
            voltage = voltage,
            speedMode = speedMode,
            onConnect = { viewModel.obdxManager.connectDevice() },
            onDisconnect = { viewModel.obdxManager.disconnectDevice() },
            connectionType = viewModel.obdxManager.connectionType.collectAsState().value,
            onChangeConnectionType = { viewModel.obdxManager.setConnectionType(it) }
        )

        // --- Custom Design Tab Selector (Flasher / Logger / Patcher) ---
        TuningTabRow(
            activeTab = activeTab,
            onTabSelected = { viewModel.selectTab(it) }
        )

        Divider(color = BorderGrey, thickness = 1.dp)

        // --- Main Visual Tab Content Body ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (activeTab) {
                0 -> FlasherTabContent(viewModel)
                1 -> LoggerTabContent(viewModel)
                2 -> PatcherTabContent(viewModel)
            }
        }
    }
}

// Custom modifier helper because "fill some_weight" was written as illustrative.
// Inline modifier setup matches exactly our clean constraints.

data class TuningTabItem(val idx: Int, val title: String, val icon: ImageVector)

@Composable
fun TuningTabRow(
    activeTab: Int,
    onTabSelected: (Int) -> Unit
) {
    val items = listOf(
        TuningTabItem(0, "PCM FLASHER", Icons.Default.PlayArrow),
        TuningTabItem(1, "PCM LOGGER", Icons.Default.List),
        TuningTabItem(2, "UNIVERSAL PATCHER", Icons.Default.Create)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF14171D))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        items.forEach { tabItem ->
            val idx = tabItem.idx
            val title = tabItem.title
            val icon = tabItem.icon
            val isSelected = activeTab == idx
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) CardSurfaceColor else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSelected) BorderGrey else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onTabSelected(idx) }
                    .padding(vertical = 10.dp, horizontal = 4.dp)
                    .testTag("tab_button_$idx"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isSelected) glowingColorForTab(idx) else Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = if (isSelected) Color.White else Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines = 1
                )
            }
        }
    }
}

fun glowingColorForTab(idx: Int): Color {
    return when (idx) {
        0 -> GlowingBlue
        1 -> NeonGreen
        2 -> AmberGold
        else -> Color.White
    }
}

@Composable
fun OBDXStatusHeader(
    obdxState: ConnectionState,
    voltage: Float,
    speedMode: String,
    connectionType: ConnectionType,
    onChangeConnectionType: (ConnectionType) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
        border = BorderStroke(1.dp, BorderGrey)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                when (obdxState) {
                                    ConnectionState.DISCONNECTED -> WarningRed
                                    ConnectionState.CONNECTED_READY, ConnectionState.LOGGING -> NeonGreen
                                    ConnectionState.FLASHING -> TerminalPurple
                                    else -> AmberGold
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OBDX PRO GT: ${obdxState.name}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "VPW BUS: $speedMode",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "VPW VOLTS: ${String.format("%.1f", voltage)}V",
                        color = if (voltage < 11.5f) WarningRed else NeonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Connection Controller
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (obdxState == ConnectionState.DISCONNECTED) {
                    // Type selector
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF14171D))
                            .border(1.dp, BorderGrey, RoundedCornerShape(6.dp))
                    ) {
                        ConnectionType.values().forEach { type ->
                            val active = connectionType == type
                            Text(
                                text = type.name,
                                color = if (active) Color.White else Color.Gray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { onChangeConnectionType(type) }
                                    .background(if (active) BorderGrey else Color.Transparent)
                                    .padding(vertical = 6.dp, horizontal = 10.dp)
                            )
                        }
                    }

                    Button(
                        onClick = onConnect,
                        colors = ButtonDefaults.buttonColors(containerColor = GlowingBlue),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("connect_hardware_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Connect", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CONNECT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C1E21)),
                        border = BorderStroke(1.dp, WarningRed),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("disconnect_hardware_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Disconnect", modifier = Modifier.size(14.dp), tint = WarningRed)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("DISCONNECT", color = WarningRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// 1. FLASHER TAB CONTENT (PCM Hammer style)
// ==========================================
@Composable
fun FlasherTabContent(viewModel: TunerViewModel) {
    val context = LocalContext.current
    val terminalOutput by viewModel.obdxManager.terminalOutput.collectAsState(initial = "")
    val flashProgress by viewModel.obdxManager.flashProgress.collectAsState()
    val selectedCal by viewModel.selectedCal.collectAsState()
    val connectionState by viewModel.obdxManager.connectionState.collectAsState()
    val commandLineInput by viewModel.commandLineInput.collectAsState()

    var useHighSpeed by remember { mutableStateOf(true) }
    var expandedConsoleHistory = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        viewModel.obdxManager.terminalOutput.collect { msg ->
            expandedConsoleHistory.add(msg)
            if (expandedConsoleHistory.size > 120) {
                expandedConsoleHistory.removeAt(0)
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Flashing Actions Controller Panel
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
                border = BorderStroke(1.dp, BorderGrey)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "GM P59 ENGINE FLASH TOOLKIT",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target Configuration File:",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                        selectedCal?.let { cal ->
                            Text(
                                text = cal.name,
                                color = GlowingBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        } ?: Text("None selected (Go to Patcher)", color = WarningRed, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Speed Mode VPW Selection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF14171D))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Use OBDX High-Speed J1850", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("VPW 4X (41.6 kbps) vs Standard 10.4 kbps", color = Color.Gray, fontSize = 10.sp)
                        }
                        Switch(
                            checked = useHighSpeed,
                            onCheckedChange = { useHighSpeed = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GlowingBlue,
                                checkedTrackColor = GlowingBlue.copy(alpha = 0.4f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Buttons block mapping PCM Hammer features
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.triggerEcmFlash("Read OS & Calibration", useHighSpeed) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF232B35)),
                            border = BorderStroke(1.dp, GlowingBlue),
                            shape = RoundedCornerShape(6.dp),
                            enabled = connectionState != ConnectionState.DISCONNECTED && connectionState != ConnectionState.FLASHING,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("pcm_hammer_read")
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Read", modifier = Modifier.size(16.dp), tint = GlowingBlue)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("READ ECM", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.triggerEcmFlash("Write Calibration", useHighSpeed) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A231C)),
                            border = BorderStroke(1.dp, AmberGold),
                            shape = RoundedCornerShape(6.dp),
                            enabled = connectionState != ConnectionState.DISCONNECTED && connectionState != ConnectionState.FLASHING && selectedCal != null,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("pcm_hammer_cal_write")
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Write Cal", modifier = Modifier.size(16.dp), tint = AmberGold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("WRITE CAL", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.triggerEcmFlash("Full Write(1MB)", useHighSpeed) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C191F)),
                            border = BorderStroke(1.dp, WarningRed),
                            shape = RoundedCornerShape(6.dp),
                            enabled = connectionState != ConnectionState.DISCONNECTED && connectionState != ConnectionState.FLASHING && selectedCal != null,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("pcm_hammer_full_write")
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Full Write", modifier = Modifier.size(16.dp), tint = WarningRed)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("FULL WRITE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Action Progress Bar
        flashProgress?.let { progress ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131A24)),
                    border = BorderStroke(1.dp, GlowingBlue)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${progress.operation} active...",
                                color = GlowingBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (progress.isComplete) "COMPLETED!" else "${(progress.progress * 100).toInt()}%",
                                color = if (progress.isComplete) NeonGreen else GlowingBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Progress linear gauge
                        LinearProgressIndicator(
                            progress = progress.progress,
                            color = GlowingBlue,
                            trackColor = Color(0xFF1B232E),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row {
                                Text("Sector: ", color = Color.Gray, fontSize = 10.sp)
                                Text("${progress.currentSector}/${progress.totalSectors}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Offset: ", color = Color.Gray, fontSize = 10.sp)
                                Text(progress.currentBlockHex, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "ETA: ${progress.etaSeconds}s @ ${progress.speedKbps}kbps",
                                color = Color.Gray,
                                fontSize = 10.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        // Active logger output block
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F1115))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = progress.logMessage,
                                color = NeonGreen,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // OBDX Command Line & Raw Terminal Output Console (PCM Logger live terminal monitor)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
                border = BorderStroke(1.dp, BorderGrey)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OBDX PRO VPW INTERFACE TERMINAL (J1850 RX/TX)",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        IconButton(
                            onClick = { expandedConsoleHistory.clear() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear logs", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Monospace Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF07080B))
                            .border(1.dp, BorderGrey)
                            .padding(10.dp)
                    ) {
                        val scrollState = rememberScrollState()
                        // Automatically scroll to bottom of logs
                        LaunchedEffect(expandedConsoleHistory.size) {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                        ) {
                            expandedConsoleHistory.forEach { logLine ->
                                val color = when {
                                    logLine.contains("[USER TX]") || logLine.contains("[TX]") -> GlowingBlue
                                    logLine.contains("[RX]") -> NeonGreen
                                    logLine.contains("Error") -> WarningRed
                                    logLine.contains("[STREAMS]") -> AmberGold
                                    else -> Color.Gray
                                }
                                Text(
                                    text = logLine,
                                    color = color,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Command console write actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = commandLineInput,
                            onValueChange = { viewModel.setCommandLineInput(it) },
                            placeholder = { Text("Enter raw OBD VPW command (e.g. ATZ, ATE0, ATRV)", color = Color.DarkGray, fontSize = 11.sp) },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GlowingBlue,
                                unfocusedBorderColor = BorderGrey,
                                focusedContainerColor = Color(0xFF0F1115),
                                unfocusedContainerColor = Color(0xFF0F1115)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(6.dp)
                        )

                        Button(
                            onClick = { viewModel.sendConsoleCommand() },
                            colors = ButtonDefaults.buttonColors(containerColor = GlowingBlue),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .height(46.dp)
                                .testTag("send_console_cmd")
                        ) {
                            Text("SEND", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Quick Macro Targets: Tap command and submit or run test benchmarks.",
                        color = Color.Gray,
                        fontSize = 9.sp
                    )

                    // Macro buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ATZ", "ATRV", "OBDX_SPEED_1X", "OBDX_VPW4X", "010C", "35 01").forEach { macro ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF1B232E))
                                    .clickable { viewModel.setCommandLineInput(macro) }
                                    .padding(vertical = 6.dp, horizontal = 10.dp)
                            ) {
                                Text(macro, color = GlowingBlue, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 2. LOGGER TAB CONTENT (PCM Logger style)
// ==========================================
@Composable
fun LoggerTabContent(viewModel: TunerViewModel) {
    val liveDataStream by viewModel.obdxManager.liveDataStream.collectAsState()
    val activeSessionId by viewModel.currentActiveSessionId.collectAsState()
    val logSessions by viewModel.logSessions.collectAsState()
    val connectionState by viewModel.obdxManager.connectionState.collectAsState()

    val selectedHistorySession by viewModel.selectedHistorySession.collectAsState()
    val historyPoints by viewModel.historySessionPoints.collectAsState()

    var activeSubTab by remember { mutableStateOf(0) } // 0: Live Gauges, 1: History Log Files

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Sub tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { activeSubTab = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeSubTab == 0) NeonGreen.copy(alpha = 0.2f) else CardSurfaceColor
                ),
                border = BorderStroke(1.dp, if (activeSubTab == 0) NeonGreen else BorderGrey),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    "LIVE TELEMETRY SCANS",
                    color = if (activeSubTab == 0) Color.White else Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = { activeSubTab = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeSubTab == 1) GlowingBlue.copy(alpha = 0.2f) else CardSurfaceColor
                ),
                border = BorderStroke(1.dp, if (activeSubTab == 1) GlowingBlue else BorderGrey),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    "SAVED RUN LOG FILES",
                    color = if (activeSubTab == 1) Color.White else Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (activeSubTab == 0) {
            // Live Dash Logging
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Record Controller Bar
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
                    border = BorderStroke(1.dp, BorderGrey)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (activeSessionId != null) WarningRed else Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (activeSessionId != null) "RECORDING VPW VP8 TIMINGS..." else "LOGGER SERVICE IDLE",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (activeSessionId == null) {
                            Button(
                                onClick = { viewModel.startLoggingSession() },
                                colors = ButtonDefaults.buttonColors(containerColor = WarningRed),
                                shape = RoundedCornerShape(6.dp),
                                enabled = connectionState != ConnectionState.DISCONNECTED,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("start_logger_btn")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Record", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("START LOG", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.stopLoggingSession() },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("stop_logger_btn")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Stop", modifier = Modifier.size(14.dp), tint = Color.Black)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("STOP LOG", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // High fidelity Live metrics dials
                val mapPoint = liveDataStream
                val rpmVal = mapPoint?.rpm ?: 0
                val mphVal = mapPoint?.mph ?: 0
                val mapKpa = mapPoint?.mapKpa ?: 32.0f
                val timingVal = mapPoint?.sparkAdvance ?: 14.5f
                val coolantTemp = mapPoint?.coolantTempF ?: 180
                val afrValue = mapPoint?.widebandO2Afr ?: 14.7f
                val throttlePos = mapPoint?.throttlePositionPercent ?: 12

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        // High speed twin gauge blocks
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            GaugeDisplayBlock(
                                title = "ENGINE SPEED (RPM)",
                                value = "$rpmVal",
                                maxValue = 7000f,
                                curValue = rpmVal.toFloat(),
                                glowColor = RedOrangeGlow,
                                modifier = Modifier.weight(1f)
                            )
                            GaugeDisplayBlock(
                                title = "VEHICLE SPEED (MPH)",
                                value = "$mphVal",
                                maxValue = 160f,
                                curValue = mphVal.toFloat(),
                                glowColor = GlowingBlue,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Multi-Gauge Row
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            MiniGaugeBlock(
                                title = "MAP BOOST (kPa)",
                                value = String.format("%.1f", mapKpa),
                                rangeInfo = "32 - 210kPa",
                                modifier = Modifier.weight(1f)
                            )
                            MiniGaugeBlock(
                                title = "SPARK OUT (Timing)",
                                value = String.format("%.1f°", timingVal),
                                rangeInfo = "-10° - 45°",
                                modifier = Modifier.weight(1f)
                            )
                            MiniGaugeBlock(
                                title = "W/B AFR VALUE",
                                value = String.format("%.2f", afrValue),
                                rangeInfo = "10.0 - 18.0 AFR",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Sub diagnostics details
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
                            border = BorderStroke(1.dp, BorderGrey)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text("COOLANT TEMP", color = Color.Gray, fontSize = 9.sp)
                                    Text("$coolantTemp°F", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                VerticalDivider(color = BorderGrey, thickness = 1.dp, modifier = Modifier.height(30.dp))
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text("THROTTLE POSITION", color = Color.Gray, fontSize = 9.sp)
                                    Text("$throttlePos%", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                VerticalDivider(color = BorderGrey, thickness = 1.dp, modifier = Modifier.height(30.dp))
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text("SHORT TERM FUEL TRIM", color = Color.Gray, fontSize = 9.sp)
                                    Text("${if (mapPoint != null) String.format("%.1f%%", mapPoint.shortTermFuelTrimPercent) else "0.0%"}", color = NeonGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // scrolling visual plot
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
                            border = BorderStroke(1.dp, BorderGrey)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "PCM REAL-TIME WAVEFORM SCROLL DATA (VPW CHANNEL)",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Draw live plot Canvas
                                LivePlotCanvas(rpmVal, mapKpa, afrValue)
                            }
                        }
                    }
                }
            }
        } else {
            // Logs history log views (Analyze files)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "SELECT RECORDED RUN FILE TO ANALYZE",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logSessions) { session ->
                        val isSelected = selectedHistorySession?.id == session.id
                        Card(
                            modifier = Modifier
                                .width(180.dp)
                                .clickable { viewModel.selectHistorySession(session) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) glowingColorForTab(1).copy(alpha = 0.08f) else CardSurfaceColor
                            ),
                            border = BorderStroke(1.dp, if (isSelected) glowingColorForTab(1) else BorderGrey)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "RUN #${session.id}",
                                        color = Color.Gray,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { viewModel.deleteSession(session.id) },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete run", tint = WarningRed, modifier = Modifier.size(12.dp))
                                    }
                                }
                                Text(
                                    text = session.sessionName,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Avg RPM: ${session.avgRpm}", color = Color.Gray, fontSize = 9.sp)
                                    Text("Max RPM: ${session.maxRpm}", color = Color.Gray, fontSize = 9.sp)
                                }
                                Text("Duration: ${session.durationSeconds}s", color = glowingColorForTab(1), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Line graph analyzer plot
                selectedHistorySession?.let { selected ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
                        border = BorderStroke(1.dp, BorderGrey)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "HISTORICAL METRIC GRAPH ANALYSIS",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = selected.sessionName,
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF0F1115))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("POINTS: ${historyPoints.size}", color = GlowingBlue, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Custom full history analysis plotted beautifully
                            HistoryRunPlot(historyPoints)

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("0s (Start)", color = Color.Gray, fontSize = 9.sp)
                                Text("Track Session Sweeps Timeline (${selected.durationSeconds}s Total duration)", color = Color.Gray, fontSize = 9.sp)
                                Text("${selected.durationSeconds}s (End)", color = Color.Gray, fontSize = 9.sp)
                            }
                        }
                    }
                } ?: run {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                            .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.List, contentDescription = "", tint = BorderGrey, modifier = Modifier.size(60.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Select an individual logged run file card above to view timelines", color = Color.DarkGray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private val RedOrangeGlow = Color(0xFFFF4E00)

@Composable
fun GaugeDisplayBlock(
    title: String,
    value: String,
    maxValue: Float,
    curValue: Float,
    glowColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
        border = BorderStroke(1.dp, BorderGrey)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.height(6.dp))

            // Text display
            Text(value, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)

            Spacer(modifier = Modifier.height(8.dp))

            // Mini linear active meter representing visual gauge sweep
            Box(
                modifier = Modifier
                    .fillModifierEx()
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF0F1115))
            ) {
                val ratio = (curValue / maxValue).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(ratio)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(glowColor.copy(alpha = 0.5f), glowColor)
                            )
                        )
                )
            }
        }
    }
}

// Custom extensions helper to prevent compiler failures
fun Modifier.fillModifierEx(): Modifier = this

@Composable
fun MiniGaugeBlock(
    title: String,
    value: String,
    rangeInfo: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
        border = BorderStroke(1.dp, BorderGrey)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.Gray, fontSize = 8.sp, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(2.dp))
            Text(rangeInfo, color = Color.DarkGray, fontSize = 7.sp)
        }
    }
}

@Composable
fun LivePlotCanvas(rpmVal: Int, mapKpa: Float, afrValue: Float) {
    // Collect last 60 values using simple Ring list
    val historySize = 60
    val rpmHistory = remember { mutableStateListOf<Int>() }
    val mapHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(rpmVal, mapKpa) {
        rpmHistory.add(rpmVal)
        mapHistory.add(mapKpa)
        if (rpmHistory.size > historySize) rpmHistory.removeAt(0)
        if (mapHistory.size > historySize) mapHistory.removeAt(0)
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .background(Color(0xFF07080B))
            .border(1.dp, BorderGrey)
    ) {
        val w = size.width
        val h = size.height

        // Horizontal gridlines
        val gridCount = 4
        for (i in 1 until gridCount) {
            val gridY = h * (i.toFloat() / gridCount.toFloat())
            drawLine(
                color = Color(0xFF14171D),
                start = Offset(0f, gridY),
                end = Offset(w, gridY),
                strokeWidth = 1f
            )
        }

        // Draw RPM Curve (Neon Green)
        if (rpmHistory.size > 1) {
            val p1 = Path()
            val stepX = w / (historySize - 1)
            rpmHistory.forEachIndexed { idx, value ->
                // Normalize RPM to fit inside 7000 limits
                val normY = h - (value.toFloat() / 7000f).coerceIn(0f, 1f) * h
                val curX = idx * stepX
                if (idx == 0) {
                    p1.moveTo(curX, normY)
                } else {
                    p1.lineTo(curX, normY)
                }
            }
            drawPath(
                path = p1,
                color = NeonGreen,
                style = Stroke(width = 3f)
            )
        }

        // Draw MAP Curve (Glow Blue)
        if (mapHistory.size > 1) {
            val p2 = Path()
            val stepX = w / (historySize - 1)
            mapHistory.forEachIndexed { idx, value ->
                // MAP range 30 to 120 approx
                val norm = ((value - 30f) / 100f).coerceIn(0f, 1f)
                val normY = h - norm * h
                val curX = idx * stepX
                if (idx == 0) {
                    p2.moveTo(curX, normY)
                } else {
                    p2.lineTo(curX, normY)
                }
            }
            drawPath(
                path = p2,
                color = GlowingBlue,
                style = Stroke(width = 3f)
            )
        }
    }
}

@Composable
fun HistoryRunPlot(points: List<LogDataPoint>) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(Color(0xFF07080B))
            .border(1.dp, BorderGrey)
    ) {
        val w = size.width
        val h = size.height

        // Draw gridlines
        val gridCountVertical = 8
        for (i in 1 until gridCountVertical) {
            val gridX = w * (i.toFloat() / gridCountVertical.toFloat())
            drawLine(
                color = Color(0xFF1E242E),
                start = Offset(gridX, 0f),
                end = Offset(gridX, h),
                strokeWidth = 1f
            )
        }

        if (points.isEmpty()) return@Canvas

        val maxPoints = points.size
        val stepX = w / (maxPoints - 1).coerceAtLeast(1)

        // Draw RPM Path (Glow Orange - Red)
        val rpmPath = Path()
        points.forEachIndexed { idx, pt ->
            val normY = h - (pt.rpm.toFloat() / 7000f).coerceIn(0f, 1f) * h
            val x = idx * stepX
            if (idx == 0) rpmPath.moveTo(x, normY) else rpmPath.lineTo(x, normY)
        }
        drawPath(path = rpmPath, color = RedOrangeGlow, style = Stroke(width = 2.5f))

        // Draw Throttle Position (Glowing cyan)
        val tpsPath = Path()
        points.forEachIndexed { idx, pt ->
            val normY = h - (pt.throttlePositionPercent.toFloat() / 100f).coerceIn(0f, 1f) * h
            val x = idx * stepX
            if (idx == 0) tpsPath.moveTo(x, normY) else tpsPath.lineTo(x, normY)
        }
        drawPath(path = tpsPath, color = GlowingBlue, style = Stroke(width = 2f))
    }
}

// ==========================================
// 3. PATCHER TAB CONTENT (Universal Patcher style)
// ==========================================
@Composable
fun PatcherTabContent(viewModel: TunerViewModel) {
    val context = LocalContext.current
    val calibrations by viewModel.calibrations.collectAsState()
    val selectedCal by viewModel.selectedCal.collectAsState()
    val segments by viewModel.calSegments.collectAsState()
    val sparkGrid by viewModel.sparkTimingGrid.collectAsState()

    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }

    var editingCellIndex by remember { mutableStateOf<Int?>(null) }
    var editingCellVal by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Calibration selector Row
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
                border = BorderStroke(1.dp, BorderGrey)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CALIBRATION REPOSITORY BIN FILES",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Button(
                            onClick = { showNewFileDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp).testTag("add_cal_file_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "", modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("NEW BIN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(calibrations) { cal ->
                            val isSelected = selectedCal?.id == cal.id
                            val checksumColor = if (cal.isChecksumValid) NeonGreen else WarningRed
                            Card(
                                modifier = Modifier
                                    .width(220.dp)
                                    .clickable { viewModel.selectCalFile(cal) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) AmberGold.copy(alpha = 0.08f) else Color(0xFF14171D)
                                ),
                                border = BorderStroke(1.dp, if (isSelected) AmberGold else BorderGrey)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "OS: ${cal.operatingSystem}",
                                            fontSize = 9.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        IconButton(
                                            onClick = { viewModel.deleteCalibration(cal) },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Cal", tint = Color.Gray, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                    Text(
                                        text = cal.name,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(checksumColor)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (cal.isChecksumValid) "CHECKSUM OK" else "CHECKSUM INVALID",
                                                color = checksumColor,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = "${cal.mapSensorBarType}-BAR MAP",
                                            color = GlowingBlue,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedCal?.let { cal ->
            // Automated Segment Checksum boundary correction module (Universal patcher style)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
                    border = BorderStroke(1.dp, BorderGrey)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "AUTOMATED SEGMENT CHECKSUM BOUNDARY TOOL",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Auto-scans segment indexes for GM P59 binary headers",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }

                            if (!cal.isChecksumValid) {
                                Button(
                                    onClick = { viewModel.applyAutomatedChecksumCorrection() },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp).testTag("fix_checksum_btn")
                                ) {
                                    Text("CORRECT CHECKSUMS", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "", tint = NeonGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("VALIDATED", color = NeonGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Grid representing the 8 bin sectors
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            segments.forEach { sec ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF14171D))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "ID #${sec.id} - ${sec.name}",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "S_Add: ${sec.startAddress} | E_Add: ${sec.endAddress}",
                                            color = Color.Gray,
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("HEX_CHK: ${sec.checksum}", color = Color.Gray, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                            Text("CALC: ${sec.calculatedChecksum}", color = GlowingBlue, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (sec.isValid) NeonGreen else WarningRed)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Easy Engine Toggles & Tunings
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
                    border = BorderStroke(1.dp, BorderGrey)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "GM P59 SYSTEM PARAMETERS & PATCH DEFINITIONS",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Switch VATS
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("VATS Anti-Theft Lockout Patch", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Disable to support engine swap runtimes", color = Color.Gray, fontSize = 9.sp)
                            }
                            Switch(
                                checked = cal.vatsEnabled,
                                onCheckedChange = { viewModel.updateVatsToggled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = AmberGold)
                            )
                        }

                        Divider(color = Color(0xFF232832), modifier = Modifier.padding(vertical = 8.dp))

                        // Switch Flex Fuel
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Flex Fuel Sensor Enablement", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Runs alcohol timing modifier tables", color = Color.Gray, fontSize = 9.sp)
                            }
                            Switch(
                                checked = cal.flexFuelEnabled,
                                onCheckedChange = { viewModel.updateFlexFuelToggled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = AmberGold)
                            )
                        }

                        Divider(color = Color(0xFF232832), modifier = Modifier.padding(vertical = 8.dp))

                        // Switch Lean Cruise
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Lean Cruise Fuel Economy Mod", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Saves fuel during steady state highway cruising", color = Color.Gray, fontSize = 9.sp)
                            }
                            Switch(
                                checked = cal.leanCruiseEnabled,
                                onCheckedChange = { viewModel.updateLeanCruiseToggled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = AmberGold)
                            )
                        }

                        Divider(color = Color(0xFF232832), modifier = Modifier.padding(vertical = 8.dp))

                        // Bar Map scaling selector
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("MAP Sensor Upgrade Scaling", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Select Bar limit for aftermarket intake swaps", color = Color.Gray, fontSize = 9.sp)
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF14171D))
                                    .border(1.dp, BorderGrey, RoundedCornerShape(6.dp))
                            ) {
                                listOf(1, 2, 3).forEach { bar ->
                                    val active = cal.mapSensorBarType == bar
                                    Text(
                                        text = "${bar}BAR",
                                        color = if (active) Color.White else Color.Gray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { viewModel.updateMapSensorType(bar) }
                                            .background(if (active) BorderGrey else Color.Transparent)
                                            .padding(vertical = 6.dp, horizontal = 12.dp)
                                    )
                                }
                            }
                        }

                        Divider(color = Color(0xFF232832), modifier = Modifier.padding(vertical = 8.dp))

                        // Target Idle RPM
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Target Idle Engine Speed", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("${cal.targetIdleRpm} RPM", color = AmberGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = cal.targetIdleRpm.toFloat(),
                                onValueChange = { viewModel.updateTargetIdleRpm(it.toInt()) },
                                valueRange = 500f..1200f,
                                steps = 14,
                                colors = SliderDefaults.colors(thumbColor = AmberGold, activeTrackColor = AmberGold)
                            )
                        }

                        Divider(color = Color(0xFF232832), modifier = Modifier.padding(vertical = 8.dp))

                        // Limit Spark Advance
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Absolute Maximum Spark Ignition timing", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("${cal.sparkMaxAdvance}° BTDC", color = AmberGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = cal.sparkMaxAdvance.toFloat(),
                                onValueChange = { viewModel.updateSparkAdvanceLimit(it.toInt()) },
                                valueRange = 20f..48f,
                                steps = 28,
                                colors = SliderDefaults.colors(thumbColor = AmberGold, activeTrackColor = AmberGold)
                            )
                        }
                    }
                }
            }

            // Interactive Heatmapped High Octane Base Spark Ignition Matrix Editor
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
                    border = BorderStroke(1.dp, BorderGrey)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "HIGH-OCTANE IGNITION TIMING MATRIX BASE SPARK MAP",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Columns: Engine RPM | Rows: Air Load (g/cyl). Adjust values to modify advance curves.",
                            color = Color.Gray,
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Show Grid of headers and values
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, BorderGrey)
                                .padding(2.dp)
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(10), // 1 Column for Row Headers (Load), 9 Columns for RPM Headers
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Corner cell
                                item {
                                    GridHeaderCell("g/cyl \\ RPM")
                                }
                                // RPM header cells
                                val rpmHeaders = listOf(600, 1000, 1600, 2400, 3200, 4000, 4800, 5600, 6400)
                                rpmHeaders.forEach { rpm ->
                                    item {
                                        GridHeaderCell("$rpm")
                                    }
                                }

                                // Air load header coordinates map
                                val airLoads = listOf(0.08, 0.16, 0.24, 0.32, 0.40, 0.48, 0.60, 0.72, 0.88, 1.00)
                                airLoads.forEach { load ->
                                    // Row Header
                                    item {
                                        GridHeaderCell(String.format("%.2f", load))
                                    }

                                    // 9 RPM values for this load
                                    rpmHeaders.forEach { rpm ->
                                        val match = sparkGrid.find { it.rpm == rpm && Math.abs(it.airLoadGrams - load) < 0.001 }
                                        if (match != null) {
                                            val cellIdx = sparkGrid.indexOf(match)
                                            item {
                                                InteractiveGridValueCell(
                                                    cellValue = match.advanceDegrees,
                                                    isSelected = editingCellIndex == cellIdx,
                                                    onClick = {
                                                        editingCellIndex = cellIdx
                                                        editingCellVal = match.advanceDegrees.toString()
                                                    }
                                                )
                                            }
                                        } else {
                                            item {
                                                Box(modifier = Modifier.padding(2.dp)) { Text("-", color = Color.Gray, fontSize = 9.sp) }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Cell Modifier Panel if selected
                        editingCellIndex?.let { index ->
                            if (index in sparkGrid.indices) {
                                val cell = sparkGrid[index]
                                Spacer(modifier = Modifier.height(14.dp))
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14171D)),
                                    border = BorderStroke(1.dp, AmberGold)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Modified Cell: Load ${cell.airLoadGrams}g, RPM ${cell.rpm}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text("Current: ${cell.advanceDegrees}° BTDC", color = Color.Gray, fontSize = 9.sp)
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = {
                                                    viewModel.updateSparkGridCell(index, cell.advanceDegrees - 1.0)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = BorderGrey),
                                                contentPadding = PaddingValues(horizontal = 10.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("-1°", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = {
                                                    viewModel.updateSparkGridCell(index, cell.advanceDegrees - 0.1)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = BorderGrey),
                                                contentPadding = PaddingValues(horizontal = 8.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("-0.1°", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = {
                                                    viewModel.updateSparkGridCell(index, cell.advanceDegrees + 0.1)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = BorderGrey),
                                                contentPadding = PaddingValues(horizontal = 8.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("+0.1°", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = {
                                                    viewModel.updateSparkGridCell(index, cell.advanceDegrees + 1.0)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = BorderGrey),
                                                contentPadding = PaddingValues(horizontal = 10.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("+1°", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            IconButton(
                                                onClick = { editingCellIndex = null },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "", tint = WarningRed)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } ?: run {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Repository is empty. Click NEW BIN to initialize a blank calibration file.", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }

    // Modal dialog to add custom new binaries (Universal patcher file creation)
    if (showNewFileDialog) {
        Dialog(onDismissRequest = { showNewFileDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
                border = BorderStroke(1.dp, BorderGrey),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "ADD NEW PCM TUNING FILE BINARY",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("Configuration name", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberGold,
                            unfocusedBorderColor = BorderGrey,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("new_cal_name_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showNewFileDialog = false }) {
                            Text("CANCEL", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newFileName.isNotBlank()) {
                                    viewModel.addNewCalibrationFile(newFileName)
                                    newFileName = ""
                                    showNewFileDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("save_new_cal_btn")
                        ) {
                            Text("CREATE BIN")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GridHeaderCell(text: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFF14171D))
            .border(0.5.dp, BorderGrey)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.Gray,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun InteractiveGridValueCell(
    cellValue: Double,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Generate simple heatmap gradient on the spark degree value (advance ranges 10 to 45)
    val factor = ((cellValue - 10.0) / 35.0).coerceIn(0.0, 1.0)
    // Blend from cool Slate-ish color to warm heat orange
    val r = (0x1F + (factor * 0xD0).toInt()).coerceIn(0, 255)
    val g = (0x24 + (factor * 0x30).toInt()).coerceIn(0, 255)
    val b = (0x30 - (factor * 0x15).toInt()).coerceIn(0, 255)
    val heatmapColor = Color(r, g, b)

    Box(
        modifier = Modifier
            .background(if (isSelected) AmberGold else heatmapColor)
            .border(0.5.dp, if (isSelected) Color.White else BorderGrey)
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = String.format("%.1f", cellValue),
            color = if (isSelected) Color.Black else Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}
