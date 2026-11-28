package com.tareqsoft.market.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import com.tareqsoft.market.util.WalletPay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.tareqsoft.market.data.TransferChannel

/** Small icon set (Material Symbols outlines) without pulling in the large extended icons library. */
object AppIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black)) }
        }.build()

    /** Bank building. */
    val Bank = icon(
        "Bank",
        "M4,10h3v7H4ZM10.5,10h3v7h-3ZM2,19h20v3H2ZM17,10h3v7h-3ZM12,1L2,6v2h20V6Z",
    )

    /** Phone (mobile wallet). */
    val PhoneWallet = icon(
        "PhoneWallet",
        "M16,1H8C6.34,1 5,2.34 5,4v16c0,1.66 1.34,3 3,3h8c1.66,0 3,-1.34 3,-3V4c0,-1.66 -1.34,-3 -3,-3zM14,21h-4v-1h4v1zM17.25,18H6.75V4h10.5v14z",
    )

    /** Wallet (محفظتي). */
    val Wallet = icon(
        "Wallet",
        "M21,18v1c0,1.1 -0.9,2 -2,2H5c-1.11,0 -2,-0.9 -2,-2V5c0,-1.1 0.89,-2 2,-2h14c1.1,0 2,0.9 2,2v1h-9c-1.11,0 -2,0.9 -2,2v8c0,1.1 0.89,2 2,2h9zM12,16h10V8H12v8zM16,13.5c-0.83,0 -1.5,-0.67 -1.5,-1.5s0.67,-1.5 1.5,-1.5 1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z",
    )

    /** Text message. */
    val Sms = icon(
        "Sms",
        "M20,2H4C2.9,2 2.01,2.9 2.01,4L2,22l4,-4h14c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM9,11H7V9h2v2zM13,11h-2V9h2v2zM17,11h-2V9h2v2z",
    )

    /** Barcode / camera scan frame. */
    val Scan = icon(
        "Scan",
        "M9.5,6.5v3h-3v-3H9.5M11,5H5v6h6V5L11,5zM9.5,14.5v3h-3v-3H9.5M11,13H5v6h6V13L11,13zM17.5,6.5v3h-3v-3H17.5M19,5h-6v6h6V5L19,5zM13,13h1.5v1.5H13V13zM14.5,14.5H16V16h-1.5V14.5zM16,13h1.5v1.5H16V13zM13,16h1.5v1.5H13V16zM14.5,17.5H16V19h-1.5V17.5zM16,16h1.5v1.5H16V16zM17.5,14.5H19V16h-1.5V14.5zM17.5,17.5H19V19h-1.5V17.5zM22,7h-2V4h-3V2h5V7zM22,22v-5h-2v3h-3v2H22zM2,22h5v-2H4v-3H2V22zM2,2v5h2V4h3V2H2z",
    )

    /** Filter funnel. */
    val Filter = icon("Filter", "M10,18h4v-2h-4v2zM3,6v2h18V6H3zM6,13h12v-2H6v2z")

    /** Departments (shelves). */
    val Departments = icon(
        "Departments",
        "M3,13h8V3H3v10zM3,21h8v-6H3v6zM13,21h8V11h-8v10zM13,3v6h8V3h-8z",
    )

    fun forChannel(channel: String): ImageVector = when (channel) {
        TransferChannel.MOBILE_WALLET -> PhoneWallet
        TransferChannel.MAHFATHATI -> Wallet
        else -> Bank
    }
}

/** Choose how a transfer was received: bank, mobile wallet or محفظتي. */
@Composable
fun ChannelSelector(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TransferChannel.all.forEach { ch ->
            FilterChip(
                selected = ch == selected,
                onClick = { onSelect(ch) },
                leadingIcon = { Icon(AppIcons.forChannel(ch), null, Modifier.size(18.dp)) },
                label = { Text(TransferChannel.label(ch)) },
            )
        }
    }
}

/** Editable transfer details shared by the checkout and debt-collection dialogs. */
class TransferFormState {
    var channel by androidx.compose.runtime.mutableStateOf(TransferChannel.BANK)
    var name by androidx.compose.runtime.mutableStateOf("")
    var phone by androidx.compose.runtime.mutableStateOf("")
    var reference by androidx.compose.runtime.mutableStateOf("")
    var note by androidx.compose.runtime.mutableStateOf("")

    fun toInfo() = com.tareqsoft.market.data.TransferInfo(
        channel = channel, bankName = name.trim(), reference = reference.trim(),
        payerPhone = phone.trim(), note = note.trim(),
    )
}

@Composable
fun TransferFields(state: TransferFormState, amount: Long? = null) {
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChannelSelector(state.channel, { state.channel = it })
        if (state.channel == TransferChannel.MAHFATHATI && amount != null && amount > 0) {
            WalletPayCard(amount)
        }
        androidx.compose.material3.OutlinedTextField(
            state.name, { state.name = it }, singleLine = true,
            label = { Text(if (state.channel == TransferChannel.BANK) "اسم البنك" else "اسم المحفظة (اختياري)") },
        )
        NumberField(state.phone, { state.phone = it }, "رقم جوال المحوِّل", decimal = false)
        androidx.compose.material3.OutlinedTextField(
            state.reference, { state.reference = it }, singleLine = true,
            label = { Text("رقم العملية / المرجع") },
        )
        androidx.compose.material3.OutlinedTextField(
            state.note, { state.note = it },
            label = { Text("ملاحظات") },
        )
        Text(
            "يكفي رقم العملية أو رقم الجوال. يظهر التحويل في شاشة المراجعة حتى تأكيد وصوله.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = Color.Gray,
        )
    }
}

/** The shop's محفظتي transfer code for this amount, as a QR the customer scans. */
@Composable
fun WalletPayCard(amount: Long, modifier: Modifier = Modifier) {
    val settings by LocalContainer.current.settings.state.collectAsState()
    val number = settings.walletNumber
    if (!WalletPay.isValidWallet(number)) {
        Text(
            "لعرض رمز الدفع بالمسح: أدخل رقم محفظتك في الإعدادات.",
            style = MaterialTheme.typography.bodySmall, color = Warning,
        )
        return
    }
    val code = WalletPay.ussd(settings.walletPrefix, number, amount)
    val qr = remember(code) { qrImage(WalletPay.telUri(code)) }
    androidx.compose.material3.Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("امسح الرمز بكاميرا جوال الزبون", style = MaterialTheme.typography.labelLarge)
            Image(
                bitmap = qr, contentDescription = "رمز الدفع عبر محفظتي",
                filterQuality = FilterQuality.None,
                modifier = Modifier.padding(vertical = 8.dp).size(190.dp),
            )
            Text(money(amount), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            LtrCode(code, MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"), Color.DarkGray)
            Text(
                "يُفتح الاتصال والرمز مكتوب، يضغط الزبون اتصال لإتمام التحويل. بعد وصول الرسالة اكتب رقم العملية بالأسفل.",
                style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center,
            )
        }
    }
}

/** Black-on-white QR bitmap. */
fun qrImage(content: String, size: Int = 512): ImageBitmap {
    val m = WalletPay.qr(content, size)
    val pixels = IntArray(m.width * m.height) { i ->
        if (m.get(i % m.width, i / m.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return android.graphics.Bitmap.createBitmap(pixels, m.width, m.height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/**
 * Shows a USSD code (*370*1*1*...#) always left to right. Each run of digits
 * and each symbol is its own piece in a left-to-right row, so no phone's
 * right-to-left text handling can reorder it.
 */
@Composable
fun LtrCode(code: String, style: androidx.compose.ui.text.TextStyle, color: Color = Color.Unspecified) {
    val pieces = Regex("[0-9]+|[^0-9]").findAll(code).map { it.value }.toList()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row { pieces.forEach { Text(it, style = style, color = color) } }
    }
}
