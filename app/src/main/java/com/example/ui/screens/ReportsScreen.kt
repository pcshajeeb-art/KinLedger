package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CategoryExpenseSlice
import com.example.data.model.CurrencyFormatter
import com.example.data.model.CurrencyOption
import com.example.data.model.DateFormatters
import com.example.data.model.IconRegistry
import com.example.data.model.MonthlyBarPoint
import com.example.ui.KinLedgerUiState
import com.example.ui.components.EmptyLedgerState
import com.example.ui.components.GlassCard
import com.example.ui.components.TransactionRow
import com.example.ui.theme.LocalLedgerColors
import java.util.Locale
import kotlin.math.max

@Composable
fun ReportsScreen(
    state: KinLedgerUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectPeriod: (year: Int, monthZeroBased: Int) -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ledgerColors = LocalLedgerColors.current
    val report = state.monthlyReport
    var showMonthPicker by remember { mutableStateOf(false) }
    var selectedSliceCategoryId by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Statements & Analytics",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Month-wise insights and one-tap PDF / Excel sharing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Month & Year Selector
            item {
                GlassCard(
                    cornerRadius = 18.dp,
                    shadowElevation = 8.dp,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = onPreviousMonth,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("report_prev_month")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "Previous month"
                            )
                        }

                        Surface(
                            onClick = { showMonthPicker = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (ledgerColors.isDark) 0.16f else 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("report_month_picker_trigger")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = DateFormatters.formatMonthYear(
                                        state.reportPeriod.year,
                                        state.reportPeriod.monthZeroBased
                                    ),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }

                        IconButton(
                            onClick = onNextMonth,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("report_next_month")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Next month"
                            )
                        }
                    }
                }
            }

            // Export & Share Action Card
            item {
                GlassCard(
                    cornerRadius = 20.dp,
                    glowColor = MaterialTheme.colorScheme.primary,
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Text(
                        text = "Export & Share Statement",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Share directly to WhatsApp, Gmail, or save to Drive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onExportPdf,
                            enabled = !state.isExporting,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_pdf_button")
                        ) {
                            if (state.isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share PDF")
                        }

                        OutlinedButton(
                            onClick = onExportCsv,
                            enabled = !state.isExporting,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ledgerColors.border),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_csv_button")
                        ) {
                            Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Excel (.csv)")
                        }
                    }
                }
            }

            if (report != null) {
                // Monthly KPI Summary Strip
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        GlassCard(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 18.dp,
                            glowColor = ledgerColors.income,
                            tintColor = ledgerColors.income,
                            shadowElevation = 8.dp,
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Text(
                                text = "Income",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = CurrencyFormatter.formatMinor(report.totalIncomeMinor, state.currency),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ledgerColors.income,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        GlassCard(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 18.dp,
                            glowColor = ledgerColors.expense,
                            tintColor = ledgerColors.expense,
                            shadowElevation = 8.dp,
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Text(
                                text = "Expense",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = CurrencyFormatter.formatMinor(report.totalExpenseMinor, state.currency),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ledgerColors.expense,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val netAccent = if (report.netCashflowMinor >= 0) ledgerColors.income else ledgerColors.expense
                        GlassCard(
                            modifier = Modifier.weight(1f),
                            cornerRadius = 18.dp,
                            glowColor = netAccent,
                            tintColor = netAccent,
                            shadowElevation = 8.dp,
                            contentPadding = PaddingValues(12.dp)
                        ) {
                            Text(
                                text = "Net",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = CurrencyFormatter.formatMinor(
                                    report.netCashflowMinor,
                                    state.currency,
                                    includeSign = true
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = netAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Category Expense Donut Chart
                item {
                    GlassCard(
                        cornerRadius = 20.dp,
                        glowColor = MaterialTheme.colorScheme.primary,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Text(
                            text = "Expense Breakdown",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (report.expenseSlices.isEmpty()) {
                            Text(
                                text = "No expenses recorded in ${DateFormatters.formatMonthYear(report.year, report.monthZeroBased)}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val activeSlice = report.expenseSlices.firstOrNull {
                                it.category.id == selectedSliceCategoryId
                            }
                            ExpenseDonutChart(
                                slices = report.expenseSlices,
                                selectedSlice = activeSlice,
                                totalExpenseMinor = report.totalExpenseMinor,
                                currency = state.currency
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                report.expenseSlices.forEach { slice ->
                                    val sliceColor = IconRegistry.colorFromLong(slice.category.colorArgb)
                                    val isSelected = slice.category.id == selectedSliceCategoryId
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isSelected) sliceColor.copy(alpha = 0.14f)
                                                else Color.Transparent
                                            )
                                            .clickable {
                                                selectedSliceCategoryId =
                                                    if (isSelected) null else slice.category.id
                                            }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(sliceColor)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = slice.category.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = String.format(Locale.US, "%.1f%%", slice.percentage * 100f),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = ledgerColors.mutedText
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = CurrencyFormatter.formatMinor(slice.amountMinor, state.currency),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { slice.percentage.coerceIn(0f, 1f) },
                                            color = sliceColor,
                                            trackColor = if (ledgerColors.isDark) {
                                                Color.White.copy(alpha = 0.10f)
                                            } else {
                                                Color.Black.copy(alpha = 0.08f)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 6-Month Income vs Expense Bar Chart
                item {
                    GlassCard(
                        cornerRadius = 20.dp,
                        glowColor = ledgerColors.transfer,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "6-Month Income vs. Expense",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                LegendDot(color = ledgerColors.income, label = "In")
                                LegendDot(color = ledgerColors.expense, label = "Out")
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        IncomeExpenseBarChart(
                            points = report.trendPoints,
                            incomeColor = ledgerColors.income,
                            expenseColor = ledgerColors.expense,
                            gridColor = ledgerColors.border
                        )
                    }
                }

                // Month's Transactions Preview
                item {
                    Text(
                        text = "Month Transactions (${report.transactions.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                if (report.transactions.isEmpty()) {
                    item {
                        EmptyLedgerState(
                            title = "No entries this month",
                            subtitle = "Switch months above or add an entry from the Home tab."
                        )
                    }
                } else {
                    items(report.transactions, key = { it.transaction.id }) { detail ->
                        TransactionRow(
                            detail = detail,
                            currency = state.currency,
                            onClick = {}
                        )
                    }
                }
            }
        }
    }

    if (showMonthPicker) {
        MonthYearPickerDialog(
            initialYear = state.reportPeriod.year,
            initialMonthZeroBased = state.reportPeriod.monthZeroBased,
            onDismiss = { showMonthPicker = false },
            onConfirm = { y, m ->
                onSelectPeriod(y, m)
                showMonthPicker = false
            }
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExpenseDonutChart(
    slices: List<CategoryExpenseSlice>,
    selectedSlice: CategoryExpenseSlice?,
    totalExpenseMinor: Long,
    currency: CurrencyOption
) {
    val ledgerColors = LocalLedgerColors.current
    val sweepProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(stiffness = 220f),
        label = "donut_sweep"
    )
    val trackColor = if (ledgerColors.isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Canvas(modifier = Modifier.size(176.dp)) {
            val strokeWidth = 28.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )

            var startAngle = -90f
            val gapDegrees = if (slices.size > 1) 2.5f else 0f
            slices.forEach { slice ->
                val rawSweep = slice.percentage * 360f * sweepProgress
                val sweep = (rawSweep - gapDegrees).coerceAtLeast(1.5f)
                val color = IconRegistry.colorFromLong(slice.category.colorArgb)
                val isHighlighted = selectedSlice == null || selectedSlice.category.id == slice.category.id
                drawArc(
                    color = if (isHighlighted) color else color.copy(alpha = 0.28f),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )
                startAngle += rawSweep
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = selectedSlice?.category?.name?.take(14) ?: "Total Spent",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = CurrencyFormatter.formatMinor(
                    selectedSlice?.amountMinor ?: totalExpenseMinor,
                    currency
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun IncomeExpenseBarChart(
    points: List<MonthlyBarPoint>,
    incomeColor: Color,
    expenseColor: Color,
    gridColor: Color
) {
    val maxMinor = max(
        1L,
        points.maxOfOrNull { max(it.incomeMinor, it.expenseMinor) } ?: 1L
    )
    val heightProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(stiffness = 240f),
        label = "bar_height"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        ) {
            val rows = 3
            for (i in 0..rows) {
                val y = size.height * (i.toFloat() / rows)
                drawLine(
                    color = gridColor.copy(alpha = 0.45f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (points.isNotEmpty()) {
                val slotWidth = size.width / points.size
                val barWidth = (slotWidth * 0.26f).coerceAtMost(18.dp.toPx())
                val barGap = 4.dp.toPx()

                points.forEachIndexed { idx, pt ->
                    val centerX = slotWidth * idx + slotWidth / 2f
                    val incH = (pt.incomeMinor.toFloat() / maxMinor.toFloat()) * size.height * heightProgress
                    val expH = (pt.expenseMinor.toFloat() / maxMinor.toFloat()) * size.height * heightProgress

                    if (incH > 0f) {
                        drawRoundRect(
                            color = incomeColor,
                            topLeft = Offset(centerX - barWidth - barGap / 2f, size.height - incH),
                            size = Size(barWidth, incH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                    if (expH > 0f) {
                        drawRoundRect(
                            color = expenseColor,
                            topLeft = Offset(centerX + barGap / 2f, size.height - expH),
                            size = Size(barWidth, expH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier.fillMaxWidth()
        ) {
            points.forEach { pt ->
                Text(
                    text = pt.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthYearPickerDialog(
    initialYear: Int,
    initialMonthZeroBased: Int,
    onDismiss: () -> Unit,
    onConfirm: (year: Int, monthZeroBased: Int) -> Unit
) {
    val ledgerColors = LocalLedgerColors.current
    var year by remember { mutableIntStateOf(initialYear) }
    var month by remember { mutableIntStateOf(initialMonthZeroBased) }
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ledgerColors.glassSheetBackground,
        title = { Text("Select Month & Year") },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { year-- }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous year")
                    }
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    IconButton(onClick = { year++ }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next year")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    months.forEachIndexed { idx, label ->
                        val selected = idx == month
                        Surface(
                            onClick = { month = idx },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else ledgerColors.cardBackground,
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else ledgerColors.border),
                            modifier = Modifier.width(64.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(year, month) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
