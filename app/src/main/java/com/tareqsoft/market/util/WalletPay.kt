package com.tareqsoft.market.util

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Pay-by-scan for محفظتي. The wallet transfer is a USSD code such as
 *     *370*1*1*0598815128*15#
 * (shop's wallet number, then the amount). It is put in a QR code as a
 * "tel:" link: the customer scans it with the phone camera, the dialer opens
 * with the code already typed, and the customer presses call to send.
 */
object WalletPay {
    /** Start of the محفظتي transfer code; the app adds  <wallet number>*<amount>#  after it. */
    const val DEFAULT_PREFIX = "*370*1*1*"

    /** Amount as the wallet expects it: 15 for 15.00, 12.5 for 12.50, no separators. */
    fun amountText(minor: Long): String = Money.toInput(minor)

    fun ussd(prefix: String, walletNumber: String, amountMinor: Long): String {
        var p = normalizeDigits(prefix).filterNot { it.isWhitespace() }.ifBlank { DEFAULT_PREFIX }
        if (!p.endsWith("*")) p += "*"
        return p + walletNumber.filter { it.isDigit() } + "*" + amountText(amountMinor) + "#"
    }

    /** "#" must be written %23 inside a tel: link, otherwise the phone cuts the code there. */
    fun telUri(ussd: String): String = "tel:" + ussd.replace("#", "%23")

    fun isValidWallet(number: String) = number.count { it.isDigit() } >= 6

    fun qr(content: String, size: Int = 512): BitMatrix =
        QRCodeWriter().encode(
            content, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1),
        )
}
