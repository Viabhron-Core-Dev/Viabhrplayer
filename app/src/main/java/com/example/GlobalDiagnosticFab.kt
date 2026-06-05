package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GlobalDiagnosticFab() {
    var isLoggerEnabled by remember { mutableStateOf(LogKeeper.isEnabled) }
    
    if (!isLoggerEnabled) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    
    var offsetX by remember { mutableFloatStateOf(with(density) { (screenWidth - 80.dp).toPx() }) }
    var offsetY by remember { mutableFloatStateOf(with(density) { (screenHeight - 150.dp).toPx() }) }
    
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        FloatingActionButton(
            onClick = { showDialog = true },
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                },
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ) {
            Icon(Icons.Default.BugReport, contentDescription = "Diagnostic Logs")
        }
    }

    if (showDialog) {
        DiagnosticDialog(
            onDismiss = { showDialog = false }, 
            context = context,
            onLoggerToggled = { enabled -> isLoggerEnabled = enabled }
        )
    }
}

@Composable
private fun DiagnosticDialog(onDismiss: () -> Unit, context: Context, onLoggerToggled: (Boolean) -> Unit) {
    var selectedFilter by remember { mutableStateOf(FilterOption.ALL) }
    var isLoggerEnabled by remember { mutableStateOf(LogKeeper.isEnabled) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Diagnostic Logger", style = MaterialTheme.typography.titleLarge)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable Logging")
                    Switch(
                        checked = isLoggerEnabled,
                        onCheckedChange = { 
                            isLoggerEnabled = it
                            LogKeeper.isEnabled = it
                            onLoggerToggled(it)
                            if (!it) {
                                onDismiss()
                            }
                        }
                    )
                }

                HorizontalDivider()
                
                Text("Time Filter", style = MaterialTheme.typography.titleMedium)
                Column(modifier = Modifier.selectableGroup()) {
                    FilterOption.values().forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .selectable(
                                    selected = (option == selectedFilter),
                                    onClick = { selectedFilter = option },
                                    role = Role.RadioButton
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (option == selectedFilter),
                                onClick = null 
                            )
                            Text(
                                text = option.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 16.dp)
                            )
                        }
                    }
                }
                
                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val logs = getFilteredLogs(selectedFilter)
                        copyToClipboard(context, logs)
                    }) {
                        Text("Copy")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val logs = getFilteredLogs(selectedFilter)
                        downloadLogs(context, logs)
                    }) {
                        Text("Download")
                    }
                }
            }
        }
    }
}

enum class FilterOption(val displayName: String, val hours: Int) {
    ONE_HOUR("Last 1 Hour", 1),
    SIX_HOURS("Last 6 Hours", 6),
    TWELVE_HOURS("Last 12 Hours", 12),
    TWENTY_FOUR_HOURS("Last 24 Hours", 24),
    ALL("All Time", Int.MAX_VALUE)
}

private fun getFilteredLogs(filter: FilterOption): String {
    val allLogs = LogKeeper.getLogs()
    val cutoffTime = if (filter == FilterOption.ALL) 0L else System.currentTimeMillis() - (filter.hours * 60 * 60 * 1000L)
    
    val filtered = allLogs.filter { it.timestamp >= cutoffTime }
    val sb = java.lang.StringBuilder()
    sb.append("--- VIABRPLAY LOGS (${filter.displayName}) ---\n\n")
    filtered.forEach { sb.append(it.toDisplayString()).append("\n") }
    return sb.toString()
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Viabrplay Logs", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun downloadLogs(context: Context, text: String) {
    try {
        val sdf = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US)
        val fileName = "Viabrplay_logs_${sdf.format(Date())}.txt"

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
                    os.write(text.toByteArray())
                }
                Toast.makeText(context, "Downloaded to Downloads folder", Toast.LENGTH_SHORT).show()
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null) {
                val file = File(downloadsDir, fileName)
                file.writeText(text)
                Toast.makeText(context, "Downloaded to ${file.absolutePath}", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to download logs", Toast.LENGTH_SHORT).show()
        LogKeeper.e("GlobalFab", "Failed to download logs", e)
    }
}
