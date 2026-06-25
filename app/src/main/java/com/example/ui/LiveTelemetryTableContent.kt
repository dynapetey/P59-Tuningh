package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CustomPid
import com.example.allAvailablePids
import com.example.getPidColor
import com.example.data.model.LogDataPoint
import com.example.hardware.ConnectionState
import com.example.ui.TunerViewModel

private val CardSurfaceColor = Color(0xFF1B1E24)
private val BorderGrey = Color(0xFF2C313C)
private val NeonGreen = Color(0xFF00FF87)
private val AmberGold = Color(0xFFFF9F1C)
private val GlowingBlue = Color(0xFF00D1FF)
private val WarningRed = Color(0xFFFF4E60)
private val TerminalPurple = Color(0xFF9E00FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTelemetryTableContent(
    viewModel: TunerViewModel,
    liveDataStream: LogDataPoint?,
    connectionState: ConnectionState,
    activeSessionId: Int?
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var isFrozen by remember { mutableStateOf(false) }

    // Map to keep track of Min/Max values seen during the active logging session
    val minMaxMap = remember { mutableStateMapOf<String, Pair<Float, Float>>() }
    
    // We also keep track of the last frozen point when frozen is active
    var frozenDataPoint by remember { mutableStateOf<LogDataPoint?>(null) }

    // Auto-update min/max map when liveDataStream changes
    LaunchedEffect(liveDataStream) {
        val point = liveDataStream
        if (point != null && !isFrozen) {
            allAvailablePids.forEach { pid ->
                val rawValue = pid.getValue(point)
                val current = minMaxMap[pid.id]
                if (current == null) {
                    minMaxMap[pid.id] = Pair(rawValue, rawValue)
                } else {
                    val newMin = minOf(current.first, rawValue)
                    val newMax = maxOf(current.second, rawValue)
                    minMaxMap[pid.id] = Pair(newMin, newMax)
                }
            }
        }
    }

    // Reset min/max values when a new session starts
    LaunchedEffect(activeSessionId) {
        if (activeSessionId != null) {
            minMaxMap.clear()
            isFrozen = false
            frozenDataPoint = null
        }
    }

    // Capture the current point for freeze-frame
    val displayedPoint = if (isFrozen) {
        frozenDataPoint
    } else {
        liveDataStream
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("telemetry_table_module")
    ) {
        // --- CONTROL BAR ---
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
                    // Session Status Indicator
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    if (activeSessionId != null) {
                                        if (isFrozen) AmberGold else WarningRed
                                    } else {
                                        Color.Gray
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                activeSessionId != null && isFrozen -> "SESSION RECORDING (FREEZE-FRAME ACTIVE)"
                                activeSessionId != null -> "SESSION RECORDING (#$activeSessionId) • VPW ACTIVE"
                                else -> "LOGGER SERVICE STANDBY"
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Start/Stop recording controls
                    if (activeSessionId == null) {
                        Button(
                            onClick = { viewModel.startLoggingSession() },
                            colors = ButtonDefaults.buttonColors(containerColor = WarningRed),
                            shape = RoundedCornerShape(6.dp),
                            enabled = connectionState != ConnectionState.DISCONNECTED,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(34.dp).testTag("table_start_log_btn")
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
                            modifier = Modifier.height(34.dp).testTag("table_stop_log_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Stop", modifier = Modifier.size(14.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("STOP LOG", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Diagnostic Controls: Freeze Frame and Min/Max Reset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Freeze Frame Button
                    Button(
                        onClick = {
                            if (!isFrozen) {
                                frozenDataPoint = liveDataStream
                                isFrozen = true
                            } else {
                                isFrozen = false
                                frozenDataPoint = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFrozen) AmberGold.copy(alpha = 0.2f) else Color(0xFF14171E)
                        ),
                        border = BorderStroke(1.dp, if (isFrozen) AmberGold else BorderGrey),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = if (isFrozen) Icons.Default.Lock else Icons.Default.Info,
                            contentDescription = "Freeze",
                            tint = if (isFrozen) AmberGold else Color.Gray,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFrozen) "RELEASE FRAME" else "FREEZE FRAME",
                            color = if (isFrozen) Color.White else Color.Gray,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Reset Stats Button
                    Button(
                        onClick = {
                            minMaxMap.clear()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14171E)),
                        border = BorderStroke(1.dp, BorderGrey),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = Color.Gray,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "RESET MIN/MAX",
                            color = Color.Gray,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- FILTERS & SEARCH ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search Input Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter PIDs...", color = Color.Gray, fontSize = 11.sp) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray, modifier = Modifier.size(14.dp)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF14171D),
                    unfocusedContainerColor = Color(0xFF14171D),
                    focusedBorderColor = GlowingBlue,
                    unfocusedBorderColor = BorderGrey,
                    focusedLabelColor = GlowingBlue
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .weight(1.3f)
                    .height(44.dp)
                    .testTag("pid_table_search_input"),
                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
            )

            // Category Scroll Row
            Box(
                modifier = Modifier
                    .weight(1.7f)
                    .horizontalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val categories = listOf("ALL", "CORE", "AIR", "FUEL", "ENGINE")
                    categories.forEach { category ->
                        val isSelected = selectedCategory == category
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isSelected) GlowingBlue.copy(alpha = 0.15f) else Color(0xFF14171E),
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) GlowingBlue else BorderGrey,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = category,
                                color = if (isSelected) Color.White else Color.Gray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- EMPTY STATE WRAPPER ---
        if (connectionState == ConnectionState.DISCONNECTED && liveDataStream == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF07080B), shape = RoundedCornerShape(8.dp))
                    .border(1.dp, BorderGrey, shape = RoundedCornerShape(8.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(WarningRed.copy(alpha = 0.1f), RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Not Connected",
                            tint = WarningRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "OBDX PRO INTERFACE OFFLINE",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Please connect to OBDX Pro device via the system status bar first to receive live vehicle J1850 VPW telemetry.",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        } else {
            // --- DATA TABLE ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF07080B)),
                border = BorderStroke(1.dp, BorderGrey)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // TABLE HEADER ROW
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF14171E))
                            .border(BorderStroke(1.dp, BorderGrey))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PARAMETER", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                        Text("PID", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                        Text("VALUE", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
                        Text("UNIT", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.4f))
                        Text("MIN", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                        Text("MAX", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.5f))
                        Text("STATUS", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
                    }

                    // Filter and render the parameters
                    val filteredPids = allAvailablePids.filter { pid ->
                        // Match category filter
                        val matchesCategory = when (selectedCategory) {
                            "ALL" -> true
                            "CORE" -> pid.id in listOf("RPM", "MPH", "TPS")
                            "AIR" -> pid.id in listOf("MAP", "MAF", "IAT")
                            "FUEL" -> pid.id in listOf("AFR", "STFT", "LTFT", "EQ_RATIO")
                            "ENGINE" -> pid.id in listOf("ECT", "IAC", "SPARK", "KNOCK", "KNK_CNT")
                            else -> true
                        }
                        
                        // Match search query
                        val matchesSearch = pid.name.contains(searchQuery, ignoreCase = true) || 
                                              pid.id.contains(searchQuery, ignoreCase = true) || 
                                              pid.hexCode.contains(searchQuery, ignoreCase = true)

                        matchesCategory && matchesSearch
                    }

                    if (filteredPids.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No telemetry parameters found matching search filters.",
                                color = Color.DarkGray,
                                fontSize = 10.sp
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(filteredPids) { index, pid ->
                                val rawVal = pid.getValue(displayedPoint)
                                val formattedStr = pid.formattedValue(displayedPoint)
                                val glowColor = getPidColor(pid.id)

                                // Retrieve stored Min/Max
                                val minVal = minMaxMap[pid.id]?.first ?: rawVal
                                val maxVal = minMaxMap[pid.id]?.second ?: rawVal

                                // Alternating rows
                                val rowBg = if (index % 2 == 0) Color.Transparent else Color(0xFF0F1115)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(rowBg)
                                        .border(BorderStroke(0.5.dp, Color(0xFF181B21)))
                                        .padding(horizontal = 10.dp, vertical = 10.dp)
                                        .testTag("telemetry_row_${pid.id.lowercase()}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. PARAMETER COLUMN
                                    Column(modifier = Modifier.weight(1.2f)) {
                                        Text(
                                            text = pid.name,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .background(glowColor, RoundedCornerShape(2.5.dp))
                                            )
                                            Text(
                                                text = pid.id,
                                                color = Color.Gray,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    // 2. PID HEX COLUMN
                                    Text(
                                        text = pid.hexCode,
                                        color = Color.DarkGray,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(0.5f)
                                    )

                                    // 3. VALUE COLUMN (Bold dynamic highlight)
                                    Text(
                                        text = if (displayedPoint != null) formattedStr.substringBefore(" ") else "---",
                                        color = glowColor,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(0.8f)
                                    )

                                    // 4. UNIT COLUMN
                                    Text(
                                        text = pid.unit,
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        modifier = Modifier.weight(0.4f)
                                    )

                                    // 5. MIN COLUMN
                                    Text(
                                        text = if (displayedPoint != null) {
                                            if (pid.unit == "%" || pid.unit == "°F" || pid.unit == "steps" || pid.unit == "knocks" || pid.unit == "RPM" || pid.unit == "MPH") {
                                                "${minVal.toInt()}"
                                            } else {
                                                String.format("%.1f", minVal)
                                            }
                                        } else "---",
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(0.5f)
                                    )

                                    // 6. MAX COLUMN
                                    Text(
                                        text = if (displayedPoint != null) {
                                            if (pid.unit == "%" || pid.unit == "°F" || pid.unit == "steps" || pid.unit == "knocks" || pid.unit == "RPM" || pid.unit == "MPH") {
                                                "${maxVal.toInt()}"
                                            } else {
                                                String.format("%.1f", maxVal)
                                            }
                                        } else "---",
                                        color = Color.LightGray,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(0.5f)
                                    )

                                    // 7. STATUS & BAR GAUGE COLUMN
                                    val (statusLabel, statusColor, progress) = getPidStatus(pid.id, rawVal)
                                    Column(
                                        modifier = Modifier.weight(0.9f),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = statusLabel,
                                                color = statusColor,
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.SansSerif
                                            )
                                            if (displayedPoint != null) {
                                                Text(
                                                    text = "${(progress * 100).toInt()}%",
                                                    color = Color.DarkGray,
                                                    fontSize = 7.5.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        LinearProgressIndicator(
                                            progress = { if (displayedPoint != null) progress else 0f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(1.5.dp)),
                                            color = statusColor,
                                            trackColor = Color(0xFF14171E)
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

private fun getPidStatus(pidId: String, value: Float): Triple<String, Color, Float> {
    return when (pidId) {
        "RPM" -> {
            val progress = (value / 7000f).coerceIn(0f, 1f)
            when {
                value >= 6000f -> Triple("REDLINE", WarningRed, progress)
                value >= 4800f -> Triple("HIGH", AmberGold, progress)
                else -> Triple("NORMAL", NeonGreen, progress)
            }
        }
        "ECT" -> {
            val progress = (value / 250f).coerceIn(0f, 1f)
            when {
                value >= 225f -> Triple("OVERHEAT", WarningRed, progress)
                value >= 210f -> Triple("WARM", AmberGold, progress)
                value < 140f -> Triple("COLD", GlowingBlue, progress)
                else -> Triple("OPTIMAL", NeonGreen, progress)
            }
        }
        "KNOCK" -> {
            val progress = (value / 10f).coerceIn(0f, 1f)
            if (value > 0.1f) {
                Triple("DETONATION", WarningRed, progress)
            } else {
                Triple("STABLE", NeonGreen, progress)
            }
        }
        "KNK_CNT" -> {
            val progress = (value / 20f).coerceIn(0f, 1f)
            if (value > 0) {
                Triple("WARNING", WarningRed, progress)
            } else {
                Triple("STABLE", NeonGreen, progress)
            }
        }
        "AFR" -> {
            val progress = ((value - 10f) / 8f).coerceIn(0f, 1f)
            when {
                value > 15.5f -> Triple("LEAN", AmberGold, progress)
                value < 12.0f -> Triple("RICH", GlowingBlue, progress)
                else -> Triple("STOCH", NeonGreen, progress)
            }
        }
        "TPS" -> {
            val progress = (value / 100f).coerceIn(0f, 1f)
            when {
                value > 85f -> Triple("WOT", GlowingBlue, progress)
                value > 15f -> Triple("PART", NeonGreen, progress)
                else -> Triple("IDLE", TerminalPurple, progress)
            }
        }
        "MPH" -> {
            val progress = (value / 120f).coerceIn(0f, 1f)
            when {
                value > 90f -> Triple("FAST", WarningRed, progress)
                value > 65f -> Triple("CRUISE", NeonGreen, progress)
                else -> Triple("LOW", Color.Gray, progress)
            }
        }
        "MAP" -> {
            val progress = (value / 200f).coerceIn(0f, 1f)
            when {
                value > 105f -> Triple("BOOST", GlowingBlue, progress)
                else -> Triple("VACUUM", NeonGreen, progress)
            }
        }
        "SPARK" -> {
            val progress = (value / 50f).coerceIn(0f, 1f)
            when {
                value > 35f -> Triple("ADVANCED", GlowingBlue, progress)
                value < 5f -> Triple("RETARD", WarningRed, progress)
                else -> Triple("OPTIMAL", NeonGreen, progress)
            }
        }
        "STFT" -> {
            val progress = ((value + 25f) / 50f).coerceIn(0f, 1f)
            when {
                value > 15f -> Triple("ADD FUEL", AmberGold, progress)
                value < -15f -> Triple("SUB FUEL", GlowingBlue, progress)
                else -> Triple("NORMAL", NeonGreen, progress)
            }
        }
        "LTFT" -> {
            val progress = ((value + 25f) / 50f).coerceIn(0f, 1f)
            when {
                value > 10f -> Triple("LEARN ADD", AmberGold, progress)
                value < -10f -> Triple("LEARN SUB", GlowingBlue, progress)
                else -> Triple("STABLE", NeonGreen, progress)
            }
        }
        "IAT" -> {
            val progress = (value / 180f).coerceIn(0f, 1f)
            when {
                value > 140f -> Triple("HOT AIR", AmberGold, progress)
                else -> Triple("NORMAL", NeonGreen, progress)
            }
        }
        else -> {
            val progress = 0.5f
            Triple("MONITOR", NeonGreen, progress)
        }
    }
}
