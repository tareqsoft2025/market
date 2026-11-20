package com.tareqsoft.market.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tareqsoft.market.ui.ConfirmDialog
import com.tareqsoft.market.ui.LabelValue
import com.tareqsoft.market.ui.LocalContainer
import com.tareqsoft.market.ui.ScreenScaffold
import com.tareqsoft.market.util.Dates
import android.net.Uri
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val c = LocalContainer.current
    val current by c.settings.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var storeName by remember { mutableStateOf(current.storeName) }
    var currency by remember { mutableStateOf(current.currency) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                c.backup.exportTo(uri)
                snackbar.showSnackbar("تم حفظ النسخة الاحتياطية")
            } catch (e: Exception) {
                snackbar.showSnackbar("فشل الحفظ: ${e.message}")
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreUri = uri
    }

    ScreenScaffold(title = "الإعدادات", onBack = onBack, snackbar = snackbar) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("بيانات المحل", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(storeName, { storeName = it }, label = { Text("اسم المحل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(currency, { currency = it }, label = { Text("رمز العملة (مثال: ر.س، د.أ، ج.م)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                c.settings.save(storeName, currency)
                scope.launch { snackbar.showSnackbar("تم الحفظ") }
            }, modifier = Modifier.fillMaxWidth()) { Text("حفظ") }

            Text("التحديثات", style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    LabelValue("الإصدار الحالي", c.updater.currentVersion, bold = true)
                    Text(
                        "التحديث يُثبَّت فوق النسخة الحالية ويحافظ على جميع البيانات.",
                        style = MaterialTheme.typography.bodySmall, color = Color.Gray,
                    )
                }
            }
            UpdateBanner(showUpToDate = true)
            OutlinedButton(onClick = { scope.launch { c.updater.check() } }, modifier = Modifier.fillMaxWidth()) {
                Text("التحقق من وجود تحديث")
            }

            Text("النسخ الاحتياطي", style = MaterialTheme.typography.titleMedium)
            Text(
                "احفظ نسخة من البيانات بشكل دوري (على Google Drive أو ذاكرة خارجية). " +
                    "حذف التطبيق أو مسح بياناته يحذف كل شيء، والنسخة الاحتياطية هي الحماية الوحيدة حتى تفعيل الرفع للإنترنت.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
            )
            Button(
                onClick = { exportLauncher.launch("market-backup-${Dates.date(System.currentTimeMillis()).replace('/', '-')}.db") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("حفظ نسخة احتياطية") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text("استرجاع من نسخة احتياطية")
            }

            Text("معرّف الجهاز (للمزامنة مستقبلاً): ${current.deviceId.take(8)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }

    restoreUri?.let { uri ->
        ConfirmDialog(
            title = "استرجاع البيانات",
            text = "سيتم استبدال جميع البيانات الحالية بالبيانات الموجودة في النسخة الاحتياطية، ثم يُعاد تشغيل التطبيق. " +
                "(يتم حفظ نسخة من البيانات الحالية داخلياً احتياطاً)",
            confirmText = "استرجاع",
            onConfirm = {
                scope.launch {
                    try {
                        c.backup.restoreFrom(uri)
                    } catch (e: Exception) {
                        snackbar.showSnackbar("فشل الاسترجاع: ${e.message}")
                    }
                }
            },
            onDismiss = { restoreUri = null },
        )
    }
}
