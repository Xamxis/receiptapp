package com.example.receiptapp.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Anfrage an unseren Backend-Proxy (nicht direkt an Anthropic, siehe README) ---

@Serializable
data class ParseReceiptRequest(
    @SerialName("raw_text") val rawText: String,
    @SerialName("known_merchant_hint") val knownMerchantHint: String? = null
)

// --- Erwartetes JSON-Antwortschema, das der Proxy 1:1 von Claude durchreicht ---

@Serializable
data class ParsedReceiptResponse(
    val merchant: String,
    val date: String, // ISO-8601 "yyyy-MM-dd"
    val total: Double,
    val items: List<ParsedItem>,
    val confidence: Float = 1.0f
)

@Serializable
data class ParsedItem(
    val name: String,
    val price: Double,
    val quantity: Double = 1.0,
    val category: String,
    val nutritionTag: String? = null,
    val healthNote: String? = null
)
