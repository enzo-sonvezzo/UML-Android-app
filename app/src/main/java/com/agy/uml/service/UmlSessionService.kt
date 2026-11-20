package com.agy.uml.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.agy.uml.MainActivity
import com.agy.uml.model.SystemMetrics
import com.agy.uml.model.UmlConfiguration
import com.agy.uml.model.UmlStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class UmlSessionService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private var wakeLock: PowerManager.WakeLock? = null
    private var umlPid: Int = -1
    private var ptyMasterFd: Int = -1

    private val _status = MutableStateFlow<UmlStatus>(UmlStatus.Stopped)
    val status: StateFlow<UmlStatus> = _status.asStateFlow()

    private val _terminalOutput = MutableSharedFlow<String>(replay = 50)
    val terminalOutput: SharedFlow<String> = _terminalOutput.asSharedFlow()

    private val _metrics = MutableStateFlow(SystemMetrics())
    val metrics: StateFlow<SystemMetrics> = _metrics.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): UmlSessionService = this@UmlSessionService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UmlAndroid::SessionLock")
    }

    fun startSession(config: UmlConfiguration) {
        if (_status.value is UmlStatus.Running || _status.value is UmlStatus.Starting) return

        _status.value = UmlStatus.Starting
        startForeground(NOTIFICATION_ID, createNotification("Starting User-Mode Linux..."))
        wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24h safety timeout

        serviceScope.launch(Dispatchers.IO) {
            try {
                val workingDir = filesDir
                val (pid, masterFd) = UmlProcessBridge.launchUmlProcess(config, workingDir)
                umlPid = pid
                ptyMasterFd = masterFd

                val startTime = System.currentTimeMillis()
                _status.value = UmlStatus.Running(pid, 0)
                updateNotification("UML Linux Running (PID $pid)")

                // Launch terminal reader loop
                launch { readTerminalLoop(masterFd) }

                // Launch process watcher and metrics loop
                launch { watchProcess(pid, startTime) }

            } catch (e: Exception) {
                _status.value = UmlStatus.Error(e.message ?: "Failed to boot UML kernel")
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }

    fun stopSession() {
        if (umlPid > 0) {
            UmlProcessBridge.nativeKill(umlPid, 15) // SIGTERM
            serviceScope.launch {
                delay(3000)
                if (umlPid > 0) {
                    UmlProcessBridge.nativeKill(umlPid, 9) // SIGKILL
                }
            }
        }
    }

    fun sendInput(input: String) {
        if (ptyMasterFd > 0) {
            val bytes = input.toByteArray(Charsets.UTF_8)
            UmlProcessBridge.nativeWrite(ptyMasterFd, bytes, 0, bytes.size)
        }
    }

    fun resizeTerminal(rows: Int, cols: Int) {
        if (ptyMasterFd > 0) {
            UmlProcessBridge.nativeSetWindowSize(ptyMasterFd, rows, cols)
        }
    }

    private suspend fun readTerminalLoop(masterFd: Int) {
        val buffer = ByteArray(4096)
        while (serviceScope.isActive) {
            val bytesRead = UmlProcessBridge.nativeRead(masterFd, buffer, 0, buffer.size)
            if (bytesRead > 0) {
                val text = String(buffer, 0, bytesRead, Charsets.UTF_8)
                _terminalOutput.emit(text)
            } else {
                // If process is stopped, drain any trailing bytes (e.g. panic or execvp error)
                if (_status.value !is UmlStatus.Running && _status.value !is UmlStatus.Starting) {
                    var hasMore = false
                    for (i in 0 until 5) {
                        delay(20)
                        val extra = UmlProcessBridge.nativeRead(masterFd, buffer, 0, buffer.size)
                        if (extra > 0) {
                            _terminalOutput.emit(String(buffer, 0, extra, Charsets.UTF_8))
                            hasMore = true
                        }
                    }
                    if (!hasMore) break
                }
                delay(10)
            }
        }
    }

    private suspend fun watchProcess(pid: Int, startTime: Long) {
        while (serviceScope.isActive) {
            val res = UmlProcessBridge.nativeWaitPid(pid, true)
            if (res != 0) {
                // Process finished or exited
                delay(100) // Allow reader to capture final stdout/stderr
                _status.value = UmlStatus.Stopped
                umlPid = -1
                ptyMasterFd = -1
                wakeLock?.release()
                stopForeground(STOP_FOREGROUND_REMOVE)
                break
            }

            val uptime = (System.currentTimeMillis() - startTime) / 1000
            _status.value = UmlStatus.Running(pid, uptime)
            delay(1000)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Linux Session Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active User-Mode Linux execution state"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("User-Mode Linux Container")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    companion object {
        const val CHANNEL_ID = "uml_session_channel"
        const val NOTIFICATION_ID = 1001
    }
}
