package com.example.receiptapp.ui.dashboard
import androidx.hilt.navigation.compose.hiltViewModel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Gesundheits-Dashboard: Score der aktuellen Woche + Aufschlüsselung
 * frisch/verarbeitet/Fast-Food + konkrete Tipps-Karten.
 */
@Composable
fun HealthDashboardScreen(viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.healthState.collectAsState()

    if (state.isLoading || state.score == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val score = state.score!!

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Gesundheits-Score diese Woche", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text("${score.score}", style = MaterialTheme.typography.displayLarge)
                Text("von 100")
            }
        }

        Text(
            "Frisch: ${score.freshSharePercent}%  ·  Verarbeitet: ${score.processedSharePercent}%  ·  Fast Food: ${score.fastFoodSharePercent}%",
            style = MaterialTheme.typography.bodyMedium
        )

        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        Text("Tipps für dich", style = MaterialTheme.typography.titleMedium)

        LazyColumn {
            items(score.tips) { tip ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(tip.title, style = MaterialTheme.typography.bodyLarge)
                        Text(tip.detail, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
