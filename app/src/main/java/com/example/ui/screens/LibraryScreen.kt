package com.example.ui.screens

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.MediaItem
import com.example.ThumbnailHelper
import com.example.ui.viewmodels.LibraryViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    libraryViewModel: LibraryViewModel = viewModel(),
    onNavigateToSettings: () -> Unit,
    onNavigateToPlayer: (String) -> Unit,
    onNavigateToPhotoEditor: (List<String>) -> Unit,
    onNavigateToAudioTrimmer: (String) -> Unit,
    onNavigateToVideoEditor: (String) -> Unit
) {
    val items by libraryViewModel.items.collectAsState()
    val isRefreshing by libraryViewModel.isRefreshing.collectAsState()
    val selectedItems by libraryViewModel.selectedItems.collectAsState()
    val context = LocalContext.current

    var itemToRename by remember { mutableStateOf<MediaItem?>(null) }
    var itemToDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (selectedItems.isEmpty()) {
                TopAppBar(
                    title = { Text("Viabrplay Library") },
                    actions = {
                        IconButton(onClick = { libraryViewModel.loadItems() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("${selectedItems.size} Selected") },
                    navigationIcon = {
                        IconButton(onClick = { libraryViewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        if (selectedItems.size == 1) {
                            IconButton(onClick = {
                                itemToRename = items.find { it.uri == selectedItems.first() }
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename")
                            }
                            IconButton(onClick = { 
                                val uriToPlay = selectedItems.first()
                                val selectedItemDetails = items.find { it.uri == uriToPlay }
                                libraryViewModel.clearSelection()
                                if (selectedItemDetails?.isImage == true) {
                                    onNavigateToPhotoEditor(listOf(uriToPlay.toString()))
                                } else {
                                    onNavigateToPlayer(uriToPlay.toString())
                                }
                            }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play/Open")
                            }
                        }
                        
                        val allSelectedAreImages = selectedItems.isNotEmpty() && selectedItems.all { uri ->
                            items.find { it.uri == uri }?.isImage == true
                        }
                        val isSingleAudio = selectedItems.size == 1 && items.find { it.uri == selectedItems.first() }?.isAudio == true
                        val isSingleVideo = selectedItems.size == 1 && items.find { it.uri == selectedItems.first() }?.isVideo == true
                        
                        if (isSingleAudio) {
                            IconButton(onClick = { 
                                val uriToTrim = selectedItems.first()
                                libraryViewModel.clearSelection()
                                onNavigateToAudioTrimmer(uriToTrim.toString())
                            }) {
                                Icon(androidx.compose.material.icons.Icons.Default.ContentCut, contentDescription = "Trim Audio")
                            }
                        }
                        if (isSingleVideo) {
                            IconButton(onClick = { 
                                val uriToEdit = selectedItems.first()
                                libraryViewModel.clearSelection()
                                onNavigateToVideoEditor(uriToEdit.toString())
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Video")
                            }
                        }

                        if (selectedItems.size > 1 && allSelectedAreImages) {
                            IconButton(onClick = { 
                                val uris = selectedItems.map { it.toString() }
                                libraryViewModel.clearSelection()
                                onNavigateToPhotoEditor(uris)
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Batch Edit Images")
                            }
                        }

                        IconButton(onClick = { 
                            Toast.makeText(context, "Playlist feature coming soon", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to Playlist")
                        }
                        IconButton(onClick = { itemToDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                        IconButton(onClick = { libraryViewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (isRefreshing && items.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (items.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.FolderOpen, 
                        contentDescription = null, 
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No media found.", style = MaterialTheme.typography.bodyLarge)
                    Text("Check Settings to include folders.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.uri.toString() }) { item ->
                        val isSelected = selectedItems.contains(item.uri)
                        LibraryItemRow(
                            item = item,
                            isSelected = isSelected,
                            onTap = {
                                if (selectedItems.isNotEmpty()) {
                                    libraryViewModel.toggleSelection(item.uri)
                                } else {
                                    onNavigateToPlayer(item.uri.toString())
                                }
                            },
                            onLongPress = {
                                libraryViewModel.toggleSelection(item.uri)
                            }
                        )
                    }
                }
            }
        }

        if (itemToRename != null) {
            RenameDialog(
                initialName = itemToRename!!.name,
                onDismiss = { itemToRename = null },
                onConfirm = { newName ->
                    libraryViewModel.renameItem(itemToRename!!.uri, newName) { success ->
                        val msg = if (success) "Renamed successfully" else "Rename failed"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                    itemToRename = null
                }
            )
        }

        if (itemToDelete) {
            AlertDialog(
                onDismissRequest = { itemToDelete = false },
                title = { Text("Confirm Deletion") },
                text = { Text("Are you sure you want to delete ${selectedItems.size} item(s)? This action cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        libraryViewModel.deleteSelected { success ->
                            val msg = if (success) "Deleted successfully" else "Failed to delete some items"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                        itemToDelete = false
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryItemRow(
    item: MediaItem,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val formattedDate = remember(item.dateModified) { sdf.format(Date(item.dateModified)) }
    val formattedSize = remember(item.size) { Formatter.formatShortFileSize(context, item.size) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail Box
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncThumbnail(item = item, modifier = Modifier.fillMaxSize())
            
            // Subtitle indicator
            if (item.subtitleUri != null) {
                Icon(
                    imageVector = Icons.Default.Subtitles,
                    contentDescription = "Has subtitles",
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Text details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = " • ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
    }
}

@Suppress("DEPRECATION")
@Composable
fun AsyncThumbnail(item: MediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(item.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    
    LaunchedEffect(item.uri) {
        if (item.isVideo || item.isImage) {
            val bp = ThumbnailHelper.generateThumbnail(context, item.uri, item.isVideo)
            if (bp != null) {
                bitmap = bp
            }
        }
    }
    
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = item.name,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = when {
                    item.isVideo -> Icons.Default.Movie
                    item.isAudio -> Icons.Default.Audiotrack
                    item.isImage -> Icons.Default.Image
                    else -> Icons.Default.InsertDriveFile
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename File") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
