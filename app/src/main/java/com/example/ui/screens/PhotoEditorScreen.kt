package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ui.viewmodels.PhotoEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    uris: List<String>,
    onNavigateBack: () -> Unit,
    photoEditorViewModel: PhotoEditorViewModel = viewModel()
) {
    val context = LocalContext.current
    val parsedUris = remember { uris.map { Uri.parse(it) } }
    
    val isProcessing by photoEditorViewModel.isProcessing.collectAsState()
    val processingProgress by photoEditorViewModel.processingProgress.collectAsState()

    var format by remember { mutableStateOf("JPEG") }
    var quality by remember { mutableFloatStateOf(80f) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var scaleRatio by remember { mutableFloatStateOf(1f) }
    var watermarkText by remember { mutableStateOf("") }
    
    var showFormatDropdown by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (parsedUris.size > 1) "Batch Edit (${parsedUris.size})" else "Photo Editor") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        photoEditorViewModel.processImages(
                            inputUris = parsedUris,
                            format = format,
                            quality = quality.toInt(),
                            rotation = rotation,
                            watermarkText = watermarkText.takeIf { it.isNotBlank() },
                            scaleRatio = scaleRatio
                        ) { success, error ->
                            Toast.makeText(context, "Completed: $success, Failed: $error", Toast.LENGTH_SHORT).show()
                            if (success > 0) {
                                onNavigateBack()
                            }
                        }
                    }, enabled = !isProcessing) {
                        Icon(Icons.Default.Save, contentDescription = "Process")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (isProcessing) {
                LinearProgressIndicator(
                    progress = { processingProgress },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
                Text("Processing...", modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Preview
            if (parsedUris.isNotEmpty()) {
                LazyRow(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    items(parsedUris) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = "Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(200.dp).padding(4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Output Format", style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(
                expanded = showFormatDropdown,
                onExpandedChange = { showFormatDropdown = !showFormatDropdown }
            ) {
                OutlinedTextField(
                    value = format,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showFormatDropdown) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                )
                ExposedDropdownMenu(
                    expanded = showFormatDropdown,
                    onDismissRequest = { showFormatDropdown = false }
                ) {
                    listOf("JPEG", "PNG", "WEBP").forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                format = option
                                showFormatDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Quality: ${quality.toInt()}%", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = quality,
                onValueChange = { quality = it },
                valueRange = 1f..100f
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Scale: ${(scaleRatio * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = scaleRatio,
                onValueChange = { scaleRatio = it },
                valueRange = 0.1f..2f
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Rotation: ${rotation.toInt()}°", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = rotation,
                onValueChange = { rotation = it },
                valueRange = 0f..360f
            )

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = watermarkText,
                onValueChange = { watermarkText = it },
                label = { Text("Watermark Text (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
