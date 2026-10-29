package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SplitSaathiDatabase
import com.example.data.local.UserSessionManager
import com.example.data.model.*
import com.example.data.repository.SplitSaathiRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: SplitSaathiDatabase
    private lateinit var sessionManager: UserSessionManager
    private lateinit var repository: SplitSaathiRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SplitSaathiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionManager = UserSessionManager(context)
        sessionManager.clearSession()
        repository = SplitSaathiRepository(db, sessionManager)
    }

    @After
    fun tearDown() {
        sessionManager.clearSession()
        db.close()
    }

    @Test
    fun testAppNameString() {
        val appName = context.getString(R.string.app_name)
        assertEquals("SplitSaathi", appName)
    }

    @Test
    fun testUserOnboardingAndSessionPersistence() = runBlocking {
        // Initially no user logged in
        assertNull(sessionManager.currentUserId)
        assertFalse(sessionManager.isOnboardingCompleted)

        // Create new user account via onboarding
        val userId = repository.createAccount(
            name = "Rohan Mehta",
            phone = "9876543210",
            upiId = "rohan@okhdfcbank",
            upiConsent = true,
            avatarKey = "avatar_2"
        )

        assertNotNull(userId)
        assertTrue(userId.isNotEmpty())
        assertTrue(sessionManager.isOnboardingCompleted)
        assertEquals("Rohan Mehta", sessionManager.currentUserName)
        assertEquals("9876543210", sessionManager.currentUserPhone)
        assertEquals("rohan@okhdfcbank", sessionManager.currentUserUpi)
        assertTrue(sessionManager.isUpiConsentGranted)

        // Verify profile in Room database
        val savedProfile = db.profileDao().getProfileById(userId)
        assertNotNull(savedProfile)
        assertEquals("Rohan Mehta", savedProfile?.displayName)
        assertTrue(savedProfile?.upiConsent == true)

        // Sign out clears session
        repository.signOut()
        assertFalse(sessionManager.isOnboardingCompleted)
        assertNull(sessionManager.currentUserId)
        assertEquals("", repository.currentUserId.value)
    }

    @Test
    fun testGroupCreationAndInviteCode() = runBlocking {
        val ownerId = repository.createAccount("Aarav Patel", phone = null, upiId = "aarav@oksbi")

        val groupId = repository.createGroup(
            name = "Trip to Goa",
            description = "Weekend trip expenses"
        )

        assertNotNull(groupId)
        val group = db.groupDao().getGroupById(groupId)
        assertNotNull(group)
        assertEquals("Trip to Goa", group?.name)
        assertEquals(ownerId, group?.ownerId)

        // Owner must be active OWNER member
        val ownerMember = db.groupDao().getMember(groupId, ownerId)
        assertNotNull(ownerMember)
        assertEquals(MemberRole.OWNER, ownerMember?.role)
        assertEquals(MemberStatus.ACTIVE, ownerMember?.status)

        // 6-character alphanumeric invite code generated
        val invite = db.groupDao().observeActiveInvite(groupId).first()
        assertNotNull(invite)
        val code = invite?.code ?: ""
        assertEquals(6, code.length)
        assertTrue(code.all { it.isLetterOrDigit() })
    }

    @Test
    fun testJoinGroupByCode() = runBlocking {
        val ownerId = repository.createAccount("Owner User", phone = null, upiId = "owner@oksbi")
        val groupId = repository.createGroup("Room 301", "Flat expenses")
        val invite = db.groupDao().observeActiveInvite(groupId).first()!!

        // Second user logs in and joins
        val member2Id = repository.createAccount("Member Two", phone = null, upiId = "member2@okicici")
        repository.setCurrentUser(member2Id)

        val joinResult = repository.joinGroupByCode(invite.code)
        assertTrue("Joining with valid invite code should succeed", joinResult.isSuccess)
        assertEquals(groupId, joinResult.getOrNull())

        val memberEntry = db.groupDao().getMember(groupId, member2Id)
        assertNotNull(memberEntry)
        assertEquals(MemberRole.MEMBER, memberEntry?.role)
        assertEquals(MemberStatus.ACTIVE, memberEntry?.status)

        // Joining with invalid code should fail gracefully
        val invalidResult = repository.joinGroupByCode("INVALID")
        assertTrue("Joining with invalid code must fail", invalidResult.isFailure)
    }

    @Test
    fun testRoleManagementAndOwnershipTransfer() = runBlocking {
        val ownerId = repository.createAccount("Original Owner")
        val groupId = repository.createGroup("College Project", null)
        val invite = db.groupDao().observeActiveInvite(groupId).first()!!

        val teammateId = repository.createAccount("Teammate")
        repository.setCurrentUser(teammateId)
        repository.joinGroupByCode(invite.code)

        // Teammate cannot promote members
        val unauthorizedAttempt = repository.updateMemberRole(groupId, ownerId, MemberRole.ADMIN)
        assertTrue("Non-owner cannot change roles", unauthorizedAttempt.isFailure)

        // Switch back to Owner
        repository.setCurrentUser(ownerId)

        // Promote teammate to ADMIN
        val promoteResult = repository.updateMemberRole(groupId, teammateId, MemberRole.ADMIN)
        assertTrue(promoteResult.isSuccess)
        assertEquals(MemberRole.ADMIN, db.groupDao().getMember(groupId, teammateId)?.role)

        // Transfer Ownership to teammate
        val transferResult = repository.updateMemberRole(groupId, teammateId, MemberRole.OWNER)
        assertTrue(transferResult.isSuccess)

        // Check that roles swapped: old owner is now ADMIN, new owner is OWNER
        assertEquals(MemberRole.ADMIN, db.groupDao().getMember(groupId, ownerId)?.role)
        assertEquals(MemberRole.OWNER, db.groupDao().getMember(groupId, teammateId)?.role)
        assertEquals(teammateId, db.groupDao().getGroupById(groupId)?.ownerId)
    }

    @Test
    fun testExpenseEqualSplitAndRemainderHandling() = runBlocking {
        val u1 = repository.createAccount("User A", phone = null, upiId = "usera@oksbi")
        val groupId = repository.createGroup("Dinner Club", null)
        val invite = db.groupDao().observeActiveInvite(groupId).first()!!

        val u2 = repository.createAccount("User B", phone = null, upiId = "userb@oksbi")
        repository.setCurrentUser(u2)
        repository.joinGroupByCode(invite.code)

        val u3 = repository.createAccount("User C", phone = null, upiId = "userc@oksbi")
        repository.setCurrentUser(u3)
        repository.joinGroupByCode(invite.code)

        // User A pays ₹100.00 (10000 paise) split equally among 3 people
        repository.setCurrentUser(u1)
        val expenseResult = repository.createExpense(
            groupId = groupId,
            title = "Pizza Party",
            amountPaise = 10000L, // ₹100.00
            category = ExpenseCategory.FOOD,
            paidBy = u1,
            participantUserIds = listOf(u1, u2, u3),
            splitType = SplitType.EQUAL
        )

        assertTrue(expenseResult.isSuccess)
        val expenseId = expenseResult.getOrNull()!!

        // Total shares must sum up to exactly 10000 paise (3334 + 3333 + 3333)
        val shares = db.expenseDao().getSharesForExpense(expenseId)
        assertEquals(3, shares.size)
        val totalSharesPaise = shares.sumOf { it.sharePaise }
        assertEquals(10000L, totalSharesPaise)

        // Non-payers (u2 and u3) have contribution records; payer (u1) does not owe himself
        val contributions = db.expenseDao().getContributionsForExpense(expenseId)
        assertEquals(2, contributions.size)
        assertTrue(contributions.none { it.userId == u1 })
        assertTrue(contributions.any { it.userId == u2 })
        assertTrue(contributions.any { it.userId == u3 })

        // Check notifications sent to u2 and u3
        val u2Notifications = db.notificationDao().observeNotifications(u2).first()
        assertEquals(1, u2Notifications.size)
        assertEquals(NotificationType.NEW_EXPENSE, u2Notifications.first().type)
    }

    @Test
    fun testPaymentClaimConfirmationAndDisputeLifecycle() = runBlocking {
        val payerId = repository.createAccount("Hostel Cook", phone = null, upiId = "cook@okhdfc")
        val groupId = repository.createGroup("Hostel Mess", null)
        val invite = db.groupDao().observeActiveInvite(groupId).first()!!

        val studentId = repository.createAccount("Hostel Student", phone = null, upiId = "student@oksbi")
        repository.setCurrentUser(studentId)
        repository.joinGroupByCode(invite.code)

        // Cook adds ₹500 grocery expense split between Cook and Student (₹250 each)
        repository.setCurrentUser(payerId)
        val expResult = repository.createExpense(
            groupId = groupId,
            title = "Groceries",
            amountPaise = 50000L, // ₹500.00
            category = ExpenseCategory.GROCERIES,
            paidBy = payerId,
            participantUserIds = listOf(payerId, studentId),
            splitType = SplitType.EQUAL
        )
        val expenseId = expResult.getOrNull()!!

        val contrib = db.expenseDao().getContributionsForExpense(expenseId).first { it.userId == studentId }
        assertEquals(25000L, contrib.sharePaise) // ₹250.00
        assertEquals(PaymentStatus.WILL_PAY, contrib.status)

        // Overpayment test: Student trying to claim ₹300 on a ₹250 obligation must fail
        repository.setCurrentUser(studentId)
        val overpaymentResult = repository.claimPayment(
            contributionId = contrib.id,
            amountPaise = 30000L, // ₹300.00
            upiReference = "UTR123456789012",
            note = "Overpayment attempt"
        )
        assertTrue("Overpayment must be rejected", overpaymentResult.isFailure)

        // Valid payment claim of ₹250.00
        val claimResult = repository.claimPayment(
            contributionId = contrib.id,
            amountPaise = 25000L,
            upiReference = "UTR123456789012",
            note = "Paid via PhonePe"
        )
        assertTrue(claimResult.isSuccess)
        val paymentId = claimResult.getOrNull()!!

        // Duplicate claim attempt while one is pending must fail
        val duplicateClaimResult = repository.claimPayment(
            contributionId = contrib.id,
            amountPaise = 25000L,
            upiReference = "UTR999999",
            note = "Second claim attempt"
        )
        assertTrue("Duplicate open claim must be rejected", duplicateClaimResult.isFailure)

        // Payment record status is PAID_PENDING_CONFIRMATION
        val paymentRecord = db.paymentDao().getPaymentById(paymentId)
        assertNotNull(paymentRecord)
        assertEquals(PaymentStatus.PAID_PENDING_CONFIRMATION, paymentRecord?.status)

        // Only the recipient (payerId) can confirm or dispute
        val unauthorizedConfirm = repository.confirmPayment(paymentId) // studentId is current user
        assertTrue("Debtor cannot confirm their own payment", unauthorizedConfirm.isFailure)

        // Switch to Cook (recipient) and dispute with reason
        repository.setCurrentUser(payerId)
        val disputeResult = repository.disputePayment(paymentId, "Amount not received in bank account")
        assertTrue(disputeResult.isSuccess)

        val disputedPayment = db.paymentDao().getPaymentById(paymentId)
        assertEquals(PaymentStatus.DISPUTED, disputedPayment?.status)
        assertEquals("Amount not received in bank account", disputedPayment?.disputeReason)

        // Creditor confirms receipt after resolution
        val confirmResult = repository.confirmPayment(paymentId)
        assertTrue(confirmResult.isSuccess)

        val confirmedPayment = db.paymentDao().getPaymentById(paymentId)
        assertEquals(PaymentStatus.PAID_CONFIRMED, confirmedPayment?.status)

        // Contribution should now reflect PAID_CONFIRMED
        val updatedContrib = db.expenseDao().getContributionById(contrib.id)
        assertEquals(PaymentStatus.PAID_CONFIRMED, updatedContrib?.status)

        // Debtor received confirmation notification
        val studentNotifs = db.notificationDao().observeNotifications(studentId).first()
        assertTrue(studentNotifs.any { it.type == NotificationType.PAYMENT_CONFIRMED })

        // Repeated confirmation on already confirmed payment must fail
        val repeatedConfirm = repository.confirmPayment(paymentId)
        assertTrue("Repeated confirmation on confirmed payment must fail", repeatedConfirm.isFailure)

        // Disputing an already confirmed payment must fail
        val disputeConfirmed = repository.disputePayment(paymentId, "Late dispute attempt")
        assertTrue("Cannot dispute an already confirmed payment", disputeConfirmed.isFailure)
    }

    @Test
    fun testPartialPaymentsAndRemainingObligation() = runBlocking {
        val payerId = repository.createAccount("Flatmate Payer", upiId = "payer@okhdfc")
        val groupId = repository.createGroup("Apartment Bills", null)
        val invite = db.groupDao().observeActiveInvite(groupId).first()!!

        val debtorId = repository.createAccount("Flatmate Debtor", upiId = "debtor@oksbi")
        repository.setCurrentUser(debtorId)
        repository.joinGroupByCode(invite.code)

        // Add ₹1,000 expense split equally: Debtor owes ₹500 (50,000 paise)
        repository.setCurrentUser(payerId)
        val expResult = repository.createExpense(
            groupId = groupId,
            title = "Electricity Bill",
            amountPaise = 100000L,
            category = ExpenseCategory.UTILITIES,
            paidBy = payerId,
            participantUserIds = listOf(payerId, debtorId),
            splitType = SplitType.EQUAL
        )
        val expenseId = expResult.getOrNull()!!
        val contrib = db.expenseDao().getContributionsForExpense(expenseId).first { it.userId == debtorId }
        assertEquals(50000L, contrib.sharePaise)

        // Step 1: Debtor pays partial ₹200 (20,000 paise)
        repository.setCurrentUser(debtorId)
        val claim1 = repository.claimPayment(contrib.id, 20000L, "UTR_PARTIAL_1", "Partial payment 1").getOrNull()!!

        // Step 2: Payer confirms partial payment
        repository.setCurrentUser(payerId)
        val confirm1 = repository.confirmPayment(claim1)
        assertTrue(confirm1.isSuccess)

        // Obligation should NOT be confirmed yet because ₹300 remains outstanding
        val contribAfterPart1 = db.expenseDao().getContributionById(contrib.id)!!
        assertEquals(PaymentStatus.WILL_PAY, contribAfterPart1.status)

        // Step 3: Debtor tries to claim ₹350 on remaining ₹300 -> MUST FAIL
        repository.setCurrentUser(debtorId)
        val overpayment = repository.claimPayment(contrib.id, 35000L, "UTR_OVERPAY", "Too much")
        assertTrue("Overpayment beyond remaining balance must fail", overpayment.isFailure)

        // Step 4: Debtor claims exact remaining ₹300 (30,000 paise)
        val claim2 = repository.claimPayment(contrib.id, 30000L, "UTR_PARTIAL_2", "Final payment").getOrNull()!!

        // Step 5: Payer confirms final payment
        repository.setCurrentUser(payerId)
        val confirm2 = repository.confirmPayment(claim2)
        assertTrue(confirm2.isSuccess)

        // Obligation is now fully settled
        val contribFinal = db.expenseDao().getContributionById(contrib.id)!!
        assertEquals(PaymentStatus.PAID_CONFIRMED, contribFinal.status)
    }

    @Test
    fun testNudgePaymentRules() = runBlocking {
        val payerId = repository.createAccount("Payer Nudge")
        val groupId = repository.createGroup("Nudge Group", null)
        val invite = db.groupDao().observeActiveInvite(groupId).first()!!

        val debtorId = repository.createAccount("Debtor Nudge")
        repository.setCurrentUser(debtorId)
        repository.joinGroupByCode(invite.code)

        repository.setCurrentUser(payerId)
        val expId = repository.createExpense(
            groupId = groupId,
            title = "Lunch",
            amountPaise = 20000L,
            category = ExpenseCategory.FOOD,
            paidBy = payerId,
            participantUserIds = listOf(payerId, debtorId),
            splitType = SplitType.EQUAL
        ).getOrNull()!!
        val contrib = db.expenseDao().getContributionsForExpense(expId).first { it.userId == debtorId }

        repository.setCurrentUser(debtorId)
        val payId = repository.claimPayment(contrib.id, 10000L, "UTR123", "Paid lunch").getOrNull()!!

        // Debtor nudges payer -> First nudge succeeds
        val nudge1 = repository.nudgePayment(payId)
        assertTrue("First nudge should succeed", nudge1.isSuccess)

        // Immediate second nudge must be rejected by 24h cooldown
        val nudge2 = repository.nudgePayment(payId)
        assertTrue("Second nudge within 24 hours must be rejected", nudge2.isFailure)

        // Payer (recipient) cannot nudge themselves
        repository.setCurrentUser(payerId)
        val unauthorizedNudge = repository.nudgePayment(payId)
        assertTrue("Non-debtor cannot send nudge", unauthorizedNudge.isFailure)
    }

    @Test
    fun testRoomMigrationDefinition() {
        val migration = SplitSaathiDatabase.MIGRATION_1_2
        assertEquals(1, migration.startVersion)
        assertEquals(2, migration.endVersion)
    }

    @Test
    fun testActivityAuditTrail() = runBlocking {
        repository.createAccount("Audit User")
        val groupId = repository.createGroup("Audit Group", "Testing audit logs")

        val logs = db.activityDao().observeLogsForGroup(groupId).first()
        assertTrue("Activity log must capture group creation", logs.isNotEmpty())
        assertEquals("group", logs.first().entityType)
        assertEquals("created", logs.first().action)
    }
}
