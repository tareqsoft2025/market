package com.tareqsoft.market.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tareqsoft.market.data.Category
import com.tareqsoft.market.data.CategoryWithCount
import com.tareqsoft.market.ui.AppIcons
import com.tareqsoft.market.ui.ConfirmDialog
import com.tareqsoft.market.ui.EmptyState
import com.tareqsoft.market.ui.LocalContainer
import com.tareqsoft.market.ui.ScreenScaffold
import com.tareqsoft.market.ui.TextInputDialog
import kotlinx.coroutines.launch

/** Store departments: add, rename and delete. Tapping one shows its items. */
@Composable
fun CategoriesScreen(onBack: () -> Unit, onOpenCategory: (String) -> Unit) {
    val repo = LocalContainer.current.repo
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val list by remember { repo.categories.observeWithCounts() }.collectAsStateWithLifecycle(emptyList())

    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<CategoryWithCount?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(
        title = "أقسام المحل",
        onBack = onBack,
        snackbar = snackbar,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("قسم جديد") },
            )
        },
    ) { padding ->
        if (list.isEmpty()) {
            EmptyState("لا توجد أقسام بعد.\nأضف أقساماً مثل: مواد غذائية، ألبان، منظفات.", Modifier.padding(padding))
            return@ScreenScaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(list, key = { it.category.id }) { row ->
                Card(
                    onClick = { onOpenCategory(row.category.name) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Departments, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(row.category.name, fontWeight = FontWeight.Bold)
                            Text(
                                if (row.productCount == 0) "لا توجد أصناف" else "${row.productCount} صنف",
                                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                            )
                        }
                        IconButton(onClick = { renaming = row.category }) { Icon(Icons.Filled.Edit, "تعديل الاسم") }
                        IconButton(onClick = { deleting = row }) { Icon(Icons.Filled.Delete, "حذف", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    if (adding) {
        TextInputDialog(
            title = "قسم جديد",
            label = "اسم القسم",
            confirmText = "إضافة",
            error = error,
            onConfirm = { name ->
                scope.launch {
                    try {
                        repo.addCategory(name); adding = false; error = null
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onDismiss = { adding = false; error = null },
        )
    }
    renaming?.let { c ->
        TextInputDialog(
            title = "تعديل اسم القسم",
            label = "اسم القسم",
            initial = c.name,
            error = error,
            onConfirm = { name ->
                scope.launch {
                    try {
                        repo.renameCategory(c, name); renaming = null; error = null
                    } catch (e: Exception) {
                        error = e.message
                    }
                }
            },
            onDismiss = { renaming = null; error = null },
        )
    }
    deleting?.let { row ->
        ConfirmDialog(
            title = "حذف قسم ${row.category.name}",
            text = if (row.productCount == 0) "سيُحذف القسم." else "الأصناف (${row.productCount}) لن تُحذف، لكنها ستصبح بدون قسم.",
            confirmText = "حذف",
            onConfirm = { scope.launch { repo.deleteCategory(row.category) } },
            onDismiss = { deleting = null },
        )
    }
}
