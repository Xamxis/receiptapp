package com.example.receiptapp.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.Locale

/**
 * Einstellungen: individuelle Budget-/Ernährungsziele, Export, Datenschutz-Hinweise.
 * Dark Mode / Material You wird global über ReceiptAppTheme gesteuert (Systemeinstellung
 * + optionaler manueller Override, hier der Kürze halber ausgelassen).
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val goals by viewModel.goals.collectAsState()

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Ernährungs- & Budgetziele", style = MaterialTheme.typography.headlineSmall)

        Text(
            String.format(Locale.GERMANY, "Snack-Budget pro Woche: %.0f €", goals.weeklySnackBudgetEuro),
            modifier = Modifier.padding(top = 16.dp)
        )
        Slider(
            value = goals.weeklySnackBudgetEuro.toFloat(),
            onValueChange = { viewModel.updateWeeklySnackBudget(it.toDouble()) },
            valueRange = 0f..200f
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text("Weniger verarbeitetes Fleisch")
            Switch(
                checked = goals.reduceProcessedMeat,
                onCheckedChange = { viewModel.updateReduceProcessedMeat(it) }
            )
        }

        Text(
            "Weitere Optionen (CSV/PDF-Export, Verschlüsselung, Datenschutz-Info) folgen " +
                "hier als weitere Sections – siehe export/CsvExporter.kt & PdfReportGenerator.kt.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}
