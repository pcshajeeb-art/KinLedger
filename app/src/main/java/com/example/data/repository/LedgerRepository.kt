package com.example.data.repository

import com.example.data.model.AccountModel
import com.example.data.model.AccountWithBalance
import com.example.data.model.CategoryModel
import com.example.data.model.DebtDetail
import com.example.data.model.DebtRecordModel
import com.example.data.model.DebtType
import com.example.data.model.DriveSyncStatus
import com.example.data.model.DriveWorkspaceRef
import com.example.data.model.FamilyWorkspaceModel
import com.example.data.model.LedgerUserInfo
import com.example.data.model.TransactionDetail
import com.example.data.model.TransactionModel
import com.example.data.model.TransactionType
import com.example.data.model.UserProfileModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

interface LedgerRepository {
    fun observeUserProfile(userId: String): Flow<UserProfileModel?>
    fun observeWorkspace(workspaceId: String): Flow<FamilyWorkspaceModel?>
    fun observeAccountsWithBalance(workspaceId: String, userId: String): Flow<List<AccountWithBalance>>
    fun observeCategories(workspaceId: String, userId: String): Flow<List<CategoryModel>>
    fun observeTransactions(workspaceId: String, userId: String): Flow<List<TransactionDetail>>
    fun observeDebtRecords(workspaceId: String, userId: String): Flow<List<DebtDetail>>

    fun observeSyncStatus(): Flow<DriveSyncStatus> = MutableStateFlow(DriveSyncStatus.Idle)
    fun observeAvailableWorkspaces(): Flow<List<DriveWorkspaceRef>> = MutableStateFlow(emptyList())
    suspend fun syncWithDrive(forcePull: Boolean = false): Result<Unit> = Result.success(Unit)
    suspend fun openExistingWorkspace(workspaceId: String): Result<Unit> = Result.success(Unit)

    suspend fun ensureUserProfile(user: LedgerUserInfo): Result<Unit>
    suspend fun createFamilyWorkspace(name: String, currencyCode: String): Result<String>
    suspend fun joinFamilyWorkspace(inviteCode: String): Result<String>
    suspend fun leaveWorkspace(): Result<Unit>
    suspend fun reconcileWorkspaceMembership(workspace: FamilyWorkspaceModel): Result<Unit>
    suspend fun updateWorkspaceSettings(workspaceId: String, name: String? = null, currencyCode: String? = null): Result<Unit>

    suspend fun saveAccount(
        workspace: FamilyWorkspaceModel,
        id: String = "",
        name: String,
        initialBalanceMinor: Long,
        iconKey: String,
        colorArgb: Long
    ): Result<Unit>

    suspend fun deleteAccount(workspaceId: String, account: AccountModel, totalAccounts: Int): Result<Unit>

    suspend fun saveCategory(
        workspace: FamilyWorkspaceModel,
        id: String = "",
        name: String,
        type: TransactionType,
        iconKey: String,
        colorArgb: Long
    ): Result<Unit>

    suspend fun toggleArchiveCategory(workspaceId: String, category: CategoryModel): Result<Unit>
    suspend fun deleteCategory(workspaceId: String, category: CategoryModel): Result<Unit>

    suspend fun saveTransaction(
        workspace: FamilyWorkspaceModel,
        id: String = "",
        previousTransaction: TransactionModel? = null,
        type: TransactionType,
        amountMinor: Long,
        accountId: String,
        destinationAccountId: String?,
        categoryId: String?,
        dateMillis: Long,
        note: String
    ): Result<Unit>

    suspend fun deleteTransaction(workspaceId: String, transaction: TransactionModel): Result<Unit>

    suspend fun saveDebtRecord(
        workspace: FamilyWorkspaceModel,
        id: String = "",
        previousDebt: DebtRecordModel? = null,
        type: DebtType,
        personName: String,
        amountMinor: Long,
        sourceAccountId: String,
        dateMillis: Long,
        dueDateMillis: Long?,
        note: String
    ): Result<Unit>

    suspend fun recordDebtSettlement(
        workspaceId: String,
        debt: DebtRecordModel,
        amountMinor: Long,
        settlementAccountId: String,
        settledDateMillis: Long,
        note: String
    ): Result<Boolean>

    suspend fun reopenDebtRecord(workspaceId: String, debt: DebtRecordModel): Result<Unit>
    suspend fun deleteDebtRecord(workspaceId: String, debt: DebtRecordModel): Result<Unit>
}
