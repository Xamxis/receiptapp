package com.bonsai.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.bonsai.app.data.model.NutritionGoal
import com.bonsai.app.data.model.ThemeMode
import com.bonsai.app.BuildConfig
import com.bonsai.app.ui.common.BonsaiCard
import com.bonsai.app.ui.common.SectionHeader
import com.bonsai.app.ui.pro.ProPaywallSheet
import com.bonsai.app.ui.pro.ProViewModel
import com.bonsai.app.ui.pro.proFeatures

/**
 * Einstellungen: Budget, Ernährungsziel, Darstellung, Export und Datenschutz.
 *
 * Das frühere Einzel-Häkchen "weniger verarbeitetes Fleisch" ist durch ein
 * klares Ernährungsziel ersetzt – das steuert jetzt, worauf der Berater achtet.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    proViewModel: ProViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val message by viewModel.message.collectAsState()
    val access by proViewModel.access.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Einstellungen",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        item {
            BonsaiCard {
                if (access.isPro) {
                    Text("Bonsai Pro aktiv", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Danke für deine Unterstützung. Alle Funktionen sind freigeschaltet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("Bonsai Pro", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Noch ${access.scansLeft} von ${access.scanLimit} Gratis-Scans diesen Monat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    proFeatures.take(3).forEach { feature ->
                        Text(
                            "·  $feature",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { showPaywall = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pro abonnieren – 2,99 € / Monat")
                    }
                    TextButton(
                        onClick = proViewModel::restore,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Kauf wiederherstellen") }
                }
            }
        }

        message?.let { msg ->
            item {
                BonsaiCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            msg.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (msg.isError) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::clearMessage) { Text("OK") }
                    }
                }
            }
        }

        // ---------- Budget ----------
        item { SectionHeader("Budget") }
        item {
            BonsaiCard {
                BudgetField(
                    label = "Monatsbudget",
                    hint = "Leer lassen für kein Limit",
                    value = settings.monthlyBudgetEuro,
                    onChange = viewModel::setMonthlyBudget
                )
                Spacer(Modifier.height(12.dp))
                BudgetField(
                    label = "Snack-Budget pro Woche",
                    hint = "Für Unterwegs-Verpflegung",
                    value = settings.weeklySnackBudgetEuro,
                    onChange = viewModel::setSnackBudget
                )
                Spacer(Modifier.height(12.dp))
                ToggleRow(
                    title = "Hinweis bei Überschreitung",
                    subtitle = "Zeigt eine Warnung in der Übersicht an",
                    checked = settings.budgetAlertsEnabled,
                    onCheckedChange = viewModel::setAlerts
                )
            }
        }

        // ---------- Ernährungsziel ----------
        item { SectionHeader("Ernährungsziel") }
        item {
            BonsaiCard {
                Text(
                    "Bestimmt, worauf der Berater bei deinen Einkäufen achtet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                NutritionGoal.entries.forEach { goal ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setNutritionGoal(goal) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.nutritionGoal == goal,
                            onClick = { viewModel.setNutritionGoal(goal) }
                        )
                        Spacer(Modifier.width(4.dp))
                        Column {
                            Text(goal.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                goal.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (settings.nutritionGoal != NutritionGoal.KEIN_ZIEL) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Zielwert frische Lebensmittel: ${settings.freshFoodTargetPercent} %",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        "Anteil unverarbeiteter Produkte an deinem Einkauf",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = settings.freshFoodTargetPercent.toFloat(),
                        onValueChange = { viewModel.setFreshTarget(it.toInt()) },
                        valueRange = 10f..90f,
                        steps = 15
                    )
                }
            }
        }

        // ---------- Darstellung ----------
        item { SectionHeader("Darstellung") }
        item {
            BonsaiCard {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setThemeMode(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(mode.displayName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    title = "Material You",
                    subtitle = "Farben vom Hintergrundbild übernehmen (ab Android 12)",
                    checked = settings.useDynamicColor,
                    onCheckedChange = viewModel::setDynamicColor
                )
            }
        }

        // ---------- Export ----------
        item { SectionHeader("Export") }
        item {
            BonsaiCard {
                Text(
                    "Deine Daten als Datei sichern. Die Dateien landen im App-Ordner " +
                        "unter Android/data auf deinem Gerät.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (access.isPro) viewModel.exportCsv() else showPaywall = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(if (access.isPro) "CSV" else "CSV ·  Pro") }
                    OutlinedButton(
                        onClick = {
                            if (access.isPro) viewModel.exportPdf() else showPaywall = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(if (access.isPro) "PDF-Bericht" else "PDF ·  Pro") }
                }
            }
        }

        // ---------- Datenschutz ----------
        item { SectionHeader("Daten & Datenschutz") }
        item {
            BonsaiCard {
                ToggleRow(
                    title = "KI-Analyse erlauben",
                    subtitle = "Nötig für Beleg-Erkennung und Berater-Chat. " +
                        "Es wird nur der Belegtext übertragen, nie das Foto.",
                    checked = settings.aiAnalysisEnabled,
                    onCheckedChange = viewModel::setAiEnabled
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Deine Belege werden verschlüsselt auf diesem Gerät gespeichert. " +
                        "Die Texterkennung läuft komplett offline auf dem Handy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            BonsaiCard {
                Text("Alle Belege löschen", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Entfernt sämtliche gescannten Belege unwiderruflich von diesem Gerät.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                if (confirmDelete) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { confirmDelete = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Abbrechen") }
                        Button(
                            onClick = {
                                viewModel.deleteAllData()
                                confirmDelete = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) { Text("Endgültig löschen") }
                    }
                } else {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Löschen", color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }

        if (BuildConfig.DEBUG) {
            item { SectionHeader("Nur für Entwicklung") }
            item {
                BonsaiCard {
                    ToggleRow(
                        title = "Pro zum Testen freischalten",
                        subtitle = "Nur in Debug-Builds sichtbar. Umgeht Google Play, " +
                            "damit die Pro-Funktionen ohne Kauf getestet werden können.",
                        checked = access.isPro,
                        onCheckedChange = viewModel::debugTogglePro
                    )
                }
            }
        }

        item {
            Text(
                if (access.isPro) "Bonsai Pro · Version 1.0" else "Bonsai · Version 1.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
            )
        }
    }

    if (showPaywall) {
        ProPaywallSheet(
            reason = "Alle Funktionen dauerhaft freischalten.",
            onDismiss = { showPaywall = false }
        )
    }
}

@Composable
private fun BudgetField(
    label: String,
    hint: String,
    value: Double?,
    onChange: (Double?) -> Unit
) {
    var text by remember(value) { mutableStateOf(value?.let { "%.0f".format(it) } ?: "") }

    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                text = new.filter { it.isDigit() || it == ',' || it == '.' }
                onChange(text.replace(',', '.').toDoubleOrNull())
            },
            label = { Text(label) },
            suffix = { Text("€") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
