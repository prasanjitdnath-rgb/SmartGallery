package com.example.smartgallery

import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

private val PERMS = arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED)

class MainActivity : ComponentActivity() {
    private val vm: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ctx = LocalContext.current
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            ) {
                Surface(Modifier.fillMaxSize()) {
                    PermissionGate { GalleryScreen(vm) }
                }
            }
        }
    }
}

@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    // Full access (13+) or "selected photos" access (14+) both count.
    fun granted() = PERMS.any { ctx.checkSelfPermission(it) == PERMISSION_GRANTED }
    var ok by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ok = granted()
    }
    LaunchedEffect(Unit) { if (!ok) launcher.launch(PERMS) }

    if (ok) content() else Box(Modifier.fillMaxSize(), Alignment.Center) {
        Button(onClick = { launcher.launch(PERMS) }) { Text("Allow access to photos & videos") }
    }
}
