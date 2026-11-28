package com.tareqsoft.market.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

data class StoreSettings(
    val storeName: String,
    val currency: String,
    val deviceId: String,
    val smsTemplate: String,
    /** Shop's محفظتي number that receives transfers. Empty = pay-by-scan off. */
    val walletNumber: String,
    /** Start of the transfer code, e.g. *370*1*1* */
    val walletPrefix: String,
)

/** Small key/value settings. Kept in SharedPreferences (survives app updates). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("market_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<StoreSettings> = _state

    private fun read(): StoreSettings {
        var deviceId = prefs.getString(KEY_DEVICE, null)
        if (deviceId == null) {
            // Identifies this device for future cloud sync.
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE, deviceId).apply()
        }
        return StoreSettings(
            storeName = prefs.getString(KEY_STORE, "سوبر ماركت") ?: "سوبر ماركت",
            currency = prefs.getString(KEY_CURRENCY, "") ?: "",
            deviceId = deviceId,
            smsTemplate = prefs.getString(KEY_SMS, null) ?: Sms.DEFAULT_TEMPLATE,
            walletNumber = prefs.getString(KEY_WALLET, "") ?: "",
            walletPrefix = prefs.getString(KEY_WALLET_PREFIX, null) ?: WalletPay.DEFAULT_PREFIX,
        )
    }

    fun save(storeName: String, currency: String) {
        prefs.edit()
            .putString(KEY_STORE, storeName.trim())
            .putString(KEY_CURRENCY, currency.trim())
            .apply()
        _state.value = read()
    }

    fun saveSmsTemplate(template: String) {
        prefs.edit().putString(KEY_SMS, template.trim().ifBlank { Sms.DEFAULT_TEMPLATE }).apply()
        _state.value = read()
    }

    fun saveWallet(number: String, prefix: String) {
        prefs.edit()
            .putString(KEY_WALLET, number.filter { it.isDigit() })
            .putString(KEY_WALLET_PREFIX, normalizeDigits(prefix).filterNot { it.isWhitespace() }.ifBlank { WalletPay.DEFAULT_PREFIX })
            .apply()
        _state.value = read()
    }

    private companion object {
        const val KEY_WALLET = "wallet_number"
        const val KEY_WALLET_PREFIX = "wallet_prefix"
        const val KEY_SMS = "sms_template"
        const val KEY_STORE = "store_name"
        const val KEY_CURRENCY = "currency"
        const val KEY_DEVICE = "device_id"
    }
}
