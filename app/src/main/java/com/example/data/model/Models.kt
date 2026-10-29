package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Enums representing core business domain states
 */
enum class MemberRole {
    OWNER,
    ADMIN,
    MEMBER
}

enum class MemberStatus {
    ACTIVE,
    REMOVED,
    LEFT
}

enum class GroupStatus {
    ACTIVE,
    ARCHIVED
}

enum class ExpenseCategory(val label: String) {
    GROCERIES("Groceries"),
    RENT("Rent"),
    INTERNET_BILLS("Internet/Bills"),
    FOOD("Food"),
    TRAVEL("Travel"),
    PROJECT_MATERIALS("Project Materials"),
    EVENT("Event"),
    UTILITIES("Utilities"),
    OTHER("Other")
}

enum class SplitType(val label: String) {
    EQUAL("Equally"),
    CUSTOM("Exact Amounts"),
    PERCENTAGE("Percentages"),
    SHARES("Shares (Ratio)")
}

enum class ExpenseStatus {
    ACTIVE,
    CANCELLED
}

enum class PaymentStatus(val label: String) {
    WILL_PAY("Will Pay"),
    PAID_PENDING_CONFIRMATION("Waiting for Confirmation"),
    PAID_CONFIRMED("Confirmed Paid"),
    DISPUTED("Disputed"),
    REJECTED_RESOLVED("Rejected"),
    CANCELLED("Cancelled")
}

enum class NotificationType {
    NEW_EXPENSE,
    CONTRIBUTION_ASSIGNED,
    COMMITMENT_CHANGED,
    PAYMENT_CLAIMED,
    PAYMENT_CONFIRMED,
    PAYMENT_DISPUTED,
    DISPUTE_RESOLVED,
    NUDGE,
    MEMBER_JOINED
}

// -------------------------------------------------------------------------------------------------
// ROOM ENTITIES (Directly mirroring the Postgres Supabase tables)
// -------------------------------------------------------------------------------------------------

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val email: String = "",
    val phone: String? = null,
    val avatarKey: String = "avatar_1",
    val upiId: String? = null,
    val upiConsent: Boolean = false,
    val upiConsentAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "groups",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["ownerId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["ownerId"])]
)
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String? = null,
    val ownerId: String,
    val status: GroupStatus = GroupStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class GroupMemberEntity(
    val groupId: String,
    val userId: String,
    val role: MemberRole = MemberRole.MEMBER,
    val status: MemberStatus = MemberStatus.ACTIVE,
    val joinedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "group_invites",
    indices = [Index(value = ["code"], unique = true), Index(value = ["groupId"])],
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class GroupInviteEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val code: String, // 6 characters
    val createdBy: String,
    val maxUses: Int? = null,
    val uses: Int = 0,
    val status: String = "active",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["groupId"]), Index(value = ["paidBy"])]
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val title: String,
    val description: String? = null,
    val amountPaise: Long, // Integer paise
    val category: ExpenseCategory,
    val paidBy: String,
    val createdBy: String,
    val expenseDate: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val receiptUri: String? = null,
    val splitType: SplitType = SplitType.EQUAL,
    val status: ExpenseStatus = ExpenseStatus.ACTIVE,
    val cancelReason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "expense_shares",
    primaryKeys = ["expenseId", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class ExpenseShareEntity(
    val expenseId: String,
    val userId: String,
    val sharePaise: Long
)

@Entity(
    tableName = "contributions",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["expenseId"]), Index(value = ["userId"])]
)
data class ContributionEntity(
    @PrimaryKey val id: String,
    val expenseId: String,
    val userId: String,
    val sharePaise: Long,
    val committedPaise: Long = 0L,
    val status: PaymentStatus = PaymentStatus.WILL_PAY,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "payment_records",
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["contributionId"]),
        Index(value = ["fromUser"]),
        Index(value = ["toUser"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ContributionEntity::class,
            parentColumns = ["id"],
            childColumns = ["contributionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PaymentRecordEntity(
    @PrimaryKey val id: String,
    val contributionId: String,
    val fromUser: String,
    val toUser: String,
    val amountPaise: Long,
    val upiReference: String? = null,
    val note: String? = null,
    val status: PaymentStatus = PaymentStatus.PAID_PENDING_CONFIRMATION,
    val idempotencyKey: String,
    val claimedAt: Long = System.currentTimeMillis(),
    val respondedAt: Long? = null,
    val respondedBy: String? = null,
    val disputeReason: String? = null,
    val resolvedBy: String? = null,
    val lastNudgedAt: Long? = null
)

@Entity(
    tableName = "activity_logs",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["groupId"])]
)
data class ActivityLogEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val actorId: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val description: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "notifications",
    indices = [Index(value = ["userId"])]
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val groupId: String? = null,
    val type: NotificationType,
    val title: String,
    val body: String,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// -------------------------------------------------------------------------------------------------
// DOMAIN / UI MODELS
// -------------------------------------------------------------------------------------------------

data class GroupMemberItem(
    val user: ProfileEntity,
    val role: MemberRole,
    val status: MemberStatus,
    val joinedAt: Long
)

data class GroupDetail(
    val group: GroupEntity,
    val members: List<GroupMemberItem>,
    val activeInviteCode: String?
)

data class WhoOwesWhomItem(
    val expenseId: String,
    val expenseTitle: String,
    val debtorId: String,
    val debtorName: String,
    val creditorId: String,
    val creditorName: String,
    val amountPaise: Long,
    val status: PaymentStatus,
    val contributionId: String,
    val latestPaymentRecord: PaymentRecordEntity? = null
)

data class PairwiseNetBalance(
    val userAId: String,
    val userAName: String,
    val userBId: String,
    val userBName: String,
    val netOwedPaise: Long // Positive: A owes B, Negative: B owes A
)

data class GroupSummary(
    val totalExpensesPaise: Long,
    val myTotalPaidPaise: Long,
    val totalCollectedPaise: Long,
    val totalRemainingPaise: Long,
    val pendingConfirmationCount: Int,
    val disputedCount: Int
)

data class ExpenseParticipantDetail(
    val user: ProfileEntity,
    val sharePaise: Long,
    val committedPaise: Long,
    val status: PaymentStatus,
    val contributionId: String?
)

data class ExpenseWithShares(
    val expense: ExpenseEntity,
    val payer: ProfileEntity,
    val participants: List<ExpenseParticipantDetail>
)

