package com.agy.uml

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.OpenableColumns
import android.provider.Settings
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
import com.agy.uml.service.UmlProcessBridge
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

    private var sessionService by mutableStateOf<UmlSessionService?>(null)
    private var isBound by mutableStateOf(false)

    // Activity-level observable state for RootFS detection and storage access
    private var rootfsPath by mutableStateOf<String?>(null)
    private var importProgress by mutableStateOf<Float?>(null)
    private var hasAllFilesAccess by mutableStateOf(false)

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

        scanForRootfs()

        setContent {
            UmlAndroidTheme {
                val service = sessionService
                val status by (service?.status?.collectAsState() ?: remember { mutableStateOf(UmlStatus.Stopped) })
                val terminalOutputFlow = service?.terminalOutput

                var currentTab by remember { mutableStateOf(ScreenTab.DASHBOARD) }
                var allocatedMemoryMb by remember { mutableIntStateOf(2048) }
                var cpuCores by remember { mutableIntStateOf(6) }
                var enableGpuAcceleration by remember { mutableStateOf(true) }
                var gpuRendererInfo by remember { mutableStateOf("Detecting Mali GPU...") }

                val coroutineScope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val renderer = UmlProcessBridge.nativeGetGpuRenderer()
                            withContext(Dispatchers.Main) {
                                gpuRendererInfo = renderer
                            }
                        } catch (_: Exception) {
                            withContext(Dispatchers.Main) {
                                gpuRendererInfo = "ARM Mali GPU (MediaTek SoC)"
                            }
                        }
                    }
                }

                // File picker launcher with atomic copy & live progress tracking
                val rootfsPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    uri?.let { selectedUri ->
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                var totalSize = -1L
                                contentResolver.query(selectedUri, null, null, null, null)?.use { cursor ->
                                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                                    if (sizeIndex != -1 && cursor.moveToFirst()) {
                                        totalSize = cursor.getLong(sizeIndex)
                                    }
                                }

                                val partFile = File(filesDir, "debian-docker.ext4.part")
                                val finalFile = File(filesDir, "debian-docker.ext4")

                                if (partFile.exists()) partFile.delete()

                                contentResolver.openInputStream(selectedUri)?.use { input ->
                                    FileOutputStream(partFile).use { output ->
                                        val buffer = ByteArray(256 * 1024)
                                        var bytesCopied = 0L
                                        var bytesRead: Int
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            output.write(buffer, 0, bytesRead)
                                            bytesCopied += bytesRead
                                            if (totalSize > 0) {
                                                withContext(Dispatchers.Main) {
                                                    importProgress = bytesCopied.toFloat() / totalSize.toFloat()
                                                }
                                            }
                                        }
                                    }
                                }

                                if (partFile.length() > 500 * 1024 * 1024L) {
                                    if (finalFile.exists()) finalFile.delete()
                                    partFile.renameTo(finalFile)
                                    withContext(Dispatchers.Main) {
                                        importProgress = null
                                        scanForRootfs()
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Disk image imported successfully (${finalFile.length() / (1024 * 1024)} MB)!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    val brokenSizeMb = partFile.length() / (1024 * 1024)
                                    partFile.delete()
                                    withContext(Dispatchers.Main) {
                                        importProgress = null
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Error: Selected file is incomplete ($brokenSizeMb MB). Full Debian image is ~1.3 GB.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    importProgress = null
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Failed to import image: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
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
                                    importProgress = importProgress,
                                    hasAllFilesAccess = hasAllFilesAccess,
                                    onRequestStoragePermission = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            try {
                                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                    data = Uri.parse("package:$packageName")
                                                }
                                                startActivity(intent)
                                            } catch (e: Exception) {
                                                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                            }
                                        }
                                    },
                                    onMemoryChange = { allocatedMemoryMb = it },
                                    cpuCores = cpuCores,
                                    onCpuCoresChange = { cpuCores = it },
                                    enableGpuAcceleration = enableGpuAcceleration,
                                    onToggleGpuAcceleration = { enableGpuAcceleration = it },
                                    gpuRendererInfo = gpuRendererInfo,
                                    onRunGpuBenchmark = {
                                        sessionService?.sendInput("\ncommand -v uml-gpu-test >/dev/null && uml-gpu-test || (DISPLAY=:0 es2gears 2>/dev/null || DISPLAY=:0 glxgears 2>/dev/null || echo 'Please install mesa-utils-extra: apt update && apt install -y --no-install-recommends mesa-utils-extra')\n")
                                        currentTab = ScreenTab.TERMINAL
                                    },
                                    onShowGpuInfo = {
                                        sessionService?.sendInput("\ncommand -v uml-gpu-info >/dev/null && uml-gpu-info || (DISPLAY=:0 glxinfo -B 2>/dev/null || DISPLAY=:0 es2_info 2>/dev/null || echo 'Please install mesa-utils: apt update && apt install -y --no-install-recommends mesa-utils')\n")
                                        currentTab = ScreenTab.TERMINAL
                                    },
                                    onSelectRootfs = {
                                        rootfsPickerLauncher.launch(arrayOf("*/*"))
                                    },
                                    onExpandDisk = { addGb ->
                                        rootfsPath?.let { path ->
                                            val file = File(path)
                                            val currentGb = file.length() / (1024L * 1024L * 1024L)
                                            val newGb = (if (currentGb < 1) 16L else currentGb) + addGb
                                            if (AssetInstaller.expandSparseDisk(file, newGb)) {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Expanded disk capacity to $newGb GB! (0 physical MB allocated)",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                scanForRootfs()
                                            }
                                        }
                                    },
                                    onStartKernel = {
                                        val targetRootfs = rootfsPath
                                        if (targetRootfs == null || !File(targetRootfs).exists() || File(targetRootfs).length() < 500 * 1024 * 1024L) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Cannot boot: RootFS image is missing or incomplete (<500MB)!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            return@DashboardScreen
                                        }

                                        val filesDir = filesDir
                                        val libDir = File(applicationInfo.nativeLibraryDir)

                                        val kernelFile = File(libDir, "liblinux.so").takeIf { it.exists() } ?: File(filesDir, "linux")
                                        val stubExeFile = File(libDir, "libstub_exe.so").takeIf { it.exists() } ?: File(filesDir, "stub_exe")
                                        val umnetFile = File(libDir, "libumnet.so").takeIf { it.exists() } ?: File(filesDir, "umnet")
                                        val passtFile = File(libDir, "libpasst.so").takeIf { it.exists() } ?: File(filesDir, "passt")

                                        val config = UmlConfiguration(
                                            memoryMb = allocatedMemoryMb,
                                            cpuCores = cpuCores,
                                            rootfsPath = targetRootfs,
                                            stubExePath = stubExeFile.absolutePath,
                                            kernelPath = kernelFile.absolutePath,
                                            umnetPath = if (umnetFile.exists()) umnetFile.absolutePath else null,
                                            passtPath = if (passtFile.exists()) passtFile.absolutePath else null,
                                            enableNetworking = true,
                                            enableGpuAcceleration = enableGpuAcceleration
                                        )
                                        sessionService?.startSession(config)
                                        currentTab = ScreenTab.TERMINAL
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
                                        importProgress = importProgress,
                                        hasAllFilesAccess = hasAllFilesAccess,
                                        onRequestStoragePermission = {},
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

    override fun onResume() {
        super.onResume()
        scanForRootfs()
    }

    private fun scanForRootfs() {
        hasAllFilesAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }

        // Clean up any corrupt or truncated partial downloads (< 500 MB) from private filesDir
        val localFile = File(filesDir, "debian-docker.ext4")
        if (localFile.exists() && localFile.length() < 500 * 1024 * 1024L) {
            localFile.delete()
        }

        val candidatePaths = listOf(
            File(filesDir, "debian-docker.ext4"),
            File(getExternalFilesDir(null), "debian-docker.ext4"),
            File("/storage/emulated/0/Download/Termux/uml-android/debian-docker.ext4"),
            File("/storage/emulated/0/Download/debian-docker.ext4"),
            File("/sdcard/Download/Termux/uml-android/debian-docker.ext4"),
            File("/sdcard/Download/debian-docker.ext4")
        )
        val found = candidatePaths.firstOrNull { it.exists() && it.canRead() && it.length() > 500 * 1024 * 1024L }
        rootfsPath = found?.absolutePath
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
