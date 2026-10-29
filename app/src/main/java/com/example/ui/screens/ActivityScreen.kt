package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.NotificationEntity
import com.example.data.model.NotificationType
import com.example.data.model.ProfileEntity
import com.example.ui.components.EmptyStateView
import com.example.ui.components.SplitSaathiTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityScreen(
    currentProfile: ProfileEntity?,
    notifications: List<NotificationEntity>,
    onNotificationClick: (String) -> Unit,
    onMarkAllAsRead: () -> Unit = {},
    onProfileClick: () -> Unit
) {
    val unreadCount = notifications.count { !it.isRead }

    Scaffold(
        topBar = {
            SplitSaathiTopBar(
                title = stringResource(R.string.nav_activity),
                currentUserName = currentProfile?.displayName,
                onProfileClick = onProfileClick,
                actions = {
                    if (unreadCount > 0) {
                        TextButton(onClick = onMarkAllAsRead) {
                            Text("Mark read", fontSize = 12.sp)
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (notifications.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Outlined.Notifications,
                    title = "All Caught Up!",
                    description = "When your friends add shared expenses or claim payments, you'll see instant updates here."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notifications, key = { it.id }) { notif ->
                        val (icon, tint) = when (notif.type) {
                            NotificationType.PAYMENT_CONFIRMED -> Pair(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary)
                            NotificationType.NEW_EXPENSE -> Pair(Icons.Outlined.ReceiptLong, MaterialTheme.colorScheme.secondary)
                            else -> Pair(Icons.Default.Notifications, MaterialTheme.colorScheme.primary)
                        }

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (notif.isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNotificationClick(notif.id) }
                                .testTag("notification_item_${notif.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = notif.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (notif.isRead) FontWeight.Medium else FontWeight.Bold
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = notif.body,
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(notif.createdAt)),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
