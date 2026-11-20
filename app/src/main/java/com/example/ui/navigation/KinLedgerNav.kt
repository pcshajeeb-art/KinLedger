package com.example.ui.navigation

import android.app.PendingIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.TransactionDetail
import com.example.ui.KinLedgerViewModel
import com.example.ui.UiEvent
import com.example.ui.WorkspaceStatus
import com.example.ui.components.BrandWatermarkOverlay
import com.example.ui.components.GlassAmbientBackdrop
import com.example.ui.components.GlassCard
import com.example.ui.screens.AccountEditorSheet
import com.example.ui.screens.AddEditTransactionSheet
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.DebtsScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WorkspaceSetupScreen
import com.example.ui.theme.KinLedgerTheme
import com.example.ui.theme.LocalLedgerColors

object NavRoutes {
    const val ROUTE_DASHBOARD = "dashboard"
    const val ROUTE_DEBTS = "debts"
    const val ROUTE_REPORTS = "reports"
    const val ROUTE_SETTINGS = "settings"
}

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TopLevelDestinations = listOf(
    NavDestination(
        route = NavRoutes.ROUTE_DASHBOARD,
        label = "Home",
        selectedIcon = Icons.Rounded.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    NavDestination(
        route = NavRoutes.ROUTE_DEBTS,
        label = "Lend/Borrow",
        selectedIcon = Icons.Rounded.SwapHoriz,
        unselectedIcon = Icons.Outlined.SwapHoriz
    ),
    NavDestination(
        route = NavRoutes.ROUTE_REPORTS,
        label = "Reports",
        selectedIcon = Icons.Rounded.Assessment,
        unselectedIcon = Icons.Outlined.Assessment
    ),
    NavDestination(
        route = NavRoutes.ROUTE_SETTINGS,
        label = "Settings",
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)

@Composable
fun KinLedgerRoot(viewModel: KinLedgerViewModel) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.handleDriveConsentResult(context, result.data)
    }

    val launchDriveConsent: (PendingIntent) -> Unit = remember(driveAuthLauncher) {
        { pendingIntent ->
            driveAuthLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    KinLedgerTheme(themePreference = state.themePreference) {
        val ledgerColors = LocalLedgerColors.current

        when (val status = state.workspaceStatus) {
            is WorkspaceStatus.Loading -> {
                GlassAmbientBackdrop(showWatermark = true, watermarkBottomPadding = 40.dp) {
                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets.safeDrawing
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            contentAlignment = Alignment.Center
                        ) {
                            GlassCard(
                                glowColor = MaterialTheme.colorScheme.primary,
                                contentPadding = PaddingValues(28.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Syncing your family ledger...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            is WorkspaceStatus.NeedsWorkspaceSetup -> {
                WorkspaceSetupScreen(
                    userDisplayName = state.userProfile?.displayName?.ifBlank { "there" } ?: "there",
                    isBusy = state.isWorkspaceBusy,
                    errorMessage = state.workspaceError,
                    availableWorkspaces = state.availableDriveWorkspaces,
                    syncStatus = state.syncStatus,
                    onCreateWorkspace = viewModel::createFamilyWorkspace,
                    onJoinWorkspace = viewModel::joinFamilyWorkspace,
                    onOpenExistingWorkspace = viewModel::openExistingWorkspace,
                    onAuthorizeDrive = launchDriveConsent,
                    onRefreshDrive = { viewModel.syncWithDrive(forcePull = true) },
                    onSignOut = { viewModel.signOut(context) }
                )
            }

            is WorkspaceStatus.Error -> {
                GlassAmbientBackdrop(showWatermark = true, watermarkBottomPadding = 40.dp) {
                    Scaffold(
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets.safeDrawing
                    ) { padding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            GlassCard(
                                modifier = Modifier.widthIn(max = 460.dp),
                                glowColor = ledgerColors.expense,
                                contentPadding = PaddingValues(20.dp)
                            ) {
                                Text(
                                    text = "Unable to sync family ledger",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = status.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Start
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = viewModel::switchOrLeaveWorkspace,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Switch Ledger")
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.signOut(context) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Sign Out")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            is WorkspaceStatus.Active -> {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route ?: NavRoutes.ROUTE_DASHBOARD

                var showTransactionSheet by remember { mutableStateOf(false) }
                var editingTransaction by remember { mutableStateOf<TransactionDetail?>(null) }
                var showQuickAddAccountSheet by remember { mutableStateOf(false) }

                fun navigateToTab(route: String) {
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useNavRail = maxWidth >= 680.dp

                    GlassAmbientBackdrop(showWatermark = false) {
                        Scaffold(
                            containerColor = Color.Transparent,
                            contentWindowInsets = WindowInsets.safeDrawing,
                            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                            floatingActionButton = {
                                if (currentRoute == NavRoutes.ROUTE_DASHBOARD) {
                                    ExtendedFloatingActionButton(
                                        onClick = {
                                            editingTransaction = null
                                            showTransactionSheet = true
                                        },
                                        icon = { Icon(Icons.Rounded.Add, contentDescription = "Add entry") },
                                        text = { Text("Add Entry") },
                                        shape = RoundedCornerShape(18.dp),
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        elevation = FloatingActionButtonDefaults.elevation(
                                            defaultElevation = 8.dp,
                                            pressedElevation = 12.dp
                                        ),
                                        modifier = Modifier
                                            .border(
                                                width = 1.dp,
                                                brush = Brush.linearGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.55f),
                                                        Color.White.copy(alpha = 0.12f)
                                                    )
                                                ),
                                                shape = RoundedCornerShape(18.dp)
                                            )
                                            .testTag("fab_quick_add_transaction")
                                    )
                                }
                            },
                            bottomBar = {
                                if (!useNavRail) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .drawBehind {
                                                drawRect(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            ledgerColors.glassNavBackgroundTop,
                                                            ledgerColors.glassNavBackgroundBottom
                                                        )
                                                    )
                                                )
                                                drawLine(
                                                    brush = Brush.horizontalGradient(
                                                        colors = listOf(
                                                            ledgerColors.glassRimBottomRight,
                                                            ledgerColors.glassRimTopLeft,
                                                            ledgerColors.glassRimBottomRight
                                                        )
                                                    ),
                                                    start = Offset(0f, 0.5.dp.toPx()),
                                                    end = Offset(size.width, 0.5.dp.toPx()),
                                                    strokeWidth = 1.dp.toPx()
                                                )
                                            }
                                    ) {
                                        NavigationBar(
                                            containerColor = Color.Transparent,
                                            tonalElevation = 0.dp
                                        ) {
                                            TopLevelDestinations.forEach { dest ->
                                                val selected = currentRoute == dest.route
                                                NavigationBarItem(
                                                    selected = selected,
                                                    onClick = { navigateToTab(dest.route) },
                                                    icon = {
                                                        Icon(
                                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                            contentDescription = dest.label
                                                        )
                                                    },
                                                    label = { Text(dest.label, maxLines = 1) },
                                                    colors = NavigationBarItemDefaults.colors(
                                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        indicatorColor = MaterialTheme.colorScheme.primary.copy(
                                                            alpha = if (ledgerColors.isDark) 0.22f else 0.16f
                                                        )
                                                    ),
                                                    modifier = Modifier.testTag("nav_tab_${dest.route}")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                BrandWatermarkOverlay(
                                    alphaValue = 0.085f,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 20.dp, bottom = 84.dp)
                                )

                                Row(modifier = Modifier.fillMaxSize()) {
                                    if (useNavRail) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .drawBehind {
                                                    drawRect(
                                                        brush = Brush.horizontalGradient(
                                                            colors = listOf(
                                                                ledgerColors.glassNavBackgroundBottom,
                                                                ledgerColors.glassNavBackgroundTop
                                                            )
                                                        )
                                                    )
                                                    drawLine(
                                                        brush = Brush.verticalGradient(
                                                            colors = listOf(
                                                                ledgerColors.glassRimTopLeft,
                                                                ledgerColors.glassRimBottomRight
                                                            )
                                                        ),
                                                        start = Offset(size.width - 0.5.dp.toPx(), 0f),
                                                        end = Offset(size.width - 0.5.dp.toPx(), size.height),
                                                        strokeWidth = 1.dp.toPx()
                                                    )
                                                }
                                        ) {
                                            NavigationRail(
                                                containerColor = Color.Transparent,
                                                header = {
                                                    FloatingActionButton(
                                                        onClick = {
                                                            editingTransaction = null
                                                            showTransactionSheet = true
                                                        },
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                                        shape = RoundedCornerShape(16.dp),
                                                        modifier = Modifier
                                                            .padding(top = 8.dp)
                                                            .border(
                                                                1.dp,
                                                                Color.White.copy(alpha = 0.45f),
                                                                RoundedCornerShape(16.dp)
                                                            )
                                                    ) {
                                                        Icon(Icons.Rounded.Add, contentDescription = "Add entry")
                                                    }
                                                }
                                            ) {
                                                Spacer(modifier = Modifier.weight(0.1f))
                                                TopLevelDestinations.forEach { dest ->
                                                    val selected = currentRoute == dest.route
                                                    NavigationRailItem(
                                                        selected = selected,
                                                        onClick = { navigateToTab(dest.route) },
                                                        icon = {
                                                            Icon(
                                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                                contentDescription = dest.label
                                                            )
                                                        },
                                                        label = { Text(dest.label) },
                                                        colors = NavigationRailItemDefaults.colors(
                                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            indicatorColor = MaterialTheme.colorScheme.primary.copy(
                                                                alpha = if (ledgerColors.isDark) 0.22f else 0.16f
                                                            )
                                                        ),
                                                        modifier = Modifier.testTag("nav_rail_${dest.route}")
                                                    )
                                                }
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }

                                    NavHost(
                                        navController = navController,
                                        startDestination = NavRoutes.ROUTE_DASHBOARD,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        composable(NavRoutes.ROUTE_DASHBOARD) {
                                            DashboardScreen(
                                                state = state,
                                                onAddTransactionClick = {
                                                    editingTransaction = null
                                                    showTransactionSheet = true
                                                },
                                                onEditTransactionClick = { detail ->
                                                    editingTransaction = detail
                                                    showTransactionSheet = true
                                                },
                                                onAccountClick = { /* Account filter handled in Dashboard */ },
                                                onAddAccountClick = { showQuickAddAccountSheet = true },
                                                onNavigateToDebts = { navigateToTab(NavRoutes.ROUTE_DEBTS) },
                                                onNavigateToReports = { navigateToTab(NavRoutes.ROUTE_REPORTS) }
                                            )
                                        }

                                        composable(NavRoutes.ROUTE_DEBTS) {
                                            DebtsScreen(
                                                state = state,
                                                onSaveDebt = viewModel::saveDebt,
                                                onSettleDebt = viewModel::settleDebt,
                                                onReopenDebt = viewModel::reopenDebt,
                                                onDeleteDebt = viewModel::deleteDebt
                                            )
                                        }

                                        composable(NavRoutes.ROUTE_REPORTS) {
                                            ReportsScreen(
                                                state = state,
                                                onPreviousMonth = viewModel::previousReportMonth,
                                                onNextMonth = viewModel::nextReportMonth,
                                                onSelectPeriod = viewModel::setReportPeriod,
                                                onExportPdf = viewModel::exportAndSharePdf,
                                                onExportCsv = viewModel::exportAndShareCsv
                                            )
                                        }

                                        composable(NavRoutes.ROUTE_SETTINGS) {
                                            SettingsScreen(
                                                state = state,
                                                onUpdateLedgerName = viewModel::setLedgerName,
                                                onSelectCurrency = viewModel::setCurrency,
                                                onSelectTheme = viewModel::setThemePreference,
                                                onSaveAccount = viewModel::saveAccount,
                                                onDeleteAccount = viewModel::deleteAccount,
                                                onSaveCategory = viewModel::saveCategory,
                                                onToggleArchiveCategory = viewModel::toggleArchiveCategory,
                                                onDeleteCategory = viewModel::deleteCategory,
                                                onSwitchWorkspace = viewModel::switchOrLeaveWorkspace,
                                                onSyncWithDrive = { viewModel.syncWithDrive(forcePull = true) },
                                                onAuthorizeDrive = launchDriveConsent,
                                                onSignOut = { viewModel.signOut(context) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showTransactionSheet) {
                        AddEditTransactionSheet(
                            editing = editingTransaction,
                            accounts = state.accounts,
                            categories = state.categories,
                            currency = state.currency,
                            onDismiss = { showTransactionSheet = false },
                            onSave = { id, previousTx, type, amountMinor, accountId, destinationAccountId, categoryId, dateMillis, note ->
                                viewModel.saveTransaction(
                                    id = id,
                                    previousTransaction = previousTx,
                                    type = type,
                                    amountMinor = amountMinor,
                                    accountId = accountId,
                                    destinationAccountId = destinationAccountId,
                                    categoryId = categoryId,
                                    dateMillis = dateMillis,
                                    note = note
                                )
                                showTransactionSheet = false
                            },
                            onDelete = { tx ->
                                viewModel.deleteTransaction(tx)
                                showTransactionSheet = false
                            }
                        )
                    }

                    if (showQuickAddAccountSheet) {
                        AccountEditorSheet(
                            editing = null,
                            currency = state.currency,
                            onDismiss = { showQuickAddAccountSheet = false },
                            onSave = { id, name, initBal, iconKey, colorArgb ->
                                viewModel.saveAccount(id, name, initBal, iconKey, colorArgb)
                                showQuickAddAccountSheet = false
                            }
                        )
                    }
                }
            }
        }
    }
}
