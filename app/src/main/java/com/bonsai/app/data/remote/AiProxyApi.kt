package com.bonsai.app.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit-Interface für den eigenen Backend-Proxy (nicht direkt Anthropic!).
 * Der Proxy hält den API-Key serverseitig geheim.
 * Siehe backend-proxy/worker.js.
 */
interface AiProxyApi {

    /** Wandelt OCR-Rohtext eines Belegs in strukturierte Posten um. */
    @POST("parse-receipt")
    suspend fun parseReceipt(@Body request: ParseReceiptRequest): ParsedReceiptResponse

    /** Freier Dialog mit dem Berater, angereichert um eine Zusammenfassung der eigenen Daten. */
    @POST("chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse
}
