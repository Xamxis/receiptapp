package com.bonsai.app.billing

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.proStore by preferencesDataStore(name = "bonsai_pro")

/**
 * Lokaler Cache des Pro-Status plus Zähler für das Gratis-Kontingent.
 *
 * Der Cache sorgt dafür, dass Pro-Funktionen offline sofort greifen. Die
 * verbindliche Quelle bleibt Google Play – bei jedem Start wird über
 * [BillingRepository.restorePurchases] abgeglichen.
 */
@Singleton
class ProStatusStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val IS_PRO = booleanPreferencesKey("is_pro")
        val SCAN_MONTH = stringPreferencesKey("scan_month")
        val SCAN_COUNT = intPreferencesKey("scan_count")
    }

    val isPro: Flow<Boolean> = context.proStore.data.map { it[Keys.IS_PRO] ?: false }

    suspend fun setPro(value: Boolean) {
        context.proStore.edit { it[Keys.IS_PRO] = value }
    }

    /** Anzahl der Scans im laufenden Kalendermonat. */
    val scansThisMonth: Flow<Int> = context.proStore.data.map { prefs ->
        if (prefs[Keys.SCAN_MONTH] == currentMonthKey()) prefs[Keys.SCAN_COUNT] ?: 0 else 0
    }

    /** Erhöht den Zähler; setzt ihn automatisch zurück, wenn ein neuer Monat begonnen hat. */
    suspend fun recordScan() {
        context.proStore.edit { prefs ->
            val month = currentMonthKey()
            if (prefs[Keys.SCAN_MONTH] != month) {
                prefs[Keys.SCAN_MONTH] = month
                prefs[Keys.SCAN_COUNT] = 1
            } else {
                prefs[Keys.SCAN_COUNT] = (prefs[Keys.SCAN_COUNT] ?: 0) + 1
            }
        }
    }

    suspend fun currentScanCount(): Int = scansThisMonth.first()

    private fun currentMonthKey(): String {
        val today = LocalDate.now()
        return "%04d-%02d".format(today.year, today.monthValue)
    }
}

/** Zentrale Definition, was die Gratis-Version darf. */
object FreeTier {
    /**
     * Scans pro Monat in der Gratis-Version.
     *
     * Dieser Wert begrenzt gleichzeitig deine KI-Kosten pro Gratis-Nutzer.
     * Zum Anpassen genügt es, hier eine andere Zahl einzutragen.
     */
    const val MONTHLY_SCAN_LIMIT = 15
}
