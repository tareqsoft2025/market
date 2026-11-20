package com.tareqsoft.market.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.MaterialTheme
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
import com.tareqsoft.market.data.Customer
import com.tareqsoft.market.data.CustomerWithBalance
import com.tareqsoft.market.data.PaymentKind
import com.tareqsoft.market.data.PaymentMethod
import com.tareqsoft.market.data.PaymentStatus
import com.tareqsoft.market.ui.ChipSelector
import com.tareqsoft.market.ui.Danger
import com.tareqsoft.market.ui.EmptyState
import com.tareqsoft.market.ui.LocalContainer
import com.tareqsoft.market.ui.NumberField
import com.tareqsoft.market.ui.ScreenScaffold
import com.tareqsoft.market.ui.StatCard
import com.tareqsoft.market.ui.Warning
import com.tareqsoft.market.ui.money
import com.tareqsoft.market.util.Dates
import com.tareqsoft.market.util.Money
import kotlinx.coroutines.launch

@Composable
fun CustomersScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val repo = LocalContainer.current.repo
    var query by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val list by remember(query) { repo.customers.observeWithBalance(query.trim()) }.collectAsStateWithLifecycle(emptyList())
    val totalDebt = list.sumOf { maxOf(0L, it.balance) }

    ScreenScaffold(
        title = "العملاء والديون",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("عميل جديد") })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            StatCard("إجمالي الديون على العملاء", money(totalDebt), Modifier.fillMaxWidth().padding(12.dp), color = Danger)
            OutlinedTextField(
                query, { query = it }, label = { Text("بحث بالاسم أو الهاتف") },
                leadingIcon = { Icon(Icons.Filled.Search, null) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            if (list.isEmpty()) {
                EmptyState("لا يوجد عملاء")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(list, key = { it.customer.id }) { c -> CustomerCard(c) { onOpen(c.customer.id) } }
                }
            }
        }
    }

    if (adding) {
        CustomerEditDialog(null, onDismiss = { adding = false }, onSaved = { adding = false })
    }
}

@Composable
private fun CustomerCard(c: CustomerWithBalance, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.customer.name, fontWeight = FontWeight.Bold)
                if (c.customer.phone.isNotBlank()) Text(c.customer.phone, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "عميل جديد" else "تعديل العميل") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("الاسم *") }, singleLine = true)
                NumberField(phone, { phone = it }, "رقم الهاتف", decimal = false)
                error?.let { Text(it, color = Danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = existing?.copy(name = name.trim(), phone = phone.trim())
                    ?: Customer(name = name.trim(), phone = phone.trim())
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
                            Modifier.fillMaxWidth().clickable { onPick(c.customer) }.padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(c.customer.name)
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

@Composable
fun CustomerDetailScreen(customerId: String, onBack: () -> Unit, onOpenSale: (String) -> Unit) {
    val repo = LocalContainer.current.repo
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val all by remember { repo.customers.observeWithBalance("") }.collectAsStateWithLifecycle(emptyList())
    val c = all.firstOrNull { it.customer.id == customerId }
    val payments by remember(customerId) { repo.payments.observeForCustomer(customerId) }.collectAsStateWithLifecycle(emptyList())
    val sales by remember(customerId) {
        repo.sales.observeSales(0, Long.MAX_VALUE, "")
    }.collectAsStateWithLifecycle(emptyList())
    val customerSales = sales.filter { it.sale.customerId == customerId }
    var editing by remember { mutableStateOf(false) }
    var collecting by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = c?.customer?.name ?: "العميل",
        onBack = onBack,
        snackbar = snackbar,
        actions = { IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "تعديل") } },
    ) { padding ->
        if (c == null) return@ScreenScaffold
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                StatCard(
                    "الرصيد", money(c.balance), Modifier.fillMaxWidth(),
                    color = if (c.balance > 0) Danger else MaterialTheme.colorScheme.primary,
                    subtitle = if (c.balance > 0) "مبلغ مستحق على العميل" else null,
                )
            }
            item {
                Button(onClick = { collecting = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("تحصيل دفعة من العميل") }
            }
            if (payments.isNotEmpty()) {
                item { Text("الدفعات", style = MaterialTheme.typography.titleMedium) }
                items(payments, key = { it.payment.id }) { row ->
                    val p = row.payment
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("${PaymentKind.label(p.kind)} • ${PaymentMethod.label(p.method)}")
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
                }
            }
            if (customerSales.isNotEmpty()) {
                item { Text("الفواتير", style = MaterialTheme.typography.titleMedium) }
                items(customerSales, key = { "s" + it.sale.id }) { row -> SaleCard(row) { onOpenSale(row.sale.id) } }
            }
        }
    }

    if (editing && c != null) {
        CustomerEditDialog(c.customer, onDismiss = { editing = false }, onSaved = { editing = false })
    }
    if (collecting) {
        var amount by remember { mutableStateOf(if ((c?.balance ?: 0L) > 0L) Money.toInput(c!!.balance) else "") }
        var method by remember { mutableStateOf(PaymentMethod.CASH) }
        var bank by remember { mutableStateOf("") }
        var ref by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { collecting = false },
            title = { Text("تحصيل دفعة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(amount, { amount = it }, "المبلغ")
                    ChipSelector(listOf(PaymentMethod.CASH, PaymentMethod.BANK), method, { PaymentMethod.label(it) }, { method = it })
                    if (method == PaymentMethod.BANK) {
                        OutlinedTextField(bank, { bank = it }, label = { Text("البنك") }, singleLine = true)
                        OutlinedTextField(ref, { ref = it }, label = { Text("رقم مرجع التحويل *") }, singleLine = true)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = Money.parse(amount) ?: return@TextButton
                    scope.launch {
                        try {
                            repo.collectDebt(customerId, v, method, bank.trim(), ref.trim(), "")
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
