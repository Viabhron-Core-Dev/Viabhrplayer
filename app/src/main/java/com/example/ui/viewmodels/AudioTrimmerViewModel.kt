package com.example.ui.viewmodels

import android.app.Application
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LogKeeper
import com.example.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

class AudioTrimmerViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val settingsManager = SettingsManager(context)

    private val _isTrimming = MutableStateFlow(false)
    val isTrimming: StateFlow<Boolean> = _isTrimming

    private val _trimProgress = MutableStateFlow(0f)
    val trimProgress: StateFlow<Float> = _trimProgress

    fun trimAudio(
        inputUri: Uri,
        startMs: Long,
        endMs: Long,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _isTrimming.value = true
            _trimProgress.value = 0f

            val outputFolder = settingsManager.outputFolderUri
            if (outputFolder == null) {
                _isTrimming.value = false
                onComplete(false, "No output folder set")
                return@launch
            }

            var success = false
            var errorMsg: String? = null

            withContext(Dispatchers.IO) {
                try {
                    val treeDocumentId = DocumentsContract.getTreeDocumentId(outputFolder)
                    val folderUri = DocumentsContract.buildDocumentUriUsingTree(outputFolder, treeDocumentId)
                    val filename = "trimmed_${System.currentTimeMillis()}.mp4"

                    val newFileUri = DocumentsContract.createDocument(
                        context.contentResolver, folderUri, "audio/mp4", filename
                    )

                    if (newFileUri != null) {
                        val pfd = context.contentResolver.openFileDescriptor(newFileUri, "w")
                        if (pfd != null) {
                            val extractor = MediaExtractor()
                            context.contentResolver.openFileDescriptor(inputUri, "r")?.use { inputPfd ->
                                extractor.setDataSource(inputPfd.fileDescriptor)
                            }
                            
                            val trackCount = extractor.trackCount
                            var audioTrackIndex = -1
                            for (i in 0 until trackCount) {
                                val format = extractor.getTrackFormat(i)
                                val mime = format.getString(MediaFormat.KEY_MIME)
                                if (mime?.startsWith("audio/") == true) {
                                    audioTrackIndex = i
                                    break
                                }
                            }

                            if (audioTrackIndex >= 0) {
                                val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                                val format = extractor.getTrackFormat(audioTrackIndex)
                                val muxerTrackIndex = muxer.addTrack(format)
                                muxer.start()

                                extractor.selectTrack(audioTrackIndex)
                                extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                                val bufferSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1024 * 1024)
                                val buffer = ByteBuffer.allocateDirect(bufferSize)
                                val bufferInfo = MediaCodec.BufferInfo()

                                val duration = endMs - startMs
                                var timeTrimmed = 0L

                                while (true) {
                                    val sampleSize = extractor.readSampleData(buffer, 0)
                                    if (sampleSize < 0) break

                                    val sampleTime = extractor.sampleTime
                                    if (sampleTime > endMs * 1000) break

                                    bufferInfo.offset = 0
                                    bufferInfo.size = sampleSize
                                    bufferInfo.flags = extractor.sampleFlags
                                    bufferInfo.presentationTimeUs = sampleTime - (startMs * 1000)

                                    if (bufferInfo.presentationTimeUs < 0) {
                                        bufferInfo.presentationTimeUs = 0
                                    }

                                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                                    
                                    timeTrimmed = (sampleTime / 1000) - startMs
                                    _trimProgress.value = (timeTrimmed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

                                    extractor.advance()
                                }

                                muxer.stop()
                                muxer.release()
                                success = true
                            } else {
                                errorMsg = "No audio track found"
                            }
                            extractor.release()
                            pfd.close()
                        } else {
                            errorMsg = "Could not open output file"
                        }
                    } else {
                        errorMsg = "Could not create output file"
                    }
                } catch (e: Exception) {
                    LogKeeper.e("AudioTrimmer", "Error trimming audio", e)
                    errorMsg = e.message
                }
            }

            _isTrimming.value = false
            onComplete(success, errorMsg)
        }
    }
}
