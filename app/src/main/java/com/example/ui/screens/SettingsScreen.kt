package com.example.ui.screens

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AccountModel
import com.example.data.model.AccountWithBalance
import com.example.data.model.CategoryModel
import com.example.data.model.CurrencyFormatter
import com.example.data.model.CurrencyOption
import com.example.data.model.DateFormatters
import com.example.data.model.DriveSyncStatus
import com.example.data.model.IconRegistry
import com.example.data.model.TransactionType
import com.example.ui.KinLedgerUiState
import com.example.ui.components.ColorPickerRow
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassSheetWrapper
import com.example.ui.components.IconPickerGrid
import com.example.ui.theme.LocalLedgerColors
import com.example.ui.theme.ThemePreference

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: KinLedgerUiState,
    openAddAccountOnLaunch: Boolean = false,
    onConsumeAddAccountTrigger: () -> Unit = {},
    onUpdateLedgerName: (String) -> Unit,
    onSelectCurrency: (String) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onSaveAccount: (id: String, name: String, initialBalanceMinor: Long, iconKey: String, colorArgb: Long) -> Unit,
    onDeleteAccount: (AccountModel) -> Unit,
    onSaveCategory: (id: String, name: String, type: TransactionType, iconKey: String, colorArgb: Long) -> Unit,
    onToggleArchiveCategory: (CategoryModel) -> Unit,
    onDeleteCategory: (CategoryModel) -> Unit,
    onSwitchWorkspace: () -> Unit,
    onSyncWithDrive: () -> Unit = {},
    onAuthorizeDrive: (PendingIntent) -> Unit = {},
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ledgerColors = LocalLedgerColors.current
    var ledgerNameInput by remember(state.ledgerName) { mutableStateOf(state.ledgerName) }
    var categoryTab by remember { mutableStateOf(TransactionType.EXPENSE) }
    var codeCopied by remember { mutableStateOf(false) }

    var showAccountSheet by remember { mutableStateOf(openAddAccountOnLaunch) }
    var editingAccount by remember { mutableStateOf<AccountWithBalance?>(null) }
    var accountPendingDelete by remember { mutableStateOf<AccountModel?>(null) }

    if (openAddAccountOnLaunch) {
        onConsumeAddAccountTrigger()
    }

    var showCategorySheet by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryModel?>(null) }

    val filteredCategories = remember(state.categories, categoryTab) {
        state.categories.filter { it.transactionType == categoryTab }
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
                Text(
                    text = "Settings & Family Sync",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Invite family members, manage accounts, and customize categories",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Family Workspace Invite & Account Card
            item {
                val inviteCode = state.workspace?.inviteCode.orEmpty()
                val memberCount = state.workspace?.memberUids?.size ?: 1
                GlassCard(
                    cornerRadius = 20.dp,
                    glowColor = MaterialTheme.colorScheme.primary,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.userProfile?.displayName?.ifBlank { "Signed-in Member" } ?: "Signed-in Member",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = state.userProfile?.email?.ifBlank { "$memberCount member(s) synced" }
                                    ?: "$memberCount member(s) synced",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (state.isDemoMode) {
                                ledgerColors.debtContainer.copy(alpha = 0.7f)
                            } else {
                                ledgerColors.incomeContainer.copy(alpha = 0.7f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (state.isDemoMode) ledgerColors.debt.copy(alpha = 0.35f)
                                else ledgerColors.income.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = if (state.isDemoMode) "Local Demo" else "$memberCount synced",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (state.isDemoMode) ledgerColors.debt else ledgerColors.income,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (inviteCode.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (ledgerColors.isDark) ledgerColors.cardBackground else Color.White.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, ledgerColors.border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Family Invite Code",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = inviteCode,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("workspace_invite_code_text")
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("KinLedger Invite Code", inviteCode))
                                        codeCopied = true
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("copy_invite_code_button")
                                ) {
                                    Icon(
                                        imageVector = if (codeCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy invite code",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val driveIdPart = state.workspace?.driveFileId?.takeIf { it.isNotBlank() }
                                            ?.let { "\nDrive File ID: $it" } ?: ""
                                        val shareText = "Join our family ledger \"${state.ledgerName}\" on KinLedger!\nInvite Code: $inviteCode$driveIdPart"
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share Family Ledger"))
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("share_invite_code_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = "Share invite code",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = onSwitchWorkspace,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ledgerColors.border),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("switch_workspace_button")
                        ) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Switch Ledger", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ledgerColors.expense.copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ledgerColors.expense),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("settings_sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (state.isDemoMode) "Exit Demo" else "Sign Out", maxLines = 1)
                        }
                    }
                }
            }

            // Ledger Name & Preferences
            item {
                GlassCard(
                    cornerRadius = 20.dp,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text("Family Ledger Profile", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = ledgerNameInput,
                            onValueChange = { ledgerNameInput = it },
                            label = { Text("Ledger Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ledgerColors.cardBackground,
                                unfocusedContainerColor = ledgerColors.cardBackground,
                                unfocusedBorderColor = ledgerColors.border
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onUpdateLedgerName(ledgerNameInput) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("Save")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Currency", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CurrencyOption.ALL.forEach { option ->
                            val selected = state.currency.code == option.code
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectCurrency(option.code) },
                                label = { Text("${option.symbol.trim()} ${option.code}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = ledgerColors.cardBackground,
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = ledgerColors.border,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.testTag("currency_${option.code}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Appearance", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ThemePreference.DARK to "Dark Glass",
                            ThemePreference.LIGHT to "Light Glass",
                            ThemePreference.SYSTEM to "System"
                        ).forEach { (pref, label) ->
                            val selected = state.themePreference == pref
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectTheme(pref) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = ledgerColors.cardBackground,
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = selected,
                                    borderColor = ledgerColors.border,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                )
                            )
                        }
                    }
                }
            }

            // Accounts Management Section
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Accounts (${state.accounts.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedButton(
                        onClick = {
                            editingAccount = null
                            showAccountSheet = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ledgerColors.border),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_add_account_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Account")
                    }
                }
            }

            items(state.accounts, key = { "acct_${it.account.id}" }) { item ->
                val accent = IconRegistry.colorFromLong(item.account.colorArgb)
                GlassCard(
                    cornerRadius = 18.dp,
                    glowColor = accent,
                    tintColor = accent,
                    shadowElevation = 8.dp,
                    contentPadding = PaddingValues(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            accent.copy(alpha = 0.26f),
                                            accent.copy(alpha = 0.10f)
                                        )
                                    )
                                )
                                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = IconRegistry.resolveIcon(item.account.iconKey),
                                contentDescription = item.account.name,
                                tint = if (ledgerColors.isDark) lerp(accent, Color.White, 0.2f) else accent
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.account.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Balance: ${CurrencyFormatter.formatMinor(item.currentBalanceMinor, state.currency)} (Start: ${CurrencyFormatter.formatMinor(item.account.initialBalanceMinor, state.currency)})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                editingAccount = item
                                showAccountSheet = true
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("edit_account_${item.account.id}")
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit account")
                        }
                        if (state.accounts.size > 1) {
                            IconButton(
                                onClick = { accountPendingDelete = item.account },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("delete_account_${item.account.id}")
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete account",
                                    tint = ledgerColors.expense
                                )
                            }
                        }
                    }
                }
            }

            // Categories Management Section
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Categories", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(
                        onClick = {
                            editingCategory = null
                            showCategorySheet = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ledgerColors.border),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_add_category_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Category")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = categoryTab == TransactionType.EXPENSE,
                        onClick = { categoryTab = TransactionType.EXPENSE },
                        label = { Text("Expense Categories") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = ledgerColors.cardBackground,
                            selectedContainerColor = ledgerColors.expense.copy(alpha = 0.22f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = categoryTab == TransactionType.EXPENSE,
                            borderColor = ledgerColors.border,
                            selectedBorderColor = ledgerColors.expense.copy(alpha = 0.7f)
                        )
                    )
                    FilterChip(
                        selected = categoryTab == TransactionType.INCOME,
                        onClick = { categoryTab = TransactionType.INCOME },
                        label = { Text("Income Categories") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = ledgerColors.cardBackground,
                            selectedContainerColor = ledgerColors.income.copy(alpha = 0.22f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = categoryTab == TransactionType.INCOME,
                            borderColor = ledgerColors.border,
                            selectedBorderColor = ledgerColors.income.copy(alpha = 0.7f)
                        )
                    )
                }
            }

            items(filteredCategories, key = { "cat_${it.id}" }) { cat ->
                val catColor = IconRegistry.colorFromLong(cat.colorArgb)
                GlassCard(
                    cornerRadius = 16.dp,
                    glowColor = if (cat.archived) null else catColor.copy(alpha = 0.6f),
                    shadowElevation = 6.dp,
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(catColor.copy(alpha = if (cat.archived) 0.08f else 0.18f))
                                .border(
                                    1.dp,
                                    if (cat.archived) ledgerColors.border else catColor.copy(alpha = 0.35f),
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = IconRegistry.resolveIcon(cat.iconKey),
                                contentDescription = cat.name,
                                tint = if (cat.archived) ledgerColors.mutedText else catColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (cat.archived) ledgerColors.mutedText else MaterialTheme.colorScheme.onSurface
                            )
                            if (cat.archived) {
                                Text(
                                    text = "Archived (hidden from quick entry)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ledgerColors.mutedText
                                )
                            } else if (cat.primaryCategory) {
                                Text(
                                    text = "Primary default",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                editingCategory = cat
                                showCategorySheet = true
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit category")
                        }
                        IconButton(
                            onClick = { onToggleArchiveCategory(cat) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (cat.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                                contentDescription = if (cat.archived) "Restore category" else "Archive category"
                            )
                        }
                        IconButton(
                            onClick = { onDeleteCategory(cat) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete category",
                                tint = ledgerColors.expense
                            )
                        }
                    }
                }
            }

            // Google Drive JSON Sync Card (or Demo Mode notice)
            item {
                val syncStatus = state.syncStatus
                val syncStatusSubtitle = when {
                    state.isDemoMode ->
                        "You are exploring KinLedger without Google Sign-In. Changes remain local for this session. Sign in with Google anytime to sync to Drive."
                    syncStatus is DriveSyncStatus.Syncing ->
                        "Syncing JSON ledger with Google Drive (KinLedger folder)..."
                    syncStatus is DriveSyncStatus.Synced -> {
                        val fileName = state.workspace?.driveFileName?.ifBlank { "kinledger.json" } ?: "kinledger.json"
                        "Saved as JSON in Google Drive • KinLedger/$fileName (${DateFormatters.formatDate(syncStatus.lastSyncedMillis)})"
                    }
                    syncStatus is DriveSyncStatus.PendingLocal ->
                        syncStatus.message
                    syncStatus is DriveSyncStatus.NeedsAuthorization ->
                        "Google Drive permission needed to save and sync your ledger JSON file."
                    syncStatus is DriveSyncStatus.Error ->
                        "Drive sync paused: ${syncStatus.message}"
                    else ->
                        "Changes are saved offline locally and synced as JSON to your Google Drive KinLedger folder."
                }

                GlassCard(
                    cornerRadius = 18.dp,
                    glowColor = if (state.isDemoMode) ledgerColors.debt else MaterialTheme.colorScheme.primary,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDone,
                            contentDescription = null,
                            tint = if (state.isDemoMode || syncStatus is DriveSyncStatus.NeedsAuthorization) ledgerColors.debt else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.isDemoMode) "Local Demo Preview" else "Google Drive JSON Sync",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = syncStatusSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (state.isDemoMode) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onSignOut,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("demo_exit_to_google_button")
                        ) {
                            Text("Exit Demo & Sign in with Google")
                        }
                    } else {
                        val driveFileId = state.workspace?.driveFileId.orEmpty()
                        if (driveFileId.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Drive File ID: $driveFileId",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (syncStatus is DriveSyncStatus.NeedsAuthorization && syncStatus.pendingIntent != null) {
                            Button(
                                onClick = { onAuthorizeDrive(syncStatus.pendingIntent) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("authorize_drive_button")
                            ) {
                                Text("Authorize Google Drive Sync")
                            }
                        } else {
                            OutlinedButton(
                                onClick = onSyncWithDrive,
                                enabled = syncStatus !is DriveSyncStatus.Syncing,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ledgerColors.border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minimumInteractiveComponentSize()
                                    .testTag("sync_drive_now_button")
                            ) {
                                Text(if (syncStatus is DriveSyncStatus.Syncing) "Syncing..." else "Sync Now with Google Drive")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAccountSheet) {
        AccountEditorSheet(
            editing = editingAccount?.account,
            currency = state.currency,
            onDismiss = { showAccountSheet = false },
            onSave = { id, name, initBal, iconKey, colorArgb ->
                onSaveAccount(id, name, initBal, iconKey, colorArgb)
                showAccountSheet = false
            }
        )
    }

    if (showCategorySheet) {
        CategoryEditorSheet(
            editing = editingCategory,
            defaultType = categoryTab,
            onDismiss = { showCategorySheet = false },
            onSave = { id, name, type, iconKey, colorArgb ->
                onSaveCategory(id, name, type, iconKey, colorArgb)
                showCategorySheet = false
            }
        )
    }

    accountPendingDelete?.let { acct ->
        AlertDialog(
            onDismissRequest = { accountPendingDelete = null },
            containerColor = ledgerColors.glassSheetBackground,
            title = { Text("Delete ${acct.name}?") },
            text = {
                Text("Remove this account from your family ledger? Existing transaction history remains preserved.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAccount(acct)
                        accountPendingDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { accountPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditorSheet(
    editing: AccountModel?,
    currency: CurrencyOption,
    onDismiss: () -> Unit,
    onSave: (id: String, name: String, initialBalanceMinor: Long, iconKey: String, colorArgb: Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ledgerColors = LocalLedgerColors.current
    var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
    var initBalanceInput by remember(editing) {
        mutableStateOf(
            editing?.let { CurrencyFormatter.minorToEditableString(it.initialBalanceMinor) } ?: "0"
        )
    }
    var iconKey by remember(editing) { mutableStateOf(editing?.iconKey ?: "wallet") }
    var colorArgb by remember(editing) { mutableLongStateOf(editing?.colorArgb ?: IconRegistry.colorPalette.first()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val accent = IconRegistry.colorFromLong(colorArgb)

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
                        text = if (editing == null) "New Account" else "Edit Account",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text("Account Name") },
                    placeholder = { Text("e.g. Dad's Savings, UPI Wallet") },
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
                        .testTag("account_name_input")
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = initBalanceInput,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1) {
                            initBalanceInput = filtered
                            errorText = null
                        }
                    },
                    label = { Text("Starting Balance") },
                    prefix = { Text(currency.symbol, fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ledgerColors.cardBackground,
                        unfocusedContainerColor = ledgerColors.cardBackground,
                        unfocusedBorderColor = ledgerColors.border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_initial_balance_input")
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                IconPickerGrid(
                    choices = IconRegistry.accountIcons,
                    selectedKey = iconKey,
                    accentColor = accent,
                    onSelect = { iconKey = it }
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Color Tag", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerRow(selectedColorArgb = colorArgb, onSelect = { colorArgb = it })

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorText!!, style = MaterialTheme.typography.bodySmall, color = ledgerColors.expense)
                }

                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = {
                        val initMinor = CurrencyFormatter.parseMajorToMinor(initBalanceInput.ifBlank { "0" })
                        when {
                            name.isBlank() -> errorText = "Enter an account name."
                            initMinor == null -> errorText = "Enter a valid starting balance."
                            else -> onSave(editing?.id.orEmpty(), name, initMinor, iconKey, colorArgb)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_account_button")
                ) {
                    Text(if (editing == null) "Create Account" else "Save Changes")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryEditorSheet(
    editing: CategoryModel?,
    defaultType: TransactionType,
    onDismiss: () -> Unit,
    onSave: (id: String, name: String, type: TransactionType, iconKey: String, colorArgb: Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ledgerColors = LocalLedgerColors.current
    var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
    var type by remember(editing, defaultType) { mutableStateOf(editing?.transactionType ?: defaultType) }
    var iconKey by remember(editing) { mutableStateOf(editing?.iconKey ?: "shopping_cart") }
    var colorArgb by remember(editing) { mutableLongStateOf(editing?.colorArgb ?: IconRegistry.colorPalette.first()) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val accent = IconRegistry.colorFromLong(colorArgb)

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
                        text = if (editing == null) "New Category" else "Edit Category",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (editing == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = type == TransactionType.EXPENSE,
                            onClick = { type = TransactionType.EXPENSE },
                            label = { Text("Expense") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = ledgerColors.cardBackground,
                                selectedContainerColor = ledgerColors.expense.copy(alpha = 0.22f)
                            )
                        )
                        FilterChip(
                            selected = type == TransactionType.INCOME,
                            onClick = { type = TransactionType.INCOME },
                            label = { Text("Income") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = ledgerColors.cardBackground,
                                selectedContainerColor = ledgerColors.income.copy(alpha = 0.22f)
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorText = null },
                    label = { Text("Category Name") },
                    placeholder = { Text("e.g. School Fees, Pet Care") },
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
                        .testTag("category_name_input")
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                IconPickerGrid(
                    choices = IconRegistry.categoryIcons,
                    selectedKey = iconKey,
                    accentColor = accent,
                    onSelect = { iconKey = it }
                )

                Spacer(modifier = Modifier.height(14.dp))
                Text("Color Tag", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                ColorPickerRow(selectedColorArgb = colorArgb, onSelect = { colorArgb = it })

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(errorText!!, style = MaterialTheme.typography.bodySmall, color = ledgerColors.expense)
                }

                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            errorText = "Enter a category name."
                        } else {
                            onSave(editing?.id.orEmpty(), name, type, iconKey, colorArgb)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_category_button")
                ) {
                    Text(if (editing == null) "Create Category" else "Save Category")
                }
            }
        }
    }
}
