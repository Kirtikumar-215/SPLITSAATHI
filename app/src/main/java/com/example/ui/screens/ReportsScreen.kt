package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ExpenseEntity
import com.example.data.model.GroupEntity
import com.example.data.model.ProfileEntity
import com.example.ui.components.DisclaimerBanner
import com.example.ui.components.SplitSaathiTopBar
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportsScreen(
    currentProfile: ProfileEntity?,
    groups: List<GroupEntity>,
    expenses: List<ExpenseEntity>,
    onProfileClick: () -> Unit
) {
    val context = LocalContext.current
    val totalExpensePaise = expenses.sumOf { it.amountPaise }
    val categoryBreakdown = expenses.groupBy { it.category }

    Scaffold(
        topBar = {
            SplitSaathiTopBar(
                title = stringResource(R.string.nav_reports),
                currentUserName = currentProfile?.displayName,
                onProfileClick = onProfileClick
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                DisclaimerBanner()
            }

            // Overview Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().testTag("reports_overview_card")
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Total Group Spending",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = MoneyUtils.formatPaiseToRupees(totalExpensePaise),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${expenses.size} expenses across ${groups.size} groups",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        )
                    }
                }
            }

            // CSV Export Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Export Expenses (CSV)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Download or share clean spreadsheet records",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                        Button(
                            onClick = {
                                val csv = buildCsv(expenses)
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, csv)
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TITLE, "SplitSaathi_Expenses.csv")
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Export Expenses"))
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("export_csv_btn")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export")
                        }
                    }
                }
            }

            // Category Breakdown List
            item {
                Text(
                    text = "Category Breakdown",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            if (categoryBreakdown.isEmpty()) {
                item {
                    Text(
                        text = "No expenses to display yet.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            } else {
                categoryBreakdown.forEach { (cat, exps) ->
                    val sum = exps.sumOf { it.amountPaise }
                    val fraction = if (totalExpensePaise > 0) (sum.toFloat() / totalExpensePaise.toFloat()) else 0f
                    val percentage = (fraction * 100).toInt()
                    item(key = cat.name) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Outlined.Receipt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(text = cat.label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                            Text(
                                                text = "${exps.size} entries • $percentage%",
                                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            )
                                        }
                                    }
                                    Text(
                                        text = MoneyUtils.formatPaiseToRupees(sum),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sanitizeCsvField(value: String): String {
    var clean = value.replace("\"", "\"\"").replace("\r\n", " ").replace("\n", " ").replace("\r", " ")
    // Prevent CSV formula injection by prepending single quote if starting with formula triggers
    if (clean.isNotEmpty() && (clean.startsWith("=") || clean.startsWith("+") || clean.startsWith("-") || clean.startsWith("@") || clean.startsWith("\t"))) {
        clean = "'$clean"
    }
    return "\"$clean\""
}

private fun buildCsv(expenses: List<ExpenseEntity>): String {
    val sb = StringBuilder()
    sb.append("Date,Title,Category,Amount (INR),Split Type,Status\n")
    val df = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    for (e in expenses) {
        val dateStr = sanitizeCsvField(df.format(Date(e.expenseDate)))
        val cleanTitle = sanitizeCsvField(e.title)
        val catStr = sanitizeCsvField(e.category.label)
        val amountStr = sanitizeCsvField(MoneyUtils.formatPaiseForUpi(e.amountPaise))
        val splitStr = sanitizeCsvField(e.splitType.name)
        val statusStr = sanitizeCsvField(e.status.name)
        sb.append("$dateStr,$cleanTitle,$catStr,$amountStr,$splitStr,$statusStr\n")
    }
    return sb.toString()
}
