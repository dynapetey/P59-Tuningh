package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import com.example.data.model.TuningSuggestions
import kotlinx.coroutines.launch

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
fun GeminiTunerTabContent(viewModel: TunerViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val chatMessages by viewModel.chatMessages.collectAsState()
    val chatLoading by viewModel.chatLoading.collectAsState()
    val engineModifications by viewModel.engineModifications.collectAsState()
    val selectedCal by viewModel.selectedCal.collectAsState()
    val logSessions by viewModel.logSessions.collectAsState()
    
    val newestCal = selectedCal // Or from calibrations state
    val newestLog = logSessions.firstOrNull()
    
    var typedMessage by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom of conversation on new message
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGreyBackground)
            .padding(12.dp)
    ) {
        // --- 1. Tuner Target Status Bar ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14171D)),
            border = BorderStroke(1.dp, BorderGrey),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Status",
                            tint = GlowingBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ACTIVE WORKSPACE TARGETS",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    IconButton(
                        onClick = { viewModel.clearChat() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Clear Chat",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // BIN File Target Info
                    Column(modifier = Modifier.weight(1.0f)) {
                        Text("Active BIN (Newest):", color = Color.Gray, fontSize = 10.sp)
                        Text(
                            text = newestCal?.name ?: "No BIN file selected",
                            color = if (newestCal != null) NeonGreen else Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Log File Target Info
                    Column(modifier = Modifier.weight(1.0f)) {
                        Text("Active Log (Current):", color = Color.Gray, fontSize = 10.sp)
                        Text(
                            text = newestLog?.sessionName ?: "No log recorded yet",
                            color = if (newestLog != null) GlowingBlue else Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        
        // --- 2. Chat Conversation Box ---
        Card(
            colors = CardDefaults.cardColors(containerColor = CardSurfaceColor),
            border = BorderStroke(1.dp, BorderGrey),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(chatMessages) { message ->
                        ChatBubbleItem(
                            message = message,
                            newestCal = newestCal,
                            onApplySuggestions = { viewModel.applyTuningSuggestions(it) }
                        )
                    }
                    
                    if (chatLoading) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = TerminalPurple,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Gemini is analyzing files and modifications...",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // --- 3. Chat Form Input Controls ---
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = typedMessage,
                onValueChange = { typedMessage = it },
                placeholder = { Text("Ask Gemini or type tuning request...", color = Color.Gray, fontSize = 13.sp) },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedContainerColor = CardSurfaceColor,
                    unfocusedContainerColor = CardSurfaceColor,
                    focusedIndicatorColor = GlowingBlue,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = GlowingBlue
                ),
                shape = RoundedCornerShape(10.dp),
                maxLines = 2,
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input")
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Button(
                onClick = {
                    if (typedMessage.isNotBlank()) {
                        viewModel.sendChatMessage(typedMessage)
                        typedMessage = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GlowingBlue),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(52.dp)
                    .testTag("send_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = DarkGreyBackground,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // --- 4. Engine Modifications Input Section (Underneath) ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14171D)),
            border = BorderStroke(1.dp, BorderGrey),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Engine Mod",
                        tint = AmberGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ENGINE MODIFICATIONS",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                Text(
                    text = "Type performance mods below. Gemini will factor these specifications into the tune suggestions.",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                
                TextField(
                    value = engineModifications,
                    onValueChange = { viewModel.updateEngineModifications(it) },
                    placeholder = { Text("e.g. Stage 2 Camshaft, CAI, 36lb Injectors, high-flow exhaust...", color = Color.Gray, fontSize = 12.sp) },
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedContainerColor = CardSurfaceColor,
                        unfocusedContainerColor = CardSurfaceColor,
                        focusedIndicatorColor = AmberGold,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = AmberGold
                    ),
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("engine_mods_input")
                )
            }
        }
    }
}

@Composable
fun ChatBubbleItem(
    message: ChatMessage,
    newestCal: com.example.data.model.CalFile?,
    onApplySuggestions: (TuningSuggestions) -> Unit
) {
    val isUser = message.sender == MessageSender.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) Color(0xFF232A35) else Color(0xFF1B1E24)
    val borderStroke = if (isUser) BorderStroke(1.dp, BorderGrey) else BorderStroke(1.dp, TerminalPurple.copy(alpha = 0.5f))
    
    Column(
        horizontalAlignment = alignment,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (isUser) Icons.Default.Person else Icons.Default.Star,
                contentDescription = null,
                tint = if (isUser) GlowingBlue else TerminalPurple,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isUser) "You" else "Gemini Tuner",
                color = Color.Gray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(bubbleColor)
                .border(borderStroke, RoundedCornerShape(12.dp))
                .padding(12.dp)
                .widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        
        // Render tuning suggestions card if present
        if (message.tuningSuggestions != null) {
            Spacer(modifier = Modifier.height(8.dp))
            TuningSuggestionsCard(
                suggestions = message.tuningSuggestions,
                currentCal = newestCal,
                onApply = onApplySuggestions
            )
        }
    }
}

@Composable
fun TuningSuggestionsCard(
    suggestions: TuningSuggestions,
    currentCal: com.example.data.model.CalFile?,
    onApply: (TuningSuggestions) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161A22)),
        border = BorderStroke(1.dp, GlowingBlue.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Suggestions Available",
                    tint = NeonGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "GEMINI TUNING RECOMMENDATIONS",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Grid of parameters comparison
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ParameterComparisonRow(
                    label = "Target Idle RPM",
                    oldValue = currentCal?.targetIdleRpm?.toString() ?: "650",
                    newValue = "${suggestions.targetIdleRpm} RPM"
                )
                ParameterComparisonRow(
                    label = "Max Spark timing",
                    oldValue = "${currentCal?.sparkMaxAdvance ?: 36}°",
                    newValue = "${suggestions.sparkMaxAdvance}°"
                )
                ParameterComparisonRow(
                    label = "Injector Flow Scale",
                    oldValue = "${currentCal?.injectorFlowRateLbHr ?: 24.8} lb/hr",
                    newValue = "${suggestions.injectorFlowRateLbHr} lb/hr"
                )
                ParameterComparisonRow(
                    label = "Rev Limit RPM",
                    oldValue = "${currentCal?.revLimitRpm ?: 5900} RPM",
                    newValue = "${suggestions.revLimitRpm} RPM"
                )
                ParameterComparisonRow(
                    label = "Cooling Fan 1 Temp",
                    oldValue = "${currentCal?.fan1OnTempF ?: 205}°F",
                    newValue = "${suggestions.fan1OnTempF}°F"
                )
                ParameterComparisonRow(
                    label = "Cooling Fan 2 Temp",
                    oldValue = "${currentCal?.fan2OnTempF ?: 215}°F",
                    newValue = "${suggestions.fan2OnTempF}°F"
                )
                ParameterComparisonRow(
                    label = "VE Global Multiplier",
                    oldValue = "${currentCal?.veMultiplierPercent ?: 100}%",
                    newValue = "${suggestions.veMultiplierPercent}%"
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Rationale text block
            Text(
                text = "Tuning Rationale:",
                color = Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = suggestions.rationale,
                color = Color.LightGray,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // FLY TUNE TO BIN & DELETE LOG BUTTON
            Button(
                onClick = { onApply(suggestions) },
                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apply_tune_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = DarkGreyBackground,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "FLY TUNE TO BIN & DELETE LOG",
                    color = DarkGreyBackground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ParameterComparisonRow(
    label: String,
    oldValue: String,
    newValue: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E232B), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = oldValue, color = Color.Gray, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = GlowingBlue,
                modifier = Modifier.size(10.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = newValue, color = NeonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
