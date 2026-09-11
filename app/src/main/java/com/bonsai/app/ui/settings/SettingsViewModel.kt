package com.bonsai.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bonsai.app.data.model.NutritionGoal
import com.bonsai.app.data.model.ThemeMode
import com.bonsai.app.data.model.UserSettings
import com.bonsai.app.billing.ProStatusStore
import com.bonsai.app.data.repository.ReceiptRepository
import com.bonsai.app.data.settings.SettingsRepository
import com.bonsai.app.export.CsvExporter
import com.bonsai.app.export.PdfReportGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** Kurze Rückmeldung nach einer Aktion (Export, Löschen). */
data class SettingsMessage(val text: String, val isError: Boolean = false)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val receiptRepository: ReceiptRepository,
    private val proStatusStore: ProStatusStore,
    private val csvExporter: CsvExporter,
    private val pdfGenerator: PdfReportGenerator
) : ViewModel() {

    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    private val _message = MutableStateFlow<SettingsMessage?>(null)
    val message: StateFlow<SettingsMessage?> = _message

    fun setMonthlyBudget(value: Double?) = update { it.copy(monthlyBudgetEuro = value) }
    fun setSnackBudget(value: Double?) = update { it.copy(weeklySnackBudgetEuro = value) }
    fun setAlerts(enabled: Boolean) = update { it.copy(budgetAlertsEnabled = enabled) }
    fun setNutritionGoal(goal: NutritionGoal) = update { it.copy(nutritionGoal = goal) }
    fun setFreshTarget(percent: Int) = update { it.copy(freshFoodTargetPercent = percent) }
    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setDynamicColor(enabled: Boolean) = update { it.copy(useDynamicColor = enabled) }
    fun setAiEnabled(enabled: Boolean) = update { it.copy(aiAnalysisEnabled = enabled) }

    private fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun exportCsv() {
        viewModelScope.launch {
            try {
                val receipts = receiptRepository.observeAll().first()
                if (receipts.isEmpty()) {
                    _message.value = SettingsMessage("Keine Belege zum Exportieren vorhanden.", isError = true)
                    return@launch
                }
                val file = csvExporter.export(receipts)
                _message.value = SettingsMessage("CSV gespeichert: ${file.name}")
            } catch (e: Exception) {
                _message.value = SettingsMessage("Export fehlgeschlagen: ${e.message}", isError = true)
            }
        }
    }

    fun exportPdf() {
        viewModelScope.launch {
            try {
                val receipts = receiptRepository.observeAll().first()
                if (receipts.isEmpty()) {
                    _message.value = SettingsMessage("Keine Belege zum Exportieren vorhanden.", isError = true)
                    return@launch
                }
                val label = LocalDate.now().format(DateTimeFormatter.ofPattern("MM-yyyy", Locale.GERMANY))
                val file = pdfGenerator.generateMonthlyReport(receipts, label)
                _message.value = SettingsMessage("PDF gespeichert: ${file.name}")
            } catch (e: Exception) {
                _message.value = SettingsMessage("Export fehlgeschlagen: ${e.message}", isError = true)
            }
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            try {
                receiptRepository.deleteAll()
                _message.value = SettingsMessage("Alle Belege wurden gelöscht.")
            } catch (e: Exception) {
                _message.value = SettingsMessage("Löschen fehlgeschlagen: ${e.message}", isError = true)
            }
        }
    }

    /**
     * Nur für Debug-Builds: schaltet Pro lokal um, damit die Funktionen auch ohne
     * Google-Play-Installation testbar sind. Im Release-Build nicht erreichbar.
     */
    fun debugTogglePro(enabled: Boolean) {
        viewModelScope.launch {
            proStatusStore.setPro(enabled)
            _message.value = SettingsMessage(
                if (enabled) "Pro zum Testen aktiviert." else "Pro-Test deaktiviert."
            )
        }
    }

    fun clearMessage() { _message.value = null }
}
