package com.example.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ForgotPasswordDialog(
    authViewModel: AuthViewModel,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    val isLoading by authViewModel.isLoading.collectAsStateWithLifecycle()
    val isResetRequested by authViewModel.isPasswordResetRequested.collectAsStateWithLifecycle()
    val errorMessage by authViewModel.errorMessage.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isResetRequested) "Check Your Email" else "Reset Password",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isResetRequested) {
                    Text(
                        text = "We have sent password reset instructions to $email. Please follow the link in your email to reset your password.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "Enter your registered email address and we will send you a link to reset your password.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_password_email_input"),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            if (isResetRequested) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("forgot_password_done_btn")
                ) {
                    Text("Done")
                }
            } else {
                Button(
                    onClick = {
                        authViewModel.sendPasswordReset(email)
                    },
                    enabled = !isLoading && email.isNotBlank() && email.contains("@"),
                    modifier = Modifier.testTag("forgot_password_submit_btn")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Send Link")
                    }
                }
            }
        },
        dismissButton = {
            if (!isResetRequested) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
