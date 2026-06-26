package com.example

import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import com.example.data.model.TuningSuggestions
import org.junit.Assert.*
import org.junit.Test

class GeminiTunerTest {

    @Test
    fun testChatMessageCreation() {
        val message = ChatMessage(
            sender = MessageSender.USER,
            text = "I have added a Stage 2 Cam and 36lb injectors."
        )

        assertEquals(MessageSender.USER, message.sender)
        assertEquals("I have added a Stage 2 Cam and 36lb injectors.", message.text)
        assertNotNull(message.id)
        assertNull(message.tuningSuggestions)
    }

    @Test
    fun testTuningSuggestionsMapping() {
        val suggestions = TuningSuggestions(
            targetIdleRpm = 800,
            sparkMaxAdvance = 34,
            injectorFlowRateLbHr = 36.0,
            revLimitRpm = 6200,
            fan1OnTempF = 190,
            fan2OnTempF = 200,
            veMultiplierPercent = 105,
            rationale = "Compensating for larger camshaft duration and scaled fueling.",
            logSessionId = 42
        )

        val message = ChatMessage(
            sender = MessageSender.GEMINI,
            text = "Here are my tuning suggestions.",
            tuningSuggestions = suggestions
        )

        assertEquals(MessageSender.GEMINI, message.sender)
        assertNotNull(message.tuningSuggestions)
        
        val actualSuggestions = message.tuningSuggestions!!
        assertEquals(800, actualSuggestions.targetIdleRpm)
        assertEquals(34, actualSuggestions.sparkMaxAdvance)
        assertEquals(36.0, actualSuggestions.injectorFlowRateLbHr, 0.01)
        assertEquals(6200, actualSuggestions.revLimitRpm)
        assertEquals(190, actualSuggestions.fan1OnTempF)
        assertEquals(200, actualSuggestions.fan2OnTempF)
        assertEquals(105, actualSuggestions.veMultiplierPercent)
        assertEquals("Compensating for larger camshaft duration and scaled fueling.", actualSuggestions.rationale)
        assertEquals(42, actualSuggestions.logSessionId)
    }
}
