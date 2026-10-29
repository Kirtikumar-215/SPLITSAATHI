package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.QrCode
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
import com.example.ui.components.DisclaimerBanner
import com.example.ui.components.QrCodeView
import com.example.util.MoneyUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayUpiBottomSheet(
    payeeName: String,
    payeeUpi: String?,
    amountPaise: Long,
    contributionId: String,
    onDismiss: () -> Unit,
    onPaymentClaimed: (contributionId: String, amountPaise: Long, upiRef: String?) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: App Intent, 1: QR Code
    var hasLaunchedIntent by remember { mutableStateOf(false) }
    var upiReferenceInput by remember { mutableStateOf("") }

    val upiUri = remember(payeeUpi, payeeName, amountPaise) {
        if (!payeeUpi.isNullOrBlank()) {
            MoneyUtils.buildUpiUri(
                payeeUpi = payeeUpi,
                payeeName = payeeName,
                amountPaise = amountPaise,
                note = "SplitSaathi Settlement"
            )
        } else {
            ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Settle with UPI",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("pay_upi_close_btn")) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Amount Display
            Text(
                text = MoneyUtils.formatPaiseToRupees(amountPaise),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 36.sp
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Paying to $payeeName",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            if (!payeeUpi.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = payeeUpi,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy UPI",
                            modifier = Modifier
                                .size(14.dp)
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("UPI ID", payeeUpi))
                                    Toast.makeText(context, "UPI ID copied: $payeeUpi", Toast.LENGTH_SHORT).show()
                                }
                        )
                    }
                }
            } else {
                Text(
                    text = "Recipient hasn't registered a UPI ID yet. Settle offline or ask them to add it.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Payment Mode Selector: UPI App vs QR Code
            if (!payeeUpi.isNullOrBlank()) {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pay via App")
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan QR")
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (selectedTab == 1 && !payeeUpi.isNullOrBlank()) {
                // QR Code Display
                QrCodeView(
                    data = upiUri,
                    sizeDp = 190,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Scan with GPay, PhonePe, Paytm, or any Indian UPI app",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Primary Intent Button / Confirmation Flow
            if (!hasLaunchedIntent && selectedTab == 0) {
                Button(
                    onClick = {
                        if (!payeeUpi.isNullOrBlank()) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                            try {
                                context.startActivity(intent)
                                hasLaunchedIntent = true
                            } catch (e: Exception) {
                                Toast.makeText(context, "No UPI app installed. Use QR code or copy UPI ID.", Toast.LENGTH_LONG).show()
                                hasLaunchedIntent = true
                            }
                        } else {
                            hasLaunchedIntent = true
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("launch_upi_intent_btn")
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pay via UPI App")
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Fallback copy buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!payeeUpi.isNullOrBlank()) {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("UPI ID", payeeUpi))
                                Toast.makeText(context, "UPI ID copied: $payeeUpi", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy UPI", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val amountStr = MoneyUtils.formatPaiseForUpi(amountPaise)
                            cm.setPrimaryClip(ClipData.newPlainText("Amount", amountStr))
                            Toast.makeText(context, "Amount copied: ₹$amountStr", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy Amount", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // "I Have Paid" Claim Flow Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Mark as Paid & Notify Recipient",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.upi_intent_note),
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = upiReferenceInput,
                        onValueChange = { upiReferenceInput = it },
                        label = { Text("UPI Ref / UTR number (Optional)") },
                        placeholder = { Text("e.g. 334455667788") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("upi_reference_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            onPaymentClaimed(contributionId, amountPaise, upiReferenceInput.ifBlank { null })
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirm_claimed_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("I Have Paid (Send Claim)")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            DisclaimerBanner()
        }
    }
}
