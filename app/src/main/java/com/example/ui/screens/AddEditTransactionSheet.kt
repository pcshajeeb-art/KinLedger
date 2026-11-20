package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AccountWithBalance
import com.example.data.model.CategoryModel
import com.example.data.model.CurrencyFormatter
import com.example.data.model.CurrencyOption
import com.example.data.model.DateFormatters
import com.example.data.model.IconRegistry
import com.example.data.model.TransactionDetail
import com.example.data.model.TransactionModel
import com.example.data.model.TransactionType
import com.example.ui.components.GlassSheetWrapper
import com.example.ui.theme.LocalLedgerColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditTransactionSheet(
    editing: TransactionDetail?,
    accounts: List<AccountWithBalance>,
    categories: List<CategoryModel>,
    currency: CurrencyOption,
    onDismiss: () -> Unit,
    onSave: (
        id: String,
        previousTransaction: TransactionModel?,
        type: TransactionType,
        amountMinor: Long,
        accountId: String,
        destinationAccountId: String?,
        categoryId: String?,
        dateMillis: Long,
        note: String
    ) -> Unit,
    onDelete: (TransactionModel) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ledgerColors = LocalLedgerColors.current

    var txType by remember(editing) {
        mutableStateOf(editing?.transaction?.transactionType ?: TransactionType.EXPENSE)
    }
    var amountInput by remember(editing) {
        mutableStateOf(
            editing?.let { CurrencyFormatter.minorToEditableString(it.transaction.amountMinor) } ?: ""
        )
    }
    var selectedAccountId by remember(editing, accounts) {
        mutableStateOf(
            editing?.transaction?.accountId ?: accounts.firstOrNull()?.account?.id.orEmpty()
        )
    }
    var selectedDestinationAccountId by remember(editing, accounts) {
        val defaultDest = editing?.transaction?.destinationAccountId
            ?: accounts.firstOrNull { it.account.id != selectedAccountId }?.account?.id
            ?: ""
        mutableStateOf(defaultDest)
    }

    LaunchedEffect(txType, selectedAccountId, accounts) {
        if (txType == TransactionType.TRANSFER &&
            (selectedDestinationAccountId.isBlank() || selectedDestinationAccountId == selectedAccountId)
        ) {
            selectedDestinationAccountId = accounts
                .firstOrNull { it.account.id != selectedAccountId }
                ?.account?.id
                .orEmpty()
        }
    }

    val availableCategories = remember(categories, txType, editing) {
        if (txType == TransactionType.TRANSFER) {
            emptyList()
        } else {
            categories.filter {
                it.transactionType == txType && (!it.archived || it.id == editing?.transaction?.categoryId)
            }
        }
    }
    var selectedCategoryId by remember(editing) {
        mutableStateOf(editing?.transaction?.categoryId.orEmpty())
    }
    LaunchedEffect(txType, availableCategories) {
        if (txType != TransactionType.TRANSFER && availableCategories.none { it.id == selectedCategoryId }) {
            selectedCategoryId = availableCategories.firstOrNull()?.id.orEmpty()
        }
    }

    var dateMillis by remember(editing) {
        mutableLongStateOf(editing?.transaction?.dateMillis ?: DateFormatters.startOfTodayMillis())
    }
    var note by remember(editing) {
        mutableStateOf(editing?.transaction?.note ?: "")
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val typeAccent = when (txType) {
        TransactionType.EXPENSE -> ledgerColors.expense
        TransactionType.INCOME -> ledgerColors.income
        TransactionType.TRANSFER -> ledgerColors.transfer
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ledgerColors.glassSheetBackground,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        GlassSheetWrapper(accentColor = typeAccent) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (editing == null) "Quick Entry" else "Edit Entry",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (editing != null) {
                        IconButton(
                            onClick = { onDelete(editing.transaction) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_transaction_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete entry",
                                tint = ledgerColors.expense
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Type Toggle: Expense vs Income vs Transfer
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
                        TransactionType.EXPENSE to "Expense",
                        TransactionType.INCOME to "Income",
                        TransactionType.TRANSFER to "Transfer"
                    ).forEach { (type, label) ->
                        val selected = txType == type
                        val pillColor = when (type) {
                            TransactionType.EXPENSE -> ledgerColors.expense
                            TransactionType.INCOME -> ledgerColors.income
                            TransactionType.TRANSFER -> ledgerColors.transfer
                        }
                        Surface(
                            onClick = {
                                txType = type
                                errorText = null
                            },
                            shape = RoundedCornerShape(11.dp),
                            color = if (selected) pillColor else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("tx_type_${type.name.lowercase()}")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) {
                                        if (ledgerColors.isDark && type != TransactionType.EXPENSE) Color(0xFF031F16) else Color.White
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Large Amount Field + Quick Preset Chips
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
                    placeholder = { Text("0") },
                    prefix = {
                        Text(
                            text = currency.symbol,
                            style = MaterialTheme.typography.headlineMedium,
                            color = typeAccent,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = typeAccent
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ledgerColors.cardBackground,
                        unfocusedContainerColor = ledgerColors.cardBackground,
                        focusedBorderColor = typeAccent,
                        unfocusedBorderColor = ledgerColors.border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_amount_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    listOf(100, 500, 1000, 5000).forEach { addVal ->
                        Surface(
                            onClick = {
                                val cur = amountInput.toDoubleOrNull() ?: 0.0
                                val updated = cur + addVal
                                amountInput = if (updated % 1.0 == 0.0) {
                                    updated.toLong().toString()
                                } else {
                                    updated.toString()
                                }
                                errorText = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = ledgerColors.cardBackground,
                            border = BorderStroke(1.dp, ledgerColors.border)
                        ) {
                            Text(
                                text = "+${currency.symbol}$addVal",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Account Selector (or Source Account for Transfer)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when (txType) {
                            TransactionType.INCOME -> "Deposit Into Account"
                            TransactionType.EXPENSE -> "Paid From Account"
                            TransactionType.TRANSFER -> "From Account (Source)"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (txType == TransactionType.TRANSFER && accounts.size >= 2) {
                        TextButton(
                            onClick = {
                                val prevSource = selectedAccountId
                                selectedAccountId = selectedDestinationAccountId
                                selectedDestinationAccountId = prevSource
                                errorText = null
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("swap_transfer_accounts_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SwapVert,
                                contentDescription = "Swap accounts",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Swap")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                AccountChipStrip(
                    accounts = accounts,
                    selectedAccountId = selectedAccountId,
                    currency = currency,
                    testTagPrefix = "tx_account_",
                    onSelect = { id ->
                        selectedAccountId = id
                        if (txType == TransactionType.TRANSFER && selectedDestinationAccountId == id) {
                            selectedDestinationAccountId = accounts
                                .firstOrNull { it.account.id != id }
                                ?.account?.id
                                .orEmpty()
                        }
                        errorText = null
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (txType == TransactionType.TRANSFER) {
                    // Destination Account Selector
                    Text(
                        text = "To Account (Destination)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (accounts.size < 2) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ledgerColors.transferContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, ledgerColors.transfer.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Add at least two accounts in Settings to transfer money between them.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        AccountChipStrip(
                            accounts = accounts,
                            selectedAccountId = selectedDestinationAccountId,
                            disabledAccountId = selectedAccountId,
                            currency = currency,
                            testTagPrefix = "tx_dest_account_",
                            onSelect = { id ->
                                selectedDestinationAccountId = id
                                errorText = null
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Contra transfer moves funds between accounts without counting as monthly Income or Expense.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ledgerColors.mutedText
                        )
                    }
                } else {
                    // Visual Category Grid
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        availableCategories.forEach { cat ->
                            val selected = cat.id == selectedCategoryId
                            val catColor = IconRegistry.colorFromLong(cat.colorArgb)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) catColor.copy(alpha = 0.22f)
                                        else ledgerColors.cardBackground
                                    )
                                    .border(
                                        width = if (selected) 1.6.dp else 1.dp,
                                        color = if (selected) catColor else ledgerColors.border,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedCategoryId = cat.id }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("tx_category_${cat.id}")
                            ) {
                                Icon(
                                    imageVector = IconRegistry.resolveIcon(cat.iconKey),
                                    contentDescription = null,
                                    tint = if (selected) catColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Date Selector
                Text(
                    text = "Date",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                val todayLabel = DateFormatters.formatDate(dateMillis)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = todayLabel == "Today",
                        onClick = { dateMillis = DateFormatters.startOfTodayMillis() },
                        label = { Text("Today") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = ledgerColors.cardBackground,
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = todayLabel == "Today",
                            borderColor = ledgerColors.border,
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    )
                    FilterChip(
                        selected = todayLabel == "Yesterday",
                        onClick = { dateMillis = DateFormatters.startOfYesterdayMillis() },
                        label = { Text("Yesterday") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = ledgerColors.cardBackground,
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = todayLabel == "Yesterday",
                            borderColor = ledgerColors.border,
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    )
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ledgerColors.border),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CalendarToday,
                            contentDescription = "Choose date",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = DateFormatters.formatFullDate(dateMillis),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Optional Short Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    placeholder = {
                        Text(
                            when (txType) {
                                TransactionType.INCOME -> "e.g. Monthly support from Dubai"
                                TransactionType.EXPENSE -> "e.g. Milk, veggies, pharmacy"
                                TransactionType.TRANSFER -> "e.g. ATM cash withdrawal"
                            }
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ledgerColors.cardBackground,
                        unfocusedContainerColor = ledgerColors.cardBackground,
                        unfocusedBorderColor = ledgerColors.border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tx_note_input")
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

                // Save Button
                Button(
                    onClick = {
                        val amountMinor = CurrencyFormatter.parseMajorToMinor(amountInput)
                        when {
                            amountMinor == null || amountMinor <= 0L -> {
                                errorText = "Please enter an amount greater than 0."
                            }
                            selectedAccountId.isBlank() -> {
                                errorText = "Please select an account."
                            }
                            txType == TransactionType.TRANSFER && selectedDestinationAccountId.isBlank() -> {
                                errorText = "Please select a destination account."
                            }
                            txType == TransactionType.TRANSFER && selectedDestinationAccountId == selectedAccountId -> {
                                errorText = "Source and destination accounts must be different."
                            }
                            txType != TransactionType.TRANSFER && selectedCategoryId.isBlank() -> {
                                errorText = "Please select a category."
                            }
                            else -> {
                                onSave(
                                    editing?.transaction?.id.orEmpty(),
                                    editing?.transaction,
                                    txType,
                                    amountMinor,
                                    selectedAccountId,
                                    if (txType == TransactionType.TRANSFER) selectedDestinationAccountId else null,
                                    if (txType == TransactionType.TRANSFER) null else selectedCategoryId,
                                    dateMillis,
                                    note
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = typeAccent,
                        contentColor = if (ledgerColors.isDark && txType != TransactionType.EXPENSE) Color(0xFF031F16) else Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_transaction_button")
                ) {
                    val saveLabel = when {
                        editing != null -> "Update Entry"
                        txType == TransactionType.INCOME -> "Save Income"
                        txType == TransactionType.EXPENSE -> "Save Expense"
                        else -> "Save Transfer"
                    }
                    Text(
                        text = saveLabel,
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
                ) {
                    Text("Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun AccountChipStrip(
    accounts: List<AccountWithBalance>,
    selectedAccountId: String,
    currency: CurrencyOption,
    testTagPrefix: String,
    onSelect: (String) -> Unit,
    disabledAccountId: String? = null
) {
    val ledgerColors = LocalLedgerColors.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        accounts.forEach { item ->
            val enabled = item.account.id != disabledAccountId
            val selected = item.account.id == selectedAccountId && enabled
            val acctColor = IconRegistry.colorFromLong(item.account.colorArgb)
            Surface(
                onClick = { if (enabled) onSelect(item.account.id) },
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
                color = when {
                    !enabled -> ledgerColors.cardBackground.copy(alpha = 0.35f)
                    selected -> acctColor.copy(alpha = 0.20f)
                    else -> ledgerColors.cardBackground
                },
                border = BorderStroke(
                    width = if (selected) 1.6.dp else 1.dp,
                    color = if (selected) acctColor else ledgerColors.border
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("$testTagPrefix${item.account.id}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = IconRegistry.resolveIcon(item.account.iconKey),
                        contentDescription = null,
                        tint = when {
                            !enabled -> ledgerColors.mutedText
                            selected -> acctColor
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = item.account.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (enabled) MaterialTheme.colorScheme.onSurface else ledgerColors.mutedText
                        )
                        Text(
                            text = CurrencyFormatter.formatMinor(item.currentBalanceMinor, currency),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else ledgerColors.mutedText
                        )
                    }
                }
            }
        }
    }
}
