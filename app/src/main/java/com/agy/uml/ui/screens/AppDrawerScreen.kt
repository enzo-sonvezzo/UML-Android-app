package com.agy.uml.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LinuxAppEntry(
    val id: String,
    val name: String,
    val category: String,
    val type: String, // "Native", "Flatpak", "AppImage"
    val icon: ImageVector,
    val launchCommand: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerScreen(
    onBack: () -> Unit,
    onLaunchApp: (LinuxAppEntry) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val sampleApps = remember {
        listOf(
            LinuxAppEntry("firefox", "Firefox Browser", "Internet", "Native", Icons.Default.Language, "firefox-esr &"),
            LinuxAppEntry("code", "VS Code (OSS)", "Development", "Flatpak", Icons.Default.Code, "flatpak run com.visualstudio.code &"),
            LinuxAppEntry("gimp", "GIMP Image Editor", "Graphics", "Flatpak", Icons.Default.Palette, "flatpak run org.gimp.GIMP &"),
            LinuxAppEntry("vlc", "VLC Media Player", "Media", "Native", Icons.Default.PlayCircle, "vlc &"),
            LinuxAppEntry("inkscape", "Inkscape Vector", "Graphics", "AppImage", Icons.Default.Draw, "./Inkscape.AppImage &"),
            LinuxAppEntry("gedit", "Text Editor", "Utilities", "Native", Icons.Default.EditNote, "gedit &"),
            LinuxAppEntry("htop", "Htop Process Viewer", "System", "Native", Icons.Default.Analytics, "x-terminal-emulator -e htop &"),
            LinuxAppEntry("filemanager", "PCManFM Files", "System", "Native", Icons.Default.Folder, "pcmanfm &")
        )
    }

    val filteredApps = remember(searchQuery) {
        if (searchQuery.isBlank()) sampleApps
        else sampleApps.filter { it.name.contains(searchQuery, ignoreCase = true) || it.category.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Linux Applications", fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed apps, Flatpaks...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredApps) { app ->
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLaunchApp(app) }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = app.icon,
                                        contentDescription = app.name,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Text(
                                text = app.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = app.type,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
