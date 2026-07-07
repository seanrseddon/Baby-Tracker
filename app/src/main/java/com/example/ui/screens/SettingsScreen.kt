package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.BabyViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BabyViewModel,
    onNavigateBack: () -> Unit
) {
    val babyName by viewModel.babyName.collectAsState()
    val babyDob by viewModel.babyDob.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncError by viewModel.syncError.collectAsState()
    val syncSuccess by viewModel.syncSuccess.collectAsState()

    var tempBabyName by remember { mutableStateOf(babyName) }
    var tempBabyDob by remember(babyDob) { mutableStateOf(babyDob) }
    var tempServerUrl by remember { mutableStateOf(serverUrl) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val calendar = Calendar.getInstance()
    if (tempBabyDob > 0) {
        calendar.timeInMillis = tempBabyDob
    }

    val datePickerDialog = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val selCal = Calendar.getInstance()
            selCal.set(Calendar.YEAR, year)
            selCal.set(Calendar.MONTH, month)
            selCal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            tempBabyDob = selCal.timeInMillis
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val scrollState = rememberScrollState()

    val formattedSyncTime = if (lastSyncTime > 0) {
        val formatter = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault())
        formatter.format(Date(lastSyncTime))
    } else {
        "Never Synced"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuration & Sync", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
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
            // Section 1: Baby Profile
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Baby Profile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = tempBabyName,
                        onValueChange = { tempBabyName = it },
                        label = { Text("Baby's Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("baby_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Face, contentDescription = null)
                        }
                    )

                    val formattedDob = if (tempBabyDob > 0) {
                        val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                        sdf.format(Date(tempBabyDob))
                    } else {
                        "Not set (Tap to choose)"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { datePickerDialog.show() }
                            .testTag("baby_dob_container")
                    ) {
                        OutlinedTextField(
                            value = formattedDob,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Date of Birth") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.DateRange, contentDescription = null)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Button(
                        onClick = { 
                            viewModel.updateBabyName(tempBabyName)
                            viewModel.updateBabyDob(tempBabyDob)
                        },
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("save_baby_profile_button")
                    ) {
                        Text("Save Profile")
                    }
                }
            }

            // Section 2: Shared Sync Server (Docker / Unraid)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Shared Synced Server",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "Configure your self-hosted Docker / Unraid server address here to synchronize feedings, sleep, and nappies in real-time with your partner.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = tempServerUrl,
                        onValueChange = { tempServerUrl = it },
                        label = { Text("Server Base URL") },
                        placeholder = { Text("e.g. http://192.168.1.100:3000") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("server_url_input"),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Dns, contentDescription = null)
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Reset sync log timer
                        TextButton(
                            onClick = { viewModel.clearSyncTime() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Reset Sync Log")
                        }

                        Button(
                            onClick = {
                                viewModel.updateServerUrl(tempServerUrl)
                                viewModel.triggerSync()
                            },
                            modifier = Modifier.testTag("save_server_and_sync_button")
                        ) {
                            Text("Save & Sync")
                        }
                    }

                    HorizontalDivider()

                    // Sync Status Metadata
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Last Synced:", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                            Text(formattedSyncTime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        if (isSyncing) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connecting to server...", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        } else if (syncSuccess) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Complete", fontSize = 13.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                            }
                        } else if (syncError != null) {
                            Text(
                                text = "Sync Failed: $syncError",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Section 3: Docker & Unraid Setup Instructions (Real local privacy benefit)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hosting Locally on Unraid / Docker",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "1. Copy the docker-compose.yml and the backend folder from the app's root repository to your Unraid appdata folder.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "2. Run `docker-compose up -d` on your terminal or add it as a custom Docker template in Unraid's UI pointing to port 3000.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "3. Check the local web dashboard at http://<your-unraid-ip>:3000 on your home network.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "4. Keep your family's logs 100% private. No external cloud database required!",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
