package com.tareqsoft.market.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tareqsoft.market.data.PaymentKind
import com.tareqsoft.market.data.PaymentMethod
import com.tareqsoft.market.data.PaymentStatus
import com.tareqsoft.market.data.ReturnLine
import com.tareqsoft.market.data.SaleRow
import com.tareqsoft.market.ui.ChipSelector
import com.tareqsoft.market.ui.Danger
import com.tareqsoft.market.ui.DayNavigator
import com.tareqsoft.market.ui.EmptyState
import com.tareqsoft.market.ui.LabelValue
import com.tareqsoft.market.ui.LocalContainer
import com.tareqsoft.market.ui.BottomSpacer
import com.tareqsoft.market.ui.PrimaryActionButton
import com.tareqsoft.market.ui.NumberField
import com.tareqsoft.market.ui.ScreenScaffold
import com.tareqsoft.market.ui.Warning
import com.tareqsoft.market.ui.money
import com.tareqsoft.market.util.Dates
import com.tareqsoft.market.util.Qty
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Invoices list

@Composable
fun SalesScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val repo = LocalContainer.current.repo
    var day by rememberSaveable { mutableStateOf(Dates.startOfDay(System.currentTimeMillis())) }
    var query by rememberSaveable { mutableStateOf("") }

    // When searching, look through all dates.
    val searching = query.isNotBlank()
    val from = if (searching) 0L else day
    val to = if (searching) Long.MAX_VALUE else Dates.endOfDay(day)
    val sales by remember(from, to, query) { repo.sales.observeSales(from, to, query.trim()) }
        .collectAsStateWithLifecycle(emptyList())

    ScreenScaffold(title = "الفواتير والمردودات", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("بحث برقم الفاتورة أو اسم العميل") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
            if (!searching) DayNavigator(day, { day = it })
            val total = sales.sumOf { it.sale.total }
            val returned = sales.sumOf { it.sale.returnedAmount }
            Text(
                "${sales.size} فاتورة • ${money(total)}" + if (returned > 0) " • مردود ${money(returned)}" else "",
                Modifier.padding(horizontal = 12.dp),
                color = Color.Gray,
            )
            if (sales.isEmpty()) {
                EmptyState("لا توجد فواتير")
            } else {
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sales, key = { it.sale.id }) { row -> SaleCard(row) { onOpen(row.sale.id) } }
                }
            }
        }
    }
}

@Composable
fun SaleCard(row: SaleRow, onClick: () -> Unit) {
    val s = row.sale
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("فاتورة #${s.invoiceNo}", fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(Dates.dateTime(s.createdAt), PaymentMethod.label(s.paymentMethod), row.customerName)
                        .joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                )
                if (s.returnedAmount > 0) {
                    Text("مردود: ${money(s.returnedAmount)}", style = MaterialTheme.typography.bodySmall, color = Danger)
                }
            }
            Text(money(s.total), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ---------------------------------------------------------------- Invoice detail

@Composable
fun SaleDetailScreen(saleId: String, onBack: () -> Unit, onReturn: (String) -> Unit) {
    val repo = LocalContainer.current.repo
    val row by remember(saleId) { repo.sales.observeSale(saleId) }.collectAsStateWithLifecycle(null)
    val items by remember(saleId) { repo.sales.observeItems(saleId) }.collectAsStateWithLifecycle(emptyList())
    val returns by remember(saleId) { repo.sales.observeReturnsForSale(saleId) }.collectAsStateWithLifecycle(emptyList())
    val payments by remember(saleId) { repo.payments.observeForSale(saleId) }.collectAsStateWithLifecycle(emptyList())

    val r = row
    ScreenScaffold(title = r?.let { "فاتورة #${it.sale.invoiceNo}" } ?: "فاتورة", onBack = onBack) { padding ->
        if (r == null) return@ScreenScaffold
        val s = r.sale
        val canReturn = items.any { it.quantity - it.returnedQuantity > 0 }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    LabelValue("التاريخ", Dates.dateTime(s.createdAt))
                    LabelValue("طريقة الدفع", PaymentMethod.label(s.paymentMethod))
                    r.customerName?.let { LabelValue("العميل", it) }
                }
            }
            Text("الأصناف", style = MaterialTheme.typography.titleMedium)
            items.forEach { it ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(it.productName, fontWeight = FontWeight.Bold)
                        Text("${money(it.unitPrice)} × ${Qty.format(it.quantity)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        if (it.returnedQuantity > 0) {
                            Text("مُرجع: ${Qty.format(it.returnedQuantity)}", style = MaterialTheme.typography.bodySmall, color = Danger)
                        }
                    }
                    Text(money(it.lineTotal))
                }
                HorizontalDivider()
            }
            LabelValue("المجموع", money(s.subtotal))
            if (s.discount > 0) LabelValue("الخصم", "- ${money(s.discount)}", Danger)
            LabelValue("الإجمالي", money(s.total), MaterialTheme.colorScheme.primary, true)
            if (s.returnedAmount > 0) {
                LabelValue("المردودات", "- ${money(s.returnedAmount)}", Danger)
                LabelValue("الصافي بعد المردودات", money(s.total - s.returnedAmount), bold = true)
            }

            if (payments.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("الحركات المالية", style = MaterialTheme.typography.titleMedium)
                payments.forEach { p ->
                    LabelValue(
                        "${PaymentKind.label(p.kind)} - ${PaymentMethod.label(p.method)} (${PaymentStatus.label(p.status)})",
                        (if (p.kind == PaymentKind.REFUND) "- " else "") + money(p.amount),
                        if (p.status == PaymentStatus.REJECTED) Danger else if (p.status == PaymentStatus.PENDING) Warning else Color.Unspecified,
                    )
                }
            }

            if (returns.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("المردودات", style = MaterialTheme.typography.titleMedium)
                returns.forEach { ret ->
                    LabelValue(
                        "مردود #${ret.returnNo} • ${Dates.dateTime(ret.createdAt)} • ${refundLabel(ret.refundMethod)}",
                        money(ret.amount), Danger,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            PrimaryActionButton(
                text = if (canReturn) "مردود مبيعات" else "تم إرجاع كل الأصناف",
                onClick = { onReturn(s.id) },
                enabled = canReturn,
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
            )
            BottomSpacer()
        }
    }
}

fun refundLabel(method: String) = when (method) {
    PaymentMethod.CASH -> "استرداد نقدي"
    PaymentMethod.BANK -> "استرداد بتحويل"
    PaymentMethod.CREDIT -> "خصم من حساب العميل"
    else -> method
}

// ---------------------------------------------------------------- Return

@Composable
fun ReturnScreen(saleId: String, onBack: () -> Unit) {
    val repo = LocalContainer.current.repo
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val row by remember(saleId) { repo.sales.observeSale(saleId) }.collectAsStateWithLifecycle(null)
    val items by remember(saleId) { repo.sales.observeItems(saleId) }.collectAsStateWithLifecycle(emptyList())

    val qtyText = remember { mutableStateMapOf<String, String>() }
    val restock = remember { mutableStateMapOf<String, Boolean>() }
    var refundMethod by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val r = row
    ScreenScaffold(title = "مردود مبيعات", onBack = onBack, snackbar = snackbar) { padding ->
        if (r == null) return@ScreenScaffold
        val sale = r.sale
        val methods = if (sale.customerId != null) {
            listOf(PaymentMethod.CASH, PaymentMethod.BANK, PaymentMethod.CREDIT)
        } else {
            listOf(PaymentMethod.CASH, PaymentMethod.BANK)
        }
        val method = refundMethod
            ?: if (sale.paymentMethod == PaymentMethod.CREDIT) PaymentMethod.CREDIT
            else if (sale.paymentMethod == PaymentMethod.BANK) PaymentMethod.BANK
            else PaymentMethod.CASH

        val lines = items.mapNotNull { item ->
            val q = Qty.parse(qtyText[item.id] ?: "") ?: return@mapNotNull null
            if (q <= 0) null else ReturnLine(item, q, restock[item.id] ?: true)
        }
        val refund = lines.sumOf { repo.refundFor(sale, it.saleItem, it.quantity) }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("فاتورة #${sale.invoiceNo} • ${Dates.dateTime(sale.createdAt)}", color = Color.Gray)
            if (sale.discount > 0) {
                Text("ملاحظة: خصم الفاتورة يوزَّع على الأصناف المُرجعة بالنسبة.", style = MaterialTheme.typography.bodySmall, color = Warning)
            }
            items.forEach { item ->
                val remaining = item.quantity - item.returnedQuantity
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.productName, fontWeight = FontWeight.Bold)
                        Text(
                            "المباع ${Qty.format(item.quantity)} • المتاح للإرجاع ${Qty.format(remaining)} • ${money(item.unitPrice)}",
                            style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                        )
                        if (remaining > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NumberField(
                                    qtyText[item.id] ?: "", { qtyText[item.id] = it }, "كمية المردود",
                                    Modifier.width(140.dp),
                                    isError = (Qty.parse(qtyText[item.id] ?: "") ?: 0.0) > remaining,
                                )
                                Checkbox(checked = restock[item.id] ?: true, onCheckedChange = { restock[item.id] = it })
                                Text("إرجاع للمخزن")
                            }
                            if (restock[item.id] == false) {
                                Text("سيُسجل كتالف ولن يُضاف للمخزن", style = MaterialTheme.typography.bodySmall, color = Danger)
                            }
                        }
                    }
                }
            }

            Text("طريقة رد المبلغ", style = MaterialTheme.typography.titleMedium)
            ChipSelector(methods, method, { refundLabel(it) }, { refundMethod = it })
            OutlinedTextField(note, { note = it }, label = { Text("سبب المردود") }, modifier = Modifier.fillMaxWidth())
            LabelValue("قيمة المردود", money(refund), Danger, true)
            PrimaryActionButton(
                text = "تأكيد المردود",
                onClick = {
                    saving = true
                    scope.launch {
                        try {
                            repo.createReturn(sale.id, lines, method, note.trim())
                            onBack()
                        } catch (e: Exception) {
                            snackbar.showSnackbar(e.message ?: "خطأ")
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = lines.isNotEmpty() && !saving,
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
            )
            BottomSpacer()
        }
    }
}
