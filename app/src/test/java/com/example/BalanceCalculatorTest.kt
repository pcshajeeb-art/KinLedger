package com.example

import com.example.data.model.AccountModel
import com.example.data.model.CategoryModel
import com.example.data.model.CurrencyFormatter
import com.example.data.model.CurrencyOption
import com.example.data.model.DebtRecordModel
import com.example.data.model.DebtSettlementEntry
import com.example.data.model.DebtType
import com.example.data.model.TransactionDetail
import com.example.data.model.TransactionModel
import com.example.data.model.TransactionType
import com.example.data.repository.BalanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class BalanceCalculatorTest {

    private val now = 1_700_000_000_000L
    private val wsId = "ws_family_1"

    private val cash = AccountModel(
        id = "acct_cash",
        workspaceId = wsId,
        name = "Cash",
        initialBalanceMinor = 100_000L, // 1,000.00
        iconKey = "wallet",
        colorArgb = 0xFF0D9488L,
        defaultAccount = true
    )

    private val bank = AccountModel(
        id = "acct_bank",
        workspaceId = wsId,
        name = "Bank",
        initialBalanceMinor = 2_000_000L, // 20,000.00
        iconKey = "bank",
        colorArgb = 0xFF0F766EL,
        defaultAccount = true
    )

    @Test
    fun `income expense transfer and receivable settlement calculate exact account balances`() {
        val transactions = listOf(
            // NRI Remittance +10,000 into Bank
            TransactionModel(
                id = "t1",
                workspaceId = wsId,
                type = TransactionType.INCOME.name,
                amountMinor = 1_000_000L,
                accountId = bank.id,
                categoryId = "cat_remit",
                dateMillis = now,
                note = "Remittance"
            ),
            // Grocery -1,500 from Cash
            TransactionModel(
                id = "t2",
                workspaceId = wsId,
                type = TransactionType.EXPENSE.name,
                amountMinor = 150_000L,
                accountId = cash.id,
                categoryId = "cat_groc",
                dateMillis = now,
                note = "Veggies"
            ),
            // ATM Transfer 2,500 from Bank -> Cash
            TransactionModel(
                id = "t3",
                workspaceId = wsId,
                type = TransactionType.TRANSFER.name,
                amountMinor = 250_000L,
                accountId = bank.id,
                destinationAccountId = cash.id,
                categoryId = null,
                dateMillis = now,
                note = "ATM withdrawal"
            )
        )

        val debts = listOf(
            // Lent 5,000 from Bank (unsettled -> Bank -5,000)
            DebtRecordModel(
                id = "d1",
                workspaceId = wsId,
                type = DebtType.RECEIVABLE.name,
                personName = "Ravi",
                amountMinor = 500_000L,
                sourceAccountId = bank.id,
                dateMillis = now,
                settled = false
            ),
            // Lent 2,000 from Bank, settled into Cash -> Bank -2,000, Cash +2,000
            DebtRecordModel(
                id = "d2",
                workspaceId = wsId,
                type = DebtType.RECEIVABLE.name,
                personName = "Meena",
                amountMinor = 200_000L,
                sourceAccountId = bank.id,
                dateMillis = now,
                settled = true,
                settledDateMillis = now,
                settlementAccountId = cash.id
            ),
            // Borrowed 3,000 into Cash (unsettled -> Cash +3,000)
            DebtRecordModel(
                id = "d3",
                workspaceId = wsId,
                type = DebtType.PAYABLE.name,
                personName = "Suresh",
                amountMinor = 300_000L,
                sourceAccountId = cash.id,
                dateMillis = now,
                settled = false
            )
        )

        val balances = BalanceCalculator.computeAccountBalances(listOf(cash, bank), transactions, debts)
        val cashBal = balances.first { it.account.id == cash.id }.currentBalanceMinor
        val bankBal = balances.first { it.account.id == bank.id }.currentBalanceMinor

        // Cash: 1,000 - 1,500 + 2,500 (ATM in) + 2,000 (settled receivable) + 3,000 (borrowed) = 7,000.00 -> 700,000 minor
        assertEquals(700_000L, cashBal)
        // Bank: 20,000 + 10,000 - 2,500 (ATM out) - 5,000 - 2,000 = 20,500.00 -> 2,050,000 minor
        assertEquals(2_050_000L, bankBal)
    }

    @Test
    fun `monthly report aggregates income and expense while excluding contra transfers from totals`() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.NOVEMBER)
            set(Calendar.DAY_OF_MONTH, 10)
        }
        val txDate = cal.timeInMillis

        val grocery = CategoryModel("c1", wsId, "Grocery & Household", TransactionType.EXPENSE.name, "shopping_cart", 0xFF0D9488L)
        val medical = CategoryModel("c2", wsId, "Hospital & Medical Expense", TransactionType.EXPENSE.name, "medical_services", 0xFFEF4444L)
        val remittance = CategoryModel("c3", wsId, "NRI Remittance", TransactionType.INCOME.name, "currency_exchange", 0xFF0D9488L)

        val details = listOf(
            TransactionDetail(
                TransactionModel("t1", wsId, TransactionType.INCOME.name, 5_000_000L, bank.id, null, remittance.id, txDate),
                bank, null, remittance
            ),
            TransactionDetail(
                TransactionModel("t2", wsId, TransactionType.EXPENSE.name, 600_000L, cash.id, null, grocery.id, txDate),
                cash, null, grocery
            ),
            TransactionDetail(
                TransactionModel("t3", wsId, TransactionType.EXPENSE.name, 400_000L, bank.id, null, medical.id, txDate),
                bank, null, medical
            ),
            // Large contra transfer should appear in itemized transactions, NOT in monthly income/expense totals
            TransactionDetail(
                TransactionModel("t4", wsId, TransactionType.TRANSFER.name, 1_500_000L, bank.id, cash.id, null, txDate, "ATM Cash"),
                bank, cash, null
            )
        )

        val report = BalanceCalculator.computeMonthlyReport(2026, Calendar.NOVEMBER, details, listOf(grocery, medical, remittance))
        assertEquals(5_000_000L, report.totalIncomeMinor)
        assertEquals(1_000_000L, report.totalExpenseMinor)
        assertEquals(4_000_000L, report.netCashflowMinor)
        assertEquals(2, report.expenseSlices.size)
        assertEquals(4, report.transactions.size)
        assertEquals(0.6f, report.expenseSlices[0].percentage, 0.001f)
        assertEquals(0.4f, report.expenseSlices[1].percentage, 0.001f)
    }

    @Test
    fun `indian currency formatting works accurately`() {
        val inr = CurrencyOption.fromCode("INR")
        assertEquals("₹1,25,000", CurrencyFormatter.formatMinor(12_500_000L, inr))
        assertEquals("₹450.50", CurrencyFormatter.formatMinor(45_050L, inr))
    }

    @Test
    fun `partial debt settlements update multiple accounts and remaining balances accurately`() {
        // Lent 10,000 from Bank; received 3,000 in Cash and 2,500 in Bank (still open, remaining 4,500)
        val partialReceivable = DebtRecordModel(
            id = "d_part_rec",
            workspaceId = wsId,
            type = DebtType.RECEIVABLE.name,
            personName = "Kiran",
            amountMinor = 1_000_000L,
            settledAmountMinor = 550_000L,
            remainingAmountMinor = 450_000L,
            sourceAccountId = bank.id,
            dateMillis = now,
            settled = false,
            settlements = listOf(
                DebtSettlementEntry(id = "s1", amountMinor = 300_000L, dateMillis = now, accountId = cash.id, note = "Cash part"),
                DebtSettlementEntry(id = "s2", amountMinor = 250_000L, dateMillis = now, accountId = bank.id, note = "UPI part")
            )
        )

        // Borrowed 6,000 into Cash; repaid 2,000 from Bank and 4,000 from Cash (now fully settled)
        val fullInstallmentPayable = DebtRecordModel(
            id = "d_part_pay",
            workspaceId = wsId,
            type = DebtType.PAYABLE.name,
            personName = "Vikram",
            amountMinor = 600_000L,
            settledAmountMinor = 600_000L,
            remainingAmountMinor = 0L,
            sourceAccountId = cash.id,
            dateMillis = now,
            settled = true,
            settledDateMillis = now,
            settlementAccountId = cash.id,
            settlements = listOf(
                DebtSettlementEntry(id = "p1", amountMinor = 200_000L, dateMillis = now, accountId = bank.id, note = "NEFT 1"),
                DebtSettlementEntry(id = "p2", amountMinor = 400_000L, dateMillis = now, accountId = cash.id, note = "Final cash")
            )
        )

        assertEquals(550_000L, partialReceivable.effectiveSettledAmountMinor)
        assertEquals(450_000L, partialReceivable.effectiveRemainingAmountMinor)
        assertEquals(600_000L, fullInstallmentPayable.effectiveSettledAmountMinor)
        assertEquals(0L, fullInstallmentPayable.effectiveRemainingAmountMinor)

        val balances = BalanceCalculator.computeAccountBalances(
            accounts = listOf(cash, bank),
            transactions = emptyList(),
            debts = listOf(partialReceivable, fullInstallmentPayable)
        )
        val cashBal = balances.first { it.account.id == cash.id }.currentBalanceMinor
        val bankBal = balances.first { it.account.id == bank.id }.currentBalanceMinor

        // Cash starts at 100,000: +300,000 (rec part 1) +600,000 (borrowed) -400,000 (repaid part 2) = 600,000
        assertEquals(600_000L, cashBal)
        // Bank starts at 2,000,000: -1,000,000 (lent) +250,000 (rec part 2) -200,000 (repaid part 1) = 1,050,000
        assertEquals(1_050_000L, bankBal)
    }
}
