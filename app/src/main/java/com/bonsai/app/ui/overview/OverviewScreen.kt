package com.bonsai.app.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bonsai.app.data.model.HealthTip
import com.bonsai.app.data.model.TimeRange
import com.bonsai.app.data.model.TipKind
import com.bonsai.app.ui.common.BonsaiCard
import com.bonsai.app.ui.common.ChipRow
import com.bonsai.app.ui.common.EmptyState
import com.bonsai.app.ui.common.ProgressBar
import com.bonsai.app.ui.common.ScoreRing
import com.bonsai.app.ui.common.SectionHeader
import com.bonsai.app.ui.common.StatRow
import com.bonsai.app.ui.common.euro
import com.bonsai.app.ui.theme.ScoreGood
import com.bonsai.app.ui.theme.Terracotta

/**
 * Startbildschirm: was habe ich ausgegeben, wie ernähre ich mich,
 * und was kann ich konkret besser machen.
 */
@Composable
fun OverviewScreen(
    onOpenAdvisor: () -> Unit,
    viewModel: OverviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Übersicht",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        item {
            ChipRow(
                options = TimeRange.entries,
                selected = state.range,
                label = { it.displayName },
                onSelect = viewModel::setRange
            )
        }

        // --- Ausgaben ---
        item {
            BonsaiCard {
                Text(
                    "Ausgaben",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(euro(state.total), style = MaterialTheme.typography.displayMedium)
                Text(
                    "${state.receiptCount} Belege · ${state.range.displayName.lowercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                state.budgetStatus?.let { budget ->
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(budget.label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${euro(budget.currentEuro)} / ${euro(budget.targetEuro)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (budget.isExceeded) Terracotta else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(
                        progress = budget.progress,
                        color = if (budget.isExceeded) Terracotta else ScoreGood
                    )
                    if (budget.isExceeded) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Budget um ${euro(budget.currentEuro - budget.targetEuro)} überschritten",
                            style = MaterialTheme.typography.labelLarge,
                            color = Terracotta
                        )
                    }
                }
            }
        }

        // --- Ernährungs-Score ---
        state.score?.let { score ->
            if (score.hasData) {
                item {
                    BonsaiCard {
                        Text(
                            "Ernährung",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ScoreRing(score = score.score)
                        }
                        Spacer(Modifier.height(16.dp))
                        ShareRow("Frisch & vollwertig", score.freshSharePercent, ScoreGood)
                        ShareRow("Verarbeitet", score.processedSharePercent, MaterialTheme.colorScheme.onSurfaceVariant)
                        ShareRow("Stark verarbeitet", score.fastFoodSharePercent, Terracotta)
                    }
                }
            }
        }

        // --- Berater-Tipps ---
        val tips = state.score?.tips.orEmpty()
        if (tips.isNotEmpty()) {
            item { SectionHeader("Was du verbessern kannst") }
            items(tips) { tip -> TipCard(tip) }
        }

        // --- Kategorien ---
        if (state.byCategory.isNotEmpty()) {
            item { SectionHeader("Ausgaben nach Kategorie") }
            item {
                BonsaiCard {
                    state.byCategory.forEach { entry ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(entry.category.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    euro(entry.totalEuro),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            ProgressBar(
                                progress = entry.sharePercent / 100f,
                                color = MaterialTheme.colorScheme.primary,
                                height = 6
                            )
                        }
                    }
                }
            }
        }

        // --- Geschäfte ---
        if (state.topMerchants.isNotEmpty()) {
            item { SectionHeader("Wo du am meisten ausgibst") }
            item {
                BonsaiCard {
                    state.topMerchants.forEach { merchant ->
                        StatRow(
                            label = "${merchant.merchant}  ·  ${merchant.receiptCount} Belege",
                            value = euro(merchant.totalSpentEuro),
                            emphasized = true
                        )
                    }
                }
            }
        }

        if (state.receiptCount == 0) {
            item {
                EmptyState(
                    title = "Noch keine Belege",
                    message = "Tippe unten auf Scannen und fotografiere deinen ersten Kassenzettel. " +
                        "Danach siehst du hier deine Ausgaben und Ernährungsauswertung."
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ShareRow(label: String, percent: Int, color: androidx.compose.ui.graphics.Color) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "$percent %",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(5.dp))
        ProgressBar(progress = percent / 100f, color = color, height = 6)
    }
}

@Composable
private fun TipCard(tip: HealthTip) {
    val accent = when (tip.kind) {
        TipKind.ERNAEHRUNG -> ScoreGood
        TipKind.AUSGABEN -> Terracotta
        TipKind.INFO -> MaterialTheme.colorScheme.primary
    }
    val badge = when (tip.kind) {
        TipKind.ERNAEHRUNG -> "Ernährung"
        TipKind.AUSGABEN -> "Ausgaben"
        TipKind.INFO -> "Hinweis"
    }

    BonsaiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                badge.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(tip.title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            tip.detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
