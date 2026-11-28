package com.tareqsoft.market.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import com.tareqsoft.market.ui.AppIcons
import com.tareqsoft.market.ui.IndigoDeep
import com.tareqsoft.market.ui.Saffron
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
import androidx.compose.foundation.layout.size
import com.tareqsoft.market.ui.PrimaryActionButton
import com.tareqsoft.market.ui.ReceiptInk
import com.tareqsoft.market.ui.ReceiptPanel
import com.tareqsoft.market.ui.ReceiptRule
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
import com.tareqsoft.market.data.TransferInfo
import com.tareqsoft.market.ui.TransferFields
import com.tareqsoft.market.ui.TransferFormState
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
/** One customer's open bill. Several can be open at once. */
class CartTab(val id: Long, val number: Int) {
    val lines = mutableStateListOf<CartLine>()
    var discountText by mutableStateOf("")

    val subtotal: Long get() = lines.sumOf { it.total }
    val discount: Long get() = Money.parse(discountText)?.coerceIn(0, subtotal) ?: 0
    val total: Long get() = subtotal - discount
    val label: String get() = "زبون $number"
}

/**
 * Open bills for the point of sale. Kept for the whole time the app is open,
 * so the cashier can leave the screen (to check stock, say) and come back to
 * the same customers.
 */
class PosViewModel(private val repo: MarketRepository) : ViewModel() {
    private var nextId = 1L
    val tabs = mutableStateListOf(CartTab(nextId++, 1))
    var activeId by mutableStateOf(tabs.first().id)
        private set
    var query by mutableStateOf("")

    val active: CartTab get() = tabs.firstOrNull { it.id == activeId } ?: tabs.first()

    // The screen works on the active customer's bill.
    val cart get() = active.lines
    var discountText: String
        get() = active.discountText
        set(v) { active.discountText = v }
    val subtotal: Long get() = active.subtotal
    val discount: Long get() = active.discount
    val total: Long get() = active.total

    /** Bills that have something in them (shown on the home screen). */
    val openBills: Int get() = tabs.count { it.lines.isNotEmpty() }

    /** Starts a bill for another customer and switches to it. */
    fun newTab() {
        // Reuse an empty bill instead of piling up empty tabs.
        tabs.firstOrNull { it.lines.isEmpty() && it.id != activeId }?.let { activeId = it.id; return }
        val number = (tabs.maxOfOrNull { it.number } ?: 0) + 1
        val tab = CartTab(nextId++, number)
        tabs.add(tab)
        activeId = tab.id
        query = ""
    }

    fun select(id: Long) {
        activeId = id
        query = ""
    }

    /** Closes a bill. The last one is emptied instead, and numbering starts again at 1. */
    fun closeTab(id: Long) {
        val i = tabs.indexOfFirst { it.id == id }
        if (i < 0) return
        if (tabs.size == 1) {
            tabs[0] = CartTab(nextId++, 1)
            activeId = tabs[0].id
        } else {
            tabs.removeAt(i)
            if (activeId == id) activeId = tabs[(i - 1).coerceAtLeast(0)].id
        }
        query = ""
    }

    /** Item sold by weight waiting for the cashier to enter a weight or an amount. */
    var weighing by mutableStateOf<Product?>(null)
    private var weighingTab: CartTab? = null

    fun add(p: Product, qty: Double = 1.0, price: Long = p.salePrice, tab: CartTab = active) {
        val lines = tab.lines
        val i = lines.indexOfFirst { it.product.id == p.id }
        if (i >= 0) lines[i] = lines[i].copy(quantity = lines[i].quantity + qty)
        else lines.add(CartLine(p, qty, price))
    }

    /** Pieces go straight into the bill; weighed items first ask for the weight or amount. */
    fun addOrWeigh(p: Product, tab: CartTab = active) {
        if (p.soldByWeight) {
            weighingTab = tab
            weighing = p
        } else {
            add(p, tab = tab)
        }
    }

    /** Called from the weight dialog. */
    fun addWeighed(p: Product, qty: Double, price: Long) {
        val tab = weighingTab?.takeIf { it in tabs } ?: active
        if (qty > 0) add(p, qty, price, tab)
        weighing = null
        weighingTab = null
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

    /**
     * Called when Enter is pressed (USB / Bluetooth barcode scanners send Enter).
     * The item goes to the bill that was open when the code was scanned, even if
     * the cashier switches customer while it is being looked up.
     */
    suspend fun addByBarcode(code: String): Boolean {
        val tab = active
        val p = repo.products.findByBarcode(code.trim()) ?: return false
        addOrWeigh(p, if (tab in tabs) tab else active)
        return true
    }

    /** The active bill is finished (sold or emptied): close it. */
    fun clear() = closeTab(activeId)
}

/** Finds the hosting activity, so the point of sale keeps its bills app-wide. */
private fun android.content.Context.findActivity(): androidx.activity.ComponentActivity {
    var c = this
    while (c is android.content.ContextWrapper) {
        if (c is androidx.activity.ComponentActivity) return c
        c = c.baseContext
    }
    error("No activity")
}

/** The point-of-sale state shared by the sales screen and the home screen. */
@Composable
fun rememberPos(): PosViewModel {
    val repo = LocalContainer.current.repo
    val activity = androidx.compose.ui.platform.LocalContext.current.findActivity()
    return viewModel(viewModelStoreOwner = activity) { PosViewModel(repo) }
}

@Composable
fun PosScreen(onBack: () -> Unit, onOpenSale: (String) -> Unit) {
    val repo = LocalContainer.current.repo
    val vm: PosViewModel = rememberPos()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var checkout by remember { mutableStateOf(false) }
    var editLine by remember { mutableStateOf<CartLine?>(null) }
    var done by remember { mutableStateOf<Pair<Sale, Long>?>(null) } // sale + change due

    var dept by rememberSaveable { mutableStateOf("") }
    val categories by remember { repo.categories.observeAll() }.collectAsStateWithLifecycle(emptyList())
    val results by remember(vm.query, dept) { repo.products.observeSearch(vm.query.trim(), dept) }
        .collectAsStateWithLifecycle(emptyList())
    val inCart = vm.cart.associate { it.product.id to it.quantity }

    fun onEnter() {
        val q = vm.query.trim()
        if (q.isEmpty()) return
        val matches = results
        // Clear the field right away: barcode scanners can send the next code
        // before the database lookup finishes, and the codes must not merge.
        vm.query = ""
        scope.launch {
            if (vm.addByBarcode(q)) return@launch
            val byName = matches.filter { it.name.contains(q) || it.barcode == q }
            when {
                byName.size == 1 -> vm.addOrWeigh(byName.first())
                byName.isEmpty() -> snackbar.showSnackbar("لا يوجد صنف بهذا الباركود: $q")
                else -> vm.query = q // several matches: keep the text so the user can pick
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
        bottomBar = {
            ReceiptPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("الإجمالي", color = ReceiptInk.copy(alpha = 0.65f), style = MaterialTheme.typography.labelLarge)
                        Text(
                            money(vm.total),
                            style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = ReceiptInk,
                        )
                        if (vm.discount > 0) {
                            Text(
                                "بعد خصم ${money(vm.discount)} من ${money(vm.subtotal)}",
                                color = Danger, style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    NumberField(vm.discountText, { vm.discountText = it }, "خصم", Modifier.width(104.dp))
                }
                PrimaryActionButton(
                    text = if (vm.cart.isEmpty()) "إتمام البيع" else "إتمام البيع (${vm.cart.size} صنف)",
                    onClick = { checkout = true },
                    enabled = vm.cart.isNotEmpty(),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ---- Customers being served
            CustomerTabs(vm)

            // ---- Search / scan
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    label = { Text("الباركود أو اسم الصنف") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) { Icon(Icons.Filled.Clear, "مسح") }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onEnter() }),
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    onClick = scan,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp).size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(AppIcons.Scan, "مسح بالكاميرا", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }

            // ---- Departments
            ChipSelector(
                options = listOf("") + categories.map { it.name },
                selected = dept,
                label = { it.ifEmpty { "كل الأصناف" } },
                onSelect = { dept = it },
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp),
            )

            // ---- Quick picks: tap a card to add it
            if (results.isEmpty()) {
                Text(
                    if (vm.query.isBlank()) "لا توجد أصناف في هذا القسم" else "لا يوجد صنف بهذا الاسم أو الباركود",
                    color = Color.Gray, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results, key = { it.id }) { p ->
                        ProductTile(p, inCart[p.id]) { vm.addOrWeigh(p); if (vm.query.isNotBlank()) vm.query = "" }
                    }
                }
            }

            // ---- Cart
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("السلة", style = MaterialTheme.typography.titleMedium)
                if (vm.cart.isNotEmpty()) {
                    Text(
                        "  ${vm.cart.size} صنف",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.clear() }) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp), tint = Danger)
                        Text(if (vm.tabs.size > 1) " إلغاء الفاتورة" else " تفريغ", color = Danger)
                    }
                }
            }
            if (vm.cart.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(AppIcons.Scan, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) }
                    Text("السلة فارغة", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        "امسح الباركود، أو اضغط على صنف من القائمة بالأعلى",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(vm.cart, key = { _, l -> l.product.id }) { i, line ->
                        CartRow(
                            index = i + 1,
                            line = line,
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
        QuantityDialog(
            product = line.product,
            unitPrice = line.unitPrice,
            initialQty = line.quantity,
            onDismiss = { editLine = null },
            onSave = { qty, price ->
                vm.setPrice(line, price)
                vm.setQty(line, qty)
                editLine = null
            },
        )
    }

    vm.weighing?.let { p ->
        QuantityDialog(
            product = p,
            unitPrice = p.salePrice,
            initialQty = null,
            onDismiss = { vm.weighing = null },
            onSave = { qty, price -> vm.addWeighed(p, qty, price) },
        )
    }

    if (checkout) {
        CheckoutDialog(
            total = vm.total,
            onDismiss = { checkout = false },
            onConfirm = { method, customer, transfer, received ->
                scope.launch {
                    try {
                        val sale = repo.createSale(
                            lines = vm.cart.toList(),
                            discount = vm.discount,
                            method = method,
                            customerId = customer?.id,
                            transfer = transfer,
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

/**
 * A tab per customer being served. Tap to switch, "+" to start another
 * customer's bill while the first one is still shopping.
 */
@Composable
private fun CustomerTabs(vm: PosViewModel) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Keep the active customer in view (e.g. right after adding one).
    androidx.compose.runtime.LaunchedEffect(vm.activeId, vm.tabs.size) {
        val i = vm.tabs.indexOfFirst { it.id == vm.activeId }
        if (i >= 0) listState.animateScrollToItem(i)
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.tabs, key = { it.id }) { tab ->
                val selected = tab.id == vm.activeId
                Surface(
                    onClick = { vm.select(tab.id) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                    border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.heightIn(min = 52.dp),
                ) {
                    Row(
                        Modifier.padding(start = 14.dp, end = if (vm.tabs.size > 1) 4.dp else 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(tab.label, style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (tab.lines.isEmpty()) "فارغة" else "${tab.lines.size} صنف • ${money(tab.total)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (vm.tabs.size > 1) {
                            IconButton(onClick = { vm.closeTab(tab.id) }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Filled.Close, "إغلاق ${tab.label}", Modifier.size(16.dp),
                                    tint = if (selected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        // Always visible: starting a new customer is one tap.
        Surface(
            onClick = { vm.newTab() },
            shape = RoundedCornerShape(14.dp),
            color = Saffron.copy(alpha = 0.22f),
            contentColor = IndigoDeep,
            modifier = Modifier.heightIn(min = 52.dp),
        ) {
            Column(
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.Add, null, Modifier.size(20.dp))
                Text("زبون جديد", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** One line of the bill, like a line in the shop's ledger. */
@Composable
private fun CartRow(index: Int, line: CartLine, onMinus: () -> Unit, onPlus: () -> Unit, onEdit: () -> Unit) {
    val short = line.quantity > line.product.quantity
    val weighed = line.product.soldByWeight
    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text("$index", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(line.product.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(
                    "${money(line.unitPrice)} × ${Qty.format(line.quantity)}" + if (weighed) " ${line.product.unit}" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (short) Text("المتوفر ${Qty.format(line.product.quantity)} فقط", style = MaterialTheme.typography.labelSmall, color = Warning)
            }
            if (weighed) {
                Surface(
                    onClick = onEdit,
                    shape = RoundedCornerShape(50),
                    color = Saffron.copy(alpha = 0.16f),
                ) {
                    Text(
                        "${Qty.format(line.quantity)} ${line.product.unit}",
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge, color = Warning,
                    )
                }
            } else {
                // − qty + grouped in one capsule
                Row(
                    Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                        .height(44.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onMinus, modifier = Modifier.size(44.dp)) {
                        Text("−", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        Qty.format(line.quantity),
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        modifier = Modifier.widthIn(min = 22.dp),
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = onPlus, modifier = Modifier.size(44.dp)) {
                        Text("+", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(
                money(line.total),
                Modifier.widthIn(min = 84.dp).padding(start = 10.dp),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                textAlign = TextAlign.End,
            )
        }
    }
}

/** Quick-pick card: tap to add. Shows how many are already in the cart. */
@Composable
private fun ProductTile(p: Product, inCart: Double?, onClick: () -> Unit) {
    val out = p.quantity <= 0
    Box {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                if (inCart != null) 2.dp else 1.dp,
                if (inCart != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.width(128.dp).height(96.dp),
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(p.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, minLines = 2)
                Column {
                    Text(
                        money(p.salePrice) + if (p.soldByWeight) " / ${p.unit}" else "",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, maxLines = 1,
                    )
                    Text(
                        if (out) "نفد من المخزن" else "متوفر ${Qty.format(p.quantity)} ${p.unit}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (out) Danger else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
            }
        }
        if (inCart != null) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).background(Saffron, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(Qty.format(inCart).take(4), style = MaterialTheme.typography.labelSmall, color = IndigoDeep, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Quantity / weight entry. The weight and the amount are linked: typing the
 * amount the customer wants to pay divides it by the unit price (20 ÷ 5 = 4
 * أوقية), and typing the weight multiplies it.
 */
@Composable
private fun QuantityDialog(
    product: Product,
    unitPrice: Long,
    initialQty: Double?,
    onDismiss: () -> Unit,
    onSave: (Double, Long) -> Unit,
) {
    var price by remember { mutableStateOf(Money.toInput(unitPrice)) }
    var qty by remember { mutableStateOf(initialQty?.let { Qty.format(it) } ?: "") }
    var amount by remember {
        mutableStateOf(initialQty?.let { Money.toInput(Math.round(unitPrice * it)) } ?: "")
    }
    val p = Money.parse(price)

    fun fromQty(text: String) {
        qty = text
        val q = Qty.parse(text)
        amount = if (q != null && p != null) Money.toInput(Math.round(p * q)) else ""
    }
    fun fromAmount(text: String) {
        amount = text
        val a = Money.parse(text)
        qty = if (a != null && p != null && p > 0) Qty.format(a.toDouble() / p) else ""
    }

    val weighed = product.soldByWeight
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "السعر: ${money(p ?: unitPrice)} لكل ${product.unit}",
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                )
                NumberField(qty, { fromQty(it) }, if (weighed) "الوزن (${product.unit})" else "الكمية")
                if (weighed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("1/4" to "¼", "1/2" to "½", "3/4" to "¾", "1" to "1", "2" to "2").forEach { (v, label) ->
                            OutlinedButton(
                                onClick = { fromQty(v) },
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            ) { Text(label, style = MaterialTheme.typography.titleMedium) }
                        }
                    }
                }
                NumberField(amount, { fromAmount(it) }, "المبلغ المطلوب")
                if (weighed) {
                    Text(
                        "اكتب المبلغ الذي يريده الزبون ويُحسب الوزن تلقائياً، أو اكتب الوزن ويُحسب المبلغ.",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                    )
                }
                NumberField(price, {
                    price = it
                    val np = Money.parse(it)
                    val q = Qty.parse(qty)
                    if (np != null && q != null) amount = Money.toInput(Math.round(np * q))
                }, "سعر ${product.unit}")
                if (initialQty != null) {
                    Text("ضع الكمية 0 لحذف الصنف من السلة", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = Qty.parse(qty) ?: return@Button
                val pr = Money.parse(price) ?: return@Button
                onSave(q, pr)
            }) { Text(if (initialQty == null) "إضافة للسلة" else "حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun CheckoutDialog(
    total: Long,
    onDismiss: () -> Unit,
    onConfirm: (method: String, customer: Customer?, transfer: TransferInfo?, received: Long?) -> Unit,
) {
    var method by remember { mutableStateOf(PaymentMethod.CASH) }
    var received by remember { mutableStateOf("") }
    val transfer = remember { TransferFormState() }
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
                    PaymentMethod.BANK -> TransferFields(transfer, amount = total)
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
                onConfirm(method, customer, if (method == PaymentMethod.BANK) transfer.toInfo() else null, Money.parse(received))
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
