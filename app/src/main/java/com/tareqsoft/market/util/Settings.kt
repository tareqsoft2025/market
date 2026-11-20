package com.tareqsoft.market.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

data class StoreSettings(
    val storeName: String,
    val currency: String,
    val deviceId: String,
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
        )
    }

    fun save(storeName: String, currency: String) {
        prefs.edit()
            .putString(KEY_STORE, storeName.trim())
            .putString(KEY_CURRENCY, currency.trim())
            .apply()
        _state.value = read()
    }

    private companion object {
        const val KEY_STORE = "store_name"
        const val KEY_CURRENCY = "currency"
        const val KEY_DEVICE = "device_id"
    }
}
