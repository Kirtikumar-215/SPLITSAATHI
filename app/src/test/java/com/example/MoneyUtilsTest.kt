package com.example

import com.example.util.MoneyUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyUtilsTest {

    @Test
    fun testFormatPaiseToRupeesIndianGrouping() {
        assertEquals("₹250.00", MoneyUtils.formatPaiseToRupees(25000L))
        assertEquals("₹1,25,000.00", MoneyUtils.formatPaiseToRupees(12500000L))
        assertEquals("₹10,50,000.50", MoneyUtils.formatPaiseToRupees(105000050L))
        assertEquals("₹0.05", MoneyUtils.formatPaiseToRupees(5L))
        assertEquals("₹0.00", MoneyUtils.formatPaiseToRupees(0L))
        assertEquals("-₹50.00", MoneyUtils.formatPaiseToRupees(-5000L))
    }

    @Test
    fun testParseRupeesToPaise() {
        assertEquals(25000L, MoneyUtils.parseRupeesToPaise("250"))
        assertEquals(25050L, MoneyUtils.parseRupeesToPaise("250.50"))
        assertEquals(25050L, MoneyUtils.parseRupeesToPaise("250.5"))
        assertEquals(12500000L, MoneyUtils.parseRupeesToPaise("1,25,000"))
        assertNull(MoneyUtils.parseRupeesToPaise("invalid"))
        assertNull(MoneyUtils.parseRupeesToPaise("-100"))
        assertNull(MoneyUtils.parseRupeesToPaise("100.123")) // more than 2 decimals
    }

    @Test
    fun testEqualSplitCases() {
        // Case 1: ₹1000 among 4 members -> exactly 25000 paise each
        val split1 = MoneyUtils.calculateEqualSplit(100000L, listOf("user_d", "user_b", "user_c", "user_a"))
        assertEquals(4, split1.size)
        assertEquals(25000L, split1["user_a"])
        assertEquals(25000L, split1["user_b"])
        assertEquals(25000L, split1["user_c"])
        assertEquals(25000L, split1["user_d"])
        assertEquals(100000L, split1.values.sum())

        // Case 2: ₹100 among 3 members -> 10000 paise / 3 = 3333 with remainder 1
        // Sorted: u1 gets 3334, u2 gets 3333, u3 gets 3333
        val split2 = MoneyUtils.calculateEqualSplit(10000L, listOf("u2", "u1", "u3"))
        assertEquals(3334L, split2["u1"])
        assertEquals(3333L, split2["u2"])
        assertEquals(3333L, split2["u3"])
        assertEquals(10000L, split2.values.sum())

        // Case 3: ₹1 among 3 members -> 100 paise / 3 = 33 with remainder 1
        // u1 gets 34, u2 gets 33, u3 gets 33
        val split3 = MoneyUtils.calculateEqualSplit(100L, listOf("u1", "u2", "u3"))
        assertEquals(34L, split3["u1"])
        assertEquals(33L, split3["u2"])
        assertEquals(33L, split3["u3"])
        assertEquals(100L, split3.values.sum())

        // Case 4: 1 paisa among 2 members -> remainder 1 -> u1 gets 1 paisa, u2 gets 0 paise
        val split4 = MoneyUtils.calculateEqualSplit(1L, listOf("u1", "u2"))
        assertEquals(1L, split4["u1"])
        assertEquals(0L, split4["u2"])
        assertEquals(1L, split4.values.sum())
    }

    @Test
    fun testCustomSplitValidation() {
        val total = 50000L // ₹500
        val validShares = mapOf("u1" to 20000L, "u2" to 15000L, "u3" to 15000L)
        assertTrue(MoneyUtils.validateCustomSplit(total, validShares))

        val invalidShares = mapOf("u1" to 20000L, "u2" to 15000L, "u3" to 10000L) // sums to 45000
        assertFalse(MoneyUtils.validateCustomSplit(total, invalidShares))

        val negativeShares = mapOf("u1" to 60000L, "u2" to -10000L)
        assertFalse(MoneyUtils.validateCustomSplit(total, negativeShares))
    }

    @Test
    fun testUpiUriGeneration() {
        val uri = MoneyUtils.buildUpiUri("alice@okhdfcbank", "Alice Sharma", 25000L, "SplitSaathi: Dinner")
        assertTrue(uri.startsWith("upi://pay?"))
        assertTrue(uri.contains("pa=alice@okhdfcbank"))
        assertTrue(uri.contains("am=250.00"))
        assertTrue(uri.contains("cu=INR"))
        assertTrue(uri.contains("pn=Alice%20Sharma"))
    }

    @Test
    fun testUpiValidation() {
        assertTrue(MoneyUtils.isValidUpiId("alice@okhdfcbank"))
        assertTrue(MoneyUtils.isValidUpiId("user.name123@oksbi"))
        assertFalse(MoneyUtils.isValidUpiId("invalid-upi"))
        assertFalse(MoneyUtils.isValidUpiId("@oksbi"))
        assertFalse(MoneyUtils.isValidUpiId("alice@"))
    }
}
