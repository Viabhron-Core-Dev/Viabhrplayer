package com.example

import android.os.Bundle
import android.net.Uri
import android.app.PictureInPictureParams
import android.util.Rational
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsManager = SettingsManager(this)
        
        val initialUris = mutableListOf<String>()
        if (intent?.action == android.content.Intent.ACTION_VIEW) {
            intent.data?.let { initialUris.add(it.toString()) }
        } else if (intent?.action == android.content.Intent.ACTION_SEND) {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(android.content.Intent.EXTRA_STREAM)?.let { initialUris.add(it.toString()) }
        } else if (intent?.action == android.content.Intent.ACTION_SEND_MULTIPLE) {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(android.content.Intent.EXTRA_STREAM)?.let { list ->
                initialUris.addAll(list.map { it.toString() })
            }
        }

        var startDest = "library"
        val initialMimeType = intent?.type ?: contentResolver.getType(initialUris.firstOrNull()?.let { Uri.parse(it) } ?: Uri.EMPTY)

        if (initialUris.isNotEmpty()) {
            startDest = if (initialMimeType?.startsWith("image/") == true || initialMimeType?.startsWith("image") == true) {
                val joined = initialUris.joinToString(",") { Uri.encode(it) }
                "photo_editor/$joined"
            } else {
                "player/${Uri.encode(initialUris.first())}"
            }
        }

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        NavHost(
                            navController = navController, 
                            startDestination = startDest,
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable("library") {
                                LibraryScreen(
                                    onNavigateToSettings = { navController.navigate("settings") },
                                    onNavigateToPlayer = { uri -> 
                                        navController.navigate("player/${Uri.encode(uri)}")
                                    },
                                    onNavigateToPhotoEditor = { uris ->
                                        navController.navigate("photo_editor/${Uri.encode(uris.joinToString(","))}")
                                    },
                                    onNavigateToAudioTrimmer = { uri ->
                                        navController.navigate("audio_trimmer/${Uri.encode(uri)}")
                                    },
                                    onNavigateToVideoEditor = { uri ->
                                        navController.navigate("video_editor/${Uri.encode(uri)}")
                                    }
                                )
                            }
                            composable("settings") {
                                SettingsScreen(
                                    settingsManager = settingsManager,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("player/{uri}") { backStackEntry ->
                                val uriString = backStackEntry.arguments?.getString("uri") ?: ""
                                com.example.ui.screens.PlayerScreen(
                                    mediaUriString = uriString,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("photo_editor/{uris}") { backStackEntry ->
                                val urisParam = backStackEntry.arguments?.getString("uris") ?: ""
                                val uris = urisParam.split(",").map { Uri.decode(it) }.filter { it.isNotBlank() }
                                com.example.ui.screens.PhotoEditorScreen(
                                    uris = uris,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("audio_trimmer/{uri}") { backStackEntry ->
                                val uriString = backStackEntry.arguments?.getString("uri") ?: ""
                                com.example.ui.screens.AudioTrimmerScreen(
                                    uriString = Uri.decode(uriString),
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                            composable("video_editor/{uri}") { backStackEntry ->
                                val uriString = backStackEntry.arguments?.getString("uri") ?: ""
                                com.example.ui.screens.VideoEditorScreen(
                                    uris = listOf(Uri.parse(Uri.decode(uriString))),
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                    
                    // The floating diagnostic FAB acting as the global logger observing the app.
                    GlobalDiagnosticFab()
                }
            }
        }
    }

    companion object {
        var isPlayerActive = false
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlayerActive && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }
}

