package com.example.ui.screens.auth

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onSkipOffline: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    val isLoading by authViewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by authViewModel.errorMessage.collectAsStateWithLifecycle()
    val successMessage by authViewModel.successMessage.collectAsStateWithLifecycle()

    LaunchedEffect(successMessage) {
        successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            authViewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign In", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("login_back_btn")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.splitsaathi_icon_1793103787935),
                contentDescription = "SplitSaathi Logo",
                modifier = Modifier.size(72.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Welcome to SplitSaathi",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Sign in to securely sync groups, record shared expenses, and verify UPI payments in the cloud.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
            )

            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
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
                    .testTag("login_email_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_password_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { showForgotPasswordDialog = true },
                    modifier = Modifier.testTag("login_forgot_password_btn")
                ) {
                    Text("Forgot Password?", fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    authViewModel.signInWithEmail(email, password, onSuccess = onLoginSuccess)
                },
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("login_submit_btn")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Sign In", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = "  OR  ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Google Sign In Button
            OutlinedButton(
                onClick = {
                    authViewModel.signInWithGoogle(context, onSuccess = onLoginSuccess)
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("login_google_btn")
            ) {
                Text("Sign In with Google", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Register Link
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Don't have an account?", style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    onClick = onNavigateToRegister,
                    modifier = Modifier.testTag("login_goto_register_btn")
                ) {
                    Text("Sign Up", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Continue Offline Button
            TextButton(
                onClick = onSkipOffline,
                modifier = Modifier.testTag("login_skip_offline_btn")
            ) {
                Text("Continue in Local Mode (Offline)", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }

    if (showForgotPasswordDialog) {
        ForgotPasswordDialog(
            authViewModel = authViewModel,
            onDismiss = { showForgotPasswordDialog = false }
        )
    }
}
