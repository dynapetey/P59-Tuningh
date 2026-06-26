package com.example.data.model

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val tuningSuggestions: TuningSuggestions? = null
)

enum class MessageSender {
    USER,
    GEMINI
}

data class TuningSuggestions(
    val targetIdleRpm: Int,
    val sparkMaxAdvance: Int,
    val injectorFlowRateLbHr: Double,
    val revLimitRpm: Int,
    val fan1OnTempF: Int,
    val fan2OnTempF: Int,
    val veMultiplierPercent: Int,
    val rationale: String,
    val logSessionId: Int? = null // ID of the LogSession used for adjustments
)
