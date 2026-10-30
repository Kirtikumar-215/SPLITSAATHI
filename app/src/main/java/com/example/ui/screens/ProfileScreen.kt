package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ProfileEntity
import com.example.ui.components.AvatarView
import androidx.compose.material.icons.outlined.Cloud
import com.example.data.remote.auth.AuthState
import com.example.ui.components.DisclaimerBanner
import com.example.ui.components.SplitSaathiTopBar
import com.example.util.MoneyUtils

@Composable
fun ProfileScreen(
    currentProfile: ProfileEntity?,
    onSaveProfile: (displayName: String, phone: String?, upiId: String?, upiConsent: Boolean, avatarKey: String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit = {},
    authState: AuthState = AuthState.SignedOut,
    onOpenAuth: () -> Unit = {},
    onCloudSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    var displayName by remember(currentProfile) { mutableStateOf(currentProfile?.displayName ?: "") }
    var phoneNumber by remember(currentProfile) { mutableStateOf(currentProfile?.phone ?: "") }
    var upiId by remember(currentProfile) { mutableStateOf(currentProfile?.upiId ?: "") }
    var upiConsent by remember(currentProfile) { mutableStateOf(currentProfile?.upiConsent ?: false) }
    var selectedAvatar by remember(currentProfile) { mutableStateOf(currentProfile?.avatarKey ?: "avatar_1") }

    var showLegalDialogTitle by remember { mutableStateOf<String?>(null) }
    var showLegalDialogContent by remember { mutableStateOf<String?>(null) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }

    val upiError = remember(upiId) {
        if (upiId.isNotBlank() && !MoneyUtils.isValidUpiId(upiId)) {
            "Please enter a valid UPI ID (e.g. yourname@bank)"
        } else null
    }

    val phoneError = remember(phoneNumber) {
        if (phoneNumber.isNotBlank() && !MoneyUtils.isValidIndianPhone(phoneNumber)) {
            "Please enter a valid 10-digit mobile starting with 6-9"
        } else null
    }

    Scaffold(
        topBar = {
            SplitSaathiTopBar(
                title = stringResource(R.string.profile_title),
                currentUserName = currentProfile?.displayName
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Initials Avatar
            AvatarView(
                name = displayName.ifEmpty { "User" },
                size = 80.dp,
                avatarKey = selectedAvatar
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!currentProfile?.phone.isNullOrBlank()) {
                Text(
                    text = "+91 ${currentProfile?.phone}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cloud Account Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = when (authState) {
                        is AuthState.Authenticated -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_cloud_auth_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Cloud,
                                contentDescription = null,
                                tint = if (authState is AuthState.Authenticated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (authState is AuthState.Authenticated) "Cloud Account" else "Local Mode",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (authState is AuthState.Authenticated) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Connected",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (authState is AuthState.Authenticated) {
                        Text(
                            text = "Email: ${authState.email ?: "Google Account"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Cloud ID: ${authState.userId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onCloudSignOut,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_cloud_signout_btn")
                        ) {
                            Text("Sign Out of Cloud Account")
                        }
                    } else {
                        Text(
                            text = "Sign in to SplitSaathi cloud to sync groups, verify member transactions, and collaborate seamlessly across devices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onOpenAuth,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_cloud_signin_btn")
                        ) {
                            Text("Sign In or Register Cloud Account")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Form: Display Name
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Full Name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_display_name_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Form: Phone Number
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = {
                    if (it.length <= 10 && it.all { c -> c.isDigit() }) {
                        phoneNumber = it
                    }
                },
                label = { Text("Mobile Number") },
                prefix = { Text("+91 ") },
                singleLine = true,
                isError = phoneError != null,
                supportingText = {
                    if (phoneError != null) {
                        Text(phoneError, color = MaterialTheme.colorScheme.error)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_phone_input")
            )

            Spacer(modifier = Modifier.height(20.dp))

            // UPI Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Payment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.profile_upi_title),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it.trim() },
                        label = { Text("UPI ID (e.g. username@bank)") },
                        singleLine = true,
                        isError = upiError != null,
                        supportingText = {
                            if (upiError != null) {
                                Text(upiError, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text(stringResource(R.string.upi_format_note), fontSize = 11.sp)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_upi_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Explicit consent checkbox
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { upiConsent = !upiConsent }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = upiConsent,
                            onCheckedChange = { upiConsent = it },
                            modifier = Modifier.testTag("profile_upi_consent_checkbox")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.upi_consent_label),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Honest Verification Note
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Details are self-declared and not verified by SplitSaathi. Reinstalling the app may require you to set up your profile again.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DisclaimerBanner()

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (phoneError != null) {
                        Toast.makeText(context, phoneError, Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (upiId.isNotBlank() && upiError != null) {
                        Toast.makeText(context, upiError, Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onSaveProfile(displayName, phoneNumber.ifEmpty { null }, upiId.ifEmpty { null }, upiConsent, selectedAvatar)
                    Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("profile_save_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = stringResource(R.string.profile_save_btn),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Legal & Compliance Documents Section
            Text(
                text = "Privacy, Terms & Compliance",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    TextButton(
                        onClick = {
                            showLegalDialogTitle = "Privacy Policy"
                            showLegalDialogContent = "SplitSaathi values your privacy. We store only your self-declared name, optional phone, and optional UPI ID. Data is retained locally on your device and synchronized to your group ledger solely for mutual expense tracking. We do not sell your personal data or track your behavior across third-party apps."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Policy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Privacy Policy", modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    TextButton(
                        onClick = {
                            showLegalDialogTitle = "Terms of Service"
                            showLegalDialogContent = "SplitSaathi is an informal shared expense calculator and tracker. SplitSaathi is NOT a bank, payment processor, or digital wallet. All money transfers occur peer-to-peer via external UPI apps. SplitSaathi does not hold, transmit, or custody user funds."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Terms of Service", modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    TextButton(
                        onClick = {
                            showLegalDialogTitle = "Refund & Dispute Policy"
                            showLegalDialogContent = "Because SplitSaathi never processes or holds funds, all financial transactions are executed directly between users via external UPI applications. Refunds, reversals, or erroneous payments must be resolved directly between the sender and recipient or through your bank."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Refund & Dispute Policy", modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    TextButton(
                        onClick = {
                            showLegalDialogTitle = "Cookie & Storage Policy"
                            showLegalDialogContent = "SplitSaathi uses device-local storage (Room Database and SharedPreferences) strictly for operational session management and offline calculation. No third-party marketing or tracking cookies are utilized."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Cookie & Local Storage Notice", modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sign Out Button
            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_sign_out_btn")
            ) {
                Icon(Icons.AutoMirrored.Outlined.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.profile_sign_out_btn))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Data Deletion / Account Reset Button
            OutlinedButton(
                onClick = { showDeleteAccountConfirm = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_delete_account_btn")
            ) {
                Icon(Icons.Outlined.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete Account & Reset Data")
            }
        }
    }

    // Legal Policy Viewer Dialog
    if (showLegalDialogTitle != null && showLegalDialogContent != null) {
        AlertDialog(
            onDismissRequest = {
                showLegalDialogTitle = null
                showLegalDialogContent = null
            },
            title = { Text(showLegalDialogTitle!!) },
            text = {
                Text(
                    text = showLegalDialogContent!!,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLegalDialogTitle = null
                    showLegalDialogContent = null
                }) {
                    Text("Close")
                }
            }
        )
    }

    // Delete Account Confirmation Dialog
    if (showDeleteAccountConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirm = false },
            icon = {
                Icon(
                    Icons.Outlined.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Delete Account & Personal Data?") },
            text = {
                Text(
                    text = "This will erase your active session and personal details (Name, Phone number, UPI ID). To preserve mutual accounting integrity for your friends and roommates, existing recorded group expenses will be anonymized to 'Deactivated Member' rather than corrupting group balance records.\n\nAre you sure you wish to proceed?",
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountConfirm = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
