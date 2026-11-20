package com.agy.uml.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agy.uml.model.UmlStatus
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    status: UmlStatus,
    allocatedMemoryMb: Int,
    rootfsPath: String?,
    importProgress: Float?,
    hasAllFilesAccess: Boolean,
    onRequestStoragePermission: () -> Unit,
    onMemoryChange: (Int) -> Unit,
    onSelectRootfs: () -> Unit,
    onExpandDisk: ((Long) -> Unit)? = null,
    onStartKernel: () -> Unit,
    onStopKernel: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenGraphical: () -> Unit
) {
    val scrollState = rememberScrollState()
    val rootfsFile = rootfsPath?.let { File(it) }
    // An ext4 rootfs must be at least 500 MB (full image is ~1.3 GB)
    val isRootfsValid = rootfsFile != null && rootfsFile.exists() && rootfsFile.length() > 500 * 1024 * 1024L
    val isRootfsTruncated = rootfsFile != null && rootfsFile.exists() && rootfsFile.length() in 1 until (500 * 1024 * 1024L)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "User-Mode Linux",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Kernel Status",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        val statusColor by animateColorAsState(
                            targetValue = when (status) {
                                is UmlStatus.Running -> Color(0xFF2E7D32) // Green
                                is UmlStatus.Starting -> Color(0xFFF57F17) // Orange
                                is UmlStatus.Stopped -> Color(0xFF757575) // Gray
                                is UmlStatus.Error -> MaterialTheme.colorScheme.error
                            },
                            animationSpec = tween(400)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (status) {
                                    is UmlStatus.Running -> "RUNNING"
                                    is UmlStatus.Starting -> "BOOTING..."
                                    is UmlStatus.Stopped -> "STOPPED"
                                    is UmlStatus.Error -> "ERROR"
                                },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (status is UmlStatus.Running) {
                        Text(
                            text = "PID: ${status.pid} • Uptime: ${status.uptimeSeconds}s",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Mode: Unprivileged userspace (ptrace)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    } else {
                        Text(
                            text = "Linux kernel 7.2.0-rc4 (ARM64)",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (status is UmlStatus.Running) {
                            Button(
                                onClick = onOpenTerminal,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Terminal, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Terminal")
                            }

                            FilledTonalButton(
                                onClick = onOpenGraphical,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.DesktopWindows, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Desktop")
                            }

                            IconButton(
                                onClick = onStopKernel,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Default.PowerSettingsNew, contentDescription = "Shutdown")
                            }
                        } else {
                            Button(
                                onClick = onStartKernel,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                enabled = status !is UmlStatus.Starting && isRootfsValid && importProgress == null
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (importProgress != null) "Importing Image..."
                                    else if (!isRootfsValid) "RootFS Image Required"
                                    else "Boot Linux Session"
                                )
                            }
                        }
                    }

                    if (!isRootfsValid && status is UmlStatus.Stopped && importProgress == null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isRootfsTruncated) "⚠️ Current image file is truncated (${rootfsFile?.length()?.div(1024 * 1024)} MB). Full image is 1.3 GB."
                            else "⚠️ Please select or link the 1.3 GB debian-docker.ext4 image below.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Disk Image / RootFS Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRootfsValid) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isRootfsValid) Icons.Default.Storage else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isRootfsValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "RootFS Image (ubda)",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        FilledTonalButton(
                            onClick = onSelectRootfs,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            enabled = status is UmlStatus.Stopped && importProgress == null
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import Image", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (importProgress != null) {
                        LinearProgressIndicator(
                            progress = { importProgress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Importing RootFS... ${(importProgress * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (isRootfsValid && rootfsFile != null) {
                        val sizeMb = rootfsFile.length() / (1024 * 1024)
                        val sizeGb = sizeMb / 1024
                        Text(
                            text = rootfsFile.name,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Virtual Capacity: ${if (sizeGb > 0) "$sizeGb GB" else "$sizeMb MB"} (Dynamic Sparse ext4)",
                            fontSize = 12.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Path: ${rootfsFile.absolutePath}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Expand Disk Capacity (Dynamic Sparse):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { onExpandDisk?.invoke(8L) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text("+8 GB", fontSize = 11.sp)
                            }
                            FilledTonalButton(
                                onClick = { onExpandDisk?.invoke(16L) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text("+16 GB", fontSize = 11.sp)
                            }
                            FilledTonalButton(
                                onClick = { onExpandDisk?.invoke(32L) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text("+32 GB", fontSize = 11.sp)
                            }
                        }
                    } else {
                        Text(
                            text = if (isRootfsTruncated) "Truncated Image File (${rootfsFile?.length()?.div(1024 * 1024)} MB)"
                            else "Full 1.3 GB debian-docker.ext4 not found",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (!hasAllFilesAccess) {
                            Button(
                                onClick = onRequestStoragePermission,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Grant All Files Access (Zero-Copy Boot)", fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Enabling All Files Access lets UML boot directly from /sdcard/Download/ without copying 1.3 GB!",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        } else {
                            Text(
                                text = "Place debian-docker.ext4 in /sdcard/Download/Termux/uml-android/ or tap 'Import Image'.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Memory Configuration Slider
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Allocated RAM",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${allocatedMemoryMb} MB",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = allocatedMemoryMb.toFloat(),
                        onValueChange = { onMemoryChange(it.toInt()) },
                        valueRange = 512f..4096f,
                        steps = 7,
                        enabled = status is UmlStatus.Stopped
                    )

                    Text(
                        text = "Memory is dynamically mapped into host RAM via mmap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Subsystem Capabilities Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Active Subsystems",
                        style = MaterialTheme.typography.titleMedium
                    )

                    CapabilityRow("Docker Engine", "Verified (dockerd + runc)", Icons.Default.CheckCircle)
                    CapabilityRow("Podman Container Runtime", "Verified (rootless namespaces)", Icons.Default.CheckCircle)
                    CapabilityRow("LXC Containers", "Supported (cgroup v2 & netns)", Icons.Default.CheckCircle)
                    CapabilityRow("Flatpak & Bubblewrap", "Supported (unprivileged user_ns)", Icons.Default.CheckCircle)
                    CapabilityRow("Rootless Networking", "Active (passt + umnet NAT)", Icons.Default.Wifi)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CapabilityRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
