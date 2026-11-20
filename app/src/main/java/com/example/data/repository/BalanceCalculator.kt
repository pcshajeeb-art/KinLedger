package com.example.data.repository

import com.example.data.model.AccountModel
import com.example.data.model.AccountWithBalance
import com.example.data.model.CategoryExpenseSlice
import com.example.data.model.CategoryModel
import com.example.data.model.DateFormatters
import com.example.data.model.DebtRecordModel
import com.example.data.model.DebtType
import com.example.data.model.MonthlyBarPoint
import com.example.data.model.MonthlyReportSummary
import com.example.data.model.TransactionDetail
import com.example.data.model.TransactionModel
import com.example.data.model.TransactionType
import java.util.Calendar

/**
 * Pure domain calculation engine for real-time account balances and monthly reporting.
 *
 * Balance rules:
 * - Starts from account.initialBalanceMinor
 * - INCOME transaction on account -> +amount
 * - EXPENSE transaction on account -> -amount
 * - TRANSFER transaction -> -amount from accountId (source) and +amount to destinationAccountId
 *   (Excluded from monthly Income & Expense analytics!)
 * - RECEIVABLE (lent from sourceAccountId) -> -amount on sourceAccountId
 *   - When settled into settlementAccountId -> +amount on settlementAccountId
 * - PAYABLE (borrowed into sourceAccountId) -> +amount on sourceAccountId
 *   - When settled from settlementAccountId -> -amount on settlementAccountId
 */
object BalanceCalculator {
    fun computeAccountBalances(
        accounts: List<AccountModel>,
        transactions: List<TransactionModel>,
        debts: List<DebtRecordModel>
    ): List<AccountWithBalance> {
        return accounts.map { account ->
            var inflow = 0L
            var outflow = 0L

            for (tx in transactions) {
                when (tx.transactionType) {
                    TransactionType.INCOME -> {
                        if (tx.accountId == account.id) inflow += tx.amountMinor
                    }
                    TransactionType.EXPENSE -> {
                        if (tx.accountId == account.id) outflow += tx.amountMinor
                    }
                    TransactionType.TRANSFER -> {
                        if (tx.accountId == account.id) outflow += tx.amountMinor
                        if (tx.destinationAccountId == account.id) inflow += tx.amountMinor
                    }
                }
            }

            for (debt in debts) {
                if (debt.sourceAccountId == account.id) {
                    when (debt.debtType) {
                        DebtType.RECEIVABLE -> outflow += debt.amountMinor
                        DebtType.PAYABLE -> inflow += debt.amountMinor
                    }
                }
                if (debt.settlements.isNotEmpty()) {
                    for (entry in debt.settlements) {
                        if (entry.accountId == account.id) {
                            when (debt.debtType) {
                                DebtType.RECEIVABLE -> inflow += entry.amountMinor
                                DebtType.PAYABLE -> outflow += entry.amountMinor
                            }
                        }
                    }
                } else if (debt.settled) {
                    val settleAccountId = debt.settlementAccountId ?: debt.sourceAccountId
                    if (settleAccountId == account.id) {
                        when (debt.debtType) {
                            DebtType.RECEIVABLE -> inflow += debt.amountMinor
                            DebtType.PAYABLE -> outflow += debt.amountMinor
                        }
                    }
                }
            }

            AccountWithBalance(
                account = account,
                currentBalanceMinor = account.initialBalanceMinor + inflow - outflow,
                totalInflowMinor = inflow,
                totalOutflowMinor = outflow
            )
        }
    }

    fun computeMonthlyReport(
        year: Int,
        monthZeroBased: Int,
        allTransactions: List<TransactionDetail>,
        categories: List<CategoryModel>
    ): MonthlyReportSummary {
        val (startMillis, endMillis) = DateFormatters.monthBoundsMillis(year, monthZeroBased)
        val monthTxs = allTransactions.filter {
            it.transaction.dateMillis in startMillis..endMillis
        }

        // Transfers (contra entries) are strictly excluded from Income & Expense totals
        val totalIncome = monthTxs
            .filter { it.transaction.transactionType == TransactionType.INCOME }
            .sumOf { it.transaction.amountMinor }
        val totalExpense = monthTxs
            .filter { it.transaction.transactionType == TransactionType.EXPENSE }
            .sumOf { it.transaction.amountMinor }

        val categoryMap = categories.associateBy { it.id }
        val groupedExpenses = monthTxs
            .filter { it.transaction.transactionType == TransactionType.EXPENSE }
            .groupBy { it.transaction.categoryId.orEmpty() }

        val slices = groupedExpenses.mapNotNull { (catId, txs) ->
            val category = categoryMap[catId] ?: return@mapNotNull null
            val sum = txs.sumOf { it.transaction.amountMinor }
            val pct = if (totalExpense > 0L) (sum.toFloat() / totalExpense.toFloat()) else 0f
            CategoryExpenseSlice(
                category = category,
                amountMinor = sum,
                percentage = pct
            )
        }.sortedByDescending { it.amountMinor }

        val trendPoints = mutableListOf<MonthlyBarPoint>()
        val cursor = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, -5)
        }
        repeat(6) {
            val y = cursor.get(Calendar.YEAR)
            val m = cursor.get(Calendar.MONTH)
            val (s, e) = DateFormatters.monthBoundsMillis(y, m)
            val mTxs = allTransactions.filter { it.transaction.dateMillis in s..e }
            val inc = mTxs.filter { it.transaction.transactionType == TransactionType.INCOME }
                .sumOf { it.transaction.amountMinor }
            val exp = mTxs.filter { it.transaction.transactionType == TransactionType.EXPENSE }
                .sumOf { it.transaction.amountMinor }
            trendPoints.add(
                MonthlyBarPoint(
                    year = y,
                    monthZeroBased = m,
                    label = DateFormatters.formatShortMonth(y, m),
                    incomeMinor = inc,
                    expenseMinor = exp
                )
            )
            cursor.add(Calendar.MONTH, 1)
        }

        return MonthlyReportSummary(
            year = year,
            monthZeroBased = monthZeroBased,
            totalIncomeMinor = totalIncome,
            totalExpenseMinor = totalExpense,
            netCashflowMinor = totalIncome - totalExpense,
            expenseSlices = slices,
            trendPoints = trendPoints,
            transactions = monthTxs
        )
    }
}
