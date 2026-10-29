package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.*
import com.example.ui.components.AvatarView
import com.example.ui.components.RoleBadge
import com.example.ui.components.StatusChip
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    groupDetail: GroupDetail?,
    expenses: List<ExpenseEntity>,
    whoOwesWhom: List<WhoOwesWhomItem>,
    netBalances: List<PairwiseNetBalance> = emptyList(),
    groupSummary: GroupSummary? = null,
    activityLogs: List<ActivityLogEntity>,
    currentUserId: String,
    onBack: () -> Unit,
    onAddExpenseClick: () -> Unit,
    onPayUpiClick: (payeeId: String, payeeName: String, amountPaise: Long, contributionId: String) -> Unit,
    onConfirmPayment: (paymentId: String) -> Unit,
    onDisputePayment: (paymentId: String, reason: String) -> Unit,
    onResolveDispute: (paymentId: String, confirm: Boolean) -> Unit,
    onNudgePayment: (paymentId: String) -> Unit,
    onCancelExpense: (expenseId: String, reason: String) -> Unit,
    onSetCommitment: (contributionId: String, amountPaise: Long) -> Unit,
    onUpdateRole: (targetUserId: String, newRole: MemberRole) -> Unit,
    onLeaveGroup: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var disputePaymentTargetId by remember { mutableStateOf<String?>(null) }
    var disputeReason by remember { mutableStateOf("") }
    var cancelExpenseTarget by remember { mutableStateOf<ExpenseEntity?>(null) }
    var cancelReason by remember { mutableStateOf("") }

    var selectedExpenseForDetail by remember { mutableStateOf<ExpenseEntity?>(null) }
    var editCommitmentTarget by remember { mutableStateOf<WhoOwesWhomItem?>(null) }
    var commitmentInput by remember { mutableStateOf("") }

    val group = groupDetail?.group
    val isOwner = group?.ownerId == currentUserId
    val myMemberItem = groupDetail?.members?.find { it.user.id == currentUserId }
    val isAdminOrOwner = isOwner || myMemberItem?.role == MemberRole.ADMIN

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = group?.name ?: "Group Details",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                        if (groupDetail?.activeInviteCode != null) {
                            Text(
                                text = "Code: ${groupDetail.activeInviteCode}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("group_detail_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (groupDetail?.activeInviteCode != null) {
                        IconButton(
                            onClick = {
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        context.getString(
                                            R.string.group_share_invite_text,
                                            group?.name ?: "",
                                            groupDetail.activeInviteCode
                                        )
                                    )
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Share Group Invite")
                                context.startActivity(shareIntent)
                            },
                            modifier = Modifier.testTag("group_share_invite_btn")
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = "Share Invite")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = onAddExpenseClick,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add Expense") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("group_add_expense_fab")
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Dashboard Summary Card (Spec Section 6)
            if (groupSummary != null && groupSummary.totalExpensesPaise > 0) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("group_summary_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Total Group Expenses",
                                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onPrimaryContainer)
                                )
                                Text(
                                    text = MoneyUtils.formatPaiseToRupees(groupSummary.totalExpensesPaise),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (groupSummary.pendingConfirmationCount > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = "${groupSummary.pendingConfirmationCount} Pending",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                if (groupSummary.disputedCount > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Text(
                                            text = "${groupSummary.disputedCount} Disputed",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Collection Progress Bar (Decision 5: Total, collected, remaining to be collected)
                        val progress = if (groupSummary.totalExpensesPaise > 0) {
                            (groupSummary.totalCollectedPaise.toFloat() / groupSummary.totalExpensesPaise.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Confirmed: ${MoneyUtils.formatPaiseToRupees(groupSummary.totalCollectedPaise)}",
                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 11.sp)
                            )
                            Text(
                                text = "Remaining: ${MoneyUtils.formatPaiseToRupees(groupSummary.totalRemainingPaise)}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Expenses (${expenses.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Who Owes Whom") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Members (${groupDetail?.members?.size ?: 0})") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Activity") }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> ExpensesTab(
                    expenses = expenses,
                    onExpenseClick = { selectedExpenseForDetail = it },
                    onAddExpenseClick = onAddExpenseClick
                )
                1 -> WhoOwesWhomTab(
                    items = whoOwesWhom,
                    netBalances = netBalances,
                    currentUserId = currentUserId,
                    isAdminOrOwner = isAdminOrOwner,
                    onPayClick = { item ->
                        onPayUpiClick(item.creditorId, item.creditorName, item.amountPaise, item.contributionId)
                    },
                    onConfirmClick = { item ->
                        item.latestPaymentRecord?.id?.let { onConfirmPayment(it) }
                    },
                    onDisputeClick = { item ->
                        disputePaymentTargetId = item.latestPaymentRecord?.id
                    },
                    onResolveDispute = { item, confirm ->
                        item.latestPaymentRecord?.id?.let { onResolveDispute(it, confirm) }
                    },
                    onNudgeClick = { item ->
                        item.latestPaymentRecord?.id?.let { onNudgePayment(it) }
                    },
                    onEditCommitment = { item ->
                        editCommitmentTarget = item
                        commitmentInput = MoneyUtils.formatPaiseForUpi(item.amountPaise)
                    }
                )
                2 -> MembersTab(
                    members = groupDetail?.members ?: emptyList(),
                    currentUserId = currentUserId,
                    isOwner = isOwner,
                    onPromoteAdmin = { uid -> onUpdateRole(uid, MemberRole.ADMIN) },
                    onTransferOwner = { uid -> onUpdateRole(uid, MemberRole.OWNER) },
                    onLeave = onLeaveGroup
                )
                3 -> ActivityTab(logs = activityLogs)
            }
        }
    }

    // Expense Detail Sheet
    if (selectedExpenseForDetail != null) {
        val exp = selectedExpenseForDetail!!
        AlertDialog(
            onDismissRequest = { selectedExpenseForDetail = null },
            title = {
                Text(text = exp.title, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Total: ${MoneyUtils.formatPaiseToRupees(exp.amountPaise)} (${exp.splitType.name} Split)",
                        style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    )
                    Text(text = "Category: ${exp.category.label}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Date: ${SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date(exp.expenseDate))}",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    if (!exp.notes.isNullOrBlank()) {
                        Text(text = "Notes: ${exp.notes}", style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isAdminOrOwner || exp.createdBy == currentUserId) {
                        OutlinedButton(
                            onClick = {
                                cancelExpenseTarget = exp
                                selectedExpenseForDetail = null
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().testTag("cancel_expense_btn")
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancel / Soft-Delete Expense")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedExpenseForDetail = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Cancel Expense Dialog with Mandatory Reason
    if (cancelExpenseTarget != null) {
        AlertDialog(
            onDismissRequest = { cancelExpenseTarget = null },
            title = { Text("Cancel Expense: ${cancelExpenseTarget?.title}") },
            text = {
                Column {
                    Text(
                        text = "Enter a mandatory reason for cancelling this expense. All open obligations will be cancelled (confirmed payments are preserved):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        label = { Text("Reason (e.g. Duplicate entry / Cancelled plan)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("cancel_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (cancelReason.isNotBlank()) {
                            onCancelExpense(cancelExpenseTarget!!.id, cancelReason)
                            cancelExpenseTarget = null
                            cancelReason = ""
                            Toast.makeText(context, "Expense cancelled", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_cancel_expense_btn")
                ) {
                    Text("Cancel Expense")
                }
            },
            dismissButton = {
                TextButton(onClick = { cancelExpenseTarget = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Edit Commitment Dialog
    if (editCommitmentTarget != null) {
        val target = editCommitmentTarget!!
        AlertDialog(
            onDismissRequest = { editCommitmentTarget = null },
            title = { Text("Set Commitment") },
            text = {
                Column {
                    Text(
                        text = "Promise an amount to pay for '${target.expenseTitle}' (Assigned share: ${MoneyUtils.formatPaiseToRupees(target.amountPaise)}):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = commitmentInput,
                        onValueChange = { commitmentInput = it },
                        label = { Text("Committed Amount in ₹") },
                        prefix = { Text("₹ ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("commitment_amount_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val paise = MoneyUtils.parseRupeesToPaise(commitmentInput) ?: 0L
                        if (paise in 0..target.amountPaise) {
                            onSetCommitment(target.contributionId, paise)
                            editCommitmentTarget = null
                            Toast.makeText(context, "Commitment updated", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Commitment cannot exceed assigned share", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("save_commitment_btn")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editCommitmentTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dispute Dialog
    if (disputePaymentTargetId != null) {
        AlertDialog(
            onDismissRequest = { disputePaymentTargetId = null },
            title = { Text("Dispute Payment Claim") },
            text = {
                Column {
                    Text(
                        text = "Specify why you are disputing this claim (mandatory):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = disputeReason,
                        onValueChange = { disputeReason = it },
                        label = { Text("Reason (e.g. Haven't received amount on UPI)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("dispute_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (disputeReason.isNotBlank()) {
                            onDisputePayment(disputePaymentTargetId!!, disputeReason)
                            disputePaymentTargetId = null
                            disputeReason = ""
                            Toast.makeText(context, "Payment marked as disputed", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("dispute_confirm_btn")
                ) {
                    Text("Dispute")
                }
            },
            dismissButton = {
                TextButton(onClick = { disputePaymentTargetId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ExpensesTab(
    expenses: List<ExpenseEntity>,
    onExpenseClick: (ExpenseEntity) -> Unit,
    onAddExpenseClick: () -> Unit
) {
    if (expenses.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "No Expenses Logged Yet", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Tap 'Add Expense' below to split dinner, groceries, or bills.", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(expenses, key = { it.id }) { exp ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onExpenseClick(exp) }.testTag("expense_item_${exp.id}")
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = exp.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${exp.category.label} • ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(exp.expenseDate))}",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = MoneyUtils.formatPaiseToRupees(exp.amountPaise),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = "${exp.splitType.name} Split",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WhoOwesWhomTab(
    items: List<WhoOwesWhomItem>,
    netBalances: List<PairwiseNetBalance>,
    currentUserId: String,
    isAdminOrOwner: Boolean,
    onPayClick: (WhoOwesWhomItem) -> Unit,
    onConfirmClick: (WhoOwesWhomItem) -> Unit,
    onDisputeClick: (WhoOwesWhomItem) -> Unit,
    onResolveDispute: (WhoOwesWhomItem, Boolean) -> Unit,
    onNudgeClick: (WhoOwesWhomItem) -> Unit,
    onEditCommitment: (WhoOwesWhomItem) -> Unit
) {
    var showNetView by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toggle: Pairwise (Decision 2) vs Optional Net View
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (showNetView) "Net Balance View (Read-Only)" else "Per-Expense Obligations",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Net View",
                    style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = showNetView,
                    onCheckedChange = { showNetView = it },
                    modifier = Modifier.testTag("net_view_toggle")
                )
            }
        }

        if (showNetView) {
            // Net View (Decision 2: Sum of A->B minus B->A as read-only information only)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "ℹ️ Informational net view only. SplitSaathi does NOT simplify or change underlying expense records.",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    modifier = Modifier.padding(8.dp)
                )
            }

            if (netBalances.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("All net balances settled! 🎉", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(netBalances) { net ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AvatarView(name = net.userAName, size = 32.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (net.userAId == currentUserId) "You owe" else "${net.userAName} owes",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    AvatarView(name = net.userBName, size = 32.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (net.userBId == currentUserId) "You" else net.userBName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }

                                Text(
                                    text = MoneyUtils.formatPaiseToRupees(net.netOwedPaise),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Per-Expense Obligations (V1 Core Principle)
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("All settled up! No outstanding obligations 🎉", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.contributionId }) { item ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth().testTag("obligation_card_${item.contributionId}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = item.expenseTitle, style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                                    StatusChip(status = item.status)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    AvatarView(name = item.debtorName, size = 32.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = if (item.debtorId == currentUserId) "You" else item.debtorName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    AvatarView(name = item.creditorName, size = 32.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = if (item.creditorId == currentUserId) "You" else item.creditorName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))

                                    Spacer(modifier = Modifier.weight(1f))

                                    Text(
                                        text = MoneyUtils.formatPaiseToRupees(item.amountPaise),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                if (item.debtorId == currentUserId) {
                                    // Debtor actions
                                    when (item.status) {
                                        PaymentStatus.WILL_PAY, PaymentStatus.DISPUTED -> {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = { onEditCommitment(item) },
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.weight(1f).testTag("commit_btn_${item.contributionId}")
                                                ) {
                                                    Text("Commit Amount", fontSize = 12.sp)
                                                }
                                                Button(
                                                    onClick = { onPayClick(item) },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                    modifier = Modifier.weight(1f).testTag("pay_upi_btn_${item.contributionId}")
                                                ) {
                                                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Pay UPI", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                        PaymentStatus.PAID_PENDING_CONFIRMATION -> {
                                            OutlinedButton(
                                                onClick = { onNudgeClick(item) },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth().testTag("nudge_btn_${item.contributionId}")
                                            ) {
                                                Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Nudge Recipient to Confirm (max 1/24h)")
                                            }
                                        }
                                        else -> {}
                                    }
                                } else if (item.creditorId == currentUserId || isAdminOrOwner) {
                                    // Creditor or Admin actions
                                    if (item.status == PaymentStatus.PAID_PENDING_CONFIRMATION) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = { onDisputeClick(item) },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                modifier = Modifier.weight(1f).testTag("dispute_btn_${item.contributionId}")
                                            ) {
                                                Text("Dispute")
                                            }
                                            Button(
                                                onClick = { onConfirmClick(item) },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                modifier = Modifier.weight(1f).testTag("confirm_btn_${item.contributionId}")
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Confirm")
                                            }
                                        }
                                    } else if (item.status == PaymentStatus.DISPUTED && isAdminOrOwner) {
                                        // Admin resolution flow (Decision 9)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = { onResolveDispute(item, false) },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Admin: Reject Claim", fontSize = 11.sp)
                                            }
                                            Button(
                                                onClick = { onResolveDispute(item, true) },
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Admin: Force Confirm", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MembersTab(
    members: List<GroupMemberItem>,
    currentUserId: String,
    isOwner: Boolean,
    onPromoteAdmin: (String) -> Unit,
    onTransferOwner: (String) -> Unit,
    onLeave: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(members, key = { it.user.id }) { item ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().testTag("member_row_${item.user.id}")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarView(name = item.user.displayName, size = 40.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (item.user.id == currentUserId) "${item.user.displayName} (You)" else item.user.displayName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = item.user.email,
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                    RoleBadge(role = item.role)

                    if (isOwner && item.user.id != currentUserId) {
                        var showRoleMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showRoleMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Role Actions")
                        }
                        DropdownMenu(expanded = showRoleMenu, onDismissRequest = { showRoleMenu = false }) {
                            if (item.role != MemberRole.ADMIN) {
                                DropdownMenuItem(
                                    text = { Text("Promote to Admin") },
                                    onClick = {
                                        onPromoteAdmin(item.user.id)
                                        showRoleMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Transfer Ownership") },
                                onClick = {
                                    onTransferOwner(item.user.id)
                                    showRoleMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onLeave,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("leave_group_btn")
            ) {
                Text(stringResource(R.string.group_leave_btn))
            }
        }
    }
}

@Composable
private fun ActivityTab(logs: List<ActivityLogEntity>) {
    if (logs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No activity recorded yet", style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs, key = { it.id }) { log ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().testTag("activity_log_${log.id}")
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = log.description, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(log.createdAt)),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                            )
                        }
                    }
                }
            }
        }
    }
}
