package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Converters {
    @TypeConverter fun fromMemberRole(v: MemberRole): String = v.name
    @TypeConverter fun toMemberRole(v: String): MemberRole = runCatching { MemberRole.valueOf(v) }.getOrDefault(MemberRole.MEMBER)

    @TypeConverter fun fromMemberStatus(v: MemberStatus): String = v.name
    @TypeConverter fun toMemberStatus(v: String): MemberStatus = runCatching { MemberStatus.valueOf(v) }.getOrDefault(MemberStatus.ACTIVE)

    @TypeConverter fun fromGroupStatus(v: GroupStatus): String = v.name
    @TypeConverter fun toGroupStatus(v: String): GroupStatus = runCatching { GroupStatus.valueOf(v) }.getOrDefault(GroupStatus.ACTIVE)

    @TypeConverter fun fromExpenseCategory(v: ExpenseCategory): String = v.name
    @TypeConverter fun toExpenseCategory(v: String): ExpenseCategory = runCatching { ExpenseCategory.valueOf(v) }.getOrDefault(ExpenseCategory.OTHER)

    @TypeConverter fun fromSplitType(v: SplitType): String = v.name
    @TypeConverter fun toSplitType(v: String): SplitType = runCatching { SplitType.valueOf(v) }.getOrDefault(SplitType.EQUAL)

    @TypeConverter fun fromExpenseStatus(v: ExpenseStatus): String = v.name
    @TypeConverter fun toExpenseStatus(v: String): ExpenseStatus = runCatching { ExpenseStatus.valueOf(v) }.getOrDefault(ExpenseStatus.ACTIVE)

    @TypeConverter fun fromPaymentStatus(v: PaymentStatus): String = v.name
    @TypeConverter fun toPaymentStatus(v: String): PaymentStatus = runCatching { PaymentStatus.valueOf(v) }.getOrDefault(PaymentStatus.WILL_PAY)

    @TypeConverter fun fromNotificationType(v: NotificationType): String = v.name
    @TypeConverter fun toNotificationType(v: String): NotificationType = runCatching { NotificationType.valueOf(v) }.getOrDefault(NotificationType.NEW_EXPENSE)
}

@Database(
    entities = [
        ProfileEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        GroupInviteEntity::class,
        ExpenseEntity::class,
        ExpenseShareEntity::class,
        ContributionEntity::class,
        PaymentRecordEntity::class,
        ActivityLogEntity::class,
        NotificationEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class SplitSaathiDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun groupDao(): GroupDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun paymentDao(): PaymentDao
    abstract fun activityDao(): ActivityDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: SplitSaathiDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Future migration path: preserves existing financial & user records
            }
        }

        fun getInstance(context: Context): SplitSaathiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SplitSaathiDatabase::class.java,
                    "splitsaathi.db"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
