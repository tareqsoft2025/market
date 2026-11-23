package com.tareqsoft.market.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.tareqsoft.market.Routes
import com.tareqsoft.market.data.MovementType
import com.tareqsoft.market.data.Product
import com.tareqsoft.market.data.StockMovement
import com.tareqsoft.market.ui.ConfirmDialog
import com.tareqsoft.market.ui.Danger
import com.tareqsoft.market.ui.LabelValue
import com.tareqsoft.market.ui.LocalContainer
import com.tareqsoft.market.ui.DropdownField
import com.tareqsoft.market.ui.TextInputDialog
import com.tareqsoft.market.ui.UNIT_SUGGESTIONS
import com.tareqsoft.market.ui.BottomSpacer
import com.tareqsoft.market.ui.PrimaryActionButton
import com.tareqsoft.market.ui.NumberField
import com.tareqsoft.market.ui.ScreenScaffold
import com.tareqsoft.market.ui.money
import com.tareqsoft.market.ui.rememberBarcodeScanner
import com.tareqsoft.market.util.Dates
import com.tareqsoft.market.util.Money
import com.tareqsoft.market.util.Qty
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val WEIGHT_UNITS = setOf("كجم", "جرام", "أوقية", "رطل")

@Composable
fun ProductEditScreen(productId: String, onBack: () -> Unit) {
    val repo = LocalContainer.current.repo
    val isNew = productId == Routes.NEW
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var name by rememberSaveable { mutableStateOf("") }
    var barcode by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("حبة") }
    var cost by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var openingQty by rememberSaveable { mutableStateOf("") }
    var minQty by rememberSaveable { mutableStateOf("") }
    var soldByWeight by rememberSaveable { mutableStateOf(false) }
    var addingCategory by remember { mutableStateOf(false) }
    var categoryError by remember { mutableStateOf<String?>(null) }
    val categories by remember { repo.categories.observeAll() }.collectAsStateWithLifecycle(emptyList())
    var loaded by rememberSaveable { mutableStateOf(isNew) }

    var showReceive by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }
    var showArchive by remember { mutableStateOf(false) }

    val live by remember(productId) {
        if (isNew) flowOf<Product?>(null) else repo.products.observe(productId)
    }.collectAsStateWithLifecycle(null)
    val movements by remember(productId) {
        if (isNew) flowOf(emptyList<StockMovement>()) else repo.movements.observeForProduct(productId)
    }.collectAsStateWithLifecycle(emptyList())

    LaunchedEffect(productId) {
        if (!loaded) {
            repo.products.get(productId)?.let { p ->
                name = p.name
                barcode = p.barcode ?: ""
                category = p.category
                unit = p.unit
                cost = Money.toInput(p.costPrice)
                price = Money.toInput(p.salePrice)
                minQty = Qty.format(p.minQuantity)
                soldByWeight = p.soldByWeight
            }
            loaded = true
        }
    }

    val scan = rememberBarcodeScanner { barcode = it }

    fun save() {
        val costV = if (cost.isBlank()) 0L else Money.parse(cost)
        val priceV = Money.parse(price)
        val minV = if (minQty.isBlank()) 0.0 else Qty.parse(minQty)
        val openV = if (openingQty.isBlank()) 0.0 else Qty.parse(openingQty)
        if (costV == null || priceV == null || minV == null || openV == null) {
            scope.launch { snackbar.showSnackbar("تأكد من الأرقام المدخلة (سعر البيع مطلوب)") }
            return
        }
        scope.launch {
            try {
                if (isNew) {
                    repo.addProduct(
                        Product(
                            name = name.trim(), barcode = barcode.trim().ifBlank { null },
                            category = category.trim().takeUnless { it == "بدون قسم" } ?: "", unit = unit.trim().ifBlank { "حبة" },
                            costPrice = costV, salePrice = priceV, quantity = openV, minQuantity = minV,
                            soldByWeight = soldByWeight,
                        )
                    )
                } else {
                    val old = repo.products.get(productId) ?: return@launch
                    repo.updateProduct(
                        old.copy(
                            name = name.trim(), barcode = barcode.trim().ifBlank { null },
                            category = category.trim().takeUnless { it == "بدون قسم" } ?: "", unit = unit.trim().ifBlank { "حبة" },
                            costPrice = costV, salePrice = priceV, minQuantity = minV,
                            soldByWeight = soldByWeight,
                        )
                    )
                }
                onBack()
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "خطأ")
            }
        }
    }

    ScreenScaffold(
        title = if (isNew) "صنف جديد" else "تعديل صنف",
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            if (!isNew) {
                IconButton(onClick = { showArchive = true }) { Icon(Icons.Filled.Delete, "حذف") }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val p = live
            if (p != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("الكمية الحالية: ", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${Qty.format(p.quantity)} ${p.unit}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (p.quantity <= p.minQuantity) Danger else MaterialTheme.colorScheme.primary,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { showReceive = true }, modifier = Modifier.weight(1f)) { Text("توريد بضاعة") }
                            OutlinedButton(onClick = { showAdjust = true }, modifier = Modifier.weight(1f)) { Text("جرد / تسوية") }
                        }
                    }
                }
            }

            OutlinedTextField(name, { name = it }, label = { Text("اسم الصنف *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                barcode, { barcode = it }, label = { Text("الباركود") }, singleLine = true,
                trailingIcon = { IconButton(onClick = scan) { Text("📷") } },
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                value = category,
                onValueChange = { category = it },
                options = listOf("بدون قسم") + categories.map { it.name },
                label = "القسم",
                placeholder = "اختر القسم",
                footer = "+ إضافة قسم جديد" to { addingCategory = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DropdownField(
                    value = unit,
                    onValueChange = {
                        unit = it
                        if (it in WEIGHT_UNITS) soldByWeight = true
                    },
                    options = UNIT_SUGGESTIONS,
                    label = "وحدة البيع",
                    editable = true,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    Modifier.weight(1f).clickable { soldByWeight = !soldByWeight },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = soldByWeight, onCheckedChange = { soldByWeight = it })
                    Text("يباع بالوزن")
                }
            }
            if (soldByWeight) {
                Text(
                    "عند البيع تكتب الوزن أو المبلغ الذي يريده الزبون، ويُحسب الآخر تلقائياً.",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(cost, { cost = it }, "سعر الشراء (التكلفة)", Modifier.weight(1f))
                NumberField(price, { price = it }, "سعر البيع *", Modifier.weight(1f))
            }
            val c = Money.parse(cost)
            val s = Money.parse(price)
            if (c != null && s != null && s > 0) {
                val margin = s - c
                Text(
                    "هامش الربح: ${money(margin)} (${"%.1f".format((margin * 100.0) / s)}%)",
                    color = if (margin < 0) Danger else Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isNew) NumberField(openingQty, { openingQty = it }, "الكمية الافتتاحية", Modifier.weight(1f))
                NumberField(minQty, { minQty = it }, "حد إعادة الطلب", Modifier.weight(1f))
            }
            PrimaryActionButton("حفظ", onClick = { save() })

            if (movements.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("حركة الصنف (كرت الصنف)", style = MaterialTheme.typography.titleMedium)
                movements.take(100).forEach { MovementRow(it) }
            }
            BottomSpacer()
        }
    }

    if (addingCategory) {
        TextInputDialog(
            title = "قسم جديد",
            label = "اسم القسم",
            confirmText = "إضافة",
            error = categoryError,
            onConfirm = { n ->
                scope.launch {
                    try {
                        repo.addCategory(n)
                        category = n.trim()
                        addingCategory = false
                        categoryError = null
                    } catch (e: Exception) {
                        categoryError = e.message
                    }
                }
            },
            onDismiss = { addingCategory = false; categoryError = null },
        )
    }

    if (showReceive && live != null) {
        ReceiveDialog(
            product = live!!,
            onDismiss = { showReceive = false },
            onConfirm = { qty, unitCost, note ->
                scope.launch {
                    try {
                        repo.receiveStock(productId, qty, unitCost, note)
                        showReceive = false
                        snackbar.showSnackbar("تم التوريد")
                    } catch (e: Exception) {
                        snackbar.showSnackbar(e.message ?: "خطأ")
                    }
                }
            },
        )
    }
    if (showAdjust && live != null) {
        AdjustDialog(
            product = live!!,
            onDismiss = { showAdjust = false },
            onConfirm = { qty, note ->
                scope.launch {
                    try {
                        repo.adjustStock(productId, qty, note)
                        showAdjust = false
                        snackbar.showSnackbar("تم تعديل الكمية")
                    } catch (e: Exception) {
                        snackbar.showSnackbar(e.message ?: "خطأ")
                    }
                }
            },
        )
    }
    if (showArchive) {
        ConfirmDialog(
            title = "حذف الصنف",
            text = "سيتم إخفاء الصنف من القوائم ونقطة البيع. الفواتير السابقة تبقى كما هي.",
            confirmText = "حذف",
            onConfirm = { scope.launch { repo.archiveProduct(productId); onBack() } },
            onDismiss = { showArchive = false },
        )
    }
}

@Composable
private fun MovementRow(m: StockMovement) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(MovementType.label(m.type), fontWeight = FontWeight.Bold)
            Text(
                (if (m.quantityChange < 0) "صادر " else "وارد ") + Qty.format(kotlin.math.abs(m.quantityChange)) + "   • الرصيد ${Qty.format(m.quantityAfter)}",
                color = if (m.quantityChange < 0) Danger else MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            listOf(Dates.dateTime(m.createdAt), m.note).filter { it.isNotBlank() }.joinToString(" • "),
            style = MaterialTheme.typography.bodySmall, color = Color.Gray,
        )
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ReceiveDialog(product: Product, onDismiss: () -> Unit, onConfirm: (Double, Long?, String) -> Unit) {
    var qty by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf(Money.toInput(product.costPrice)) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("توريد: ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(qty, { qty = it }, "الكمية الواردة")
                NumberField(cost, { cost = it }, "تكلفة الوحدة")
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة (المورد / رقم الفاتورة)") })
                val q = Qty.parse(qty)
                if (q != null) LabelValue("الكمية بعد التوريد", Qty.format(product.quantity + q), bold = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = Qty.parse(qty) ?: return@TextButton
                onConfirm(q, Money.parse(cost), note.trim())
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun AdjustDialog(product: Product, onDismiss: () -> Unit, onConfirm: (Double, String) -> Unit) {
    var qty by remember { mutableStateOf(Qty.format(product.quantity)) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("جرد: ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("أدخل الكمية الفعلية الموجودة على الرف / في المخزن.")
                NumberField(qty, { qty = it }, "الكمية الفعلية")
                OutlinedTextField(note, { note = it }, label = { Text("سبب التسوية") })
                val q = Qty.parse(qty)
                if (q != null) {
                    val diff = q - product.quantity
                    LabelValue("الفرق", (if (diff > 0) "+" else "") + Qty.format(diff), if (diff < 0) Danger else Color.Unspecified, true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = Qty.parse(qty) ?: return@TextButton
                onConfirm(q, note.trim())
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
