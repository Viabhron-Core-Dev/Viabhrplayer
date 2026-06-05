@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.ui.viewmodels.AudioTrimmerViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTrimmerScreen(
    uriString: String,
    onNavigateBack: () -> Unit,
    audioTrimmerViewModel: AudioTrimmerViewModel = viewModel()
) {
    val context = LocalContext.current
    val uri = Uri.parse(uriString)
    
    val isTrimming by audioTrimmerViewModel.isTrimming.collectAsState()
    val trimProgress by audioTrimmerViewModel.trimProgress.collectAsState()

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }

    var trimRange by remember { mutableStateOf(0f..100f) }

    DisposableEffect(Unit) {
        val exoPlayer = ExoPlayer.Builder(context).build()
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0)
                    if (trimRange.endInclusive == 100f && duration > 0) {
                        trimRange = 0f..duration.toFloat()
                    }
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        })
        
        player = exoPlayer
        
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(player, isPlaying) {
        while (isPlaying) {
            player?.let {
                currentPosition = it.currentPosition
                if (currentPosition > trimRange.endInclusive) {
                    it.pause()
                    it.seekTo(trimRange.start.toLong())
                }
            }
            delay(50)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio Trimmer") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val startMs = trimRange.start.toLong()
                            val endMs = trimRange.endInclusive.toLong()
                            
                            audioTrimmerViewModel.trimAudio(uri, startMs, endMs) { success, error ->
                                if (success) {
                                    Toast.makeText(context, "Audio trimmed successfully", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                } else {
                                    Toast.makeText(context, "Trimming failed: $error", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isTrimming && duration > 0
                    ) {
                        Icon(Icons.Default.ContentCut, contentDescription = "Trim")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isTrimming) {
                LinearProgressIndicator(
                    progress = { trimProgress },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
                Text("Trimming audio...")
            } else {
                Text(
                    text = "Trim Range: ${formatTime(trimRange.start.toLong())} - ${formatTime(trimRange.endInclusive.toLong())}",
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (duration > 0) {
                    RangeSlider(
                        value = trimRange,
                        onValueChange = { range ->
                            trimRange = range
                            if (!isPlaying) {
                                player?.seekTo(range.start.toLong())
                                currentPosition = range.start.toLong()
                            }
                        },
                        valueRange = 0f..duration.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Current Position: ${formatTime(currentPosition)}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (isPlaying) {
                            player?.pause()
                        } else {
                            if (currentPosition < trimRange.start || currentPosition >= trimRange.endInclusive) {
                                player?.seekTo(trimRange.start.toLong())
                            }
                            player?.play()
                        }
                    }
                ) {
                    Text(if (isPlaying) "Pause" else "Play Scrub")
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    return String.format("%02d:%02d", min, sec)
}
