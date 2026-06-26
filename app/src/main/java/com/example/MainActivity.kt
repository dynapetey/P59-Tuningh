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
import com.example.ui.TunerViewModel
import com.example.ui.LiveTelemetryTableContent
import com.example.ui.theme.MyApplicationTheme
import com.example.engine.FuelGridCell
import com.example.engine.SparkGridCell
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
            onDisconnect = { viewModel.obdxManager.disconnectDevice() }
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
                3 -> com.example.ui.GeminiTunerTabContent(viewModel)
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
        TuningTabItem(2, "UNIVERSAL PATCHER", Icons.Default.Create),
        TuningTabItem(3, "GEMINI TUNER", Icons.Default.Star)
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
        3 -> TerminalPurple
        else -> Color.White
    }
}

@Composable
fun OBDXStatusHeader(
    obdxState: ConnectionState,
    voltage: Float,
    speedMode: String,
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
                    // Bluetooth Status Indicator
                    Text(
                        text = "BLUETOOTH SPP",
                        color = GlowingBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF14171D))
                            .border(1.dp, BorderGrey, RoundedCornerShape(6.dp))
                            .padding(vertical = 8.dp, horizontal = 12.dp)
                    )

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
    val activeDtcs by viewModel.obdxManager.activeDtcs.collectAsState()

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

        // DTC DIAGNOSTICS & FAULT CODES CONTROL MODULE
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
                border = BorderStroke(1.dp, BorderGrey),
                modifier = Modifier.fillMaxWidth().testTag("dtc_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PCM DIAGNOSTIC TROUBLE CODES (DTC)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    if (activeDtcs.isNotEmpty()) Color(0xFF33141E) else Color(0xFF142C1E),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (activeDtcs.isNotEmpty()) "${activeDtcs.size} FAULTS" else "HEALTHY",
                                color = if (activeDtcs.isNotEmpty()) WarningRed else NeonGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Query and clear active or pending emission-related diagnostic trouble codes stored in the P59 ECM's EEPROM/DTC matrix link.",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.fetchDtcCodes() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2835)),
                            border = BorderStroke(1.dp, GlowingBlue),
                            shape = RoundedCornerShape(6.dp),
                            enabled = connectionState != ConnectionState.DISCONNECTED,
                            modifier = Modifier
                                .weight(1.5f)
                                .height(40.dp)
                                .testTag("fetch_dtc_btn")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Read Codes", modifier = Modifier.size(16.dp), tint = GlowingBlue)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("READ DTC CODES", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.clearDtcCodes() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF331F25)),
                            border = BorderStroke(1.dp, WarningRed),
                            shape = RoundedCornerShape(6.dp),
                            enabled = connectionState != ConnectionState.DISCONNECTED,
                            modifier = Modifier
                                .weight(1.5f)
                                .height(40.dp)
                                .testTag("clear_dtc_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear Codes", modifier = Modifier.size(16.dp), tint = WarningRed)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CLEAR DTC REGISTER", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (activeDtcs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            activeDtcs.forEach { dtc ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF14171E), shape = RoundedCornerShape(6.dp))
                                        .border(1.dp, Color(0xFF232832), shape = RoundedCornerShape(6.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF33141E), shape = RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = dtc.code,
                                            color = WarningRed,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = dtc.description,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = dtc.severity,
                                            color = Color.Gray,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F1B12), shape = RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF173522), shape = RoundedCornerShape(6.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = NeonGreen, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "No diagnostic trouble codes currently stored in the PCM.",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
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
// CUSTOM PID MODELS & HELPER STRUCTURES
// ==========================================
data class CustomPid(
    val id: String,
    val name: String,
    val hexCode: String,
    val unit: String,
    val maxValue: Float,
    val description: String,
    val getValue: (LogDataPoint?) -> Float,
    val formattedValue: (LogDataPoint?) -> String
)

fun getPidColor(id: String): Color {
    return when (id) {
        "RPM" -> Color(0xFF39FF14) // Neon Green
        "MPH" -> Color(0xFF00E5FF) // Glowing Blue
        "MAP" -> Color(0xFFFF9F0A) // Orange
        "ECT" -> Color(0xFFFF375F) // Pink-Red
        "SPARK" -> Color(0xFFFFD60A) // Amber Gold
        "STFT" -> Color(0xFFBF5AF2) // Purple
        "LTFT" -> Color(0xFF5E5CE6) // Indigo
        "AFR" -> Color(0xFF30D158) // Teal-Green
        "TPS" -> Color(0xFFE5C158) // Yellow
        "MAF" -> Color(0xFFFF453A) // Coral-Red
        "IAT" -> Color(0xFF64D2FF) // Sky-Blue
        "IAC" -> Color(0xFF0A84FF) // Blue
        "KNOCK" -> Color(0xFFFF3B30) // Severe Red
        "KNK_CNT" -> Color(0xFFAC8E68) // Bronze
        "EQ_RATIO" -> Color(0xFFD4AF37) // Gold
        else -> Color.White
    }
}

val allAvailablePids = listOf(
    CustomPid(
        id = "RPM",
        name = "Engine Speed",
        hexCode = "010C",
        unit = "RPM",
        maxValue = 7000f,
        description = "Engine speed in revolutions per minute (RPM)",
        getValue = { it?.rpm?.toFloat() ?: 0f },
        formattedValue = { "${it?.rpm ?: 0} RPM" }
    ),
    CustomPid(
        id = "MPH",
        name = "Vehicle Speed",
        hexCode = "010D",
        unit = "MPH",
        maxValue = 160f,
        description = "Vehicle speed in miles per hour (MPH)",
        getValue = { it?.mph?.toFloat() ?: 0f },
        formattedValue = { "${it?.mph ?: 0} MPH" }
    ),
    CustomPid(
        id = "MAP",
        name = "Manifold Pressure",
        hexCode = "010B",
        unit = "kPa",
        maxValue = 210f,
        description = "MAP sensor manifold intake air pressure in kPa",
        getValue = { it?.mapKpa ?: 32.0f },
        formattedValue = { String.format("%.1f kPa", it?.mapKpa ?: 32.0f) }
    ),
    CustomPid(
        id = "ECT",
        name = "Coolant Temp",
        hexCode = "0105",
        unit = "°F",
        maxValue = 250f,
        description = "Engine coolant temperature in degrees Fahrenheit",
        getValue = { it?.coolantTempF?.toFloat() ?: 180f },
        formattedValue = { "${it?.coolantTempF ?: 180}°F" }
    ),
    CustomPid(
        id = "SPARK",
        name = "Spark Advance",
        hexCode = "010E",
        unit = "°",
        maxValue = 45f,
        description = "Ignition timing spark advance in degrees BTDC",
        getValue = { it?.sparkAdvance ?: 14.5f },
        formattedValue = { String.format("%.1f°", it?.sparkAdvance ?: 14.5f) }
    ),
    CustomPid(
        id = "STFT",
        name = "Short Term Fuel Trim",
        hexCode = "0106",
        unit = "%",
        maxValue = 25f,
        description = "Short term adaptive closed-loop fuel correction percentage",
        getValue = { it?.shortTermFuelTrimPercent ?: 0f },
        formattedValue = { String.format("%.1f%%", it?.shortTermFuelTrimPercent ?: 0f) }
    ),
    CustomPid(
        id = "LTFT",
        name = "Long Term Fuel Trim",
        hexCode = "0107",
        unit = "%",
        maxValue = 25f,
        description = "Long term learned fuel offset correction percentage",
        getValue = { it?.longTermFuelTrimPercent ?: 0f },
        formattedValue = { String.format("%.1f%%", it?.longTermFuelTrimPercent ?: 0f) }
    ),
    CustomPid(
        id = "AFR",
        name = "Wideband O2 AFR",
        hexCode = "0124",
        unit = "AFR",
        maxValue = 18f,
        description = "Measured air-fuel ratio from external wideband controller",
        getValue = { it?.widebandO2Afr ?: 14.7f },
        formattedValue = { String.format("%.2f AFR", it?.widebandO2Afr ?: 14.7f) }
    ),
    CustomPid(
        id = "TPS",
        name = "Throttle Position",
        hexCode = "0111",
        unit = "%",
        maxValue = 100f,
        description = "Relative throttle pedal blade opening percentage",
        getValue = { it?.throttlePositionPercent?.toFloat() ?: 12f },
        formattedValue = { "${it?.throttlePositionPercent ?: 12}%" }
    ),
    CustomPid(
        id = "MAF",
        name = "Mass Air Flow",
        hexCode = "0110",
        unit = "g/s",
        maxValue = 500f,
        description = "Mass of air flow entering throttle body in grams/sec",
        getValue = { it?.massAirFlowGps ?: 12.5f },
        formattedValue = { String.format("%.1f g/s", it?.massAirFlowGps ?: 12.5f) }
    ),
    CustomPid(
        id = "IAT",
        name = "Intake Air Temp",
        hexCode = "010F",
        unit = "°F",
        maxValue = 180f,
        description = "Temperature of intake air charge inside manifold runners",
        getValue = { it?.manifoldAirTempF?.toFloat() ?: 95f },
        formattedValue = { "${it?.manifoldAirTempF ?: 95}°F" }
    ),
    CustomPid(
        id = "IAC",
        name = "IAC Position",
        hexCode = "015C",
        unit = "steps",
        maxValue = 160f,
        description = "Idle Air Control actuator pintle step position counter",
        getValue = { it?.iacPositionSteps?.toFloat() ?: 45f },
        formattedValue = { "${it?.iacPositionSteps ?: 45} steps" }
    ),
    CustomPid(
        id = "KNOCK",
        name = "Knock Retard",
        hexCode = "01A6",
        unit = "°",
        maxValue = 12f,
        description = "Timing advance pulled due to detected detonation noise",
        getValue = { it?.knockRetardDegrees ?: 0f },
        formattedValue = { String.format("%.1f°", it?.knockRetardDegrees ?: 0f) }
    ),
    CustomPid(
        id = "KNK_CNT",
        name = "Knock Count",
        hexCode = "01A7",
        unit = "knocks",
        maxValue = 100f,
        description = "Cumulative count of registered detonation knock occurrences",
        getValue = { it?.knockCount?.toFloat() ?: 0f },
        formattedValue = { "${it?.knockCount ?: 0}" }
    ),
    CustomPid(
        id = "EQ_RATIO",
        name = "Commanded Lambda",
        hexCode = "0144",
        unit = "EQ",
        maxValue = 2f,
        description = "Targeted equivalence fuel-to-air lambda ratio",
        getValue = { it?.commandedEquivalenceRatio ?: 1.0f },
        formattedValue = { String.format("%.3f", it?.commandedEquivalenceRatio ?: 1.0f) }
    )
)

@Composable
fun DynamicLivePlotCanvas(
    selectedPids: List<CustomPid>,
    liveDataStream: LogDataPoint?
) {
    val historySize = 60
    val pointsHistory = remember { mutableStateListOf<LogDataPoint>() }

    LaunchedEffect(liveDataStream) {
        if (liveDataStream != null) {
            pointsHistory.add(liveDataStream)
            if (pointsHistory.size > historySize) {
                pointsHistory.removeAt(0)
            }
        }
    }

    if (selectedPids.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(Color(0xFF07080B))
                .border(1.dp, BorderGrey),
            contentAlignment = Alignment.Center
        ) {
            Text("No vehicle parameters selected for real-time visualization.", color = Color.Gray, fontSize = 10.sp)
        }
        return
    }

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(Color(0xFF07080B))
                .border(1.dp, BorderGrey)
        ) {
            val w = size.width
            val h = size.height

            // Draw horizontal gridlines
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

            // Draw curves for the first 4 selected PIDs to avoid visual clutter
            val pidsToPlot = selectedPids.take(4)
            val stepX = w / (historySize - 1)

            pidsToPlot.forEachIndexed { pidIdx, pid ->
                if (pointsHistory.size > 1) {
                    val path = Path()
                    pointsHistory.forEachIndexed { idx, point ->
                        val value = pid.getValue(point)
                        // Normalize between 0 and maxValue of this PID
                        val normY = h - (value / pid.maxValue).coerceIn(0f, 1f) * h
                        val curX = idx * stepX
                        if (idx == 0) {
                            path.moveTo(curX, normY)
                        } else {
                            path.lineTo(curX, normY)
                        }
                    }

                    // Assign color based on PID ID
                    val curveColor = getPidColor(pid.id)
                    drawPath(
                        path = path,
                        color = curveColor,
                        style = Stroke(width = 2.5f)
                    )
                }
            }
        }

        // Color legend for the plotted PIDs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            selectedPids.take(4).forEach { pid ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(getPidColor(pid.id), shape = RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = "${pid.name} (${pid.unit})",
                        color = Color.LightGray,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (selectedPids.size > 4) {
                Text(
                    text = "+${selectedPids.size - 4} more selected",
                    color = Color.Gray,
                    fontSize = 8.5.sp
                )
            }
        }
    }
}

// ==========================================
// CSV LOG EXPORTER HELPER
// ==========================================
fun convertLogPointsToCsv(points: List<LogDataPoint>): String {
    val sb = java.lang.StringBuilder()
    sb.append("TimestampOffsetMs,RPM,MPH,MAP_kPa,CoolantTemp_F,SparkAdvance_Deg,STFT_Pct,LTFT_Pct,Wideband_AFR,ThrottlePosition_Pct,MAF_gps,IAT_F,DesiredIdle_RPM,IAC_Steps,DwellTime_ms,KnockRetard_Deg,KnockCount,CommandedLambda\n")
    for (p in points) {
        sb.append("${p.timestampOffsetMs},")
        sb.append("${p.rpm},")
        sb.append("${p.mph},")
        sb.append("${String.format(java.util.Locale.US, "%.2f", p.mapKpa)},")
        sb.append("${p.coolantTempF},")
        sb.append("${String.format(java.util.Locale.US, "%.2f", p.sparkAdvance)},")
        sb.append("${String.format(java.util.Locale.US, "%.2f", p.shortTermFuelTrimPercent)},")
        sb.append("${String.format(java.util.Locale.US, "%.2f", p.longTermFuelTrimPercent)},")
        sb.append("${String.format(java.util.Locale.US, "%.2f", p.widebandO2Afr)},")
        sb.append("${p.throttlePositionPercent},")
        sb.append("${String.format(java.util.Locale.US, "%.2f", p.massAirFlowGps)},")
        sb.append("${p.manifoldAirTempF},")
        sb.append("${p.desiredIdleRpm},")
        sb.append("${p.iacPositionSteps},")
        sb.append("${String.format(java.util.Locale.US, "%.2f", p.dwellTimeMs)},")
        sb.append("${String.format(java.util.Locale.US, "%.2f", p.knockRetardDegrees)},")
        sb.append("${p.knockCount},")
        sb.append("${String.format(java.util.Locale.US, "%.3f", p.commandedEquivalenceRatio)}\n")
    }
    return sb.toString()
}

// ==========================================
// 2. LOGGER TAB CONTENT (PCM Logger style)
// ==========================================
@Composable
fun LoggerTabContent(viewModel: TunerViewModel) {
    val context = LocalContext.current
    val liveDataStream by viewModel.obdxManager.liveDataStream.collectAsState()
    val activeSessionId by viewModel.currentActiveSessionId.collectAsState()
    val logSessions by viewModel.logSessions.collectAsState()
    val connectionState by viewModel.obdxManager.connectionState.collectAsState()

    val selectedHistorySession by viewModel.selectedHistorySession.collectAsState()
    val historyPoints by viewModel.historySessionPoints.collectAsState()

    var activeSubTab by remember { mutableStateOf(0) } // 0: Live Gauges, 1: Live Table, 2: D3.js, 3: History Log Files
    val selectedPidIds = remember { mutableStateListOf("RPM", "MAP", "ECT", "SPARK", "AFR", "TPS") }
    val supportedPids by viewModel.obdxManager.supportedPids.collectAsState()

    // Query PCM for PIDs once connected and update selection
    LaunchedEffect(supportedPids) {
        if (supportedPids.isNotEmpty()) {
            selectedPidIds.clear()
            selectedPidIds.addAll(supportedPids)
        }
    }

    // Automatically initiate telemetry logging loop when OBDX device is successfully connected
    LaunchedEffect(connectionState) {
        if (connectionState == ConnectionState.CONNECTED_READY && activeSessionId == null) {
            viewModel.startLoggingSession()
        }
    }

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
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { activeSubTab = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeSubTab == 0) NeonGreen.copy(alpha = 0.2f) else CardSurfaceColor
                ),
                border = BorderStroke(1.dp, if (activeSubTab == 0) NeonGreen else BorderGrey),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                Text(
                    "NATIVE DASH",
                    color = if (activeSubTab == 0) Color.White else Color.Gray,
                    fontSize = 9.sp,
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
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                Text(
                    "LIVE TABLE",
                    color = if (activeSubTab == 1) Color.White else Color.Gray,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = { activeSubTab = 2 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeSubTab == 2) TerminalPurple.copy(alpha = 0.2f) else CardSurfaceColor
                ),
                border = BorderStroke(1.dp, if (activeSubTab == 2) TerminalPurple else BorderGrey),
                modifier = Modifier.weight(1.2f),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                Text(
                    "D3.JS ANALYZER",
                    color = if (activeSubTab == 2) Color.White else Color.Gray,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = { activeSubTab = 3 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeSubTab == 3) AmberGold.copy(alpha = 0.2f) else CardSurfaceColor
                ),
                border = BorderStroke(1.dp, if (activeSubTab == 3) AmberGold else BorderGrey),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                Text(
                    "SAVED RUNS",
                    color = if (activeSubTab == 3) Color.White else Color.Gray,
                    fontSize = 9.sp,
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

                val mapPoint = liveDataStream

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // P59 ECM PID STREAM SELECTOR
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pid_selector_card"),
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
                            border = BorderStroke(1.dp, GlowingBlue.copy(alpha = 0.5f))
                        ) {
                            var expanded by remember { mutableStateOf(false) }
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expanded = !expanded },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = "PIDs",
                                            tint = GlowingBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "P59 ECM PID STREAM SELECTOR",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = "${selectedPidIds.size} / ${allAvailablePids.size} PIDs Active • Real-time VPW Stream",
                                                color = Color.Gray,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (expanded) "Collapse" else "Expand",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                if (expanded) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = BorderGrey, thickness = 1.dp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Quick select row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                selectedPidIds.clear()
                                                selectedPidIds.addAll(allAvailablePids.map { it.id })
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("SELECT ALL PIDS", color = GlowingBlue, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                        }

                                        TextButton(
                                            onClick = {
                                                selectedPidIds.clear()
                                                selectedPidIds.addAll(listOf("RPM", "MAP", "ECT", "SPARK", "AFR", "TPS"))
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("RESET DEFAULT", color = Color.Gray, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                        }

                                        TextButton(
                                            onClick = {
                                                selectedPidIds.clear()
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text("CLEAR ALL", color = WarningRed, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    val categories = listOf("CORE TELEMETRY", "AIR & PRESSURE", "FUEL SYSTEM", "ENGINE STATE")
                                    categories.forEach { category ->
                                        val pidsInCategory = when (category) {
                                            "CORE TELEMETRY" -> allAvailablePids.filter { it.id in listOf("RPM", "MPH", "TPS") }
                                            "AIR & PRESSURE" -> allAvailablePids.filter { it.id in listOf("MAP", "MAF", "IAT") }
                                            "FUEL SYSTEM" -> allAvailablePids.filter { it.id in listOf("AFR", "STFT", "LTFT", "EQ_RATIO") }
                                            else -> allAvailablePids.filter { it.id in listOf("ECT", "IAC", "SPARK", "KNOCK", "KNK_CNT") }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = category,
                                            color = Color.Gray,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .padding(bottom = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            pidsInCategory.forEach { pid ->
                                                val isSelected = selectedPidIds.contains(pid.id)
                                                val pidColor = getPidColor(pid.id)

                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            if (isSelected) pidColor.copy(alpha = 0.15f) else Color(0xFF14171E),
                                                            shape = RoundedCornerShape(6.dp)
                                                        )
                                                        .border(
                                                            1.dp,
                                                            if (isSelected) pidColor else BorderGrey,
                                                            shape = RoundedCornerShape(6.dp)
                                                        )
                                                        .clickable {
                                                            if (isSelected) {
                                                                selectedPidIds.remove(pid.id)
                                                            } else {
                                                                selectedPidIds.add(pid.id)
                                                            }
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .background(pidColor, shape = RoundedCornerShape(3.dp))
                                                        )
                                                        Text(
                                                            text = pid.id,
                                                            color = if (isSelected) Color.White else Color.Gray,
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "(${pid.hexCode})",
                                                            color = Color.DarkGray,
                                                            fontSize = 8.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Dynamically allocated selected PID telemetry gauge cards
                    val selectedCustomPids = allAvailablePids.filter { selectedPidIds.contains(it.id) }
                    selectedCustomPids.chunked(2).forEach { rowPids ->
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowPids.forEach { pid ->
                                    val rawVal = pid.getValue(mapPoint)
                                    val formattedStr = pid.formattedValue(mapPoint)
                                    val glowColor = getPidColor(pid.id)

                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .border(BorderStroke(1.dp, BorderGrey), shape = RoundedCornerShape(8.dp))
                                            .testTag("pid_gauge_${pid.id.lowercase()}"),
                                        colors = CardDefaults.cardColors(containerColor = CardSurfaceColor)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = pid.name.uppercase(),
                                                    color = Color.Gray,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFF14171E), shape = RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = pid.hexCode,
                                                        color = glowColor,
                                                        fontSize = 7.5.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = formattedStr,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            LinearProgressIndicator(
                                                progress = { (rawVal / pid.maxValue).coerceIn(0f, 1f) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(3.dp)
                                                    .clip(RoundedCornerShape(1.5.dp)),
                                                color = glowColor,
                                                trackColor = Color(0xFF1C1E24)
                                            )
                                        }
                                    }
                                }
                                if (rowPids.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // Scrolling real-time waveform visual plot for selected PIDs
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
                            border = BorderStroke(1.dp, BorderGrey)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "PCM REAL-TIME MULTI-CHANNEL WAVEFORM SCROLL DATA (VPW CHANNEL)",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                DynamicLivePlotCanvas(
                                    selectedPids = selectedCustomPids,
                                    liveDataStream = mapPoint
                                )
                            }
                        }
                    }
                }
            }
        } else if (activeSubTab == 1) {
            // Live Telemetry Table (Structured Table)
            LiveTelemetryTableContent(
                viewModel = viewModel,
                liveDataStream = liveDataStream,
                connectionState = connectionState,
                activeSessionId = activeSessionId
            )
        } else if (activeSubTab == 2) {
            // D3.js Graphic Analyzer WebView
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
                                text = if (activeSessionId != null) "D3.JS ANALYZER - ACTIVE STREAM" else "D3.JS STREAM IDLE",
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
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Stop", modifier = Modifier.size(14.dp), tint = Color.Black)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("STOP LOG", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                D3DashboardView(viewModel = viewModel, modifier = Modifier.weight(1f))
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

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val csv = convertLogPointsToCsv(historyPoints)
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        val clip = android.content.ClipData.newPlainText("P59 ECM Log CSV", csv)
                                        clipboard.setPrimaryClip(clip)
                                        android.widget.Toast.makeText(context, "Log CSV copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceColor),
                                    border = BorderStroke(1.dp, BorderGrey),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp).testTag("copy_csv_btn")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Copy", modifier = Modifier.size(12.dp), tint = NeonGreen)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("COPY CSV", fontSize = 9.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val csv = convertLogPointsToCsv(historyPoints)
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(android.content.Intent.EXTRA_SUBJECT, "P59 ECM Log: ${selected.sessionName}")
                                            putExtra(android.content.Intent.EXTRA_TEXT, csv)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, "Share Log CSV"))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GlowingBlue.copy(alpha = 0.15f)),
                                    border = BorderStroke(1.dp, GlowingBlue),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp).testTag("share_csv_btn")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(12.dp), tint = Color.White)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("SHARE CSV", fontSize = 9.5.sp, color = Color.White, fontWeight = FontWeight.Bold)
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
// MAP VALIDATION STRUCTURES & ENGINE
// ==========================================
data class ValidationIssue(
    val severity: String, // "ERROR" or "WARNING"
    val message: String
)

fun validateSparkMap(grid: List<SparkGridCell>, maxLimit: Int): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    if (grid.isEmpty()) return issues

    for (cell in grid) {
        if (cell.advanceDegrees > maxLimit) {
            issues.add(ValidationIssue("ERROR", "Cell [${cell.rpm} RPM, ${String.format("%.2f", cell.airLoadGrams)} g/cyl]: Spark advance (${cell.advanceDegrees}°) exceeds configured table safety limit of $maxLimit° BTDC."))
        }
        if (cell.advanceDegrees > 45.0) {
            issues.add(ValidationIssue("ERROR", "Cell [${cell.rpm} RPM, ${String.format("%.2f", cell.airLoadGrams)} g/cyl]: Spark advance (${cell.advanceDegrees}°) exceeds maximum physical cylinder limit of 45.0° BTDC."))
        }
        if (cell.advanceDegrees < -5.0) {
            issues.add(ValidationIssue("ERROR", "Cell [${cell.rpm} RPM, ${String.format("%.2f", cell.airLoadGrams)} g/cyl]: Excessive retard timing (${cell.advanceDegrees}°), engine exhaust thermal threshold danger."))
        }
    }

    val rpmHeaders = listOf(600, 1000, 1600, 2400, 3200, 4000, 4800, 5600, 6400)
    val airLoadHeaders = listOf(0.08, 0.16, 0.24, 0.32, 0.40, 0.48, 0.60, 0.72, 0.88, 1.00)
    
    // Smoothness / Gradient check across adjacent RPM bins
    for (load in airLoadHeaders) {
        for (i in 0 until rpmHeaders.size - 1) {
            val cell1 = grid.find { it.rpm == rpmHeaders[i] && Math.abs(it.airLoadGrams - load) < 0.001 }
            val cell2 = grid.find { it.rpm == rpmHeaders[i+1] && Math.abs(it.airLoadGrams - load) < 0.001 }
            if (cell1 != null && cell2 != null) {
                val diff = Math.abs(cell1.advanceDegrees - cell2.advanceDegrees)
                if (diff > 8.0) {
                    issues.add(ValidationIssue("WARNING", "Unstable RPM Timing Step: Spark leap is ${String.format("%.1f", diff)}° between ${cell1.rpm} & ${cell2.rpm} RPM at ${load}g load (Max recommended: 8.0°)."))
                }
            }
        }
    }

    // Smoothness / Gradient check across adjacent Load bins
    for (rpm in rpmHeaders) {
        for (j in 0 until airLoadHeaders.size - 1) {
            val cell1 = grid.find { it.rpm == rpm && Math.abs(it.airLoadGrams - airLoadHeaders[j]) < 0.001 }
            val cell2 = grid.find { it.rpm == rpm && Math.abs(it.airLoadGrams - airLoadHeaders[j+1]) < 0.001 }
            if (cell1 != null && cell2 != null) {
                val diff = Math.abs(cell1.advanceDegrees - cell2.advanceDegrees)
                if (diff > 10.0) {
                    issues.add(ValidationIssue("WARNING", "Unstable Load Timing Step: Spark leap is ${String.format("%.1f", diff)}° between ${String.format("%.2f", airLoadHeaders[j])} & ${String.format("%.2f", airLoadHeaders[j+1])}g load at ${rpm} RPM."))
                }
            }
        }
    }

    return issues
}

fun validateFuelMap(grid: List<FuelGridCell>): List<ValidationIssue> {
    val issues = mutableListOf<ValidationIssue>()
    if (grid.isEmpty()) return issues

    for (cell in grid) {
        if (cell.vePercent > 140.0) {
            issues.add(ValidationIssue("ERROR", "Cell [${cell.rpm} RPM, ${String.format("%.2f", cell.airLoadGrams)} g/cyl]: VE (${cell.vePercent}%) exceeds physical supercharging/boost volumetric ceiling of 140%."))
        }
        if (cell.vePercent < 15.0) {
            issues.add(ValidationIssue("ERROR", "Cell [${cell.rpm} RPM, ${String.format("%.2f", cell.airLoadGrams)} g/cyl]: VE (${cell.vePercent}%) falls below minimum physical compression idle threshold of 15%."))
        }
    }

    val rpmHeaders = listOf(600, 1000, 1600, 2400, 3200, 4000, 4800, 5600, 6400)
    val airLoadHeaders = listOf(0.08, 0.16, 0.24, 0.32, 0.40, 0.48, 0.60, 0.72, 0.88, 1.00)

    // Smoothness / Gradient check across adjacent RPM bins
    for (load in airLoadHeaders) {
        for (i in 0 until rpmHeaders.size - 1) {
            val cell1 = grid.find { it.rpm == rpmHeaders[i] && Math.abs(it.airLoadGrams - load) < 0.001 }
            val cell2 = grid.find { it.rpm == rpmHeaders[i+1] && Math.abs(it.airLoadGrams - load) < 0.001 }
            if (cell1 != null && cell2 != null) {
                val diff = Math.abs(cell1.vePercent - cell2.vePercent)
                if (diff > 30.0) {
                    issues.add(ValidationIssue("WARNING", "Extreme RPM Fuel Transition: VE jumps ${String.format("%.1f", diff)}% between ${cell1.rpm} & ${cell2.rpm} RPM at ${load}g load (Max recommended: 30.0%)."))
                }
            }
        }
    }

    // Smoothness / Gradient check across adjacent Load bins
    for (rpm in rpmHeaders) {
        for (j in 0 until airLoadHeaders.size - 1) {
            val cell1 = grid.find { it.rpm == rpm && Math.abs(it.airLoadGrams - airLoadHeaders[j]) < 0.001 }
            val cell2 = grid.find { it.rpm == rpm && Math.abs(it.airLoadGrams - airLoadHeaders[j+1]) < 0.001 }
            if (cell1 != null && cell2 != null) {
                val diff = Math.abs(cell1.vePercent - cell2.vePercent)
                if (diff > 35.0) {
                    issues.add(ValidationIssue("WARNING", "Extreme Load Fuel Transition: VE jumps ${String.format("%.1f", diff)}% between ${String.format("%.2f", airLoadHeaders[j])} & ${String.format("%.2f", airLoadHeaders[j+1])}g load at ${rpm} RPM."))
                }
            }
        }
    }

    return issues
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
    val fuelGrid by viewModel.fuelVeGrid.collectAsState()
    val aiTuningLoading by viewModel.aiTuningLoading.collectAsState()
    val aiTuningResultExplanation by viewModel.aiTuningResultExplanation.collectAsState()

    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }

    var activeMapType by remember { mutableStateOf(0) } // 0: Spark Map, 1: Fuel VE Map
    var editingCellIndex by remember { mutableStateOf<Int?>(null) }
    var editingCellVal by remember { mutableStateOf("") }

    LaunchedEffect(activeMapType) {
        editingCellIndex = null
        editingCellVal = ""
    }

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

        // Tune Persistence side-by-side comparison (Most Recent and Next Modified)
        item {
            val mostRecent by viewModel.mostRecentTune.collectAsState()
            val nextModified by viewModel.nextModifiedTune.collectAsState()
            TuneComparisonWidget(mostRecent = mostRecent, nextModified = nextModified)
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

            // AI AUTOTUNING MATRIX CONTROLLER
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
                    border = BorderStroke(1.dp, AmberGold),
                    modifier = Modifier.fillMaxWidth().testTag("ai_autotune_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "AI", tint = AmberGold, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "GEMINI AI CALIBRATIONS MATRIX CO-PILOT",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            if (aiTuningLoading) {
                                CircularProgressIndicator(
                                    color = AmberGold,
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF2E2419), shape = RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "INTELLIGENT",
                                        color = AmberGold,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Optimize spark advance scaling, airflow modeling (VE Multipliers), target idle speeds, cooling fan triggers, and rev limits in parallel safely using advanced neural LLM modeling.",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )

                        Button(
                            onClick = { viewModel.triggerAiAutoTune() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2419)),
                            border = BorderStroke(1.dp, AmberGold),
                            shape = RoundedCornerShape(6.dp),
                            enabled = !aiTuningLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("trigger_ai_tune_btn")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Tune", modifier = Modifier.size(16.dp), tint = AmberGold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RUN GEMINI POWER & SAFETY AUTO-TUNE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        aiTuningResultExplanation?.let { explanation ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A)),
                                border = BorderStroke(1.dp, Color(0xFF232832)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Info, contentDescription = "", tint = AmberGold, modifier = Modifier.size(14.dp))
                                        Text(
                                            text = "TUNING EXPLANATION & RATIO STRATEGY",
                                            color = AmberGold,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = explanation,
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp
                                    )
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

                        Divider(color = Color(0xFF232832), modifier = Modifier.padding(vertical = 8.dp))

                        // Injector Flow Rate Scaling
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Injector Flow Rate Scaling (IFR)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("Set injector size to scale fuel delivery pulses correctly", color = Color.Gray, fontSize = 8.sp)
                                }
                                Text("${String.format("%.1f", cal.injectorFlowRateLbHr)} lb/hr", color = AmberGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = cal.injectorFlowRateLbHr.toFloat(),
                                onValueChange = { viewModel.updateInjectorFlowRate(it.toDouble()) },
                                valueRange = 15f..100f,
                                colors = SliderDefaults.colors(thumbColor = AmberGold, activeTrackColor = AmberGold)
                            )
                        }

                        Divider(color = Color(0xFF232832), modifier = Modifier.padding(vertical = 8.dp))

                        // Engine Rev Limiter
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Engine Rev Limiter (Fuel/Spark Cut)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("Defines absolute fuel cutoff RPM to protect mechanical internals", color = Color.Gray, fontSize = 8.sp)
                                }
                                Text("${cal.revLimitRpm} RPM", color = AmberGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = cal.revLimitRpm.toFloat(),
                                onValueChange = { viewModel.updateRevLimitRpm(it.toInt()) },
                                valueRange = 4000f..8000f,
                                steps = 40,
                                colors = SliderDefaults.colors(thumbColor = AmberGold, activeTrackColor = AmberGold)
                            )
                        }

                        Divider(color = Color(0xFF232832), modifier = Modifier.padding(vertical = 8.dp))

                        // Primary Cooling Fan 1 Trigger Temp
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Primary Cooling Fan (Fan 1) Turn-On Temp", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("Triggers low-speed electric cooling fans to actuate", color = Color.Gray, fontSize = 8.sp)
                                }
                                Text("${cal.fan1OnTempF}°F", color = AmberGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                             }
                             Slider(
                                 value = cal.fan1OnTempF.toFloat(),
                                 onValueChange = { viewModel.updateFan1OnTempF(it.toInt()) },
                                 valueRange = 160f..230f,
                                 colors = SliderDefaults.colors(thumbColor = AmberGold, activeTrackColor = AmberGold)
                             )
                         }

                         Divider(color = Color(0xFF232832), modifier = Modifier.padding(vertical = 8.dp))

                         // Secondary Cooling Fan 2 Trigger Temp
                         Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                             Row(
                                 modifier = Modifier.fillMaxWidth(),
                                 horizontalArrangement = Arrangement.SpaceBetween
                             ) {
                                 Column {
                                     Text("Secondary Cooling Fan (Fan 2) Turn-On Temp", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                     Text("Triggers high-speed electric cooling fans for heavy loads", color = Color.Gray, fontSize = 8.sp)
                                 }
                                 Text("${cal.fan2OnTempF}°F", color = AmberGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                             }
                             Slider(
                                 value = cal.fan2OnTempF.toFloat(),
                                 onValueChange = { viewModel.updateFan2OnTempF(it.toInt()) },
                                 valueRange = 165f..240f,
                                 colors = SliderDefaults.colors(thumbColor = AmberGold, activeTrackColor = AmberGold)
                             )
                         }

                         Divider(color = Color(0xFF232832), modifier = Modifier.padding(vertical = 8.dp))

                         // Volumetric Efficiency Multiplier
                         Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                             Row(
                                 modifier = Modifier.fillMaxWidth(),
                                 horizontalArrangement = Arrangement.SpaceBetween
                             ) {
                                 Column {
                                     Text("VE Table Global Multiplier", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                     Text("Scales calculated cylinder airmass mapping by a global percentage", color = Color.Gray, fontSize = 8.sp)
                                 }
                                 Text("${cal.veMultiplierPercent}%", color = AmberGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                             }
                             Slider(
                                 value = cal.veMultiplierPercent.toFloat(),
                                 onValueChange = { viewModel.updateVeMultiplierPercent(it.toInt()) },
                                 valueRange = 50f..180f,
                                 colors = SliderDefaults.colors(thumbColor = AmberGold, activeTrackColor = AmberGold)
                             )
                         }
                    }
                }
            }

            // Interactive Heatmapped Calibration Maps Visual Table Editor (Dual Map: Spark and Fuel VE)
            item {
                val sparkIssues = validateSparkMap(sparkGrid, cal.sparkMaxAdvance)
                val fuelIssues = validateFuelMap(fuelGrid)

                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
                    border = BorderStroke(1.dp, BorderGrey),
                    modifier = Modifier.fillMaxWidth().testTag("cal_visual_editor_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PCM CALIBRATION 3D MAP VISUAL EDITOR",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )

                            // Quick status indicator badge
                            val activeIssuesCount = if (activeMapType == 0) sparkIssues.size else fuelIssues.size
                            val activeErrors = if (activeMapType == 0) sparkIssues.count { it.severity == "ERROR" } else fuelIssues.count { it.severity == "ERROR" }
                            
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = when {
                                            activeIssuesCount == 0 -> NeonGreen.copy(alpha = 0.15f)
                                            activeErrors > 0 -> WarningRed.copy(alpha = 0.15f)
                                            else -> AmberGold.copy(alpha = 0.15f)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = when {
                                        activeIssuesCount == 0 -> "ALL CHECKS PASSED"
                                        activeErrors > 0 -> "$activeIssuesCount ANOMALIES ($activeErrors ERR)"
                                        else -> "$activeIssuesCount WARNINGS"
                                    },
                                    color = when {
                                        activeIssuesCount == 0 -> NeonGreen
                                        activeErrors > 0 -> WarningRed
                                        else -> AmberGold
                                    },
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Segmented Map Toggle Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF14171D), RoundedCornerShape(6.dp))
                                .border(1.dp, BorderGrey, RoundedCornerShape(6.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Spark Map Toggle Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (activeMapType == 0) BorderGrey else Color.Transparent)
                                    .clickable { activeMapType = 0 }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.FlashOn,
                                        contentDescription = "",
                                        tint = if (activeMapType == 0) AmberGold else Color.Gray,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        "SPARK ADVANCE",
                                        color = if (activeMapType == 0) Color.White else Color.Gray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (sparkIssues.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(if (sparkIssues.any { it.severity == "ERROR" }) WarningRed else AmberGold, RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("${sparkIssues.size}", color = Color.Black, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Fuel Map Toggle Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (activeMapType == 1) BorderGrey else Color.Transparent)
                                    .clickable { activeMapType = 1 }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.LocalGasStation,
                                        contentDescription = "",
                                        tint = if (activeMapType == 1) GlowingBlue else Color.Gray,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        "FUEL VE TABLE",
                                        color = if (activeMapType == 1) Color.White else Color.Gray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (fuelIssues.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(if (fuelIssues.any { it.severity == "ERROR" }) WarningRed else AmberGold, RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("${fuelIssues.size}", color = Color.Black, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = if (activeMapType == 0) {
                                "HIGH-OCTANE BASE SPARK TIMING LIMITS MAP"
                            } else {
                                "VOLUMETRIC EFFICIENCY (VE) CYLINDER AIRMASS MAP"
                            },
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (activeMapType == 0) {
                                "Columns: RPM | Rows: Air Load (g/cyl). Interactive heatmapped Spark ignition advance angle (° BTDC)."
                            } else {
                                "Columns: RPM | Rows: Air Load (g/cyl). Dynamic cylinder filling multipliers relative to volume (VE %)."
                            },
                            color = Color.Gray,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // 3D Matrix Heatmap Grid Component
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, BorderGrey)
                                .padding(2.dp)
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(10), // 1 Column for Load Header, 9 Columns for RPM Headers
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Dynamic Corner Header
                                item {
                                    GridHeaderCell("g/cyl \\ RPM")
                                }
                                
                                val rpmHeaders = listOf(600, 1000, 1600, 2400, 3200, 4000, 4800, 5600, 6400)
                                rpmHeaders.forEach { rpm ->
                                    item {
                                        GridHeaderCell("$rpm")
                                    }
                                }

                                val airLoads = listOf(0.08, 0.16, 0.24, 0.32, 0.40, 0.48, 0.60, 0.72, 0.88, 1.00)
                                airLoads.forEach { load ->
                                    // Row Load Header
                                    item {
                                        GridHeaderCell(String.format("%.2f", load))
                                    }

                                    // Render 9 cell values for this load header coordinate
                                    rpmHeaders.forEach { rpm ->
                                        if (activeMapType == 0) {
                                            val match = sparkGrid.find { it.rpm == rpm && Math.abs(it.airLoadGrams - load) < 0.001 }
                                            if (match != null) {
                                                val cellIdx = sparkGrid.indexOf(match)
                                                item {
                                                    InteractiveGridValueCell(
                                                        cellValue = match.advanceDegrees,
                                                        isSelected = editingCellIndex == cellIdx,
                                                        isFuelMap = false,
                                                        onClick = {
                                                            editingCellIndex = cellIdx
                                                            editingCellVal = String.format("%.1f", match.advanceDegrees)
                                                        }
                                                    )
                                                }
                                            } else {
                                                item { GridHeaderCell("-") }
                                            }
                                        } else {
                                            val match = fuelGrid.find { it.rpm == rpm && Math.abs(it.airLoadGrams - load) < 0.001 }
                                            if (match != null) {
                                                val cellIdx = fuelGrid.indexOf(match)
                                                item {
                                                    InteractiveGridValueCell(
                                                        cellValue = match.vePercent,
                                                        isSelected = editingCellIndex == cellIdx,
                                                        isFuelMap = true,
                                                        onClick = {
                                                            editingCellIndex = cellIdx
                                                            editingCellVal = String.format("%.1f", match.vePercent)
                                                        }
                                                    )
                                                }
                                            } else {
                                                item { GridHeaderCell("-") }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Cell Modifier Panel
                        editingCellIndex?.let { index ->
                            val currentListSize = if (activeMapType == 0) sparkGrid.size else fuelGrid.size
                            if (index in 0 until currentListSize) {
                                val rpm = if (activeMapType == 0) sparkGrid[index].rpm else fuelGrid[index].rpm
                                val airLoad = if (activeMapType == 0) sparkGrid[index].airLoadGrams else fuelGrid[index].airLoadGrams
                                val cellVal = if (activeMapType == 0) sparkGrid[index].advanceDegrees else fuelGrid[index].vePercent

                                Spacer(modifier = Modifier.height(14.dp))

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14171D)),
                                    border = BorderStroke(1.dp, AmberGold),
                                    modifier = Modifier.fillMaxWidth().testTag("cell_modifier_panel")
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "MODIFY CELL: Load ${String.format("%.2f", airLoad)}g | RPM $rpm",
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = if (activeMapType == 0) "Current Spark Advance: $cellVal° BTDC" else "Current VE Multiplier: $cellVal%",
                                                    color = Color.Gray,
                                                    fontSize = 9.5.sp
                                                )
                                            }

                                            IconButton(
                                                onClick = { editingCellIndex = null },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Close modifier", tint = WarningRed, modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Edit actions row (Incremental and manual entry)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Manual text input field
                                            OutlinedTextField(
                                                value = editingCellVal,
                                                onValueChange = { editingCellVal = it },
                                                label = { Text("Manual value", color = Color.Gray, fontSize = 8.sp) },
                                                singleLine = true,
                                                textStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = AmberGold,
                                                    unfocusedBorderColor = BorderGrey,
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("cell_manual_input")
                                            )

                                            Button(
                                                onClick = {
                                                    val typedVal = editingCellVal.toDoubleOrNull()
                                                    if (typedVal == null) {
                                                        Toast.makeText(context, "Invalid input number!", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        if (activeMapType == 0) {
                                                            if (typedVal < -15.0 || typedVal > 60.0) {
                                                                Toast.makeText(context, "Ignition angle must be between -15° and 60°!", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                viewModel.updateSparkGridCell(index, typedVal)
                                                            }
                                                        } else {
                                                            if (typedVal < 10.0 || typedVal > 180.0) {
                                                                Toast.makeText(context, "Fuel VE percent must be between 10% and 180%!", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                viewModel.updateFuelGridCell(index, typedVal)
                                                            }
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                                                shape = RoundedCornerShape(4.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                                modifier = Modifier.height(36.dp).testTag("apply_cell_value_btn")
                                            ) {
                                                Text("APPLY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Fast tuning increments
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (activeMapType == 0) {
                                                // Spark Quick Steps
                                                val steps = listOf(-5.0, -1.0, -0.1, 0.1, 1.0, 5.0)
                                                steps.forEach { step ->
                                                    Button(
                                                        onClick = {
                                                            val newVal = Math.round((cellVal + step) * 10) / 10.0
                                                            viewModel.updateSparkGridCell(index, newVal.coerceIn(-15.0, 60.0))
                                                            editingCellVal = String.format("%.1f", newVal.coerceIn(-15.0, 60.0))
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = BorderGrey),
                                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(28.dp)
                                                    ) {
                                                        Text(
                                                            text = if (step > 0) "+$step" else "$step",
                                                            fontSize = 8.5.sp,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            } else {
                                                // Fuel VE Quick Steps (e.g. -10%, -2%, -0.5%, +0.5%, +2%, +10%)
                                                val steps = listOf(-10.0, -2.0, -0.5, 0.5, 2.0, 10.0)
                                                steps.forEach { step ->
                                                    Button(
                                                        onClick = {
                                                            val newVal = Math.round((cellVal + step) * 10) / 10.0
                                                            viewModel.updateFuelGridCell(index, newVal.coerceIn(10.0, 180.0))
                                                            editingCellVal = String.format("%.1f", newVal.coerceIn(10.0, 180.0))
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = BorderGrey),
                                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(28.dp)
                                                    ) {
                                                        Text(
                                                            text = if (step > 0) "+$step%" else "$step%",
                                                            fontSize = 8.5.sp,
                                                            color = Color.White,
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

                        Spacer(modifier = Modifier.height(16.dp))

                        // SAFETY & GRADIENT STABILITY MONITOR PANEL
                        val issues = if (activeMapType == 0) sparkIssues else fuelIssues
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A)),
                            border = BorderStroke(1.dp, if (issues.isNotEmpty()) BorderGrey else NeonGreen.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().testTag("map_safety_monitor")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (issues.isNotEmpty()) Icons.Default.Warning else Icons.Default.CheckCircle,
                                        contentDescription = "Monitor status",
                                        tint = if (issues.any { it.severity == "ERROR" }) WarningRed else if (issues.isNotEmpty()) AmberGold else NeonGreen,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (activeMapType == 0) "SPARK CALIBRATION ENGINE SAFETY MONITOR" else "FUEL SCAVENGING DENSITY SAFETY MONITOR",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (issues.isEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(NeonGreen, RoundedCornerShape(3.dp))
                                        )
                                        Text(
                                            text = "All cell advance limits and flow scavenging gradients verified. No mechanical timing hazards detected.",
                                            color = Color.Gray,
                                            fontSize = 9.5.sp
                                        )
                                    }
                                } else {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())
                                    ) {
                                        issues.forEach { issue ->
                                            val badgeColor = if (issue.severity == "ERROR") WarningRed else AmberGold
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.Top,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .padding(top = 4.dp)
                                                        .size(5.dp)
                                                        .background(badgeColor, RoundedCornerShape(2.5.dp))
                                                )
                                                Column {
                                                    Text(
                                                        text = "[${issue.severity}] ${issue.message}",
                                                        color = Color.LightGray,
                                                        fontSize = 9.sp,
                                                        lineHeight = 12.sp
                                                    )
                                                }
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
    isFuelMap: Boolean = false,
    onClick: () -> Unit
) {
    // Generate simple heatmap gradient based on cell type
    val factor = if (isFuelMap) {
        // VE ranges 20.0 to 120.0
        ((cellValue - 20.0) / 100.0).coerceIn(0.0, 1.0)
    } else {
        // Spark advance ranges 10.0 to 45.0
        ((cellValue - 10.0) / 35.0).coerceIn(0.0, 1.0)
    }

    val heatmapColor = if (isFuelMap) {
        // Greenish cyan to warm violet/pink
        val r = (0x0C + (factor * 0x80).toInt()).coerceIn(0, 255)
        val g = (0x3C + (factor * 0x20).toInt()).coerceIn(0, 255)
        val b = (0x5C - (factor * 0x2C).toInt()).coerceIn(0, 255)
        Color(r, g, b)
    } else {
        // Cool Slate-ish color to warm heat orange
        val r = (0x1F + (factor * 0xD0).toInt()).coerceIn(0, 255)
        val g = (0x24 + (factor * 0x30).toInt()).coerceIn(0, 255)
        val b = (0x30 - (factor * 0x15).toInt()).coerceIn(0, 255)
        Color(r, g, b)
    }

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

@Composable
fun D3DashboardView(viewModel: TunerViewModel, modifier: Modifier = Modifier) {
    val liveDataStream by viewModel.obdxManager.liveDataStream.collectAsState()
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }

    fun mapLogPointToJson(point: LogDataPoint?): String {
        if (point == null) return "{}"
        return """
            {
                "rpm": ${point.rpm},
                "mph": ${point.mph},
                "mapKpa": ${point.mapKpa},
                "coolantTempF": ${point.coolantTempF},
                "sparkAdvance": ${point.sparkAdvance},
                "shortTermFuelTrimPercent": ${point.shortTermFuelTrimPercent},
                "widebandO2Afr": ${point.widebandO2Afr},
                "throttlePositionPercent": ${point.throttlePositionPercent},
                "massAirFlowGps": ${point.massAirFlowGps},
                "manifoldAirTempF": ${point.manifoldAirTempF},
                "desiredIdleRpm": ${point.desiredIdleRpm},
                "iacPositionSteps": ${point.iacPositionSteps},
                "dwellTimeMs": ${point.dwellTimeMs},
                "knockRetardDegrees": ${point.knockRetardDegrees},
                "knockCount": ${point.knockCount},
                "longTermFuelTrimPercent": ${point.longTermFuelTrimPercent},
                "commandedEquivalenceRatio": ${point.commandedEquivalenceRatio}
            }
        """.trimIndent().replace("\n", "").replace(" ", "")
    }

    LaunchedEffect(liveDataStream) {
        val point = liveDataStream
        if (point != null && webViewRef != null) {
            val json = mapLogPointToJson(point)
            webViewRef?.evaluateJavascript("javascript:updateSensorData('$json')", null)
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                
                setBackgroundColor(0xFF0B0E14.toInt())
                
                webViewClient = android.webkit.WebViewClient()
                loadUrl("file:///android_asset/d3_dashboard.html")
                webViewRef = this
            }
        },
        update = { webView ->
            webViewRef = webView
        },
        modifier = modifier
            .fillMaxSize()
            .border(1.dp, BorderGrey, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
    )
}

@Composable
fun TuneComparisonWidget(
    mostRecent: CalFile?,
    nextModified: CalFile?
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
        border = BorderStroke(1.dp, BorderGrey),
        modifier = Modifier.fillMaxWidth().testTag("tune_comparison_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
                    Text(
                        text = "MOST RECENT VS. NEXT MODIFIED TUNE COMPILER",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                TextButton(
                    onClick = { isExpanded = !isExpanded },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = if (isExpanded) "COLLAPSE" else "COMPARE PARAMETERS",
                        color = GlowingBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Two-column side-by-side overview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Column 1: Most Recent
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14171D)),
                    border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(NeonGreen))
                            Text("MOST RECENT TUNE", color = NeonGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }
                        if (mostRecent != null) {
                            Text(mostRecent.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("OS: ${mostRecent.operatingSystem}", color = Color.LightGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("Idle: ${mostRecent.targetIdleRpm} RPM | Rev: ${mostRecent.revLimitRpm}", color = Color.Gray, fontSize = 9.sp)
                        } else {
                            Text("No recent tune found", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }

                // Column 2: Next Modified
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14171D)),
                    border = BorderStroke(1.dp, BorderGrey)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(AmberGold))
                            Text("NEXT MODIFIED TUNE", color = AmberGold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        }
                        if (nextModified != null) {
                            Text(nextModified.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("OS: ${nextModified.operatingSystem}", color = Color.LightGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("Idle: ${nextModified.targetIdleRpm} RPM | Rev: ${nextModified.revLimitRpm}", color = Color.Gray, fontSize = 9.sp)
                        } else {
                            Text("No second tune found", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Comparison list
            AnimatedVisibility(visible = isExpanded && mostRecent != null && nextModified != null) {
                if (mostRecent != null && nextModified != null) {
                    Column(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth()
                            .background(Color(0xFF0F1115))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "CALIBRATION DIFFERENTIAL REGISTER",
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Divider(color = BorderGrey)

                        // Compare parameters
                        ParameterDiffRow("VATS Status", if (mostRecent.vatsEnabled) "Enabled" else "Disabled", if (nextModified.vatsEnabled) "Enabled" else "Disabled")
                        ParameterDiffRow("Flex Fuel Status", if (mostRecent.flexFuelEnabled) "Enabled" else "Disabled", if (nextModified.flexFuelEnabled) "Enabled" else "Disabled")
                        ParameterDiffRow("MAP Sensor Bar", "${mostRecent.mapSensorBarType}-Bar", "${nextModified.mapSensorBarType}-Bar")
                        ParameterDiffRow("Lean Cruise", if (mostRecent.leanCruiseEnabled) "Enabled" else "Disabled", if (nextModified.leanCruiseEnabled) "Enabled" else "Disabled")
                        ParameterDiffRow("Max Spark Advance", "${mostRecent.sparkMaxAdvance}°", "${nextModified.sparkMaxAdvance}°")
                        ParameterDiffRow("Target Idle RPM", "${mostRecent.targetIdleRpm} RPM", "${nextModified.targetIdleRpm} RPM")
                        ParameterDiffRow("Injector Flow Rate", "${mostRecent.injectorFlowRateLbHr} lb/hr", "${nextModified.injectorFlowRateLbHr} lb/hr")
                        ParameterDiffRow("Rev Limit Cut", "${mostRecent.revLimitRpm} RPM", "${nextModified.revLimitRpm} RPM")
                        ParameterDiffRow("Fan 1 Trigger Temp", "${mostRecent.fan1OnTempF}°F", "${nextModified.fan1OnTempF}°F")
                        ParameterDiffRow("Fan 2 Trigger Temp", "${mostRecent.fan2OnTempF}°F", "${nextModified.fan2OnTempF}°F")
                        ParameterDiffRow("VE Table Scaling", "${mostRecent.veMultiplierPercent}%", "${nextModified.veMultiplierPercent}%")
                    }
                }
            }
        }
    }
}

@Composable
fun ParameterDiffRow(
    label: String,
    valRecent: String,
    valNext: String
) {
    val isDifferent = valRecent != valNext
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.Gray, fontSize = 11.sp, modifier = Modifier.weight(1.2f))
        
        Row(
            modifier = Modifier.weight(2f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = valRecent,
                color = if (isDifferent) NeonGreen else Color.White,
                fontSize = 11.sp,
                fontWeight = if (isDifferent) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(10.dp))
            Text(
                text = valNext,
                color = if (isDifferent) AmberGold else Color.White,
                fontSize = 11.sp,
                fontWeight = if (isDifferent) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start
            )
        }
    }
}
