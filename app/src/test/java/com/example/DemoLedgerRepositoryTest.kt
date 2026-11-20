package com.example

import com.example.data.model.DateFormatters
import com.example.data.model.TransactionType
import com.example.data.repository.DemoLedgerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoLedgerRepositoryTest {

    @Test
    fun `initial demo seed provides workspace accounts categories transactions and partial debt`() = runBlocking {
        val repo = DemoLedgerRepository()
        val uid = DemoLedgerRepository.DEMO_USER_ID

        val profile = repo.observeUserProfile(uid).first()
        assertNotNull(profile)
        val wsId = profile!!.activeWorkspaceId!!

        val ws = repo.observeWorkspace(wsId).first()
        assertNotNull(ws)
        assertEquals("INR", ws!!.currencyCode)

        val accounts = repo.observeAccountsWithBalance(wsId, uid).first()
        assertEquals(3, accounts.size)

        val categories = repo.observeCategories(wsId, uid).first()
        assertEquals(8, categories.size)

        val txs = repo.observeTransactions(wsId, uid).first()
        assertTrue(txs.isNotEmpty())

        val debts = repo.observeDebtRecords(wsId, uid).first()
        assertEquals(3, debts.size)
        val partialDebt = debts.first { !it.debt.settled && it.debt.settlements.isNotEmpty() }
        assertEquals(600_000L, partialDebt.debt.effectiveRemainingAmountMinor)
    }

    @Test
    fun `saving transaction and recording partial debt settlement update balances in memory`() = runBlocking {
        val repo = DemoLedgerRepository()
        val uid = DemoLedgerRepository.DEMO_USER_ID
        val wsId = repo.observeUserProfile(uid).first()!!.activeWorkspaceId!!
        val ws = repo.observeWorkspace(wsId).first()!!

        val initialAccounts = repo.observeAccountsWithBalance(wsId, uid).first()
        val cashBefore = initialAccounts.first { it.account.name == "Cash" }.currentBalanceMinor

        // Add ₹500 (50_000 minor) expense from Cash
        val expenseResult = repo.saveTransaction(
            workspace = ws,
            type = TransactionType.EXPENSE,
            amountMinor = 50_000L,
            accountId = initialAccounts.first { it.account.name == "Cash" }.account.id,
            destinationAccountId = null,
            categoryId = "demo_cat_grocery",
            dateMillis = DateFormatters.startOfTodayMillis(),
            note = "Demo milk & bread"
        )
        assertTrue(expenseResult.isSuccess)

        val cashAfterExpense = repo.observeAccountsWithBalance(wsId, uid).first()
            .first { it.account.name == "Cash" }.currentBalanceMinor
        assertEquals(cashBefore - 50_000L, cashAfterExpense)

        // Partial settle ₹2,000 (200_000 minor) of Arjun's ₹6,000 remaining receivable into Cash
        val arjunDebt = repo.observeDebtRecords(wsId, uid).first()
            .first { it.debt.personName == "Arjun Mehta" }.debt
        val settleResult = repo.recordDebtSettlement(
            workspaceId = wsId,
            debt = arjunDebt,
            amountMinor = 200_000L,
            settlementAccountId = initialAccounts.first { it.account.name == "Cash" }.account.id,
            settledDateMillis = DateFormatters.startOfTodayMillis(),
            note = "Second installment"
        )
        assertTrue(settleResult.isSuccess)
        assertFalse(settleResult.getOrThrow())

        val updatedArjun = repo.observeDebtRecords(wsId, uid).first()
            .first { it.debt.personName == "Arjun Mehta" }.debt
        assertEquals(400_000L, updatedArjun.effectiveRemainingAmountMinor)
        assertEquals(2, updatedArjun.settlements.size)

        val cashAfterSettle = repo.observeAccountsWithBalance(wsId, uid).first()
            .first { it.account.name == "Cash" }.currentBalanceMinor
        assertEquals(cashAfterExpense + 200_000L, cashAfterSettle)
    }

    @Test
    fun `leaving and creating workspace updates active workspace cleanly`() = runBlocking {
        val repo = DemoLedgerRepository()
        val uid = DemoLedgerRepository.DEMO_USER_ID

        assertTrue(repo.leaveWorkspace().isSuccess)
        assertNull(repo.observeUserProfile(uid).first()!!.activeWorkspaceId)

        val newWs = repo.createFamilyWorkspace("Vacation Fund", "USD")
        assertTrue(newWs.isSuccess)
        val newId = newWs.getOrThrow()
        assertEquals(newId, repo.observeUserProfile(uid).first()!!.activeWorkspaceId)
        assertEquals("USD", repo.observeWorkspace(newId).first()!!.currencyCode)
        assertEquals(2, repo.observeAccountsWithBalance(newId, uid).first().size)
    }
}
