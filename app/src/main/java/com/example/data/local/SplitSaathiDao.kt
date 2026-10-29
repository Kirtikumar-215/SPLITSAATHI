package com.example.data.local

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: String): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observeProfileById(id: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: ProfileEntity)
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups WHERE id = :groupId")
    suspend fun getGroupById(groupId: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE id = :groupId")
    fun observeGroupById(groupId: String): Flow<GroupEntity?>

    @Query("""
        SELECT g.* FROM groups g
        INNER JOIN group_members gm ON g.id = gm.groupId
        WHERE gm.userId = :userId AND gm.status = 'ACTIVE' AND g.status = 'ACTIVE'
        ORDER BY g.createdAt DESC
    """)
    fun observeGroupsForUser(userId: String): Flow<List<GroupEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)

    @Update
    suspend fun updateGroup(group: GroupEntity)

    // Members
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: GroupMemberEntity)

    @Query("SELECT * FROM group_members WHERE groupId = :groupId AND userId = :userId")
    suspend fun getMember(groupId: String, userId: String): GroupMemberEntity?

    @Query("SELECT * FROM group_members WHERE groupId = :groupId AND status = 'ACTIVE'")
    fun observeActiveMembers(groupId: String): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId AND status = 'ACTIVE'")
    suspend fun getActiveMembers(groupId: String): List<GroupMemberEntity>

    // Invites
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvite(invite: GroupInviteEntity)

    @Query("SELECT * FROM group_invites WHERE code = :code AND status = 'active' LIMIT 1")
    suspend fun getInviteByCode(code: String): GroupInviteEntity?

    @Query("SELECT * FROM group_invites WHERE groupId = :groupId AND status = 'active' ORDER BY createdAt DESC LIMIT 1")
    fun observeActiveInvite(groupId: String): Flow<GroupInviteEntity?>

    @Update
    suspend fun updateInvite(invite: GroupInviteEntity)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE groupId = :groupId AND status = 'ACTIVE' ORDER BY expenseDate DESC")
    fun observeExpensesForGroup(groupId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE status = 'ACTIVE' ORDER BY expenseDate DESC")
    fun observeAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :expenseId")
    suspend fun getExpenseById(expenseId: String): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    // Shares
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShares(shares: List<ExpenseShareEntity>)

    @Query("SELECT * FROM expense_shares WHERE expenseId = :expenseId")
    suspend fun getSharesForExpense(expenseId: String): List<ExpenseShareEntity>

    // Contributions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContributions(contributions: List<ContributionEntity>)

    @Update
    suspend fun updateContribution(contribution: ContributionEntity)

    @Query("SELECT * FROM contributions WHERE expenseId = :expenseId")
    suspend fun getContributionsForExpense(expenseId: String): List<ContributionEntity>

    @Query("SELECT * FROM contributions WHERE expenseId = :expenseId")
    fun observeContributionsForExpense(expenseId: String): Flow<List<ContributionEntity>>

    @Query("SELECT * FROM contributions WHERE id = :id")
    suspend fun getContributionById(id: String): ContributionEntity?

    @Query("""
        SELECT c.* FROM contributions c
        INNER JOIN expenses e ON c.expenseId = e.id
        WHERE e.groupId = :groupId AND e.status = 'ACTIVE'
    """)
    fun observeContributionsForGroup(groupId: String): Flow<List<ContributionEntity>>
}

@Dao
interface PaymentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPaymentRecord(record: PaymentRecordEntity)

    @Update
    suspend fun updatePaymentRecord(record: PaymentRecordEntity)

    @Query("SELECT * FROM payment_records WHERE contributionId = :contributionId ORDER BY claimedAt DESC")
    suspend fun getPaymentsForContribution(contributionId: String): List<PaymentRecordEntity>

    @Query("SELECT * FROM payment_records WHERE contributionId = :contributionId ORDER BY claimedAt DESC LIMIT 1")
    fun observeLatestPaymentForContribution(contributionId: String): Flow<PaymentRecordEntity?>

    @Query("SELECT * FROM payment_records WHERE id = :id")
    suspend fun getPaymentById(id: String): PaymentRecordEntity?

    @Query("""
        SELECT pr.* FROM payment_records pr
        INNER JOIN contributions c ON pr.contributionId = c.id
        INNER JOIN expenses e ON c.expenseId = e.id
        WHERE e.groupId = :groupId
        ORDER BY pr.claimedAt DESC
    """)
    fun observePaymentsForGroup(groupId: String): Flow<List<PaymentRecordEntity>>
}

@Dao
interface ActivityDao {
    @Insert
    suspend fun insertLog(log: ActivityLogEntity)

    @Query("SELECT * FROM activity_logs WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun observeLogsForGroup(groupId: String): Flow<List<ActivityLogEntity>>

    @Query("SELECT * FROM activity_logs ORDER BY createdAt DESC LIMIT 50")
    fun observeRecentLogs(): Flow<List<ActivityLogEntity>>
}

@Dao
interface NotificationDao {
    @Insert
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeNotifications(userId: String): Flow<List<NotificationEntity>>

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId")
    suspend fun markAllAsRead(userId: String)
}
