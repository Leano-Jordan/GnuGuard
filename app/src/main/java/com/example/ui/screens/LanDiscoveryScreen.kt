package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CurrentNetworkInfo
import com.example.data.model.DeviceType
import com.example.data.model.LanDevice
import com.example.ui.components.StatCard
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.SecurityGreen
import com.example.ui.viewmodel.DeviceFilter

@Composable
fun LanDiscoveryScreen(
    currentNetworkInfo: CurrentNetworkInfo,
    devices: List<LanDevice>,
    isScanning: Boolean,
    progress: Float,
    scanningIp: String,
    filter: DeviceFilter,
    onFilterChanged: (DeviceFilter) -> Unit,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onDeviceSelected: (LanDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "lan_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isScanning) onCancelScan() else onStartScan()
                },
                containerColor = if (isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_scan_lan")
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.Refresh,
                    contentDescription = if (isScanning) "Stop Discovery" else "Start Discovery",
                    modifier = if (isScanning) Modifier else Modifier
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Subnet Gateway Header Card
            item {
                SubnetOverviewCard(
                    networkInfo = currentNetworkInfo,
                    deviceCount = devices.size
                )
            }

            // Live Discovery Scan Progress
            item {
                AnimatedVisibility(visible = isScanning) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Scanning Local Subnet /24…",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp)),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Probing: $scanningIp • mDNS / ZeroConf active",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick Device Stats Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val phonesCount = devices.count { it.deviceType == DeviceType.PHONE || it.deviceType == DeviceType.TABLET }
                    StatCard(
                        title = "Phones",
                        value = "$phonesCount",
                        icon = Icons.Default.Smartphone,
                        accentColor = CyanAccent,
                        modifier = Modifier.weight(1f)
                    )
                    val pcCount = devices.count { it.deviceType == DeviceType.COMPUTER }
                    StatCard(
                        title = "Computers",
                        value = "$pcCount",
                        icon = Icons.Default.Computer,
                        accentColor = CyanPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    val mediaCount = devices.count { it.deviceType == DeviceType.SMART_TV_CAST }
                    StatCard(
                        title = "Cast/TV",
                        value = "$mediaCount",
                        icon = Icons.Default.Cast,
                        accentColor = SecurityGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Filter Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeviceFilterChip("All (${devices.size})", filter == DeviceFilter.ALL) {
                        onFilterChanged(DeviceFilter.ALL)
                    }
                    DeviceFilterChip("Phones & Tablets", filter == DeviceFilter.PHONES_TABLETS) {
                        onFilterChanged(DeviceFilter.PHONES_TABLETS)
                    }
                    DeviceFilterChip("Computers", filter == DeviceFilter.COMPUTERS) {
                        onFilterChanged(DeviceFilter.COMPUTERS)
                    }
                    DeviceFilterChip("Media & Cast", filter == DeviceFilter.MEDIA_CAST) {
                        onFilterChanged(DeviceFilter.MEDIA_CAST)
                    }
                    DeviceFilterChip("Routers", filter == DeviceFilter.ROUTERS_GATEWAYS) {
                        onFilterChanged(DeviceFilter.ROUTERS_GATEWAYS)
                    }
                }
            }

            // Device List Items
            if (devices.isEmpty() && !isScanning) {
                item {
                    EmptyLanState(onScan = onStartScan)
                }
            } else {
                items(devices, key = { it.ipAddress }) { device ->
                    LanDeviceCard(
                        device = device,
                        onClick = { onDeviceSelected(device) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SubnetOverviewCard(
    networkInfo: CurrentNetworkInfo,
    deviceCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lan,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "LOCAL NETWORK (LAN)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Box(
                    modifier = Modifier
                        .background(SecurityGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "$deviceCount Active Hosts",
                        color = SecurityGreen,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Subnet ${networkInfo.localIpAddress?.substringBeforeLast(".") ?: "192.168.1"}.0/24",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HeroDetailPill(label = "Your Device IP", value = networkInfo.localIpAddress ?: "192.168.1.105")
                HeroDetailPill(label = "Gateway IP", value = networkInfo.gatewayIp ?: "192.168.1.1")
                HeroDetailPill(label = "Subnet Mask", value = networkInfo.subnetMask ?: "255.255.255.0")
            }
        }
    }
}

@Composable
fun LanDeviceCard(
    device: LanDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("device_card_${device.ipAddress}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isSelf)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Device Type Icon Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (device.isSelf) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getDeviceIcon(device.deviceType, device.isSelf),
                    contentDescription = device.deviceType.label,
                    tint = if (device.isSelf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Middle: Hostname, IP, Vendor, Discovered Services
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val displayName = device.hostName ?: device.vendor ?: device.deviceType.label
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (device.isSelf) {
                        BadgePill("THIS DEVICE", MaterialTheme.colorScheme.primary)
                    } else if (device.isGateway) {
                        BadgePill("GATEWAY", CyanAccent)
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${device.ipAddress} • ${device.vendor ?: device.deviceType.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (device.discoveredServices.isNotEmpty() || device.openPorts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        device.discoveredServices.take(2).forEach { s ->
                            ServiceChip(s.serviceName)
                        }
                        if (device.openPorts.isNotEmpty()) {
                            ServiceChip("${device.openPorts.size} open ports")
                        }
                    }
                }
            }

            // Trailing: Response Latency
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .background(SecurityGreen.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${device.responseTimeMs} ms",
                        color = SecurityGreen,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    )
                }
                Text(
                    text = "latency",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ServiceChip(label: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            text = label.take(18),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun BadgePill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            ),
            color = color
        )
    }
}

fun getDeviceIcon(type: DeviceType, isSelf: Boolean): ImageVector {
    if (isSelf) return Icons.Default.Smartphone
    return when (type) {
        DeviceType.PHONE -> Icons.Default.Smartphone
        DeviceType.TABLET -> Icons.Default.Tablet
        DeviceType.COMPUTER -> Icons.Default.Computer
        DeviceType.ROUTER_GATEWAY -> Icons.Default.Router
        DeviceType.SMART_TV_CAST -> Icons.Default.Tv
        DeviceType.IOT_PRINTER -> Icons.Default.Print
        DeviceType.UNKNOWN -> Icons.Default.DeviceUnknown
    }
}

@Composable
fun DeviceFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
fun EmptyLanState(onScan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Devices,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(56.dp)
        )
        Text(
            text = "No Nearby Devices Discovered Yet",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Tap the button below to scan your local Wi-Fi subnet and discover active phones, laptops, and smart devices via ICMP and mDNS ZeroConf.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onScan,
            modifier = Modifier.testTag("button_start_lan_scan")
        ) {
            Text("Discover LAN Devices")
        }
    }
}
