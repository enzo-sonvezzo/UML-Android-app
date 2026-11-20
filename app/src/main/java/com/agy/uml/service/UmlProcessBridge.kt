package com.agy.uml.service

import com.agy.uml.model.UmlConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object UmlProcessBridge {

    init {
        System.loadLibrary("uml_jni")
    }

    external fun nativeOpenPty(rows: Int, cols: Int): IntArray?
    external fun nativeForkAndExec(
        cmdArray: Array<String>,
        slaveFd: Int,
        workingDir: String
    ): Int

    external fun nativeSetWindowSize(masterFd: Int, rows: Int, cols: Int)
    external fun nativeWrite(masterFd: Int, data: ByteArray, offset: Int, length: Int): Int
    external fun nativeRead(masterFd: Int, buffer: ByteArray, offset: Int, length: Int): Int
    external fun nativeKill(pid: Int, sig: Int)
    external fun nativeWaitPid(pid: Int, nohang: Boolean): Int

    suspend fun launchUmlProcess(
        config: UmlConfiguration,
        workingDir: File,
        rows: Int = 24,
        cols: Int = 80
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val fds = nativeOpenPty(rows, cols) ?: throw IllegalStateException("Failed to open pseudoterminal")
        val masterFd = fds[0]
        val slaveFd = fds[1]

        val cmdList = mutableListOf<String>()

        if (config.enableNetworking && config.umnetPath != null && config.passtPath != null) {
            cmdList.add(config.umnetPath)
            cmdList.add("--passt")
            cmdList.add(config.passtPath)
            cmdList.add("--dns")
            cmdList.add("1.1.1.1")
            cmdList.add("--")
        }

        cmdList.add(config.kernelPath)
        cmdList.add("mem=${config.memoryMb}M")
        cmdList.add("ubd0=${config.rootfsPath}")
        cmdList.add("root=/dev/ubda")
        cmdList.add("init=/umarm-init")
        cmdList.add("stub_exe=${config.stubExePath}")

        config.extraKernelParams.split(" ").filter { it.isNotBlank() }.forEach {
            cmdList.add(it)
        }

        val pid = nativeForkAndExec(cmdList.toTypedArray(), slaveFd, workingDir.absolutePath)
        if (pid <= 0) {
            throw IllegalStateException("Failed to fork/exec UML binary, pid=$pid")
        }

        Pair(pid, masterFd)
    }

    /**
     * Helper script to initialize guest networking, mount shm/cgroup,
     * and bridge the Anland Wayland socket to the Android host Surface consumer.
     */
    fun getGuestStartupScript(anlandPort: Int = 19455): String {
        return """
            ifconfig vec0 up 2>/dev/null || true
            udhcpc -i vec0 -n -q -t 4 2>/dev/null || true
            mkdir -p /sys/fs/cgroup /run /dev/shm
            mount -t cgroup2 none /sys/fs/cgroup 2>/dev/null || true
            mount -t tmpfs tmpfs /run 2>/dev/null || true
            mount -t tmpfs tmpfs /dev/shm 2>/dev/null || true
            echo 'nameserver 1.1.1.1' > /etc/resolv.conf
            # Bridge guest Wayland/Anland socket to Android Host
            mkdir -p /tmp/.anland
            socat UNIX-LISTEN:/tmp/.anland/wayland-0,fork TCP:10.0.2.2:$anlandPort &
            export WAYLAND_DISPLAY=/tmp/.anland/wayland-0
        """.trimIndent()
    }
}
