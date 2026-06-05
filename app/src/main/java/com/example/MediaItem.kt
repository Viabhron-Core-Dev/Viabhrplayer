package com.example

import android.net.Uri

data class MediaItem(
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateModified: Long,
    val mimeType: String,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val isImage: Boolean,
    val subtitleUri: Uri? = null
)
