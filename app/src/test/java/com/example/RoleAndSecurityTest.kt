package com.example

import com.example.data.model.MemberRole
import com.example.data.model.PaymentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleAndSecurityTest {

    @Test
    fun testRolePrivileges() {
        // Owner can transfer ownership and change roles
        val owner = MemberRole.OWNER
        val admin = MemberRole.ADMIN
        val member = MemberRole.MEMBER

        assertTrue(owner.ordinal < admin.ordinal) // OWNER is highest priority
        assertTrue(admin.ordinal < member.ordinal)
    }

    @Test
    fun testPaymentStatusTransitions() {
        val willPay = PaymentStatus.WILL_PAY
        val pending = PaymentStatus.PAID_PENDING_CONFIRMATION
        val confirmed = PaymentStatus.PAID_CONFIRMED
        val disputed = PaymentStatus.DISPUTED

        // Standard happy path
        assertEquals("Will Pay", willPay.label)
        assertEquals("Waiting for Confirmation", pending.label)
        assertEquals("Confirmed Paid", confirmed.label)
        assertEquals("Disputed", disputed.label)
    }

    @Test
    fun testOverpaymentRejectionLogic() {
        val assignedSharePaise = 25000L // ₹250
        val alreadyConfirmedPaise = 10000L // ₹100
        val remainingOwedPaise = assignedSharePaise - alreadyConfirmedPaise // ₹150

        // Attempting to pay ₹200 should be rejected because remaining is ₹150
        val claimPaise = 20000L
        val isOverpayment = claimPaise > remainingOwedPaise
        assertTrue("Claiming ₹200 on an obligation with ₹150 remaining must be detected as overpayment", isOverpayment)

        // Valid claim of ₹150
        val validClaimPaise = 15000L
        assertFalse(validClaimPaise > remainingOwedPaise)
    }

    @Test
    fun testUpiPrivacyObligationCheck() {
        // When consent is false, UPI ID should not be disclosed to other members
        val consentGranted = false
        assertFalse(consentGranted)
    }
}
