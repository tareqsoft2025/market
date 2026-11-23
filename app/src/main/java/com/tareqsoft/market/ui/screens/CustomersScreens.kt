package com.tareqsoft.market.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.tareqsoft.market.data.Customer
import com.tareqsoft.market.data.CustomerWithBalance
import com.tareqsoft.market.data.DebtSummary
import com.tareqsoft.market.data.InvoiceDebt
import com.tareqsoft.market.data.InvoiceStatus
import com.tareqsoft.market.data.PaymentKind
import com.tareqsoft.market.data.PaymentMethod
import com.tareqsoft.market.data.PaymentStatus
import com.tareqsoft.market.data.TransferChannel
import com.tareqsoft.market.ui.AppIcons
import com.tareqsoft.market.ui.ChipSelector
import com.tareqsoft.market.ui.Danger
import com.tareqsoft.market.ui.EmptyState
import com.tareqsoft.market.ui.Green
import com.tareqsoft.market.ui.LabelValue
import com.tareqsoft.market.ui.LocalContainer
import com.tareqsoft.market.ui.NumberField
import com.tareqsoft.market.ui.PrimaryActionButton
import com.tareqsoft.market.ui.ScreenScaffold
import com.tareqsoft.market.ui.TransferFields
import com.tareqsoft.market.ui.TransferFormState
import com.tareqsoft.market.ui.Warning
import com.tareqsoft.market.ui.money
import com.tareqsoft.market.util.Dates
import com.tareqsoft.market.util.Money
import com.tareqsoft.market.util.Sms
import com.tareqsoft.market.util.SmsMessage
import com.tareqsoft.market.util.rememberSmsSender
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Debt per customer, recalculated whenever a sale, return or payment changes. */
@Composable
private fun rememberDebts(): Map<String, DebtSummary> {
    val repo = LocalContainer.current.repo
    val flow = remember {
        combine(repo.debts.observeCreditInvoices(), repo.debts.observeCollections()) { inv, col ->
            DebtSummary.perCustomer(inv, col)
        }
    }
    return flow.collectAsStateWithLifecycle(emptyMap()).value
}

/** Builds the reminder text for one customer with the store's template. */
@Composable
private fun reminderText(): (Customer, Long) -> String {
    val settings by LocalContainer.current.settings.state.collectAsState()
    return { c, amount ->
        val amt = Money.format(amount) + if (settings.currency.isBlank()) "" else " ${settings.currency}"
        Sms.build(settings.smsTemplate, c.name, amt, settings.storeName)
    }
}

@Composable
fun CustomersScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val repo = LocalContainer.current.repo
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var query by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var confirmBulk by remember { mutableStateOf(false) }
    var debtorsOnly by rememberSaveable { mutableStateOf(false) }
    val list by remember(query) { repo.customers.observeWithBalance(query.trim()) }.collectAsStateWithLifecycle(emptyList())
    val debts = rememberDebts()
    val total = DebtSummary.total(debts.values)
    val text = reminderText()

    val debtors = list.filter { it.balance > 0 }
    val reachable = debtors.filter { Sms.isValidPhone(it.customer.phone) }
    val send = rememberSmsSender { sent, count, denied ->
        scope.launch {
            snackbar.showSnackbar(
                if (denied) "لم يتم الإرسال: اسمح للتطبيق بإرسال الرسائل من إعدادات الهاتف"
                else "تم إرسال $sent من $count رسالة تذكير"
            )
        }
    }
    val shown = if (debtorsOnly) debtors else list

    ScreenScaffold(
        title = "العملاء والديون",
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            if (reachable.isNotEmpty()) {
                IconButton(onClick = { confirmBulk = true }) { Icon(AppIcons.Sms, "تذكير كل المدينين برسالة") }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("عميل جديد") })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { DebtSummaryCard(total, title = "ديون كل العملاء") }
            item {
                OutlinedTextField(
                    query, { query = it }, label = { Text("بحث بالاسم أو الهاتف") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                ChipSelector(
                    listOf(false, true), debtorsOnly,
                    { if (it) "عليهم ديون (${debtors.size})" else "كل العملاء (${list.size})" },
                    { debtorsOnly = it },
                )
            }
            if (shown.isEmpty()) item { EmptyState(if (list.isEmpty()) "لا يوجد عملاء" else "لا يوجد عملاء عليهم ديون") }
            items(shown, key = { it.customer.id }) { c -> CustomerCard(c, debts[c.customer.id]) { onOpen(c.customer.id) } }
        }
    }

    if (adding) {
        CustomerEditDialog(null, onDismiss = { adding = false }, onSaved = { adding = false })
    }
    if (confirmBulk) {
        AlertDialog(
            onDismissRequest = { confirmBulk = false },
            icon = { Icon(AppIcons.Sms, null) },
            title = { Text("تذكير المدينين بالدفع") },
            text = {
                Column {
                    Text("سيتم إرسال رسالة من شريحة هذا الجوال إلى ${reachable.size} عميل عليهم مبالغ مستحقة.")
                    if (reachable.size < debtors.size) {
                        Text(
                            "${debtors.size - reachable.size} عميل بدون رقم جوال لن تصلهم رسالة.",
                            color = Warning, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "مثال: " + text(reachable.first().customer, reachable.first().balance),
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    confirmBulk = false
                    send(reachable.map { SmsMessage(it.customer.phone, text(it.customer, it.balance)) })
                }) { Text("إرسال ${reachable.size} رسالة") }
            },
            dismissButton = { TextButton(onClick = { confirmBulk = false }) { Text("إلغاء") } },
        )
    }
}

/** Collected / remaining / rate, and how many invoices are paid. */
@Composable
fun DebtSummaryCard(s: DebtSummary, title: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "%.0f%%".format(s.rate),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    color = if (s.rate >= 80) Green else if (s.rate >= 50) Warning else Danger,
                )
            }
            Text("نسبة التحصيل", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.align(Alignment.End))
            LinearProgressIndicator(
                progress = { (s.rate / 100).toFloat() },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(8.dp),
                color = Green,
                trackColor = Danger.copy(alpha = 0.18f),
                drawStopIndicator = {},
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Figure("إجمالي الآجل", money(s.totalDue), Color.Unspecified, Modifier.weight(1f))
                Figure("المحصّل", money(s.collected), Green, Modifier.weight(1f))
                Figure("المتبقي", money(s.remaining), Danger, Modifier.weight(1f))
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Figure("فواتير محصّلة", "${s.paidCount}", Green, Modifier.weight(1f))
                Figure("محصّلة جزئياً", "${s.partialCount}", Warning, Modifier.weight(1f))
                Figure("غير محصّلة", "${s.unpaidCount}", Danger, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun StatusBadge(status: String) {
    val color = when (status) {
        InvoiceStatus.PAID -> Green
        InvoiceStatus.PARTIAL -> Warning
        else -> Danger
    }
    Box(
        Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(InvoiceStatus.label(status), color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CustomerCard(c: CustomerWithBalance, debt: DebtSummary?, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.customer.name, fontWeight = FontWeight.Bold)
                val sub = listOf(c.customer.phone, c.customer.address).filter { it.isNotBlank() }.joinToString(" • ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                if (debt != null && debt.invoices.isNotEmpty()) {
                    Text(
                        "تحصيل %.0f%% • %d غير محصّلة".format(debt.rate, debt.unpaidCount + debt.partialCount),
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(c.balance), fontWeight = FontWeight.Bold, color = if (c.balance > 0) Danger else Color.Gray)
                Text(if (c.balance > 0) "عليه" else if (c.balance < 0) "له" else "لا يوجد دين", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun CustomerEditDialog(existing: Customer?, onDismiss: () -> Unit, onSaved: (Customer) -> Unit) {
    val repo = LocalContainer.current.repo
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var address by remember { mutableStateOf(existing?.address ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "عميل جديد" else "تعديل العميل") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("الاسم *") }, singleLine = true)
                NumberField(phone, { phone = it }, "رقم الجوال (للتذكير بالرسائل)", decimal = false)
                OutlinedTextField(
                    address, { address = it },
                    label = { Text("عنوان السكن") },
                    placeholder = { Text("الحي، الشارع، علامة مميزة") },
                    minLines = 2,
                )
                error?.let { Text(it, color = Danger) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val c = existing?.copy(name = name.trim(), phone = phone.trim(), address = address.trim())
                    ?: Customer(name = name.trim(), phone = phone.trim(), address = address.trim())
                scope.launch {
                    try {
                        repo.saveCustomer(c)
                        onSaved(c)
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** Used by the sales screen to choose (or quickly add) a customer. */
@Composable
fun CustomerPickerDialog(onDismiss: () -> Unit, onPick: (Customer) -> Unit) {
    val repo = LocalContainer.current.repo
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val list by remember(query) { repo.customers.observeWithBalance(query.trim()) }.collectAsStateWithLifecycle(emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر العميل") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text("بحث") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(list, key = { it.customer.id }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(c.customer) }.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.customer.name)
                                if (c.customer.address.isNotBlank()) {
                                    Text(c.customer.address, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                            if (c.balance != 0L) Text(money(c.balance), color = if (c.balance > 0) Danger else Color.Gray)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { adding = true }) { Text("+ عميل جديد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
    if (adding) {
        CustomerEditDialog(null, onDismiss = { adding = false }, onSaved = { adding = false; onPick(it) })
    }
}

private const val TAB_INVOICES = 0
private const val TAB_PAYMENTS = 1

@Composable
fun CustomerDetailScreen(customerId: String, onBack: () -> Unit, onOpenSale: (String) -> Unit) {
    val repo = LocalContainer.current.repo
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val all by remember { repo.customers.observeWithBalance("") }.collectAsStateWithLifecycle(emptyList())
    val c = all.firstOrNull { it.customer.id == customerId }
    val payments by remember(customerId) { repo.payments.observeForCustomer(customerId) }.collectAsStateWithLifecycle(emptyList())
    val debt = rememberDebts()[customerId] ?: DebtSummary.EMPTY
    var tab by rememberSaveable { mutableStateOf(TAB_INVOICES) }
    var invoiceFilter by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var collecting by remember { mutableStateOf(false) }
    val text = reminderText()
    val send = rememberSmsSender { sent, _, denied ->
        scope.launch {
            snackbar.showSnackbar(
                when {
                    denied -> "فُتحت الرسالة في تطبيق الرسائل، اضغط إرسال"
                    sent > 0 -> "تم إرسال رسالة التذكير"
                    else -> "تعذّر إرسال الرسالة"
                }
            )
        }
    }

    ScreenScaffold(
        title = c?.customer?.name ?: "العميل",
        onBack = onBack,
        snackbar = snackbar,
        actions = { IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "تعديل") } },
    ) { padding ->
        if (c == null) return@ScreenScaffold
        val invoices = debt.invoices.reversed().filter { invoiceFilter.isEmpty() || it.status == invoiceFilter }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (c.customer.phone.isNotBlank() || c.customer.address.isNotBlank()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            if (c.customer.phone.isNotBlank()) LabelValue("الجوال", c.customer.phone)
                            if (c.customer.address.isNotBlank()) LabelValue("عنوان السكن", c.customer.address)
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = if (c.balance > 0) Danger.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("الرصيد المستحق", color = Color.Gray)
                        Text(money(c.balance), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                            color = if (c.balance > 0) Danger else Green)
                    }
                }
            }
            item { DebtSummaryCard(debt, title = "ملخص التحصيل") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryActionButton("تحصيل دفعة", onClick = { collecting = true }, modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { send(listOf(SmsMessage(c.customer.phone, text(c.customer, c.balance)))) },
                        enabled = c.balance > 0 && Sms.isValidPhone(c.customer.phone),
                        modifier = Modifier.weight(1f).heightIn(min = 58.dp),
                    ) {
                        Icon(AppIcons.Sms, null, Modifier.size(18.dp))
                        Text("  تذكير برسالة")
                    }
                }
            }
            if (c.balance > 0 && !Sms.isValidPhone(c.customer.phone)) {
                item { Text("أضف رقم جوال العميل لإرسال تذكير بالدفع.", style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
            }
            item {
                ChipSelector(listOf(TAB_INVOICES, TAB_PAYMENTS), tab, {
                    if (it == TAB_INVOICES) "فواتير الآجل (${debt.invoices.size})" else "الدفعات (${payments.size})"
                }, { tab = it })
            }
            if (tab == TAB_INVOICES) {
                item {
                    ChipSelector(
                        listOf("", InvoiceStatus.UNPAID, InvoiceStatus.PARTIAL, InvoiceStatus.PAID), invoiceFilter,
                        { if (it.isEmpty()) "الكل" else InvoiceStatus.label(it) },
                        { invoiceFilter = it },
                    )
                }
                if (invoices.isEmpty()) item { EmptyState("لا توجد فواتير") }
                items(invoices, key = { it.invoice.saleId }) { d -> InvoiceDebtRow(d) { onOpenSale(d.invoice.saleId) } }
            } else {
                if (payments.isEmpty()) item { EmptyState("لا توجد دفعات") }
                items(payments, key = { it.payment.id }) { row ->
                    val p = row.payment
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (p.method == PaymentMethod.BANK) {
                            Icon(AppIcons.forChannel(p.channel), null, Modifier.size(20.dp), tint = Color.Gray)
                        }
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(
                                "${PaymentKind.label(p.kind)} • " +
                                    if (p.method == PaymentMethod.BANK) TransferChannel.label(p.channel) else PaymentMethod.label(p.method)
                            )
                            Text(Dates.dateTime(p.createdAt), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money(p.amount), fontWeight = FontWeight.Bold)
                            Text(
                                PaymentStatus.label(p.status), style = MaterialTheme.typography.bodySmall,
                                color = if (p.status == PaymentStatus.PENDING) Warning else if (p.status == PaymentStatus.REJECTED) Danger else Color.Gray,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (editing && c != null) {
        CustomerEditDialog(c.customer, onDismiss = { editing = false }, onSaved = { editing = false })
    }
    if (collecting) {
        var amount by remember { mutableStateOf(if ((c?.balance ?: 0L) > 0L) Money.toInput(c!!.balance) else "") }
        var method by remember { mutableStateOf(PaymentMethod.CASH) }
        val transfer = remember { TransferFormState() }
        AlertDialog(
            onDismissRequest = { collecting = false },
            title = { Text("تحصيل دفعة") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(amount, { amount = it }, "المبلغ")
                    ChipSelector(listOf(PaymentMethod.CASH, PaymentMethod.BANK), method, {
                        if (it == PaymentMethod.BANK) "تحويل" else PaymentMethod.label(it)
                    }, { method = it })
                    if (method == PaymentMethod.BANK) TransferFields(transfer)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val v = Money.parse(amount) ?: return@Button
                    scope.launch {
                        try {
                            repo.collectDebt(customerId, v, method, if (method == PaymentMethod.BANK) transfer.toInfo() else null)
                            collecting = false
                            snackbar.showSnackbar("تم تسجيل الدفعة")
                        } catch (e: Exception) {
                            snackbar.showSnackbar(e.message ?: "خطأ")
                        }
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { collecting = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun InvoiceDebtRow(d: InvoiceDebt, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("فاتورة #${d.invoice.invoiceNo}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                    StatusBadge(d.status)
                }
                Text(Dates.dateTime(d.invoice.createdAt), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(d.invoice.due), fontWeight = FontWeight.Bold)
                if (d.status != InvoiceStatus.PAID) {
                    Text("المتبقي ${money(d.remaining)}", style = MaterialTheme.typography.bodySmall, color = Danger)
                }
            }
        }
    }
}
