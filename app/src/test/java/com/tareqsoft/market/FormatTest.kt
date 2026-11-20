package com.tareqsoft.market

import com.tareqsoft.market.update.Updater
import com.tareqsoft.market.util.Money
import com.tareqsoft.market.util.Qty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {
    @Test
    fun moneyParse() {
        assertEquals(1250L, Money.parse("12.5"))
        assertEquals(1250L, Money.parse("12,50"))
        assertEquals(1000L, Money.parse("١٠"))      // Arabic-Indic digits
        assertEquals(1L, Money.parse("0.005"))       // rounds half up
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
    }

    @Test
    fun moneyFormat() {
        assertEquals("12.50", Money.format(1250L))
        assertEquals("1,234.05", Money.format(123405L))
        assertEquals("12.5", Money.toInput(1250L))
    }

    @Test
    fun qty() {
        assertEquals("2", Qty.format(2.0))
        assertEquals("1.25", Qty.format(1.25))
        assertEquals(1.5, Qty.parse("1٫5")!!, 0.0001)
    }

    @Test
    fun versionCompare() {
        assertTrue(Updater.isNewer("1.0.1", "1.0.0"))
        assertTrue(Updater.isNewer("1.10.0", "1.9.9"))
        assertTrue(Updater.isNewer("2.0", "1.9.9"))
        assertFalse(Updater.isNewer("1.0.0", "1.0.0"))
        assertFalse(Updater.isNewer("1.0.0", "1.0.0-debug"))
        assertFalse(Updater.isNewer("0.9.0", "1.0.0"))
    }
}
