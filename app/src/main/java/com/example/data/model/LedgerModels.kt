package com.example.data.model

import android.app.PendingIntent
import androidx.annotation.Keep

enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER;

    companion object {
        fun fromRaw(raw: String?): TransactionType =
            entries.firstOrNull { it.name == raw } ?: EXPENSE
    }
}

enum class DebtType {
    RECEIVABLE,
    PAYABLE;

    companion object {
        fun fromRaw(raw: String?): DebtType =
            entries.firstOrNull { it.name == raw } ?: RECEIVABLE
    }
}

@Keep
data class GoogleAccountUser(
    val uid: String,
    val displayName: String,
    val email: String,
    val avatarUrl: String? = null
)

data class LedgerUserInfo(
    val uid: String,
    val displayName: String,
    val email: String
)

@Keep
data class UserProfileModel(
    val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val activeWorkspaceId: String? = null,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

@Keep
data class FamilyWorkspaceModel(
    val id: String = "",
    val name: String = "Family Ledger",
    val inviteCode: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val currencyCode: String = "INR",
    val driveFileId: String = "",
    val driveFileName: String = "",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

@Keep
data class AccountModel(
    val id: String = "",
    val workspaceId: String = "",
    val name: String = "",
    val initialBalanceMinor: Long = 0L,
    val cachedBalanceMinor: Long = 0L,
    val iconKey: String = "wallet",
    val colorArgb: Long = 0xFF0D9488L,
    val defaultAccount: Boolean = false,
    val memberUids: List<String> = emptyList(),
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)

data class AccountWithBalance(
    val account: AccountModel,
    val currentBalanceMinor: Long,
    val totalInflowMinor: Long,
    val totalOutflowMinor: Long
)

@Keep
data class CategoryModel(
    val id: String = "",
    val workspaceId: String = "",
    val name: String = "",
    val type: String = TransactionType.EXPENSE.name,
    val iconKey: String = "category",
    val colorArgb: Long = 0xFF0D9488L,
    val primaryCategory: Boolean = false,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val memberUids: List<String> = emptyList(),
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
) {
    val transactionType: TransactionType
        get() = TransactionType.fromRaw(type)
}

@Keep
data class TransactionModel(
    val id: String = "",
    val workspaceId: String = "",
    val type: String = TransactionType.EXPENSE.name,
    val amountMinor: Long = 0L,
    val accountId: String = "",
    val destinationAccountId: String? = null,
    val categoryId: String? = null,
    val dateMillis: Long = 0L,
    val note: String = "",
    val createdByUid: String = "",
    val createdByName: String = "",
    val createdByEmail: String = "",
    val memberUids: List<String> = emptyList(),
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
) {
    val transactionType: TransactionType
        get() = TransactionType.fromRaw(type)
}

data class TransactionDetail(
    val transaction: TransactionModel,
    val account: AccountModel?,
    val destinationAccount: AccountModel?,
    val category: CategoryModel?
)

@Keep
data class DebtSettlementEntry(
    val id: String = "",
    val amountMinor: Long = 0L,
    val dateMillis: Long = 0L,
    val accountId: String = "",
    val note: String = "",
    val settledByUid: String = "",
    val settledByName: String = ""
)

@Keep
data class DebtRecordModel(
    val id: String = "",
    val workspaceId: String = "",
    val type: String = DebtType.RECEIVABLE.name,
    val personName: String = "",
    val amountMinor: Long = 0L,
    val settledAmountMinor: Long = 0L,
    val remainingAmountMinor: Long = 0L,
    val sourceAccountId: String = "",
    val dateMillis: Long = 0L,
    val dueDateMillis: Long? = null,
    val note: String = "",
    val settled: Boolean = false,
    val settledDateMillis: Long? = null,
    val settlementAccountId: String? = null,
    val settlements: List<DebtSettlementEntry> = emptyList(),
    val createdByUid: String = "",
    val createdByName: String = "",
    val memberUids: List<String> = emptyList(),
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
) {
    val debtType: DebtType
        get() = DebtType.fromRaw(type)

    val effectiveOriginalAmountMinor: Long
        get() = amountMinor

    val effectiveSettledAmountMinor: Long
        get() = when {
            settlements.isNotEmpty() -> settledAmountMinor.coerceIn(0L, amountMinor)
            settled && settledAmountMinor <= 0L -> amountMinor
            else -> settledAmountMinor.coerceIn(0L, amountMinor)
        }

    val effectiveRemainingAmountMinor: Long
        get() = (amountMinor - effectiveSettledAmountMinor).coerceAtLeast(0L)
}

data class DebtSettlementDetail(
    val entry: DebtSettlementEntry,
    val account: AccountModel?
)

data class DebtDetail(
    val debt: DebtRecordModel,
    val sourceAccount: AccountModel?,
    val settlementAccount: AccountModel?,
    val settlementDetails: List<DebtSettlementDetail> = emptyList()
)

@Keep
data class DriveWorkspaceRef(
    val workspaceId: String = "",
    val name: String = "Family Ledger",
    val inviteCode: String = "",
    val currencyCode: String = "INR",
    val driveFileId: String = "",
    val driveFileName: String = "",
    val updatedAtMillis: Long = 0L
)

@Keep
data class DriveUserIndex(
    val schemaVersion: Int = 1,
    val userProfile: UserProfileModel = UserProfileModel(),
    val workspaceRefs: List<DriveWorkspaceRef> = emptyList(),
    val updatedAtMillis: Long = 0L
)

@Keep
data class DriveLedgerDocument(
    val schemaVersion: Int = 1,
    val workspace: FamilyWorkspaceModel = FamilyWorkspaceModel(),
    val accounts: List<AccountModel> = emptyList(),
    val categories: List<CategoryModel> = emptyList(),
    val transactions: List<TransactionModel> = emptyList(),
    val debts: List<DebtRecordModel> = emptyList(),
    val updatedAtMillis: Long = 0L
)

sealed interface DriveSyncStatus {
    data object Idle : DriveSyncStatus
    data object Syncing : DriveSyncStatus
    data class Synced(val lastSyncedMillis: Long) : DriveSyncStatus
    data class PendingLocal(val message: String = "Saved locally • Sync pending") : DriveSyncStatus
    data class NeedsAuthorization(val pendingIntent: PendingIntent?) : DriveSyncStatus
    data class Error(val message: String) : DriveSyncStatus
}

data class CategoryExpenseSlice(
    val category: CategoryModel,
    val amountMinor: Long,
    val percentage: Float
)

data class MonthlyBarPoint(
    val year: Int,
    val monthZeroBased: Int,
    val label: String,
    val incomeMinor: Long,
    val expenseMinor: Long
)

data class MonthlyReportSummary(
    val year: Int,
    val monthZeroBased: Int,
    val totalIncomeMinor: Long,
    val totalExpenseMinor: Long,
    val netCashflowMinor: Long,
    val expenseSlices: List<CategoryExpenseSlice>,
    val trendPoints: List<MonthlyBarPoint>,
    val transactions: List<TransactionDetail>
)

data class CurrencyOption(
    val code: String,
    val symbol: String,
    val displayName: String
) {
    companion object {
        val ALL = listOf(
            CurrencyOption("INR", "₹", "Indian Rupee (₹)"),
            CurrencyOption("USD", "$", "US Dollar ($)"),
            CurrencyOption("EUR", "€", "Euro (€)"),
            CurrencyOption("GBP", "£", "British Pound (£)"),
            CurrencyOption("AED", "AED ", "UAE Dirham (AED)"),
            CurrencyOption("SGD", "S$", "Singapore Dollar (S$)")
        )

        fun fromCode(code: String): CurrencyOption =
            ALL.firstOrNull { it.code == code } ?: ALL.first()
    }
}
