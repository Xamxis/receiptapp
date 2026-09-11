package com.bonsai.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Einzelposten eines Belegs (1:n zu Receipt).
 * Wird direkt aus der KI-JSON-Antwort gemappt, siehe ClaudeReceiptParser.
 */
@Entity(
    tableName = "receipt_items",
    foreignKeys = [
        ForeignKey(
            entity = Receipt::class,
            parentColumns = ["id"],
            childColumns = ["receiptId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [androidx.room.Index("receiptId")]
)
data class ReceiptItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptId: Long,
    val name: String,
    val priceEuro: Double,
    val quantity: Double = 1.0,
    @ColumnInfo(name = "category") val category: Category,
    @ColumnInfo(name = "nutrition_tag") val nutritionTag: NutritionTag? = null,
    val healthNote: String? = null,
    /** Vom Nutzer manuell gesetzte Tags, z. B. "Geschäftsessen", "Geburtstag" */
    val userTags: List<String> = emptyList()
)
