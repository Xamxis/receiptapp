package com.example.receiptapp.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit-Interface für unseren eigenen Backend-Proxy (nicht direkt Anthropic!).
 * Der Proxy hält den ANTHROPIC_API_KEY serverseitig geheim und leitet die
 * Anfrage inkl. SYSTEM_PROMPT an die Claude Messages API weiter.
 * Siehe backend-proxy/worker.js für ein Minimalbeispiel.
 */
interface AiProxyApi {
    @POST("parse-receipt")
    suspend fun parseReceipt(@Body request: ParseReceiptRequest): ParsedReceiptResponse
}
