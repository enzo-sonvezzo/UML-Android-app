package com.agy.uml.ui.screens

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphicalScreen(
    onBack: () -> Unit,
    onSendKey: (String) -> Unit
) {
    var isTouchpadMode by remember { mutableStateOf(false) }
    var selectedResolution by remember { mutableStateOf("1920x1080") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Linux Graphical Display", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isTouchpadMode = !isTouchpadMode }) {
                        Icon(
                            imageVector = if (isTouchpadMode) Icons.Default.TouchApp else Icons.Default.Mouse,
                            contentDescription = "Input Mode",
                            tint = if (isTouchpadMode) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                    IconButton(onClick = { onSendKey("\u001b") }) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Toggle Fullscreen")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            // Android SurfaceView for Wayland/X11 frame buffer rendering
            AndroidView(
                factory = { context ->
                    SurfaceView(context).apply {
                        // In Phase 4, the Wayland/X11 native bridge binds to this SurfaceHolder
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Info banner when display server is awaiting connection
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DesktopWindows,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Wayland / X11 Display Ready",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Apps launched from the App Library or shell stream here at $selectedResolution (Mesa llvmpipe).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            // Floating Desktop Utility Controls (Super / Alt-Tab / Keyboard)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(onClick = { onSendKey("Super") }) {
                        Text("Super", fontSize = 12.sp)
                    }
                    FilledTonalButton(onClick = { onSendKey("AltTab") }) {
                        Text("Alt+Tab", fontSize = 12.sp)
                    }
                    FilledTonalButton(onClick = { onSendKey("CtrlAltT") }) {
                        Text("Terminal", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
