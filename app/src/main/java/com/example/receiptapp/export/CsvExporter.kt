package com.example.receiptapp.export

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.receiptapp.data.model.ReceiptWithItems
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

/** Exportiert Belege als CSV (Beleg-Ebene + Posten-Ebene, flach). */
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun export(receipts: List<ReceiptWithItems>, fileName: String = "receiptapp_export.csv"): File {
        val file = File(context.getExternalFilesDir(null), fileName)
        FileWriter(file).use { writer ->
            writer.append("Datum,Händler,Posten,Kategorie,Preis,Menge,NährwertTag\n")
            receipts.forEach { rwi ->
                rwi.items.forEach { item ->
                    writer.append(rwi.receipt.date.toString()).append(',')
                    writer.append(escape(rwi.receipt.merchant)).append(',')
                    writer.append(escape(item.name)).append(',')
                    writer.append(item.category.name).append(',')
                    writer.append(item.priceEuro.toString()).append(',')
                    writer.append(item.quantity.toString()).append(',')
                    writer.append(item.nutritionTag?.name ?: "").append('\n')
                }
            }
        }
        return file
    }

    private fun escape(value: String): String =
        if (value.contains(",") || value.contains("\"")) "\"${value.replace("\"", "\"\"")}\"" else value
}
