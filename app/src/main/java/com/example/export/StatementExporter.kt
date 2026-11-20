package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.AccountWithBalance
import com.example.data.model.CurrencyFormatter
import com.example.data.model.CurrencyOption
import com.example.data.model.DateFormatters
import com.example.data.model.DebtDetail
import com.example.data.model.DebtType
import com.example.data.model.MonthlyReportSummary
import com.example.data.model.TransactionType
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class StatementExporter(private val context: Context) {

    private fun exportsDir(): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun safeFileName(ledgerName: String, year: Int, monthZeroBased: Int, ext: String): String {
        val monthLabel = DateFormatters.formatMonthYear(year, monthZeroBased)
            .replace(" ", "_")
        val cleanLedger = ledgerName.replace(Regex("[^A-Za-z0-9_-]"), "_")
            .trim('_')
            .ifEmpty { "KinLedger" }
        return "${cleanLedger}_Statement_${monthLabel}.$ext"
    }

    fun exportMonthlyPdf(
        ledgerName: String,
        report: MonthlyReportSummary,
        accounts: List<AccountWithBalance>,
        currency: CurrencyOption,
        debts: List<DebtDetail> = emptyList()
    ): Uri {
        val file = File(exportsDir(), safeFileName(ledgerName, report.year, report.monthZeroBased, "pdf"))
        val document = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val contentWidth = pageWidth - margin * 2

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#475569")
            textSize = 11f
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 9.5f
        }
        val boldBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 9f
        }
        val tealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0D9488")
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val rosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E11D48")
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
        }
        val cardFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
            style = Paint.Style.FILL
        }
        val accentBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0D9488")
            style = Paint.Style.FILL
        }

        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin

        fun startNextPage() {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin
            canvas.drawText(
                "$ledgerName — ${DateFormatters.formatMonthYear(report.year, report.monthZeroBased)} (Page $pageNumber)",
                margin,
                y + 10f,
                mutedPaint
            )
            y += 26f
            canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
            y += 18f
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - margin) {
                startNextPage()
            }
        }

        canvas.drawRect(margin, y, margin + 36f, y + 4f, accentBarPaint)
        y += 18f
        canvas.drawText(ledgerName, margin, y, titlePaint)
        y += 16f
        val periodLabel = DateFormatters.formatMonthYear(report.year, report.monthZeroBased)
        canvas.drawText("Monthly Financial Statement • $periodLabel", margin, y, subtitlePaint)
        y += 22f

        val boxGap = 10f
        val boxWidth = (contentWidth - boxGap * 2) / 3f
        val boxHeight = 56f
        val summaryItems = listOf(
            Triple("Total Income", CurrencyFormatter.formatMinor(report.totalIncomeMinor, currency), tealPaint),
            Triple("Total Expense", CurrencyFormatter.formatMinor(report.totalExpenseMinor, currency), rosePaint),
            Triple(
                "Net Cashflow",
                CurrencyFormatter.formatMinor(report.netCashflowMinor, currency, includeSign = true),
                if (report.netCashflowMinor >= 0) tealPaint else rosePaint
            )
        )
        summaryItems.forEachIndexed { idx, (label, value, valPaint) ->
            val left = margin + idx * (boxWidth + boxGap)
            val rect = RectF(left, y, left + boxWidth, y + boxHeight)
            canvas.drawRoundRect(rect, 8f, 8f, cardFillPaint)
            canvas.drawRoundRect(rect, 8f, 8f, dividerPaint.apply { style = Paint.Style.STROKE })
            dividerPaint.style = Paint.Style.FILL
            canvas.drawText(label, left + 12f, y + 20f, mutedPaint)
            canvas.drawText(value, left + 12f, y + 42f, valPaint)
        }
        y += boxHeight + 24f

        ensureSpace(70f)
        canvas.drawText("Current Account Balances", margin, y, sectionPaint)
        y += 14f
        accounts.forEach { acct ->
            ensureSpace(22f)
            canvas.drawText(acct.account.name, margin + 4f, y + 10f, bodyPaint)
            val bal = CurrencyFormatter.formatMinor(acct.currentBalanceMinor, currency)
            val balWidth = boldBodyPaint.measureText(bal)
            canvas.drawText(bal, pageWidth - margin - balWidth, y + 10f, boldBodyPaint)
            y += 18f
            canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
            y += 6f
        }
        y += 14f

        if (report.expenseSlices.isNotEmpty()) {
            ensureSpace(50f)
            canvas.drawText("Expense Breakdown by Category", margin, y, sectionPaint)
            y += 16f
            report.expenseSlices.forEach { slice ->
                ensureSpace(26f)
                val pctText = String.format(Locale.US, "%.1f%%", slice.percentage * 100f)
                canvas.drawText("${slice.category.name} ($pctText)", margin + 4f, y + 10f, bodyPaint)
                val amt = CurrencyFormatter.formatMinor(slice.amountMinor, currency)
                val amtWidth = boldBodyPaint.measureText(amt)
                canvas.drawText(amt, pageWidth - margin - amtWidth, y + 10f, boldBodyPaint)
                y += 16f
                val barWidth = contentWidth * slice.percentage.coerceIn(0.02f, 1f)
                val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = slice.category.colorArgb.toInt()
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(RectF(margin, y, margin + contentWidth, y + 4f), 2f, 2f, cardFillPaint)
                canvas.drawRoundRect(RectF(margin, y, margin + barWidth, y + 4f), 2f, 2f, barPaint)
                y += 12f
            }
            y += 14f
        }

        ensureSpace(60f)
        canvas.drawText("Itemized Entries (${report.transactions.size})", margin, y, sectionPaint)
        y += 14f

        fun drawTableHeader(targetCanvas: Canvas, topY: Float) {
            targetCanvas.drawRect(margin, topY, pageWidth - margin, topY + 22f, cardFillPaint)
            targetCanvas.drawText("Date", margin + 6f, topY + 14f, boldBodyPaint)
            targetCanvas.drawText("Category / Transfer", margin + 72f, topY + 14f, boldBodyPaint)
            targetCanvas.drawText("Account", margin + 215f, topY + 14f, boldBodyPaint)
            targetCanvas.drawText("By / Note", margin + 300f, topY + 14f, boldBodyPaint)
            val amtHdr = "Amount"
            targetCanvas.drawText(amtHdr, pageWidth - margin - 6f - boldBodyPaint.measureText(amtHdr), topY + 14f, boldBodyPaint)
        }

        drawTableHeader(canvas, y)
        y += 28f

        if (report.transactions.isEmpty()) {
            canvas.drawText("No transactions recorded for this month.", margin + 6f, y + 10f, mutedPaint)
        } else {
            report.transactions.forEach { detail ->
                if (y + 24f > pageHeight - margin) {
                    startNextPage()
                    drawTableHeader(canvas, y)
                    y += 28f
                }
                val tx = detail.transaction
                val txType = tx.transactionType
                val dateStr = DateFormatters.formatFullDate(tx.dateMillis)
                val catName = when (txType) {
                    TransactionType.TRANSFER -> "Transfer"
                    else -> (detail.category?.name ?: "Uncategorized")
                }.take(20)
                val acctName = when (txType) {
                    TransactionType.TRANSFER -> "${detail.account?.name ?: "?"}→${detail.destinationAccount?.name ?: "?"}"
                    else -> detail.account?.name ?: "Account"
                }.take(14)
                val meta = listOfNotNull(
                    tx.createdByName.takeIf { it.isNotBlank() },
                    tx.note.takeIf { it.isNotBlank() }
                ).joinToString(" • ").ifEmpty { "—" }.take(22)

                val sign = when (txType) {
                    TransactionType.INCOME -> "+"
                    TransactionType.EXPENSE -> "-"
                    TransactionType.TRANSFER -> "⇄ "
                }
                val amtStr = "$sign${CurrencyFormatter.formatMinor(tx.amountMinor, currency)}"
                val amtColor = when (txType) {
                    TransactionType.INCOME -> "#0D9488"
                    TransactionType.EXPENSE -> "#E11D48"
                    TransactionType.TRANSFER -> "#0284C7"
                }
                val amtPaint = Paint(boldBodyPaint).apply { color = Color.parseColor(amtColor) }

                canvas.drawText(dateStr, margin + 6f, y + 10f, bodyPaint)
                canvas.drawText(catName, margin + 72f, y + 10f, bodyPaint)
                canvas.drawText(acctName, margin + 215f, y + 10f, bodyPaint)
                canvas.drawText(meta, margin + 300f, y + 10f, mutedPaint)
                canvas.drawText(amtStr, pageWidth - margin - 6f - amtPaint.measureText(amtStr), y + 10f, amtPaint)
                y += 18f
                canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
                y += 6f
            }
        }

        if (debts.isNotEmpty()) {
            y += 16f
            ensureSpace(64f)
            canvas.drawText("Lend & Borrow Summary (${debts.size})", margin, y, sectionPaint)
            y += 14f
            debts.forEach { detail ->
                ensureSpace(34f)
                val debt = detail.debt
                val typeLabel = if (debt.debtType == DebtType.RECEIVABLE) "Receivable" else "Payable"
                val orig = CurrencyFormatter.formatMinor(debt.effectiveOriginalAmountMinor, currency)
                val paid = CurrencyFormatter.formatMinor(debt.effectiveSettledAmountMinor, currency)
                val rem = CurrencyFormatter.formatMinor(debt.effectiveRemainingAmountMinor, currency)
                val statusStr = if (debt.settled) "Settled" else "Remaining: $rem"
                canvas.drawText("${debt.personName} ($typeLabel)", margin + 4f, y + 10f, boldBodyPaint)
                val rightSummary = "Paid $paid / $orig • $statusStr"
                canvas.drawText(
                    rightSummary,
                    pageWidth - margin - bodyPaint.measureText(rightSummary),
                    y + 10f,
                    bodyPaint
                )
                y += 16f
                detail.settlementDetails.forEach { s ->
                    ensureSpace(16f)
                    val sDate = DateFormatters.formatFullDate(s.entry.dateMillis)
                    val sAcct = s.account?.name ?: "Account"
                    val sAmt = CurrencyFormatter.formatMinor(s.entry.amountMinor, currency)
                    val sNote = listOfNotNull(
                        s.entry.settledByName.takeIf { it.isNotBlank() },
                        s.entry.note.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                    val line = "  ↳ $sDate via $sAcct: $sAmt" + if (sNote.isNotBlank()) " ($sNote)" else ""
                    canvas.drawText(line.take(90), margin + 10f, y + 9f, mutedPaint)
                    y += 14f
                }
                canvas.drawLine(margin, y, pageWidth - margin, y, dividerPaint)
                y += 6f
            }
        }

        document.finishPage(page)
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun exportMonthlyCsv(
        ledgerName: String,
        report: MonthlyReportSummary,
        accounts: List<AccountWithBalance>,
        currency: CurrencyOption,
        debts: List<DebtDetail> = emptyList()
    ): Uri {
        val file = File(exportsDir(), safeFileName(ledgerName, report.year, report.monthZeroBased, "csv"))
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.appendLine("KinLedger Monthly Statement")
        sb.appendLine("Ledger,${escapeCsv(ledgerName)}")
        sb.appendLine("Period,${escapeCsv(DateFormatters.formatMonthYear(report.year, report.monthZeroBased))}")
        sb.appendLine("Currency,${escapeCsv(currency.code)}")
        sb.appendLine()
        sb.appendLine("Summary,Amount")
        sb.appendLine("Total Income,${escapeCsv(CurrencyFormatter.formatMinor(report.totalIncomeMinor, currency, forceDecimals = true))}")
        sb.appendLine("Total Expense,${escapeCsv(CurrencyFormatter.formatMinor(report.totalExpenseMinor, currency, forceDecimals = true))}")
        sb.appendLine("Net Cashflow,${escapeCsv(CurrencyFormatter.formatMinor(report.netCashflowMinor, currency, includeSign = true, forceDecimals = true))}")
        sb.appendLine()
        sb.appendLine("Account,Current Balance")
        accounts.forEach { acct ->
            sb.appendLine("${escapeCsv(acct.account.name)},${escapeCsv(CurrencyFormatter.formatMinor(acct.currentBalanceMinor, currency, forceDecimals = true))}")
        }
        sb.appendLine()
        sb.appendLine("Category Breakdown,Amount,Share (%)")
        report.expenseSlices.forEach { slice ->
            val pct = String.format(Locale.US, "%.1f%%", slice.percentage * 100f)
            sb.appendLine("${escapeCsv(slice.category.name)},${escapeCsv(CurrencyFormatter.formatMinor(slice.amountMinor, currency, forceDecimals = true))},$pct")
        }
        sb.appendLine()
        sb.appendLine("Date,Type,Category,Account,Destination Account,Amount,Logged By,Note")
        report.transactions.forEach { detail ->
            val tx = detail.transaction
            val txType = tx.transactionType
            val date = DateFormatters.formatIsoDate(tx.dateMillis)
            val category = if (txType == TransactionType.TRANSFER) "Account Transfer" else (detail.category?.name ?: "Uncategorized")
            val account = detail.account?.name ?: "Account"
            val destAccount = detail.destinationAccount?.name ?: ""
            val signedMinor = when (txType) {
                TransactionType.INCOME -> tx.amountMinor
                TransactionType.EXPENSE -> -tx.amountMinor
                TransactionType.TRANSFER -> tx.amountMinor
            }
            val amount = CurrencyFormatter.minorToEditableString(signedMinor)
            sb.appendLine(
                "${escapeCsv(date)},${escapeCsv(txType.name)},${escapeCsv(category)},${escapeCsv(account)},${escapeCsv(destAccount)},${escapeCsv(amount)},${escapeCsv(tx.createdByName)},${escapeCsv(tx.note)}"
            )
        }

        if (debts.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Lend & Borrow Summary")
            sb.appendLine("Person,Type,Original Amount,Settled Amount,Remaining Amount,Status,Source Account,Installments")
            debts.forEach { detail ->
                val debt = detail.debt
                val orig = CurrencyFormatter.minorToEditableString(debt.effectiveOriginalAmountMinor)
                val paid = CurrencyFormatter.minorToEditableString(debt.effectiveSettledAmountMinor)
                val rem = CurrencyFormatter.minorToEditableString(debt.effectiveRemainingAmountMinor)
                val status = if (debt.settled) "Settled" else "Pending"
                val srcAcct = detail.sourceAccount?.name ?: "Account"
                val history = detail.settlementDetails.joinToString(" | ") { s ->
                    val dt = DateFormatters.formatIsoDate(s.entry.dateMillis)
                    val amt = CurrencyFormatter.minorToEditableString(s.entry.amountMinor)
                    val acct = s.account?.name ?: "Account"
                    "$dt $amt via $acct"
                }
                sb.appendLine(
                    "${escapeCsv(debt.personName)},${escapeCsv(debt.debtType.name)},${escapeCsv(orig)},${escapeCsv(paid)},${escapeCsv(rem)},${escapeCsv(status)},${escapeCsv(srcAcct)},${escapeCsv(history)}"
                )
            }
        }

        file.writeText(sb.toString(), Charsets.UTF_8)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun launchShareSheet(uri: Uri, mimeType: String, subject: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, subject).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
