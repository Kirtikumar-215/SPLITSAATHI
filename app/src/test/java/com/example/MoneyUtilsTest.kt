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
        assertNull(MoneyUtils.parseRupeesToPaise(""))
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
        val split2 = MoneyUtils.calculateEqualSplit(10000L, listOf("u2", "u1", "u3"))
        assertEquals(3334L, split2["u1"])
        assertEquals(3333L, split2["u2"])
        assertEquals(3333L, split2["u3"])
        assertEquals(10000L, split2.values.sum())

        // Case 3: 7 participants with remainder (₹100 = 10000 paise / 7 = 1428 with remainder 4)
        val sevenUsers = (1..7).map { "user_$it" }
        val split7 = MoneyUtils.calculateEqualSplit(10000L, sevenUsers)
        assertEquals(7, split7.size)
        assertEquals(10000L, split7.values.sum())
        // Exactly 4 participants get 1429, 3 get 1428
        assertEquals(4, split7.values.count { it == 1429L })
        assertEquals(3, split7.values.count { it == 1428L })

        // Case 4: 17 participants with remainder (₹1000.50 = 100050 paise)
        val seventeenUsers = (1..17).map { "user_$it" }
        val split17 = MoneyUtils.calculateEqualSplit(100050L, seventeenUsers)
        assertEquals(17, split17.size)
        assertEquals(100050L, split17.values.sum())

        // Case 5: 1 paisa among 2 members -> remainder 1 -> u1 gets 1 paisa, u2 gets 0 paise
        val split4 = MoneyUtils.calculateEqualSplit(1L, listOf("u1", "u2"))
        assertEquals(1L, split4["u1"])
        assertEquals(0L, split4["u2"])
        assertEquals(1L, split4.values.sum())
    }

    @Test
    fun testPercentageSplit() {
        val total = 100000L // ₹1000
        val pcts = mapOf("u1" to 50.0, "u2" to 30.0, "u3" to 20.0)
        val split = MoneyUtils.calculatePercentageSplit(total, pcts)
        assertEquals(50000L, split["u1"])
        assertEquals(30000L, split["u2"])
        assertEquals(20000L, split["u3"])
        assertEquals(total, split.values.sum())

        // Uneven percentage (33.33%, 33.33%, 33.34%)
        val unevenPcts = mapOf("u1" to 33.33, "u2" to 33.33, "u3" to 33.34)
        val unevenSplit = MoneyUtils.calculatePercentageSplit(10000L, unevenPcts)
        assertEquals(10000L, unevenSplit.values.sum())
    }

    @Test
    fun testSharesRatioSplit() {
        val total = 100000L // ₹1000
        val weights = mapOf("u1" to 1, "u2" to 2, "u3" to 3) // total weights = 6
        val split = MoneyUtils.calculateSharesRatioSplit(total, weights)
        assertEquals(3, split.size)
        assertEquals(total, split.values.sum())

        // Unequal weights with remainder
        val weightsWithRemainder = mapOf("u1" to 3, "u2" to 5, "u3" to 7)
        val splitRemainder = MoneyUtils.calculateSharesRatioSplit(50001L, weightsWithRemainder)
        assertEquals(50001L, splitRemainder.values.sum())
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

    @Test
    fun testPhoneValidation() {
        assertTrue(MoneyUtils.isValidIndianPhone("9876543210"))
        assertTrue(MoneyUtils.isValidIndianPhone("6123456789"))
        assertFalse(MoneyUtils.isValidIndianPhone("5123456789")) // doesn't start with 6-9
        assertFalse(MoneyUtils.isValidIndianPhone("12345")) // too short
        assertFalse(MoneyUtils.isValidIndianPhone("98765432101")) // too long
    }
}
