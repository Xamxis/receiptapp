package com.example.receiptapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.receiptapp.data.model.Receipt
import com.example.receiptapp.data.model.ReceiptItem
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [Receipt::class, ReceiptItem::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao

    companion object {
        private const val DB_NAME = "receiptapp.db"

        /**
         * Erstellt die Datenbank mit SQLCipher-Verschlüsselung.
         * Die Passphrase kommt aus dem DbPassphraseProvider (Android Keystore-gesichert),
         * NICHT hartkodiert.
         */
        fun build(context: Context, passphrase: ByteArray): AppDatabase {
            val factory = SupportFactory(passphrase)
            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
