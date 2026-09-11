package com.bonsai.app.export

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.bonsai.app.data.model.ReceiptWithItems
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import java.util.Locale
import javax.inject.Inject

/**
 * Erstellt einen einfachen PDF-Monatsbericht (Summe pro Kategorie).
 * Nutzt PdfBox-Android; für aufwendigeres Layout ggf. auf ein Template
 * mit mehreren Seiten / Diagrammen als Bitmap-Export erweitern.
 */
class PdfReportGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun generateMonthlyReport(receipts: List<ReceiptWithItems>, monthLabel: String): File {
        val document = PDDocument()
        val page = PDPage()
        document.addPage(page)

        val total = receipts.sumOf { it.receipt.totalEuro }
        val byCategory = receipts.flatMap { it.items }
            .groupBy { it.category }
            .mapValues { (_, items) -> items.sumOf { it.priceEuro * it.quantity } }
            .toList()
            .sortedByDescending { it.second }

        PDPageContentStream(document, page).use { content ->
            content.beginText()
            content.setFont(PDType1Font.HELVETICA_BOLD, 18f)
            content.newLineAtOffset(50f, 750f)
            content.showText("Monatsbericht – $monthLabel")
            content.endText()

            content.beginText()
            content.setFont(PDType1Font.HELVETICA, 12f)
            content.newLineAtOffset(50f, 710f)
            content.showText("Gesamtausgaben: %.2f €".format(Locale.GERMANY, total))
            content.endText()

            var y = 670f
            byCategory.forEach { (category, sum) ->
                content.beginText()
                content.setFont(PDType1Font.HELVETICA, 11f)
                content.newLineAtOffset(50f, y)
                content.showText("${category.displayName}: %.2f €".format(Locale.GERMANY, sum))
                content.endText()
                y -= 18f
            }
        }

        val file = File(context.getExternalFilesDir(null), "bericht_$monthLabel.pdf")
        document.save(file)
        document.close()
        return file
    }
}
