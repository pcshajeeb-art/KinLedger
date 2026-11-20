package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AccountWithBalance
import com.example.data.model.CurrencyFormatter
import com.example.data.model.TransactionDetail
import com.example.data.model.TransactionType
import com.example.ui.KinLedgerUiState
import com.example.ui.components.AccountBalanceCard
import com.example.ui.components.EmptyLedgerState
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassContainer
import com.example.ui.components.TransactionRow
import com.example.ui.theme.LocalLedgerColors

enum class DashboardFilter {
    ALL, EXPENSE, INCOME, TRANSFER
}

@Composable
fun DashboardScreen(
    state: KinLedgerUiState,
    onAddTransactionClick: () -> Unit,
    onEditTransactionClick: (TransactionDetail) -> Unit,
    onAccountClick: (AccountWithBalance) -> Unit,
    onAddAccountClick: () -> Unit,
    onNavigateToDebts: () -> Unit,
    onNavigateToReports: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ledgerColors = LocalLedgerColors.current
    var selectedFilter by remember { mutableStateOf(DashboardFilter.ALL) }
    var selectedAccountFilterId by remember { mutableStateOf<String?>(null) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredTransactions = remember(
        state.transactions,
        selectedFilter,
        selectedAccountFilterId,
        searchQuery
    ) {
        state.transactions.filter { detail ->
            val txType = detail.transaction.transactionType
            val matchesType = when (selectedFilter) {
                DashboardFilter.ALL -> true
                DashboardFilter.EXPENSE -> txType == TransactionType.EXPENSE
                DashboardFilter.INCOME -> txType == TransactionType.INCOME
                DashboardFilter.TRANSFER -> txType == TransactionType.TRANSFER
            }
            val matchesAccount = selectedAccountFilterId == null ||
                detail.transaction.accountId == selectedAccountFilterId ||
                detail.transaction.destinationAccountId == selectedAccountFilterId
            val matchesSearch = searchQuery.isBlank() ||
                (detail.category?.name?.contains(searchQuery, ignoreCase = true) == true) ||
                detail.transaction.note.contains(searchQuery, ignoreCase = true) ||
                detail.transaction.createdByName.contains(searchQuery, ignoreCase = true) ||
                (detail.account?.name?.contains(searchQuery, ignoreCase = true) == true) ||
                (detail.destinationAccount?.name?.contains(searchQuery, ignoreCase = true) == true)
            matchesType && matchesAccount && matchesSearch
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
            // Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.ledgerName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val memberCount = state.workspace?.memberUids?.size ?: 1
                        val memberLabel = if (memberCount == 1) "1 member" else "$memberCount members"
                        Text(
                            text = if (state.isDemoMode) {
                                "Demo Preview • Local sample data (not synced)"
                            } else {
                                "$memberLabel • Live cloud sync & offline ready"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    GlassContainer(
                        shape = CircleShape,
                        cornerRadius = 24.dp,
                        shadowElevation = 6.dp,
                        onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) searchQuery = ""
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("dashboard_search_toggle")
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = if (searchVisible) "Close search" else "Search transactions",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            if (searchVisible) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search category, note, or account...") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ledgerColors.cardBackground,
                            unfocusedContainerColor = ledgerColors.cardBackground,
                            unfocusedBorderColor = ledgerColors.border,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_search_input")
                    )
                }
            }

            // 3D Glass Total Family Balance Hero Card
            item {
                val primaryColor = MaterialTheme.colorScheme.primary
                GlassCard(
                    cornerRadius = 24.dp,
                    glowColor = primaryColor,
                    tintColor = primaryColor,
                    shadowElevation = 16.dp,
                    borderWidth = 1.2.dp,
                    contentPadding = PaddingValues(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("total_balance_card")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Total Net Balance",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            onClick = onNavigateToReports,
                            shape = RoundedCornerShape(20.dp),
                            color = primaryColor.copy(alpha = if (ledgerColors.isDark) 0.18f else 0.14f),
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.35f)),
                            modifier = Modifier.testTag("hero_view_report_button")
                        ) {
                            Text(
                                text = "Monthly Report →",
                                style = MaterialTheme.typography.labelMedium,
                                color = primaryColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = CurrencyFormatter.formatMinor(state.totalBalanceMinor, state.currency),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Inset frosted glass Income vs Expense pills
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ledgerColors.incomeContainer.copy(alpha = if (ledgerColors.isDark) 0.78f else 0.72f),
                            border = BorderStroke(1.dp, ledgerColors.income.copy(alpha = if (ledgerColors.isDark) 0.45f else 0.28f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(ledgerColors.income.copy(alpha = 0.20f))
                                        .border(1.dp, ledgerColors.income.copy(alpha = 0.42f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDownward,
                                        contentDescription = "Income",
                                        tint = ledgerColors.income,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "This Month In",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = CurrencyFormatter.formatMinor(
                                            state.currentMonthIncomeMinor,
                                            state.currency
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ledgerColors.income
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ledgerColors.expenseContainer.copy(alpha = if (ledgerColors.isDark) 0.78f else 0.72f),
                            border = BorderStroke(1.dp, ledgerColors.expense.copy(alpha = if (ledgerColors.isDark) 0.45f else 0.28f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(ledgerColors.expense.copy(alpha = 0.20f))
                                        .border(1.dp, ledgerColors.expense.copy(alpha = 0.42f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowUpward,
                                        contentDescription = "Expense",
                                        tint = ledgerColors.expense,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "This Month Out",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = CurrencyFormatter.formatMinor(
                                            state.currentMonthExpenseMinor,
                                            state.currency
                                        ),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ledgerColors.expense
                                    )
                                }
                            }
                        }
                    }

                    if (state.totalPendingReceivablesMinor > 0L || state.totalPendingPayablesMinor > 0L) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            onClick = onNavigateToDebts,
                            shape = RoundedCornerShape(14.dp),
                            color = ledgerColors.debtContainer.copy(alpha = if (ledgerColors.isDark) 0.72f else 0.72f),
                            border = BorderStroke(1.dp, ledgerColors.debt.copy(alpha = if (ledgerColors.isDark) 0.42f else 0.32f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SwapHoriz,
                                    contentDescription = null,
                                    tint = ledgerColors.debt,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Pending: To Receive ${CurrencyFormatter.formatMinor(state.totalPendingReceivablesMinor, state.currency)} • To Pay ${CurrencyFormatter.formatMinor(state.totalPendingPayablesMinor, state.currency)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 3D Glass Accounts Strip
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Accounts",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (selectedAccountFilterId != null) {
                        TextButton(onClick = { selectedAccountFilterId = null }) {
                            Text("Clear filter")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(state.accounts, key = { it.account.id }) { acct ->
                        val isFilterSelected = selectedAccountFilterId == acct.account.id
                        AccountBalanceCard(
                            item = acct,
                            currency = state.currency,
                            isSelected = isFilterSelected,
                            onClick = {
                                selectedAccountFilterId = if (isFilterSelected) null else acct.account.id
                                onAccountClick(acct)
                            }
                        )
                    }
                    item {
                        GlassCard(
                            onClick = onAddAccountClick,
                            cornerRadius = 20.dp,
                            glowColor = MaterialTheme.colorScheme.primary,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .width(132.dp)
                                .height(118.dp)
                                .testTag("dashboard_add_account_card"),
                            contentPadding = PaddingValues(14.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                                )
                                            )
                                        )
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Add Account",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Add Account",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Recent Activity Header + Filter Chips
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${filteredTransactions.size} entries",
                        style = MaterialTheme.typography.labelMedium,
                        color = ledgerColors.mutedText
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        DashboardFilter.ALL to "All",
                        DashboardFilter.EXPENSE to "Expenses",
                        DashboardFilter.INCOME to "Income",
                        DashboardFilter.TRANSFER to "Transfers"
                    ).forEach { (filter, label) ->
                        val selected = selectedFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = { selectedFilter = filter },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = ledgerColors.cardBackground,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = ledgerColors.border,
                                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                borderWidth = 1.dp,
                                selectedBorderWidth = 1.2.dp
                            ),
                            modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            // Transactions List or Empty State
            if (filteredTransactions.isEmpty()) {
                item {
                    EmptyLedgerState(
                        title = if (state.transactions.isEmpty()) "Ready for your first entry" else "No matching entries",
                        subtitle = if (state.transactions.isEmpty()) {
                            "Log a household expense or an NRI remittance to Cash or Bank in under 10 seconds."
                        } else {
                            "Try clearing your active search or filter."
                        },
                        actionLabel = if (state.transactions.isEmpty()) "Add First Entry" else null,
                        onAction = if (state.transactions.isEmpty()) onAddTransactionClick else null
                    )
                }
            } else {
                items(
                    items = filteredTransactions,
                    key = { it.transaction.id }
                ) { detail ->
                    TransactionRow(
                        detail = detail,
                        currency = state.currency,
                        onClick = { onEditTransactionClick(detail) }
                    )
                }
            }
        }
    }
}
