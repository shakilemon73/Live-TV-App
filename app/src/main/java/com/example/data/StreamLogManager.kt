package com.example.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class StreamLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "Playback", "Verification", "Playlist Fetch"
    val targetName: String,
    val url: String,
    val errorMessage: String
)

object StreamLogManager {
    private const val LOG_FILE_NAME = "stream_error_logs.json"
    private const val MAX_LOGS = 500

    private val _logs = MutableStateFlow<List<StreamLog>>(emptyList())
    val logs: StateFlow<List<StreamLog>> = _logs.asStateFlow()

    private var logFile: File? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        logFile = File(context.applicationContext.filesDir, LOG_FILE_NAME)
        loadLogs()
    }

    private fun loadLogs() {
        val file = logFile ?: return
        if (!file.exists()) return
        scope.launch {
            try {
                val jsonString = file.readText()
                val jsonArray = JSONArray(jsonString)
                val loadedList = mutableListOf<StreamLog>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    loadedList.add(
                        StreamLog(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            type = obj.optString("type", "Unknown"),
                            targetName = obj.optString("targetName", "Unknown"),
                            url = obj.optString("url", ""),
                            errorMessage = obj.optString("errorMessage", "")
                        )
                    )
                }
                // Sort by timestamp descending (newest first)
                loadedList.sortByDescending { it.timestamp }
                _logs.value = loadedList
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun logError(type: String, targetName: String, url: String, errorMessage: String) {
        scope.launch {
            val newLog = StreamLog(
                type = type,
                targetName = targetName,
                url = url,
                errorMessage = errorMessage
            )
            val currentList = _logs.value.toMutableList()
            currentList.add(0, newLog)
            
            val trimmedList = if (currentList.size > MAX_LOGS) {
                currentList.take(MAX_LOGS)
            } else {
                currentList
            }
            
            _logs.value = trimmedList
            saveLogs(trimmedList)
        }
    }

    private fun saveLogs(list: List<StreamLog>) {
        val file = logFile ?: return
        try {
            val jsonArray = JSONArray()
            for (log in list) {
                val obj = JSONObject()
                obj.put("id", log.id)
                obj.put("timestamp", log.timestamp)
                obj.put("type", log.type)
                obj.put("targetName", log.targetName)
                obj.put("url", log.url)
                obj.put("errorMessage", log.errorMessage)
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearLogs() {
        scope.launch {
            _logs.value = emptyList()
            val file = logFile ?: return@launch
            try {
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
