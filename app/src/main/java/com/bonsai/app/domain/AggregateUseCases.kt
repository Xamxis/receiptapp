package com.bonsai.app.domain

import com.bonsai.app.data.model.MerchantSummary
import com.bonsai.app.data.model.ProductSort
import com.bonsai.app.data.model.ProductSummary
import com.bonsai.app.data.model.ReceiptGrouping
import com.bonsai.app.data.model.ReceiptSort
import com.bonsai.app.data.model.ReceiptWithItems
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Fasst alle Einzelposten über sämtliche Belege zu Produkten zusammen:
 * "Wurstsemmel – 7x gekauft – 24,50 € gesamt".
 *
 * Namen werden normalisiert (Kleinschreibung, doppelte Leerzeichen), damit
 * "Wurstsemmel" und "WURSTSEMMEL " aus verschiedenen OCR-Durchläufen als
 * dasselbe Produkt zählen.
 */
class AggregateProductsUseCase @Inject constructor() {

    operator fun invoke(
        receipts: List<ReceiptWithItems>,
        sort: ProductSort = ProductSort.HAEUFIGKEIT,
        query: String = ""
    ): List<ProductSummary> {
        val rows = receipts.flatMap { rwi -> rwi.items.map { rwi to it } }

        val grouped = rows.groupBy { (_, item) -> normalize(item.name) }

        val summaries = grouped.map { (_, entries) ->
            val latest = entries.maxByOrNull { (rwi, _) -> rwi.receipt.date }
            val items = entries.map { it.second }
            val totalSpent = items.sumOf { it.priceEuro * it.quantity }
            val count = items.sumOf { it.quantity }.toInt().coerceAtLeast(items.size)

            ProductSummary(
                // Für die Anzeige die häufigste Original-Schreibweise nehmen
                name = items.groupingBy { it.name }.eachCount().maxByOrNull { it.value }?.key
                    ?: items.first().name,
                purchaseCount = count,
                totalSpentEuro = totalSpent,
                averagePriceEuro = if (count > 0) totalSpent / count else 0.0,
                category = items.groupingBy { it.category }.eachCount()
                    .maxByOrNull { it.value }!!.key,
                nutritionTag = items.mapNotNull { it.nutritionTag }
                    .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
                lastMerchant = latest?.first?.receipt?.merchant.orEmpty(),
                lastPurchaseIso = latest?.first?.receipt?.date?.toString().orEmpty()
            )
        }

        val filtered = if (query.isBlank()) summaries else summaries.filter {
            it.name.contains(query.trim(), ignoreCase = true)
        }

        return when (sort) {
            ProductSort.HAEUFIGKEIT -> filtered.sortedByDescending { it.purchaseCount }
            ProductSort.AUSGABEN -> filtered.sortedByDescending { it.totalSpentEuro }
            ProductSort.NAME_AZ -> filtered.sortedBy { it.name.lowercase(Locale.GERMANY) }
            ProductSort.ZULETZT -> filtered.sortedByDescending { it.lastPurchaseIso }
        }
    }

    private fun normalize(name: String) =
        name.trim().lowercase(Locale.GERMANY).replace(Regex("\\s+"), " ")
}

/** Fasst Ausgaben pro Geschäft zusammen. */
class AggregateMerchantsUseCase @Inject constructor() {

    operator fun invoke(receipts: List<ReceiptWithItems>): List<MerchantSummary> =
        receipts
            .groupBy { it.receipt.merchant.trim() }
            .map { (merchant, list) ->
                MerchantSummary(
                    merchant = merchant.ifBlank { "Unbekannt" },
                    receiptCount = list.size,
                    totalSpentEuro = list.sumOf { it.receipt.totalEuro }
                )
            }
            .sortedByDescending { it.totalSpentEuro }
}

/** Sortiert und gruppiert die Beleg-Liste nach den Auswahl-Chips im UI. */
class SortReceiptsUseCase @Inject constructor() {

    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY)

    operator fun invoke(
        receipts: List<ReceiptWithItems>,
        sort: ReceiptSort,
        grouping: ReceiptGrouping,
        query: String = ""
    ): List<ReceiptGroup> {
        val filtered = if (query.isBlank()) receipts else receipts.filter { rwi ->
            rwi.receipt.merchant.contains(query, ignoreCase = true) ||
                rwi.items.any { it.name.contains(query, ignoreCase = true) }
        }

        val sorted = when (sort) {
            ReceiptSort.DATUM_NEU -> filtered.sortedByDescending { it.receipt.date }
            ReceiptSort.DATUM_ALT -> filtered.sortedBy { it.receipt.date }
            ReceiptSort.BETRAG_HOCH -> filtered.sortedByDescending { it.receipt.totalEuro }
            ReceiptSort.BETRAG_NIEDRIG -> filtered.sortedBy { it.receipt.totalEuro }
            ReceiptSort.HAENDLER_AZ -> filtered.sortedBy { it.receipt.merchant.lowercase(Locale.GERMANY) }
        }

        return when (grouping) {
            ReceiptGrouping.KEINE -> listOf(ReceiptGroup(null, sorted))

            ReceiptGrouping.NACH_HAENDLER -> sorted
                .groupBy { it.receipt.merchant.trim().ifBlank { "Unbekannt" } }
                .map { (merchant, list) ->
                    ReceiptGroup(
                        title = merchant,
                        receipts = list,
                        subtitle = "%d Belege · %.2f €".format(
                            Locale.GERMANY, list.size, list.sumOf { it.receipt.totalEuro }
                        )
                    )
                }
                .sortedByDescending { group -> group.receipts.sumOf { it.receipt.totalEuro } }

            ReceiptGrouping.NACH_MONAT -> sorted
                .groupBy { it.receipt.date.withDayOfMonth(1) }
                .map { (month, list) ->
                    ReceiptGroup(
                        title = month.format(monthFormatter),
                        receipts = list,
                        subtitle = "%d Belege · %.2f €".format(
                            Locale.GERMANY, list.size, list.sumOf { it.receipt.totalEuro }
                        )
                    )
                }
                .sortedByDescending { it.receipts.firstOrNull()?.receipt?.date }
        }
    }
}

/** Eine Gruppe in der Beleg-Liste (mit Kopfzeile), oder alles ungruppiert wenn title == null. */
data class ReceiptGroup(
    val title: String?,
    val receipts: List<ReceiptWithItems>,
    val subtitle: String? = null
)
