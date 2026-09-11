package com.bonsai.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.bonsai.app.data.model.Receipt
import com.bonsai.app.data.model.ReceiptItem
import com.bonsai.app.data.model.ReceiptWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {

    @Insert
    suspend fun insertReceipt(receipt: Receipt): Long

    @Insert
    suspend fun insertItems(items: List<ReceiptItem>)

    /** Speichert Beleg + Posten atomar in einer Transaktion. */
    @Transaction
    suspend fun insertReceiptWithItems(receipt: Receipt, items: List<ReceiptItem>): Long {
        val receiptId = insertReceipt(receipt)
        insertItems(items.map { it.copy(receiptId = receiptId) })
        return receiptId
    }

    @Transaction
    @Query("SELECT * FROM receipts ORDER BY date DESC")
    fun observeAllWithItems(): Flow<List<ReceiptWithItems>>

    @Transaction
    @Query(
        "SELECT * FROM receipts WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC"
    )
    fun observeInRange(startDate: String, endDate: String): Flow<List<ReceiptWithItems>>

    @Query("SELECT SUM(totalEuro) FROM receipts WHERE date BETWEEN :startDate AND :endDate")
    fun observeTotalInRange(startDate: String, endDate: String): Flow<Double?>

    @Query("DELETE FROM receipts WHERE id = :receiptId")
    suspend fun deleteReceipt(receiptId: Long)

    /** Löscht alle Belege (Posten verschwinden per CASCADE mit). */
    @Query("DELETE FROM receipts")
    suspend fun deleteAllReceipts()

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :receiptId LIMIT 1")
    fun observeReceiptWithItems(receiptId: Long): Flow<ReceiptWithItems?>
}
