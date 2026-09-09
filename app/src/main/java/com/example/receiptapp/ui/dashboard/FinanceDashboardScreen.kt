package com.example.receiptapp.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.Locale

/**
 * Finanz-Dashboard: monatliche Gesamtausgaben + Aufschlüsselung nach Kategorie.
 * Die eigentliche Kreisdiagramm-Darstellung ist hier als einfache Balken-Liste
 * umgesetzt (bewusst dependency-arm); Ersatz durch Vico-PieChart ist 1:1 möglich.
 */
@Composable
fun FinanceDashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.financeState.collectAsState()

    if (state.isLoading) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Finanzen diesen Monat", style = MaterialTheme.typography.headlineSmall)
        Text(
            String.format(Locale.GERMANY, "%.2f €", state.totalThisMonth),
            style = MaterialTheme.typography.displaySmall
        )

        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        Text("Nach Kategorie", style = MaterialTheme.typography.titleMedium)

        val maxValue = state.spendByCategory.maxOfOrNull { it.totalEuro } ?: 1.0

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.spendByCategory) { entry ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(entry.category.displayName, style = MaterialTheme.typography.bodyLarge)
                        LinearProgressIndicator(
                            progress = { (entry.totalEuro / maxValue).toFloat() },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                        Text(String.format(Locale.GERMANY, "%.2f €", entry.totalEuro))
                    }
                }
            }
        }
    }
}
