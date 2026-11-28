package com.tareqsoft.market.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.tareqsoft.market.ui.NumberField
import com.tareqsoft.market.ui.WalletPayCard
import com.tareqsoft.market.ui.LtrCode
import com.tareqsoft.market.ui.BottomSpacer
import com.tareqsoft.market.ui.PrimaryActionButton
import com.tareqsoft.market.ui.ScreenScaffold
import com.tareqsoft.market.util.Dates
import com.tareqsoft.market.util.Sms
import com.tareqsoft.market.util.WalletPay
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
    var smsTemplate by remember { mutableStateOf(current.smsTemplate) }
    var walletNumber by remember { mutableStateOf(current.walletNumber) }
    var walletPrefix by remember { mutableStateOf(current.walletPrefix) }
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

            Text("الدفع بالمسح عبر محفظتي", style = MaterialTheme.typography.titleMedium)
            Text(
                "عند اختيار \"محفظتي\" في الدفع يظهر رمز QR فيه كود التحويل ومبلغ الفاتورة. " +
                    "يمسحه الزبون بكاميرا جواله فيُفتح الاتصال والكود مكتوب، ويضغط اتصال.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
            )
            NumberField(walletNumber, { walletNumber = it }, "رقم محفظتي لاستلام المبالغ", Modifier.fillMaxWidth(), decimal = false)
            OutlinedTextField(
                walletPrefix, { walletPrefix = it },
                label = { Text("بداية كود التحويل") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                    textDirection = androidx.compose.ui.text.style.TextDirection.Ltr,
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
                ),
            )
            Text(
                "يُضاف بعدها تلقائياً: رقم المحفظة * المبلغ #",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
            )
            if (WalletPay.isValidWallet(walletNumber)) {
                LtrCode(
                    WalletPay.ussd(walletPrefix, walletNumber, 1500),
                    MaterialTheme.typography.titleMedium, MaterialTheme.colorScheme.primary,
                )
                Text("مثال: الكود الكامل لفاتورة بمبلغ 15", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            OutlinedButton(onClick = {
                c.settings.saveWallet(walletNumber, walletPrefix)
                walletNumber = c.settings.state.value.walletNumber
                walletPrefix = c.settings.state.value.walletPrefix
                scope.launch { snackbar.showSnackbar("تم حفظ بيانات المحفظة") }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("حفظ بيانات المحفظة") }
            if (current.walletNumber.isNotEmpty()) {
                Text("معاينة (مبلغ تجريبي 1):", style = MaterialTheme.typography.labelLarge)
                WalletPayCard(amount = 100)
            }

            Text("رسالة التذكير بالدفع", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                smsTemplate, { smsTemplate = it },
                label = { Text("نص الرسالة") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "تُستبدل تلقائياً: {الاسم} اسم العميل، {المبلغ} المبلغ المستحق، {المحل} اسم المحل.\n" +
                    "مثال: " + Sms.build(smsTemplate, "أحمد", "1,500.00 ${currency.trim()}".trim(), storeName),
                style = MaterialTheme.typography.bodySmall, color = Color.Gray,
            )
            OutlinedButton(onClick = {
                c.settings.saveSmsTemplate(smsTemplate)
                smsTemplate = c.settings.state.value.smsTemplate
                scope.launch { snackbar.showSnackbar("تم حفظ نص الرسالة") }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("حفظ نص الرسالة") }

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
            PrimaryActionButton(
                "حفظ نسخة احتياطية",
                onClick = { exportLauncher.launch("market-backup-${Dates.date(System.currentTimeMillis()).replace('/', '-')}.db") },
            )
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("استرجاع من نسخة احتياطية")
            }

            Text("معرّف الجهاز (للمزامنة مستقبلاً): ${current.deviceId.take(8)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            BottomSpacer()
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
