package com.example

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("timestamp", timestamp)
        obj.put("level", level)
        obj.put("tag", tag)
        obj.put("message", message)
        return obj
    }
    
    fun toDisplayString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return "[${sdf.format(Date(timestamp))}] $level/$tag: $message"
    }

    companion object {
        fun fromJson(json: JSONObject): LogEntry {
            return LogEntry(
                timestamp = json.optLong("timestamp", 0L),
                level = json.optString("level", "INFO"),
                tag = json.optString("tag", "Unknown"),
                message = json.optString("message", "")
            )
        }
    }
}

object LogKeeper {
    private const val PREFS_NAME = "log_keeper_prefs"
    private const val KEY_ENABLED = "log_keeper_enabled"
    private const val KEY_LOGS = "log_keeper_logs"
    private const val MAX_LOGS = 500

    private var sharedPreferences: SharedPreferences? = null
    private val logs = mutableListOf<LogEntry>()
    private var isInitialized = false

    var isEnabled: Boolean = true
        set(value) {
            field = value
            sharedPreferences?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
            if (!value) clearLogs()
        }

    fun init(context: Context) {
        if (isInitialized) return
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isEnabled = sharedPreferences?.getBoolean(KEY_ENABLED, true) ?: true
        
        if (isEnabled) {
            loadLogsFromPrefs()
        }

        setupCrashHandler(context)
        isInitialized = true
        i("LogKeeper", "LogKeeper Initialized")
    }

    fun d(tag: String, message: String) = log("DEBUG", tag, message)
    fun i(tag: String, message: String) = log("INFO", tag, message)
    fun w(tag: String, message: String) = log("WARN", tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            message + "\n" + Log.getStackTraceString(throwable)
        } else message
        log("ERROR", tag, fullMessage)
    }

    private fun log(level: String, tag: String, message: String) {
        if (!isEnabled) return
        
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        
        synchronized(logs) {
            logs.add(0, entry)
            if (logs.size > MAX_LOGS) {
                logs.removeAt(logs.size - 1)
            }
        }
        
        saveLogsToPrefs()
        
        when (level) {
            "DEBUG" -> Log.d(tag, message)
            "INFO" -> Log.i(tag, message)
            "WARN" -> Log.w(tag, message)
            "ERROR" -> Log.e(tag, message)
        }
    }

    fun getLogs(): List<LogEntry> {
        synchronized(logs) {
            return logs.toList()
        }
    }

    private fun clearLogs() {
        synchronized(logs) {
            logs.clear()
        }
        sharedPreferences?.edit()?.remove(KEY_LOGS)?.apply()
    }

    private fun saveLogsToPrefs() {
        val array = JSONArray()
        getLogs().forEach { array.put(it.toJson()) }
        sharedPreferences?.edit()?.putString(KEY_LOGS, array.toString())?.apply()
    }

    private fun loadLogsFromPrefs() {
        val logsString = sharedPreferences?.getString(KEY_LOGS, null)
        if (logsString != null) {
            try {
                val array = JSONArray(logsString)
                val loadedLogs = mutableListOf<LogEntry>()
                for (i in 0 until array.length()) {
                    loadedLogs.add(LogEntry.fromJson(array.getJSONObject(i)))
                }
                synchronized(logs) {
                    logs.clear()
                    logs.addAll(loadedLogs)
                }
            } catch (e: Exception) {
                Log.e("LogKeeper", "Failed to deserialize logs", e)
            }
        }
    }

    private fun setupCrashHandler(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(context, thread, throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun handleUncaughtException(context: Context, thread: Thread, throwable: Throwable) {
        if (!isEnabled) return

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()
        val crashMessage = "FATAL CRASH in thread ${thread.name}:\n$stackTrace"
        
        // Log it to memory first so it might be included in the crash dump if we fetch logs inline
        val entry = LogEntry(System.currentTimeMillis(), "FATAL", "CrashHandler", crashMessage)
        synchronized(logs) {
            logs.add(0, entry)
        }

        dumpCrashToFile(context, stackTrace)
    }

    private fun dumpCrashToFile(context: Context, stackTrace: String) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)
            val fileName = "Viabrplay_crash_${sdf.format(Date())}.txt"

            val stringBuilder = StringBuilder()
            stringBuilder.append("--- VIABRPLAY CRASH DUMP ---\n\n")
            stringBuilder.append(stackTrace)
            stringBuilder.append("\n\n--- LAST 50 LOGS ---\n")
            
            val latestLogs = getLogs().take(50)
            latestLogs.forEach {
                stringBuilder.append(it.toDisplayString()).append("\n")
            }
            
            val fileContents = stringBuilder.toString()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        os.write(fileContents.toByteArray())
                    }
                }
            } else {
                // Fallback for pre-Q if WRITE_EXTERNAL_STORAGE is granted, though minSdk 24 requires it.
                // For simplicity, we just use File API. Need permission handling though.
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir != null) {
                    val file = java.io.File(downloadsDir, fileName)
                    file.writeText(fileContents)
                }
            }
        } catch (e: Exception) {
            Log.e("LogKeeper", "Failed to dump crash to Downloads", e)
        }
    }
}
