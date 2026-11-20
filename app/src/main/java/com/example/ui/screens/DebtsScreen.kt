package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AccountWithBalance
import com.example.data.model.CurrencyFormatter
import com.example.data.model.CurrencyOption
import com.example.data.model.DateFormatters
import com.example.data.model.DebtDetail
import com.example.data.model.DebtRecordModel
import com.example.data.model.DebtType
import com.example.data.model.IconRegistry
import com.example.ui.KinLedgerUiState
import com.example.ui.components.EmptyLedgerState
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassContainer
import com.example.ui.components.GlassSheetWrapper
import com.example.ui.theme.LocalLedgerColors

enum class DebtStatusTab {
    PENDING, SETTLED
}

@Composable
fun DebtsScreen(
    state: KinLedgerUiState,
    onSaveDebt: (
        id: String,
        previousDebt: DebtRecordModel?,
        type: DebtType,
        personName: String,
        amountMinor: Long,
        sourceAccountId: String,
        dateMillis: Long,
        dueDateMillis: Long?,
        note: String
    ) -> Unit,
    onSettleDebt: (
        debt: DebtRecordModel,
        amountMinor: Long,
        settlementAccountId: String,
        settledDateMillis: Long,
        note: String
    ) -> Unit,
    onReopenDebt: (DebtRecordModel) -> Unit,
    onDeleteDebt: (DebtRecordModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val ledgerColors = LocalLedgerColors.current
    var statusTab by remember { mutableStateOf(DebtStatusTab.PENDING) }
    var typeFilter by remember { mutableStateOf<DebtType?>(null) }

    var showAddEditSheet by remember { mutableStateOf(false) }
    var editingDebt by remember { mutableStateOf<DebtDetail?>(null) }
    var settlingDebt by remember { mutableStateOf<DebtDetail?>(null) }

    val filteredDebts = remember(state.debts, statusTab, typeFilter) {
        state.debts.filter { detail ->
            val matchesStatus = if (statusTab == DebtStatusTab.PENDING) !detail.debt.settled else detail.debt.settled
            val matchesType = typeFilter == null || detail.debt.debtType == typeFilter
            matchesStatus && matchesType
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lend & Borrow",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Receivables & Payables synced to accounts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            editingDebt = null
                            showAddEditSheet = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("add_debt_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Record", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 3D Glass Summary Cards: Cool Emerald/Cyan for Receivables vs Warm Amber/Coral for Payables
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GlassCard(
                        modifier = Modifier.weight(1f),
                        cornerRadius = 20.dp,
                        glowColor = ledgerColors.incomeGlow,
                        tintColor = ledgerColors.income,
                        shadowElevation = 12.dp,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Text(
                            text = "To Receive (Lent)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = CurrencyFormatter.formatMinor(state.totalPendingReceivablesMinor, state.currency),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = ledgerColors.income
                        )
                    }

                    GlassCard(
                        modifier = Modifier.weight(1f),
                        cornerRadius = 20.dp,
                        glowColor = ledgerColors.expense,
                        tintColor = ledgerColors.expense,
                        shadowElevation = 12.dp,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Text(
                            text = "To Pay (Borrowed)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = CurrencyFormatter.formatMinor(state.totalPendingPayablesMinor, state.currency),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = ledgerColors.expense
                        )
                    }
                }
            }

            // Frosted Status Tabs (Pending vs Settled) & Type Filter
            item {
                GlassContainer(
                    cornerRadius = 16.dp,
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            DebtStatusTab.PENDING to "Pending",
                            DebtStatusTab.SETTLED to "Settled History"
                        ).forEach { (tab, label) ->
                            val selected = statusTab == tab
                            Surface(
                                onClick = { statusTab = tab },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = if (ledgerColors.isDark) 0.28f else 0.18f)
                                } else {
                                    Color.Transparent
                                },
                                border = if (selected) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                                } else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("debt_status_tab_${tab.name.lowercase()}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        null to "All",
                        DebtType.RECEIVABLE to "Lent (Receivables)",
                        DebtType.PAYABLE to "Borrowed (Payables)"
                    ).forEach { (filterType, label) ->
                        val selected = typeFilter == filterType
                        val chipAccent = when (filterType) {
                            DebtType.RECEIVABLE -> ledgerColors.income
                            DebtType.PAYABLE -> ledgerColors.expense
                            null -> MaterialTheme.colorScheme.primary
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { typeFilter = filterType },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = ledgerColors.cardBackground,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = chipAccent.copy(alpha = 0.22f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = ledgerColors.border,
                                selectedBorderColor = chipAccent.copy(alpha = 0.7f),
                                borderWidth = 1.dp,
                                selectedBorderWidth = 1.2.dp
                            )
                        )
                    }
                }
            }

            if (filteredDebts.isEmpty()) {
                item {
                    EmptyLedgerState(
                        title = if (statusTab == DebtStatusTab.PENDING) "No pending lending or borrowing" else "No settled records yet",
                        subtitle = "Record money lent to friends or borrowed locally—your Cash or Bank balance updates automatically.",
                        actionLabel = if (statusTab == DebtStatusTab.PENDING) "Record Receivable / Payable" else null,
                        onAction = if (statusTab == DebtStatusTab.PENDING) {
                            {
                                editingDebt = null
                                showAddEditSheet = true
                            }
                        } else null
                    )
                }
            } else {
                items(filteredDebts, key = { it.debt.id }) { detail ->
                    DebtRecordCard(
                        detail = detail,
                        currency = state.currency,
                        onEdit = {
                            editingDebt = detail
                            showAddEditSheet = true
                        },
                        onSettleClick = { settlingDebt = detail },
                        onReopenClick = { onReopenDebt(detail.debt) }
                    )
                }
            }
        }
    }

    if (showAddEditSheet) {
        AddEditDebtSheet(
            editing = editingDebt,
            accounts = state.accounts,
            currency = state.currency,
            onDismiss = { showAddEditSheet = false },
            onSave = { id, prev, type, person, amountMinor, accountId, dateMillis, dueMillis, note ->
                onSaveDebt(id, prev, type, person, amountMinor, accountId, dateMillis, dueMillis, note)
                showAddEditSheet = false
            },
            onDelete = { debt ->
                onDeleteDebt(debt)
                showAddEditSheet = false
            }
        )
    }

    settlingDebt?.let { target ->
        SettleDebtSheet(
            detail = target,
            accounts = state.accounts,
            currency = state.currency,
            onDismiss = { settlingDebt = null },
            onConfirmSettle = { amountMinor, settlementAccountId, settledDateMillis, note ->
                onSettleDebt(target.debt, amountMinor, settlementAccountId, settledDateMillis, note)
                settlingDebt = null
            }
        )
    }
}

@Composable
private fun DebtRecordCard(
    detail: DebtDetail,
    currency: CurrencyOption,
    onEdit: () -> Unit,
    onSettleClick: () -> Unit,
    onReopenClick: () -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    val debt = detail.debt
    val isReceivable = debt.debtType == DebtType.RECEIVABLE
    val accent = if (isReceivable) ledgerColors.income else ledgerColors.expense
    val semanticGlow = if (isReceivable) ledgerColors.incomeGlow else ledgerColors.expense
    val originalMinor = debt.effectiveOriginalAmountMinor
    val settledMinor = debt.effectiveSettledAmountMinor
    val remainingMinor = debt.effectiveRemainingAmountMinor
    val progress = if (originalMinor > 0L) {
        (settledMinor.toFloat() / originalMinor.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    var historyExpanded by remember(debt.id) { mutableStateOf(false) }

    GlassCard(
        onClick = onEdit,
        cornerRadius = 20.dp,
        glowColor = semanticGlow,
        tintColor = accent,
        shadowElevation = 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("debt_card_${debt.id}"),
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.26f),
                                accent.copy(alpha = 0.10f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = accent.copy(alpha = 0.38f),
                        shape = RoundedCornerShape(13.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = debt.personName,
                    tint = if (ledgerColors.isDark) lerp(accent, Color.White, 0.2f) else accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = debt.personName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                val directionText = if (isReceivable) {
                    "Lent from ${detail.sourceAccount?.name ?: "Account"}"
                } else {
                    "Borrowed into ${detail.sourceAccount?.name ?: "Account"}"
                }
                val authorSuffix = if (debt.createdByName.isNotBlank()) " • ${debt.createdByName}" else ""
                Text(
                    text = "$directionText • ${DateFormatters.formatFullDate(debt.dateMillis)}$authorSuffix",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (debt.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = debt.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = ledgerColors.mutedText
                    )
                }
                if (debt.settled && debt.settledDateMillis != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val settleTarget = detail.settlementAccount?.name ?: detail.sourceAccount?.name ?: "Account"
                    Text(
                        text = "Settled via $settleTarget on ${DateFormatters.formatFullDate(debt.settledDateMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ledgerColors.income
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyFormatter.formatMinor(
                        if (!debt.settled && settledMinor > 0L) remainingMinor else originalMinor,
                        currency
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                if (!debt.settled && settledMinor > 0L) {
                    Text(
                        text = "of ${CurrencyFormatter.formatMinor(originalMinor, currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ledgerColors.mutedText
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accent.copy(alpha = if (ledgerColors.isDark) 0.18f else 0.14f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.32f))
                ) {
                    Text(
                        text = if (isReceivable) "Receivable" else "Payable",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        if (settledMinor > 0L || !debt.settled) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = accent,
                trackColor = if (ledgerColors.isDark) {
                    Color.White.copy(alpha = 0.10f)
                } else {
                    Color.Black.copy(alpha = 0.08f)
                },
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.height(6.dp))
            val paidLabel = if (isReceivable) "Received" else "Paid"
            val progressText = if (remainingMinor > 0L) {
                "$paidLabel ${CurrencyFormatter.formatMinor(settledMinor, currency)} of ${CurrencyFormatter.formatMinor(originalMinor, currency)} • Remaining: ${CurrencyFormatter.formatMinor(remainingMinor, currency)}"
            } else {
                "$paidLabel ${CurrencyFormatter.formatMinor(settledMinor, currency)} of ${CurrencyFormatter.formatMinor(originalMinor, currency)} • Settled in full"
            }
            Text(
                text = progressText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("debt_progress_text_${debt.id}")
            )
        }

        if (detail.settlementDetails.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { historyExpanded = !historyExpanded }
                    .padding(vertical = 6.dp, horizontal = 6.dp)
                    .testTag("toggle_history_${debt.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Payment History (${detail.settlementDetails.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (historyExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (historyExpanded) "Collapse payment history" else "Expand payment history",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = historyExpanded) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (ledgerColors.isDark) {
                        ledgerColors.cardBackground
                    } else {
                        Color.White.copy(alpha = 0.55f)
                    },
                    border = BorderStroke(1.dp, ledgerColors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        detail.settlementDetails.forEachIndexed { idx, item ->
                            val entry = item.entry
                            if (idx > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = ledgerColors.border
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val acctLabel = item.account?.name ?: "Account"
                                    val bySuffix = if (entry.settledByName.isNotBlank()) " • ${entry.settledByName}" else ""
                                    Text(
                                        text = "$acctLabel • ${DateFormatters.formatFullDate(entry.dateMillis)}$bySuffix",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (entry.note.isNotBlank()) {
                                        Text(
                                            text = entry.note,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ledgerColors.mutedText
                                        )
                                    }
                                }
                                Text(
                                    text = CurrencyFormatter.formatMinor(entry.amountMinor, currency),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!debt.settled) {
            Button(
                onClick = onSettleClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = if (isReceivable && ledgerColors.isDark) Color(0xFF031F16) else Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("settle_debt_button_${debt.id}")
            ) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (settledMinor > 0L) {
                        "Record Payment (${CurrencyFormatter.formatMinor(remainingMinor, currency)} left)"
                    } else {
                        "Record Settlement"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            OutlinedButton(
                onClick = onReopenClick,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, ledgerColors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("reopen_debt_button_${debt.id}")
            ) {
                Icon(Icons.Rounded.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reopen as Pending")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditDebtSheet(
    editing: DebtDetail?,
    accounts: List<AccountWithBalance>,
    currency: CurrencyOption,
    onDismiss: () -> Unit,
    onSave: (
        id: String,
        previousDebt: DebtRecordModel?,
        type: DebtType,
        personName: String,
        amountMinor: Long,
        sourceAccountId: String,
        dateMillis: Long,
        dueDateMillis: Long?,
        note: String
    ) -> Unit,
    onDelete: (DebtRecordModel) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ledgerColors = LocalLedgerColors.current

    var debtType by remember(editing) {
        mutableStateOf(editing?.debt?.debtType ?: DebtType.RECEIVABLE)
    }
    var personName by remember(editing) { mutableStateOf(editing?.debt?.personName ?: "") }
    var amountInput by remember(editing) {
        mutableStateOf(editing?.let { CurrencyFormatter.minorToEditableString(it.debt.amountMinor) } ?: "")
    }
    var selectedAccountId by remember(editing, accounts) {
        mutableStateOf(editing?.debt?.sourceAccountId ?: accounts.firstOrNull()?.account?.id.orEmpty())
    }
    var dateMillis by remember(editing) {
        mutableLongStateOf(editing?.debt?.dateMillis ?: DateFormatters.startOfTodayMillis())
    }
    var note by remember(editing) { mutableStateOf(editing?.debt?.note ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val accent = if (debtType == DebtType.RECEIVABLE) ledgerColors.income else ledgerColors.expense

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ledgerColors.glassSheetBackground,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        GlassSheetWrapper(accentColor = accent) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (editing == null) "Record Lend / Borrow" else "Edit Record",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (editing != null) {
                        IconButton(onClick = { onDelete(editing.debt) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = ledgerColors.expense)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                        .border(1.dp, ledgerColors.border, RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        DebtType.RECEIVABLE to "Lent (Receivable)",
                        DebtType.PAYABLE to "Borrowed (Payable)"
                    ).forEach { (type, label) ->
                        val selected = debtType == type
                        val pillColor = if (type == DebtType.RECEIVABLE) ledgerColors.income else ledgerColors.expense
                        Surface(
                            onClick = { debtType = type },
                            shape = RoundedCornerShape(11.dp),
                            color = if (selected) pillColor else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("debt_type_${type.name.lowercase()}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) {
                                        if (type == DebtType.RECEIVABLE && ledgerColors.isDark) Color(0xFF031F16) else Color.White
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it; errorText = null },
                    label = { Text("Person / Party Name") },
                    placeholder = { Text("e.g. Uncle Thomas, Neighbor") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ledgerColors.cardBackground,
                        unfocusedContainerColor = ledgerColors.cardBackground,
                        unfocusedBorderColor = ledgerColors.border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("debt_person_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1) {
                            amountInput = filtered
                            errorText = null
                        }
                    },
                    label = { Text("Amount") },
                    prefix = { Text(currency.symbol, fontWeight = FontWeight.Bold, color = accent) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ledgerColors.cardBackground,
                        unfocusedContainerColor = ledgerColors.cardBackground,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = ledgerColors.border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("debt_amount_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (debtType == DebtType.RECEIVABLE) {
                        "Deduct from Account (auto-updates balance)"
                    } else {
                        "Credit into Account (auto-updates balance)"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    accounts.forEach { item ->
                        val selected = item.account.id == selectedAccountId
                        val acctColor = IconRegistry.colorFromLong(item.account.colorArgb)
                        Surface(
                            onClick = { selectedAccountId = item.account.id },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) acctColor.copy(alpha = 0.20f) else ledgerColors.cardBackground,
                            border = BorderStroke(
                                if (selected) 1.6.dp else 1.dp,
                                if (selected) acctColor else ledgerColors.border
                            ),
                            modifier = Modifier.testTag("debt_account_${item.account.id}")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                Text(item.account.name, style = MaterialTheme.typography.labelLarge)
                                Text(
                                    CurrencyFormatter.formatMinor(item.currentBalanceMinor, currency),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ledgerColors.border),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Date: ${DateFormatters.formatFullDate(dateMillis)}")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ledgerColors.cardBackground,
                        unfocusedContainerColor = ledgerColors.cardBackground,
                        unfocusedBorderColor = ledgerColors.border
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorText!!, style = MaterialTheme.typography.bodySmall, color = ledgerColors.expense)
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        val amountMinor = CurrencyFormatter.parseMajorToMinor(amountInput)
                        when {
                            personName.isBlank() -> errorText = "Please enter the person's name."
                            amountMinor == null || amountMinor <= 0L -> errorText = "Enter an amount greater than 0."
                            selectedAccountId.isBlank() -> errorText = "Select an account."
                            else -> onSave(
                                editing?.debt?.id.orEmpty(),
                                editing?.debt,
                                debtType,
                                personName,
                                amountMinor,
                                selectedAccountId,
                                dateMillis,
                                null,
                                note
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = if (debtType == DebtType.RECEIVABLE && ledgerColors.isDark) Color(0xFF031F16) else Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_debt_button")
                ) {
                    Text(
                        text = if (editing == null) "Save Record" else "Update Record",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { dateMillis = it }
                        showDatePicker = false
                    }
                ) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettleDebtSheet(
    detail: DebtDetail,
    accounts: List<AccountWithBalance>,
    currency: CurrencyOption,
    onDismiss: () -> Unit,
    onConfirmSettle: (
        amountMinor: Long,
        settlementAccountId: String,
        settledDateMillis: Long,
        note: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ledgerColors = LocalLedgerColors.current
    val debt = detail.debt
    val isReceivable = debt.debtType == DebtType.RECEIVABLE
    val accent = if (isReceivable) ledgerColors.income else ledgerColors.expense
    val remainingMinor = debt.effectiveRemainingAmountMinor
    val originalMinor = debt.effectiveOriginalAmountMinor
    val settledMinor = debt.effectiveSettledAmountMinor

    var amountInput by remember(detail) {
        mutableStateOf(CurrencyFormatter.minorToEditableString(remainingMinor))
    }
    var selectedAccountId by remember(detail, accounts) {
        mutableStateOf(
            accounts.firstOrNull { it.account.id == debt.sourceAccountId }?.account?.id
                ?: accounts.firstOrNull()?.account?.id.orEmpty()
        )
    }
    var settledDateMillis by remember(detail) {
        mutableLongStateOf(DateFormatters.startOfTodayMillis())
    }
    var note by remember(detail) { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val parsedAmountMinor = CurrencyFormatter.parseMajorToMinor(amountInput)
    val previewDelta = (parsedAmountMinor ?: 0L).coerceAtLeast(0L)
    val isFullPayment = parsedAmountMinor == remainingMinor

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ledgerColors.glassSheetBackground,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        GlassSheetWrapper(accentColor = accent) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        tint = accent
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isReceivable) "Record Receipt" else "Record Repayment",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = buildString {
                        append(if (isReceivable) "From " else "To ")
                        append(debt.personName)
                        append(" • Remaining ")
                        append(CurrencyFormatter.formatMinor(remainingMinor, currency))
                        if (settledMinor > 0L) {
                            append(" (of ")
                            append(CurrencyFormatter.formatMinor(originalMinor, currency))
                            append(")")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1) {
                            amountInput = filtered
                            errorText = null
                        }
                    },
                    label = { Text("Settlement Amount") },
                    prefix = { Text(currency.symbol, fontWeight = FontWeight.Bold, color = accent) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ledgerColors.cardBackground,
                        unfocusedContainerColor = ledgerColors.cardBackground,
                        focusedBorderColor = accent,
                        unfocusedBorderColor = ledgerColors.border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settle_amount_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick fraction chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val quarterMinor = (remainingMinor / 4L).coerceAtLeast(1L)
                    val halfMinor = (remainingMinor / 2L).coerceAtLeast(1L)
                    listOf(
                        "25%" to quarterMinor,
                        "50%" to halfMinor,
                        "Full Remaining" to remainingMinor
                    ).forEach { (label, targetMinor) ->
                        val selected = parsedAmountMinor == targetMinor
                        FilterChip(
                            selected = selected,
                            onClick = {
                                amountInput = CurrencyFormatter.minorToEditableString(targetMinor)
                                errorText = null
                            },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = ledgerColors.cardBackground,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = accent.copy(alpha = 0.22f),
                                selectedLabelColor = accent
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = ledgerColors.border,
                                selectedBorderColor = accent.copy(alpha = 0.7f),
                                borderWidth = 1.dp,
                                selectedBorderWidth = 1.2.dp
                            ),
                            modifier = Modifier.testTag("settle_chip_${label.lowercase().replace("%", "pct").replace(" ", "_")}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isReceivable) "Credit into Account" else "Pay from Account",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                accounts.forEach { acct ->
                    val selected = acct.account.id == selectedAccountId
                    val delta = if (isReceivable) previewDelta else -previewDelta
                    val projected = acct.currentBalanceMinor + delta
                    Surface(
                        onClick = {
                            selectedAccountId = acct.account.id
                            errorText = null
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) accent.copy(alpha = 0.16f) else ledgerColors.cardBackground,
                        border = BorderStroke(
                            if (selected) 1.6.dp else 1.dp,
                            if (selected) accent else ledgerColors.border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("settle_account_option_${acct.account.id}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(acct.account.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Current: ${CurrencyFormatter.formatMinor(acct.currentBalanceMinor, currency)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "→ ${CurrencyFormatter.formatMinor(projected, currency)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Date selector
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val todayMillis = remember { DateFormatters.startOfTodayMillis() }
                    val yesterdayMillis = remember { DateFormatters.startOfYesterdayMillis() }
                    FilterChip(
                        selected = DateFormatters.formatDate(settledDateMillis) == "Today",
                        onClick = { settledDateMillis = todayMillis },
                        label = { Text("Today") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = ledgerColors.cardBackground,
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = DateFormatters.formatDate(settledDateMillis) == "Today",
                            borderColor = ledgerColors.border,
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    )
                    FilterChip(
                        selected = DateFormatters.formatDate(settledDateMillis) == "Yesterday",
                        onClick = { settledDateMillis = yesterdayMillis },
                        label = { Text("Yesterday") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = ledgerColors.cardBackground,
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = DateFormatters.formatDate(settledDateMillis) == "Yesterday",
                            borderColor = ledgerColors.border,
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    )
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ledgerColors.border),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(DateFormatters.formatShortDate(settledDateMillis))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. UPI installment 1, cash handover") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ledgerColors.cardBackground,
                        unfocusedContainerColor = ledgerColors.cardBackground,
                        unfocusedBorderColor = ledgerColors.border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settle_note_input")
                )

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = ledgerColors.expense
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        val amount = parsedAmountMinor
                        when {
                            amount == null || amount <= 0L -> {
                                errorText = "Enter a settlement amount greater than 0."
                            }
                            amount > remainingMinor -> {
                                errorText = "Amount cannot exceed remaining ${CurrencyFormatter.formatMinor(remainingMinor, currency)}."
                            }
                            selectedAccountId.isBlank() -> {
                                errorText = "Please select an account."
                            }
                            else -> onConfirmSettle(amount, selectedAccountId, settledDateMillis, note.trim())
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = if (isReceivable && ledgerColors.isDark) Color(0xFF031F16) else Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("confirm_settle_button")
                ) {
                    Text(
                        text = when {
                            isFullPayment && isReceivable -> "Confirm Full Receipt"
                            isFullPayment -> "Confirm Full Payment"
                            isReceivable -> "Record Partial Receipt"
                            else -> "Record Partial Payment"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = settledDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { settledDateMillis = it }
                        showDatePicker = false
                    }
                ) { Text("Select") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
