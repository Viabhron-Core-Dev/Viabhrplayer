package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Output
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.LogKeeper
import com.example.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settingsManager: SettingsManager, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var outputUri by remember { mutableStateOf(settingsManager.outputFolderUri) }
    var inclusionFolders by remember { mutableStateOf(settingsManager.inclusionFolders) }

    val outputFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            settingsManager.outputFolderUri = uri
            outputUri = uri
            LogKeeper.i("Settings", "Output folder set to: $uri")
        }
    }

    val inclusionFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            val newSet = inclusionFolders.toMutableSet().apply { add(uri.toString()) }
            settingsManager.inclusionFolders = newSet
            inclusionFolders = newSet
            LogKeeper.i("Settings", "Added inclusion folder: $uri")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                ListItem(
                    headlineContent = { Text("Output Folder") },
                    supportingContent = { 
                        Text(outputUri?.toString() ?: "None selected") 
                    },
                    leadingContent = { Icon(Icons.Default.Output, contentDescription = null) },
                    modifier = Modifier.clickable { outputFolderLauncher.launch(null) }
                )
                HorizontalDivider()
            }
            
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Inclusion Folders", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Button(onClick = { inclusionFolderLauncher.launch(null) }) {
                        Text("Add Folder")
                    }
                }
            }

            items(inclusionFolders.size) { index ->
                val uriString = inclusionFolders.elementAt(index)
                ListItem(
                    headlineContent = { Text(Uri.parse(uriString).lastPathSegment ?: uriString) },
                    supportingContent = { Text(uriString) },
                    leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = {
                            val newSet = inclusionFolders.toMutableSet().apply { remove(uriString) }
                            settingsManager.inclusionFolders = newSet
                            inclusionFolders = newSet
                            LogKeeper.i("Settings", "Removed inclusion folder: $uriString")
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove")
                        }
                    }
                )
            }
        }
    }
}
