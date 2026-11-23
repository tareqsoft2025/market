package com.tareqsoft.market.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

data class SmsMessage(val phone: String, val text: String)

object Sms {
    const val DEFAULT_TEMPLATE =
        "عزيزنا {الاسم}، نذكّركم بأن المبلغ المستحق عليكم لدى {المحل} هو {المبلغ}. شاكرين تعاملكم معنا."

    /** Fills {الاسم} {المبلغ} {المحل} in the reminder template. */
    fun build(template: String, name: String, amount: String, store: String): String =
        template.ifBlank { DEFAULT_TEMPLATE }
            .replace("{الاسم}", name)
            .replace("{المبلغ}", amount)
            .replace("{المحل}", store)

    /** Keeps digits and a leading +, so "077 712-3456" works. */
    fun normalizePhone(phone: String): String {
        val p = normalizeDigits(phone).trim()
        val digits = p.filter { it.isDigit() }
        return if (p.startsWith("+")) "+$digits" else digits
    }

    fun isValidPhone(phone: String) = normalizePhone(phone).count { it.isDigit() } >= 6

    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /** Sends directly from the phone's SIM. Returns how many messages were handed to the system. */
    fun sendAll(context: Context, messages: List<SmsMessage>): Int {
        @Suppress("DEPRECATION")
        val manager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
        var sent = 0
        messages.forEach { m ->
            try {
                val parts = manager.divideMessage(m.text)
                manager.sendMultipartTextMessage(normalizePhone(m.phone), null, parts, null, null)
                sent++
            } catch (_: Exception) {
            }
        }
        return sent
    }

    /** Opens the phone's messages app with the text ready; the user presses Send. */
    fun openComposer(context: Context, m: SmsMessage) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + normalizePhone(m.phone)))
            .putExtra("sms_body", m.text)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}

/**
 * Returns a function that sends reminders. The first time it asks for the
 * "send SMS" permission. If the user refuses, a single message opens in the
 * messages app instead; bulk sending reports that permission is needed.
 */
@Composable
fun rememberSmsSender(onResult: (sent: Int, total: Int, permissionDenied: Boolean) -> Unit): (List<SmsMessage>) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<List<SmsMessage>>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val msgs = pending[0] ?: return@rememberLauncherForActivityResult
        pending[0] = null
        if (granted) {
            onResult(Sms.sendAll(context, msgs), msgs.size, false)
        } else if (msgs.size == 1) {
            Sms.openComposer(context, msgs.first())
            onResult(0, 1, true)
        } else {
            onResult(0, msgs.size, true)
        }
    }
    return { msgs ->
        if (msgs.isNotEmpty()) {
            if (Sms.hasPermission(context)) {
                onResult(Sms.sendAll(context, msgs), msgs.size, false)
            } else {
                pending[0] = msgs
                launcher.launch(Manifest.permission.SEND_SMS)
            }
        }
    }
}
