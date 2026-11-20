package com.agy.uml.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

enum class DisplayEngineMode {
    NATIVE_CANVAS, // Native RFB/VNC frame receiver directly on SurfaceView
    WEB_NOVNC       // Hardware-accelerated HTML5 canvas over local WebSocket
}

sealed interface DisplayConnectionState {
    data object Disconnected : DisplayConnectionState
    data object Connecting : DisplayConnectionState
    data class Connected(val width: Int, val height: Int) : DisplayConnectionState
    data class Error(val message: String) : DisplayConnectionState
}

@SuppressLint("ClickableViewAccessibility")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphicalScreen(
    onBack: () -> Unit,
    onSendKey: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var engineMode by remember { mutableStateOf(DisplayEngineMode.NATIVE_CANVAS) }
    var connectionState by remember { mutableStateOf<DisplayConnectionState>(DisplayConnectionState.Disconnected) }
    var isTouchpadMode by remember { mutableStateOf(true) }
    var targetResolution by remember { mutableStateOf("1280x720") }
    var activePort by remember { mutableIntStateOf(5900) }

    // Surface reference for native frame drawing
    var surfaceHolderRef by remember { mutableStateOf<SurfaceHolder?>(null) }
    var vncSocket by remember { mutableStateOf<Socket?>(null) }

    fun connectDisplay() {
        connectionState = DisplayConnectionState.Connecting
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress("127.0.0.1", activePort), 4000)
                socket.tcpNoDelay = true
                vncSocket = socket

                val inStream = DataInputStream(socket.getInputStream())
                val outStream = DataOutputStream(socket.getOutputStream())

                // 1. Handshake: Read server RFB version (e.g. "RFB 003.008\n")
                val versionBuf = ByteArray(12)
                inStream.readFully(versionBuf)
                // Respond with matching version
                outStream.write("RFB 003.008\n".toByteArray(Charsets.US_ASCII))
                outStream.flush()

                // 2. Security negotiation
                val numSecurityTypes = inStream.readByte().toInt() and 0xFF
                if (numSecurityTypes > 0) {
                    val secTypes = ByteArray(numSecurityTypes)
                    inStream.readFully(secTypes)
                    // Pick None (1) or VNC auth (2)
                    outStream.writeByte(1)
                    outStream.flush()
                }

                // SecurityResult (for RFB 3.8: 0 = OK)
                val secResult = inStream.readInt()
                if (secResult != 0) {
                    throw IllegalStateException("VNC auth failed: code $secResult")
                }

                // 3. ClientInit: share desktop with others = 1
                outStream.writeByte(1)
                outStream.flush()

                // 4. ServerInit: framebuffer width, height, pixel format, name
                val fbWidth = inStream.readShort().toInt() and 0xFFFF
                val fbHeight = inStream.readShort().toInt() and 0xFFFF
                // Pixel format (16 bytes)
                val pixelFormat = ByteArray(16)
                inStream.readFully(pixelFormat)
                val nameLen = inStream.readInt()
                val nameBuf = ByteArray(nameLen)
                inStream.readFully(nameBuf)

                withContext(Dispatchers.Main) {
                    connectionState = DisplayConnectionState.Connected(fbWidth, fbHeight)
                }

                // Set Pixel Format to 32-bit ARGB8888
                outStream.writeByte(0) // message-type: SetPixelFormat
                outStream.write(ByteArray(3)) // pad
                outStream.writeByte(32) // bpp
                outStream.writeByte(24) // depth
                outStream.writeByte(0)  // big-endian = false
                outStream.writeByte(1)  // true-color = true
                outStream.writeShort(255) // red-max
                outStream.writeShort(255) // green-max
                outStream.writeShort(255) // blue-max
                outStream.writeByte(16) // red-shift
                outStream.writeByte(8)  // green-shift
                outStream.writeByte(0)  // blue-shift
                outStream.write(ByteArray(3)) // pad
                outStream.flush()

                // Set Encodings (Raw = 0)
                outStream.writeByte(2) // SetEncodings
                outStream.writeByte(0) // pad
                outStream.writeShort(1) // 1 encoding
                outStream.writeInt(0) // Raw
                outStream.flush()

                val bitmap = Bitmap.createBitmap(fbWidth, fbHeight, Bitmap.Config.ARGB_8888)
                val paint = Paint().apply { isFilterBitmap = true }
                val pixels = IntArray(fbWidth * fbHeight)

                // Frame loop
                while (isActive && !socket.isClosed) {
                    // FramebufferUpdateRequest: incremental = 1
                    outStream.writeByte(3)
                    outStream.writeByte(1) // incremental
                    outStream.writeShort(0) // x
                    outStream.writeShort(0) // y
                    outStream.writeShort(fbWidth)
                    outStream.writeShort(fbHeight)
                    outStream.flush()

                    val msgType = inStream.readByte().toInt() and 0xFF
                    if (msgType == 0) { // FramebufferUpdate
                        inStream.readByte() // pad
                        val numRects = inStream.readShort().toInt() and 0xFFFF
                        for (r in 0 until numRects) {
                            val rx = inStream.readShort().toInt() and 0xFFFF
                            val ry = inStream.readShort().toInt() and 0xFFFF
                            val rw = inStream.readShort().toInt() and 0xFFFF
                            val rh = inStream.readShort().toInt() and 0xFFFF
                            val encoding = inStream.readInt()

                            if (encoding == 0) { // Raw
                                val rawBytes = ByteArray(rw * rh * 4)
                                inStream.readFully(rawBytes)

                                var byteIdx = 0
                                for (y in 0 until rh) {
                                    for (x in 0 until rw) {
                                        val b = rawBytes[byteIdx].toInt() and 0xFF
                                        val g = rawBytes[byteIdx + 1].toInt() and 0xFF
                                        val rCol = rawBytes[byteIdx + 2].toInt() and 0xFF
                                        val a = 0xFF
                                        pixels[(ry + y) * fbWidth + (rx + x)] = (a shl 24) or (rCol shl 16) or (g shl 8) or b
                                        byteIdx += 4
                                    }
                                }
                            }
                        }

                        bitmap.setPixels(pixels, 0, fbWidth, 0, 0, fbWidth, fbHeight)

                        // Render to SurfaceView
                        val holder = surfaceHolderRef
                        if (holder != null && holder.surface.isValid) {
                            val canvas = holder.lockHardwareCanvas()
                            if (canvas != null) {
                                val destRect = Rect(0, 0, canvas.width, canvas.height)
                                canvas.drawBitmap(bitmap, null, destRect, paint)
                                holder.unlockCanvasAndPost(canvas)
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    connectionState = DisplayConnectionState.Error(e.message ?: "Connection failed")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    when (connectionState) {
                                        is DisplayConnectionState.Connected -> Color(0xFF2E7D32)
                                        is DisplayConnectionState.Connecting -> Color(0xFFF57F17)
                                        else -> Color.Gray
                                    },
                                    RoundedCornerShape(5.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Linux Wayland / X11 Display", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = when (val s = connectionState) {
                                    is DisplayConnectionState.Connected -> "Live 60Hz (${s.width}x${s.height}) • Port $activePort"
                                    is DisplayConnectionState.Connecting -> "Connecting to 127.0.0.1:$activePort..."
                                    is DisplayConnectionState.Error -> "Offline: ${s.message}"
                                    DisplayConnectionState.Disconnected -> "Display Server Offline"
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
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
                    IconButton(onClick = {
                        if (connectionState is DisplayConnectionState.Connected) {
                            vncSocket?.close()
                            connectionState = DisplayConnectionState.Disconnected
                        } else {
                            connectDisplay()
                        }
                    }) {
                        Icon(
                            imageVector = if (connectionState is DisplayConnectionState.Connected) Icons.Default.Refresh else Icons.Default.PlayArrow,
                            contentDescription = "Toggle Connection"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F0F14))
        ) {
            when (engineMode) {
                DisplayEngineMode.NATIVE_CANVAS -> {
                    // Native Hardware Canvas SurfaceView
                    AndroidView(
                        factory = { context ->
                            SurfaceView(context).apply {
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {
                                        surfaceHolderRef = holder
                                        if (connectionState is DisplayConnectionState.Disconnected) {
                                            connectDisplay()
                                        }
                                    }

                                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                        surfaceHolderRef = holder
                                    }

                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        surfaceHolderRef = null
                                    }
                                })

                                // Touch to mouse / pointer event translation
                                setOnTouchListener { _, event ->
                                    val socket = vncSocket
                                    if (socket != null && !socket.isClosed) {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val out = DataOutputStream(socket.getOutputStream())
                                                val mask = when (event.actionMasked) {
                                                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> 1 // Left button
                                                    MotionEvent.ACTION_UP -> 0
                                                    else -> 0
                                                }
                                                val normX = (event.x / width.toFloat() * 1280).toInt().coerceIn(0, 1279)
                                                val normY = (event.y / height.toFloat() * 720).toInt().coerceIn(0, 719)
                                                // PointerEvent message
                                                out.writeByte(5)
                                                out.writeByte(mask)
                                                out.writeShort(normX)
                                                out.writeShort(normY)
                                                out.flush()
                                            } catch (_: Exception) {}
                                        }
                                    }
                                    true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                DisplayEngineMode.WEB_NOVNC -> {
                    // HTML5 Accelerated noVNC / WebSocket Canvas Fallback
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                webViewClient = WebViewClient()
                                loadUrl("http://127.0.0.1:6080/vnc.html?autoconnect=true&resize=scale")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Setup / Launch Guide Banner when Server is not yet running
            if (connectionState !is DisplayConnectionState.Connected) {
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .fillMaxWidth(0.9f),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.DesktopWindows,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Text(
                            text = "Launch Wayland / X11 Server",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "To render GUI apps (Chrome, Blender, GIMP, Firefox) with Mesa llvmpipe at 60 FPS, start the display server inside your Linux shell:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            color = Color(0xFF1E1E24),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "vncserver :0 -geometry $targetResolution -depth 24 -localhost no -SecurityTypes None",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFF61AFEF),
                                modifier = Modifier.padding(12.dp)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    // Send 1-tap start command ensuring /etc/hosts has hostname to prevent 'Name or service not known'
                                    onSendKey("grep -q 'umdebian' /etc/hosts 2>/dev/null || echo '127.0.0.1 localhost umdebian' | sudo tee -a /etc/hosts; vncserver -kill :0 2>/dev/null; vncserver :0 -geometry $targetResolution -depth 24 -localhost no -SecurityTypes None; export DISPLAY=:0 WAYLAND_DISPLAY=wayland-0\n")
                                    connectDisplay()
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("1-Tap Start GUI Engine")
                            }

                            FilledTonalButton(
                                onClick = { connectDisplay() },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry Connection")
                            }
                        }
                    }
                }
            }

            // Floating Desktop Utility Controls Bar (Super, Alt+Tab, Terminal, Esc)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { onSendKey("\u001b") },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("ESC", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { onSendKey("\u001b\t") },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Alt+Tab", fontSize = 11.sp)
                    }

                    FilledTonalButton(
                        onClick = { onSendKey("\nx-terminal-emulator &\n") },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Terminal", fontSize = 11.sp)
                    }

                    FilledTonalButton(
                        onClick = { onSendKey("\nopenbox --restart 2>/dev/null &\n") },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Window Mgr", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
