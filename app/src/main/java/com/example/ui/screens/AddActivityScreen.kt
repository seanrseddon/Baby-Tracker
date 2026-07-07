package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.BabyViewModel
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddActivityScreen(
    viewModel: BabyViewModel,
    onNavigateBack: () -> Unit
) {
    var selectedType by remember { mutableStateOf("FEEDING") } // FEEDING, SLEEP, DIAPER
    var notes by remember { mutableStateOf("") }
    
    // Feeding fields
    var feedingMethod by remember { mutableStateOf("Bottle") } // Bottle, Breast, Solid
    var feedingAmount by remember { mutableStateOf("120") }
    var feedingUnit by remember { mutableStateOf("ml") } // ml, oz
    var feedingDuration by remember { mutableStateOf("15") }

    // Sleep fields
    var sleepDuration by remember { mutableStateOf("60") }

    // Diaper fields
    var diaperStatus by remember { mutableStateOf("Wet") } // Wet, Dirty, Both, Dry

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log New Activity", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Activity Type Tab Row
            Text(
                text = "Select Activity Type",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.outline
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TypeTabButton(
                    label = "Feeding",
                    isSelected = selectedType == "FEEDING",
                    icon = Icons.Default.Restaurant,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedType = "FEEDING" }
                )
                TypeTabButton(
                    label = "Sleep",
                    isSelected = selectedType == "SLEEP",
                    icon = Icons.Default.NightsStay,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedType = "SLEEP" }
                )
                TypeTabButton(
                    label = "Nappy",
                    isSelected = selectedType == "DIAPER",
                    icon = Icons.Default.Opacity,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedType = "DIAPER" }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Dynamic Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (selectedType) {
                        "FEEDING" -> {
                            // Method Selection
                            Column {
                                Text("Method", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Bottle", "Breast", "Solid").forEach { method ->
                                        val isSel = feedingMethod == method
                                        val activeColor = MaterialTheme.colorScheme.secondary
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSel) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow)
                                                .clickable { feedingMethod = method }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = method,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }

                            if (feedingMethod != "Solid") {
                                // Amount
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = feedingAmount,
                                        onValueChange = { feedingAmount = it },
                                        label = { Text("Amount") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(2f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    // Unit selection
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Unit", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                        Row(modifier = Modifier.fillMaxWidth()) {
                                            listOf("ml", "oz").forEach { unit ->
                                                val isSel = feedingUnit == unit
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                                        .clickable { feedingUnit = unit }
                                                        .padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        unit,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Duration
                            OutlinedTextField(
                                value = feedingDuration,
                                onValueChange = { feedingDuration = it },
                                label = { Text("Duration (minutes)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        "SLEEP" -> {
                            OutlinedTextField(
                                value = sleepDuration,
                                onValueChange = { sleepDuration = it },
                                label = { Text("Duration (minutes)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        "DIAPER" -> {
                            Column {
                                Text("Status", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("Wet", "Dirty", "Both", "Dry").forEach { status ->
                                        val isSel = diaperStatus == status
                                        val activeColor = MaterialTheme.colorScheme.tertiary
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSel) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow)
                                                .clickable { diaperStatus = status }
                                                .padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                status,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Notes (Common)
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes & Comments") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save Button
            Button(
                onClick = {
                    val details = JSONObject()
                    when (selectedType) {
                        "FEEDING" -> {
                            details.put("method", feedingMethod)
                            details.put("amount", feedingAmount.toDoubleOrNull() ?: 0.0)
                            details.put("unit", feedingUnit)
                            details.put("durationMinutes", feedingDuration.toIntOrNull() ?: 15)
                        }
                        "SLEEP" -> {
                            details.put("durationMinutes", sleepDuration.toIntOrNull() ?: 60)
                        }
                        "DIAPER" -> {
                            details.put("status", diaperStatus)
                        }
                    }

                    viewModel.logActivity(
                        type = selectedType,
                        detailsJson = details.toString(),
                        notes = notes
                    )

                    onNavigateBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("save_activity_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (selectedType) {
                        "FEEDING" -> MaterialTheme.colorScheme.secondary
                        "SLEEP" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                )
            ) {
                Text("Save Log Entry", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun TypeTabButton(
    label: String,
    isSelected: Boolean,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("type_tab_$label"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, color) else null
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) color else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isSelected) color else MaterialTheme.colorScheme.outline
            )
        }
    }
}
