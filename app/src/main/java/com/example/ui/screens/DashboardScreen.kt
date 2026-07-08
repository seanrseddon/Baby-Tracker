package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BabyActivity
import com.example.ui.BabyViewModel
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: BabyViewModel,
    onNavigateToAdd: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val activities by viewModel.activities.collectAsState()
    val babyName by viewModel.babyName.collectAsState()
    val babyDob by viewModel.babyDob.collectAsState()
    val aiRecommendation by viewModel.aiRecommendation.collectAsState()
    val isAnalyzingSleep by viewModel.isAnalyzingSleep.collectAsState()
    val analysisError by viewModel.analysisError.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val syncError by viewModel.syncError.collectAsState()
    val sleepTimerStart by viewModel.sleepTimerStart.collectAsState()
    var showAdjustSleepDialog by remember { mutableStateOf(false) }
    var editingActivity by remember { mutableStateOf<BabyActivity?>(null) }
    var selectedTimelineType by remember { mutableStateOf<String?>(null) }

    // Periodically poll server status every 10 seconds to sync sleep timer/activities
    LaunchedEffect(key1 = serverUrl) {
        if (serverUrl.isNotBlank()) {
            while (true) {
                viewModel.triggerSync()
                kotlinx.coroutines.delay(10000)
            }
        }
    }

    // Calculate today's stats
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayMidnight = calendar.timeInMillis
    val todayActivities = activities.filter { it.timestamp >= todayMidnight }

    val feedingCount = todayActivities.count { it.type == "FEEDING" }
    val diaperCount = todayActivities.count { it.type == "DIAPER" || it.type == "NAPPY" }
    val medicationCount = todayActivities.count { it.type == "MEDICATION" }
    
    val totalSleepMinutes = todayActivities.filter { it.type == "SLEEP" }.sumOf {
        try {
            JSONObject(it.detailsJson).optInt("durationMinutes", 0)
        } catch (e: Exception) {
            0
        }
    }
    val sleepHoursText = String.format(Locale.getDefault(), "%.1f", totalSleepMinutes / 60.0)

    val groupedActivities = remember(activities) {
        val sorted = activities.sortedByDescending { it.timestamp }
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        
        val result = linkedMapOf<String, LinkedHashMap<String, MutableList<BabyActivity>>>()
        val cal = Calendar.getInstance()
        
        for (act in sorted) {
            cal.timeInMillis = act.timestamp
            val monthStr = monthFormat.format(Date(act.timestamp))
            
            // Start of week calculation (e.g. Monday)
            val firstDayCal = cal.clone() as Calendar
            firstDayCal.set(Calendar.DAY_OF_WEEK, firstDayCal.firstDayOfWeek)
            val firstDayStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(firstDayCal.time)
            
            val lastDayCal = firstDayCal.clone() as Calendar
            lastDayCal.add(Calendar.DAY_OF_WEEK, 6)
            val lastDayStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(lastDayCal.time)
            
            val weekStr = "Week of $firstDayStr - $lastDayStr"
            
            val monthMap = result.getOrPut(monthStr) { linkedMapOf() }
            val weekList = monthMap.getOrPut(weekStr) { mutableListOf() }
            weekList.add(act)
        }
        result
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("collapse_prefs", android.content.Context.MODE_PRIVATE) }

    val collapsedMonths = remember {
        val map = mutableStateMapOf<String, Boolean>()
        sharedPrefs.all.forEach { (key, value) ->
            if (key.startsWith("month_") && value is Boolean) {
                map[key.substringAfter("month_")] = value
            }
        }
        map
    }
    val collapsedWeeks = remember {
        val map = mutableStateMapOf<String, Boolean>()
        sharedPrefs.all.forEach { (key, value) ->
            if (key.startsWith("week_") && value is Boolean) {
                map[key.substringAfter("week_")] = value
            }
        }
        map
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "$babyName's Tracker",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (serverUrl.isBlank()) "Offline (No server configured)" else "Local sync active",
                            fontSize = 12.sp,
                            color = if (serverUrl.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    if (serverUrl.isNotBlank()) {
                        IconButton(
                            onClick = { viewModel.triggerSync() },
                            modifier = Modifier.testTag("sync_button")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = "Sync Now",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .testTag("add_activity_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Activity"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Sync error banner
            if (syncError != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Sync Error",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = syncError!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Quick Baby Status / Stat Row
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(
                            title = "Feedings",
                            value = feedingCount.toString(),
                            sub = "entries today",
                            icon = Icons.Default.Restaurant,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedTimelineType = "FEEDING" }
                        )
                        StatCard(
                            title = "Sleep",
                            value = "${sleepHoursText}h",
                            sub = "slept today",
                            icon = Icons.Default.NightsStay,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedTimelineType = "SLEEP" }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(
                            title = "Nappies",
                            value = diaperCount.toString(),
                            sub = "changes today",
                            icon = Icons.Default.Opacity,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                            onClick = { selectedTimelineType = "DIAPER" }
                        )
                        StatCard(
                            title = "Meds",
                            value = medicationCount.toString(),
                            sub = "given today",
                            icon = Icons.Default.MedicalServices,
                            color = Color(0xFF10B981),
                            modifier = Modifier.weight(1f),
                            onClick = { selectedTimelineType = "MEDICATION" }
                        )
                    }
                }
            }

            // Sleep Timer Controller Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (sleepTimerStart != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (sleepTimerStart != null) MaterialTheme.colorScheme.primary.copy(
                                            alpha = 0.2f
                                        ) else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = "Sleep Timer",
                                    tint = if (sleepTimerStart != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sleep Tracking Timer",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (sleepTimerStart != null) "Timer is active..." else "Press Start when baby goes to sleep",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (sleepTimerStart != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Simple timer counter
                            var elapsedMinutes by remember { mutableStateOf(0) }
                            LaunchedEffect(key1 = sleepTimerStart) {
                                while (true) {
                                    val elapsed = (System.currentTimeMillis() - sleepTimerStart!!) / 60000
                                    elapsedMinutes = elapsed.toInt()
                                    kotlinx.coroutines.delay(10000) // check every 10 seconds
                                }
                            }

                            Text(
                                text = "Asleep for $elapsedMinutes mins",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showAdjustSleepDialog = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                val sdf = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                                val startTimeFormatted = sdf.format(Date(sleepTimerStart!!))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Start Time",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Started at $startTimeFormatted (Tap to adjust)",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.cancelSleepTimer() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Discard", fontSize = 13.sp)
                                }
                                Button(
                                    onClick = { viewModel.stopAndSaveSleepTimer() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Save")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Wake Up", fontSize = 13.sp)
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.startSleepTimer() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("start_sleep_timer_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Start")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Start Sleep Timer")
                            }
                        }
                    }
                }
            }

            // Gemini Sleep Coach Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Header Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Sleep Coach",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Gemini Sleep Coach",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Smart Nap Predictions & Analysis",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (aiRecommendation.isNotBlank()) {
                                IconButton(
                                    onClick = { viewModel.clearSleepAnalysis() },
                                    modifier = Modifier.testTag("clear_sleep_analysis_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Clear Sleep Analysis",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        if (babyDob <= 0L) {
                            // Prompt to add DOB
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Add $babyName's Date of Birth in Settings to receive age-specific sleep recommendations matched against global baby nap milestones.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = onNavigateToSettings,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("set_dob_nav_btn")
                                ) {
                                    Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Set Date of Birth", fontSize = 13.sp)
                                }
                            }
                        } else {
                            // Calculate current age
                            val diffMs = System.currentTimeMillis() - babyDob
                            val days = diffMs / (1000 * 60 * 60 * 24)
                            val months = days / 30
                            val weeks = days / 7
                            val ageText = when {
                                months > 0 -> "$months months old"
                                weeks > 0 -> "$weeks weeks old"
                                else -> "$days days old"
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Baby Age Banner
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChildCare,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "$babyName is $ageText",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (aiRecommendation.isNotBlank()) {
                                    // Expandable/Scrollable AI text
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.surfaceContainerLow,
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .padding(16.dp)
                                    ) {
                                        Text(
                                            text = "Latest Sleep Analysis:",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        
                                        MarkdownText(
                                            text = aiRecommendation,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                if (analysisError != null) {
                                    Text(
                                        text = analysisError!!,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                }

                                // Fetch/Analyze Button
                                Button(
                                    onClick = { viewModel.analyzeSleepPattern() },
                                    enabled = !isAnalyzingSleep,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("analyze_sleep_btn"),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    if (isAnalyzingSleep) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Analyzing Sleep Logs...", fontSize = 13.sp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (aiRecommendation.isBlank()) "Analyze Sleep & Predict Naps" else "Refresh Sleep Insights",
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // List Title
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Activity Log",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${activities.size} logs",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Empty state placeholder
            if (activities.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChildCare,
                            contentDescription = "No logs",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No activities logged yet",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Tap the '+' button or start a timer to begin tracking",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }

            // Lazy List of Activities grouped by Month and Week
            groupedActivities.forEach { (month, weeks) ->
                val isMonthCollapsed = collapsedMonths[month] == true
                
                // Month Header
                item(key = "month_hdr_$month") {
                    Card(
                        onClick = {
                            val newVal = !isMonthCollapsed
                            collapsedMonths[month] = newVal
                            sharedPrefs.edit().putBoolean("month_$month", newVal).apply()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = month,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Icon(
                                imageVector = if (isMonthCollapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                                contentDescription = if (isMonthCollapsed) "Expand" else "Collapse",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (!isMonthCollapsed) {
                    weeks.forEach { (week, weekActivities) ->
                        val weekKey = "$month|$week"
                        val isWeekCollapsed = collapsedWeeks[weekKey] == true
                        
                        // Week Header (with small indentation or slightly smaller text)
                        item(key = "week_hdr_$weekKey") {
                            Card(
                                onClick = {
                                    val newVal = !isWeekCollapsed
                                    collapsedWeeks[weekKey] = newVal
                                    sharedPrefs.edit().putBoolean("week_$weekKey", newVal).apply()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = week,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "(${weekActivities.size} logs)",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isWeekCollapsed) Icons.Default.ChevronRight else Icons.Default.ExpandMore,
                                        contentDescription = if (isWeekCollapsed) "Expand" else "Collapse",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (!isWeekCollapsed) {
                            items(weekActivities, key = { "act_${it.id}" }) { activity ->
                                Box(modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp)) {
                                    ActivityLogCard(
                                        activity = activity,
                                        onEdit = { editingActivity = activity },
                                        onDelete = { viewModel.deleteActivity(activity.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) } // Cushion space for FAB
        }
    }

    if (showAdjustSleepDialog && sleepTimerStart != null) {
        AdjustSleepTimeDialog(
            initialStartTime = sleepTimerStart!!,
            onDismiss = { showAdjustSleepDialog = false },
            onConfirm = { adjustedTime ->
                viewModel.updateSleepTimerStart(adjustedTime)
                showAdjustSleepDialog = false
            }
        )
    }

    if (editingActivity != null) {
        EditActivityDialog(
            activity = editingActivity!!,
            onDismiss = { editingActivity = null },
            onConfirm = { updated ->
                viewModel.updateActivity(updated)
                editingActivity = null
            }
        )
    }

    if (selectedTimelineType != null) {
        val filtered = todayActivities.filter { 
            if (selectedTimelineType == "DIAPER") {
                it.type == "DIAPER" || it.type == "NAPPY"
            } else {
                it.type == selectedTimelineType
            }
        }
        TimelineDialog(
            type = selectedTimelineType!!,
            activities = filtered,
            onDismiss = { selectedTimelineType = null }
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    sub: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = color
            )
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = sub,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun TimelineDialog(
    type: String,
    activities: List<BabyActivity>,
    onDismiss: () -> Unit
) {
    val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
    val categoryColor = when (type) {
        "FEEDING" -> MaterialTheme.colorScheme.secondary
        "SLEEP" -> MaterialTheme.colorScheme.primary
        "DIAPER", "NAPPY" -> MaterialTheme.colorScheme.tertiary
        "MEDICATION" -> Color(0xFF10B981)
        else -> MaterialTheme.colorScheme.outline
    }
    val title = when (type) {
        "FEEDING" -> "Today's Feedings"
        "SLEEP" -> "Today's Sleep Logs"
        "DIAPER", "NAPPY" -> "Today's Nappies"
        "MEDICATION" -> "Today's Medications"
        else -> "Today's Timeline"
    }

    val sortedActivities = remember(activities) {
        activities.sortedBy { it.timestamp }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = when (type) {
                        "FEEDING" -> Icons.Default.Restaurant
                        "SLEEP" -> Icons.Default.NightsStay
                        "DIAPER", "NAPPY" -> Icons.Default.Opacity
                        else -> Icons.Default.MedicalServices
                    },
                    contentDescription = null,
                    tint = categoryColor
                )
                Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        text = {
            if (sortedActivities.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No events logged for today yet.",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 14.sp
                    )
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(sortedActivities) { index, activity ->
                        val timeStr = formatter.format(Date(activity.timestamp))
                        var detailsText = ""
                        when (activity.type) {
                            "FEEDING" -> {
                                try {
                                    val details = JSONObject(activity.detailsJson)
                                    val method = details.optString("method", "Bottle")
                                    val amount = details.optDouble("amount", 0.0)
                                    val unit = details.optString("unit", "oz")
                                    val duration = details.optInt("durationMinutes", 0)
                                    detailsText = if (method == "Solid") {
                                        "Solid feeding ($duration mins)"
                                    } else {
                                        "$method: $amount $unit ($duration mins)"
                                    }
                                } catch (e: Exception) {
                                    detailsText = "Feeding activity"
                                }
                            }
                            "SLEEP" -> {
                                try {
                                    val details = JSONObject(activity.detailsJson)
                                    val dur = details.optInt("durationMinutes", 0)
                                    val hrs = dur / 60
                                    val mins = dur % 60
                                    val durStr = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
                                    val startTime = details.optLong("startTime", 0L)
                                    val endTime = details.optLong("endTime", 0L)
                                    detailsText = if (startTime > 0L && endTime > 0L) {
                                        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                                        "Slept for $durStr (${sdf.format(Date(startTime))} - ${sdf.format(Date(endTime))})"
                                    } else {
                                        "Slept for $durStr"
                                    }
                                } catch (e: Exception) {
                                    detailsText = "Sleeping activity"
                                }
                            }
                            "DIAPER", "NAPPY" -> {
                                try {
                                    val details = JSONObject(activity.detailsJson)
                                    val status = details.optString("status", "Wet")
                                    val weeSize = details.optString("weeSize", "")
                                    val poopSize = details.optString("poopSize", "")
                                    val parts = mutableListOf<String>()
                                    if (status == "Wet" || status == "Both") {
                                        parts.add("Wee: " + (if (weeSize.isNotBlank()) weeSize else "Medium"))
                                    }
                                    if (status == "Dirty" || status == "Both") {
                                        parts.add("Poop: " + (if (poopSize.isNotBlank()) poopSize else "Medium"))
                                    }
                                    if (status == "Dry") {
                                        parts.add("Dry")
                                    }
                                    detailsText = "Nappy: " + parts.joinToString(", ")
                                } catch (e: Exception) {
                                    detailsText = "Nappy activity"
                                }
                            }
                            "MEDICATION" -> {
                                try {
                                    val details = JSONObject(activity.detailsJson)
                                    val name = details.optString("name", "Unspecified")
                                    val dosage = details.optString("dosage", "")
                                    val frequency = details.optString("frequency", "")
                                    val dosePart = if (dosage.isNotBlank()) " - $dosage" else ""
                                    val freqPart = if (frequency.isNotBlank()) " ($frequency)" else ""
                                    detailsText = "$name$dosePart$freqPart"
                                } catch (e: Exception) {
                                    detailsText = "Medication log"
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(36.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .weight(1f)
                                        .background(if (index == 0) Color.Transparent else categoryColor.copy(alpha = 0.3f))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(categoryColor)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .weight(1f)
                                        .background(if (index == sortedActivities.size - 1) Color.Transparent else categoryColor.copy(alpha = 0.3f))
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = timeStr,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = detailsText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!activity.notes.isNullOrBlank()) {
                                    Text(
                                        text = activity.notes,
                                        fontSize = 12.sp,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ActivityLogCard(
    activity: BabyActivity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // Animation States
    val alphaAnim = remember { Animatable(0f) }
    val slideUpAnim = remember { Animatable(40f) }
    val shimmerOffsetAnim = remember { Animatable(-250f) }
    var showSparkle by remember { mutableStateOf(true) }

    LaunchedEffect(activity.id) {
        launch {
            alphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 800,
                    easing = FastOutSlowInEasing
                )
            )
        }
        launch {
            slideUpAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 800,
                    easing = FastOutSlowInEasing
                )
            )
        }
        shimmerOffsetAnim.animateTo(
            targetValue = 700f,
            animationSpec = tween(
                durationMillis = 1400,
                easing = LinearEasing
            )
        )
        showSparkle = false
    }

    val formatter = SimpleDateFormat("h:mm a - MMM d", Locale.getDefault())
    val timeStr = formatter.format(Date(activity.timestamp))

    var icon = Icons.Default.Restaurant
    val categoryColor = when (activity.type) {
        "FEEDING" -> MaterialTheme.colorScheme.secondary
        "SLEEP" -> MaterialTheme.colorScheme.primary
        "DIAPER", "NAPPY" -> MaterialTheme.colorScheme.tertiary
        "MEDICATION" -> Color(0xFF10B981)
        else -> MaterialTheme.colorScheme.outline
    }
    var detailsText = ""

    when (activity.type) {
        "FEEDING" -> {
            icon = Icons.Default.Restaurant
            try {
                val details = JSONObject(activity.detailsJson)
                val method = details.optString("method", "Bottle")
                val amount = details.optDouble("amount", 0.0)
                val unit = details.optString("unit", "oz")
                val duration = details.optInt("durationMinutes", 0)
                detailsText = if (method == "Solid") {
                    "Solid feeding ($duration mins)"
                } else {
                    "$method: $amount $unit ($duration mins)"
                }
            } catch (e: Exception) {
                detailsText = "Feeding activity"
            }
        }
        "SLEEP" -> {
            icon = Icons.Default.NightsStay
            try {
                val details = JSONObject(activity.detailsJson)
                val dur = details.optInt("durationMinutes", 0)
                val hrs = dur / 60
                val mins = dur % 60
                val durStr = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
                val startTime = details.optLong("startTime", 0L)
                val endTime = details.optLong("endTime", 0L)
                detailsText = if (startTime > 0L && endTime > 0L) {
                    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                    "Slept for $durStr (${sdf.format(Date(startTime))} - ${sdf.format(Date(endTime))})"
                } else {
                    "Slept for $durStr"
                }
            } catch (e: Exception) {
                detailsText = "Sleeping activity"
            }
        }
        "DIAPER", "NAPPY" -> {
            icon = Icons.Default.Opacity
            try {
                val details = JSONObject(activity.detailsJson)
                val status = details.optString("status", "Wet")
                val weeSize = details.optString("weeSize", "")
                val poopSize = details.optString("poopSize", "")
                val parts = mutableListOf<String>()
                if (status == "Wet" || status == "Both") {
                    parts.add("Wee: " + (if (weeSize.isNotBlank()) weeSize else "Medium"))
                }
                if (status == "Dirty" || status == "Both") {
                    parts.add("Poop: " + (if (poopSize.isNotBlank()) poopSize else "Medium"))
                }
                if (status == "Dry") {
                    parts.add("Dry")
                }
                detailsText = "Nappy: " + parts.joinToString(", ")
            } catch (e: Exception) {
                detailsText = "Nappy activity"
            }
        }
        "MEDICATION" -> {
            icon = Icons.Default.MedicalServices
            try {
                val details = JSONObject(activity.detailsJson)
                val name = details.optString("name", "Unspecified")
                val dosage = details.optString("dosage", "")
                val frequency = details.optString("frequency", "")
                val dosePart = if (dosage.isNotBlank()) " - $dosage" else ""
                val freqPart = if (frequency.isNotBlank()) " ($frequency)" else ""
                detailsText = "$name$dosePart$freqPart"
            } catch (e: Exception) {
                detailsText = "Medication log"
            }
        }
    }

    val shimmerBrush = if (shimmerOffsetAnim.value < 700f) {
        androidx.compose.ui.graphics.Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                categoryColor.copy(alpha = 0.05f),
                categoryColor.copy(alpha = 0.22f),
                categoryColor.copy(alpha = 0.05f),
                Color.Transparent
            ),
            start = androidx.compose.ui.geometry.Offset(shimmerOffsetAnim.value, 0f),
            end = androidx.compose.ui.geometry.Offset(shimmerOffsetAnim.value + 160f, 160f)
        )
    } else {
        null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("activity_card_${activity.id}")
            .graphicsLayer {
                alpha = alphaAnim.value
                translationY = slideUpAnim.value
            }
            .then(
                if (shimmerBrush != null) {
                    Modifier.drawWithContent {
                        drawContent()
                        drawRect(brush = shimmerBrush)
                    }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(categoryColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = activity.type,
                    tint = categoryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = activity.type,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = categoryColor,
                            modifier = Modifier
                                .background(categoryColor.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        if (showSparkle) {
                            val infiniteTransition = rememberInfiniteTransition(label = "sparkle")
                            val scale by infiniteTransition.animateFloat(
                                initialValue = 0.6f,
                                targetValue = 1.3f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(500, easing = androidx.compose.animation.core.LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "sparkle_scale"
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Sparkle",
                                tint = Color(0xFFFFD700),
                                modifier = Modifier
                                    .size(13.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                            )
                        }
                    }
                    Text(
                        text = timeStr,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = detailsText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (activity.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = activity.notes,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("edit_button_${activity.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("delete_button_${activity.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun AdjustSleepTimeDialog(
    initialStartTime: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val calendar = remember {
        Calendar.getInstance().apply {
            timeInMillis = initialStartTime
        }
    }
    
    var selectedHour by remember { mutableStateOf(calendar.get(Calendar.HOUR)) }
    val isPm = calendar.get(Calendar.AM_PM) == Calendar.PM
    var selectedMinute by remember { mutableStateOf(calendar.get(Calendar.MINUTE)) }
    var selectedAmPm by remember { mutableStateOf(if (isPm) "PM" else "AM") }
    
    if (selectedHour == 0) selectedHour = 12

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Adjust Sleep Start Time",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Update when the baby fell asleep. The timer will update its elapsed time from this start point.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = if (selectedHour == 0) "" else selectedHour.toString(),
                        onValueChange = { input ->
                            val parsed = input.toIntOrNull()
                            if (parsed == null) {
                                selectedHour = 0
                            } else if (parsed in 1..12) {
                                selectedHour = parsed
                            }
                        },
                        label = { Text("Hour") },
                        modifier = Modifier.width(72.dp),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    Text(
                        text = ":",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    
                    OutlinedTextField(
                        value = String.format("%02d", selectedMinute),
                        onValueChange = { input ->
                            val parsed = input.toIntOrNull()
                            if (parsed == null) {
                                selectedMinute = 0
                            } else if (parsed in 0..59) {
                                selectedMinute = parsed
                            }
                        },
                        label = { Text("Min") },
                        modifier = Modifier.width(72.dp),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("AM", "PM").forEach { item ->
                            val isSelected = selectedAmPm == item
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { selectedAmPm = item }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = item,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                Text(
                    text = "Quick Presets (Fell asleep...)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 15, 30).forEach { mins ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                .clickable {
                                    val newCal = Calendar.getInstance().apply {
                                        timeInMillis = System.currentTimeMillis() - (mins * 60 * 1000L)
                                    }
                                    selectedHour = newCal.get(Calendar.HOUR)
                                    if (selectedHour == 0) selectedHour = 12
                                    selectedMinute = newCal.get(Calendar.MINUTE)
                                    selectedAmPm = if (newCal.get(Calendar.AM_PM) == Calendar.PM) "PM" else "AM"
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${mins}m ago",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(45, 60, 90).forEach { mins ->
                        val label = if (mins >= 60) "${mins / 60}h ${if (mins % 60 > 0) "${mins % 60}m" else ""} ago" else "${mins}m ago"
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                .clickable {
                                    val newCal = Calendar.getInstance().apply {
                                        timeInMillis = System.currentTimeMillis() - (mins * 60 * 1000L)
                                    }
                                    selectedHour = newCal.get(Calendar.HOUR)
                                    if (selectedHour == 0) selectedHour = 12
                                    selectedMinute = newCal.get(Calendar.MINUTE)
                                    selectedAmPm = if (newCal.get(Calendar.AM_PM) == Calendar.PM) "PM" else "AM"
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetCal = Calendar.getInstance()
                    val hr24 = when {
                        selectedAmPm == "PM" && selectedHour < 12 -> selectedHour + 12
                        selectedAmPm == "AM" && selectedHour == 12 -> 0
                        else -> selectedHour
                    }
                    targetCal.set(Calendar.HOUR_OF_DAY, hr24)
                    targetCal.set(Calendar.MINUTE, selectedMinute)
                    targetCal.set(Calendar.SECOND, 0)
                    targetCal.set(Calendar.MILLISECOND, 0)
                    
                    if (targetCal.timeInMillis > System.currentTimeMillis()) {
                        targetCal.add(Calendar.DAY_OF_YEAR, -1)
                    }
                    
                    onConfirm(targetCal.timeInMillis)
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val lines = text.split("\n")
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith("-") || trimmedLine.startsWith("*")) {
                // Bullet list item
                Row(modifier = Modifier.padding(start = 8.dp), verticalAlignment = Alignment.Top) {
                    Text("•", style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = parseBoldText(trimmedLine.substring(1).trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (trimmedLine.startsWith("#")) {
                // Header item
                val level = trimmedLine.takeWhile { it == '#' }.length
                val headerText = trimmedLine.drop(level).trim()
                Text(
                    text = headerText,
                    style = when (level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else if (trimmedLine.isNotEmpty()) {
                // Standard text
                val parsedText = parseBoldText(trimmedLine)
                Text(
                    text = parsedText,
                    style = if (trimmedLine.startsWith("**") && trimmedLine.endsWith("**")) {
                        MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun parseBoldText(text: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        val parts = text.split("**")
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                // Odd indexes are inside asterisks
                withStyle(style = androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                    append(part)
                }
            } else {
                append(part)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditActivityDialog(
    activity: BabyActivity,
    onDismiss: () -> Unit,
    onConfirm: (BabyActivity) -> Unit
) {
    var notes by remember { mutableStateOf(activity.notes) }
    var timestamp by remember { mutableStateOf(activity.timestamp) }

    val initialDetails = remember(activity.detailsJson) {
        try {
            JSONObject(activity.detailsJson)
        } catch (e: Exception) {
            JSONObject()
        }
    }

    // FEEDING states
    var feedingMethod by remember { mutableStateOf(initialDetails.optString("method", "Bottle")) }
    var feedingAmount by remember { mutableStateOf(initialDetails.optDouble("amount", 4.0).toString()) }
    var feedingUnit by remember { mutableStateOf(initialDetails.optString("unit", "oz")) }
    var feedingDuration by remember { mutableStateOf(initialDetails.optInt("durationMinutes", 15).toString()) }

    // SLEEP states
    var sleepStartTime by remember { mutableStateOf(initialDetails.optLong("startTime", activity.timestamp - (initialDetails.optInt("durationMinutes", 60) * 60000L))) }
    var sleepEndTime by remember { mutableStateOf(initialDetails.optLong("endTime", activity.timestamp)) }

    // NAPPY states
    var nappyStatus by remember { mutableStateOf(initialDetails.optString("status", "Wet")) }
    var weeSize by remember { mutableStateOf(initialDetails.optString("weeSize", "Medium")) }
    var poopSize by remember { mutableStateOf(initialDetails.optString("poopSize", "Medium")) }

    // MEDICATION states
    var medName by remember { mutableStateOf(initialDetails.optString("name", "")) }
    var medDosage by remember { mutableStateOf(initialDetails.optString("dosage", "")) }
    var medFrequency by remember { mutableStateOf(initialDetails.optString("frequency", "")) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val customCalendar = remember(timestamp) {
        Calendar.getInstance().apply { timeInMillis = timestamp }
    }

    val datePickerDialog = remember(timestamp) {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selCal = Calendar.getInstance().apply {
                    timeInMillis = timestamp
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                timestamp = selCal.timeInMillis
            },
            customCalendar.get(Calendar.YEAR),
            customCalendar.get(Calendar.MONTH),
            customCalendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    val timePickerDialog = remember(timestamp) {
        android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val selCal = Calendar.getInstance().apply {
                    timeInMillis = timestamp
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                }
                timestamp = selCal.timeInMillis
            },
            customCalendar.get(Calendar.HOUR_OF_DAY),
            customCalendar.get(Calendar.MINUTE),
            false
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Logged Activity", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Activity Type: ${activity.type}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Adjust Date & Time",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )

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
                        Text(sdfDate.format(Date(timestamp)), fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { timePickerDialog.show() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(sdfTime.format(Date(timestamp)), fontSize = 11.sp)
                    }
                }

                when (activity.type) {
                    "FEEDING" -> {
                        Column {
                            Text("Method", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Bottle", "Breast", "Solid").forEach { method ->
                                    val isSel = feedingMethod == method
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow)
                                            .clickable { feedingMethod = method }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = method,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        if (feedingMethod != "Solid") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = feedingAmount,
                                    onValueChange = { feedingAmount = it },
                                    label = { Text("Amount") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1.5f),
                                    shape = RoundedCornerShape(10.dp)
                                )

                                OutlinedTextField(
                                    value = feedingUnit,
                                    onValueChange = { feedingUnit = it },
                                    label = { Text("Unit") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = feedingDuration,
                            onValueChange = { feedingDuration = it },
                            label = { Text("Duration (mins)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
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

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Sleep Timing", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Start:", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                                val sdfDate = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
                                val sdfTime = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                                
                                OutlinedButton(
                                    onClick = { startDatePicker.show() },
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(sdfDate.format(Date(sleepStartTime)), fontSize = 10.sp)
                                }
                                OutlinedButton(
                                    onClick = { startTimePicker.show() },
                                    modifier = Modifier.weight(1.0f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(sdfTime.format(Date(sleepStartTime)), fontSize = 10.sp)
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("End:", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.width(40.dp))
                                val sdfDate = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
                                val sdfTime = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                                
                                OutlinedButton(
                                    onClick = { endDatePicker.show() },
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(sdfDate.format(Date(sleepEndTime)), fontSize = 10.sp)
                                }
                                OutlinedButton(
                                    onClick = { endTimePicker.show() },
                                    modifier = Modifier.weight(1.0f),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(sdfTime.format(Date(sleepEndTime)), fontSize = 10.sp)
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Duration:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text(durationString, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                    "DIAPER", "NAPPY" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Status", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Wet", "Dirty", "Both", "Dry").forEach { status ->
                                    val isSel = nappyStatus == status
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow)
                                            .clickable { nappyStatus = status }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = status,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            if (nappyStatus == "Wet" || nappyStatus == "Both") {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Wee Size", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Small", "Medium", "Big").forEach { size ->
                                        val isSel = weeSize == size
                                        val activeColor = MaterialTheme.colorScheme.primary
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow)
                                                .clickable { weeSize = size }
                                                .padding(vertical = 8.dp),
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
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Poop Size", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("Small", "Medium", "Big").forEach { size ->
                                        val isSel = poopSize == size
                                        val activeColor = MaterialTheme.colorScheme.secondary
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) activeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerLow)
                                                .clickable { poopSize = size }
                                                .padding(vertical = 8.dp),
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
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = medName,
                                onValueChange = { medName = it },
                                label = { Text("Medication Name") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = medDosage,
                                    onValueChange = { medDosage = it },
                                    label = { Text("Dosage") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                OutlinedTextField(
                                    value = medFrequency,
                                    onValueChange = { medFrequency = it },
                                    label = { Text("Frequency") },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalDetails = JSONObject()
                    try {
                        when (activity.type) {
                            "FEEDING" -> {
                                finalDetails.put("method", feedingMethod)
                                finalDetails.put("amount", feedingAmount.toDoubleOrNull() ?: 0.0)
                                finalDetails.put("unit", feedingUnit)
                                finalDetails.put("durationMinutes", feedingDuration.toIntOrNull() ?: 15)
                            }
                            "SLEEP" -> {
                                val diff = sleepEndTime - sleepStartTime
                                val calculatedMinutes = if (diff > 0) (diff / 60000).toInt() else 0
                                finalDetails.put("durationMinutes", calculatedMinutes)
                                finalDetails.put("startTime", sleepStartTime)
                                finalDetails.put("endTime", sleepEndTime)
                            }
                            "DIAPER", "NAPPY" -> {
                                finalDetails.put("status", nappyStatus)
                                finalDetails.put("weeSize", weeSize)
                                finalDetails.put("poopSize", poopSize)
                            }
                            "MEDICATION" -> {
                                finalDetails.put("name", medName.trim().ifEmpty { "Unspecified Medication" })
                                finalDetails.put("dosage", medDosage.trim())
                                finalDetails.put("frequency", medFrequency.trim())
                            }
                        }
                    } catch (e: Exception) {}

                    val finalTimestamp = when (activity.type) {
                        "SLEEP" -> sleepStartTime
                        else -> timestamp
                    }

                    val updated = activity.copy(
                        timestamp = finalTimestamp,
                        notes = notes,
                        detailsJson = finalDetails.toString()
                    )
                    onConfirm(updated)
                }
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

