package com.bonsai.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Anfrage an den Chat-Endpunkt des Proxys.
 *
 * Wichtig für den Datenschutz: Wir schicken NICHT die komplette Datenbank mit,
 * sondern nur eine kompakte, anonymisierte Zusammenfassung ([dataSummary]) –
 * genug Kontext für sinnvolle Antworten, ohne alle Rohdaten preiszugeben.
 */
@Serializable
data class ChatRequest(
    val messages: List<ChatMessageDto>,
    @SerialName("data_summary") val dataSummary: String,
    @SerialName("nutrition_goal") val nutritionGoal: String
)

@Serializable
data class ChatMessageDto(
    val role: String,   // "user" | "assistant"
    val content: String
)

@Serializable
data class ChatResponse(
    val reply: String
)
