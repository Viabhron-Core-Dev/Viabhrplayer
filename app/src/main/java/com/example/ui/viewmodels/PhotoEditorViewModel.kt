package com.example.ui.viewmodels

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
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
import java.io.InputStream
import java.io.OutputStream

class PhotoEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val settingsManager = SettingsManager(context)

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing

    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress: StateFlow<Float> = _processingProgress

    fun processImages(
        inputUris: List<Uri>,
        format: String, // "JPEG", "PNG", "WEBP"
        quality: Int,
        rotation: Float,
        watermarkText: String?,
        scaleRatio: Float,
        onComplete: (Int, Int) -> Unit
    ) {
        viewModelScope.launch {
            _isProcessing.value = true
            _processingProgress.value = 0f

            var successCount = 0
            var errorCount = 0

            val outputFolder = settingsManager.outputFolderUri
            if (outputFolder == null) {
                LogKeeper.e("PhotoEditor", "No output folder set")
                _isProcessing.value = false
                onComplete(0, inputUris.size)
                return@launch
            }

            withContext(Dispatchers.IO) {
                inputUris.forEachIndexed { index, uri ->
                    try {
                        val treeDocumentId = DocumentsContract.getTreeDocumentId(outputFolder)
                        val folderUri = DocumentsContract.buildDocumentUriUsingTree(outputFolder, treeDocumentId)

                        val mimeType = when (format) {
                            "PNG" -> "image/png"
                            "WEBP" -> "image/webp"
                            else -> "image/jpeg"
                        }
                        
                        val extension = format.lowercase()
                        val filename = "edited_${System.currentTimeMillis()}_$index.$extension"

                        val newFileUri = DocumentsContract.createDocument(context.contentResolver, folderUri, mimeType, filename)
                        
                        if (newFileUri != null) {
                            val inStream: InputStream? = context.contentResolver.openInputStream(uri)
                            val bitmap = BitmapFactory.decodeStream(inStream)
                            inStream?.close()

                            if (bitmap != null) {
                                val outBitmap = applyEdits(bitmap, rotation, watermarkText, scaleRatio)

                                val outStream: OutputStream? = context.contentResolver.openOutputStream(newFileUri)
                                if (outStream != null) {
                                    val compressFormat = when (format) {
                                        "PNG" -> Bitmap.CompressFormat.PNG
                                        "WEBP" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            Bitmap.CompressFormat.WEBP_LOSSY
                                        } else {
                                            @Suppress("DEPRECATION")
                                            Bitmap.CompressFormat.WEBP
                                        }
                                        else -> Bitmap.CompressFormat.JPEG
                                    }
                                    outBitmap.compress(compressFormat, quality, outStream)
                                    outStream.close()
                                    successCount++
                                } else {
                                    errorCount++
                                }
                                if (outBitmap != bitmap) {
                                    outBitmap.recycle()
                                }
                                bitmap.recycle()
                            } else {
                                errorCount++
                            }
                        } else {
                            errorCount++
                        }
                    } catch (e: Exception) {
                        LogKeeper.e("PhotoEditor", "Error processing $uri", e)
                        errorCount++
                    }
                    _processingProgress.value = (index + 1).toFloat() / inputUris.size
                }
            }

            _isProcessing.value = false
            onComplete(successCount, errorCount)
        }
    }

    private fun applyEdits(bitmap: Bitmap, rotation: Float, watermark: String?, scaleRatio: Float): Bitmap {
        var bmp = bitmap
        
        // Scale
        if (scaleRatio != 1f) {
            val width = (bmp.width * scaleRatio).toInt()
            val height = (bmp.height * scaleRatio).toInt()
            if (width > 0 && height > 0) {
                val scaledBmp = Bitmap.createScaledBitmap(bmp, width, height, true)
                bmp = scaledBmp
            }
        }

        // Rotate
        if (rotation != 0f) {
            val matrix = Matrix()
            matrix.postRotate(rotation)
            val rotatedBmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (bmp != bitmap) bmp.recycle()
            bmp = rotatedBmp
        }

        // Watermark
        if (!watermark.isNullOrBlank()) {
            val config = bmp.config ?: Bitmap.Config.ARGB_8888
            val mutableBmp = bmp.copy(config, true)
            val canvas = Canvas(mutableBmp)
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = bmp.width * 0.05f // 5% of width
                isAntiAlias = true
                setShadowLayer(5f, 2f, 2f, Color.BLACK)
            }
            canvas.drawText(watermark, bmp.width * 0.05f, bmp.height * 0.95f, paint)
            if (bmp != bitmap) bmp.recycle()
            bmp = mutableBmp
        }

        return bmp
    }
}
