package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.GroupEntity
import com.example.data.repository.SplitSaathiRepository
import kotlinx.coroutines.launch

sealed class Screen {
    object Onboarding : Screen()
    object MainTabs : Screen()
    data class GroupDetail(val groupId: String) : Screen()
}

@Composable
fun MainScreen(
    repository: SplitSaathiRepository,
    initialDeepLinkCode: String? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val currentProfile by repository.currentProfile.collectAsStateWithLifecycle(initialValue = null)
    val userGroups by repository.observeUserGroups().collectAsStateWithLifecycle(initialValue = emptyList())
    val notifications by repository.observeNotifications().collectAsStateWithLifecycle(initialValue = emptyList())
    val currentUserId by repository.currentUserId.collectAsStateWithLifecycle()

    var currentScreen by remember(currentUserId) {
        mutableStateOf<Screen>(if (currentUserId.isEmpty()) Screen.Onboarding else Screen.MainTabs)
    }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Groups, 1: Activity, 2: Reports, 3: Profile

    // Active bottom sheet / dialog states
    var showAddExpenseForGroupId by remember { mutableStateOf<String?>(null) }
    var payUpiState by remember {
        mutableStateOf<PayUpiData?>(null)
    }

    // Auto-join deep link handler if code passed
    LaunchedEffect(initialDeepLinkCode) {
        if (!initialDeepLinkCode.isNullOrBlank()) {
            val res = repository.joinGroupByCode(initialDeepLinkCode)
            if (res.isSuccess) {
                Toast.makeText(context, "Joined group via invite link!", Toast.LENGTH_SHORT).show()
                currentScreen = Screen.GroupDetail(res.getOrThrow())
            }
        }
    }

    when (val screen = currentScreen) {
        is Screen.Onboarding -> {
            OnboardingScreen(
                onProfileCreated = { name, phone, upi, consent ->
                    coroutineScope.launch {
                        repository.createAccount(
                            name = name,
                            phone = phone,
                            upiId = upi,
                            upiConsent = consent
                        )
                        currentScreen = Screen.MainTabs
                    }
                }
            )
        }
        is Screen.GroupDetail -> {
            BackHandler {
                currentScreen = Screen.MainTabs
            }

            val groupDetail by repository.observeGroupDetails(screen.groupId).collectAsStateWithLifecycle(initialValue = null)
            val expenses by repository.observeExpenses(screen.groupId).collectAsStateWithLifecycle(initialValue = emptyList())
            val whoOwesWhom by repository.observeWhoOwesWhom(screen.groupId).collectAsStateWithLifecycle(initialValue = emptyList())
            val netBalances by repository.observeNetPairwiseBalances(screen.groupId).collectAsStateWithLifecycle(initialValue = emptyList())
            val groupSummary by repository.observeGroupSummary(screen.groupId).collectAsStateWithLifecycle(initialValue = null)
            val activityLogs by repository.observeRecentActivity(screen.groupId).collectAsStateWithLifecycle(initialValue = emptyList())

            GroupDetailScreen(
                groupId = screen.groupId,
                groupDetail = groupDetail,
                expenses = expenses,
                whoOwesWhom = whoOwesWhom,
                netBalances = netBalances,
                groupSummary = groupSummary,
                activityLogs = activityLogs,
                currentUserId = currentUserId,
                onBack = { currentScreen = Screen.MainTabs },
                onAddExpenseClick = { showAddExpenseForGroupId = screen.groupId },
                onPayUpiClick = { payeeId, payeeName, amount, contribId ->
                    coroutineScope.launch {
                        val payeeUpiResult = repository.getPayeeUpi(payeeId)
                        val payeeUpi = payeeUpiResult.getOrNull()
                        payUpiState = PayUpiData(
                            payeeName = payeeName,
                            payeeUpi = payeeUpi,
                            amountPaise = amount,
                            contributionId = contribId
                        )
                    }
                },
                onConfirmPayment = { payId ->
                    coroutineScope.launch {
                        val res = repository.confirmPayment(payId)
                        if (res.isSuccess) {
                            Toast.makeText(context, "Payment confirmed successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onDisputePayment = { payId, reason ->
                    coroutineScope.launch {
                        val res = repository.disputePayment(payId, reason)
                        if (res.isSuccess) {
                            Toast.makeText(context, "Payment marked as disputed", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onResolveDispute = { payId, confirm ->
                    coroutineScope.launch {
                        val res = repository.resolveDispute(payId, confirm)
                        if (res.isSuccess) {
                            val action = if (confirm) "confirmed" else "rejected"
                            Toast.makeText(context, "Dispute resolved: payment $action", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onNudgePayment = { payId ->
                    coroutineScope.launch {
                        val res = repository.nudgePayment(payId)
                        if (res.isSuccess) {
                            Toast.makeText(context, "Nudge sent to recipient! 👋", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onCancelExpense = { expId, reason ->
                    coroutineScope.launch {
                        val res = repository.cancelExpense(expId, reason)
                        if (res.isSuccess) {
                            Toast.makeText(context, "Expense cancelled", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onSetCommitment = { contribId, amt ->
                    coroutineScope.launch {
                        repository.setCommitment(contribId, amt)
                    }
                },
                onUpdateRole = { targetUid, newRole ->
                    coroutineScope.launch {
                        repository.updateMemberRole(screen.groupId, targetUid, newRole)
                    }
                },
                onLeaveGroup = {
                    coroutineScope.launch {
                        val res = repository.leaveGroup(screen.groupId)
                        if (res.isSuccess) {
                            Toast.makeText(context, "Left group", Toast.LENGTH_SHORT).show()
                            currentScreen = Screen.MainTabs
                        } else {
                            Toast.makeText(context, res.exceptionOrNull()?.message ?: "Cannot leave group", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )

            // Add Expense Dialog
            if (showAddExpenseForGroupId != null && groupDetail != null) {
                AddExpenseDialog(
                    members = groupDetail!!.members,
                    currentUserId = currentUserId,
                    onDismiss = { showAddExpenseForGroupId = null },
                    onSaveExpense = { title, amt, cat, payer, participants, split, customShares, notes, receiptUri ->
                        coroutineScope.launch {
                            val res = repository.createExpense(
                                groupId = screen.groupId,
                                title = title,
                                amountPaise = amt,
                                category = cat,
                                paidBy = payer,
                                participantUserIds = participants,
                                splitType = split,
                                customShares = customShares,
                                notes = notes,
                                receiptUri = receiptUri
                            )
                            if (res.isSuccess) {
                                showAddExpenseForGroupId = null
                            } else {
                                Toast.makeText(context, res.exceptionOrNull()?.message ?: "Failed", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
        }
        is Screen.MainTabs -> {
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.testTag("main_bottom_nav")
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == 0) Icons.Default.Groups else Icons.Outlined.Groups,
                                    contentDescription = stringResource(R.string.nav_groups)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_groups)) }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        val unreadCount = notifications.count { !it.isRead }
                                        if (unreadCount > 0) {
                                            Badge { Text("$unreadCount") }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (selectedTab == 1) Icons.Default.Notifications else Icons.Outlined.Notifications,
                                        contentDescription = stringResource(R.string.nav_activity)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.nav_activity)) }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == 2) Icons.Default.BarChart else Icons.Outlined.BarChart,
                                    contentDescription = stringResource(R.string.nav_reports)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_reports)) }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 3,
                            onClick = { selectedTab = 3 },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == 3) Icons.Default.Person else Icons.Outlined.Person,
                                    contentDescription = stringResource(R.string.nav_profile)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_profile)) }
                        )
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    when (selectedTab) {
                        0 -> GroupsScreen(
                            currentProfile = currentProfile,
                            groups = userGroups,
                            onGroupClick = { gid -> currentScreen = Screen.GroupDetail(gid) },
                            onCreateGroup = { name, desc ->
                                coroutineScope.launch {
                                    repository.createGroup(name, desc)
                                }
                            },
                            onJoinGroup = { code ->
                                coroutineScope.launch {
                                    val res = repository.joinGroupByCode(code)
                                    if (res.isSuccess) {
                                        Toast.makeText(context, "Joined group!", Toast.LENGTH_SHORT).show()
                                        currentScreen = Screen.GroupDetail(res.getOrThrow())
                                    } else {
                                        Toast.makeText(context, res.exceptionOrNull()?.message ?: "Failed", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onProfileClick = { selectedTab = 3 }
                        )
                        1 -> ActivityScreen(
                            currentProfile = currentProfile,
                            notifications = notifications,
                            onNotificationClick = { nid ->
                                coroutineScope.launch { repository.markNotificationAsRead(nid) }
                            },
                            onMarkAllAsRead = {
                                coroutineScope.launch { repository.markAllNotificationsAsRead() }
                            },
                            onProfileClick = { selectedTab = 3 }
                        )
                        2 -> {
                            val allExpenses by repository.observeAllExpenses().collectAsStateWithLifecycle(initialValue = emptyList())
                            ReportsScreen(
                                currentProfile = currentProfile,
                                groups = userGroups,
                                expenses = allExpenses,
                                onProfileClick = { selectedTab = 3 }
                            )
                        }
                        3 -> ProfileScreen(
                            currentProfile = currentProfile,
                            onSaveProfile = { name, phone, upi, consent, avatar ->
                                coroutineScope.launch {
                                    repository.updateProfile(name, phone, upi, consent, avatar)
                                }
                            },
                            onSignOut = {
                                coroutineScope.launch {
                                    repository.signOut()
                                    currentScreen = Screen.Onboarding
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal Pay UPI Bottom Sheet
    if (payUpiState != null) {
        PayUpiBottomSheet(
            payeeName = payUpiState!!.payeeName,
            payeeUpi = payUpiState!!.payeeUpi,
            amountPaise = payUpiState!!.amountPaise,
            contributionId = payUpiState!!.contributionId,
            onDismiss = { payUpiState = null },
            onPaymentClaimed = { contribId, amt, ref ->
                coroutineScope.launch {
                    val res = repository.claimPayment(contribId, amt, ref, null)
                    if (res.isSuccess) {
                        Toast.makeText(context, "Payment claimed! Recipient notified to confirm.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, res.exceptionOrNull()?.message ?: "Error", Toast.LENGTH_LONG).show()
                    }
                    payUpiState = null
                }
            }
        )
    }
}

private data class PayUpiData(
    val payeeName: String,
    val payeeUpi: String?,
    val amountPaise: Long,
    val contributionId: String
)
