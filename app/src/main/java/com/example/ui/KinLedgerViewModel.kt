package com.example.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.KinLedgerApp
import com.example.auth.AppSession
import com.example.auth.AuthService
import com.example.auth.DriveTokenResult
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.AccountModel
import com.example.data.model.AccountWithBalance
import com.example.data.model.CategoryModel
import com.example.data.model.CurrencyOption
import com.example.data.model.DateFormatters
import com.example.data.model.DebtDetail
import com.example.data.model.DebtRecordModel
import com.example.data.model.DebtType
import com.example.data.model.DriveSyncStatus
import com.example.data.model.DriveWorkspaceRef
import com.example.data.model.FamilyWorkspaceModel
import com.example.data.model.LedgerUserInfo
import com.example.data.model.MonthlyReportSummary
import com.example.data.model.TransactionDetail
import com.example.data.model.TransactionModel
import com.example.data.model.TransactionType
import com.example.data.model.UserProfileModel
import com.example.data.repository.BalanceCalculator
import com.example.data.repository.LedgerRepository
import com.example.export.StatementExporter
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class ReportPeriod(
    val year: Int,
    val monthZeroBased: Int
) {
    companion object {
        fun current(): ReportPeriod {
            val cal = Calendar.getInstance()
            return ReportPeriod(
                year = cal.get(Calendar.YEAR),
                monthZeroBased = cal.get(Calendar.MONTH)
            )
        }
    }

    fun previousMonth(): ReportPeriod {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -1)
        }
        return ReportPeriod(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }

    fun nextMonth(): ReportPeriod {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, 1)
        }
        return ReportPeriod(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
    }
}

sealed interface WorkspaceStatus {
    data object Loading : WorkspaceStatus
    data object NeedsWorkspaceSetup : WorkspaceStatus
    data class Active(val workspace: FamilyWorkspaceModel) : WorkspaceStatus
    data class Error(val message: String) : WorkspaceStatus
}

data class KinLedgerUiState(
    val workspaceStatus: WorkspaceStatus = WorkspaceStatus.Loading,
    val userProfile: UserProfileModel? = null,
    val workspace: FamilyWorkspaceModel? = null,
    val ledgerName: String = "Family Ledger",
    val currency: CurrencyOption = CurrencyOption.ALL.first(),
    val themePreference: ThemePreference = ThemePreference.DARK,
    val accounts: List<AccountWithBalance> = emptyList(),
    val totalBalanceMinor: Long = 0L,
    val categories: List<CategoryModel> = emptyList(),
    val transactions: List<TransactionDetail> = emptyList(),
    val currentMonthIncomeMinor: Long = 0L,
    val currentMonthExpenseMinor: Long = 0L,
    val debts: List<DebtDetail> = emptyList(),
    val totalPendingReceivablesMinor: Long = 0L,
    val totalPendingPayablesMinor: Long = 0L,
    val reportPeriod: ReportPeriod = ReportPeriod.current(),
    val monthlyReport: MonthlyReportSummary? = null,
    val syncStatus: DriveSyncStatus = DriveSyncStatus.Idle,
    val availableDriveWorkspaces: List<DriveWorkspaceRef> = emptyList(),
    val isDemoMode: Boolean = false,
    val isExporting: Boolean = false,
    val isWorkspaceBusy: Boolean = false,
    val workspaceError: String? = null
)

sealed interface UiEvent {
    data class ShowMessage(val text: String) : UiEvent
}

private data class WorkspaceBundle(
    val workspace: FamilyWorkspaceModel?,
    val accounts: List<AccountWithBalance>,
    val categories: List<CategoryModel>,
    val transactions: List<TransactionDetail>,
    val debts: List<DebtDetail>
)

private sealed interface StreamResult<out T> {
    data object Loading : StreamResult<Nothing>
    data class Data<T>(val value: T) : StreamResult<T>
    data class Error(val message: String) : StreamResult<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class KinLedgerViewModel(
    private val currentUserId: String,
    private val currentUserInfo: LedgerUserInfo,
    private val isDemoMode: Boolean,
    private val repository: LedgerRepository,
    private val authService: AuthService,
    private val preferences: UserPreferencesRepository,
    private val exporter: StatementExporter
) : ViewModel() {

    private val reportPeriodFlow = MutableStateFlow(ReportPeriod.current())
    private val isExportingFlow = MutableStateFlow(false)
    private val isWorkspaceBusyFlow = MutableStateFlow(false)
    private val workspaceErrorFlow = MutableStateFlow<String?>(null)
    private val profileSeededFlow = MutableStateFlow(false)
    private var lastReconciledMemberKey: String? = null

    private val _events = MutableSharedFlow<UiEvent>()
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUserInfo)
            profileSeededFlow.value = true
        }
    }

    private fun <T> Flow<T>.toStreamResult(label: String): Flow<StreamResult<T>> =
        combine(flowOf(Unit)) { item, _ -> StreamResult.Data(item) as StreamResult<T> }
            .catch { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Stream error ($label)", error)
                emit(StreamResult.Error(error.localizedMessage ?: "Failed to load $label"))
            }

    private val userProfileStream: Flow<StreamResult<UserProfileModel?>> =
        repository.observeUserProfile(currentUserId)
            .toStreamResult("profile")

    private val workspaceBundleStream: Flow<StreamResult<WorkspaceBundle?>> =
        userProfileStream.flatMapLatest { profileResult ->
            when (profileResult) {
                is StreamResult.Loading -> flowOf(StreamResult.Loading)
                is StreamResult.Error -> flowOf(StreamResult.Error(profileResult.message))
                is StreamResult.Data -> {
                    val wsId = profileResult.value?.activeWorkspaceId
                    if (wsId.isNullOrBlank()) {
                        flowOf(StreamResult.Data(null))
                    } else {
                        combine(
                            repository.observeWorkspace(wsId),
                            repository.observeAccountsWithBalance(wsId, currentUserId),
                            repository.observeCategories(wsId, currentUserId),
                            repository.observeTransactions(wsId, currentUserId),
                            repository.observeDebtRecords(wsId, currentUserId)
                        ) { ws, accts, cats, txs, debts ->
                            if (ws != null) {
                                val memberKey = "${ws.id}:${ws.memberUids.sorted().joinToString(",")}"
                                if (memberKey != lastReconciledMemberKey && ws.memberUids.size > 1) {
                                    lastReconciledMemberKey = memberKey
                                    viewModelScope.launch {
                                        repository.reconcileWorkspaceMembership(ws)
                                    }
                                }
                            }
                            StreamResult.Data(
                                WorkspaceBundle(ws, accts, cats, txs, debts)
                            ) as StreamResult<WorkspaceBundle?>
                        }.catch { error ->
                            if (error is CancellationException) throw error
                            Log.w(TAG, "Workspace stream error", error)
                            emit(StreamResult.Error(error.localizedMessage ?: "Sync error"))
                        }
                    }
                }
            }
        }

    private data class UiMetaFlags(
        val themePreference: ThemePreference,
        val period: ReportPeriod,
        val isExporting: Boolean,
        val isWorkspaceBusy: Boolean,
        val workspaceError: String?,
        val syncStatus: DriveSyncStatus,
        val availableWorkspaces: List<DriveWorkspaceRef>
    )

    private val metaFlagsFlow = combine(
        preferences.themePreferenceFlow,
        reportPeriodFlow,
        isExportingFlow,
        isWorkspaceBusyFlow,
        workspaceErrorFlow,
        repository.observeSyncStatus(),
        repository.observeAvailableWorkspaces()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        UiMetaFlags(
            themePreference = values[0] as ThemePreference,
            period = values[1] as ReportPeriod,
            isExporting = values[2] as Boolean,
            isWorkspaceBusy = values[3] as Boolean,
            workspaceError = values[4] as String?,
            syncStatus = values[5] as DriveSyncStatus,
            availableWorkspaces = values[6] as List<DriveWorkspaceRef>
        )
    }

    val uiState: StateFlow<KinLedgerUiState> = combine(
        profileSeededFlow,
        userProfileStream,
        workspaceBundleStream,
        metaFlagsFlow
    ) { seeded, profileResult, bundleResult, meta ->
        val profile = (profileResult as? StreamResult.Data)?.value

        val status = when {
            !seeded || profileResult is StreamResult.Loading || bundleResult is StreamResult.Loading -> {
                WorkspaceStatus.Loading
            }
            profileResult is StreamResult.Error -> WorkspaceStatus.Error(profileResult.message)
            bundleResult is StreamResult.Error -> WorkspaceStatus.Error(bundleResult.message)
            bundleResult is StreamResult.Data && bundleResult.value?.workspace != null -> {
                WorkspaceStatus.Active(bundleResult.value.workspace)
            }
            else -> WorkspaceStatus.NeedsWorkspaceSetup
        }

        val bundle = (bundleResult as? StreamResult.Data)?.value
        val workspace = bundle?.workspace
        val accounts = bundle?.accounts.orEmpty()
        val categories = bundle?.categories.orEmpty()
        val transactions = bundle?.transactions.orEmpty()
        val debts = bundle?.debts.orEmpty()

        val totalBal = accounts.sumOf { it.currentBalanceMinor }
        val nowPeriod = ReportPeriod.current()
        val (curStart, curEnd) = DateFormatters.monthBoundsMillis(nowPeriod.year, nowPeriod.monthZeroBased)
        val curMonthTxs = transactions.filter { it.transaction.dateMillis in curStart..curEnd }
        val curIncome = curMonthTxs
            .filter { it.transaction.transactionType == TransactionType.INCOME }
            .sumOf { it.transaction.amountMinor }
        val curExpense = curMonthTxs
            .filter { it.transaction.transactionType == TransactionType.EXPENSE }
            .sumOf { it.transaction.amountMinor }

        val pendingReceivables = debts
            .filter { !it.debt.settled && it.debt.debtType == DebtType.RECEIVABLE }
            .sumOf { it.debt.effectiveRemainingAmountMinor }
        val pendingPayables = debts
            .filter { !it.debt.settled && it.debt.debtType == DebtType.PAYABLE }
            .sumOf { it.debt.effectiveRemainingAmountMinor }

        val report = BalanceCalculator.computeMonthlyReport(
            year = meta.period.year,
            monthZeroBased = meta.period.monthZeroBased,
            allTransactions = transactions,
            categories = categories
        )

        val currency = CurrencyOption.fromCode(workspace?.currencyCode ?: "INR")

        KinLedgerUiState(
            workspaceStatus = status,
            userProfile = profile,
            workspace = workspace,
            ledgerName = workspace?.name ?: "Family Ledger",
            currency = currency,
            themePreference = meta.themePreference,
            accounts = accounts,
            totalBalanceMinor = totalBal,
            categories = categories,
            transactions = transactions,
            currentMonthIncomeMinor = curIncome,
            currentMonthExpenseMinor = curExpense,
            debts = debts,
            totalPendingReceivablesMinor = pendingReceivables,
            totalPendingPayablesMinor = pendingPayables,
            reportPeriod = meta.period,
            monthlyReport = report,
            syncStatus = meta.syncStatus,
            availableDriveWorkspaces = meta.availableWorkspaces,
            isDemoMode = isDemoMode,
            isExporting = meta.isExporting,
            isWorkspaceBusy = meta.isWorkspaceBusy,
            workspaceError = meta.workspaceError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = KinLedgerUiState(isDemoMode = isDemoMode)
    )

    fun syncWithDrive(forcePull: Boolean = true) {
        if (isDemoMode) return
        viewModelScope.launch {
            repository.syncWithDrive(forcePull = forcePull)
                .onSuccess {
                    _events.emit(UiEvent.ShowMessage("Synced with Google Drive"))
                }
                .onFailure { err ->
                    if (repository.observeSyncStatus().let { false }) {
                        // handled below
                    }
                    if (uiState.value.syncStatus !is DriveSyncStatus.NeedsAuthorization) {
                        _events.emit(UiEvent.ShowMessage(err.localizedMessage ?: "Google Drive sync failed"))
                    }
                }
        }
    }

    fun handleDriveConsentResult(context: Context, data: Intent?) {
        viewModelScope.launch {
            when (val res = authService.handleDriveConsentResult(context, data)) {
                is DriveTokenResult.Token -> {
                    repository.syncWithDrive(forcePull = true)
                        .onSuccess { _events.emit(UiEvent.ShowMessage("Google Drive connected & synced")) }
                        .onFailure { _events.emit(UiEvent.ShowMessage(it.localizedMessage ?: "Drive sync failed")) }
                }
                is DriveTokenResult.NeedsConsent -> {
                    _events.emit(UiEvent.ShowMessage("Additional Google Drive authorization required"))
                }
                is DriveTokenResult.Failure -> {
                    _events.emit(UiEvent.ShowMessage(res.error.localizedMessage ?: "Drive authorization cancelled"))
                }
            }
        }
    }

    fun openExistingWorkspace(workspaceId: String) {
        viewModelScope.launch {
            isWorkspaceBusyFlow.value = true
            workspaceErrorFlow.value = null
            repository.openExistingWorkspace(workspaceId)
                .onSuccess { _events.emit(UiEvent.ShowMessage("Opened family ledger")) }
                .onFailure { err -> workspaceErrorFlow.value = err.localizedMessage ?: "Unable to open ledger" }
            isWorkspaceBusyFlow.value = false
        }
    }

    fun createFamilyWorkspace(name: String, currencyCode: String) {
        viewModelScope.launch {
            isWorkspaceBusyFlow.value = true
            workspaceErrorFlow.value = null
            repository.createFamilyWorkspace(name, currencyCode)
                .onSuccess {
                    _events.emit(UiEvent.ShowMessage("Family workspace created"))
                }
                .onFailure { err ->
                    workspaceErrorFlow.value = err.localizedMessage ?: "Failed to create workspace"
                }
            isWorkspaceBusyFlow.value = false
        }
    }

    fun joinFamilyWorkspace(inviteCode: String) {
        viewModelScope.launch {
            isWorkspaceBusyFlow.value = true
            workspaceErrorFlow.value = null
            repository.joinFamilyWorkspace(inviteCode)
                .onSuccess {
                    _events.emit(UiEvent.ShowMessage("Joined family ledger"))
                }
                .onFailure { err ->
                    workspaceErrorFlow.value = err.localizedMessage ?: "Failed to join workspace"
                }
            isWorkspaceBusyFlow.value = false
        }
    }

    fun switchOrLeaveWorkspace() {
        viewModelScope.launch {
            repository.leaveWorkspace()
                .onFailure { _events.emit(UiEvent.ShowMessage(it.localizedMessage ?: "Unable to switch workspace")) }
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            authService.signOut(context)
        }
    }

    fun saveTransaction(
        id: String = "",
        previousTransaction: TransactionModel? = null,
        type: TransactionType,
        amountMinor: Long,
        accountId: String,
        destinationAccountId: String?,
        categoryId: String?,
        dateMillis: Long,
        note: String
    ) {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            repository.saveTransaction(
                workspace = ws,
                id = id,
                previousTransaction = previousTransaction,
                type = type,
                amountMinor = amountMinor,
                accountId = accountId,
                destinationAccountId = destinationAccountId,
                categoryId = categoryId,
                dateMillis = dateMillis,
                note = note
            ).onSuccess {
                val label = when (type) {
                    TransactionType.EXPENSE -> "Expense"
                    TransactionType.INCOME -> "Income"
                    TransactionType.TRANSFER -> "Transfer"
                }
                _events.emit(UiEvent.ShowMessage(if (id.isBlank()) "$label saved" else "$label updated"))
            }.onFailure {
                _events.emit(UiEvent.ShowMessage(it.localizedMessage ?: "Failed to save entry"))
            }
        }
    }

    fun deleteTransaction(transaction: TransactionModel) {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            repository.deleteTransaction(ws.id, transaction)
                .onSuccess { _events.emit(UiEvent.ShowMessage("Entry deleted")) }
                .onFailure { _events.emit(UiEvent.ShowMessage(it.localizedMessage ?: "Failed to delete entry")) }
        }
    }

    fun saveDebt(
        id: String = "",
        previousDebt: DebtRecordModel? = null,
        type: DebtType,
        personName: String,
        amountMinor: Long,
        sourceAccountId: String,
        dateMillis: Long,
        dueDateMillis: Long?,
        note: String
    ) {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            repository.saveDebtRecord(ws, id, previousDebt, type, personName, amountMinor, sourceAccountId, dateMillis, dueDateMillis, note)
                .onSuccess {
                    val label = if (type == DebtType.RECEIVABLE) "Receivable" else "Payable"
                    _events.emit(UiEvent.ShowMessage(if (id.isBlank()) "$label recorded" else "$label updated"))
                }
                .onFailure { _events.emit(UiEvent.ShowMessage(it.localizedMessage ?: "Failed to save record")) }
        }
    }

    fun settleDebt(
        debt: DebtRecordModel,
        amountMinor: Long,
        settlementAccountId: String,
        settledDateMillis: Long,
        note: String
    ) {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            repository.recordDebtSettlement(
                workspaceId = ws.id,
                debt = debt,
                amountMinor = amountMinor,
                settlementAccountId = settlementAccountId,
                settledDateMillis = settledDateMillis,
                note = note
            ).onSuccess { fullySettled ->
                _events.emit(
                    UiEvent.ShowMessage(
                        if (fullySettled) "Fully settled & account balance updated"
                        else "Partial settlement recorded & balance updated"
                    )
                )
            }.onFailure { _events.emit(UiEvent.ShowMessage(it.localizedMessage ?: "Failed to record settlement")) }
        }
    }

    fun reopenDebt(debt: DebtRecordModel) {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            repository.reopenDebtRecord(ws.id, debt)
                .onSuccess { _events.emit(UiEvent.ShowMessage("Reopened as pending")) }
                .onFailure { _events.emit(UiEvent.ShowMessage(it.localizedMessage ?: "Failed to reopen")) }
        }
    }

    fun deleteDebt(debt: DebtRecordModel) {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            repository.deleteDebtRecord(ws.id, debt)
                .onSuccess { _events.emit(UiEvent.ShowMessage("Record deleted")) }
                .onFailure { _events.emit(UiEvent.ShowMessage(it.localizedMessage ?: "Failed to delete")) }
        }
    }

    fun saveAccount(
        id: String = "",
        name: String,
        initialBalanceMinor: Long,
        iconKey: String,
        colorArgb: Long
    ) {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            repository.saveAccount(ws, id, name, initialBalanceMinor, iconKey, colorArgb)
                .onSuccess { _events.emit(UiEvent.ShowMessage(if (id.isBlank()) "Account added" else "Account updated")) }
                .onFailure { _events.emit(UiEvent.ShowMessage(it.localizedMessage ?: "Failed to save account")) }
        }
    }

    fun deleteAccount(account: AccountModel) {
        val ws = uiState.value.workspace ?: return
        val count = uiState.value.accounts.size
        viewModelScope.launch {
            repository.deleteAccount(ws.id, account, count)
                .onSuccess { _events.emit(UiEvent.ShowMessage("Account removed")) }
                .onFailure { _events.emit(UiEvent.ShowMessage(it.localizedMessage ?: "Unable to delete account")) }
        }
    }

    fun saveCategory(
        id: String = "",
        name: String,
        type: TransactionType,
        iconKey: String,
        colorArgb: Long
    ) {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            repository.saveCategory(ws, id, name, type, iconKey, colorArgb)
                .onSuccess { _events.emit(UiEvent.ShowMessage(if (id.isBlank()) "Category added" else "Category updated")) }
                .onFailure { _events.emit(UiEvent.ShowMessage(it.localizedMessage ?: "Failed to save category")) }
        }
    }

    fun toggleArchiveCategory(category: CategoryModel) {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            repository.toggleArchiveCategory(ws.id, category)
                .onSuccess {
                    _events.emit(
                        UiEvent.ShowMessage(if (category.archived) "Category restored" else "Category archived")
                    )
                }
                .onFailure { _events.emit(UiEvent.ShowMessage(it.localizedMessage ?: "Failed to update category")) }
        }
    }

    fun deleteCategory(category: CategoryModel) {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            repository.deleteCategory(ws.id, category)
                .onSuccess { _events.emit(UiEvent.ShowMessage("Category deleted")) }
                .onFailure { _events.emit(UiEvent.ShowMessage(it.localizedMessage ?: "Unable to delete category")) }
        }
    }

    fun previousReportMonth() {
        reportPeriodFlow.update { it.previousMonth() }
    }

    fun nextReportMonth() {
        reportPeriodFlow.update { it.nextMonth() }
    }

    fun setReportPeriod(year: Int, monthZeroBased: Int) {
        reportPeriodFlow.value = ReportPeriod(year, monthZeroBased)
    }

    fun setCurrency(code: String) {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            repository.updateWorkspaceSettings(ws.id, currencyCode = code)
                .onFailure { _events.emit(UiEvent.ShowMessage(it.localizedMessage ?: "Failed to update currency")) }
        }
    }

    fun setLedgerName(name: String) {
        val ws = uiState.value.workspace ?: return
        viewModelScope.launch {
            repository.updateWorkspaceSettings(ws.id, name = name)
                .onSuccess { _events.emit(UiEvent.ShowMessage("Ledger name updated")) }
                .onFailure { _events.emit(UiEvent.ShowMessage(it.localizedMessage ?: "Failed to update name")) }
        }
    }

    fun setThemePreference(preference: ThemePreference) {
        viewModelScope.launch { preferences.setThemePreference(preference) }
    }

    fun exportAndSharePdf() {
        val state = uiState.value
        val report = state.monthlyReport ?: return
        viewModelScope.launch {
            isExportingFlow.value = true
            try {
                val uri = withContext(Dispatchers.IO) {
                    exporter.exportMonthlyPdf(
                        ledgerName = state.ledgerName,
                        report = report,
                        accounts = state.accounts,
                        currency = state.currency,
                        debts = state.debts
                    )
                }
                val title = "${state.ledgerName} - ${DateFormatters.formatMonthYear(report.year, report.monthZeroBased)} Statement"
                exporter.launchShareSheet(uri, "application/pdf", title)
            } catch (e: Exception) {
                _events.emit(UiEvent.ShowMessage("PDF export failed: ${e.message ?: "Unknown error"}"))
            } finally {
                isExportingFlow.value = false
            }
        }
    }

    fun exportAndShareCsv() {
        val state = uiState.value
        val report = state.monthlyReport ?: return
        viewModelScope.launch {
            isExportingFlow.value = true
            try {
                val uri = withContext(Dispatchers.IO) {
                    exporter.exportMonthlyCsv(
                        ledgerName = state.ledgerName,
                        report = report,
                        accounts = state.accounts,
                        currency = state.currency,
                        debts = state.debts
                    )
                }
                val title = "${state.ledgerName} - ${DateFormatters.formatMonthYear(report.year, report.monthZeroBased)} Excel CSV"
                exporter.launchShareSheet(uri, "text/csv", title)
            } catch (e: Exception) {
                _events.emit(UiEvent.ShowMessage("Excel/CSV export failed: ${e.message ?: "Unknown error"}"))
            } finally {
                isExportingFlow.value = false
            }
        }
    }

    companion object {
        private const val TAG = "KinLedgerVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun factory(session: AppSession): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as KinLedgerApp)
                val info = requireNotNull(session.userInfo) {
                    "AppSession must be signed in or in LocalDemo mode."
                }
                val repo = if (session.isDemoMode) {
                    app.container.createDemoLedgerRepository()
                } else {
                    app.container.ledgerRepository
                }
                KinLedgerViewModel(
                    currentUserId = session.uid,
                    currentUserInfo = info,
                    isDemoMode = session.isDemoMode,
                    repository = repo,
                    authService = app.container.authService,
                    preferences = app.container.preferencesRepository,
                    exporter = app.container.statementExporter
                )
            }
        }
    }
}
