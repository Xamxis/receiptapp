package com.bonsai.app.ui.pro

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/** Die Vorteile, die mit Pro freigeschaltet werden – auch im Einstellungs-Screen verwendet. */
val proFeatures = listOf(
    "Unbegrenzt Belege scannen",
    "Berater-Chat ohne Limit",
    "Produktübersicht: was du wie oft kaufst",
    "Alle Zeiträume auswerten, nicht nur den aktuellen Monat",
    "Export als CSV und PDF-Monatsbericht"
)

/**
 * Kauf-Dialog für die Pro-Version. Wird überall dort eingeblendet, wo eine
 * gesperrte Funktion angetippt wird.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPaywallSheet(
    reason: String,
    onDismiss: () -> Unit,
    viewModel: ProViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    val activity = LocalContext.current.findActivity()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Bonsai Pro", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))
            proFeatures.forEach { feature ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(feature, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "${state.priceText ?: "2,99 €"} ${state.billingPeriodText ?: "pro Monat"} – jederzeit kündbar.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            state.error?.let { error ->
                Spacer(Modifier.height(10.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { activity?.let(viewModel::purchase) },
                enabled = !state.isPurchasing && activity != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (state.isPurchasing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Pro abonnieren – ${state.priceText ?: "2,99 €"} ${state.billingPeriodText ?: "pro Monat"}")
                }
            }

            TextButton(
                onClick = viewModel::restore,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kauf wiederherstellen")
            }
        }
    }
}

/** Findet die umgebende Activity – Play Billing braucht sie für den Kauf-Dialog. */
fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
