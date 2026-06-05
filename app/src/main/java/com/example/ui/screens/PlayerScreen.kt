package com.example.ui.screens

import android.net.Uri
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.MainActivity
import com.example.ui.viewmodels.PlayerViewModel
import com.example.MediaItem as MyMediaItem
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    mediaUriString: String,
    playerViewModel: PlayerViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isReady by playerViewModel.isReady.collectAsState()

    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var overlayText by remember { mutableStateOf<String?>(null) }
    var showOverlayText by remember { mutableStateOf(false) }

    LaunchedEffect(overlayText) {
        if (overlayText != null) {
            showOverlayText = true
            delay(1000)
            showOverlayText = false
            overlayText = null
        }
    }

    DisposableEffect(Unit) {
        MainActivity.isPlayerActive = true
        (context as? Activity)?.window?.let { window ->
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        playerViewModel.initializeController(context) { player ->
            // Minimal mock of MyMediaItem for direct play
            val mediaItem = MyMediaItem(
                uri = Uri.parse(mediaUriString),
                name = "Playing Media",
                size = 0L,
                dateModified = 0L,
                mimeType = "*/*",
                isVideo = true,
                isAudio = false,
                isImage = false
            )
            playerViewModel.playMedia(context, mediaItem)
        }
        
        onDispose {
            MainActivity.isPlayerActive = false
            (context as? Activity)?.window?.let { window ->
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    show(WindowInsetsCompat.Type.systemBars())
                }
            }
            playerViewModel.player?.pause()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!isReady) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = playerViewModel.player
                        playerViewRef = this
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
            val window = (context as? Activity)?.window
            
            var dragMode by remember { mutableIntStateOf(0) } // 0=None, 1=Brightness, 2=Volume, 3=Seek
            var startX by remember { mutableFloatStateOf(0f) }
            var initialVolume by remember { mutableFloatStateOf(0f) }
            var initialBrightness by remember { mutableFloatStateOf(0f) }
            var seekPosition by remember { mutableLongStateOf(0L) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(playerViewModel.player) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                playerViewModel.player?.let { p ->
                                    val isLeft = offset.x < size.width / 2
                                    if (isLeft) {
                                        val newPos = (p.currentPosition - 10000).coerceAtLeast(0)
                                        p.seekTo(newPos)
                                        overlayText = "⏪ 10s"
                                    } else {
                                        val newPos = (p.currentPosition + 10000).coerceAtMost(p.duration)
                                        p.seekTo(newPos)
                                        overlayText = "10s ⏩"
                                    }
                                }
                            },
                            onTap = {
                                playerViewRef?.let { pv ->
                                    if (pv.isControllerFullyVisible) pv.hideController() else pv.showController()
                                }
                            }
                        )
                    }
                    .pointerInput(playerViewModel.player) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragMode = 0
                                startX = offset.x
                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                initialVolume = currentVol.toFloat() / max
                                
                                initialBrightness = window?.attributes?.screenBrightness ?: 0.5f
                                if (initialBrightness < 0) initialBrightness = 0.5f
                                
                                seekPosition = playerViewModel.player?.currentPosition ?: 0L
                            },
                            onDragEnd = {
                                if (dragMode == 3) {
                                    playerViewModel.player?.seekTo(seekPosition)
                                }
                                dragMode = 0
                            },
                            onDragCancel = { dragMode = 0 },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (dragMode == 0) {
                                    if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                        dragMode = 3
                                    } else {
                                        dragMode = if (startX < size.width / 2) 1 else 2
                                    }
                                }

                                when (dragMode) {
                                    1 -> { // Brightness
                                        if (window != null) {
                                            val delta = -dragAmount.y / size.height
                                            initialBrightness = (initialBrightness + delta * 2f).coerceIn(0f, 1f)
                                            val lp = window.attributes
                                            lp.screenBrightness = initialBrightness
                                            window.attributes = lp
                                            overlayText = "Brightness: ${(initialBrightness * 100).toInt()}%"
                                        }
                                    }
                                    2 -> { // Volume
                                        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        val delta = -dragAmount.y / size.height
                                        initialVolume = (initialVolume + delta * 2f).coerceIn(0f, 1f)
                                        val newVol = (initialVolume * max).toInt()
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                        overlayText = "Volume: ${(initialVolume * 100).toInt()}%"
                                    }
                                    3 -> { // Seek
                                        val duration = playerViewModel.player?.duration ?: 0L
                                        val deltaSec = (dragAmount.x / size.width) * 120 * 1000 // 2 mins full swipe
                                        seekPosition = (seekPosition + deltaSec.toLong()).coerceIn(0L, duration)
                                        
                                        val totalSeconds = seekPosition / 1000
                                        val min = totalSeconds / 60
                                        val sec = totalSeconds % 60
                                        
                                        val durSec = duration / 1000
                                        val dMin = durSec / 60
                                        val dSec = durSec % 60
                                        
                                        val minStr = min.toString().padStart(2, '0')
                                        val secStr = sec.toString().padStart(2, '0')
                                        val dMinStr = dMin.toString().padStart(2, '0')
                                        val dSecStr = dSec.toString().padStart(2, '0')
                                        
                                        overlayText = "Seek: $minStr:$secStr / $dMinStr:$dSecStr"
                                        playerViewModel.player?.seekTo(seekPosition)
                                    }
                                }
                            }
                        )
                    }
            )
            
            if (showOverlayText && overlayText != null) {
                Box(
                    modifier = Modifier.align(Alignment.Center)
                                       .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
                                       .padding(16.dp)
                ) {
                    Text(text = overlayText!!, color = Color.White, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
