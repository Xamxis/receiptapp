package com.bonsai.app.data.repository

import com.bonsai.app.data.model.NutritionGoal
import com.bonsai.app.data.model.ReceiptWithItems
import com.bonsai.app.data.remote.AiProxyApi
import com.bonsai.app.data.remote.ChatMessageDto
import com.bonsai.app.data.remote.ChatRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Eine Nachricht im Chatverlauf (UI-Modell). */
data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val isError: Boolean = false
)

sealed interface ChatResult {
    data class Success(val reply: String) : ChatResult
    data class Failure(val reason: String) : ChatResult
}

/**
 * Verbindet den Chat mit den lokal gespeicherten Belegdaten.
 *
 * Datenschutz-Prinzip: Es wird eine kompakte Zusammenfassung erzeugt
 * (Summen, Kategorien, häufigste Produkte) statt aller Rohbelege – die KI bekommt
 * genug Kontext für nützliche Antworten, aber nicht die vollständige Einkaufshistorie.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val api: AiProxyApi
) {

    suspend fun send(
        history: List<ChatMessage>,
        receipts: List<ReceiptWithItems>,
        goal: NutritionGoal
    ): ChatResult = withContext(Dispatchers.IO) {
        try {
            val response = api.chat(
                ChatRequest(
                    messages = history
                        .filterNot { it.isError }
                        .map { ChatMessageDto(if (it.fromUser) "user" else "assistant", it.text) },
                    dataSummary = buildSummary(receipts),
                    nutritionGoal = goal.displayName
                )
            )
            ChatResult.Success(response.reply)
        } catch (e: Exception) {
            ChatResult.Failure(e.message ?: "Verbindung zum Berater fehlgeschlagen")
        }
    }

    /** Erzeugt die kompakte Datenzusammenfassung, die als Kontext mitgeschickt wird. */
    fun buildSummary(receipts: List<ReceiptWithItems>): String {
        if (receipts.isEmpty()) return "Der Nutzer hat noch keine Belege gescannt."

        val items = receipts.flatMap { it.items }
        val total = receipts.sumOf { it.receipt.totalEuro }

        val byCategory = items
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.priceEuro * it.quantity } }
            .toList()
            .sortedByDescending { it.second }
            .take(6)
            .joinToString("; ") { (cat, sum) ->
                "%s: %.2f €".format(Locale.GERMANY, cat.displayName, sum)
            }

        val topProducts = items
            .groupBy { it.name.trim().lowercase(Locale.GERMANY) }
            .toList()
            .sortedByDescending { it.second.size }
            .take(8)
            .joinToString("; ") { (_, list) ->
                "%s (%d×, %.2f €)".format(
                    Locale.GERMANY,
                    list.first().name,
                    list.size,
                    list.sumOf { it.priceEuro * it.quantity }
                )
            }

        val merchants = receipts
            .groupBy { it.receipt.merchant.trim() }
            .toList()
            .sortedByDescending { (_, l) -> l.sumOf { it.receipt.totalEuro } }
            .take(5)
            .joinToString("; ") { (m, l) ->
                "%s (%.2f €)".format(Locale.GERMANY, m, l.sumOf { it.receipt.totalEuro })
            }

        val nutrition = items
            .mapNotNull { it.nutritionTag }
            .groupingBy { it.displayName }
            .eachCount()
            .toList()
            .joinToString("; ") { (tag, count) -> "$tag: $count" }

        return buildString {
            appendLine("Zeitraum: die zuletzt gescannten ${receipts.size} Belege.")
            appendLine("Gesamtausgaben: %.2f €.".format(Locale.GERMANY, total))
            appendLine("Ausgaben nach Kategorie: $byCategory.")
            appendLine("Häufigste Produkte: $topProducts.")
            appendLine("Geschäfte: $merchants.")
            appendLine("Nährwert-Einordnung der Posten: $nutrition.")
        }
    }
}
