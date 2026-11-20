package com.agy.uml

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.agy.uml.model.UmlConfiguration
import com.agy.uml.model.UmlStatus
import com.agy.uml.service.AssetInstaller
import com.agy.uml.service.UmlSessionService
import com.agy.uml.ui.screens.*
import com.agy.uml.ui.theme.UmlAndroidTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class ScreenTab {
    DASHBOARD, TERMINAL, GRAPHICAL, APPS
}

class MainActivity : ComponentActivity() {

    private var sessionService: UmlSessionService? = null
    private var isBound by mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as UmlSessionService.LocalBinder
            sessionService = localBinder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sessionService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val serviceIntent = Intent(this, UmlSessionService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            AssetInstaller.ensureBinariesInstalled(this@MainActivity)
        }

        setContent {
            UmlAndroidTheme {
                val service = sessionService
                val status by (service?.status?.collectAsState() ?: remember { mutableStateOf(UmlStatus.Stopped) })
                val terminalOutputFlow = service?.terminalOutput

                var currentTab by remember { mutableStateOf(ScreenTab.DASHBOARD) }
                var allocatedMemoryMb by remember { mutableIntStateOf(2048) }
                var rootfsPath by remember { mutableStateOf<String?>(null) }
                val coroutineScope = rememberCoroutineScope()

                // Check standard paths for debian-docker.ext4
                LaunchedEffect(Unit) {
                    val candidatePaths = listOf(
                        File(filesDir, "debian-docker.ext4"),
                        File(getExternalFilesDir(null), "debian-docker.ext4"),
                        File("/storage/emulated/0/Download/Termux/uml-android/debian-docker.ext4"),
                        File("/storage/emulated/0/Download/debian-docker.ext4")
                    )
                    val found = candidatePaths.firstOrNull { it.exists() && it.length() > 0 }
                    if (found != null) {
                        rootfsPath = found.absolutePath
                    }
                }

                // File picker launcher for .ext4 images
                val rootfsPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    uri?.let { selectedUri ->
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val targetFile = File(filesDir, "debian-docker.ext4")
                                contentResolver.openInputStream(selectedUri)?.use { input ->
                                    FileOutputStream(targetFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    rootfsPath = targetFile.absolutePath
                                    Toast.makeText(this@MainActivity, "Disk image imported successfully!", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MainActivity, "Failed to import image: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentTab == ScreenTab.DASHBOARD,
                                onClick = { currentTab = ScreenTab.DASHBOARD },
                                icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                                label = { Text("Dashboard") }
                            )
                            NavigationBarItem(
                                selected = currentTab == ScreenTab.TERMINAL,
                                onClick = { currentTab = ScreenTab.TERMINAL },
                                icon = { Icon(Icons.Default.Terminal, contentDescription = "Terminal") },
                                label = { Text("Terminal") }
                            )
                            NavigationBarItem(
                                selected = currentTab == ScreenTab.GRAPHICAL,
                                onClick = { currentTab = ScreenTab.GRAPHICAL },
                                icon = { Icon(Icons.Default.DesktopWindows, contentDescription = "Display") },
                                label = { Text("Display") }
                            )
                            NavigationBarItem(
                                selected = currentTab == ScreenTab.APPS,
                                onClick = { currentTab = ScreenTab.APPS },
                                icon = { Icon(Icons.Default.Apps, contentDescription = "Apps") },
                                label = { Text("Apps") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (currentTab) {
                            ScreenTab.DASHBOARD -> {
                                DashboardScreen(
                                    status = status,
                                    allocatedMemoryMb = allocatedMemoryMb,
                                    rootfsPath = rootfsPath,
                                    onMemoryChange = { allocatedMemoryMb = it },
                                    onSelectRootfs = {
                                        rootfsPickerLauncher.launch(arrayOf("*/*"))
                                    },
                                    onStartKernel = {
                                        val filesDir = filesDir
                                        val libDir = File(applicationInfo.nativeLibraryDir)

                                        // Resolve binaries prioritizing SELinux-executable nativeLibraryDir
                                        val kernelFile = File(libDir, "liblinux.so").takeIf { it.exists() } ?: File(filesDir, "linux")
                                        val stubExeFile = File(libDir, "libstub_exe.so").takeIf { it.exists() } ?: File(filesDir, "stub_exe")
                                        val umnetFile = File(libDir, "libumnet.so").takeIf { it.exists() } ?: File(filesDir, "umnet")
                                        val passtFile = File(libDir, "libpasst.so").takeIf { it.exists() } ?: File(filesDir, "passt")
                                        val targetRootfs = rootfsPath ?: File(filesDir, "debian-docker.ext4").absolutePath

                                        val config = UmlConfiguration(
                                            memoryMb = allocatedMemoryMb,
                                            rootfsPath = targetRootfs,
                                            stubExePath = stubExeFile.absolutePath,
                                            kernelPath = kernelFile.absolutePath,
                                            umnetPath = if (umnetFile.exists()) umnetFile.absolutePath else null,
                                            passtPath = if (passtFile.exists()) passtFile.absolutePath else null,
                                            enableNetworking = true
                                        )
                                        sessionService?.startSession(config)
                                    },
                                    onStopKernel = { sessionService?.stopSession() },
                                    onOpenTerminal = { currentTab = ScreenTab.TERMINAL },
                                    onOpenGraphical = { currentTab = ScreenTab.GRAPHICAL }
                                )
                            }
                            ScreenTab.TERMINAL -> {
                                if (terminalOutputFlow != null) {
                                    TerminalScreen(
                                        outputFlow = terminalOutputFlow,
                                        onSendInput = { sessionService?.sendInput(it) },
                                        onBack = { currentTab = ScreenTab.DASHBOARD }
                                    )
                                } else {
                                    DashboardScreen(
                                        status = status,
                                        allocatedMemoryMb = allocatedMemoryMb,
                                        rootfsPath = rootfsPath,
                                        onMemoryChange = {},
                                        onSelectRootfs = {},
                                        onStartKernel = {},
                                        onStopKernel = {},
                                        onOpenTerminal = {},
                                        onOpenGraphical = {}
                                    )
                                }
                            }
                            ScreenTab.GRAPHICAL -> {
                                GraphicalScreen(
                                    onBack = { currentTab = ScreenTab.DASHBOARD },
                                    onSendKey = { key ->
                                        sessionService?.sendInput(key)
                                    }
                                )
                            }
                            ScreenTab.APPS -> {
                                AppDrawerScreen(
                                    onBack = { currentTab = ScreenTab.DASHBOARD },
                                    onLaunchApp = { app ->
                                        sessionService?.sendInput(app.launchCommand + "\n")
                                        currentTab = ScreenTab.GRAPHICAL
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
