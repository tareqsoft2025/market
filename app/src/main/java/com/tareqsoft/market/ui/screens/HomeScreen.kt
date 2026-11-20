package com.tareqsoft.market.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tareqsoft.market.Routes
import com.tareqsoft.market.ui.Danger
import com.tareqsoft.market.ui.Info
import com.tareqsoft.market.ui.LocalContainer
import com.tareqsoft.market.ui.BottomSpacer
import com.tareqsoft.market.ui.ScreenScaffold
import com.tareqsoft.market.ui.StatCard
import com.tareqsoft.market.ui.Warning
import com.tareqsoft.market.ui.money
import com.tareqsoft.market.update.UpdateState
import com.tareqsoft.market.util.Dates
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(navigate: (String) -> Unit) {
    val c = LocalContainer.current
    val repo = c.repo
    val settings by c.settings.state.collectAsState()
    val from = remember { Dates.startOfDay(System.currentTimeMillis()) }
    val to = remember { Dates.endOfDay(System.currentTimeMillis()) }

    // Flows are remembered so they are not re-created on every recomposition.
    val salesTotal by remember { repo.sales.observeSalesTotal(from, to) }.collectAsStateWithLifecycle(0L)
    val salesCount by remember { repo.sales.observeSalesCount(from, to) }.collectAsStateWithLifecycle(0)
    val returnsTotal by remember { repo.sales.observeReturnsTotal(from, to) }.collectAsStateWithLifecycle(0L)
    val pendingBank by remember { repo.payments.observePendingBankCount() }.collectAsStateWithLifecycle(0)
    val lowStock by remember { repo.products.observeLowStockCount() }.collectAsStateWithLifecycle(0)

    ScreenScaffold(title = settings.storeName) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UpdateBanner()

            Text("ملخص اليوم  ${Dates.date(from)}", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("المبيعات", money(salesTotal), Modifier.weight(1f), subtitle = "$salesCount فاتورة")
                StatCard("المردودات", money(returnsTotal), Modifier.weight(1f), color = Danger)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("صافي المبيعات", money(salesTotal - returnsTotal), Modifier.weight(1f), color = Info)
                StatCard(
                    "تحويلات بانتظار المراجعة", "$pendingBank", Modifier.weight(1f),
                    color = if (pendingBank > 0) Warning else Color.Gray,
                    onClick = { navigate(Routes.COLLECTIONS) },
                )
            }
            if (lowStock > 0) {
                StatCard(
                    "أصناف وصلت لحد الطلب", "$lowStock صنف", Modifier.fillMaxWidth(),
                    color = Danger, subtitle = "اضغط لعرض المخزن",
                    onClick = { navigate(Routes.PRODUCTS) },
                )
            }

            Spacer(Modifier.height(4.dp))
            val tiles = listOf(
                Tile("نقطة البيع", Icons.Filled.ShoppingCart, Routes.POS, MaterialTheme.colorScheme.primary),
                Tile("المخزن والأصناف", Icons.AutoMirrored.Filled.List, Routes.PRODUCTS, Color(0xFF6D4C41)),
                Tile("الفواتير والمردودات", Icons.Filled.DateRange, Routes.SALES, Color(0xFF00695C)),
                Tile("التحصيلات والتحويلات", Icons.Filled.CheckCircle, Routes.COLLECTIONS, Info),
                Tile("العملاء والديون", Icons.Filled.Person, Routes.CUSTOMERS, Color(0xFF6A1B9A)),
                Tile("التقارير", Icons.Filled.Star, Routes.REPORTS, Warning),
                Tile("الإعدادات والنسخ الاحتياطي", Icons.Filled.Settings, Routes.SETTINGS, Color.DarkGray),
            )
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { t -> TileCard(t, Modifier.weight(1f)) { navigate(t.route) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            BottomSpacer()
        }
    }
}

private data class Tile(val title: String, val icon: ImageVector, val route: String, val color: Color)

@Composable
private fun TileCard(t: Tile, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(110.dp),
        colors = CardDefaults.cardColors(containerColor = t.color),
        elevation = CardDefaults.cardElevation(3.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(t.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text(t.title, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/** Shows when a new version is available on GitHub and lets the user install it. */
@Composable
fun UpdateBanner(showUpToDate: Boolean = false) {
    val updater = LocalContainer.current.updater
    val state by updater.state.collectAsState()
    val scope = rememberCoroutineScope()

    var text = ""
    var actionLabel: String? = null
    var onAction: () -> Unit = {}
    when (val s = state) {
        is UpdateState.Available -> {
            text = "يتوفر إصدار جديد ${s.release.version}"
            actionLabel = "تنزيل وتثبيت"
            onAction = { scope.launch { updater.download(s.release) } }
        }
        is UpdateState.Downloading -> text = "جارِ تنزيل التحديث ${(s.progress * 100).toInt()}%"
        is UpdateState.ReadyToInstall -> {
            text = "التحديث جاهز للتثبيت (بياناتك محفوظة)"
            actionLabel = "تثبيت الآن"
            onAction = { updater.install(s.file) }
        }
        is UpdateState.Failed -> {
            text = s.message
            actionLabel = "إعادة المحاولة"
            onAction = { scope.launch { updater.check() } }
        }
        is UpdateState.Checking -> text = "جارِ التحقق من التحديثات..."
        is UpdateState.UpToDate -> text = "أنت تستخدم أحدث إصدار (${updater.currentVersion})"
        UpdateState.Idle -> {}
    }
    val hidden = state is UpdateState.Idle ||
        (!showUpToDate && (state is UpdateState.Checking || state is UpdateState.UpToDate))
    if (hidden) return

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(text, fontWeight = FontWeight.Bold)
            val s = state
            if (s is UpdateState.Available && s.release.notes.isNotBlank()) {
                Text(s.release.notes.take(400), style = MaterialTheme.typography.bodySmall)
            }
            if (s is UpdateState.Downloading) {
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            val label = actionLabel
            if (label != null) {
                TextButton(onClick = onAction) { Text(label) }
            }
        }
    }
}
