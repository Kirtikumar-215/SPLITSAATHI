package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.DisclaimerBanner
import com.example.util.MoneyUtils

@Composable
fun OnboardingScreen(
    onProfileCreated: (name: String, phone: String, upiId: String?, upiConsent: Boolean) -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }

    // Profile form state
    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    var upiConsent by remember { mutableStateOf(true) }

    var fullNameTouched by remember { mutableStateOf(false) }
    var phoneTouched by remember { mutableStateOf(false) }
    var upiTouched by remember { mutableStateOf(false) }

    val isNameValid = fullName.trim().length >= 2
    val isPhoneValid = MoneyUtils.isValidIndianPhone(phoneNumber)
    val isUpiValid = upiId.isBlank() || MoneyUtils.isValidUpiId(upiId)
    val canSubmit = isNameValid && isPhoneValid && isUpiValid

    val slides = listOf(
        Triple(
            stringResource(R.string.onboarding_title_1),
            stringResource(R.string.onboarding_desc_1),
            R.drawable.splitsaathi_hero_1793104083440
        ),
        Triple(
            stringResource(R.string.onboarding_title_2),
            stringResource(R.string.onboarding_desc_2),
            R.drawable.splitsaathi_icon_1793103787935
        )
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (currentStep < 2) {
            // Intro slides (Slide 0 & 1)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Skip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { currentStep = 2 },
                        modifier = Modifier.testTag("onboarding_skip_btn")
                    ) {
                        Text(
                            text = stringResource(R.string.btn_skip),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Slide Illustration & Content
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(slides[currentStep].third),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(20.dp))
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = slides[currentStep].first,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = slides[currentStep].second,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    DisclaimerBanner()
                }

                // Bottom Stepper
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        repeat(3) { index ->
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(if (index == currentStep) 10.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index == currentStep)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                    )
                            )
                        }
                    }

                    Button(
                        onClick = { currentStep++ },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("onboarding_action_btn"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = if (currentStep == 1) "Create My Profile" else stringResource(R.string.btn_next),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        } else {
            // STEP 3: Anonymous Onboarding Form (3 fields)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(R.drawable.splitsaathi_icon_1793103787935),
                        contentDescription = "SplitSaathi",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Welcome to SplitSaathi",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                        Text(
                            text = stringResource(R.string.tagline),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Set Up Your Profile",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "No password or email verification needed. Your friends in groups will see your name and can settle with your UPI ID.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            ),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Field 1: Full Name
                OutlinedTextField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        fullNameTouched = true
                    },
                    label = { Text("Full Name *") },
                    placeholder = { Text("e.g. Rahul Sharma") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    isError = fullNameTouched && !isNameValid,
                    supportingText = {
                        if (fullNameTouched && !isNameValid) {
                            Text("Please enter your name (min 2 letters)", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_name_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Field 2: Mobile Number
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        if (it.length <= 10 && it.all { char -> char.isDigit() }) {
                            phoneNumber = it
                            phoneTouched = true
                        }
                    },
                    label = { Text("Mobile Number *") },
                    placeholder = { Text("10-digit mobile (e.g. 9876543210)") },
                    prefix = { Text("+91 ", fontWeight = FontWeight.SemiBold) },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    isError = phoneTouched && !isPhoneValid,
                    supportingText = {
                        if (phoneTouched && !isPhoneValid) {
                            Text("Enter a valid 10-digit Indian number starting with 6-9", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Used to connect with group members", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_phone_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Field 3: UPI ID (Optional / Recommended)
                OutlinedTextField(
                    value = upiId,
                    onValueChange = {
                        upiId = it.trim()
                        upiTouched = true
                    },
                    label = { Text("UPI ID (Optional but recommended)") },
                    placeholder = { Text("e.g. rahul@okhdfcbank or 9876543210@upi") },
                    leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                    singleLine = true,
                    isError = upiTouched && upiId.isNotBlank() && !isUpiValid,
                    supportingText = {
                        if (upiTouched && upiId.isNotBlank() && !isUpiValid) {
                            Text("Invalid UPI ID format (e.g. name@bank)", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text(stringResource(R.string.upi_format_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_upi_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                // UPI Consent Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = upiConsent,
                        onCheckedChange = { upiConsent = it },
                        modifier = Modifier.testTag("onboarding_upi_consent_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.upi_consent_label),
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        fullNameTouched = true
                        phoneTouched = true
                        upiTouched = true
                        if (canSubmit) {
                            onProfileCreated(
                                fullName.trim(),
                                phoneNumber.trim(),
                                upiId.trim().ifEmpty { null },
                                upiConsent
                            )
                        } else {
                            Toast.makeText(context, "Please complete the required fields", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = canSubmit,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("onboarding_submit_btn")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Using SplitSaathi",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                DisclaimerBanner()
            }
        }
    }
}
