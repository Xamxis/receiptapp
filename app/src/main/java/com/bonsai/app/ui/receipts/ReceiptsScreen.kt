package com.bonsai.app.ui.receipts

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bonsai.app.data.model.ProductSort
import com.bonsai.app.data.model.ProductSummary
import com.bonsai.app.data.model.ReceiptGrouping
import com.bonsai.app.data.model.ReceiptSort
import com.bonsai.app.data.model.ReceiptWithItems
import com.bonsai.app.data.model.TimeRange
import com.bonsai.app.ui.common.BonsaiCard
import com.bonsai.app.ui.common.ChipRow
import com.bonsai.app.ui.common.EmptyState
import com.bonsai.app.ui.common.SectionHeader
import com.bonsai.app.ui.common.euro
import com.bonsai.app.ui.pro.ProPaywallSheet
import com.bonsai.app.ui.pro.ProViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

/**
 * Belege-Screen mit zwei Ansichten:
 *  - "Belege": alle Kassenzettel, sortier- und gruppierbar (nach Geschäft, Betrag, Datum)
 *  - "Produkte": alles, was jemals gekauft wurde, mit Häufigkeit und Gesamtausgaben
 */
@Composable
fun ReceiptsScreen(
    viewModel: ReceiptsViewModel = hiltViewModel(),
    proViewModel: ProViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val access by proViewModel.access.collectAsState()
    var showPaywall by remember { mutableStateOf(false) }
    var paywallReason by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Meine Einkäufe",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
        )

        TabRow(selectedTabIndex = state.tab.ordinal) {
            ReceiptsTab.entries.forEach { tab ->
                Tab(
                    selected = state.tab == tab,
                    onClick = {
                        if (tab == ReceiptsTab.PRODUKTE && !access.isPro) {
                            paywallReason = "Die Produktübersicht gehört zu Bonsai Pro."
                            showPaywall = true
                        } else {
                            viewModel.setTab(tab)
                        }
                    },
                    text = {
                        Text(
                            if (tab == ReceiptsTab.PRODUKTE && !access.isPro)
                                "${tab.displayName} ·  Pro" else tab.displayName,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                )
            }
        }

        SearchField(
            query = state.query,
            onQueryChange = viewModel::setQuery,
            placeholder = if (state.tab == ReceiptsTab.BELEGE)
                "Geschäft oder Produkt suchen" else "Produkt suchen"
        )

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        when (state.tab) {
            ReceiptsTab.BELEGE -> ReceiptsList(
                state = state,
                viewModel = viewModel,
                isPro = access.isPro,
                onProRequired = { reason ->
                    paywallReason = reason
                    showPaywall = true
                }
            )
            ReceiptsTab.PRODUKTE -> ProductsList(state, viewModel)
        }
    }

    if (showPaywall) {
        ProPaywallSheet(
            reason = paywallReason,
            onDismiss = { showPaywall = false }
        )
    }
}

/** Zeiträume, die über den aktuellen Monat hinausgehen, sind Pro-Funktionen. */
private fun TimeRange.requiresPro(): Boolean =
    this == TimeRange.LETZTE_3_MONATE || this == TimeRange.ALLES

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Suche löschen")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun ReceiptsList(
    state: ReceiptsUiState,
    viewModel: ReceiptsViewModel,
    isPro: Boolean,
    onProRequired: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow(
                    options = TimeRange.entries,
                    selected = state.range,
                    label = { if (!isPro && it.requiresPro()) "${it.displayName} ·  Pro" else it.displayName },
                    onSelect = { range ->
                        if (!isPro && range.requiresPro()) {
                            onProRequired("Längere Zeiträume auswerten gehört zu Bonsai Pro.")
                        } else {
                            viewModel.setRange(range)
                        }
                    }
                )
                ChipRow(
                    options = ReceiptSort.entries,
                    selected = state.sort,
                    label = { it.displayName },
                    onSelect = viewModel::setSort
                )
                ChipRow(
                    options = ReceiptGrouping.entries,
                    selected = state.grouping,
                    label = { it.displayName },
                    onSelect = viewModel::setGrouping
                )
            }
        }

        if (state.groups.all { it.receipts.isEmpty() }) {
            item {
                EmptyState(
                    title = if (state.query.isBlank()) "Keine Belege" else "Nichts gefunden",
                    message = if (state.query.isBlank())
                        "Scanne deinen ersten Kassenzettel, dann erscheint er hier."
                    else
                        "Für \"${state.query}\" gibt es in diesem Zeitraum keine Treffer."
                )
            }
        }

        state.groups.forEach { group ->
            if (group.title != null) {
                item {
                    Column(Modifier.padding(top = 8.dp)) {
                        SectionHeader(group.title)
                        group.subtitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                            )
                        }
                    }
                }
            }
            items(group.receipts, key = { it.receipt.id }) { rwi ->
                ReceiptCard(rwi, onDelete = { viewModel.deleteReceipt(rwi.receipt.id) })
            }
        }
    }
}

@Composable
private fun ReceiptCard(rwi: ReceiptWithItems, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    BonsaiCard(modifier = Modifier.clickable { expanded = !expanded }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    rwi.receipt.merchant.ifBlank { "Unbekanntes Geschäft" },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${rwi.receipt.date.format(dateFormat)} · ${rwi.items.size} Posten",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                euro(rwi.receipt.totalEuro),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Zuklappen" else "Aufklappen",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))

                rwi.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (item.quantity > 1) "${item.quantity.toInt()}× ${item.name}" else item.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                item.category.displayName +
                                    (item.nutritionTag?.let { " · ${it.displayName}" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(euro(item.priceEuro * item.quantity), style = MaterialTheme.typography.bodyLarge)
                    }
                }

                if (rwi.receipt.aiConfidence < 0.6f) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Die automatische Erkennung war bei diesem Beleg unsicher " +
                            "(${(rwi.receipt.aiConfidence * 100).toInt()} %). Bitte Werte prüfen.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Spacer(Modifier.height(8.dp))
                if (confirmDelete) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Wirklich löschen?",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
                        TextButton(onClick = onDelete) {
                            Text("Löschen", color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                } else {
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Beleg löschen")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductsList(state: ReceiptsUiState, viewModel: ReceiptsViewModel) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow(
                    options = TimeRange.entries,
                    selected = state.range,
                    label = { it.displayName },
                    onSelect = viewModel::setRange
                )
                ChipRow(
                    options = ProductSort.entries,
                    selected = state.productSort,
                    label = { it.displayName },
                    onSelect = viewModel::setProductSort
                )
            }
        }

        if (state.products.isEmpty()) {
            item {
                EmptyState(
                    title = if (state.query.isBlank()) "Noch keine Produkte" else "Nichts gefunden",
                    message = if (state.query.isBlank())
                        "Sobald du Belege scannst, siehst du hier, was du wie oft kaufst."
                    else
                        "Für \"${state.query}\" gibt es keine Treffer."
                )
            }
        }

        items(state.products, key = { it.name + it.lastPurchaseIso }) { product ->
            ProductCard(product)
        }
    }
}

@Composable
private fun ProductCard(product: ProductSummary) {
    BonsaiCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${product.purchaseCount}× gekauft · Ø ${euro(product.averagePriceEuro)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    product.category.displayName +
                        (product.nutritionTag?.let { " · ${it.displayName}" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (product.lastMerchant.isNotBlank()) {
                    Text(
                        "zuletzt bei ${product.lastMerchant}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                euro(product.totalSpentEuro),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
