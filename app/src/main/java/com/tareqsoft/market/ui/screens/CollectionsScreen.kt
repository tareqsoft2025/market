package com.tareqsoft.market.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.tareqsoft.market.data.PaymentRow
import com.tareqsoft.market.data.PaymentStatus
import com.tareqsoft.market.data.TransferChannel
import com.tareqsoft.market.ui.AppIcons
import com.tareqsoft.market.ui.ChipSelector
import com.tareqsoft.market.ui.Danger
import com.tareqsoft.market.ui.DayNavigator
import com.tareqsoft.market.ui.EmptyState
import com.tareqsoft.market.ui.Info
import com.tareqsoft.market.ui.LabelValue
import com.tareqsoft.market.ui.LocalContainer
import com.tareqsoft.market.ui.ScreenScaffold
import com.tareqsoft.market.ui.Warning
import com.tareqsoft.market.ui.money
import com.tareqsoft.market.util.Dates
import kotlinx.coroutines.launch

private const val TAB_PENDING = 0
private const val TAB_DAY = 1

@Composable
fun CollectionsScreen(onBack: () -> Unit, onOpenSale: (String) -> Unit) {
    val repo = LocalContainer.current.repo
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(TAB_PENDING) }
    var day by rememberSaveable { mutableStateOf(Dates.startOfDay(System.currentTimeMillis())) }
    var rejecting by remember { mutableStateOf<PaymentRow?>(null) }

    val pending by remember { repo.payments.observePendingBank() }.collectAsStateWithLifecycle(emptyList())
    val dayRows by remember(day) { repo.payments.observeRange(day, Dates.endOfDay(day)) }
        .collectAsStateWithLifecycle(emptyList())

    fun setStatus(row: PaymentRow, status: String, note: String? = null) {
        scope.launch {
            try {
                repo.reviewPayment(row.payment.id, status, note)
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "خطأ")
            }
        }
    }

    ScreenScaffold(title = "التحصيلات والتحويلات", onBack = onBack, snackbar = snackbar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ChipSelector(
                options = listOf(TAB_PENDING, TAB_DAY),
                selected = tab,
                label = { if (it == TAB_PENDING) "تحويلات بانتظار المراجعة (${pending.size})" else "حركة الصندوق اليومية" },
                onSelect = { tab = it },
                modifier = Modifier.padding(12.dp),
            )

            val rows = if (tab == TAB_PENDING) pending else dayRows
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (tab == TAB_DAY) {
                    item { DayNavigator(day, { day = it }) }
                    item { DaySummary(dayRows) }
                }
                if (rows.isEmpty()) {
                    item {
                        EmptyState(if (tab == TAB_PENDING) "لا توجد تحويلات بانتظار المراجعة 👍" else "لا توجد حركات في هذا اليوم")
                    }
                }
                items(rows, key = { it.payment.id }) { row ->
                    PaymentCard(
                        row = row,
                        onOpenSale = onOpenSale,
                        onConfirm = { setStatus(row, PaymentStatus.CONFIRMED) },
                        onReject = { rejecting = row },
                        onReopen = { setStatus(row, PaymentStatus.PENDING) },
                    )
                }
            }
        }
    }

    rejecting?.let { row ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejecting = null },
            title = { Text("رفض التحويل") },
            text = {
                Column {
                    Text("التحويل ${money(row.payment.amount)} برقم مرجع ${row.payment.reference} لم يصل للحساب؟")
                    OutlinedTextField(reason, { reason = it }, label = { Text("السبب") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    setStatus(row, PaymentStatus.REJECTED, reason.trim().ifBlank { null })
                    rejecting = null
                }) { Text("رفض", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { rejecting = null }) { Text("إلغاء") } },
        )
    }
}

/** Cash drawer and bank summary for one day. */
@Composable
private fun DaySummary(rows: List<PaymentRow>) {
    fun sum(method: String, kind: String? = null, status: String? = null, refund: Boolean = false) =
        rows.filter {
            it.payment.method == method &&
                (kind == null || it.payment.kind == kind) &&
                (status == null || it.payment.status == status) &&
                ((it.payment.kind == PaymentKind.REFUND) == refund)
        }.sumOf { it.payment.amount }

    val cashSales = sum(PaymentMethod.CASH, PaymentKind.SALE)
    val cashDebt = sum(PaymentMethod.CASH, PaymentKind.DEBT_COLLECTION)
    val cashRefund = sum(PaymentMethod.CASH, refund = true)
    val bankConfirmed = sum(PaymentMethod.BANK, status = PaymentStatus.CONFIRMED)
    val bankPending = sum(PaymentMethod.BANK, status = PaymentStatus.PENDING)
    val bankRejected = sum(PaymentMethod.BANK, status = PaymentStatus.REJECTED)
    val bankRefund = sum(PaymentMethod.BANK, refund = true)

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("الصندوق (النقدي)", fontWeight = FontWeight.Bold)
            LabelValue("مبيعات نقدية", money(cashSales))
            LabelValue("تحصيل ديون نقداً", money(cashDebt))
            LabelValue("استرداد مردودات نقداً", "- ${money(cashRefund)}", Danger)
            LabelValue("النقد المتوقع في الدرج", money(cashSales + cashDebt - cashRefund), MaterialTheme.colorScheme.primary, true)
            Text("التحويلات البنكية", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            LabelValue("مؤكدة", money(bankConfirmed), MaterialTheme.colorScheme.primary)
            LabelValue("بانتظار المراجعة", money(bankPending), Warning)
            LabelValue("مرفوضة", money(bankRejected), Danger)
            LabelValue("استرداد بتحويل", "- ${money(bankRefund)}", Danger)
        }
    }
}

@Composable
private fun PaymentCard(
    row: PaymentRow,
    onOpenSale: (String) -> Unit,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onReopen: () -> Unit,
) {
    val p = row.payment
    val statusColor = when (p.status) {
        PaymentStatus.PENDING -> Warning
        PaymentStatus.REJECTED -> Danger
        else -> MaterialTheme.colorScheme.primary
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val transfer = p.method == PaymentMethod.BANK
                Box(
                    Modifier.size(40.dp).background(
                        (if (transfer) Info else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f), CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (transfer) AppIcons.forChannel(p.channel) else AppIcons.Wallet, null,
                        tint = if (transfer) Info else MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp),
                    )
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        "${PaymentKind.label(p.kind)} • " +
                            if (transfer) TransferChannel.label(p.channel) else PaymentMethod.label(p.method),
                        fontWeight = FontWeight.Bold,
                    )
                    val details = listOfNotNull(
                        Dates.dateTime(p.createdAt),
                        row.invoiceNo?.let { "فاتورة #$it" },
                        row.customerName,
                        p.bankName.ifBlank { null },
                        p.payerPhone.ifBlank { null }?.let { "جوال: $it" },
                        p.reference.ifBlank { null }?.let { "مرجع: $it" },
                    ).joinToString(" • ")
                    Text(details, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    if (p.note.isNotBlank()) Text("ملاحظة: ${p.note}", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        (if (p.kind == PaymentKind.REFUND) "- " else "") + money(p.amount),
                        fontWeight = FontWeight.Bold,
                        color = if (p.kind == PaymentKind.REFUND) Danger else Info,
                    )
                    Text(PaymentStatus.label(p.status), style = MaterialTheme.typography.bodySmall, color = statusColor)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                if (p.method == PaymentMethod.BANK && p.kind != PaymentKind.REFUND) {
                    if (p.status == PaymentStatus.PENDING) {
                        Button(onClick = onConfirm) { Text("تأكيد الوصول") }
                        Button(onClick = onReject, colors = ButtonDefaults.buttonColors(containerColor = Danger)) { Text("رفض") }
                    } else {
                        OutlinedButton(onClick = onReopen) { Text("إعادة للمراجعة") }
                    }
                }
                val saleId = p.saleId
                if (saleId != null) {
                    TextButton(onClick = { onOpenSale(saleId) }) { Text("عرض الفاتورة") }
                }
            }
        }
    }
}
