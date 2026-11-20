package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AccountModel
import com.example.data.model.CurrencyFormatter
import com.example.data.model.CurrencyOption
import com.example.data.model.DebtRecordModel
import com.example.data.model.DebtType
import com.example.data.model.TransactionModel
import com.example.data.model.TransactionType
import com.example.data.repository.BalanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app_name and default_web_client_id from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        val webClientId = context.getString(R.string.default_web_client_id)
        assertEquals("KinLedger", appName)
        assertEquals("582390237671-77fbggb0sn9pu2vsr8ub97jjeqf7l49e.apps.googleusercontent.com", webClientId)
    }

    @Test
    fun `account transfer and debt settlement calculate exact balances`() {
        val now = 1_700_000_000_000L
        val wsId = "ws_test"
        val cash = AccountModel(id = "cash", workspaceId = wsId, name = "Cash", initialBalanceMinor = 0L, defaultAccount = true)
        val bank = AccountModel(id = "bank", workspaceId = wsId, name = "Bank", initialBalanceMinor = 0L, defaultAccount = true)

        val txs = listOf(
            TransactionModel(
                id = "tx1",
                workspaceId = wsId,
                type = TransactionType.INCOME.name,
                amountMinor = 2_500_000L, // +25,000 into Bank
                accountId = bank.id,
                categoryId = "remittance",
                dateMillis = now,
                createdByName = "Alice"
            ),
            TransactionModel(
                id = "tx2",
                workspaceId = wsId,
                type = TransactionType.TRANSFER.name,
                amountMinor = 500_000L, // 5,000 Bank -> Cash
                accountId = bank.id,
                destinationAccountId = cash.id,
                dateMillis = now,
                createdByName = "Bob"
            ),
            TransactionModel(
                id = "tx3",
                workspaceId = wsId,
                type = TransactionType.EXPENSE.name,
                amountMinor = 120_000L, // -1,200 from Cash
                accountId = cash.id,
                categoryId = "grocery",
                dateMillis = now,
                createdByName = "Bob"
            )
        )

        val debts = listOf(
            DebtRecordModel(
                id = "d1",
                workspaceId = wsId,
                type = DebtType.RECEIVABLE.name,
                personName = "Arjun",
                amountMinor = 300_000L, // -3,000 from Bank, settled into Cash -> +3,000 Cash
                sourceAccountId = bank.id,
                dateMillis = now,
                settled = true,
                settledDateMillis = now,
                settlementAccountId = cash.id
            )
        )

        val balances = BalanceCalculator.computeAccountBalances(listOf(cash, bank), txs, debts)
        val cashBal = balances.first { it.account.id == cash.id }.currentBalanceMinor
        val bankBal = balances.first { it.account.id == bank.id }.currentBalanceMinor

        // Bank: 25,000 - 5,000 (transfer) - 3,000 (lent) = 17,000.00 (1,700,000 minor)
        assertEquals(1_700_000L, bankBal)
        // Cash: +5,000 (transfer) - 1,200 (expense) + 3,000 (settled receivable) = 6,800.00 (680,000 minor)
        assertEquals(680_000L, cashBal)

        val formatted = CurrencyFormatter.formatMinor(bankBal, CurrencyOption.fromCode("INR"))
        assertEquals("₹17,000", formatted)
    }
}
