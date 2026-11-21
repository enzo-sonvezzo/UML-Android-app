package com.agy.uml.model

sealed interface UmlStatus {
    data object Stopped : UmlStatus
    data object Starting : UmlStatus
    data class Running(val pid: Int, val uptimeSeconds: Long) : UmlStatus
    data class Error(val message: String) : UmlStatus
}

data class UmlConfiguration(
    val memoryMb: Int = 2048,
    val cpuCores: Int = 4,
    val rootfsPath: String,
    val stubExePath: String,
    val kernelPath: String,
    val umnetPath: String? = null,
    val passtPath: String? = null,
    val enableNetworking: Boolean = true,
    val enableGpuAcceleration: Boolean = true,
    val extraKernelParams: String = "panic=-1 con=null con0=fd:0,fd:1 rw"
)

data class SystemMetrics(
    val cpuUsagePercent: Float = 0f,
    val usedMemoryMb: Int = 0,
    val totalMemoryMb: Int = 2048,
    val diskUsedMb: Long = 0L,
    val diskTotalMb: Long = 0L
)
