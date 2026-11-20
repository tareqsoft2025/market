package com.tareqsoft.market.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tareqsoft.market.data.Product
import com.tareqsoft.market.ui.ChipSelector
import com.tareqsoft.market.ui.Danger
import com.tareqsoft.market.ui.EmptyState
import com.tareqsoft.market.ui.LocalContainer
import com.tareqsoft.market.ui.ScreenScaffold
import com.tareqsoft.market.ui.StatCard
import com.tareqsoft.market.ui.money
import com.tareqsoft.market.ui.rememberBarcodeScanner
import com.tareqsoft.market.util.Qty

@Composable
fun ProductsScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val repo = LocalContainer.current.repo
    var query by rememberSaveable { mutableStateOf("") }
    var lowOnly by rememberSaveable { mutableStateOf(false) }

    val flow = remember(query, lowOnly) {
        if (lowOnly) repo.products.observeLowStock() else repo.products.observeSearch(query.trim())
    }
    val products by flow.collectAsStateWithLifecycle(emptyList())
    val count by remember { repo.products.observeCount() }.collectAsStateWithLifecycle(0)
    val stockValue by remember { repo.products.observeStockValue() }.collectAsStateWithLifecycle(0.0)
    val lowCount by remember { repo.products.observeLowStockCount() }.collectAsStateWithLifecycle(0)

    val scan = rememberBarcodeScanner { code ->
        lowOnly = false
        query = code
    }

    ScreenScaffold(
        title = "المخزن والأصناف",
        onBack = onBack,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("صنف جديد") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("عدد الأصناف", "$count", Modifier.weight(1f))
                StatCard("قيمة المخزون (تكلفة)", money(stockValue), Modifier.weight(1.4f))
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; lowOnly = false },
                label = { Text("بحث بالاسم أو الباركود أو التصنيف") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    Row {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "مسح") }
                        }
                        IconButton(onClick = scan) { Text("📷") }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            ChipSelector(
                options = listOf(false, true),
                selected = lowOnly,
                label = { if (it) "تحت حد الطلب ($lowCount)" else "كل الأصناف" },
                onSelect = { lowOnly = it },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            if (products.isEmpty()) {
                EmptyState(if (count == 0) "لا توجد أصناف بعد.\nاضغط \"صنف جديد\" لإضافة أول صنف." else "لا توجد نتائج")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(products, key = { it.id }) { p -> ProductRow(p) { onOpen(p.id) } }
                }
            }
        }
    }
}

@Composable
private fun ProductRow(p: Product, onClick: () -> Unit) {
    val low = p.minQuantity > 0 && p.quantity <= p.minQuantity
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, fontWeight = FontWeight.Bold)
                val sub = listOfNotNull(p.category.ifBlank { null }, p.barcode?.ifBlank { null }).joinToString(" • ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text(money(p.salePrice), color = MaterialTheme.colorScheme.primary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Qty.format(p.quantity),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (low || p.quantity < 0) Danger else Color.Unspecified,
                    fontWeight = FontWeight.Bold,
                )
                Text(p.unit, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}
