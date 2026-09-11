package com.bonsai.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bonsai.app.data.model.TimeRange
import com.bonsai.app.data.repository.ChatMessage
import com.bonsai.app.data.repository.ChatRepository
import com.bonsai.app.data.repository.ChatResult
import com.bonsai.app.data.repository.ReceiptRepository
import com.bonsai.app.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val aiEnabled: Boolean = true,
    val hasData: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val receiptRepository: ReceiptRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    /** Vorschlagsfragen als Einstieg – senken die Hemmschwelle gegenüber einem leeren Eingabefeld. */
    val suggestions = listOf(
        "Wo gebe ich am meisten Geld aus?",
        "Was könnte ich gesünder einkaufen?",
        "Wie kann ich beim Einkauf sparen?",
        "Was kaufe ich am häufigsten?"
    )

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val receipts = receiptRepository.observeAll().first()
            _uiState.value = _uiState.value.copy(
                aiEnabled = settings.aiAnalysisEnabled,
                hasData = receipts.isNotEmpty()
            )
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.isSending) return

        val withUserMessage = _uiState.value.messages + ChatMessage(fromUser = true, text = trimmed)
        _uiState.value = _uiState.value.copy(messages = withUserMessage, isSending = true)

        viewModelScope.launch {
            val settings = settingsRepository.settings.first()

            if (!settings.aiAnalysisEnabled) {
                _uiState.value = _uiState.value.copy(
                    messages = withUserMessage + ChatMessage(
                        fromUser = false,
                        text = "Die KI-Analyse ist in den Einstellungen ausgeschaltet. " +
                            "Schalte sie dort ein, wenn du den Berater nutzen möchtest.",
                        isError = true
                    ),
                    isSending = false
                )
                return@launch
            }

            val receipts = receiptRepository.observeAll().first()

            when (val result = chatRepository.send(withUserMessage, receipts, settings.nutritionGoal)) {
                is ChatResult.Success -> _uiState.value = _uiState.value.copy(
                    messages = withUserMessage + ChatMessage(fromUser = false, text = result.reply),
                    isSending = false,
                    hasData = receipts.isNotEmpty()
                )

                is ChatResult.Failure -> _uiState.value = _uiState.value.copy(
                    messages = withUserMessage + ChatMessage(
                        fromUser = false,
                        text = "Der Berater ist gerade nicht erreichbar (${result.reason}). " +
                            "Prüfe deine Internetverbindung und versuch es nochmal.",
                        isError = true
                    ),
                    isSending = false
                )
            }
        }
    }

    fun clear() {
        _uiState.value = _uiState.value.copy(messages = emptyList())
    }
}
