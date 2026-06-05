package com.example.ui.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.LogKeeper
import com.example.ffmpeg.FFmpegCommandBuilder
import com.example.ffmpeg.FFmpegEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream

data class VideoEditorState(
    val selectedUris: List<Uri> = emptyList(),
    val currentUriIndex: Int = 0,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val currentPositionMs: Long = 0L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val cropEnabled: Boolean = false,
    val cropRatio: Float = 1f, // 1:1, 16:9, etc.
    val selectedLut: String? = null,
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val error: String? = null,
    val exportSuccess: Boolean = false
)

class VideoEditorViewModel : ViewModel() {
    private val _state = MutableStateFlow(VideoEditorState())
    val state: StateFlow<VideoEditorState> = _state.asStateFlow()

    fun loadVideos(uris: List<Uri>) {
        _state.value = _state.value.copy(
            selectedUris = uris,
            currentUriIndex = 0,
            exportSuccess = false,
            error = null
        )
        // Would extract duration using ExoPlayer or MediaMetadataRetriever
        // Setting mock duration for now
        // _state.value = _state.value.copy(durationMs = ...)
    }

    fun setDuration(duration: Long) {
        _state.value = _state.value.copy(
            durationMs = duration,
            trimEndMs = if (_state.value.trimEndMs == 0L || _state.value.trimEndMs > duration) duration else _state.value.trimEndMs
        )
    }

    fun updatePosition(posMs: Long) {
        _state.value = _state.value.copy(currentPositionMs = posMs)
    }

    fun setTrimRange(startMs: Long, endMs: Long) {
        _state.value = _state.value.copy(trimStartMs = startMs, trimEndMs = endMs)
    }

    fun toggleCrop() {
        _state.value = _state.value.copy(cropEnabled = !_state.value.cropEnabled)
    }

    fun setLut(lut: String?) {
        _state.value = _state.value.copy(selectedLut = lut)
    }

    fun exportVideo(context: Context, outputUri: Uri) {
        if (_state.value.selectedUris.isEmpty()) return
        
        viewModelScope.launch {
            _state.value = _state.value.copy(isExporting = true, exportProgress = 0f, error = null, exportSuccess = false)
            
            try {
                val currentFileUri = _state.value.selectedUris[_state.value.currentUriIndex]
                
                // Copy input to cache if it's a content URI because FFmpeg needs file paths
                val cachedInput = File(context.cacheDir, "temp_video_input.mp4")
                context.contentResolver.openInputStream(currentFileUri)?.use { input ->
                    cachedInput.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                val cachedOutput = File(context.cacheDir, "temp_video_output.mp4")
                if (cachedOutput.exists()) cachedOutput.delete()
                
                val builder = FFmpegCommandBuilder()
                    .addInput(cachedInput.absolutePath)
                
                // Trim
                if (_state.value.trimEndMs > _state.value.trimStartMs) {
                    builder.setTrim(_state.value.trimStartMs, _state.value.trimEndMs)
                }
                
                // Crop
                if (_state.value.cropEnabled) {
                     // Just a mock 1:1 crop from center
                    builder.setCrop(480, 480, 100, 100)
                }
                
                // LUT
                _state.value.selectedLut?.let {
                     builder.setLut(it)
                }
                
                // Codecs
                builder.setVideoCodec("libx264")
                       .setPreset("fast")
                       .setCrf(28)
                       .setAudioCodec("aac")
                       .addOutput(cachedOutput.absolutePath)
                       
                val command = builder.build()
                LogKeeper.d("VideoEditor", "Executing: $command")
                
                // Execute FFmpeg Engine
                val success = FFmpegEngine.execute(command) { progress ->
                     _state.value = _state.value.copy(exportProgress = progress)
                }
                
                if (success && cachedOutput.exists()) {
                    // Copy back to SAF output Uri
                    context.contentResolver.openOutputStream(outputUri)?.use { out ->
                         cachedOutput.inputStream().use { input ->
                             input.copyTo(out)
                         }
                    }
                    _state.value = _state.value.copy(exportSuccess = true)
                } else {
                    _state.value = _state.value.copy(error = "FFmpeg execution failed or output missing.")
                }
                
            } catch (e: Exception) {
                LogKeeper.e("VideoEditorViewModel", "Export failed", e)
                _state.value = _state.value.copy(error = e.localizedMessage)
            } finally {
                _state.value = _state.value.copy(isExporting = false)
            }
        }
    }
    
    fun dismissError() {
         _state.value = _state.value.copy(error = null)
    }

    fun resetSuccess() {
        _state.value = _state.value.copy(exportSuccess = false)
    }
}
