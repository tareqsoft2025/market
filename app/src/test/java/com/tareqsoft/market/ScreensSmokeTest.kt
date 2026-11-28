package com.tareqsoft.market

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Opens the app and every main screen to make sure nothing crashes on start. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreensSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun openAndBack(tile: String, expected: String, substring: Boolean = false) {
        rule.onNodeWithText(tile).performScrollTo().performClick()
        rule.waitForIdle()
        // Lists load from the database a moment after the screen opens.
        rule.waitUntil(5_000) { rule.onAllNodesWithText(expected, substring = substring).fetchSemanticsNodes().isNotEmpty() }
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test
    fun allScreensOpen() {
        rule.onNodeWithText("ملخص اليوم", substring = true).assertExists()
        openAndBack("نقطة البيع", "السلة فارغة", substring = true)
        openAndBack("المخزن والأصناف", "عدد الأصناف")
        openAndBack("الفواتير والمردودات", "لا توجد فواتير")
        openAndBack("التحصيلات والتحويلات", "حركة الصندوق اليومية")
        openAndBack("العملاء والديون", "ديون كل العملاء")
        openAndBack("أقسام المحل", "ألبان وأجبان")
        openAndBack("التقارير", "الأكثر مبيعاً")
        openAndBack("الإعدادات والنسخ الاحتياطي", "بيانات المحل")
    }

    @Test
    fun addProduct() {
        rule.onNodeWithText("المخزن والأصناف").performScrollTo().performClick()
        rule.onNodeWithText("صنف جديد", useUnmergedTree = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("اسم الصنف *").performTextInput("ماء")
        rule.onNodeWithText("سعر البيع *").performTextInput("1.5")
        rule.onNodeWithText("الكمية الافتتاحية").performTextInput("10")
        rule.onNodeWithText("حفظ").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("ماء").assertExists()
    }

    /** A barcode scanner can send codes faster than the database lookup; none may be lost. */
    @Test
    fun rapidBarcodeScansAreAllCounted() {
        val repo = (rule.activity.application as MarketApp).container.repo
        kotlinx.coroutines.runBlocking {
            repo.addProduct(com.tareqsoft.market.data.Product(name = "حليب", barcode = "111", salePrice = 500, quantity = 9.0))
        }
        rule.onNodeWithText("نقطة البيع").performScrollTo().performClick()
        val field = rule.onNodeWithText("الباركود أو اسم الصنف")
        repeat(3) { field.performTextInput("111"); field.performImeAction() }
        Thread.sleep(800); rule.waitForIdle()
        rule.onNodeWithText("إتمام البيع (1 صنف)").assertExists()
        rule.onNodeWithText("5.00 × 3", substring = true).assertExists()
    }

    /** Price per أوقية is 5: the customer asks for 20 worth, the app works out 4 أوقية. */
    @Test
    fun weightItemFromAmount() {
        val repo = (rule.activity.application as MarketApp).container.repo
        kotlinx.coroutines.runBlocking {
            repo.addProduct(
                com.tareqsoft.market.data.Product(
                    name = "هيل", barcode = "555", unit = "أوقية", salePrice = 500, quantity = 30.0, soldByWeight = true,
                )
            )
        }
        rule.onNodeWithText("نقطة البيع").performScrollTo().performClick()
        val field = rule.onNodeWithText("الباركود أو اسم الصنف")
        field.performTextInput("555"); field.performImeAction()
        Thread.sleep(500); rule.waitForIdle()
        rule.onNodeWithText("المبلغ المطلوب").performTextInput("20")
        rule.waitForIdle()
        rule.onNodeWithText("إضافة للسلة").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("5.00 × 4", substring = true).assertExists()
    }

    /** The payment QR can be enlarged to full screen and closed again. */
    @Test
    fun walletQrEnlarges() {
        val c = (rule.activity.application as MarketApp).container
        c.settings.saveWallet("0598815128", com.tareqsoft.market.util.WalletPay.DEFAULT_PREFIX)
        rule.onNodeWithText("الإعدادات والنسخ الاحتياطي").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("تكبير الرمز", substring = true).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("إغلاق").assertExists().performClick()
        // The full-screen window is removed a moment after closing.
        rule.waitUntil(3_000) { rule.onAllNodesWithText("إغلاق").fetchSemanticsNodes().isEmpty() }
    }
}
