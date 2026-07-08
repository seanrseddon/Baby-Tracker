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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddActivityScreen(
    viewModel: BabyViewModel,
    onNavigateBack: () -> Unit
) {
    var selectedType by remember { mutableStateOf("FEEDING") } // FEEDING, SLEEP, NAPPY
    var notes by remember { mutableStateOf("") }
    
    // Feeding fields
    var feedingMethod by remember { mutableStateOf("Bottle") } // Bottle, Breast, Solid
    var feedingAmount by remember { mutableStateOf("4") }
    var feedingUnit by remember { mutableStateOf("oz") } // ml, oz
    var feedingDuration by remember { mutableStateOf("15") }

    // Sleep fields
    var sleepStartTime by remember { mutableStateOf(System.currentTimeMillis() - 60 * 60 * 1000L) } // default 1 hour ago
    var sleepEndTime by remember { mutableStateOf(System.currentTimeMillis()) } // default now

    // Nappy fields
    var nappyStatus by remember { mutableStateOf("Wet") } // Wet, Dirty, Both, Dry
    var weeSize by remember { mutableStateOf("Medium") } // Small, Medium, Big
    var poopSize by remember { mutableStateOf("Medium") } // Small, Medium, Big

    // Medication fields
    var medName by remember { mutableStateOf("") }
    var medDosage by remember { mutableStateOf("") }
    var medFrequency by remember { mutableStateOf("") }

    var timeOffsetMinutes by remember { mutableStateOf(0) } // 0=Now, 15=15m ago, 30=30m ago, 60=1h ago, -1=Custom
    var selectedCustomTime by remember { mutableStateOf(System.currentTimeMillis()) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val customCalendar = remember(selectedCustomTime) {
        Calendar.getInstance().apply { timeInMillis = selectedCustomTime }
    }

    val datePickerDialog = remember(selectedCustomTime) {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selCal = Calendar.getInstance().apply {
                    timeInMillis = selectedCustomTime
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedCustomTime = selCal.timeInMillis
            },
            customCalendar.get(Calendar.YEAR),
            customCalendar.get(Calendar.MONTH),
            customCalendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    val timePickerDialog = remember(selectedCustomTime) {
        android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val selCal = Calendar.getInstance().apply {
                    timeInMillis = selectedCustomTime
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                selectedCustomTime = selCal.timeInMillis
            },
            customCalendar.get(Calendar.HOUR_OF_DAY),
            customCalendar.get(Calendar.MINUTE),
            false // 12-hour format
        )
    }

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
                    isSelected = selectedType == "NAPPY",
                    icon = Icons.Default.Opacity,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedType = "NAPPY" }
                )
                TypeTabButton(
                    label = "Meds",
                    isSelected = selectedType == "MEDICATION",
                    icon = Icons.Default.MedicalServices,
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f),
                    onClick = { selectedType = "MEDICATION" }
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
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val startCal = remember(sleepStartTime) { Calendar.getInstance().apply { timeInMillis = sleepStartTime } }
                            val endCal = remember(sleepEndTime) { Calendar.getInstance().apply { timeInMillis = sleepEndTime } }
                            
                            val startDatePicker = remember(sleepStartTime) {
                                android.app.DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        val sel = Calendar.getInstance().apply {
                                            timeInMillis = sleepStartTime
                                            set(Calendar.YEAR, y)
                                            set(Calendar.MONTH, m)
                                            set(Calendar.DAY_OF_MONTH, d)
                                        }
                                        sleepStartTime = sel.timeInMillis
                                        if (sleepEndTime < sel.timeInMillis) {
                                            sleepEndTime = sel.timeInMillis + 60 * 60 * 1000L
                                        }
                                    },
                                    startCal.get(Calendar.YEAR),
                                    startCal.get(Calendar.MONTH),
                                    startCal.get(Calendar.DAY_OF_MONTH)
                                )
                            }
                            val startTimePicker = remember(sleepStartTime) {
                                android.app.TimePickerDialog(
                                    context,
                                    { _, h, min ->
                                        val sel = Calendar.getInstance().apply {
                                            timeInMillis = sleepStartTime
                                            set(Calendar.HOUR_OF_DAY, h)
                                            set(Calendar.MINUTE, min)
                                        }
                                        sleepStartTime = sel.timeInMillis
                                        if (sleepEndTime < sel.timeInMillis) {
                                            sleepEndTime = sel.timeInMillis + 60 * 60 * 1000L
                                        }
                                    },
                                    startCal.get(Calendar.HOUR_OF_DAY),
                                    startCal.get(Calendar.MINUTE),
                                    false
                                )
                            }
                            
                            val endDatePicker = remember(sleepEndTime) {
                                android.app.DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        val sel = Calendar.getInstance().apply {
                                            timeInMillis = sleepEndTime
                                            set(Calendar.YEAR, y)
                                            set(Calendar.MONTH, m)
                                            set(Calendar.DAY_OF_MONTH, d)
                                        }
                                        sleepEndTime = sel.timeInMillis
                                    },
                                    endCal.get(Calendar.YEAR),
                                    endCal.get(Calendar.MONTH),
                                    endCal.get(Calendar.DAY_OF_MONTH)
                                )
                            }
                            val endTimePicker = remember(sleepEndTime) {
                                android.app.TimePickerDialog(
                                    context,
                                    { _, h, min ->
                                        val sel = Calendar.getInstance().apply {
                                            timeInMillis = sleepEndTime
                                            set(Calendar.HOUR_OF_DAY, h)
                                            set(Calendar.MINUTE, min)
                                        }
                                        sleepEndTime = sel.timeInMillis
                                    },
                                    endCal.get(Calendar.HOUR_OF_DAY),
                                    endCal.get(Calendar.MINUTE),
                                    false
                                )
                            }

                            val calculatedMinutes = remember(sleepStartTime, sleepEndTime) {
                                val diff = sleepEndTime - sleepStartTime
                                if (diff > 0) (diff / 60000).toInt() else 0
                            }
                            val durationString = remember(calculatedMinutes) {
                                val hrs = calculatedMinutes / 60
                                val mins = calculatedMinutes % 60
                                if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Sleep Timing", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                
                                // Start Time Row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Start:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.width(48.dp))
                                    val sdfDate = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
                                    val sdfTime = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                                    
                                    OutlinedButton(
                                        onClick = { startDatePicker.show() },
                                        modifier = Modifier.weight(1.2f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(sdfDate.format(Date(sleepStartTime)), fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { startTimePicker.show() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(sdfTime.format(Date(sleepStartTime)), fontSize = 11.sp)
                                    }
                                }

                                // End Time Row
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("End:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.width(48.dp))
                                    val sdfDate = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
                                    val sdfTime = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                                    
                                    OutlinedButton(
                                        onClick = { endDatePicker.show() },
                                        modifier = Modifier.weight(1.2f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(sdfDate.format(Date(sleepEndTime)), fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { endTimePicker.show() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(sdfTime.format(Date(sleepEndTime)), fontSize = 11.sp)
                                    }
                                }

                                // Calculated Duration Box
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Total Duration:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(durationString, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                                    }
                                }
                            }
                        }

                        "NAPPY" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Status", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("Wet", "Dirty", "Both", "Dry").forEach { status ->
                                        val isSel = nappyStatus == status
                                        val activeColor = MaterialTheme.colorScheme.tertiary
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSel) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow)
                                                .clickable { nappyStatus = status }
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

                                if (nappyStatus == "Wet" || nappyStatus == "Both") {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Wee Size", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("Small", "Medium", "Big").forEach { size ->
                                            val isSel = weeSize == size
                                            val activeColor = MaterialTheme.colorScheme.primary
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isSel) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow)
                                                    .clickable { weeSize = size }
                                                    .padding(vertical = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    size,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSel) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                if (nappyStatus == "Dirty" || nappyStatus == "Both") {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Poop Size", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("Small", "Medium", "Big").forEach { size ->
                                            val isSel = poopSize == size
                                            val activeColor = MaterialTheme.colorScheme.secondary
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isSel) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow)
                                                    .clickable { poopSize = size }
                                                    .padding(vertical = 10.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    size,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSel) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        "MEDICATION" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Medication Details", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                OutlinedTextField(
                                    value = medName,
                                    onValueChange = { medName = it },
                                    label = { Text("Medication Name (e.g. Calpol, Vitamin D)") },
                                    modifier = Modifier.fillMaxWidth().testTag("med_name_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = medDosage,
                                        onValueChange = { medDosage = it },
                                        label = { Text("Dosage") },
                                        modifier = Modifier.weight(1f).testTag("med_dosage_input"),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = medFrequency,
                                        onValueChange = { medFrequency = it },
                                        label = { Text("Frequency") },
                                        modifier = Modifier.weight(1f).testTag("med_frequency_input"),
                                        shape = RoundedCornerShape(12.dp)
                                    )
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

                    Spacer(modifier = Modifier.height(4.dp))

                    // Time of Activity
                    Text(
                        text = "Time of Activity",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(0 to "Now", 15 to "15m", 30 to "30m", 60 to "1h", -1 to "Custom").forEach { (offset, label) ->
                            val isSel = timeOffsetMinutes == offset
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                                    .clickable { timeOffsetMinutes = offset }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (timeOffsetMinutes == -1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val sdfDate = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
                            val sdfTime = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

                            OutlinedButton(
                                onClick = { datePickerDialog.show() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(sdfDate.format(Date(selectedCustomTime)), fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { timePickerDialog.show() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(sdfTime.format(Date(selectedCustomTime)), fontSize = 12.sp)
                            }
                        }
                    }
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
                            val diff = sleepEndTime - sleepStartTime
                            val calculatedMinutes = if (diff > 0) (diff / 60000).toInt() else 0
                            details.put("durationMinutes", calculatedMinutes)
                            details.put("startTime", sleepStartTime)
                            details.put("endTime", sleepEndTime)
                        }
                        "NAPPY" -> {
                            details.put("status", nappyStatus)
                            details.put("weeSize", weeSize)
                            details.put("poopSize", poopSize)
                        }
                        "MEDICATION" -> {
                            details.put("name", medName.trim().ifEmpty { "Unspecified Medication" })
                            details.put("dosage", medDosage.trim())
                            details.put("frequency", medFrequency.trim())
                        }
                    }

                    val finalTimestamp = when (selectedType) {
                        "SLEEP" -> sleepStartTime
                        else -> when (timeOffsetMinutes) {
                            -1 -> selectedCustomTime
                            0 -> System.currentTimeMillis()
                            else -> System.currentTimeMillis() - (timeOffsetMinutes * 60 * 1000L)
                        }
                    }

                    viewModel.logActivity(
                        type = selectedType,
                        detailsJson = details.toString(),
                        notes = notes,
                        timestamp = finalTimestamp
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
                        "MEDICATION" -> Color(0xFF10B981)
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
