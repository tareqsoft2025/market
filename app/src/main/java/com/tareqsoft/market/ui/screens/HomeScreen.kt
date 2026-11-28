package com.tareqsoft.market.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.tareqsoft.market.ui.Emerald
import com.tareqsoft.market.ui.Indigo
import com.tareqsoft.market.ui.IndigoDeep
import com.tareqsoft.market.ui.QamariyaWindow
import com.tareqsoft.market.ui.Ruby
import com.tareqsoft.market.ui.Saffron
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
import com.tareqsoft.market.ui.AppIcons
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
    val pos = rememberPos()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
    ) {
        // ---- Header: the qamariya over the store's name
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(Brush.verticalGradient(listOf(IndigoDeep, Indigo)))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 20.dp, bottom = 52.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                QamariyaWindow(Modifier.width(200.dp).height(100.dp))
                Spacer(Modifier.height(14.dp))
                Text(
                    settings.storeName,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    "ملخص اليوم  •  ${Dates.date(from)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                )
            }
        }

        Column(
            Modifier.padding(horizontal = 16.dp).offset(y = (-36).dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- Today, as one ledger line
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(6.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 14.dp)) {
                    LedgerFigure("المبيعات", money(salesTotal), "$salesCount فاتورة", Indigo, Modifier.weight(1f))
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LedgerFigure("المردودات", money(returnsTotal), null, Ruby, Modifier.weight(1f))
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    LedgerFigure("الصافي", money(salesTotal - returnsTotal), null, Emerald, Modifier.weight(1f))
                }
            }

            UpdateBanner()

            // ---- Things waiting for the owner
            if (pendingBank > 0 || lowStock > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                    if (pendingBank > 0) {
                        AlertPill("تحويلات للمراجعة: $pendingBank", Saffron, Modifier.weight(1f).fillMaxHeight()) {
                            navigate(Routes.COLLECTIONS)
                        }
                    }
                    if (lowStock > 0) {
                        AlertPill("نواقص المخزن: $lowStock", Ruby, Modifier.weight(1f).fillMaxHeight()) { navigate(Routes.PRODUCTS) }
                    }
                }
            }

            // ---- The main job of the shop: selling
            Card(
                onClick = { navigate(Routes.POS) },
                colors = CardDefaults.cardColors(containerColor = Indigo),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(4.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).background(Saffron, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.ShoppingCart, null, tint = IndigoDeep, modifier = Modifier.size(28.dp)) }
                    Column(Modifier.weight(1f).padding(start = 16.dp)) {
                        Text("نقطة البيع", style = MaterialTheme.typography.titleLarge, color = Color.White)
                        Text(
                            if (pos.openBills > 0) "فواتير مفتوحة: ${pos.openBills}" else "ابدأ فاتورة جديدة",
                            color = if (pos.openBills > 0) Saffron else Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White.copy(alpha = 0.8f))
                }
            }

            // ---- Everything else
            val tiles = listOf(
                Tile("المخزن والأصناف", "الكميات والتوريد والجرد", Icons.AutoMirrored.Filled.List, Routes.PRODUCTS, Emerald),
                Tile("الفواتير والمردودات", "كل فاتورة ومردودها", Icons.Filled.DateRange, Routes.SALES, Indigo),
                Tile("التحصيلات والتحويلات", "الصندوق والبنك والمحافظ", AppIcons.Wallet, Routes.COLLECTIONS, Info),
                Tile("العملاء والديون", "الآجل ونسبة التحصيل", Icons.Filled.Person, Routes.CUSTOMERS, Ruby),
                Tile("التقارير", "المبيعات والأرباح", Icons.Filled.Star, Routes.REPORTS, Warning),
                Tile("أقسام المحل", "مواد غذائية، ألبان...", AppIcons.Departments, Routes.CATEGORIES, Color(0xFF6B4E8A)),
                Tile("الإعدادات والنسخ الاحتياطي", "المحل والرسائل والتحديث", Icons.Filled.Settings, Routes.SETTINGS, Color(0xFF5A5F73)),
            )
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                    row.forEach { t -> TileCard(t, Modifier.weight(1f).fillMaxHeight()) { navigate(t.route) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            BottomSpacer()
        }
    }
}

@Composable
private fun LedgerFigure(label: String, value: String, sub: String?, color: Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold, color = color, maxLines = 1,
        )
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AlertPill(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.13f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(
                text, Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelLarge, color = if (color == Saffron) Warning else color,
            )
        }
    }
}

private data class Tile(val title: String, val hint: String, val icon: ImageVector, val route: String, val color: Color)

@Composable
private fun TileCard(t: Tile, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(44.dp).background(t.color.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(t.icon, contentDescription = null, tint = t.color, modifier = Modifier.size(24.dp)) }
            Column {
                Text(t.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(t.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
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
