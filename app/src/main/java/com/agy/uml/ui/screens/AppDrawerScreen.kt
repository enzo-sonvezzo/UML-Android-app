package com.agy.uml.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppPackageType(val displayName: String, val badgeColor: Color) {
    ALL("All Types", Color(0xFF6750A4)),
    NATIVE("Native (.deb)", Color(0xFF2E7D32)),
    FLATPAK("Flatpak", Color(0xFF1565C0)),
    APPIMAGE("AppImage", Color(0xFFE65100))
}

data class LinuxAppEntry(
    val id: String,
    val name: String,
    val category: String,
    val type: AppPackageType,
    val icon: ImageVector,
    val launchCommand: String,
    val description: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerScreen(
    onBack: () -> Unit,
    onLaunchApp: (LinuxAppEntry) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AppPackageType.ALL) }

    // Curated catalog with full support for Native deb, Flatpak, and AppImage formats
    val appsList = remember {
        listOf(
            LinuxAppEntry("firefox", "Firefox Browser", "Internet", AppPackageType.NATIVE, Icons.Default.Language, "firefox-esr &", "Fast, extensible web browser"),
            LinuxAppEntry("code", "VS Code OSS", "Development", AppPackageType.FLATPAK, Icons.Default.Code, "flatpak run com.visualstudio.code &", "Code editing redefined"),
            LinuxAppEntry("gimp", "GIMP Image Editor", "Graphics", AppPackageType.FLATPAK, Icons.Default.Palette, "flatpak run org.gimp.GIMP &", "GNU Image Manipulation Program"),
            LinuxAppEntry("vlc", "VLC Media Player", "Media", AppPackageType.NATIVE, Icons.Default.PlayCircle, "vlc &", "Cross-platform multimedia player"),
            LinuxAppEntry("inkscape", "Inkscape Vector", "Graphics", AppPackageType.APPIMAGE, Icons.Default.Draw, "./Inkscape.AppImage &", "Vector graphics editor"),
            LinuxAppEntry("pcmanfm", "PCManFM Files", "System", AppPackageType.NATIVE, Icons.Default.Folder, "pcmanfm &", "Extremely fast file manager"),
            LinuxAppEntry("htop", "Htop Monitor", "System", AppPackageType.NATIVE, Icons.Default.Analytics, "x-terminal-emulator -e htop &", "Interactive system process viewer"),
            LinuxAppEntry("gedit", "Gedit Text Editor", "Utilities", AppPackageType.NATIVE, Icons.Default.EditNote, "gedit &", "Official GNOME text editor"),
            LinuxAppEntry("blender", "Blender 3D", "Graphics", AppPackageType.FLATPAK, Icons.Default.ViewInAr, "flatpak run org.blender.Blender &", "3D creation suite"),
            LinuxAppEntry("obsidian", "Obsidian Notes", "Utilities", AppPackageType.APPIMAGE, Icons.Default.Description, "./Obsidian.AppImage &", "Knowledge base & notes"),
            LinuxAppEntry("docker-ui", "Portainer (Docker)", "Containers", AppPackageType.NATIVE, Icons.Default.Terminal, "docker run -d -p 9000:9000 portainer/portainer-ce &", "Lightweight container management UI"),
            LinuxAppEntry("terminal", "X Terminal", "System", AppPackageType.NATIVE, Icons.Default.Terminal, "x-terminal-emulator &", "Native X11 terminal")
        )
    }

    val filteredApps = remember(searchQuery, selectedType) {
        appsList.filter { app ->
            val matchesSearch = searchQuery.isBlank() ||
                    app.name.contains(searchQuery, ignoreCase = true) ||
                    app.category.contains(searchQuery, ignoreCase = true)
            val matchesType = selectedType == AppPackageType.ALL || app.type == selectedType
            matchesSearch && matchesType
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Linux Applications", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${filteredApps.size} apps available", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search packages, Flatpaks, AppImages...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            // Package Type Filter Chips
            val chipScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(chipScroll),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppPackageType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type.displayName, fontSize = 12.sp) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Grid of Apps
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
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
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = app.icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Surface(
                                    color = app.type.badgeColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = app.type.name,
                                        color = app.type.badgeColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = app.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = app.category,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            if (app.description.isNotBlank()) {
                                Text(
                                    text = app.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 2
                                )
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = app.launchCommand,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(4.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
