package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ExpenseCategory
import com.example.data.model.GroupMemberItem
import com.example.data.model.SplitType
import com.example.ui.components.AvatarView
import com.example.util.MoneyUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseDialog(
    members: List<GroupMemberItem>,
    currentUserId: String,
    onDismiss: () -> Unit,
    onSaveExpense: (
        title: String,
        amountPaise: Long,
        category: ExpenseCategory,
        paidBy: String,
        participantIds: List<String>,
        splitType: SplitType,
        customShares: Map<String, Long>,
        notes: String?,
        receiptUri: String?
    ) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ExpenseCategory.FOOD) }
    var paidByUserId by remember { mutableStateOf(currentUserId) }
    var splitType by remember { mutableStateOf(SplitType.EQUAL) }
    var notes by remember { mutableStateOf("") }
    var selectedReceiptUri by remember { mutableStateOf<Uri?>(null) }

    // Android Photo Picker launcher (zero storage permissions required)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        selectedReceiptUri = uri
    }

    // Selected participants default to all active members
    var selectedParticipants by remember {
        mutableStateOf(members.map { it.user.id }.toSet())
    }

    // Inputs for different split modes
    val exactShareInputs = remember { mutableStateMapOf<String, String>() }
    val percentageInputs = remember { mutableStateMapOf<String, String>() }
    val shareWeightInputs = remember { mutableStateMapOf<String, String>() }

    // Initialize defaults for participants
    LaunchedEffect(selectedParticipants) {
        selectedParticipants.forEach { uid ->
            if (!percentageInputs.containsKey(uid) && selectedParticipants.isNotEmpty()) {
                val defaultPct = 100.0 / selectedParticipants.size
                percentageInputs[uid] = String.format(java.util.Locale.US, "%.1f", defaultPct)
            }
            if (!shareWeightInputs.containsKey(uid)) {
                shareWeightInputs[uid] = "1"
            }
        }
    }

    val amountPaise = remember(amountInput) {
        MoneyUtils.parseRupeesToPaise(amountInput) ?: 0L
    }

    // Computed shares based on current mode
    val computedShares = remember(splitType, amountPaise, selectedParticipants, exactShareInputs.toMap(), percentageInputs.toMap(), shareWeightInputs.toMap()) {
        if (amountPaise <= 0 || selectedParticipants.isEmpty()) {
            emptyMap()
        } else {
            when (splitType) {
                SplitType.EQUAL -> {
                    MoneyUtils.calculateEqualSplit(amountPaise, selectedParticipants.toList())
                }
                SplitType.CUSTOM -> {
                    selectedParticipants.associateWith { uid ->
                        MoneyUtils.parseRupeesToPaise(exactShareInputs[uid] ?: "0") ?: 0L
                    }
                }
                SplitType.PERCENTAGE -> {
                    val pcts = selectedParticipants.associateWith { uid ->
                        (percentageInputs[uid] ?: "0").toDoubleOrNull() ?: 0.0
                    }
                    val totalPct = pcts.values.sum()
                    if (kotlin.math.abs(totalPct - 100.0) < 0.01) {
                        MoneyUtils.calculatePercentageSplit(amountPaise, pcts)
                    } else {
                        emptyMap()
                    }
                }
                SplitType.SHARES -> {
                    val weights = selectedParticipants.associateWith { uid ->
                        (shareWeightInputs[uid] ?: "1").toIntOrNull() ?: 1
                    }
                    if (weights.values.sum() > 0) {
                        MoneyUtils.calculateSharesRatioSplit(amountPaise, weights)
                    } else {
                        emptyMap()
                    }
                }
            }
        }
    }

    val exactSumPaise = remember(exactShareInputs.toMap(), selectedParticipants) {
        selectedParticipants.sumOf { uid ->
            MoneyUtils.parseRupeesToPaise(exactShareInputs[uid] ?: "0") ?: 0L
        }
    }

    val percentageSum = remember(percentageInputs.toMap(), selectedParticipants) {
        selectedParticipants.sumOf { uid ->
            (percentageInputs[uid] ?: "0").toDoubleOrNull() ?: 0.0
        }
    }

    val isSplitValid = remember(splitType, amountPaise, exactSumPaise, percentageSum, computedShares) {
        if (amountPaise <= 0 || selectedParticipants.isEmpty()) false
        else when (splitType) {
            SplitType.EQUAL -> true
            SplitType.CUSTOM -> exactSumPaise == amountPaise
            SplitType.PERCENTAGE -> kotlin.math.abs(percentageSum - 100.0) < 0.01 && computedShares.isNotEmpty()
            SplitType.SHARES -> computedShares.isNotEmpty()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Shared Expense",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("add_expense_close_btn")) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Title input
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Expense Title * (e.g. Hostel Grocery / Dinner)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("expense_title_input")
                        )
                    }

                    // Amount input (Plain numeric text input, strictly NO sliders)
                    item {
                        OutlinedTextField(
                            value = amountInput,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.matches(Regex("""^\d*(\.\d{0,2})?$"""))) {
                                    amountInput = input
                                }
                            },
                            label = { Text("Total Amount in ₹ * (e.g. 750 or 750.50)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("expense_amount_input")
                        )
                    }

                    // Category Chooser
                    item {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                ExpenseCategory.FOOD,
                                ExpenseCategory.GROCERIES,
                                ExpenseCategory.RENT,
                                ExpenseCategory.TRAVEL
                            ).forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory == cat,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat.label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Paid By Selector
                    item {
                        Text(
                            text = "Paid By (Who paid upfront?)",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            members.forEach { m ->
                                val isSelected = paidByUserId == m.user.id
                                Surface(
                                    onClick = { paidByUserId = m.user.id },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        AvatarView(name = m.user.displayName, size = 24.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (m.user.id == currentUserId) "You" else m.user.displayName.split(" ").first(),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Participants Selection
                    item {
                        Text(
                            text = "Participants (Who is sharing?)",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            members.forEach { m ->
                                val isChecked = selectedParticipants.contains(m.user.id)
                                Surface(
                                    onClick = {
                                        selectedParticipants = if (isChecked) {
                                            if (selectedParticipants.size > 1) selectedParticipants - m.user.id else selectedParticipants
                                        } else {
                                            selectedParticipants + m.user.id
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { check ->
                                                selectedParticipants = if (check) selectedParticipants + m.user.id
                                                else if (selectedParticipants.size > 1) selectedParticipants - m.user.id
                                                else selectedParticipants
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        AvatarView(name = m.user.displayName, size = 28.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = m.user.displayName, style = MaterialTheme.typography.bodyMedium)
                                        Spacer(modifier = Modifier.weight(1f))

                                        // Live share preview
                                        if (isChecked && computedShares.containsKey(m.user.id)) {
                                            val share = computedShares[m.user.id] ?: 0L
                                            Text(
                                                text = MoneyUtils.formatPaiseToRupees(share),
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Split Mode Tabs (Equal, Exact, Percentage, Shares)
                    item {
                        Text(
                            text = "Split Mode",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                SplitType.EQUAL to "Equally",
                                SplitType.CUSTOM to "Exact ₹",
                                SplitType.PERCENTAGE to "%",
                                SplitType.SHARES to "Shares"
                            ).forEach { (type, label) ->
                                FilterChip(
                                    selected = splitType == type,
                                    onClick = { splitType = type },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    // Mode: Exact Paise Inputs
                    if (splitType == SplitType.CUSTOM) {
                        item {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Enter Exact Amount for each participant:",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    members.filter { selectedParticipants.contains(it.user.id) }.forEach { m ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = m.user.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = exactShareInputs[m.user.id] ?: "",
                                                onValueChange = { exactShareInputs[m.user.id] = it },
                                                placeholder = { Text("0.00") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                prefix = { Text("₹ ") },
                                                modifier = Modifier.width(130.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    val diff = amountPaise - exactSumPaise
                                    Text(
                                        text = if (diff == 0L) "✓ Exact match: ${MoneyUtils.formatPaiseToRupees(amountPaise)}"
                                        else if (diff > 0) "⚠️ ${MoneyUtils.formatPaiseToRupees(diff)} remaining to allocate"
                                        else "⚠️ Overallocated by ${MoneyUtils.formatPaiseToRupees(-diff)}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (diff == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Mode: Percentage Inputs
                    if (splitType == SplitType.PERCENTAGE) {
                        item {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Enter Percentage for each participant (Total must be 100%):",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    members.filter { selectedParticipants.contains(it.user.id) }.forEach { m ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = m.user.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = percentageInputs[m.user.id] ?: "",
                                                onValueChange = { percentageInputs[m.user.id] = it },
                                                placeholder = { Text("0") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                                singleLine = true,
                                                suffix = { Text("%") },
                                                modifier = Modifier.width(110.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    val isPct100 = kotlin.math.abs(percentageSum - 100.0) < 0.01
                                    Text(
                                        text = if (isPct100) "✓ Exactly 100.0%" else "⚠️ Total: ${String.format(java.util.Locale.US, "%.1f", percentageSum)}% (Must be 100%)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isPct100) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Mode: Shares Ratio Inputs (e.g. 1 share, 2 shares)
                    if (splitType == SplitType.SHARES) {
                        item {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Enter Shares ratio (e.g. 1 for single share, 2 for double):",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    members.filter { selectedParticipants.contains(it.user.id) }.forEach { m ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = m.user.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = shareWeightInputs[m.user.id] ?: "1",
                                                onValueChange = { input ->
                                                    if (input.all { it.isDigit() }) {
                                                        shareWeightInputs[m.user.id] = input
                                                    }
                                                },
                                                placeholder = { Text("1") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                suffix = { Text("x") },
                                                modifier = Modifier.width(90.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bill Photo / Receipt Attachment (Zero-permission Android Photo Picker)
                    item {
                        Surface(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Icon(
                                    imageVector = if (selectedReceiptUri != null) Icons.Default.Image else Icons.Default.AttachFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (selectedReceiptUri != null) "Bill Photo Attached ✓" else "Attach Bill Photo (Optional)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = if (selectedReceiptUri != null) "Tap to change photo" else "Pick photo using Android Photo Picker",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }
                                if (selectedReceiptUri != null) {
                                    IconButton(onClick = { selectedReceiptUri = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove receipt", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Optional Notes
                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Notes (Optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Save Button with strict validation
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            Toast.makeText(context, "Please enter an expense title", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (amountPaise <= 0) {
                            Toast.makeText(context, "Please enter a valid amount greater than ₹0", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (selectedParticipants.isEmpty()) {
                            Toast.makeText(context, "Select at least one participant", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!isSplitValid) {
                            Toast.makeText(context, "Please verify split amounts match the total", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        onSaveExpense(
                            title.trim(),
                            amountPaise,
                            selectedCategory,
                            paidByUserId,
                            selectedParticipants.toList(),
                            splitType,
                            computedShares,
                            notes.ifBlank { null },
                            selectedReceiptUri?.toString()
                        )
                        Toast.makeText(context, "Expense saved successfully!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    enabled = title.isNotBlank() && amountPaise > 0 && selectedParticipants.isNotEmpty() && isSplitValid,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_expense_submit_btn")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Save Expense (${MoneyUtils.formatPaiseToRupees(amountPaise)})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
