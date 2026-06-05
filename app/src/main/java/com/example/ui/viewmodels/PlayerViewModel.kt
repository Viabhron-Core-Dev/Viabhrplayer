package com.example.ui.viewmodels

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.LogKeeper
import androidx.core.content.ContextCompat
import com.example.MediaPlaybackService
import com.example.MediaItem as MyMediaItem
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayerViewModel : ViewModel() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var player: Player? = null
        private set
    
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    fun initializeController(context: Context, onReady: (Player) -> Unit) {
        if (player != null) {
            onReady(player!!)
            return
        }
        val sessionToken = SessionToken(context, ComponentName(context, MediaPlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                player = controllerFuture?.get()
                _isReady.value = true
                player?.let { onReady(it) }
                LogKeeper.i("PlayerViewModel", "MediaController initialized")
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun playMedia(context: Context, myMediaItem: MyMediaItem) {
        val player = this.player ?: return
        
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(myMediaItem.uri)
            .setMediaId(myMediaItem.uri.toString())
            
        // Adding subtitles if present
        myMediaItem.subtitleUri?.let { subUri ->
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subUri)
                .setMimeType("application/x-subrip") // Basic assumption, true parsing might need exoplayer specific mime
                .setLanguage("en")
                .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                .build()
            mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
        }

        player.setMediaItem(mediaItemBuilder.build())
        player.prepare()
        player.play()
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        player = null
        _isReady.value = false
    }
}
