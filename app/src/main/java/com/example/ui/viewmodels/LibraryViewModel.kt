package com.example.ui.viewmodels

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.HeadlessLibraryEngine
import com.example.LogKeeper
import com.example.MediaItem
import com.example.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val settingsManager = SettingsManager(context)
    private val engine = HeadlessLibraryEngine(context, settingsManager)
    
    private val _items = MutableStateFlow<List<MediaItem>>(emptyList())
    val items: StateFlow<List<MediaItem>> = _items
    
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing
    
    private val _selectedItems = MutableStateFlow<Set<Uri>>(emptySet())
    val selectedItems: StateFlow<Set<Uri>> = _selectedItems

    init {
        loadItems()
    }
    
    fun loadItems() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                LogKeeper.i("LibraryViewModel", "Scanning library")
                _items.value = engine.scanLibrary()
            } catch (e: Exception) {
                LogKeeper.e("LibraryViewModel", "Error scanning library", e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }
    
    fun toggleSelection(uri: Uri) {
        val current = _selectedItems.value.toMutableSet()
        if (current.contains(uri)) {
            current.remove(uri)
        } else {
            current.add(uri)
        }
        _selectedItems.value = current
    }
    
    fun clearSelection() {
        _selectedItems.value = emptySet()
    }
    
    fun selectAll() {
        _selectedItems.value = _items.value.map { it.uri }.toSet()
    }
    
    fun deleteSelected(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            var allSuccess = true
            val toDelete = _selectedItems.value.toList()
            for (uri in toDelete) {
                try {
                    val success = DocumentsContract.deleteDocument(context.contentResolver, uri)
                    if (!success) {
                        allSuccess = false
                    } else {
                        LogKeeper.i("LibraryViewModel", "Deleted document: $uri")
                    }
                } catch (e: Exception) {
                    allSuccess = false
                    LogKeeper.e("LibraryViewModel", "Failed to delete: $uri", e)
                }
            }
            clearSelection()
            loadItems()
            onResult(allSuccess)
        }
    }
    
    fun renameItem(uri: Uri, newName: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val newUri = DocumentsContract.renameDocument(context.contentResolver, uri, newName)
                if (newUri != null) {
                    LogKeeper.i("LibraryViewModel", "Renamed document to: $newName")
                    clearSelection()
                    loadItems()
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                LogKeeper.e("LibraryViewModel", "Failed to rename: $uri", e)
                onResult(false)
            }
        }
    }
}
