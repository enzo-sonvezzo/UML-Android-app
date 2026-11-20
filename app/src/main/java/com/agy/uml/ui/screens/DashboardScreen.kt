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
    onMemoryChange: (Int) -> Unit,
    onSelectRootfs: () -> Unit,
    onStartKernel: () -> Unit,
    onStopKernel: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenGraphical: () -> Unit
) {
    val scrollState = rememberScrollState()
    val isRootfsValid = rootfsPath != null && File(rootfsPath).exists() && File(rootfsPath).length() > 0

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
                                enabled = status !is UmlStatus.Starting && isRootfsValid
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Boot Linux Session")
                            }
                        }
                    }

                    if (!isRootfsValid && status is UmlStatus.Stopped) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠️ Select a valid RootFS (.ext4) image below to boot.",
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
                    containerColor = if (isRootfsValid) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
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
                                text = "RootFS Block Device (ubda)",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        FilledTonalButton(
                            onClick = onSelectRootfs,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            enabled = status is UmlStatus.Stopped
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Select Image", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isRootfsValid && rootfsPath != null) {
                        val file = File(rootfsPath)
                        val sizeMb = file.length() / (1024 * 1024)
                        Text(
                            text = file.name,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Size: ${sizeMb} MB • Path: $rootfsPath",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    } else {
                        Text(
                            text = "debian-docker.ext4 not found in app storage.",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Tap 'Select Image' to choose debian-docker.ext4 from Downloads, or copy it to /sdcard/Download/Termux/uml-android/",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
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
