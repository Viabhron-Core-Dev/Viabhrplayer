package com.example

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HeadlessLibraryEngine(
    private val context: Context,
    private val settingsManager: SettingsManager
) {
    suspend fun scanLibrary(): List<MediaItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaItem>()
        val allowedExtensions = settingsManager.allowedExtensions.map { it.lowercase() }
        
        for (folderUriStr in settingsManager.inclusionFolders) {
            try {
                val folderUri = Uri.parse(folderUriStr)
                val treeDocumentId = DocumentsContract.getTreeDocumentId(folderUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, treeDocumentId)
                
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )
                
                val cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
                val subtitleCandidates = mutableMapOf<String, Uri>()
                val itemsInFolder = mutableListOf<MediaItem>()

                cursor?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val modCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    val sizeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    val mimeCol = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    
                    while (c.moveToNext()) {
                        val docId = c.getString(idCol)
                        val name = c.getString(nameCol) ?: "Unknown"
                        val lastModified = c.getLong(modCol)
                        val size = c.getLong(sizeCol)
                        val mimeType = c.getString(mimeCol) ?: "*/*"
                        
                        val itemUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, docId)
                        val ext = name.substringAfterLast('.', "").lowercase()
                        
                        if (ext in listOf("srt", "vtt", "ass", "ssa")) {
                            val baseName = name.substringBeforeLast('.')
                            subtitleCandidates[baseName] = itemUri
                        } else if (ext in allowedExtensions) {
                            val isVideo = mimeType.startsWith("video/") || ext in listOf("mp4", "mkv", "webm", "ts")
                            val isAudio = mimeType.startsWith("audio/") || ext in listOf("mp3", "m4a", "wav", "flac", "ogg")
                            val isImage = mimeType.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "gif", "webp")
                            
                            itemsInFolder.add(
                                MediaItem(
                                    uri = itemUri,
                                    name = name,
                                    size = size,
                                    dateModified = lastModified,
                                    mimeType = mimeType,
                                    isVideo = isVideo,
                                    isAudio = isAudio,
                                    isImage = isImage
                                )
                            )
                        }
                    }
                }

                // Pair subtitles
                val finalItemsInFolder = itemsInFolder.map { item ->
                    val baseName = item.name.substringBeforeLast('.')
                    if (item.isVideo && subtitleCandidates.containsKey(baseName)) {
                        item.copy(subtitleUri = subtitleCandidates[baseName])
                    } else {
                        item
                    }
                }
                
                result.addAll(finalItemsInFolder)
                LogKeeper.i("LibraryEngine", "Scanned ${finalItemsInFolder.size} items in $folderUriStr")
                
            } catch (e: Exception) {
                LogKeeper.e("LibraryEngine", "Error scanning folder: $folderUriStr", e)
            }
        }
        
        result.sortedByDescending { it.dateModified }
    }
}
