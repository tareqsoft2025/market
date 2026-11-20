package com.tareqsoft.market.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tareqsoft.market.data.PaymentMethod
import com.tareqsoft.market.ui.ChipSelector
import com.tareqsoft.market.ui.Danger
import com.tareqsoft.market.ui.DayNavigator
import com.tareqsoft.market.ui.LabelValue
import com.tareqsoft.market.ui.LocalContainer
import com.tareqsoft.market.ui.BottomSpacer
import com.tareqsoft.market.ui.ScreenScaffold
import com.tareqsoft.market.ui.StatCard
import com.tareqsoft.market.ui.money
import com.tareqsoft.market.util.Dates
import com.tareqsoft.market.util.Qty

private const val PERIOD_DAY = 0
private const val PERIOD_MONTH = 1

@Composable
fun ReportsScreen(onBack: () -> Unit) {
    val repo = LocalContainer.current.repo
    var period by rememberSaveable { mutableStateOf(PERIOD_DAY) }
    var day by rememberSaveable { mutableStateOf(Dates.startOfDay(System.currentTimeMillis())) }

    val from = if (period == PERIOD_DAY) day else Dates.startOfMonth(day)
    val to = Dates.endOfDay(day)

    val d = repo.sales
    val count by remember(from, to) { d.observeSalesCount(from, to) }.collectAsStateWithLifecycle(0)
    val total by remember(from, to) { d.observeSalesTotal(from, to) }.collectAsStateWithLifecycle(0L)
    val discounts by remember(from, to) { d.observeDiscountTotal(from, to) }.collectAsStateWithLifecycle(0L)
    val returns by remember(from, to) { d.observeReturnsTotal(from, to) }.collectAsStateWithLifecycle(0L)
    val cost by remember(from, to) { d.observeCostOfSales(from, to) }.collectAsStateWithLifecycle(0.0)
    val returnedCost by remember(from, to) { d.observeCostOfReturns(from, to) }.collectAsStateWithLifecycle(0.0)
    val byMethod by remember(from, to) { d.observeSalesByMethod(from, to) }.collectAsStateWithLifecycle(emptyList())
    val top by remember(from, to) { d.observeTopProducts(from, to) }.collectAsStateWithLifecycle(emptyList())

    val net = total - returns
    val netCost = Math.round(cost - returnedCost)
    val profit = net - netCost

    ScreenScaffold(title = "التقارير", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChipSelector(
                listOf(PERIOD_DAY, PERIOD_MONTH), period,
                { if (it == PERIOD_DAY) "يومي" else "من بداية الشهر حتى اليوم المحدد" },
                { period = it },
            )
            DayNavigator(day, { day = it })
            Text("الفترة: ${Dates.date(from)} - ${Dates.date(to)}", color = Color.Gray)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("المبيعات", money(total), Modifier.weight(1f), subtitle = "$count فاتورة")
                StatCard("المردودات", money(returns), Modifier.weight(1f), color = Danger)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("صافي المبيعات", money(net), Modifier.weight(1f))
                StatCard("مجمل الربح", money(profit), Modifier.weight(1f), color = if (profit < 0) Danger else MaterialTheme.colorScheme.primary)
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("التفاصيل", fontWeight = FontWeight.Bold)
                    LabelValue("إجمالي الخصومات الممنوحة", money(discounts))
                    LabelValue("تكلفة البضاعة المباعة (صافي)", money(netCost))
                    if (net > 0) LabelValue("نسبة الربح", "%.1f%%".format(profit * 100.0 / net))
                    if (count > 0) LabelValue("متوسط الفاتورة", money(total / count))
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("المبيعات حسب طريقة الدفع", fontWeight = FontWeight.Bold)
                    if (byMethod.isEmpty()) Text("لا توجد مبيعات", color = Color.Gray)
                    byMethod.forEach { LabelValue("${PaymentMethod.label(it.method)} (${it.count})", money(it.amount)) }
                }
            }

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
                "ملاحظة: الربح محسوب على تكلفة الصنف وقت البيع.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
            )
            BottomSpacer()
        }
    }
}
