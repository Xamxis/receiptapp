package com.bonsai.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bonsai.app.data.repository.ChatMessage
import com.bonsai.app.ui.pro.ProPaywallSheet
import com.bonsai.app.ui.pro.ProViewModel

/**
 * Der KI-Berater als Chat. Bekommt eine kompakte Zusammenfassung der eigenen
 * Belegdaten als Kontext mit, kann also konkret über die eigenen Einkäufe reden.
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    proViewModel: ProViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val access by proViewModel.access.collectAsState()
    var input by remember { mutableStateOf("") }
    var showPaywall by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Berater", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Fragen zu deinen Ausgaben und deiner Ernährung",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.messages.isNotEmpty()) {
                TextButton(onClick = viewModel::clear) { Text("Neu") }
            }
        }

        Box(Modifier.weight(1f)) {
            if (state.messages.isEmpty()) {
                EmptyChat(
                    hasData = state.hasData,
                    suggestions = viewModel.suggestions,
                    onSuggestion = {
                        if (access.isPro) viewModel.send(it) else showPaywall = true
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.messages) { message -> MessageBubble(message) }
                    if (state.isSending) {
                        item { TypingIndicator() }
                    }
                }
            }
        }

        InputBar(
            value = input,
            onValueChange = { input = it },
            enabled = !state.isSending,
            onSend = {
                if (access.isPro) {
                    viewModel.send(input)
                    input = ""
                } else {
                    showPaywall = true
                }
            }
        )
    }

    if (showPaywall) {
        ProPaywallSheet(
            reason = "Der Berater-Chat gehört zu Bonsai Pro.",
            onDismiss = { showPaywall = false }
        )
    }
}

@Composable
private fun EmptyChat(
    hasData: Boolean,
    suggestions: List<String>,
    onSuggestion: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (hasData) "Frag mich etwas zu deinen Einkäufen"
            else "Scanne zuerst ein paar Belege",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (hasData)
                "Ich kenne deine gescannten Belege und kann dir sagen, wo dein Geld hingeht " +
                    "und was du beim Einkauf anders machen könntest."
            else
                "Sobald Belege da sind, kann ich dir konkrete Hinweise zu deinen Ausgaben " +
                    "und deiner Ernährung geben. Fragen kannst du trotzdem jederzeit stellen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))
        suggestions.forEach { suggestion ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onSuggestion(suggestion) }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.fromUser
    val bubbleColor = when {
        message.isError -> MaterialTheme.colorScheme.tertiaryContainer
        isUser -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        message.isError -> MaterialTheme.colorScheme.onTertiaryContainer
        isUser -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp
                    )
                )
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(message.text, style = MaterialTheme.typography.bodyLarge, color = textColor)
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "denkt nach…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("Nachricht schreiben…") },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(22.dp),
            maxLines = 4
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    if (enabled && value.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Senden",
                tint = if (enabled && value.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
