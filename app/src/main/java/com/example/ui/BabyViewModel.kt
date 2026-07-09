package com.example.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.GeminiClient
import com.example.data.api.GeminiRequest
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiPart
import com.example.data.api.SyncClient
import com.example.data.api.SleepTimerStatus
import com.example.data.local.BabyDatabase
import com.example.data.model.BabyActivity
import com.example.data.repository.BabyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class BabyViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BabyDatabase.getDatabase(application)
    private val repository = BabyRepository(db.babyActivityDao())

    private val prefs: SharedPreferences = application.getSharedPreferences(
        "baby_tracker_prefs",
        Context.MODE_PRIVATE
    )

    // Expose reactive stream of baby activities
    val activities: StateFlow<List<BabyActivity>> = repository.allActiveActivities
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Configuration states
    private val _serverUrl = MutableStateFlow(prefs.getString("server_url", "http://192.168.1.100:3000") ?: "")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _babyName = MutableStateFlow(prefs.getString("baby_name", "Baby") ?: "Baby")
    val babyName: StateFlow<String> = _babyName.asStateFlow()

    private val _babyDob = MutableStateFlow(prefs.getLong("baby_dob", 0L))
    val babyDob: StateFlow<Long> = _babyDob.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(prefs.getLong("last_sync_time", 0L))
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    private val _dbInstanceId = MutableStateFlow(prefs.getString("db_instance_id", "") ?: "")
    val dbInstanceId: StateFlow<String> = _dbInstanceId.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(prefs.getBoolean("is_dark_theme", false))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _themeMode = MutableStateFlow(
        prefs.getString("theme_mode", null) ?: if (prefs.contains("is_dark_theme")) {
            if (prefs.getBoolean("is_dark_theme", false)) "dark" else "light"
        } else {
            "system"
        }
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    // Sync state
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _syncSuccess = MutableStateFlow(false)
    val syncSuccess: StateFlow<Boolean> = _syncSuccess.asStateFlow()

    // AI Recommendations states
    private val _aiRecommendation = MutableStateFlow(prefs.getString("ai_recommendation", "") ?: "")
    val aiRecommendation: StateFlow<String> = _aiRecommendation.asStateFlow()

    private val _isAnalyzingSleep = MutableStateFlow(false)
    val isAnalyzingSleep: StateFlow<Boolean> = _isAnalyzingSleep.asStateFlow()

    private val _analysisError = MutableStateFlow<String?>(null)
    val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

    // Active Sleep Timer state
    private val _sleepTimerStart = MutableStateFlow<Long?>(
        if (prefs.contains("sleep_timer_start")) {
            val savedTime = prefs.getLong("sleep_timer_start", 0L)
            if (savedTime > 0L) savedTime else null
        } else null
    )
    val sleepTimerStart: StateFlow<Long?> = _sleepTimerStart.asStateFlow()

    init {
        // Automatically fetch if we have a server configured
        if (_serverUrl.value.isNotBlank()) {
            triggerSync()
        }
    }

    fun updateServerUrl(url: String) {
        _serverUrl.value = url
        prefs.edit().putString("server_url", url).apply()
        clearSyncTime()
    }

    fun updateBabyName(name: String) {
        _babyName.value = name
        prefs.edit().putString("baby_name", name).apply()
    }

    fun updateDarkTheme(enabled: Boolean) {
        _isDarkTheme.value = enabled
        prefs.edit().putBoolean("is_dark_theme", enabled).apply()
        // Keep themeMode in sync for simple toggle backward compatibility
        updateThemeMode(if (enabled) "dark" else "light")
    }

    fun updateThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode).apply()
        // Keep isDarkTheme in sync for backward compatibility
        if (mode == "dark") {
            _isDarkTheme.value = true
            prefs.edit().putBoolean("is_dark_theme", true).apply()
        } else if (mode == "light") {
            _isDarkTheme.value = false
            prefs.edit().putBoolean("is_dark_theme", false).apply()
        }
    }

    fun updateActivity(activity: BabyActivity) {
        viewModelScope.launch {
            repository.insertActivity(activity.copy(updatedAt = System.currentTimeMillis()))
            
            // Auto-sync after updating if a server is set
            if (_serverUrl.value.isNotBlank()) {
                triggerSync()
            }
        }
    }

    fun clearSyncTime() {
        _lastSyncTime.value = 0L
        _dbInstanceId.value = ""
        prefs.edit()
            .putLong("last_sync_time", 0L)
            .remove("db_instance_id")
            .apply()
    }

    // Start active sleep tracking timer
    fun startSleepTimer() {
        val now = System.currentTimeMillis()
        _sleepTimerStart.value = now
        prefs.edit().putLong("sleep_timer_start", now).apply()

        // Sync start with server
        viewModelScope.launch {
            try {
                val url = _serverUrl.value
                if (url.isNotBlank()) {
                    val api = SyncClient.getApiService(url)
                    api.startSleepTimer(SleepTimerStatus(now))
                }
            } catch (e: Exception) {
                Log.e("BabyViewModel", "Failed to sync started timer to server", e)
            }
        }
    }

    // Stop and save active sleep timer log
    fun stopAndSaveSleepTimer(notes: String = "") {
        val start = _sleepTimerStart.value ?: return
        val end = System.currentTimeMillis()
        val durationMinutes = ((end - start) / 60000).toInt().coerceAtLeast(1)

        val details = JSONObject().apply {
            put("durationMinutes", durationMinutes)
            put("startTime", start)
            put("endTime", end)
        }.toString()

        logActivity(
            type = "SLEEP",
            detailsJson = details,
            notes = notes,
            timestamp = start
        )

        _sleepTimerStart.value = null
        prefs.edit().remove("sleep_timer_start").apply()

        // Sync stop with server (deleting active timer)
        viewModelScope.launch {
            try {
                val url = _serverUrl.value
                if (url.isNotBlank()) {
                    val api = SyncClient.getApiService(url)
                    api.deleteSleepTimer()
                }
            } catch (e: Exception) {
                Log.e("BabyViewModel", "Failed to sync stopped timer to server", e)
            }
        }
    }

    fun cancelSleepTimer() {
        _sleepTimerStart.value = null
        prefs.edit().remove("sleep_timer_start").apply()

        // Sync cancel with server (deleting active timer)
        viewModelScope.launch {
            try {
                val url = _serverUrl.value
                if (url.isNotBlank()) {
                    val api = SyncClient.getApiService(url)
                    api.deleteSleepTimer()
                }
            } catch (e: Exception) {
                Log.e("BabyViewModel", "Failed to sync canceled timer to server", e)
            }
        }
    }

    fun updateSleepTimerStart(newStartTime: Long) {
        _sleepTimerStart.value = newStartTime
        prefs.edit().putLong("sleep_timer_start", newStartTime).apply()

        // Sync updated start with server
        viewModelScope.launch {
            try {
                val url = _serverUrl.value
                if (url.isNotBlank()) {
                    val api = SyncClient.getApiService(url)
                    api.startSleepTimer(SleepTimerStatus(newStartTime))
                }
            } catch (e: Exception) {
                Log.e("BabyViewModel", "Failed to sync updated timer to server", e)
            }
        }
    }

    // Core log activity
    fun logActivity(type: String, detailsJson: String, notes: String, timestamp: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val activity = BabyActivity(
                id = UUID.randomUUID().toString(),
                type = type,
                babyName = _babyName.value,
                timestamp = timestamp,
                detailsJson = detailsJson,
                notes = notes,
                updatedAt = System.currentTimeMillis(),
                isDeleted = false
            )
            repository.insertActivity(activity)
            
            // Auto-sync after adding if a server is set
            if (_serverUrl.value.isNotBlank()) {
                triggerSync()
            }
        }
    }

    fun deleteActivity(id: String) {
        viewModelScope.launch {
            repository.softDeleteActivity(id)
            // Auto-sync deletion
            if (_serverUrl.value.isNotBlank()) {
                triggerSync()
            }
        }
    }

    fun eraseAllData(onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                // 1. Delete all server data if server URL is configured
                val url = _serverUrl.value
                if (url.isNotBlank()) {
                    repository.eraseAllServerActivities(url)
                }
                
                // 2. Delete all local activities
                repository.deleteAllLocalActivities()
                
                // 3. Reset last sync time so synchronization can start clean on new data
                clearSyncTime()
                
                onComplete(true, "All data successfully erased from app and server.")
            } catch (e: Exception) {
                Log.e("BabyViewModel", "Failed to erase data", e)
                onComplete(false, "Failed to delete: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun updateBabyDob(dob: Long) {
        _babyDob.value = dob
        prefs.edit().putLong("baby_dob", dob).apply()
    }

    fun analyzeSleepPattern() {
        val dob = _babyDob.value
        if (dob <= 0L) {
            _analysisError.value = "Please set your baby's Date of Birth in Settings first."
            return
        }

        viewModelScope.launch {
            _isAnalyzingSleep.value = true
            _analysisError.value = null
            try {
                val name = _babyName.value
                val diffMs = System.currentTimeMillis() - dob
                val days = diffMs / (1000 * 60 * 60 * 24)
                val months = days / 30
                val weeks = days / 7
                val ageStr = when {
                    months > 0 -> "$months months"
                    weeks > 0 -> "$weeks weeks"
                    else -> "$days days"
                }

                val sleepLogsList = activities.value
                    .filter { it.type == "SLEEP" && !it.isDeleted }
                    .sortedByDescending { it.timestamp }
                    .take(20)

                val sleepLogsStr = if (sleepLogsList.isEmpty()) {
                    "No sleep history recorded yet."
                } else {
                    sleepLogsList.joinToString("\n") { log ->
                        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(log.timestamp))
                        val dur = try {
                            JSONObject(log.detailsJson).optInt("durationMinutes", 0)
                        } catch (e: Exception) {
                            0
                        }
                        "- Date: $dateStr, Duration: $dur mins, Notes: ${log.notes}"
                    }
                }

                val prompt = """
                    You are a professional pediatric sleep consultant.
                    Analyze the sleep log for a baby named $name who is $ageStr old.
                    
                    Here is their recent sleep log history (newest first):
                    $sleepLogsStr
                    
                    Based on their age of $ageStr and recent sleep patterns:
                    1. Evaluate if their sleep duration and frequency align with healthy targets for their developmental stage (citing global pediatric nap and wake window standards).
                    2. Estimate their average "wake window" (awake time between naps) and identify any overtiredness patterns.
                    3. Formulate clear, actionable recommendations for when they should go down for their next nap(s) or bedtime today.
                    4. Provide 2-3 specific, practical tips for optimizing their sleeping environment or routine.
                    
                    Please structure your response beautifully with Markdown:
                    - Use bold titles for sections like: **Developmental Sleep Evaluation**, **Estimated Wake Window**, **Next Nap Target & Bedtime**, **Practical Sleep Tips**.
                    - Keep it clear, friendly, and practical. No verbose introduction.
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(
                                GeminiPart(text = prompt)
                            )
                        )
                    )
                )

                val response = GeminiClient.service.generateContent(BuildConfig.GEMINI_API_KEY, request)
                val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (!responseText.isNullOrBlank()) {
                    _aiRecommendation.value = responseText
                    prefs.edit().putString("ai_recommendation", responseText).apply()
                } else {
                    _analysisError.value = "Gemini didn't return any analysis. Please try again."
                }
            } catch (e: Exception) {
                Log.e("BabyViewModel", "Failed to analyze sleep pattern", e)
                _analysisError.value = "Failed to run analysis: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                _isAnalyzingSleep.value = false
            }
        }
    }

    fun clearSleepAnalysis() {
        _aiRecommendation.value = ""
        _analysisError.value = null
        prefs.edit().remove("ai_recommendation").apply()
    }

    fun triggerSync() {
        val url = _serverUrl.value
        if (url.isBlank()) return

        viewModelScope.launch {
            _isSyncing.value = true
            _syncError.value = null
            _syncSuccess.value = false
            try {
                val syncResult = repository.syncWithServer(url, _lastSyncTime.value, _dbInstanceId.value)
                _lastSyncTime.value = syncResult.serverSyncTime
                _dbInstanceId.value = syncResult.dbInstanceId
                prefs.edit()
                    .putLong("last_sync_time", syncResult.serverSyncTime)
                    .putString("db_instance_id", syncResult.dbInstanceId)
                    .apply()
                _syncSuccess.value = true

                // Sync sleep timer status from server
                try {
                    val api = SyncClient.getApiService(url)
                    val status = api.getSleepTimer()
                    val serverTime = status.startTime
                    if (serverTime != null && serverTime > 0L) {
                        if (_sleepTimerStart.value != serverTime) {
                            _sleepTimerStart.value = serverTime
                            prefs.edit().putLong("sleep_timer_start", serverTime).apply()
                        }
                    } else {
                        if (_sleepTimerStart.value != null) {
                            _sleepTimerStart.value = null
                            prefs.edit().remove("sleep_timer_start").apply()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("BabyViewModel", "Failed to sync sleep timer status from server", e)
                }
            } catch (e: Exception) {
                Log.e("BabyViewModel", "Sync failed", e)
                _syncError.value = e.localizedMessage ?: "Connection error. Ensure local server is running."
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun importCsv(csvText: String, onComplete: (Boolean, Int, String) -> Unit) {
        viewModelScope.launch {
            try {
                val lines = csvText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.size < 2) {
                    onComplete(false, 0, "Empty or invalid CSV file.")
                    return@launch
                }

                // Helper to clean quotes
                fun cleanVal(v: String): String {
                    return v.trim().removeSurrounding("\"").trim()
                }

                // Parse the header
                // Keep track of quotes when splitting by comma
                fun parseCsvLine(line: String): List<String> {
                    val result = mutableListOf<String>()
                    val currentField = StringBuilder()
                    var insideQuote = false
                    var j = 0
                    while (j < line.length) {
                        val char = line[j]
                        if (char == '"') {
                            if (insideQuote && j + 1 < line.length && line[j + 1] == '"') {
                                currentField.append('"')
                                j++
                            } else {
                                insideQuote = !insideQuote
                            }
                        } else if (char == ',' && !insideQuote) {
                            result.add(currentField.toString())
                            currentField.clear()
                        } else {
                            currentField.append(char)
                        }
                        j++
                    }
                    result.add(currentField.toString())
                    return result
                }

                val headers = parseCsvLine(lines[0]).map { cleanVal(it) }
                val babyNameValue = _babyName.value.ifBlank { "Baby" }
                val importedActivities = mutableListOf<BabyActivity>()

                for (i in 1 until lines.size) {
                    val line = lines[i]
                    val row = parseCsvLine(line).map { cleanVal(it) }
                    if (row.size < headers.size) continue

                    // Build raw row key-value map
                    val rawRow = mutableMapOf<String, String>()
                    headers.forEachIndexed { idx, h ->
                        if (idx < row.size) {
                            rawRow[h] = row[idx]
                        }
                    }

                    // Search for headers case-insensitively
                    var rawType = ""
                    var rawStart = ""
                    var rawEnd = ""
                    var rawDuration = ""
                    var rawNotes = ""
                    var rawAmount = ""
                    var rawUnit = ""
                    var rawFeedType = ""
                    var rawNappyStatus = ""
                    var rawMedName = ""
                    var rawMedDosage = ""

                    rawRow.forEach { (key, valStr) ->
                        val lowerKey = key.lowercase()
                        if (lowerKey == "type" || lowerKey == "category" || lowerKey == "activity") {
                            rawType = valStr
                        } else if (lowerKey.contains("start time") || lowerKey == "start" || lowerKey == "timestamp" || lowerKey == "date" || lowerKey == "time") {
                            if (rawStart.isEmpty()) rawStart = valStr
                        } else if (lowerKey.contains("end time") || lowerKey == "end") {
                            rawEnd = valStr
                        } else if (lowerKey.contains("duration")) {
                            rawDuration = valStr
                        } else if (lowerKey.contains("notes") || lowerKey.contains("note") || lowerKey.contains("comment")) {
                            rawNotes = valStr
                        } else if (lowerKey.contains("amount") || lowerKey.contains("quantity") || lowerKey.contains("volume")) {
                            rawAmount = valStr
                        } else if (lowerKey.contains("unit")) {
                            rawUnit = valStr
                        } else if (lowerKey.contains("feed type") || lowerKey.contains("method") || lowerKey.contains("subtype")) {
                            rawFeedType = valStr
                        } else if (lowerKey.contains("nappy") || lowerKey.contains("diaper") || lowerKey.contains("status") || lowerKey.contains("condition")) {
                            rawNappyStatus = valStr
                        } else if (lowerKey.contains("medicine") || lowerKey.contains("medication") || lowerKey.contains("med name") || lowerKey == "name") {
                            rawMedName = valStr
                        } else if (lowerKey.contains("dosage") || lowerKey.contains("dose")) {
                            rawMedDosage = valStr
                        }
                    }

                    // Map Activity Type
                    val typeLower = rawType.lowercase()
                    var finalType = ""
                    if (typeLower.contains("sleep") || typeLower.contains("nap")) {
                        finalType = "SLEEP"
                    } else if (typeLower.contains("feed") || typeLower.contains("bottle") || typeLower.contains("breast") || typeLower.contains("solid") || typeLower.contains("nursing")) {
                        finalType = "FEEDING"
                    } else if (typeLower.contains("diaper") || typeLower.contains("nappy") || typeLower.contains("change") || typeLower.contains("pee") || typeLower.contains("poo")) {
                        finalType = "DIAPER"
                    } else if (typeLower.contains("med") || typeLower.contains("pills") || typeLower.contains("dose") || typeLower.contains("vacc") || typeLower.contains("temp")) {
                        finalType = "MEDICATION"
                    } else {
                        if (rawFeedType.isNotEmpty()) finalType = "FEEDING"
                        else if (rawNappyStatus.isNotEmpty()) finalType = "DIAPER"
                        else if (rawMedName.isNotEmpty()) finalType = "MEDICATION"
                        else if (rawEnd.isNotEmpty() || rawDuration.isNotEmpty()) finalType = "SLEEP"
                        else continue // skip unrecognized row
                    }

                    // Parse Timestamp
                    var finalTimestamp = System.currentTimeMillis()
                    if (rawStart.isNotEmpty()) {
                        val parsed = parseDateTime(rawStart)
                        if (parsed != null) {
                            finalTimestamp = parsed
                        }
                    }

                    // Parse detailsJson
                    val detailsObj = JSONObject()
                    if (finalType == "SLEEP") {
                        var durationMin = 60
                        if (rawDuration.isNotEmpty()) {
                            if (rawDuration.contains(":")) {
                                val parts = rawDuration.split(":")
                                val hrs = parts.getOrNull(0)?.toIntOrNull() ?: 0
                                val mins = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                durationMin = hrs * 60 + mins
                            } else {
                                durationMin = rawDuration.toIntOrNull() ?: 60
                            }
                        } else if (rawEnd.isNotEmpty() && rawStart.isNotEmpty()) {
                            val startMs = parseDateTime(rawStart)
                            val endMs = parseDateTime(rawEnd)
                            if (startMs != null && endMs != null) {
                                durationMin = ((endMs - startMs) / 60000).toInt()
                            }
                        }
                        detailsObj.put("durationMinutes", if (durationMin > 0) durationMin else 60)
                    } else if (finalType == "FEEDING") {
                        var method = "Bottle"
                        val feedLower = (rawFeedType + rawType).lowercase()
                        if (feedLower.contains("breast") || feedLower.contains("nursing") || feedLower.contains("left") || feedLower.contains("right")) {
                            method = "Breast"
                        } else if (feedLower.contains("solid") || feedLower.contains("food") || feedLower.contains("puree")) {
                            method = "Solid"
                        }
                        val amount = rawAmount.toDoubleOrNull() ?: 4.0
                        var unit = rawUnit.ifEmpty { "oz" }
                        if (rawUnit.isEmpty() && amount > 15.0) {
                            unit = "ml"
                        }
                        detailsObj.put("method", method)
                        detailsObj.put("amount", amount)
                        detailsObj.put("unit", unit)
                        detailsObj.put("durationMinutes", rawDuration.toIntOrNull() ?: 15)
                    } else if (finalType == "DIAPER") {
                        var status = "Wet"
                        val napLower = (rawNappyStatus + rawNotes).lowercase()
                        val isWet = napLower.contains("wet") || napLower.contains("pee") || napLower.contains("urine")
                        val isDirty = napLower.contains("dirty") || napLower.contains("poo") || napLower.contains("stool") || napLower.contains("bowel")
                        if (isWet && isDirty) status = "Both"
                        else if (isDirty) status = "Dirty"
                        else if (napLower.contains("dry")) status = "Dry"
                        detailsObj.put("status", status)
                    } else if (finalType == "MEDICATION") {
                        detailsObj.put("name", rawMedName.ifEmpty { "Unspecified Medication" })
                        detailsObj.put("dosage", rawMedDosage.ifEmpty { "2.5ml" })
                        detailsObj.put("frequency", "Once")
                    }

                    val act = BabyActivity(
                        id = rawRow["id"] ?: UUID.randomUUID().toString(),
                        type = finalType,
                        babyName = rawRow["babyName"] ?: babyNameValue,
                        timestamp = finalTimestamp,
                        detailsJson = detailsObj.toString(),
                        notes = rawNotes.ifEmpty { rawRow["notes"] ?: "" },
                        updatedAt = System.currentTimeMillis(),
                        isDeleted = false
                    )
                    importedActivities.add(act)
                }

                if (importedActivities.isEmpty()) {
                    onComplete(false, 0, "No valid activities found to import. Check your CSV header formats.")
                    return@launch
                }

                repository.insertActivities(importedActivities)
                onComplete(true, importedActivities.size, "Imported ${importedActivities.size} records successfully!")
            } catch (e: Exception) {
                Log.e("BabyViewModel", "CSV Import failed", e)
                onComplete(false, 0, "Error importing: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun exportActivitiesToCsv(): String {
        val sb = java.lang.StringBuilder()
        sb.append("Type,Start Time,End Time,Duration,Notes,Amount,Unit,Feed Type,Nappy,Medicine,Dosage,Temperature\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        activities.value.sortedByDescending { it.timestamp }.forEach { act ->
            var startTimeStr = sdf.format(Date(act.timestamp))
            var endTimeStr = ""
            var durationStr = ""
            var amountStr = ""
            var unitStr = ""
            var feedTypeStr = ""
            var nappyStatusStr = ""
            var medNameStr = ""
            var medDosageStr = ""
            var tempStr = ""
            
            try {
                val details = JSONObject(act.detailsJson)
                when (act.type) {
                    "FEEDING" -> {
                        feedTypeStr = details.optString("method", "")
                        val amountVal = details.optDouble("amount", 0.0)
                        if (amountVal > 0) {
                            amountStr = amountVal.toString()
                        }
                        unitStr = details.optString("unit", "")
                        val durationVal = details.optInt("durationMinutes", 0)
                        if (durationVal > 0) {
                            durationStr = durationVal.toString()
                        }
                    }
                    "SLEEP" -> {
                        val durationVal = details.optInt("durationMinutes", 0)
                        if (durationVal > 0) {
                            durationStr = durationVal.toString()
                        }
                        val sTime = details.optLong("startTime", 0L)
                        val eTime = details.optLong("endTime", 0L)
                        if (sTime > 0L) {
                            startTimeStr = sdf.format(Date(sTime))
                        }
                        if (eTime > 0L) {
                            endTimeStr = sdf.format(Date(eTime))
                        }
                    }
                    "DIAPER", "NAPPY" -> {
                        val status = details.optString("status", "")
                        val weeSize = details.optString("weeSize", "")
                        val poopSize = details.optString("poopSize", "")
                        val parts = mutableListOf<String>()
                        if (weeSize.isNotBlank()) parts.add("Wee:$weeSize")
                        if (poopSize.isNotBlank()) parts.add("Poop:$poopSize")
                        val detailPart = if (parts.isNotEmpty()) " (${parts.joinToString(",")})" else ""
                        nappyStatusStr = "$status$detailPart"
                    }
                    "MEDICATION" -> {
                        medNameStr = details.optString("name", "")
                        medDosageStr = details.optString("dosage", "")
                    }
                    "TEMPERATURE" -> {
                        val value = if (details.has("value")) details.optDouble("value", 0.0) else details.optDouble("temperature", 0.0)
                        var unit = details.optString("unit", "")
                        if (unit.isNotBlank() && !unit.startsWith("°") && (unit == "C" || unit == "F")) {
                            unit = "°$unit"
                        }
                        tempStr = "$value$unit"
                    }
                }
            } catch (e: Exception) {}
            
            fun csvCell(cell: String): String {
                val clean = cell.replace("\"", "\"\"")
                return if (clean.contains(",") || clean.contains("\n") || clean.contains("\"")) {
                    "\"$clean\""
                } else {
                    clean
                }
            }
            
            sb.append(csvCell(act.type)).append(",")
            sb.append(csvCell(startTimeStr)).append(",")
            sb.append(csvCell(endTimeStr)).append(",")
            sb.append(csvCell(durationStr)).append(",")
            sb.append(csvCell(act.notes)).append(",")
            sb.append(csvCell(amountStr)).append(",")
            sb.append(csvCell(unitStr)).append(",")
            sb.append(csvCell(feedTypeStr)).append(",")
            sb.append(csvCell(nappyStatusStr)).append(",")
            sb.append(csvCell(medNameStr)).append(",")
            sb.append(csvCell(medDosageStr)).append(",")
            sb.append(csvCell(tempStr)).append("\n")
        }
        return sb.toString()
    }

    private fun parseDateTime(dateTimeStr: String): Long? {
        val clean = dateTimeStr.trim().replace("\\s+".toRegex(), " ")
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd",
            "M/d/yy, h:mm:ss a",
            "M/d/yy, h:mm a",
            "M/d/yyyy, h:mm:ss a",
            "M/d/yyyy, h:mm a",
            "M/d/yy h:mm:ss a",
            "M/d/yy h:mm a",
            "M/d/yyyy h:mm:ss a",
            "M/d/yyyy h:mm a",
            "dd/MM/yyyy HH:mm:ss",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy h:mm:ss a",
            "dd/MM/yyyy h:mm a",
            "MM/dd/yyyy HH:mm:ss",
            "MM/dd/yyyy HH:mm",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm"
        )
        for (f in formats) {
            try {
                val sdf = SimpleDateFormat(f, Locale.US)
                val date = sdf.parse(clean)
                if (date != null) return date.time
            } catch (e: Exception) {}
        }
        return clean.toLongOrNull()
    }

    // Factory for ViewModel
    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BabyViewModel(application) as T
            }
        }
    }
}
