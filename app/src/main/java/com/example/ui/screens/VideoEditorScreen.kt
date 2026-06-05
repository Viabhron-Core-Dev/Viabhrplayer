@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Filter
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.ui.viewmodels.VideoEditorViewModel
import kotlinx.coroutines.delay

@Composable
fun VideoEditorScreen(
    uris: List<Uri>,
    onBack: () -> Unit,
    viewModel: VideoEditorViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    
    // Create launcher for saving file
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
        if (uri != null) {
            viewModel.exportVideo(context, uri)
        }
    }

    LaunchedEffect(uris) {
        viewModel.loadVideos(uris)
    }

    LaunchedEffect(state.selectedUris, state.currentUriIndex) {
        if (state.selectedUris.isNotEmpty()) {
            val uri = state.selectedUris[state.currentUriIndex]
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying) {
                viewModel.updatePosition(exoPlayer.currentPosition)
            }
            if (state.durationMs == 0L && exoPlayer.duration > 0) {
                viewModel.setDuration(exoPlayer.duration)
            }
            delay(100)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    if (state.exportSuccess) {
        LaunchedEffect(Unit) {
            // Wait a moment so user sees it completed, maybe show a snackbar
            onBack()
            viewModel.resetSuccess()
        }
    }
    
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    @Suppress("DEPRECATION")
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text("Video Editor", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 16.dp))
                IconButton(onClick = {
                    saveLauncher.launch("edited_video.mp4")
                }) {
                    Icon(Icons.Filled.Save, contentDescription = "Export")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Video Player
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Trim", style = MaterialTheme.typography.titleMedium)
                
                @OptIn(ExperimentalMaterial3Api::class)
                @Composable
                fun EditorControls() {
                    if (state.durationMs > 0) {
                        var sliderValues by remember(state.trimStartMs, state.trimEndMs, state.durationMs) {
                            mutableStateOf(
                                (state.trimStartMs.toFloat() / state.durationMs)..
                                (if (state.trimEndMs > 0) state.trimEndMs.toFloat() / state.durationMs else 1f)
                            )
                        }
                        
                        RangeSlider(
                            value = sliderValues,
                            onValueChange = {
                                sliderValues = it
                            },
                            onValueChangeFinished = {
                                viewModel.setTrimRange(
                                    (sliderValues.start * state.durationMs).toLong(),
                                    (sliderValues.endInclusive * state.durationMs).toLong()
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatVideoTime((sliderValues.start * state.durationMs).toLong()))
                            Text(formatVideoTime((sliderValues.endInclusive * state.durationMs).toLong()))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilterChip(
                            selected = state.cropEnabled,
                            onClick = { viewModel.toggleCrop() },
                            label = { Text("Crop 1:1") },
                            leadingIcon = { Icon(Icons.Filled.Crop, "Crop") }
                        )
                        
                        val luts = listOf("None", "lut_retro.cube", "lut_cinematic.cube", "lut_bw.cube")
                        var expanded by remember { mutableStateOf(false) }
                        
                        Box {
                            FilterChip(
                                selected = state.selectedLut != null,
                                onClick = { expanded = true },
                                label = { Text(state.selectedLut ?: "LUT") },
                                leadingIcon = { Icon(Icons.Filled.Filter, "LUT Filter") }
                            )
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                luts.forEach { lut ->
                                    DropdownMenuItem(
                                        text = { Text(lut) },
                                        onClick = { 
                                            viewModel.setLut(if(lut == "None") null else lut)
                                            expanded = false 
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                EditorControls()
            }
        }
        
        if (state.isExporting) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Exporting Video") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(progress = { state.exportProgress })
                        Spacer(Modifier.height(8.dp))
                        Text("Simulating FFmpeg: ${(state.exportProgress * 100).toInt()}%")
                    }
                },
                confirmButton = {}
            )
        }
        
        state.error?.let { err ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissError() },
                title = { Text("Error") },
                text = { Text(err) },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

private fun formatVideoTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d", m, s)
}
