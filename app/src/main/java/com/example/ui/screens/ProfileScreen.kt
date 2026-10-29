package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.outlined.ExitToApp
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
import com.example.ui.components.DisclaimerBanner
import com.example.ui.components.SplitSaathiTopBar
import com.example.util.MoneyUtils

@Composable
fun ProfileScreen(
    currentProfile: ProfileEntity?,
    onSaveProfile: (displayName: String, phone: String?, upiId: String?, upiConsent: Boolean, avatarKey: String) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var displayName by remember(currentProfile) { mutableStateOf(currentProfile?.displayName ?: "") }
    var phoneNumber by remember(currentProfile) { mutableStateOf(currentProfile?.phone ?: "") }
    var upiId by remember(currentProfile) { mutableStateOf(currentProfile?.upiId ?: "") }
    var upiConsent by remember(currentProfile) { mutableStateOf(currentProfile?.upiConsent ?: false) }
    var selectedAvatar by remember(currentProfile) { mutableStateOf(currentProfile?.avatarKey ?: "avatar_1") }

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

            Spacer(modifier = Modifier.height(24.dp))

            DisclaimerBanner()

            Spacer(modifier = Modifier.height(24.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_sign_out_btn")
            ) {
                Icon(Icons.Outlined.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.profile_sign_out_btn))
            }
        }
    }
}
