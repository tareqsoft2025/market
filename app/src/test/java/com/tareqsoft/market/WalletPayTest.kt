package com.tareqsoft.market

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.tareqsoft.market.util.WalletPay
import org.junit.Assert.assertEquals
import org.junit.Test

class WalletPayTest {
    @Test
    fun buildsTheMahfathatiCode() {
        // The exact example: wallet 0598815128, amount 15
        assertEquals("*370*1*1*0598815128*15#", WalletPay.ussd(WalletPay.DEFAULT_PREFIX, "0598815128", 1500))
        assertEquals("*370*1*1*0598815128*12.5#", WalletPay.ussd(WalletPay.DEFAULT_PREFIX, "059 881 5128", 1250))
        assertEquals("*370*1*1*0598815128*15#", WalletPay.ussd("*370*1*1", "0598815128", 1500))   // missing * added
        assertEquals("*370*1*1*0598815128*15#", WalletPay.ussd("*٣٧٠*١*١*", "0598815128", 1500)) // Arabic digits
        assertEquals("tel:*370*1*1*0598815128*15%23", WalletPay.telUri("*370*1*1*0598815128*15#"))
    }

    @Test
    fun qrCodeReadsBackAsTheTelLink() {
        val content = WalletPay.telUri(WalletPay.ussd(WalletPay.DEFAULT_PREFIX, "0598815128", 1500))
        val m = WalletPay.qr(content, 300)
        val pixels = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else -1 }
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(m.width, m.height, pixels)))).text
        assertEquals("*370*1*1*0598815128*15#", WalletPay.ussd("*370*1*1", "0598815128", 1500))   // missing * added
        assertEquals("*370*1*1*0598815128*15#", WalletPay.ussd("*٣٧٠*١*١*", "0598815128", 1500)) // Arabic digits
        assertEquals("tel:*370*1*1*0598815128*15%23", decoded)
    }
}
