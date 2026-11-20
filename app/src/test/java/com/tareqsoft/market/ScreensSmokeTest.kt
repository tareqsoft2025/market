package com.tareqsoft.market

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        rule.onAllNodesWithText(expected, substring = substring)[0].assertExists()
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
        openAndBack("العملاء والديون", "إجمالي الديون على العملاء")
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
}
