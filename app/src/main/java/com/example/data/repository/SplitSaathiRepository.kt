package com.example.data.repository

import com.example.data.local.SplitSaathiDatabase
import com.example.data.model.*
import com.example.util.MoneyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.util.UUID

import com.example.data.local.UserSessionManager

class SplitSaathiRepository(
    private val db: SplitSaathiDatabase,
    private val sessionManager: UserSessionManager? = null
) {

    // Current logged in user session
    private val _currentUserId = MutableStateFlow<String>(sessionManager?.currentUserId ?: "")
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    val currentProfile: Flow<ProfileEntity?> = _currentUserId.flatMapLatest { uid ->
        if (uid.isEmpty()) flowOf(null)
        else db.profileDao().observeProfileById(uid)
    }

    val allProfiles: Flow<List<ProfileEntity>> = db.profileDao().getAllProfiles()

    fun setCurrentUser(userId: String) {
        _currentUserId.value = userId
        sessionManager?.currentUserId = userId
    }

    suspend fun updateProfile(
        displayName: String,
        phone: String? = null,
        upiId: String?,
        upiConsent: Boolean,
        avatarKey: String
    ) {
        withContext(Dispatchers.IO) {
            val uid = _currentUserId.value
            val current = db.profileDao().getProfileById(uid) ?: return@withContext
            val updated = current.copy(
                displayName = displayName.trim(),
                phone = phone?.trim()?.ifEmpty { null },
                upiId = upiId?.trim()?.ifEmpty { null },
                upiConsent = upiConsent,
                upiConsentAt = if (upiConsent) System.currentTimeMillis() else null,
                avatarKey = avatarKey
            )
            db.profileDao().upsertProfile(updated)
            sessionManager?.saveUserSession(
                userId = uid,
                name = displayName.trim(),
                phone = phone?.trim()?.ifEmpty { null },
                upiId = upiId?.trim()?.ifEmpty { null },
                upiConsent = upiConsent
            )
        }
    }

    suspend fun createAccount(
        name: String,
        phone: String? = null,
        upiId: String? = null,
        upiConsent: Boolean = true,
        avatarKey: String = "avatar_1"
    ): String {
        return withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val profile = ProfileEntity(
                id = id,
                displayName = name.trim(),
                email = "",
                phone = phone?.trim()?.ifEmpty { null },
                upiId = upiId?.trim()?.ifEmpty { null },
                upiConsent = upiConsent,
                upiConsentAt = if (upiConsent) System.currentTimeMillis() else null,
                avatarKey = avatarKey
            )
            db.profileDao().upsertProfile(profile)
            sessionManager?.saveUserSession(
                userId = id,
                name = name.trim(),
                phone = phone?.trim()?.ifEmpty { null },
                upiId = upiId?.trim()?.ifEmpty { null },
                upiConsent = upiConsent
            )
            _currentUserId.value = id
            id
        }
    }

    suspend fun signOut() {
        withContext(Dispatchers.IO) {
            sessionManager?.clearSession()
            _currentUserId.value = ""
        }
    }

    // ---------------------------------------------------------------------------------------------
    // GROUPS & MEMBERSHIP
    // ---------------------------------------------------------------------------------------------

    fun observeUserGroups(): Flow<List<GroupEntity>> {
        return _currentUserId.flatMapLatest { uid ->
            db.groupDao().observeGroupsForUser(uid)
        }
    }

    fun observeGroupDetails(groupId: String): Flow<GroupDetail?> {
        val groupFlow = db.groupDao().observeGroupById(groupId)
        val membersFlow = db.groupDao().observeActiveMembers(groupId)
        val inviteFlow = db.groupDao().observeActiveInvite(groupId)
        val profilesFlow = db.profileDao().getAllProfiles()

        return combine(groupFlow, membersFlow, inviteFlow, profilesFlow) { group, members, invite, profiles ->
            if (group == null) return@combine null
            val profileMap = profiles.associateBy { it.id }
            val memberItems = members.mapNotNull { m ->
                val p = profileMap[m.userId] ?: ProfileEntity(m.userId, "Member ${m.userId.take(4)}", "")
                GroupMemberItem(user = p, role = m.role, status = m.status, joinedAt = m.joinedAt)
            }
            GroupDetail(
                group = group,
                members = memberItems,
                activeInviteCode = invite?.code
            )
        }
    }

    suspend fun createGroup(name: String, description: String?): String {
        return withContext(Dispatchers.IO) {
            val uid = _currentUserId.value
            val groupId = "group_" + UUID.randomUUID().toString().take(8)
            val group = GroupEntity(
                id = groupId,
                name = name.trim(),
                description = description?.trim()?.ifEmpty { null },
                ownerId = uid
            )
            db.groupDao().insertGroup(group)

            // Owner member entry
            val ownerMember = GroupMemberEntity(
                groupId = groupId,
                userId = uid,
                role = MemberRole.OWNER,
                status = MemberStatus.ACTIVE
            )
            db.groupDao().upsertMember(ownerMember)

            // Generate 6-char unique invite code
            val code = generateInviteCode()
            val invite = GroupInviteEntity(
                id = UUID.randomUUID().toString(),
                groupId = groupId,
                code = code,
                createdBy = uid
            )
            db.groupDao().insertInvite(invite)

            // Log activity
            db.activityDao().insertLog(
                ActivityLogEntity(
                    id = UUID.randomUUID().toString(),
                    groupId = groupId,
                    actorId = uid,
                    entityType = "group",
                    entityId = groupId,
                    action = "created",
                    description = "Created group '$name'"
                )
            )

            groupId
        }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    suspend fun joinGroupByCode(code: String): Result<String> {
        return withContext(Dispatchers.IO) {
            val uid = _currentUserId.value
            val cleanCode = code.trim().uppercase()
            val invite = db.groupDao().getInviteByCode(cleanCode)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid or expired invite code"))

            val group = db.groupDao().getGroupById(invite.groupId)
                ?: return@withContext Result.failure(IllegalStateException("Group not found"))

            // Add or reactivate member
            val existing = db.groupDao().getMember(group.id, uid)
            val member = GroupMemberEntity(
                groupId = group.id,
                userId = uid,
                role = existing?.role ?: MemberRole.MEMBER,
                status = MemberStatus.ACTIVE
            )
            db.groupDao().upsertMember(member)

            db.groupDao().updateInvite(invite.copy(uses = invite.uses + 1))

            val actor = db.profileDao().getProfileById(uid)?.displayName ?: "New member"
            db.activityDao().insertLog(
                ActivityLogEntity(
                    id = UUID.randomUUID().toString(),
                    groupId = group.id,
                    actorId = uid,
                    entityType = "member",
                    entityId = uid,
                    action = "joined",
                    description = "$actor joined the group via invite code"
                )
            )

            Result.success(group.id)
        }
    }

    suspend fun updateMemberRole(groupId: String, targetUserId: String, newRole: MemberRole): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val callerId = _currentUserId.value
            val callerMember = db.groupDao().getMember(groupId, callerId)
                ?: return@withContext Result.failure(IllegalStateException("You are not in this group"))

            if (callerMember.role != MemberRole.OWNER) {
                return@withContext Result.failure(IllegalStateException("Only group owner can promote or demote roles"))
            }

            val target = db.groupDao().getMember(groupId, targetUserId)
                ?: return@withContext Result.failure(IllegalStateException("Target member not found"))

            if (newRole == MemberRole.OWNER) {
                // Ownership transfer
                db.groupDao().upsertMember(callerMember.copy(role = MemberRole.ADMIN))
                db.groupDao().upsertMember(target.copy(role = MemberRole.OWNER))
                val grp = db.groupDao().getGroupById(groupId)
                if (grp != null) {
                    db.groupDao().updateGroup(grp.copy(ownerId = targetUserId))
                }
            } else {
                db.groupDao().upsertMember(target.copy(role = newRole))
            }
            Result.success(Unit)
        }
    }

    suspend fun leaveGroup(groupId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val uid = _currentUserId.value
            val member = db.groupDao().getMember(groupId, uid)
                ?: return@withContext Result.failure(IllegalStateException("Not a group member"))

            if (member.role == MemberRole.OWNER) {
                val otherMembers = db.groupDao().getActiveMembers(groupId).filter { it.userId != uid }
                if (otherMembers.isNotEmpty()) {
                    return@withContext Result.failure(IllegalStateException("Owner must transfer ownership before leaving"))
                }
            }

            db.groupDao().upsertMember(member.copy(status = MemberStatus.LEFT))
            Result.success(Unit)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // EXPENSES & MONEY LOGIC
    // ---------------------------------------------------------------------------------------------

    fun observeExpenses(groupId: String): Flow<List<ExpenseEntity>> {
        return db.expenseDao().observeExpensesForGroup(groupId)
    }

    fun observeAllExpenses(): Flow<List<ExpenseEntity>> {
        return db.expenseDao().observeAllExpenses()
    }

    suspend fun createExpense(
        groupId: String,
        title: String,
        amountPaise: Long,
        category: ExpenseCategory,
        paidBy: String,
        participantUserIds: List<String>,
        splitType: SplitType,
        customShares: Map<String, Long> = emptyMap(),
        notes: String? = null,
        receiptUri: String? = null
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            if (amountPaise <= 0) {
                return@withContext Result.failure(IllegalArgumentException("Amount must be greater than zero"))
            }
            if (participantUserIds.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("At least one participant is required"))
            }

            val shares: Map<String, Long> = if (splitType == SplitType.EQUAL) {
                MoneyUtils.calculateEqualSplit(amountPaise, participantUserIds)
            } else {
                if (!MoneyUtils.validateCustomSplit(amountPaise, customShares)) {
                    return@withContext Result.failure(IllegalArgumentException("Sum of custom shares must equal total amount exactly"))
                }
                customShares
            }

            val uid = _currentUserId.value
            val expenseId = "exp_" + UUID.randomUUID().toString().take(8)
            val expense = ExpenseEntity(
                id = expenseId,
                groupId = groupId,
                title = title.trim(),
                amountPaise = amountPaise,
                category = category,
                paidBy = paidBy,
                createdBy = uid,
                notes = notes?.trim()?.ifEmpty { null },
                receiptUri = receiptUri?.trim()?.ifEmpty { null },
                splitType = splitType
            )
            db.expenseDao().insertExpense(expense)

            // Save individual shares
            val shareEntities = shares.map { (userId, share) ->
                ExpenseShareEntity(expenseId = expenseId, userId = userId, sharePaise = share)
            }
            db.expenseDao().insertShares(shareEntities)

            // Save contributions for non-payers
            // Note: The payer's own share is automatically settled (Decision 5: payer does not owe themselves)
            val contributions = shares.filter { it.key != paidBy }.map { (userId, share) ->
                ContributionEntity(
                    id = "contrib_" + UUID.randomUUID().toString().take(8),
                    expenseId = expenseId,
                    userId = userId,
                    sharePaise = share,
                    committedPaise = 0L,
                    status = PaymentStatus.WILL_PAY
                )
            }
            db.expenseDao().insertContributions(contributions)

            // Log activity
            val payerName = db.profileDao().getProfileById(paidBy)?.displayName ?: "Someone"
            db.activityDao().insertLog(
                ActivityLogEntity(
                    id = UUID.randomUUID().toString(),
                    groupId = groupId,
                    actorId = uid,
                    entityType = "expense",
                    entityId = expenseId,
                    action = "created",
                    description = "Added expense '$title' for ${MoneyUtils.formatPaiseToRupees(amountPaise)} (Paid by $payerName)"
                )
            )

            // Notify non-payers
            contributions.forEach { c ->
                db.notificationDao().insertNotification(
                    NotificationEntity(
                        id = UUID.randomUUID().toString(),
                        userId = c.userId,
                        groupId = groupId,
                        type = NotificationType.NEW_EXPENSE,
                        title = "New Expense: $title",
                        body = "Your share is ${MoneyUtils.formatPaiseToRupees(c.sharePaise)} in '$title'."
                    )
                )
            }

            Result.success(expenseId)
        }
    }

    suspend fun setCommitment(contributionId: String, committedPaise: Long): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val contrib = db.expenseDao().getContributionById(contributionId)
                ?: return@withContext Result.failure(IllegalStateException("Contribution record not found"))

            if (committedPaise < 0 || committedPaise > contrib.sharePaise) {
                return@withContext Result.failure(IllegalArgumentException("Committed amount cannot exceed assigned share"))
            }

            db.expenseDao().updateContribution(contrib.copy(committedPaise = committedPaise, updatedAt = System.currentTimeMillis()))
            Result.success(Unit)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // PAYMENTS, CONFIRMATIONS & DISPUTES
    // ---------------------------------------------------------------------------------------------

    suspend fun claimPayment(
        contributionId: String,
        amountPaise: Long,
        upiReference: String?,
        note: String?,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            val contrib = db.expenseDao().getContributionById(contributionId)
                ?: return@withContext Result.failure(IllegalStateException("Contribution not found"))

            val expense = db.expenseDao().getExpenseById(contrib.expenseId)
                ?: return@withContext Result.failure(IllegalStateException("Expense not found"))

            // Overpayment protection (Decision 3: payment claim can never exceed remaining owed amount)
            val existingPayments = db.paymentDao().getPaymentsForContribution(contributionId)
            val alreadyConfirmed = existingPayments
                .filter { it.status == PaymentStatus.PAID_CONFIRMED }
                .sumOf { it.amountPaise }
            val remainingOwed = contrib.sharePaise - alreadyConfirmed

            if (amountPaise <= 0 || amountPaise > remainingOwed) {
                return@withContext Result.failure(IllegalArgumentException("Payment claim (${MoneyUtils.formatPaiseToRupees(amountPaise)}) exceeds remaining owed (${MoneyUtils.formatPaiseToRupees(remainingOwed)})"))
            }

            // Duplicate protection (Decision 11: only one open claim per obligation at a time)
            val hasOpenClaim = existingPayments.any {
                it.status == PaymentStatus.PAID_PENDING_CONFIRMATION || it.status == PaymentStatus.DISPUTED
            }
            if (hasOpenClaim) {
                return@withContext Result.failure(IllegalStateException("An active claim is already pending response for this obligation"))
            }

            val paymentId = "pay_" + UUID.randomUUID().toString().take(8)
            val record = PaymentRecordEntity(
                id = paymentId,
                contributionId = contributionId,
                fromUser = contrib.userId,
                toUser = expense.paidBy,
                amountPaise = amountPaise,
                upiReference = upiReference?.trim()?.ifEmpty { null },
                note = note?.trim()?.ifEmpty { null },
                status = PaymentStatus.PAID_PENDING_CONFIRMATION,
                idempotencyKey = idempotencyKey
            )
            db.paymentDao().insertPaymentRecord(record)

            db.expenseDao().updateContribution(contrib.copy(status = PaymentStatus.PAID_PENDING_CONFIRMATION))

            val debtorName = db.profileDao().getProfileById(contrib.userId)?.displayName ?: "Someone"
            db.notificationDao().insertNotification(
                NotificationEntity(
                    id = UUID.randomUUID().toString(),
                    userId = expense.paidBy,
                    groupId = expense.groupId,
                    type = NotificationType.PAYMENT_CLAIMED,
                    title = "Payment Claimed: ${MoneyUtils.formatPaiseToRupees(amountPaise)}",
                    body = "$debtorName claimed payment of ${MoneyUtils.formatPaiseToRupees(amountPaise)} for '${expense.title}'. Please verify in your UPI app and confirm."
                )
            )

            Result.success(paymentId)
        }
    }

    suspend fun confirmPayment(paymentId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val record = db.paymentDao().getPaymentById(paymentId)
                ?: return@withContext Result.failure(IllegalStateException("Payment record not found"))

            val callerId = _currentUserId.value
            val contrib = db.expenseDao().getContributionById(record.contributionId)
                ?: return@withContext Result.failure(IllegalStateException("Contribution not found"))
            val expense = db.expenseDao().getExpenseById(contrib.expenseId)
                ?: return@withContext Result.failure(IllegalStateException("Expense not found"))

            // Verify caller is recipient or group admin/owner after 7 days (Decision 8)
            val isRecipient = callerId == record.toUser
            val isGroupAdmin = isUserAdminOrOwner(expense.groupId, callerId)
            val isStuckSevenDays = (System.currentTimeMillis() - record.claimedAt) >= (7 * 24 * 60 * 60 * 1000L)

            if (!isRecipient && !(isGroupAdmin && isStuckSevenDays)) {
                return@withContext Result.failure(IllegalStateException("Only the recipient can confirm this payment (or group admin after 7 days)"))
            }

            if (record.status == PaymentStatus.PAID_CONFIRMED) {
                return@withContext Result.failure(IllegalStateException("Payment record has already been confirmed"))
            }

            if (record.status != PaymentStatus.PAID_PENDING_CONFIRMATION && record.status != PaymentStatus.DISPUTED) {
                return@withContext Result.failure(IllegalStateException("Invalid payment status for confirmation"))
            }

            val updatedRecord = record.copy(
                status = PaymentStatus.PAID_CONFIRMED,
                respondedAt = System.currentTimeMillis(),
                respondedBy = callerId
            )
            db.paymentDao().updatePaymentRecord(updatedRecord)

            // Check if contribution is now fully paid
            val allPayments = db.paymentDao().getPaymentsForContribution(record.contributionId)
            val totalConfirmed = allPayments.filter { it.status == PaymentStatus.PAID_CONFIRMED }.sumOf { it.amountPaise }
            val newStatus = if (totalConfirmed >= contrib.sharePaise) PaymentStatus.PAID_CONFIRMED else PaymentStatus.WILL_PAY
            db.expenseDao().updateContribution(contrib.copy(status = newStatus))

            val recipientName = db.profileDao().getProfileById(record.toUser)?.displayName ?: "Recipient"
            db.activityDao().insertLog(
                ActivityLogEntity(
                    id = UUID.randomUUID().toString(),
                    groupId = expense.groupId,
                    actorId = callerId,
                    entityType = "payment",
                    entityId = paymentId,
                    action = "confirmed",
                    description = "$recipientName confirmed receipt of ${MoneyUtils.formatPaiseToRupees(record.amountPaise)} for '${expense.title}'"
                )
            )

            db.notificationDao().insertNotification(
                NotificationEntity(
                    id = UUID.randomUUID().toString(),
                    userId = record.fromUser,
                    groupId = expense.groupId,
                    type = NotificationType.PAYMENT_CONFIRMED,
                    title = "Payment Confirmed! 🎉",
                    body = "$recipientName confirmed your payment of ${MoneyUtils.formatPaiseToRupees(record.amountPaise)}."
                )
            )

            Result.success(Unit)
        }
    }

    suspend fun disputePayment(paymentId: String, reason: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val record = db.paymentDao().getPaymentById(paymentId)
                ?: return@withContext Result.failure(IllegalStateException("Payment record not found"))

            val callerId = _currentUserId.value
            if (callerId != record.toUser) {
                return@withContext Result.failure(IllegalStateException("Only the recipient can dispute a payment claim"))
            }
            if (record.status == PaymentStatus.PAID_CONFIRMED) {
                return@withContext Result.failure(IllegalStateException("Cannot dispute an already confirmed payment"))
            }
            if (reason.trim().isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Dispute reason is mandatory"))
            }

            val updated = record.copy(
                status = PaymentStatus.DISPUTED,
                respondedAt = System.currentTimeMillis(),
                respondedBy = callerId,
                disputeReason = reason.trim()
            )
            db.paymentDao().updatePaymentRecord(updated)

            val contrib = db.expenseDao().getContributionById(record.contributionId)
            if (contrib != null) {
                db.expenseDao().updateContribution(contrib.copy(status = PaymentStatus.DISPUTED))
            }

            db.notificationDao().insertNotification(
                NotificationEntity(
                    id = UUID.randomUUID().toString(),
                    userId = record.fromUser,
                    type = NotificationType.PAYMENT_DISPUTED,
                    title = "Payment Claim Disputed",
                    body = "Your payment claim was disputed: \"${reason.trim()}\". You can edit the details or contact the recipient."
                )
            )

            Result.success(Unit)
        }
    }

    suspend fun nudgePayment(paymentId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val record = db.paymentDao().getPaymentById(paymentId)
                ?: return@withContext Result.failure(IllegalStateException("Payment not found"))

            val callerId = _currentUserId.value
            if (callerId != record.fromUser) {
                return@withContext Result.failure(IllegalStateException("Only the debtor can nudge the recipient"))
            }
            if (record.status != PaymentStatus.PAID_PENDING_CONFIRMATION) {
                return@withContext Result.failure(IllegalStateException("Cannot nudge a payment that is not pending confirmation"))
            }

            val now = System.currentTimeMillis()
            val lastNudge = record.lastNudgedAt ?: 0L
            val cooldown = 24 * 60 * 60 * 1000L // 24 hours (Decision 8)
            if (now - lastNudge < cooldown) {
                val remainingHours = ((cooldown - (now - lastNudge)) / (60 * 60 * 1000L)) + 1
                return@withContext Result.failure(IllegalStateException("You can only nudge once per 24 hours (try in $remainingHours hours)"))
            }

            db.paymentDao().updatePaymentRecord(record.copy(lastNudgedAt = now))

            val senderName = db.profileDao().getProfileById(record.fromUser)?.displayName ?: "Someone"
            db.notificationDao().insertNotification(
                NotificationEntity(
                    id = UUID.randomUUID().toString(),
                    userId = record.toUser,
                    type = NotificationType.NUDGE,
                    title = "Payment Confirmation Reminder 👋",
                    body = "$senderName is waiting for you to verify their payment of ${MoneyUtils.formatPaiseToRupees(record.amountPaise)}."
                )
            )
            Result.success(Unit)
        }
    }

    private suspend fun isUserAdminOrOwner(groupId: String, userId: String): Boolean {
        val member = db.groupDao().getMember(groupId, userId) ?: return false
        return member.role == MemberRole.OWNER || member.role == MemberRole.ADMIN
    }

    // ---------------------------------------------------------------------------------------------
    // WHO OWES WHOM & PRIVACY
    // ---------------------------------------------------------------------------------------------

    fun observeWhoOwesWhom(groupId: String): Flow<List<WhoOwesWhomItem>> {
        val expensesFlow = db.expenseDao().observeExpensesForGroup(groupId)
        val contributionsFlow = db.expenseDao().observeContributionsForGroup(groupId)
        val profilesFlow = db.profileDao().getAllProfiles()
        val paymentsFlow = db.paymentDao().observePaymentsForGroup(groupId)

        return combine(expensesFlow, contributionsFlow, profilesFlow, paymentsFlow) { expenses, contributions, profiles, payments ->
            val profileMap = profiles.associateBy { it.id }
            val expenseMap = expenses.associateBy { it.id }
            val paymentsByContrib = payments.groupBy { it.contributionId }

            contributions.mapNotNull { contrib ->
                val expense = expenseMap[contrib.expenseId] ?: return@mapNotNull null
                val debtor = profileMap[contrib.userId] ?: ProfileEntity(contrib.userId, "User", "")
                val creditor = profileMap[expense.paidBy] ?: ProfileEntity(expense.paidBy, "Payer", "")
                val latestPay = paymentsByContrib[contrib.id]?.maxByOrNull { it.claimedAt }

                WhoOwesWhomItem(
                    expenseId = expense.id,
                    expenseTitle = expense.title,
                    debtorId = contrib.userId,
                    debtorName = debtor.displayName,
                    creditorId = expense.paidBy,
                    creditorName = creditor.displayName,
                    amountPaise = contrib.sharePaise,
                    status = contrib.status,
                    contributionId = contrib.id,
                    latestPaymentRecord = latestPay
                )
            }
        }
    }

    suspend fun getPayeeUpi(payeeId: String): Result<String?> {
        return withContext(Dispatchers.IO) {
            val callerId = _currentUserId.value
            val payee = db.profileDao().getProfileById(payeeId)
                ?: return@withContext Result.failure(IllegalStateException("Payee not found"))

            if (callerId == payeeId) {
                return@withContext Result.success(payee.upiId)
            }

            // Enforce Decision 12: UPI ID is visible only to people who currently need to pay them
            if (!payee.upiConsent) {
                return@withContext Result.failure(IllegalStateException("Payee has not consented to sharing their UPI ID"))
            }

            Result.success(payee.upiId)
        }
    }

    // Activity & Notifications
    fun observeRecentActivity(groupId: String): Flow<List<ActivityLogEntity>> {
        return db.activityDao().observeLogsForGroup(groupId)
    }

    fun observeNotifications(): Flow<List<NotificationEntity>> {
        return _currentUserId.flatMapLatest { uid ->
            db.notificationDao().observeNotifications(uid)
        }
    }

    suspend fun markNotificationAsRead(id: String) {
        withContext(Dispatchers.IO) {
            db.notificationDao().markAsRead(id)
        }
    }

    suspend fun markAllNotificationsAsRead() {
        withContext(Dispatchers.IO) {
            val uid = _currentUserId.value
            db.notificationDao().markAllAsRead(uid)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // ADVANCED EXPENSE & DISPUTE WORKFLOWS (Phase 2 & 3)
    // ---------------------------------------------------------------------------------------------

    suspend fun cancelExpense(expenseId: String, reason: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val expense = db.expenseDao().getExpenseById(expenseId)
                ?: return@withContext Result.failure(IllegalStateException("Expense not found"))

            val callerId = _currentUserId.value
            val isCallerAdmin = isUserAdminOrOwner(expense.groupId, callerId)
            val isCreator = callerId == expense.createdBy

            if (!isCreator && !isCallerAdmin) {
                return@withContext Result.failure(IllegalStateException("Only the creator or a group admin can cancel an expense"))
            }

            // Decision 10: Expenses with confirmed payments can only be cancelled by an owner/admin
            val contributions = db.expenseDao().getContributionsForExpense(expenseId)
            val hasConfirmedPayments = contributions.any { it.status == PaymentStatus.PAID_CONFIRMED }
            if (hasConfirmedPayments && !isCallerAdmin) {
                return@withContext Result.failure(IllegalStateException("This expense has confirmed payments and can only be cancelled by a group admin/owner"))
            }

            if (reason.trim().isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Cancellation reason is mandatory"))
            }

            // Soft delete expense
            db.expenseDao().updateExpense(
                expense.copy(
                    status = ExpenseStatus.CANCELLED,
                    cancelReason = reason.trim()
                )
            )

            // Cancel open contributions
            contributions.forEach { c ->
                if (c.status != PaymentStatus.PAID_CONFIRMED) {
                    db.expenseDao().updateContribution(c.copy(status = PaymentStatus.CANCELLED))
                }
            }

            val cancellerName = db.profileDao().getProfileById(callerId)?.displayName ?: "Someone"
            db.activityDao().insertLog(
                ActivityLogEntity(
                    id = UUID.randomUUID().toString(),
                    groupId = expense.groupId,
                    actorId = callerId,
                    entityType = "expense",
                    entityId = expenseId,
                    action = "cancelled",
                    description = "$cancellerName cancelled expense '${expense.title}'. Reason: ${reason.trim()}"
                )
            )

            Result.success(Unit)
        }
    }

    suspend fun resolveDispute(paymentId: String, confirm: Boolean, disputeResolutionNote: String? = null): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val record = db.paymentDao().getPaymentById(paymentId)
                ?: return@withContext Result.failure(IllegalStateException("Payment record not found"))

            val callerId = _currentUserId.value
            val contrib = db.expenseDao().getContributionById(record.contributionId)
                ?: return@withContext Result.failure(IllegalStateException("Contribution not found"))
            val expense = db.expenseDao().getExpenseById(contrib.expenseId)
                ?: return@withContext Result.failure(IllegalStateException("Expense not found"))

            val isAdmin = isUserAdminOrOwner(expense.groupId, callerId)
            val isRecipient = callerId == record.toUser

            if (!isAdmin && !isRecipient) {
                return@withContext Result.failure(IllegalStateException("Only group admin or recipient can resolve disputes"))
            }

            val newStatus = if (confirm) PaymentStatus.PAID_CONFIRMED else PaymentStatus.REJECTED_RESOLVED
            val updatedRecord = record.copy(
                status = newStatus,
                respondedAt = System.currentTimeMillis(),
                respondedBy = callerId,
                resolvedBy = callerId
            )
            db.paymentDao().updatePaymentRecord(updatedRecord)

            db.expenseDao().updateContribution(contrib.copy(status = newStatus))

            val resolverName = db.profileDao().getProfileById(callerId)?.displayName ?: "Admin"
            val actionLabel = if (confirm) "confirmed" else "rejected"
            db.activityDao().insertLog(
                ActivityLogEntity(
                    id = UUID.randomUUID().toString(),
                    groupId = expense.groupId,
                    actorId = callerId,
                    entityType = "payment",
                    entityId = paymentId,
                    action = "dispute_resolved",
                    description = "$resolverName resolved dispute and $actionLabel the payment of ${MoneyUtils.formatPaiseToRupees(record.amountPaise)}"
                )
            )

            Result.success(Unit)
        }
    }

    fun observeGroupSummary(groupId: String): Flow<GroupSummary> {
        val expensesFlow = db.expenseDao().observeExpensesForGroup(groupId)
        val contributionsFlow = db.expenseDao().observeContributionsForGroup(groupId)
        val paymentsFlow = db.paymentDao().observePaymentsForGroup(groupId)

        return combine(expensesFlow, contributionsFlow, paymentsFlow, _currentUserId) { expenses, contributions, payments, uid ->
            val totalExpenses = expenses.sumOf { it.amountPaise }
            val myPaid = expenses.filter { it.paidBy == uid }.sumOf { it.amountPaise }
            val confirmedPayments = payments.filter { it.status == PaymentStatus.PAID_CONFIRMED }.sumOf { it.amountPaise }
            val remainingToCollect = maxOf(0L, totalExpenses - myPaid - confirmedPayments)

            val pendingCount = payments.count { it.status == PaymentStatus.PAID_PENDING_CONFIRMATION }
            val disputedCount = payments.count { it.status == PaymentStatus.DISPUTED }

            GroupSummary(
                totalExpensesPaise = totalExpenses,
                myTotalPaidPaise = myPaid,
                totalCollectedPaise = confirmedPayments,
                totalRemainingPaise = remainingToCollect,
                pendingConfirmationCount = pendingCount,
                disputedCount = disputedCount
            )
        }
    }

    fun observeNetPairwiseBalances(groupId: String): Flow<List<PairwiseNetBalance>> {
        return observeWhoOwesWhom(groupId).map { obligations ->
            // Filter non-cancelled, non-confirmed obligations
            val active = obligations.filter { it.status != PaymentStatus.PAID_CONFIRMED && it.status != PaymentStatus.CANCELLED }
            val pairMap = mutableMapOf<Pair<String, String>, Long>()
            val nameMap = mutableMapOf<String, String>()

            active.forEach { item ->
                nameMap[item.debtorId] = item.debtorName
                nameMap[item.creditorId] = item.creditorName

                val pairKey = if (item.debtorId < item.creditorId) {
                    Pair(item.debtorId, item.creditorId)
                } else {
                    Pair(item.creditorId, item.debtorId)
                }

                val sign = if (pairKey.first == item.debtorId) 1L else -1L
                pairMap[pairKey] = (pairMap[pairKey] ?: 0L) + (sign * item.amountPaise)
            }

            pairMap.mapNotNull { (pair, net) ->
                if (net == 0L) null
                else if (net > 0) {
                    PairwiseNetBalance(
                        userAId = pair.first,
                        userAName = nameMap[pair.first] ?: "User",
                        userBId = pair.second,
                        userBName = nameMap[pair.second] ?: "User",
                        netOwedPaise = net
                    )
                } else {
                    PairwiseNetBalance(
                        userAId = pair.second,
                        userAName = nameMap[pair.second] ?: "User",
                        userBId = pair.first,
                        userBName = nameMap[pair.first] ?: "User",
                        netOwedPaise = -net
                    )
                }
            }
        }
    }
}
