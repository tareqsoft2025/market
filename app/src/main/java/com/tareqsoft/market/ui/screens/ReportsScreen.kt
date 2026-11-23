package com.tareqsoft.market.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tareqsoft.market.data.PaymentMethod
import com.tareqsoft.market.data.ReportTotals
import com.tareqsoft.market.data.ReturnTotals
import com.tareqsoft.market.ui.AppIcons
import com.tareqsoft.market.ui.BottomSpacer
import com.tareqsoft.market.ui.ChipSelector
import com.tareqsoft.market.ui.Danger
import com.tareqsoft.market.ui.DropdownField
import com.tareqsoft.market.ui.LabelValue
import com.tareqsoft.market.ui.LocalContainer
import com.tareqsoft.market.ui.ScreenScaffold
import com.tareqsoft.market.ui.StatCard
import com.tareqsoft.market.ui.money
import com.tareqsoft.market.util.Dates
import com.tareqsoft.market.util.Qty
import java.util.Calendar
import java.util.TimeZone

/** Ready-made report periods. */
enum class Period(val label: String) {
    TODAY("اليوم"), YESTERDAY("أمس"), WEEK("هذا الأسبوع"), MONTH("هذا الشهر"),
    LAST_MONTH("الشهر الماضي"), YEAR("هذه السنة"), CUSTOM("فترة مخصصة");

    /** [from, to] in epoch millis for this period (week starts on Saturday). */
    fun range(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val today = Dates.startOfDay(now)
        val cal = Calendar.getInstance().apply { timeInMillis = today }
        return when (this) {
            TODAY, CUSTOM -> today to Dates.endOfDay(today)
            YESTERDAY -> Dates.addDays(today, -1).let { it to Dates.endOfDay(it) }
            WEEK -> {
                val back = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.SATURDAY + 7) % 7
                Dates.addDays(today, -back) to Dates.endOfDay(today)
            }
            MONTH -> Dates.startOfMonth(today) to Dates.endOfDay(today)
            LAST_MONTH -> {
                val thisMonth = Dates.startOfMonth(today)
                Dates.startOfMonth(Dates.addDays(thisMonth, -1)) to thisMonth - 1
            }
            YEAR -> cal.apply { set(Calendar.DAY_OF_YEAR, 1) }.timeInMillis to Dates.endOfDay(today)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit) {
    val repo = LocalContainer.current.repo
    var period by rememberSaveable { mutableStateOf(Period.TODAY) }
    var customFrom by rememberSaveable { mutableStateOf(0L) }
    var customTo by rememberSaveable { mutableStateOf(0L) }
    var method by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var pickingRange by remember { mutableStateOf(false) }
    val categories by remember { repo.categories.observeAll() }.collectAsStateWithLifecycle(emptyList())

    val (from, to) = if (period == Period.CUSTOM && customFrom > 0) customFrom to customTo else period.range()

    val r = repo.reports
    val totals by remember(from, to, method, category) { r.totals(from, to, method, category) }
        .collectAsStateWithLifecycle(ReportTotals(0, 0.0, 0.0, 0.0))
    val returns by remember(from, to, method, category) { r.returns(from, to, method, category) }
        .collectAsStateWithLifecycle(ReturnTotals(0, 0.0))
    val byMethod by remember(from, to, method, category) { r.byMethod(from, to, method, category) }
        .collectAsStateWithLifecycle(emptyList())
    val byCategory by remember(from, to, method, category) { r.byCategory(from, to, method, category) }
        .collectAsStateWithLifecycle(emptyList())
    val top by remember(from, to, method, category) { r.topProducts(from, to, method, category) }
        .collectAsStateWithLifecycle(emptyList())

    val sales = Math.round(totals.sales)
    val net = sales - returns.amount
    val netCost = Math.round(totals.cost - returns.cost)
    val profit = net - netCost
    val filtered = method.isNotEmpty() || category.isNotEmpty()

    ScreenScaffold(title = "التقارير", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---- Filters
            ChipSelector(Period.entries, period, { it.label }, {
                period = it
                if (it == Period.CUSTOM) pickingRange = true
            })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DropdownField(
                    value = if (method.isEmpty()) "كل طرق الدفع" else PaymentMethod.label(method),
                    onValueChange = { label ->
                        method = listOf(PaymentMethod.CASH, PaymentMethod.BANK, PaymentMethod.CREDIT)
                            .firstOrNull { PaymentMethod.label(it) == label } ?: ""
                    },
                    options = listOf("كل طرق الدفع") + listOf(PaymentMethod.CASH, PaymentMethod.BANK, PaymentMethod.CREDIT).map { PaymentMethod.label(it) },
                    label = "طريقة الدفع",
                    modifier = Modifier.weight(1f),
                )
                DropdownField(
                    value = category.ifEmpty { "كل الأقسام" },
                    onValueChange = { category = if (it == "كل الأقسام") "" else it },
                    options = listOf("كل الأقسام") + categories.map { it.name },
                    label = "القسم",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Filter, null, tint = Color.Gray, modifier = Modifier.padding(end = 6.dp))
                Text(
                    if (from == Dates.startOfDay(to)) Dates.date(from) else "${Dates.date(from)}  ←  ${Dates.date(to)}",
                    color = Color.Gray, modifier = Modifier.weight(1f),
                )
                if (period == Period.CUSTOM) TextButton(onClick = { pickingRange = true }) { Text("تغيير الفترة") }
                if (filtered) {
                    AssistChip(onClick = { method = ""; category = "" }, label = { Text("مسح الفلاتر") })
                }
            }

            // ---- Headline figures
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("المبيعات", money(sales), Modifier.weight(1f), subtitle = "${totals.invoices} فاتورة")
                StatCard("المردودات", money(returns.amount), Modifier.weight(1f), color = Danger)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("صافي المبيعات", money(net), Modifier.weight(1f))
                StatCard(
                    "مجمل الربح", money(profit), Modifier.weight(1f),
                    color = if (profit < 0) Danger else MaterialTheme.colorScheme.primary,
                    subtitle = if (net > 0) "%.1f%% من الصافي".format(profit * 100.0 / net) else null,
                )
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("التفاصيل", fontWeight = FontWeight.Bold)
                    LabelValue("الخصومات الممنوحة", money(totals.discount))
                    LabelValue("تكلفة البضاعة المباعة (صافي)", money(netCost))
                    if (totals.invoices > 0) LabelValue("متوسط الفاتورة", money(sales / totals.invoices))
                    if (category.isNotEmpty()) {
                        Text(
                            "المبالغ لأصناف قسم \"$category\" فقط، بعد توزيع خصم كل فاتورة على أصنافها.",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                        )
                    }
                }
            }

            BreakdownCard("المبيعات حسب القسم", byCategory.map { it.name to it.amount }, sales)
            BreakdownCard("المبيعات حسب طريقة الدفع", byMethod.map { "${PaymentMethod.label(it.method)} (${it.count})" to it.amount }, sales)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("الأكثر مبيعاً", fontWeight = FontWeight.Bold)
                    if (top.isEmpty()) Text("لا توجد بيانات", color = Color.Gray)
                    top.forEachIndexed { i, t ->
                        LabelValue("${i + 1}. ${t.name} (${Qty.format(t.quantity)})", money(t.amount))
                    }
                }
            }
            Text(
                "الربح محسوب على تكلفة الصنف وقت البيع.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
            )
            BottomSpacer()
        }
    }

    if (pickingRange) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = toUtcDay(from),
            initialSelectedEndDateMillis = toUtcDay(to),
        )
        DatePickerDialog(
            onDismissRequest = { pickingRange = false; if (customFrom == 0L) period = Period.TODAY },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val start = fromUtcDay(state.selectedStartDateMillis!!)
                        val end = fromUtcDay(state.selectedEndDateMillis ?: state.selectedStartDateMillis!!)
                        customFrom = Dates.startOfDay(start)
                        customTo = Dates.endOfDay(end)
                        period = Period.CUSTOM
                        pickingRange = false
                    },
                ) { Text("عرض") }
            },
            dismissButton = { TextButton(onClick = { pickingRange = false; if (customFrom == 0L) period = Period.TODAY }) { Text("إلغاء") } },
        ) {
            DateRangePicker(state = state, modifier = Modifier.weight(1f), title = {
                Text("اختر الفترة", Modifier.padding(start = 24.dp, top = 16.dp))
            })
        }
    }
}

/** Date pickers work in UTC midnight; the app works in local days. */
private fun toUtcDay(local: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = local }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun fromUtcDay(utc: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    return Calendar.getInstance().apply {
        clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** Rows with a proportional bar, so the biggest share stands out at a glance. */
@Composable
private fun BreakdownCard(title: String, rows: List<Pair<String, Long>>, total: Long) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            if (rows.isEmpty()) Text("لا توجد مبيعات", color = Color.Gray)
            rows.forEach { (label, amount) ->
                val share = if (total > 0) (amount.toFloat() / total).coerceIn(0f, 1f) else 0f
                Column {
                    Row {
                        Text(label, Modifier.weight(1f))
                        Text(money(amount), fontWeight = FontWeight.SemiBold)
                        Text(
                            "  %.0f%%".format(share * 100),
                            color = Color.Gray, style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                    Box(
                        Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            Modifier.fillMaxWidth(share).height(6.dp).clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

