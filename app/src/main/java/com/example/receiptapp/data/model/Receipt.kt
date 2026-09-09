package com.example.receiptapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate

@Entity(tableName = "receipts")
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String,
    val date: LocalDate,
    val totalEuro: Double,
    /** Roher OCR-Text, für Debugging / erneutes Parsen falls KI-Ergebnis korrigiert wird */
    val rawOcrText: String,
    /** Confidence-Wert (0..1), den die KI für die Extraktion zurückgibt */
    val aiConfidence: Float = 1.0f,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

/** Aggregat für UI: Beleg inkl. seiner Posten (Room @Relation) */
data class ReceiptWithItems(
    val receipt: Receipt,
    @Relation(parentColumn = "id", entityColumn = "receiptId")
    val items: List<ReceiptItem>
)
