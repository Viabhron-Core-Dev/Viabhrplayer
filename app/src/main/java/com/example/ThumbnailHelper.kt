package com.example

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thumbnail generator logic handling backward compatibility.
 * Leverages native ContentResolver caching primarily.
 */
object ThumbnailHelper {

    suspend fun generateThumbnail(context: Context, uri: Uri, isVideo: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android Q and above, use the native ContentResolver method to get a cached thumbnail
                return@withContext context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
            } else {
                // Fallback for older APIs (though minSdk is usually 24)
                if (isVideo) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            retriever.setDataSource(pfd.fileDescriptor)
                            return@withContext retriever.frameAtTime
                        }
                    } finally {
                        retriever.release()
                    }
                }
            }
        } catch (e: Exception) {
            LogKeeper.e("ThumbnailHelper", "Failed to generate thumbnail for $uri", e)
        }
        null
    }
}
