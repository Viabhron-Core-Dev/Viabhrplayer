package com.example

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("viabrplay_settings", Context.MODE_PRIVATE)

    var outputFolderUri: Uri?
        get() = prefs.getString("output_folder_uri", null)?.let { Uri.parse(it) }
        set(value) {
            prefs.edit().putString("output_folder_uri", value?.toString()).apply()
        }

    var inclusionFolders: Set<String>
        get() = prefs.getStringSet("inclusion_folders", setOf()) ?: setOf()
        set(value) {
            prefs.edit().putStringSet("inclusion_folders", value).apply()
        }

    var allowedExtensions: Set<String>
        get() = prefs.getStringSet("allowed_extensions", setOf("mp4", "mkv", "mp3", "jpg", "png", "gif", "webp")) ?: setOf()
        set(value) {
            prefs.edit().putStringSet("allowed_extensions", value).apply()
        }
}
