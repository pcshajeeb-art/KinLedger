package com.example.data.repository

import com.example.data.model.AccountModel
import com.example.data.model.AccountWithBalance
import com.example.data.model.CategoryModel
import com.example.data.model.DateFormatters
import com.example.data.model.DebtDetail
import com.example.data.model.DebtRecordModel
import com.example.data.model.DebtSettlementDetail
import com.example.data.model.DebtSettlementEntry
import com.example.data.model.DebtType
import com.example.data.model.FamilyWorkspaceModel
import com.example.data.model.LedgerUserInfo
import com.example.data.model.TransactionDetail
import com.example.data.model.TransactionModel
import com.example.data.model.TransactionType
import com.example.data.model.UserProfileModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.Calendar
import java.util.UUID

/**
 * Fully functional in-memory repository for exploring KinLedger without a Google account.
 * Never reads or writes to Google Drive or any remote backend.
 */
class DemoLedgerRepository(
    private val demoUid: String = DEMO_USER_ID
) : LedgerRepository {

    companion object {
        const val DEMO_USER_ID = "local_demo_user"
        private const val DEMO_MEMBER_PRIYA = "demo_member_priya"
        private const val DEMO_WORKSPACE_ID = "demo_ws_family"
        private const val DEMO_INVITE_CODE = "DEMO24"
    }

    private data class DemoStore(
        val userProfile: UserProfileModel?,
        val workspaces: Map<String, FamilyWorkspaceModel>,
        val accounts: List<AccountModel>,
        val categories: List<CategoryModel>,
        val transactions: List<TransactionModel>,
        val debts: List<DebtRecordModel>
    )

    private val storeFlow = MutableStateFlow(createInitialStore())

    fun reset() {
        storeFlow.value = createInitialStore()
    }

    private fun createInitialStore(): DemoStore {
        val wsId = DEMO_WORKSPACE_ID
        val members = listOf(demoUid, DEMO_MEMBER_PRIYA)

        val today = DateFormatters.startOfTodayMillis()
        val yesterday = DateFormatters.startOfYesterdayMillis()
        val twoDaysAgo = daysAgoInCurrentOrRecentMonth(2)
        val fourDaysAgo = daysAgoInCurrentOrRecentMonth(4)
        val sixDaysAgo = daysAgoInCurrentOrRecentMonth(6)
        val prevMonthMid = previousMonthMidMillis(1)
        val twoMonthsAgoMid = previousMonthMidMillis(2)

        val workspace = FamilyWorkspaceModel(
            id = wsId,
            name = "Sharma Family (Demo)",
            inviteCode = DEMO_INVITE_CODE,
            ownerId = demoUid,
            memberUids = members,
            currencyCode = "INR"
        )

        val profile = UserProfileModel(
            id = demoUid,
            userId = demoUid,
            displayName = "Guest Preview",
            email = "Local Preview • Not synced",
            activeWorkspaceId = wsId
        )

        val cashAccount = AccountModel(
            id = "demo_acct_cash",
            workspaceId = wsId,
            name = "Cash",
            initialBalanceMinor = 500_000L, // ₹5,000
            cachedBalanceMinor = 500_000L,
            iconKey = "wallet",
            colorArgb = 0xFF0D9488L,
            defaultAccount = true,
            memberUids = members
        )
        val bankAccount = AccountModel(
            id = "demo_acct_bank",
            workspaceId = wsId,
            name = "Bank",
            initialBalanceMinor = 4_500_000L, // ₹45,000
            cachedBalanceMinor = 4_500_000L,
            iconKey = "bank",
            colorArgb = 0xFF0EA5E9L,
            defaultAccount = true,
            memberUids = members
        )
        val upiAccount = AccountModel(
            id = "demo_acct_upi",
            workspaceId = wsId,
            name = "UPI Savings",
            initialBalanceMinor = 1_200_000L, // ₹12,000
            cachedBalanceMinor = 1_200_000L,
            iconKey = "credit_card",
            colorArgb = 0xFF8B5CF6L,
            defaultAccount = false,
            memberUids = members
        )

        val categories = defaultCategories(wsId, members)

        val transactions = listOf(
            TransactionModel(
                id = "demo_tx_1",
                workspaceId = wsId,
                type = TransactionType.EXPENSE.name,
                amountMinor = 245_000L, // ₹2,450
                accountId = cashAccount.id,
                categoryId = "demo_cat_grocery",
                dateMillis = today,
                note = "Weekly groceries & fresh produce",
                createdByUid = demoUid,
                createdByName = "Guest Preview",
                memberUids = members
            ),
            TransactionModel(
                id = "demo_tx_2",
                workspaceId = wsId,
                type = TransactionType.TRANSFER.name,
                amountMinor = 500_000L, // ₹5,000 Bank -> Cash
                accountId = bankAccount.id,
                destinationAccountId = cashAccount.id,
                categoryId = null,
                dateMillis = yesterday,
                note = "ATM cash withdrawal",
                createdByUid = demoUid,
                createdByName = "Guest Preview",
                memberUids = members
            ),
            TransactionModel(
                id = "demo_tx_3",
                workspaceId = wsId,
                type = TransactionType.EXPENSE.name,
                amountMinor = 185_000L, // ₹1,850
                accountId = upiAccount.id,
                categoryId = "demo_cat_utilities",
                dateMillis = twoDaysAgo,
                note = "Electricity & fiber internet",
                createdByUid = DEMO_MEMBER_PRIYA,
                createdByName = "Priya",
                memberUids = members
            ),
            TransactionModel(
                id = "demo_tx_4",
                workspaceId = wsId,
                type = TransactionType.INCOME.name,
                amountMinor = 3_500_000L, // ₹35,000
                accountId = bankAccount.id,
                categoryId = "demo_cat_remittance",
                dateMillis = fourDaysAgo,
                note = "Monthly family support remittance",
                createdByUid = demoUid,
                createdByName = "Guest Preview",
                memberUids = members
            ),
            TransactionModel(
                id = "demo_tx_5",
                workspaceId = wsId,
                type = TransactionType.EXPENSE.name,
                amountMinor = 112_000L, // ₹1,120
                accountId = upiAccount.id,
                categoryId = "demo_cat_dining",
                dateMillis = sixDaysAgo,
                note = "Weekend family dinner",
                createdByUid = DEMO_MEMBER_PRIYA,
                createdByName = "Priya",
                memberUids = members
            ),
            TransactionModel(
                id = "demo_tx_6",
                workspaceId = wsId,
                type = TransactionType.INCOME.name,
                amountMinor = 2_800_000L, // ₹28,000 last month
                accountId = bankAccount.id,
                categoryId = "demo_cat_remittance",
                dateMillis = prevMonthMid,
                note = "Last month remittance",
                createdByUid = demoUid,
                createdByName = "Guest Preview",
                memberUids = members
            ),
            TransactionModel(
                id = "demo_tx_7",
                workspaceId = wsId,
                type = TransactionType.EXPENSE.name,
                amountMinor = 420_000L, // ₹4,200 last month
                accountId = bankAccount.id,
                categoryId = "demo_cat_medical",
                dateMillis = prevMonthMid,
                note = "Routine health checkup",
                createdByUid = DEMO_MEMBER_PRIYA,
                createdByName = "Priya",
                memberUids = members
            ),
            TransactionModel(
                id = "demo_tx_8",
                workspaceId = wsId,
                type = TransactionType.EXPENSE.name,
                amountMinor = 310_000L, // ₹3,100 two months ago
                accountId = cashAccount.id,
                categoryId = "demo_cat_grocery",
                dateMillis = twoMonthsAgoMid,
                note = "Monthly household supplies",
                createdByUid = demoUid,
                createdByName = "Guest Preview",
                memberUids = members
            )
        )

        val debts = listOf(
            DebtRecordModel(
                id = "demo_debt_1",
                workspaceId = wsId,
                type = DebtType.RECEIVABLE.name,
                personName = "Arjun Mehta",
                amountMinor = 1_000_000L, // ₹10,000 lent
                settledAmountMinor = 400_000L, // ₹4,000 repaid
                remainingAmountMinor = 600_000L, // ₹6,000 remaining
                sourceAccountId = bankAccount.id,
                dateMillis = sixDaysAgo,
                dueDateMillis = today + 10L * 86_400_000L,
                note = "Home repair advance",
                settled = false,
                settlements = listOf(
                    DebtSettlementEntry(
                        id = "demo_settle_1",
                        amountMinor = 400_000L,
                        dateMillis = yesterday,
                        accountId = cashAccount.id,
                        note = "First cash installment",
                        settledByUid = demoUid,
                        settledByName = "Guest Preview"
                    )
                ),
                createdByUid = demoUid,
                createdByName = "Guest Preview",
                memberUids = members
            ),
            DebtRecordModel(
                id = "demo_debt_2",
                workspaceId = wsId,
                type = DebtType.PAYABLE.name,
                personName = "Kiran Uncle",
                amountMinor = 350_000L, // ₹3,500 borrowed
                settledAmountMinor = 0L,
                remainingAmountMinor = 350_000L,
                sourceAccountId = cashAccount.id,
                dateMillis = fourDaysAgo,
                note = "Festival catering balance",
                settled = false,
                settlements = emptyList(),
                createdByUid = DEMO_MEMBER_PRIYA,
                createdByName = "Priya",
                memberUids = members
            ),
            DebtRecordModel(
                id = "demo_debt_3",
                workspaceId = wsId,
                type = DebtType.RECEIVABLE.name,
                personName = "Sneha",
                amountMinor = 200_000L, // ₹2,000 lent & settled
                settledAmountMinor = 200_000L,
                remainingAmountMinor = 0L,
                sourceAccountId = upiAccount.id,
                dateMillis = prevMonthMid,
                note = "Train tickets",
                settled = true,
                settledDateMillis = twoDaysAgo,
                settlementAccountId = upiAccount.id,
                settlements = listOf(
                    DebtSettlementEntry(
                        id = "demo_settle_2",
                        amountMinor = 200_000L,
                        dateMillis = twoDaysAgo,
                        accountId = upiAccount.id,
                        note = "Settled via UPI",
                        settledByUid = demoUid,
                        settledByName = "Guest Preview"
                    )
                ),
                createdByUid = demoUid,
                createdByName = "Guest Preview",
                memberUids = members
            )
        )

        return DemoStore(
            userProfile = profile,
            workspaces = mapOf(wsId to workspace),
            accounts = listOf(cashAccount, bankAccount, upiAccount),
            categories = categories,
            transactions = transactions,
            debts = debts
        )
    }

    private fun defaultCategories(wsId: String, members: List<String>, idPrefix: String = "demo_cat_"): List<CategoryModel> {
        data class SeedCat(
            val key: String,
            val name: String,
            val type: TransactionType,
            val icon: String,
            val color: Long,
            val primary: Boolean,
            val order: Int
        )
        val seeds = listOf(
            SeedCat("grocery", "Grocery & Household", TransactionType.EXPENSE, "shopping_cart", 0xFF0D9488L, true, 1),
            SeedCat("dining", "Dining & Food Delivery", TransactionType.EXPENSE, "restaurant", 0xFFE11D48L, false, 2),
            SeedCat("gift", "Gift", TransactionType.EXPENSE, "card_giftcard", 0xFF8B5CF6L, false, 3),
            SeedCat("medical", "Hospital & Medical Expense", TransactionType.EXPENSE, "medical_services", 0xFFEF4444L, false, 4),
            SeedCat("utilities", "Utilities & Bills", TransactionType.EXPENSE, "receipt_long", 0xFFD97706L, false, 5),
            SeedCat("misc", "Miscellaneous", TransactionType.EXPENSE, "category", 0xFF64748BL, false, 6),
            SeedCat("remittance", "NRI Remittance", TransactionType.INCOME, "currency_exchange", 0xFF0D9488L, true, 1),
            SeedCat("other_income", "Other Income / Refunds", TransactionType.INCOME, "savings", 0xFF059669L, false, 2)
        )
        return seeds.map { seed ->
            CategoryModel(
                id = "$idPrefix${seed.key}",
                workspaceId = wsId,
                name = seed.name,
                type = seed.type.name,
                iconKey = seed.icon,
                colorArgb = seed.color,
                primaryCategory = seed.primary,
                archived = false,
                sortOrder = seed.order,
                memberUids = members
            )
        }
    }

    private fun daysAgoInCurrentOrRecentMonth(daysAgo: Int): Long {
        val nowCal = Calendar.getInstance()
        val currentMonth = nowCal.get(Calendar.MONTH)
        val targetCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Keep seeded "this month" activity inside the current month even on the 1st–5th day
        return if (targetCal.get(Calendar.MONTH) == currentMonth) {
            targetCal.timeInMillis
        } else {
            DateFormatters.startOfTodayMillis()
        }
    }

    private fun previousMonthMidMillis(monthsBack: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 15)
            add(Calendar.MONTH, -monthsBack)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    override fun observeUserProfile(userId: String): Flow<UserProfileModel?> =
        storeFlow.map { it.userProfile }

    override fun observeWorkspace(workspaceId: String): Flow<FamilyWorkspaceModel?> =
        storeFlow.map { it.workspaces[workspaceId] }

    override fun observeAccountsWithBalance(workspaceId: String, userId: String): Flow<List<AccountWithBalance>> =
        storeFlow.map { store ->
            val accts = store.accounts
                .filter { it.workspaceId == workspaceId }
                .sortedWith(compareByDescending<AccountModel> { it.defaultAccount }.thenBy { it.name.lowercase() })
            val txs = store.transactions.filter { it.workspaceId == workspaceId }
            val debts = store.debts.filter { it.workspaceId == workspaceId }
            BalanceCalculator.computeAccountBalances(accts, txs, debts)
        }

    override fun observeCategories(workspaceId: String, userId: String): Flow<List<CategoryModel>> =
        storeFlow.map { store ->
            store.categories
                .filter { it.workspaceId == workspaceId }
                .sortedWith(compareBy<CategoryModel> { it.type }.thenBy { it.sortOrder }.thenBy { it.name.lowercase() })
        }

    override fun observeTransactions(workspaceId: String, userId: String): Flow<List<TransactionDetail>> =
        storeFlow.map { store ->
            val acctMap = store.accounts.filter { it.workspaceId == workspaceId }.associateBy { it.id }
            val catMap = store.categories.filter { it.workspaceId == workspaceId }.associateBy { it.id }
            store.transactions
                .filter { it.workspaceId == workspaceId }
                .sortedByDescending { it.dateMillis }
                .map { tx ->
                    TransactionDetail(
                        transaction = tx,
                        account = acctMap[tx.accountId],
                        destinationAccount = tx.destinationAccountId?.let { acctMap[it] },
                        category = tx.categoryId?.let { catMap[it] }
                    )
                }
        }

    override fun observeDebtRecords(workspaceId: String, userId: String): Flow<List<DebtDetail>> =
        storeFlow.map { store ->
            val acctMap = store.accounts.filter { it.workspaceId == workspaceId }.associateBy { it.id }
            store.debts
                .filter { it.workspaceId == workspaceId }
                .sortedWith(compareBy<DebtRecordModel> { it.settled }.thenByDescending { it.dateMillis })
                .map { debt ->
                    DebtDetail(
                        debt = debt,
                        sourceAccount = acctMap[debt.sourceAccountId],
                        settlementAccount = debt.settlementAccountId?.let { acctMap[it] },
                        settlementDetails = debt.settlements
                            .sortedByDescending { it.dateMillis }
                            .map { entry ->
                                DebtSettlementDetail(
                                    entry = entry,
                                    account = acctMap[entry.accountId]
                                )
                            }
                    )
                }
        }

    override suspend fun ensureUserProfile(user: LedgerUserInfo): Result<Unit> {
        storeFlow.update { current ->
            val existing = current.userProfile
            val updated = existing?.copy(
                userId = user.uid,
                displayName = user.displayName,
                email = user.email
            ) ?: UserProfileModel(
                id = user.uid,
                userId = user.uid,
                displayName = user.displayName,
                email = user.email,
                activeWorkspaceId = DEMO_WORKSPACE_ID
            )
            current.copy(userProfile = updated)
        }
        return Result.success(Unit)
    }

    override suspend fun createFamilyWorkspace(name: String, currencyCode: String): Result<String> {
        val cleanName = name.trim().ifEmpty { "Family Ledger" }.take(60)
        val cleanCurrency = currencyCode.trim().ifEmpty { "INR" }.take(6)
        val newWsId = "demo_ws_${UUID.randomUUID().toString().take(8)}"
        val code = UUID.randomUUID().toString().replace("-", "").uppercase().take(6)
        val members = listOf(demoUid)

        val ws = FamilyWorkspaceModel(
            id = newWsId,
            name = cleanName,
            inviteCode = code,
            ownerId = demoUid,
            memberUids = members,
            currencyCode = cleanCurrency
        )
        val cash = AccountModel(
            id = "acct_${UUID.randomUUID().toString().take(8)}",
            workspaceId = newWsId,
            name = "Cash",
            initialBalanceMinor = 0L,
            cachedBalanceMinor = 0L,
            iconKey = "wallet",
            colorArgb = 0xFF0D9488L,
            defaultAccount = true,
            memberUids = members
        )
        val bank = AccountModel(
            id = "acct_${UUID.randomUUID().toString().take(8)}",
            workspaceId = newWsId,
            name = "Bank",
            initialBalanceMinor = 0L,
            cachedBalanceMinor = 0L,
            iconKey = "bank",
            colorArgb = 0xFF0F766EL,
            defaultAccount = true,
            memberUids = members
        )
        val cats = defaultCategories(newWsId, members, idPrefix = "${newWsId}_cat_")

        storeFlow.update { store ->
            store.copy(
                userProfile = store.userProfile?.copy(activeWorkspaceId = newWsId),
                workspaces = store.workspaces + (newWsId to ws),
                accounts = store.accounts + listOf(cash, bank),
                categories = store.categories + cats
            )
        }
        return Result.success(newWsId)
    }

    override suspend fun joinFamilyWorkspace(inviteCode: String): Result<String> {
        val cleanCode = inviteCode.trim().uppercase()
        if (!cleanCode.matches(Regex("^[A-Z0-9]{6}$"))) {
            return Result.failure(IllegalArgumentException("Enter a valid 6-character invite code."))
        }
        val target = storeFlow.value.workspaces.values.firstOrNull { it.inviteCode == cleanCode }
            ?: return Result.failure(
                IllegalStateException("Code '$cleanCode' not found in local preview. Use DEMO24 or create a new ledger.")
            )
        storeFlow.update { store ->
            store.copy(userProfile = store.userProfile?.copy(activeWorkspaceId = target.id))
        }
        return Result.success(target.id)
    }

    override suspend fun leaveWorkspace(): Result<Unit> {
        storeFlow.update { store ->
            store.copy(userProfile = store.userProfile?.copy(activeWorkspaceId = null))
        }
        return Result.success(Unit)
    }

    override suspend fun reconcileWorkspaceMembership(workspace: FamilyWorkspaceModel): Result<Unit> =
        Result.success(Unit)

    override suspend fun updateWorkspaceSettings(
        workspaceId: String,
        name: String?,
        currencyCode: String?
    ): Result<Unit> {
        storeFlow.update { store ->
            val existing = store.workspaces[workspaceId] ?: return@update store
            val updated = existing.copy(
                name = name?.trim()?.takeIf { it.isNotEmpty() }?.take(60) ?: existing.name,
                currencyCode = currencyCode?.trim()?.takeIf { it.isNotEmpty() }?.take(6) ?: existing.currencyCode
            )
            store.copy(workspaces = store.workspaces + (workspaceId to updated))
        }
        return Result.success(Unit)
    }

    override suspend fun saveAccount(
        workspace: FamilyWorkspaceModel,
        id: String,
        name: String,
        initialBalanceMinor: Long,
        iconKey: String,
        colorArgb: Long
    ): Result<Unit> {
        val cleanName = name.trim().take(50)
        if (cleanName.isEmpty()) return Result.failure(IllegalArgumentException("Account name required"))
        storeFlow.update { store ->
            val nextAccounts = if (id.isBlank()) {
                val created = AccountModel(
                    id = "demo_acct_${UUID.randomUUID().toString().take(8)}",
                    workspaceId = workspace.id,
                    name = cleanName,
                    initialBalanceMinor = initialBalanceMinor,
                    cachedBalanceMinor = initialBalanceMinor,
                    iconKey = iconKey,
                    colorArgb = colorArgb,
                    defaultAccount = false,
                    memberUids = workspace.memberUids
                )
                store.accounts + created
            } else {
                store.accounts.map { acct ->
                    if (acct.id == id) {
                        acct.copy(
                            name = cleanName,
                            initialBalanceMinor = initialBalanceMinor,
                            iconKey = iconKey,
                            colorArgb = colorArgb
                        )
                    } else acct
                }
            }
            store.copy(accounts = nextAccounts)
        }
        return Result.success(Unit)
    }

    override suspend fun deleteAccount(
        workspaceId: String,
        account: AccountModel,
        totalAccounts: Int
    ): Result<Unit> {
        if (totalAccounts <= 1) {
            return Result.failure(IllegalStateException("Keep at least one active account."))
        }
        storeFlow.update { store ->
            store.copy(accounts = store.accounts.filterNot { it.id == account.id && it.workspaceId == workspaceId })
        }
        return Result.success(Unit)
    }

    override suspend fun saveCategory(
        workspace: FamilyWorkspaceModel,
        id: String,
        name: String,
        type: TransactionType,
        iconKey: String,
        colorArgb: Long
    ): Result<Unit> {
        val cleanName = name.trim().take(50)
        if (cleanName.isEmpty()) return Result.failure(IllegalArgumentException("Category name required"))
        val safeType = if (type == TransactionType.INCOME) TransactionType.INCOME else TransactionType.EXPENSE
        storeFlow.update { store ->
            val nextCats = if (id.isBlank()) {
                val created = CategoryModel(
                    id = "demo_cat_${UUID.randomUUID().toString().take(8)}",
                    workspaceId = workspace.id,
                    name = cleanName,
                    type = safeType.name,
                    iconKey = iconKey,
                    colorArgb = colorArgb,
                    primaryCategory = false,
                    archived = false,
                    sortOrder = 50,
                    memberUids = workspace.memberUids
                )
                store.categories + created
            } else {
                store.categories.map { cat ->
                    if (cat.id == id) {
                        cat.copy(name = cleanName, iconKey = iconKey, colorArgb = colorArgb)
                    } else cat
                }
            }
            store.copy(categories = nextCats)
        }
        return Result.success(Unit)
    }

    override suspend fun toggleArchiveCategory(workspaceId: String, category: CategoryModel): Result<Unit> {
        storeFlow.update { store ->
            store.copy(
                categories = store.categories.map {
                    if (it.id == category.id && it.workspaceId == workspaceId) {
                        it.copy(archived = !it.archived)
                    } else it
                }
            )
        }
        return Result.success(Unit)
    }

    override suspend fun deleteCategory(workspaceId: String, category: CategoryModel): Result<Unit> {
        val isUsed = storeFlow.value.transactions.any {
            it.workspaceId == workspaceId && it.categoryId == category.id
        }
        if (isUsed) {
            return Result.failure(
                IllegalStateException("This category has transactions. Archive it instead so history stays intact.")
            )
        }
        storeFlow.update { store ->
            store.copy(
                categories = store.categories.filterNot { it.id == category.id && it.workspaceId == workspaceId }
            )
        }
        return Result.success(Unit)
    }

    override suspend fun saveTransaction(
        workspace: FamilyWorkspaceModel,
        id: String,
        previousTransaction: TransactionModel?,
        type: TransactionType,
        amountMinor: Long,
        accountId: String,
        destinationAccountId: String?,
        categoryId: String?,
        dateMillis: Long,
        note: String
    ): Result<Unit> {
        if (amountMinor <= 0L) return Result.failure(IllegalArgumentException("Amount must be greater than 0."))
        if (type == TransactionType.TRANSFER) {
            if (destinationAccountId.isNullOrBlank() || destinationAccountId == accountId) {
                return Result.failure(IllegalArgumentException("Select two different accounts for transfer."))
            }
        } else if (categoryId.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Please select a category."))
        }

        val cleanNote = note.trim().take(200)
        val memberName = storeFlow.value.userProfile?.displayName?.ifBlank { "Guest Preview" } ?: "Guest Preview"

        storeFlow.update { store ->
            val nextTxs = if (id.isBlank()) {
                val created = TransactionModel(
                    id = "demo_tx_${UUID.randomUUID().toString().take(8)}",
                    workspaceId = workspace.id,
                    type = type.name,
                    amountMinor = amountMinor,
                    accountId = accountId,
                    destinationAccountId = if (type == TransactionType.TRANSFER) destinationAccountId else null,
                    categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
                    dateMillis = dateMillis,
                    note = cleanNote,
                    createdByUid = demoUid,
                    createdByName = memberName,
                    createdByEmail = "",
                    memberUids = workspace.memberUids
                )
                store.transactions + created
            } else {
                store.transactions.map { tx ->
                    if (tx.id == id) {
                        tx.copy(
                            type = type.name,
                            amountMinor = amountMinor,
                            accountId = accountId,
                            destinationAccountId = if (type == TransactionType.TRANSFER) destinationAccountId else null,
                            categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
                            dateMillis = dateMillis,
                            note = cleanNote
                        )
                    } else tx
                }
            }
            store.copy(transactions = nextTxs)
        }
        return Result.success(Unit)
    }

    override suspend fun deleteTransaction(workspaceId: String, transaction: TransactionModel): Result<Unit> {
        storeFlow.update { store ->
            store.copy(
                transactions = store.transactions.filterNot { it.id == transaction.id && it.workspaceId == workspaceId }
            )
        }
        return Result.success(Unit)
    }

    override suspend fun saveDebtRecord(
        workspace: FamilyWorkspaceModel,
        id: String,
        previousDebt: DebtRecordModel?,
        type: DebtType,
        personName: String,
        amountMinor: Long,
        sourceAccountId: String,
        dateMillis: Long,
        dueDateMillis: Long?,
        note: String
    ): Result<Unit> {
        val cleanPerson = personName.trim().take(60)
        if (cleanPerson.isEmpty()) return Result.failure(IllegalArgumentException("Enter person name"))
        if (amountMinor <= 0L) return Result.failure(IllegalArgumentException("Amount must be > 0"))
        val alreadySettled = previousDebt?.effectiveSettledAmountMinor ?: 0L
        if (previousDebt != null && amountMinor < alreadySettled) {
            return Result.failure(IllegalArgumentException("Principal cannot be less than already settled amount."))
        }

        val cleanNote = note.trim().take(200)
        val memberName = storeFlow.value.userProfile?.displayName?.ifBlank { "Guest Preview" } ?: "Guest Preview"

        storeFlow.update { store ->
            val nextDebts = if (id.isBlank()) {
                val created = DebtRecordModel(
                    id = "demo_debt_${UUID.randomUUID().toString().take(8)}",
                    workspaceId = workspace.id,
                    type = type.name,
                    personName = cleanPerson,
                    amountMinor = amountMinor,
                    settledAmountMinor = 0L,
                    remainingAmountMinor = amountMinor,
                    sourceAccountId = sourceAccountId,
                    dateMillis = dateMillis,
                    dueDateMillis = dueDateMillis?.takeIf { it > 0L },
                    note = cleanNote,
                    settled = false,
                    settlements = emptyList(),
                    createdByUid = demoUid,
                    createdByName = memberName,
                    memberUids = workspace.memberUids
                )
                store.debts + created
            } else {
                val newRemaining = (amountMinor - alreadySettled).coerceAtLeast(0L)
                val nowSettled = newRemaining == 0L
                store.debts.map { d ->
                    if (d.id == id) {
                        val lastSettlement = d.settlements.maxByOrNull { it.dateMillis }
                        d.copy(
                            type = type.name,
                            personName = cleanPerson,
                            amountMinor = amountMinor,
                            remainingAmountMinor = newRemaining,
                            settled = nowSettled,
                            sourceAccountId = sourceAccountId,
                            dateMillis = dateMillis,
                            dueDateMillis = dueDateMillis?.takeIf { it > 0L },
                            note = cleanNote,
                            settledDateMillis = if (nowSettled) {
                                d.settledDateMillis ?: lastSettlement?.dateMillis ?: System.currentTimeMillis()
                            } else null,
                            settlementAccountId = if (nowSettled) {
                                d.settlementAccountId ?: lastSettlement?.accountId ?: sourceAccountId
                            } else null
                        )
                    } else d
                }
            }
            store.copy(debts = nextDebts)
        }
        return Result.success(Unit)
    }

    override suspend fun recordDebtSettlement(
        workspaceId: String,
        debt: DebtRecordModel,
        amountMinor: Long,
        settlementAccountId: String,
        settledDateMillis: Long,
        note: String
    ): Result<Boolean> {
        val remainingBefore = debt.effectiveRemainingAmountMinor
        if (amountMinor <= 0L) {
            return Result.failure(IllegalArgumentException("Settlement amount must be greater than 0."))
        }
        if (amountMinor > remainingBefore) {
            return Result.failure(IllegalArgumentException("Amount cannot exceed remaining balance."))
        }
        if (settlementAccountId.isBlank()) {
            return Result.failure(IllegalArgumentException("Please select an account."))
        }

        val memberName = storeFlow.value.userProfile?.displayName?.ifBlank { "Guest Preview" } ?: "Guest Preview"
        val newSettledTotal = debt.effectiveSettledAmountMinor + amountMinor
        val newRemaining = (debt.amountMinor - newSettledTotal).coerceAtLeast(0L)
        val isFullySettled = newRemaining == 0L

        val newEntry = DebtSettlementEntry(
            id = "demo_settle_${UUID.randomUUID().toString().take(8)}",
            amountMinor = amountMinor,
            dateMillis = settledDateMillis,
            accountId = settlementAccountId,
            note = note.trim().take(200),
            settledByUid = demoUid,
            settledByName = memberName
        )

        storeFlow.update { store ->
            store.copy(
                debts = store.debts.map { existing ->
                    if (existing.id == debt.id && existing.workspaceId == workspaceId) {
                        existing.copy(
                            settled = isFullySettled,
                            settledAmountMinor = newSettledTotal,
                            remainingAmountMinor = newRemaining,
                            settlements = existing.settlements + newEntry,
                            settledDateMillis = if (isFullySettled) settledDateMillis else existing.settledDateMillis,
                            settlementAccountId = if (isFullySettled) settlementAccountId else existing.settlementAccountId
                        )
                    } else existing
                }
            )
        }
        return Result.success(isFullySettled)
    }

    override suspend fun reopenDebtRecord(workspaceId: String, debt: DebtRecordModel): Result<Unit> {
        storeFlow.update { store ->
            store.copy(
                debts = store.debts.map { existing ->
                    if (existing.id == debt.id && existing.workspaceId == workspaceId) {
                        existing.copy(
                            settled = false,
                            settledAmountMinor = 0L,
                            remainingAmountMinor = existing.amountMinor,
                            settlements = emptyList(),
                            settledDateMillis = null,
                            settlementAccountId = null
                        )
                    } else existing
                }
            )
        }
        return Result.success(Unit)
    }

    override suspend fun deleteDebtRecord(workspaceId: String, debt: DebtRecordModel): Result<Unit> {
        storeFlow.update { store ->
            store.copy(
                debts = store.debts.filterNot { it.id == debt.id && it.workspaceId == workspaceId }
            )
        }
        return Result.success(Unit)
    }
}
