package com.bonsai.app.data.remote

import com.bonsai.app.data.model.Category
import com.bonsai.app.data.model.NutritionTag
import com.bonsai.app.data.model.Receipt
import com.bonsai.app.data.model.ReceiptItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schnittstelle, damit der KI-Anbieter austauschbar bleibt
 * (z. B. Umstieg auf ein anderes Modell oder On-Device-Fallback).
 */
interface ReceiptAiParser {
    suspend fun parse(rawOcrText: String): AiParseResult
}

sealed interface AiParseResult {
    data class Success(val receipt: Receipt, val items: List<ReceiptItem>) : AiParseResult
    data class Failure(val reason: String) : AiParseResult
}

/**
 * Implementierung über den Backend-Proxy, der wiederum die Anthropic
 * Messages API mit Claude aufruft. Der Prompt (SYSTEM_PROMPT) ist hier
 * dokumentiert, auch wenn er serverseitig ausgeführt wird – so bleibt er
 * versionierbar und nachvollziehbar für dieses Repo.
 */
@Singleton
class ClaudeReceiptParser @Inject constructor(
    private val api: AiProxyApi
) : ReceiptAiParser {

    override suspend fun parse(rawOcrText: String): AiParseResult = withContext(Dispatchers.IO) {
        if (rawOcrText.isBlank()) return@withContext AiParseResult.Failure("Kein Text erkannt (OCR leer).")

        try {
            val response = api.parseReceipt(ParseReceiptRequest(rawText = rawOcrText))

            val items = response.items.map { item ->
                ReceiptItem(
                    receiptId = 0, // wird beim Insert in Room gesetzt
                    name = item.name,
                    priceEuro = item.price,
                    quantity = item.quantity,
                    category = Category.fromKeyOrNull(item.category) ?: Category.SONSTIGES,
                    nutritionTag = NutritionTag.fromKeyOrNull(item.nutritionTag),
                    healthNote = item.healthNote
                )
            }

            val receipt = Receipt(
                merchant = response.merchant,
                date = runCatching { LocalDate.parse(response.date) }.getOrDefault(LocalDate.now()),
                totalEuro = response.total,
                rawOcrText = rawOcrText,
                aiConfidence = response.confidence
            )

            AiParseResult.Success(receipt, items)
        } catch (e: Exception) {
            AiParseResult.Failure(e.message ?: "Unbekannter Fehler beim KI-Parsing")
        }
    }

    companion object {
        /**
         * System-Prompt für die Claude Messages API (server-seitig verwendet,
         * siehe backend-proxy/worker.js). Zentrale Design-Entscheidungen:
         *
         * 1. Striktes JSON-Only-Format -> einfaches, robustes Parsing im Client.
         * 2. Enum-Werte für category/nutritionTag exakt vorgeben, damit das
         *    Kotlin-Mapping (Category.fromKeyOrNull) nicht scheitert.
         * 3. Few-Shot-Beispiel mit typischem AT/DE-Kassenzettel-Format
         *    (Komma als Dezimaltrennzeichen im Rohtext, aber Punkt im JSON-Output).
         * 4. Anweisung, bei Unsicherheit "confidence" niedriger anzusetzen,
         *    statt zu halluzinieren - UI zeigt dann eine Korrektur-Aufforderung.
         */
        const val SYSTEM_PROMPT = """
Du bist ein spezialisierter Parser für österreichische/deutsche Kassenzettel (OCR-Rohtext, oft mit Fehlern).
Deine einzige Aufgabe: Extrahiere die Daten und antworte AUSSCHLIESSLICH mit validem JSON nach folgendem Schema.
Keine Erklärung, kein Markdown, kein Codeblock - nur das reine JSON-Objekt.

Schema:
{
  "merchant": string,              // Händlername, z. B. "Spar", "Hofer", "Billa"
  "date": string,                  // Format YYYY-MM-DD. Wenn nicht erkennbar: heutiges Datum
  "total": number,                 // Gesamtbetrag in Euro, Punkt als Dezimaltrennzeichen
  "items": [
    {
      "name": string,              // bereinigter, lesbarer Produktname (keine Kassenkürzel)
      "price": number,             // Einzelpreis in Euro
      "quantity": number,          // Menge, Default 1
      "category": string,          // GENAU einer von: LEBENSMITTEL_FRISCH, LEBENSMITTEL_VERARBEITET,
                                    // SNACKS_UNTERWEGS, GETRAENKE, RESTAURANT_LIEFERDIENST,
                                    // DROGERIE_HYGIENE, HAUSHALT, KLEIDUNG, FREIZEIT, TRANSPORT,
                                    // GESUNDHEIT, SONSTIGES
      "nutritionTag": string|null, // GENAU einer von: FRISCH_UNVERARBEITET, VOLLWERTIG, VERARBEITET,
                                    // STARK_VERARBEITET, ZUCKERHALTIG, ALKOHOL, NICHT_LEBENSMITTEL
                                    // (null wenn kein Lebensmittel/Getränk)
      "healthNote": string|null    // 1 kurzer Satz konkrete, konstruktive Ernährungs-Einordnung,
                                    // NUR bei Lebensmitteln/Getränken, sonst null
    }
  ],
  "confidence": number             // 0.0-1.0, deine Einschätzung der Extraktionssicherheit
}

Regeln:
- Rabatte, Pfand-Rückgaben und Zwischensummen NICHT als eigene Posten aufnehmen.
- Wenn der Rohtext stark verrauscht ist, versuche dennoch eine bestmögliche Zuordnung
  und senke stattdessen "confidence".
- Beträge im Rohtext nutzen oft Komma als Dezimaltrennzeichen (z. B. "3,50") -
  wandle das im JSON-Output in Punkt-Notation um (3.50).
- Erfinde keine Posten, die nicht im Text erkennbar sind.

Beispiel Input:
SPAR MARKT
12.03.2026
WURSTSEMMEL      3,50
COLA 0,5L        2,10
APFEL BIO 1KG    2,49
SUMME            8,09

Beispiel Output:
{"merchant":"Spar","date":"2026-03-12","total":8.09,"items":[{"name":"Wurstsemmel","price":3.50,"quantity":1,"category":"SNACKS_UNTERWEGS","nutritionTag":"STARK_VERARBEITET","healthNote":"Viel Salz und Weißmehl - als Alternative z. B. Vollkornsemmel mit magerem Aufschnitt."},{"name":"Cola 0,5L","price":2.10,"quantity":1,"category":"GETRAENKE","nutritionTag":"ZUCKERHALTIG","healthNote":"Hoher Zuckergehalt - Wasser oder ungesüßter Tee wäre eine kalorienfreie Alternative."},{"name":"Apfel Bio 1kg","price":2.49,"quantity":1,"category":"LEBENSMITTEL_FRISCH","nutritionTag":"FRISCH_UNVERARBEITET","healthNote":"Gute Wahl - frisches, unverarbeitetes Obst."}],"confidence":0.94}
"""
    }
}
