package com.example.ui.screens

import android.app.PendingIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AddBusiness
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.data.model.CurrencyOption
import com.example.data.model.DriveSyncStatus
import com.example.data.model.DriveWorkspaceRef
import com.example.ui.components.GlassAmbientBackdrop
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassContainer
import com.example.ui.theme.LocalLedgerColors

private enum class WorkspaceMode {
    CREATE, JOIN
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkspaceSetupScreen(
    userDisplayName: String,
    isBusy: Boolean,
    errorMessage: String?,
    availableWorkspaces: List<DriveWorkspaceRef> = emptyList(),
    syncStatus: DriveSyncStatus = DriveSyncStatus.Idle,
    onCreateWorkspace: (name: String, currencyCode: String) -> Unit,
    onJoinWorkspace: (inviteCode: String) -> Unit,
    onOpenExistingWorkspace: (workspaceId: String) -> Unit = {},
    onAuthorizeDrive: (PendingIntent) -> Unit = {},
    onRefreshDrive: () -> Unit = {},
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ledgerColors = LocalLedgerColors.current
    var mode by remember { mutableStateOf(WorkspaceMode.CREATE) }
    var ledgerName by remember { mutableStateOf("Family Ledger") }
    var currencyCode by remember { mutableStateOf("INR") }
    var inviteCode by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    GlassAmbientBackdrop(
        modifier = modifier,
        showWatermark = true,
        watermarkBottomPadding = 28.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Welcome, $userDisplayName",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Create a family ledger or open one from Google Drive",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("workspace_setup_sign_out")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Sign out",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                if (syncStatus is DriveSyncStatus.NeedsAuthorization) {
                    Spacer(modifier = Modifier.height(14.dp))
                    GlassCard(
                        cornerRadius = 18.dp,
                        glowColor = ledgerColors.debt,
                        contentPadding = PaddingValues(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = null,
                                tint = ledgerColors.debt
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Connect Google Drive", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Grant Drive access to save and restore your ledger JSON files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            syncStatus.pendingIntent?.let { pending ->
                                Button(
                                    onClick = { onAuthorizeDrive(pending) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Allow")
                                }
                            }
                        }
                    }
                }

                if (availableWorkspaces.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    GlassCard(
                        cornerRadius = 20.dp,
                        glowColor = ledgerColors.income,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = ledgerColors.income)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Saved in Google Drive", style = MaterialTheme.typography.titleSmall)
                            }
                            IconButton(
                                onClick = onRefreshDrive,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Rounded.Sync, contentDescription = "Refresh Drive ledgers")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        availableWorkspaces.forEach { ref ->
                            Surface(
                                onClick = { onOpenExistingWorkspace(ref.workspaceId) },
                                shape = RoundedCornerShape(12.dp),
                                color = ledgerColors.cardBackground,
                                border = BorderStroke(1.dp, ledgerColors.border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(ref.name, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "Currency: ${ref.currencyCode} • Code: ${ref.inviteCode}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "Open",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Frosted Mode Toggle: Create vs Join
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
                            WorkspaceMode.CREATE to "Create Ledger",
                            WorkspaceMode.JOIN to "Join / Open File"
                        ).forEach { (tab, label) ->
                            val selected = mode == tab
                            Surface(
                                onClick = {
                                    mode = tab
                                    validationError = null
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("workspace_mode_${tab.name.lowercase()}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                GlassCard(
                    cornerRadius = 22.dp,
                    glowColor = MaterialTheme.colorScheme.primary,
                    shadowElevation = 14.dp,
                    contentPadding = PaddingValues(18.dp)
                ) {
                    if (mode == WorkspaceMode.CREATE) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AddBusiness, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create a New Family Ledger", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Creates a JSON ledger in your Google Drive KinLedger folder with Cash & Bank accounts and 8 household categories.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = ledgerName,
                            onValueChange = { ledgerName = it; validationError = null },
                            label = { Text("Family Ledger Name") },
                            placeholder = { Text("e.g. Home Household Ledger") },
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
                                .testTag("create_ledger_name_input")
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Primary Currency", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CurrencyOption.ALL.forEach { option ->
                                val selected = currencyCode == option.code
                                FilterChip(
                                    selected = selected,
                                    onClick = { currencyCode = option.code },
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
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = {
                                if (ledgerName.isBlank()) {
                                    validationError = "Please enter a ledger name."
                                } else {
                                    onCreateWorkspace(ledgerName, currencyCode)
                                }
                            },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("create_workspace_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Create Family Ledger", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Join a Shared Drive Ledger", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enter a 6-character invite code or paste a shared Google Drive ledger File ID / link from Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = inviteCode,
                            onValueChange = { raw ->
                                inviteCode = raw.take(160)
                                validationError = null
                            },
                            label = { Text("Invite Code or Drive File Link / ID") },
                            placeholder = { Text("e.g. K7M9P2 or Drive File ID") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ledgerColors.cardBackground,
                                unfocusedContainerColor = ledgerColors.cardBackground,
                                unfocusedBorderColor = ledgerColors.border
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("join_invite_code_input")
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = {
                                if (inviteCode.trim().length < 6) {
                                    validationError = "Enter a 6-character code or Drive File ID."
                                } else {
                                    onJoinWorkspace(inviteCode.trim())
                                }
                            },
                            enabled = !isBusy,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("join_workspace_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Open / Join Family Ledger", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    val shownError = validationError ?: errorMessage
                    if (shownError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ledgerColors.expenseContainer.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, ledgerColors.expense.copy(alpha = 0.45f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = shownError,
                                style = MaterialTheme.typography.bodySmall,
                                color = ledgerColors.expense,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
