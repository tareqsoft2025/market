package com.tareqsoft.market.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tareqsoft.market.data.CartLine
import com.tareqsoft.market.data.Customer
import com.tareqsoft.market.data.MarketRepository
import com.tareqsoft.market.data.PaymentMethod
import com.tareqsoft.market.data.Product
import com.tareqsoft.market.data.Sale
import com.tareqsoft.market.ui.ChipSelector
import com.tareqsoft.market.ui.Danger
import com.tareqsoft.market.ui.EmptyState
import com.tareqsoft.market.ui.LabelValue
import com.tareqsoft.market.ui.LocalContainer
import com.tareqsoft.market.ui.NumberField
import com.tareqsoft.market.ui.ScreenScaffold
import com.tareqsoft.market.ui.Warning
import com.tareqsoft.market.ui.money
import com.tareqsoft.market.ui.rememberBarcodeScanner
import com.tareqsoft.market.util.Money
import com.tareqsoft.market.util.Qty
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Holds the cart so it survives screen rotation. */
class PosViewModel(private val repo: MarketRepository) : ViewModel() {
    val cart = mutableStateListOf<CartLine>()
    var discountText by mutableStateOf("")
    var query by mutableStateOf("")

    val subtotal: Long get() = cart.sumOf { it.total }
    val discount: Long get() = Money.parse(discountText)?.coerceIn(0, subtotal) ?: 0
    val total: Long get() = subtotal - discount

    fun add(p: Product, qty: Double = 1.0) {
        val i = cart.indexOfFirst { it.product.id == p.id }
        if (i >= 0) cart[i] = cart[i].copy(quantity = cart[i].quantity + qty)
        else cart.add(CartLine(p, qty))
    }

    fun setQty(line: CartLine, qty: Double) {
        val i = cart.indexOfFirst { it.product.id == line.product.id }
        if (i < 0) return
        if (qty <= 0) cart.removeAt(i) else cart[i] = cart[i].copy(quantity = qty)
    }

    fun setPrice(line: CartLine, price: Long) {
        val i = cart.indexOfFirst { it.product.id == line.product.id }
        if (i >= 0) cart[i] = cart[i].copy(unitPrice = price)
    }

    /** Called when Enter is pressed (USB / Bluetooth barcode scanners send Enter). */
    suspend fun addByBarcode(code: String): Boolean {
        val p = repo.products.findByBarcode(code.trim()) ?: return false
        add(p)
        return true
    }

    fun clear() {
        cart.clear()
        discountText = ""
        query = ""
    }
}

@Composable
fun PosScreen(onBack: () -> Unit, onOpenSale: (String) -> Unit) {
    val repo = LocalContainer.current.repo
    val vm: PosViewModel = viewModel { PosViewModel(repo) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var checkout by remember { mutableStateOf(false) }
    var editLine by remember { mutableStateOf<CartLine?>(null) }
    var done by remember { mutableStateOf<Pair<Sale, Long>?>(null) } // sale + change due

    val results by remember(vm.query) {
        val q = vm.query.trim()
        if (q.isEmpty()) flowOf(emptyList<Product>()) else repo.products.observeSearch(q)
    }.collectAsStateWithLifecycle(emptyList())

    fun onEnter() {
        val q = vm.query.trim()
        if (q.isEmpty()) return
        scope.launch {
            if (vm.addByBarcode(q)) {
                vm.query = ""
            } else if (results.size == 1) {
                vm.add(results.first()); vm.query = ""
            } else if (results.isEmpty()) {
                snackbar.showSnackbar("لا يوجد صنف بهذا الباركود: $q")
            }
        }
    }

    val scan = rememberBarcodeScanner { code ->
        scope.launch {
            if (!vm.addByBarcode(code)) snackbar.showSnackbar("لا يوجد صنف بهذا الباركود: $code")
        }
    }

    ScreenScaffold(
        title = "نقطة البيع",
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            if (vm.cart.isNotEmpty()) {
                IconButton(onClick = { vm.clear() }) { Icon(Icons.Filled.Delete, "تفريغ السلة") }
            }
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("الإجمالي: ${money(vm.subtotal)}", color = Color.Gray)
                            Text(money(vm.total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        NumberField(vm.discountText, { vm.discountText = it }, "خصم", Modifier.width(110.dp))
                    }
                    Button(
                        onClick = { checkout = true },
                        enabled = vm.cart.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                    ) { Text("إتمام البيع (${vm.cart.size} صنف)") }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = vm.query,
                onValueChange = { vm.query = it },
                label = { Text("امسح الباركود أو اكتب اسم الصنف") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    Row {
                        if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) { Icon(Icons.Filled.Clear, "مسح") }
                        IconButton(onClick = scan) { Text("📷") }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onEnter() }),
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )

            if (vm.query.isNotBlank()) {
                LazyColumn(Modifier.heightIn(max = 260.dp).padding(horizontal = 12.dp)) {
                    items(results, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                Text("المتوفر: ${Qty.format(p.quantity)} ${p.unit}", style = MaterialTheme.typography.bodySmall, color = if (p.quantity <= 0) Danger else Color.Gray)
                            }
                            Text(money(p.salePrice))
                            IconButton(onClick = { vm.add(p); vm.query = "" }) { Icon(Icons.Filled.Add, "إضافة") }
                        }
                        HorizontalDivider()
                    }
                }
            }

            if (vm.cart.isEmpty()) {
                EmptyState("السلة فارغة\nامسح باركود صنف أو ابحث بالاسم")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(vm.cart, key = { it.product.id }) { line ->
                        CartRow(
                            line,
                            onMinus = { vm.setQty(line, line.quantity - 1) },
                            onPlus = { vm.setQty(line, line.quantity + 1) },
                            onEdit = { editLine = line },
                        )
                    }
                }
            }
        }
    }

    editLine?.let { line ->
        EditLineDialog(
            line = line,
            onDismiss = { editLine = null },
            onSave = { qty, price ->
                vm.setPrice(line, price)
                vm.setQty(line, qty)
                editLine = null
            },
        )
    }

    if (checkout) {
        CheckoutDialog(
            total = vm.total,
            onDismiss = { checkout = false },
            onConfirm = { method, customer, bankName, ref, received ->
                scope.launch {
                    try {
                        val sale = repo.createSale(
                            lines = vm.cart.toList(),
                            discount = vm.discount,
                            method = method,
                            customerId = customer?.id,
                            bankName = bankName,
                            reference = ref,
                            note = "",
                        )
                        checkout = false
                        val change = if (method == PaymentMethod.CASH && received != null) received - sale.total else 0L
                        done = sale to change
                        vm.clear()
                    } catch (e: Exception) {
                        snackbar.showSnackbar(e.message ?: "خطأ")
                    }
                }
            },
        )
    }

    done?.let { (sale, change) ->
        AlertDialog(
            onDismissRequest = { done = null },
            title = { Text("تم حفظ الفاتورة رقم ${sale.invoiceNo}") },
            text = {
                Column {
                    LabelValue("الإجمالي", money(sale.total), bold = true)
                    LabelValue("طريقة الدفع", PaymentMethod.label(sale.paymentMethod))
                    if (change > 0) LabelValue("الباقي للعميل", money(change), Warning, bold = true)
                }
            },
            confirmButton = { Button(onClick = { done = null }) { Text("فاتورة جديدة") } },
            dismissButton = { TextButton(onClick = { done = null; onOpenSale(sale.id) }) { Text("عرض الفاتورة") } },
        )
    }
}

@Composable
private fun CartRow(line: CartLine, onMinus: () -> Unit, onPlus: () -> Unit, onEdit: () -> Unit) {
    val short = line.quantity > line.product.quantity
    Card(onClick = onEdit, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(line.product.name, fontWeight = FontWeight.Bold)
                Text("${money(line.unitPrice)} × ${Qty.format(line.quantity)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                if (short) Text("تنبيه: الكمية في المخزن ${Qty.format(line.product.quantity)} فقط", style = MaterialTheme.typography.bodySmall, color = Warning)
            }
            OutlinedButton(onClick = onMinus, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(40.dp)) { Text("−") }
            Text(Qty.format(line.quantity), Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onPlus, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(40.dp)) { Text("+") }
            Text(money(line.total), Modifier.width(90.dp).padding(start = 8.dp), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EditLineDialog(line: CartLine, onDismiss: () -> Unit, onSave: (Double, Long) -> Unit) {
    var qty by remember { mutableStateOf(Qty.format(line.quantity)) }
    var price by remember { mutableStateOf(Money.toInput(line.unitPrice)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(line.product.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(qty, { qty = it }, "الكمية (أو الوزن)")
                NumberField(price, { price = it }, "سعر الوحدة")
                Text("ضع الكمية 0 لحذف الصنف من السلة", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = Qty.parse(qty) ?: return@TextButton
                val p = Money.parse(price) ?: return@TextButton
                onSave(q, p)
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun CheckoutDialog(
    total: Long,
    onDismiss: () -> Unit,
    onConfirm: (method: String, customer: Customer?, bankName: String, ref: String, received: Long?) -> Unit,
) {
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var received by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var ref by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf<Customer?>(null) }
    var pickCustomer by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الدفع - ${money(total)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipSelector(
                    options = listOf(PaymentMethod.CASH, PaymentMethod.BANK, PaymentMethod.CREDIT),
                    selected = method,
                    label = { PaymentMethod.label(it) },
                    onSelect = { method = it },
                )
                when (method) {
                    PaymentMethod.CASH -> {
                        NumberField(received, { received = it }, "المبلغ المستلم (اختياري)")
                        val r = Money.parse(received)
                        if (r != null) {
                            val change = r - total
                            LabelValue(if (change >= 0) "الباقي" else "ناقص", money(kotlin.math.abs(change)), if (change < 0) Danger else Warning, true)
                        }
                    }
                    PaymentMethod.BANK -> {
                        OutlinedTextField(bankName, { bankName = it }, label = { Text("البنك / المحفظة") }, singleLine = true)
                        OutlinedTextField(ref, { ref = it }, label = { Text("رقم مرجع التحويل *") }, singleLine = true)
                        Text("سيظهر التحويل في شاشة المراجعة حتى يتم تأكيده.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
                if (method == PaymentMethod.CREDIT || customer != null) {
                    OutlinedButton(onClick = { pickCustomer = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(customer?.let { "العميل: ${it.name}" } ?: "اختر العميل *")
                    }
                } else {
                    TextButton(onClick = { pickCustomer = true }) { Text("ربط الفاتورة بعميل (اختياري)") }
                }
                if (customer != null) {
                    TextButton(onClick = { customer = null }) {
                        Icon(Icons.Filled.Close, null); Text("إزالة العميل")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(method, customer, bankName.trim(), ref.trim(), Money.parse(received))
            }) { Text("تأكيد البيع") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("رجوع") } },
    )

    if (pickCustomer) {
        CustomerPickerDialog(
            onDismiss = { pickCustomer = false },
            onPick = { customer = it; pickCustomer = false },
        )
    }
}
